/*
 * Copyright (C) 2024-2026 Piko contributors
 * Copyright (C) 2026 kveld9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Under Section 7(b) of GNU GPL version 3, you are required to preserve
 * the original author attributions and legal notices.
 */

package app.morphe.patches.twitter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.wrapWithScratchRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.ReferenceUtil

private val urtShowInstructionsStateFingerprint = Fingerprint(
    strings = listOf("UrtShowInstructionsState(showInstructions="),
)

private val quickPromoteEligibilityFingerprint = Fingerprint(
    strings = listOf(
        "x_lite_quick_promote_enabled",
        "x_lite_quick_promote_premium_paywall_enabled",
    ),
    returnType = "Z",
)

private val focalPostStateFingerprint = Fingerprint(
    strings = listOf("FocalPostState(timelinePostState="),
)

private val timelineItemFilterFingerprint = Fingerprint(
    strings = listOf("surface_filter"),
)

private val profileFollowRecommendationResponseFingerprint = Fingerprint(
    strings = listOf("ProfileFollowRecommendationResponse(style="),
)

private val fleetlineApiResponseFingerprint = Fingerprint(
    strings = listOf("FleetlineApiResponse(threads="),
)

private val communityNotesFingerprint = Fingerprint(
    strings = listOf("birdwatch_consumption_enabled"),
)

private val canonicalPostToStringFingerprint = Fingerprint(
    strings = listOf("CanonicalPost(id="),
)

private val inlineActionsFingerprint = Fingerprint(
    strings = listOf("android_ntab_display_options_inline_actions_enabled"),
)

private val homeTopBarFingerprint = Fingerprint(
    returnType = "V",
    filters = listOf(string("home_navigation_bar_title")),
)

// Compose group keys of the add-tab (+) groups in the home tab pager and tab header. Both composables live in the top bar's class.
private const val HOME_TAB_PAGER_ADD_GROUP = -585845020
private const val HOME_TAB_HEADER_ADD_GROUP = -868946867

private val homeTabBarPagerFingerprint = Fingerprint(
    classFingerprint = homeTopBarFingerprint,
    returnType = "V",
    filters = listOf(literal(HOME_TAB_PAGER_ADD_GROUP)),
)

private val homeTabBarHeaderFingerprint = Fingerprint(
    classFingerprint = homeTopBarFingerprint,
    returnType = "V",
    filters = listOf(literal(HOME_TAB_HEADER_ADD_GROUP)),
)

private val liveBannerFingerprint = Fingerprint(
    strings = listOf("x_lite_livestream_pill_enabled"),
)

private val homeTopicsTabFlagFingerprint = Fingerprint(
    strings = listOf("hometimeline_pinned_tabs_topics_enabled"),
)

private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private val ENUM_CONSTANT_FLAGS =
    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value or AccessFlags.ENUM.value

private fun Method.parameterTypeNames() = parameterTypes.map(CharSequence::toString)

private fun dataClassToStringFingerprint(prefix: String) = Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string(prefix)),
)

// Without an exact string filter, matchAllOrNull checks every method in the app, so lookups without one name their package.
private fun BytecodePatchContext.uniqueMatch(what: String, fingerprint: Fingerprint, packagePrefix: String? = null): Match {
    val matches = if (packagePrefix == null) {
        fingerprint.matchAllOrNull().orEmpty()
    } else {
        buildList { classDefForEach { if (it.type.startsWith(packagePrefix)) addAll(fingerprint.matchAllOrNull(it).orEmpty()) } }
    }
    return matches.singleOrNull()
        ?: error("[Timeline UI] Expected one $what, found ${matches.map { it.originalMethod }}.")
}

/** The field a data class `toString` reads right after appending [label]. */
private fun Method.toStringField(label: String, opcode: Opcode, type: String? = null): FieldReference {
    val instructions = implementation?.instructions?.toList().orEmpty()
    val labelIndex = instructions.indexOfFirst {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == label
    }
    val field = if (labelIndex < 0) null else instructions.subList(labelIndex + 1, minOf(labelIndex + 6, instructions.size))
        .firstOrNull { it.opcode == opcode }
        ?.let { (it as ReferenceInstruction).reference as FieldReference }
    return field?.takeIf { type == null || it.type == type }
        ?: error("[Timeline UI] Field after '$label' not found in $definingClass->toString.")
}

private fun ClassDef.hasEnumConstant(name: String) =
    staticFields.any { it.name == name && it.type == type && it.accessFlags == ENUM_CONSTANT_FLAGS }

/** URT timeline models the item filters read, all found through their data class `toString`. */
private class TimelineModels(
    val item: String,
    val itemEntryId: String,
    val module: String,
    val moduleInnerContent: String,
    val moduleEntryId: String,
    val moduleItem: String,
    val moduleItemItem: String,
    val user: String,
)

private fun BytecodePatchContext.timelineModels(): TimelineModels {
    val moduleToString = uniqueMatch(
        "UrtTimelineModule toString",
        dataClassToStringFingerprint("UrtTimelineModule(innerContent="),
    ).originalMethod
    val innerContent = moduleToString.toStringField("UrtTimelineModule(innerContent=", Opcode.IGET_OBJECT, "Ljava/util/List;")
    val moduleEntryId = moduleToString.toStringField(", entryId=", Opcode.IGET_OBJECT, "Ljava/lang/String;")

    val moduleItemToString = uniqueMatch(
        "UrtTimelineModuleItem toString",
        dataClassToStringFingerprint("UrtTimelineModuleItem(item="),
    ).originalMethod
    val moduleItemItem = moduleItemToString.toStringField("UrtTimelineModuleItem(item=", Opcode.IGET_OBJECT)

    val item = moduleItemItem.type
    val user = uniqueMatch("UrtTimelineUser toString", dataClassToStringFingerprint("UrtTimelineUser(user="))
        .originalMethod.definingClass
    val module = moduleToString.definingClass
    listOf(module, user).forEach { model ->
        if (item !in classDefBy(model).interfaces) error("[Timeline UI] $model does not implement timeline item $item.")
    }

    val entryIdGetters = classDefBy(item).methods.filter {
        AccessFlags.ABSTRACT.isSet(it.accessFlags) && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
    }
    val entryIdGetter = entryIdGetters.singleOrNull()
        ?: error("[Timeline UI] Expected one entryId getter on $item, found ${entryIdGetters.map { it.name }}.")

    return TimelineModels(
        item = item,
        itemEntryId = ReferenceUtil.getMethodDescriptor(entryIdGetter),
        module = module,
        moduleInnerContent = ReferenceUtil.getFieldDescriptor(innerContent),
        moduleEntryId = ReferenceUtil.getFieldDescriptor(moduleEntryId),
        moduleItem = moduleItemToString.definingClass,
        moduleItemItem = ReferenceUtil.getFieldDescriptor(moduleItemItem),
        user = user,
    ).also {
        println(
            "[Timeline UI] Resolved timeline item ${it.item} (entryId ${it.itemEntryId}), module ${it.module} " +
                "(${it.moduleInnerContent}, ${it.moduleEntryId}), module item ${it.moduleItemItem}, user ${it.user}.",
        )
    }
}

// Focal post header composable that receives the follow button state.
private fun focalPostHeaderFingerprint(followButtonState: String) = Fingerprint(
    returnType = "V",
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypeNames().let { followButtonState in it && COMPOSER in it }
    },
)

// Post-detail timeline component; its constructor starts the conversation prefetch.
private val postDetailComponentFingerprint = Fingerprint(
    name = "<init>",
    filters = listOf(string("Conversation request started for post ")),
)

/**
 * Singletons implementing [presenter] that [method] reads with `sget-object` and returns right away.
 * A set, because a Remove Ads hook adds another read of the same singleton.
 */
private fun BytecodePatchContext.emptyPresenterReads(method: Method, presenter: String): Set<FieldReference> {
    val instructions = method.implementation?.instructions?.toList() ?: return emptySet()
    return instructions.zipWithNext().mapNotNullTo(mutableSetOf()) { (read, next) ->
        val field = (read as? ReferenceInstruction)?.reference as? FieldReference
        field?.takeIf {
            read.opcode == Opcode.SGET_OBJECT && next.opcode == Opcode.RETURN_OBJECT &&
                it.type == it.definingClass && classDefByOrNull(it.type)?.interfaces?.contains(presenter) == true
        }
    }
}

// URT item presenter factory: (index, item, ...) -> presenter, falling back to the empty presenter.
private fun BytecodePatchContext.urtItemPresenterFingerprint(item: String, presenter: String) = Fingerprint(
    returnType = presenter,
    custom = { method, _ ->
        !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            method.parameterTypeNames().take(2) == listOf("I", item) &&
            emptyPresenterReads(method, presenter).size == 1
    },
)

private class DiscoverMoreHooks(
    val postDetail: MutableMethod,
    val urtItem: MutableMethod,
    val presenter: String,
    val emptyPresenter: String,
)

private fun BytecodePatchContext.discoverMoreHooks(item: String): DiscoverMoreHooks {
    val postDetailClass = uniqueMatch("post-detail component constructor", postDetailComponentFingerprint).classDef
    // Remove Ads may already have wrapped this method; its moved body is a morphe* copy with the same signature.
    val postDetailCandidates = postDetailClass.methods.filter {
        !AccessFlags.ABSTRACT.isSet(it.accessFlags) && it.parameterTypeNames() == listOf("I", item) &&
            !it.name.startsWith("morphe")
    }
    val postDetail = postDetailCandidates.singleOrNull()
        ?: error("[Timeline UI] Expected one (I, $item) presenter in ${postDetailClass.type}, found ${postDetailCandidates.map { it.name }}.")
    val presenter = postDetail.returnType
    if (!AccessFlags.INTERFACE.isSet(classDefBy(presenter).accessFlags)) {
        error("[Timeline UI] Post-detail presenter type $presenter is not an interface.")
    }

    // Remove Ads may have given the post-detail presenter the same empty-presenter return, so search only com.x.urt.
    val urtItem = uniqueMatch("URT item presenter factory", urtItemPresenterFingerprint(item, presenter), packagePrefix = "Lcom/x/urt/").method
    val emptyPresenter = ReferenceUtil.getFieldDescriptor(emptyPresenterReads(urtItem, presenter).single())

    return DiscoverMoreHooks(postDetail, urtItem, presenter, emptyPresenter).also {
        println(
            "[Timeline UI] Resolved post-detail presenter ${ReferenceUtil.getMethodDescriptor(postDetail)}, " +
                "URT item presenter ${ReferenceUtil.getMethodDescriptor(urtItem)}, empty presenter $emptyPresenter.",
        )
    }
}

private fun Method.viewCountInvokeIndex(entry: String) =
    implementation?.instructions?.indexOfFirst { ins ->
        ins.opcode == Opcode.INVOKE_STATIC_RANGE &&
            ((ins as? ReferenceInstruction)?.reference as? MethodReference)
                ?.parameterTypes?.firstOrNull()?.toString() == entry
    } ?: -1

/** The inline action bar item lambda and the models its pill style and view count hooks read. */
private class InlineActionBarItem(
    val method: MutableMethod,
    val pillStyle: String,
    val entryActionType: String,
    val actionType: String,
)

private fun BytecodePatchContext.inlineActionBarItem(): InlineActionBarItem {
    // An enum's static initializer holds each constant's name as a string, so the string index yields the candidates.
    val pillStyles = classDefByStrings("CountedPill").filter { classDef ->
        classDef.superclass == "Ljava/lang/Enum;" &&
            classDef.hasEnumConstant("CountedPill") && classDef.hasEnumConstant("Countless")
    }.map { it.type }
    val pillStyle = pillStyles.singleOrNull()
        ?: error("[Timeline UI] Expected one enum with CountedPill and Countless, found $pillStyles.")

    val entryToString = uniqueMatch(
        "InlineActionEntry toString",
        dataClassToStringFingerprint("InlineActionEntry(actionType="),
    ).originalMethod
    val entryActionType = entryToString.toStringField("InlineActionEntry(actionType=", Opcode.IGET_OBJECT)
    val actionType = entryActionType.type
    if (!classDefBy(actionType).hasEnumConstant("ViewCount")) {
        error("[Timeline UI] Inline action type $actionType has no ViewCount constant.")
    }

    val entry = entryToString.definingClass
    val method = uniqueMatch(
        "inline action bar item lambda",
        Fingerprint(
            name = "invoke",
            filters = listOf(
                fieldAccess(definingClass = pillStyle, name = "CountedPill", type = pillStyle, opcode = Opcode.SGET_OBJECT),
            ),
            custom = { method, classDef ->
                "Lkotlin/jvm/functions/Function2;" in classDef.interfaces && method.viewCountInvokeIndex(entry) >= 0
            },
        ),
        packagePrefix = pillStyle.substringBeforeLast('/') + "/",
    ).method

    return InlineActionBarItem(method, pillStyle, ReferenceUtil.getFieldDescriptor(entryActionType), actionType).also {
        println(
            "[Timeline UI] Resolved inline action bar item ${it.method.definingClass}->${it.method.name}, " +
                "pill style $pillStyle, action type ${it.entryActionType}.",
        )
    }
}


@Suppress("unused")
val twitterTimelineUiPatch = bytecodePatch(
    name = "Timeline UI",
    description = "Customizes timeline surfaces including banners, recommendation carousels, and action buttons.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideBanner by booleanOption(
        key = "hideBanner",
        default = true,
        title = "Hide Banner",
        description = "Hide new post banner.",
        required = false,
    )

    val hidePromoteButton by booleanOption(
        key = "hidePromoteButton",
        default = true,
        title = "Hide Promote Button",
        description = "Hide promote button under self posts.",
        required = false,
    )

    val hideNudgeButton by booleanOption(
        key = "hideNudgeButton",
        default = true,
        title = "Hide Nudge Button",
        description = "Hide follow, subscribe, and follow back buttons on posts.",
        required = false,
    )

    val hideRecommendationItems by booleanOption(
        key = "hideRecommendationItems",
        default = true,
        title = "Hide Recommendation Items",
        description = "Hide recommendation items such as Who to follow and Today's news in timeline, search, and replies.",
        required = false,
    )

    val hideDiscoverMore by booleanOption(
        key = "hideDiscoverMore",
        default = true,
        title = "Hide Discover More",
        description = "Hide Discover more recommendations in tweet reply threads.",
        required = false,
    )

    val hideRecommendedUsers by booleanOption(
        key = "hideRecommendedUsers",
        default = true,
        title = "Hide Recommended Users",
        description = "Hide recommended users popup shown when following someone.",
        required = false,
    )

    val hideLiveThreads by booleanOption(
        key = "hideLiveThreads",
        default = true,
        title = "Hide Live Threads",
        description = "Hide live threads.",
        required = false,
    )

    val hideCommunityNotes by booleanOption(
        key = "hideCommunityNotes",
        default = true,
        title = "Hide Community Notes",
        description = "Hide community notes and birdwatch contributions on posts.",
        required = false,
    )

    val hidePostMetrics by booleanOption(
        key = "hidePostMetrics",
        default = true,
        title = "Hide Post Metrics",
        description = "Hide like, repost, and reply count metrics on timeline posts.",
        required = false,
    )

    val hideViewCount by booleanOption(
        key = "hideViewCount",
        default = true,
        title = "Hide View Count",
        description = "Hide post view count metrics on timeline and post detail.",
        required = false,
    )

    val inlineActionBar by booleanOption(
        key = "inlineActionBar",
        default = true,
        title = "Hide Inline Action Bar",
        description = "Hide inline action buttons across notification timeline.",
        required = false,
    )

    val timelineAddTab by booleanOption(
        key = "timelineAddTab",
        default = true,
        title = "Hide Timeline Add Tab",
        description = "Hide add tab (+) button in home timeline tab header.",
        required = false,
    )

    val timelineTopBar by booleanOption(
        key = "timelineTopBar",
        default = true,
        title = "Hide Timeline Top Bar",
        description = "Hide top bar elements in home and custom timelines.",
        required = false,
    )

    val hideLiveBanner by booleanOption(
        key = "hideLiveBanner",
        default = true,
        title = "Hide Live Banner",
        description = "Hide live and broadcast pill at top of home timeline.",
        required = false,
    )

    execute {
        var patched = 0
        val timelineModels by lazy { timelineModels() }
        val inlineActionBarItem by lazy { inlineActionBarItem() }

        // 1. hideBanner
        if (hideBanner ?: true) {
            val stateMethod = urtShowInstructionsStateFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val initMethod = stateClass.methods.firstOrNull { it.name == "<init>" }
                ?: error("[Timeline UI] Constructor not found in '$stateClassDescriptor'.")

            val instructions = initMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

            val iputIndex = instructions.indexOfFirst { it.opcode == Opcode.IPUT_OBJECT }
            if (iputIndex < 0) {
                error("[Timeline UI] iput-object instruction not found in '$stateClassDescriptor' constructor.")
            }

            val listReg = (instructions[iputIndex] as? TwoRegisterInstruction)?.registerA ?: 1

            val boolRegs = instructions
                .filter { it.opcode == Opcode.IPUT_BOOLEAN }
                .mapNotNull { (it as? TwoRegisterInstruction)?.registerA }
                .distinct()

            val smali = buildString {
                appendLine("invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;")
                appendLine("move-result-object v$listReg")
                for (reg in boolRegs) {
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    appendLine(constIns)
                }
            }.trimEnd()

            initMethod.addInstructions(iputIndex, smali)
            patched++
        } else {
            println("[Timeline UI] Skipped: Hide Banner disabled.")
        }

        // 2. hidePromoteButton
        if (hidePromoteButton ?: true) {
            quickPromoteEligibilityFingerprint.method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """.trimIndent(),
            )
            patched++
        } else {
            println("[Timeline UI] Skipped: Hide Promote Button disabled.")
        }

        // 3. hideNudgeButton
        if (hideNudgeButton ?: true) {
            val stateMethod = focalPostStateFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
                ?: error("[Timeline UI] toString not found in '$stateClassDescriptor'.")

            val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] toString in '$stateClassDescriptor' has no instructions.")

            var followFieldName: String? = null
            var followFieldType: String? = null
            var subscribeFieldName: String? = null
            var statefulFollowFieldName: String? = null

            for (i in toStringInstructions.indices) {
                val ins = toStringInstructions[i]
                if (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) {
                    val str = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
                    when (str) {
                        ", followButtonState=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                    val followField = (nextIns as? ReferenceInstruction)?.reference as? FieldReference
                                    followFieldName = followField?.name
                                    followFieldType = followField?.type
                                    break
                                }
                            }
                        }
                        ", isSubscribeEligible=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_BOOLEAN) {
                                    subscribeFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                    break
                                }
                            }
                        }
                        ", useStatefulFollowButton=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_BOOLEAN) {
                                    statefulFollowFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                    break
                                }
                            }
                        }
                    }
                }
            }

            if (followFieldName == null) {
                error("[Timeline UI] followButtonState field not found in '$stateClassDescriptor'.")
            }
            if (subscribeFieldName == null) {
                error("[Timeline UI] isSubscribeEligible field not found in '$stateClassDescriptor'.")
            }

            val initMethod = stateClass.methods.firstOrNull { it.name == "<init>" }
                ?: error("[Timeline UI] Constructor not found in '$stateClassDescriptor'.")

            val initInstructions = initMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

            val followIputIndex = initInstructions.indexOfFirst { ins ->
                ins.opcode == Opcode.IPUT_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == followFieldName
            }
            if (followIputIndex < 0) {
                error("[Timeline UI] iput-object for '$followFieldName' not found in '$stateClassDescriptor' constructor.")
            }

            val subscribeIputIndex = initInstructions.indexOfFirst { ins ->
                ins.opcode == Opcode.IPUT_BOOLEAN &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == subscribeFieldName
            }
            if (subscribeIputIndex < 0) {
                error("[Timeline UI] iput-boolean for '$subscribeFieldName' not found in '$stateClassDescriptor' constructor.")
            }

            val statefulIputIndex = statefulFollowFieldName?.let { name ->
                initInstructions.indexOfFirst { ins ->
                    ins.opcode == Opcode.IPUT_BOOLEAN &&
                        ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == name
                }
            }

            val subscribeReg = (initInstructions[subscribeIputIndex] as? TwoRegisterInstruction)?.registerA
                ?: error("[Timeline UI] Failed to resolve subscribe register in constructor.")

            // followButtonState intentionally left untouched: zeroing the object
            // field to null crashes post-detail init with an NPE (getClass on null)
            // on flows that reuse the state (e.g. DM-shared tweets). Suppression
            // is achieved via the boolean flags below.
            val targets = mutableListOf(
                subscribeIputIndex to subscribeReg,
            )

            if (statefulIputIndex != null && statefulIputIndex >= 0) {
                val statefulReg = (initInstructions[statefulIputIndex] as? TwoRegisterInstruction)?.registerA
                if (statefulReg != null) {
                    targets.add(statefulIputIndex to statefulReg)
                }
            }

            targets.sortByDescending { it.first }

            for ((index, reg) in targets) {
                val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                initMethod.addInstructions(index, constIns)
                patched++
            }

            // Null follow button state at composable entry instead of in state constructor (which crashed).
            val followButtonState = followFieldType
                ?: error("[Timeline UI] followButtonState field type not found in '$stateClassDescriptor'.")
            val headerMethod = uniqueMatch("focal post header", focalPostHeaderFingerprint(followButtonState), packagePrefix = "Lcom/x/urt/items/post/").method
            val followParamIndex = headerMethod.parameterTypes.indexOfFirst { it.toString() == followButtonState }
            if (followParamIndex < 0) {
                error("[Timeline UI] Follow button state parameter not found in focal post header.")
            }
            println("[Timeline UI] Resolved focal post header ${headerMethod.definingClass}->${headerMethod.name} (follow state $followButtonState).")

            val followParamRegister = headerMethod.parameterTypes
                .take(followParamIndex)
                .sumOf { if (it.toString() == "J" || it.toString() == "D") 2 else 1 }

            headerMethod.addInstructions(0, "const/16 p$followParamRegister, 0x0")
            patched++
        } else {
            println("[Timeline UI] Skipped: Hide Nudge Button disabled.")
        }

        // 4. hideRecommendationItems
        if (hideRecommendationItems ?: true) {
            val models = timelineModels
            timelineItemFilterFingerprint.method.addInstructionsWithLabels(
                0,
                """
                    move-object/from16 v0, p1
                    if-eqz v0, :tlflt_cond_filter_end

                    new-instance v1, Ljava/util/ArrayList;
                    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

                    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                    move-result-object v0

                    :tlflt_loop_start
                    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
                    move-result v2
                    if-eqz v2, :tlflt_loop_end

                    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                    move-result-object v2
                    check-cast v2, ${models.item}

                    instance-of v3, v2, ${models.module}
                    if-eqz v3, :tlflt_cond_add_item

                    move-object v3, v2
                    check-cast v3, ${models.module}

                    # Check 1: entryId contains who-to-follow, who_to_follow, or user_recommendations
                    iget-object v4, v3, ${models.moduleEntryId}
                    if-eqz v4, :tlflt_cond_check_inner

                    const-string v5, "who-to-follow"
                    invoke-virtual {v4, v5}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v5
                    if-nez v5, :tlflt_cond_drop_item

                    const-string v5, "who_to_follow"
                    invoke-virtual {v4, v5}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v5
                    if-nez v5, :tlflt_cond_drop_item

                    const-string v5, "user_recommendations"
                    invoke-virtual {v4, v5}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v5
                    if-nez v5, :tlflt_cond_drop_item

                    :tlflt_cond_check_inner
                    # Check 3: innerContent contains UrtTimelineUser
                    iget-object v4, v3, ${models.moduleInnerContent}
                    if-eqz v4, :tlflt_cond_add_item

                    invoke-interface {v4}, Ljava/util/List;->isEmpty()Z
                    move-result v5
                    if-nez v5, :tlflt_cond_add_item

                    const/4 v5, 0x0
                    invoke-interface {v4, v5}, Ljava/util/List;->get(I)Ljava/lang/Object;
                    move-result-object v4

                    # Defect 2 guard: verify instance-of UrtTimelineModuleItem before casting
                    instance-of v5, v4, ${models.moduleItem}
                    if-eqz v5, :tlflt_cond_add_item

                    check-cast v4, ${models.moduleItem}
                    iget-object v4, v4, ${models.moduleItemItem}
                    instance-of v5, v4, ${models.user}
                    if-nez v5, :tlflt_cond_drop_item

                    :tlflt_cond_add_item
                    invoke-interface {v1, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                    goto :tlflt_loop_start

                    :tlflt_cond_drop_item
                    goto :tlflt_loop_start

                    :tlflt_loop_end
                    move-object/from16 p1, v1

                    :tlflt_cond_filter_end
                    nop
                """.trimIndent(),
            )
            patched++
        } else {
            println("[Timeline UI] Skipped: Hide Recommendation Items disabled.")
        }

        // 5. hideDiscoverMore
        if (hideDiscoverMore ?: true) {
            val models = timelineModels
            val hooks = discoverMoreHooks(models.item)
            // Discover more module entryId has the form "tweetdetailrelatedtweets-<postId>"
            val discoverItemTestSmali = """
                :tluid_cond_check_item
                invoke-interface {v0}, ${models.itemEntryId}
                move-result-object v1
                if-eqz v1, :tluid_cond_continue

                :tluid_cond_check_entry_id
                const-string v2, "relatedtweets"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :tluid_cond_suppress_discover

                goto :tluid_cond_continue

                :tluid_cond_suppress_discover
                sget-object v0, ${hooks.emptyPresenter}
                return-object v0
            """.trimIndent()

            val inlineSmali = """
                move-object/from16 v0, p2
                if-nez v0, :tluid_cond_check_item
                goto :tluid_cond_continue

                $discoverItemTestSmali

                :tluid_cond_continue
                nop
            """.trimIndent()

            val wrapperSmali = """
                move-object/from16 v0, p2
                if-nez v0, :tluid_cond_check_item
                goto :tluid_cond_continue

                $discoverItemTestSmali

                :tluid_cond_continue
                invoke-virtual {p0, p1, p2}, ${hooks.postDetail.definingClass}->morphePostDetailPresenterTimelineUi(I${models.item})${hooks.presenter}
                move-result-object v0
                return-object v0
            """.trimIndent()

            val listFilterSmali = """
                move-object/from16 v0, p1
                if-eqz v0, :tldsc_cond_filter_end

                new-instance v1, Ljava/util/ArrayList;
                invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

                invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v0

                :tldsc_loop_start
                invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
                move-result v2
                if-eqz v2, :tldsc_loop_end

                invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v2
                check-cast v2, ${models.item}

                instance-of v3, v2, ${models.module}
                if-eqz v3, :tldsc_cond_add_item

                move-object v3, v2
                check-cast v3, ${models.module}

                # Null-guard on entryId before contains
                iget-object v4, v3, ${models.moduleEntryId}
                if-eqz v4, :tldsc_cond_add_item

                const-string v5, "relatedtweets"
                invoke-virtual {v4, v5}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v5
                if-nez v5, :tldsc_cond_drop_item

                :tldsc_cond_add_item
                invoke-interface {v1, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                goto :tldsc_loop_start

                :tldsc_cond_drop_item
                goto :tldsc_loop_start

                :tldsc_loop_end
                move-object/from16 p1, v1

                :tldsc_cond_filter_end
                nop
            """.trimIndent()

            val discBefore = patched
            timelineItemFilterFingerprint.method.addInstructionsWithLabels(0, listFilterSmali)
            patched++

            hooks.urtItem.addInstructionsWithLabels(0, inlineSmali)
            patched++

            val wrapped = hooks.postDetail.wrapWithScratchRegisters(
                mutableClassDefBy(hooks.postDetail.definingClass),
                "morphePostDetailPresenterTimelineUi",
                3,
                wrapperSmali,
            )
            if (!wrapped) hooks.postDetail.addInstructionsWithLabels(0, inlineSmali)
            patched++

            if (patched == discBefore) {
                error("[Timeline UI] No hooks applied for Hide Discover More.")
            }
        } else {
            println("[Timeline UI] Skipped: Hide Discover More disabled.")
        }

        // 6. hideRecommendedUsers
        if (hideRecommendedUsers ?: true) {
            val stateMethod = profileFollowRecommendationResponseFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
                ?: error("[Timeline UI] toString not found in '$stateClassDescriptor'.")

            val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] toString in '$stateClassDescriptor' has no instructions.")

            var recommendedUsersFieldName: String? = null

            for (i in toStringInstructions.indices) {
                val ins = toStringInstructions[i]
                if (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) {
                    val str = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (str == ", recommendedUsers=") {
                        for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                            val nextIns = toStringInstructions[j]
                            if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                recommendedUsersFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                break
                            }
                        }
                    }
                }
            }

            if (recommendedUsersFieldName == null) {
                error("[Timeline UI] recommendedUsers field not found in '$stateClassDescriptor'.")
            }

            val initMethods = stateClass.methods.filter { it.name == "<init>" }
            if (initMethods.isEmpty()) {
                error("[Timeline UI] No constructors found in '$stateClassDescriptor'.")
            }

            var usersPatched = 0
            for (initMethod in initMethods) {
                val initInstructions = initMethod.implementation?.instructions?.toList()
                    ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

                val targets = mutableListOf<Pair<Int, Int>>()
                for ((index, ins) in initInstructions.withIndex()) {
                    if (ins.opcode == Opcode.IPUT_OBJECT) {
                        val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                        if (fieldRef?.name == recommendedUsersFieldName) {
                            val reg = (ins as? TwoRegisterInstruction)?.registerA
                                ?: error("[Timeline UI] Failed to resolve value register in constructor.")
                            targets.add(index to reg)
                        }
                    }
                }

                targets.sortByDescending { it.first }

                for ((index, reg) in targets) {
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    initMethod.addInstructions(index, constIns)
                    usersPatched++
                }
            }

            if (usersPatched == 0) {
                error("[Timeline UI] No iput-object instructions found for '$recommendedUsersFieldName' in '$stateClassDescriptor' constructors.")
            }
            patched += usersPatched
        } else {
            println("[Timeline UI] Skipped: Hide Recommended Users disabled.")
        }

        // 7. hideLiveThreads
        if (hideLiveThreads ?: true) {
            val stateMethod = fleetlineApiResponseFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
                ?: error("[Timeline UI] toString not found in '$stateClassDescriptor'.")

            val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] toString in '$stateClassDescriptor' has no instructions.")

            var threadsFieldName: String? = null

            for (i in toStringInstructions.indices) {
                val ins = toStringInstructions[i]
                if (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) {
                    val str = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (str == "FleetlineApiResponse(threads=") {
                        for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                            val nextIns = toStringInstructions[j]
                            if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                threadsFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                break
                            }
                        }
                    }
                }
            }

            if (threadsFieldName == null) {
                error("[Timeline UI] threads field not found in '$stateClassDescriptor'.")
            }

            val initMethods = stateClass.methods.filter { it.name == "<init>" }
            if (initMethods.isEmpty()) {
                error("[Timeline UI] No constructors found in '$stateClassDescriptor'.")
            }

            var threadsPatched = 0
            for (initMethod in initMethods) {
                val initInstructions = initMethod.implementation?.instructions?.toList()
                    ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

                val targets = mutableListOf<Pair<Int, Int>>()
                for ((index, ins) in initInstructions.withIndex()) {
                    if (ins.opcode == Opcode.IPUT_OBJECT) {
                        val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                        if (fieldRef?.name == threadsFieldName) {
                            val reg = (ins as? TwoRegisterInstruction)?.registerA
                                ?: error("[Timeline UI] Failed to resolve value register in constructor.")
                            targets.add(index to reg)
                        }
                    }
                }

                targets.sortByDescending { it.first }

                for ((index, reg) in targets) {
                    val smali = """
                        invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
                        move-result-object v$reg
                    """.trimIndent()
                    initMethod.addInstructions(index, smali)
                    threadsPatched++
                }
            }

            if (threadsPatched == 0) {
                error("[Timeline UI] No iput-object instructions found for '$threadsFieldName' in '$stateClassDescriptor' constructors.")
            }
            patched += threadsPatched
        } else {
            println("[Timeline UI] Skipped: Hide Live Threads disabled.")
        }

        // 9. hideCommunityNotes
        if (hideCommunityNotes ?: true) {
            if (neutralizeFlag(communityNotesFingerprint, "birdwatch_consumption_enabled", "Timeline UI")) {
                patched++
            } else {
                error("[Timeline UI] Failed to neutralize 'birdwatch_consumption_enabled' flag.")
            }

            // Flag only controls the drawer entry; null post model fields to hide notes on posts.
            val classDescriptor = canonicalPostToStringFingerprint.method.definingClass
            val classDef = mutableClassDefBy(classDescriptor)

            val toStringMethod = canonicalPostToStringFingerprint.method
            val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] toString in '$classDescriptor' has no instructions.")

            var communityNoteFieldName: String? = null
            var tombstoneCommunityNoteFieldName: String? = null

            for (i in toStringInstructions.indices) {
                val ins = toStringInstructions[i]
                if (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) {
                    val str = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
                    when (str) {
                        ", communityNote=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                    communityNoteFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                    break
                                }
                            }
                        }
                        ", tombstoneCommunityNote=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                    tombstoneCommunityNoteFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                    break
                                }
                            }
                        }
                    }
                }
            }

            if (communityNoteFieldName == null) {
                error("[Timeline UI] communityNote field not found in '$classDescriptor'.")
            }
            if (tombstoneCommunityNoteFieldName == null) {
                error("[Timeline UI] tombstoneCommunityNote field not found in '$classDescriptor'.")
            }

            var notesNulled = 0
            for (method in classDef.methods) {
                if (method.name != "<init>") continue
                val instructions = method.implementation?.instructions?.toList() ?: continue

                val targetIndices = mutableListOf<Int>()
                for ((index, ins) in instructions.withIndex()) {
                    if (ins.opcode == Opcode.IPUT_OBJECT) {
                        val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                        if (fieldRef?.definingClass == classDescriptor &&
                            (fieldRef.name == communityNoteFieldName || fieldRef.name == tombstoneCommunityNoteFieldName)
                        ) {
                            targetIndices.add(index)
                        }
                    }
                }

                targetIndices.sortByDescending { it }

                for (index in targetIndices) {
                    val reg = (instructions[index] as? TwoRegisterInstruction)?.registerA
                        ?: error("[Timeline UI] Failed to resolve register for iput-object in '$classDescriptor'.")
                    val constIns = when {
                        reg <= 15 -> "const/4 v$reg, 0x0"
                        reg <= 255 -> "const/16 v$reg, 0x0"
                        else -> error("[Timeline UI] Community note register v$reg out of range in '$classDescriptor'.")
                    }
                    method.addInstructions(index, constIns)
                    notesNulled++
                }
            }

            if (notesNulled == 0) {
                error("[Timeline UI] No community note field writes found in '$classDescriptor'.")
            }
            patched++
            println("[Timeline UI] Nulled $notesNulled community note field writes in '$classDescriptor'.")
        } else {
            println("[Timeline UI] Skipped hideCommunityNotes: disabled by patch option.")
        }

        // 13. hidePostMetrics
        if (hidePostMetrics ?: true) {
            val item = inlineActionBarItem
            val method = item.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method ${method.definingClass}->invoke has no instructions.")
            val countedPillIndex = instructions.indexOfFirst { ins ->
                ins.opcode == Opcode.SGET_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == "CountedPill"
            }
            if (countedPillIndex >= 0) {
                val countedPillIns = instructions[countedPillIndex] as OneRegisterInstruction
                val targetReg = countedPillIns.registerA
                method.replaceInstruction(
                    countedPillIndex,
                    "sget-object v$targetReg, ${item.pillStyle}->Countless:${item.pillStyle}",
                )
                patched++
            } else {
                error("[Timeline UI] Failed to find CountedPill instruction in ${method.definingClass}->invoke.")
            }
        } else {
            println("[Timeline UI] Skipped hidePostMetrics: disabled by patch option.")
        }

        // 14. hideViewCount
        if (hideViewCount ?: true) {
            val item = inlineActionBarItem
            val method = item.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method ${method.definingClass}->invoke has no instructions.")
            val entry = item.entryActionType.substringBefore("->")
            val invokeIndex = method.viewCountInvokeIndex(entry)
            if (invokeIndex >= 0) {
                val invokeIns = instructions[invokeIndex]
                val rangeIns = invokeIns as RegisterRangeInstruction
                val startReg = rangeIns.startRegister
                val endReg = startReg + rangeIns.registerCount - 1
                val methodRef = (invokeIns as ReferenceInstruction).reference as MethodReference
                val methodDescriptor = ReferenceUtil.getMethodDescriptor(methodRef)

                method.removeInstruction(invokeIndex)
                method.addInstructionsWithLabels(
                    invokeIndex,
                    """
                        iget-object v3, v$startReg, ${item.entryActionType}
                        sget-object v4, ${item.actionType}->ViewCount:${item.actionType}
                        if-eq v3, v4, :skip_view_count
                        invoke-static/range {v$startReg .. v$endReg}, $methodDescriptor
                        :skip_view_count
                        nop
                    """.trimIndent(),
                )
                patched++
            } else {
                error("[Timeline UI] Failed to find $entry render invoke in ${method.definingClass}->invoke.")
            }
        } else {
            println("[Timeline UI] Skipped hideViewCount: disabled by patch option.")
        }

        // 15. inlineActionBar
        if (inlineActionBar ?: true) {
            if (neutralizeFlag(inlineActionsFingerprint, "android_ntab_display_options_inline_actions_enabled", "Timeline UI")) {
                patched++
            } else {
                error("[Timeline UI] Failed to neutralize 'android_ntab_display_options_inline_actions_enabled' flag.")
            }
        } else {
            println("[Timeline UI] Skipped inlineActionBar: disabled by patch option.")
        }

        // 17. timelineAddTab
        if (timelineAddTab ?: true) {
            val pagerMethod = uniqueMatch("home tab pager", homeTabBarPagerFingerprint).method
            val pagerName = "${pagerMethod.definingClass}->${pagerMethod.name}"
            println("[Timeline UI] Resolved home tab pager $pagerName.")
            val pagerInstructions = pagerMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method $pagerName has no instructions.")
            val pagerConstIdx = pagerInstructions.indexOfFirst { ins ->
                (ins as? NarrowLiteralInstruction)?.narrowLiteral == HOME_TAB_PAGER_ADD_GROUP
            }
            if (pagerConstIdx > 0) {
                val ifEqzIdx = pagerInstructions.subList(0, pagerConstIdx).indexOfLast {
                    it.opcode == Opcode.IF_EQZ
                }
                if (ifEqzIdx >= 0 && pagerConstIdx - ifEqzIdx <= 3) {
                    val reg = (pagerInstructions[ifEqzIdx] as OneRegisterInstruction).registerA
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    requireNotJumpTarget(pagerMethod, ifEqzIdx, "Timeline UI")
                    pagerMethod.addInstructions(ifEqzIdx, constIns)
                    patched++
                } else {
                    error("[Timeline UI] Preceding if-eqz branch not found before const group in $pagerName.")
                }
            } else {
                error("[Timeline UI] Target group constant $HOME_TAB_PAGER_ADD_GROUP not found in $pagerName.")
            }

            val headerMethod = uniqueMatch("home tab header", homeTabBarHeaderFingerprint).method
            val headerName = "${headerMethod.definingClass}->${headerMethod.name}"
            println("[Timeline UI] Resolved home tab header $headerName.")
            val headerInstructions = headerMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method $headerName has no instructions.")
            val headerConstIdx = headerInstructions.indexOfFirst { ins ->
                (ins as? NarrowLiteralInstruction)?.narrowLiteral == HOME_TAB_HEADER_ADD_GROUP
            }
            if (headerConstIdx > 0) {
                val ifEqzIdx = headerInstructions.subList(0, headerConstIdx).indexOfLast {
                    it.opcode == Opcode.IF_EQZ
                }
                if (ifEqzIdx >= 0 && headerConstIdx - ifEqzIdx <= 3) {
                    val reg = (headerInstructions[ifEqzIdx] as OneRegisterInstruction).registerA
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    requireNotJumpTarget(headerMethod, ifEqzIdx, "Timeline UI")
                    headerMethod.addInstructions(ifEqzIdx, constIns)
                    patched++
                } else {
                    error("[Timeline UI] Preceding if-eqz branch not found before const group in $headerName.")
                }
            } else {
                error("[Timeline UI] Target group constant $HOME_TAB_HEADER_ADD_GROUP not found in $headerName.")
            }

            if (neutralizeFlag(homeTopicsTabFlagFingerprint, "hometimeline_pinned_tabs_topics_enabled", "Timeline UI")) {
                patched++
            } else {
                error("[Timeline UI] Failed to neutralize 'hometimeline_pinned_tabs_topics_enabled' flag.")
            }
        } else {
            println("[Timeline UI] Skipped timelineAddTab: disabled by patch option.")
        }

        // 18. timelineTopBar
        if (timelineTopBar ?: true) {
            val method = uniqueMatch("home top bar", homeTopBarFingerprint).method
            val topBarName = "${method.definingClass}->${method.name}"
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method $topBarName has no instructions.")
            // The follow button slot is the only composable lambda singleton the top bar reads from its own class.
            val ownSingletonReads = instructions.withIndex().filter { (_, ins) ->
                ins.opcode == Opcode.SGET_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                        it.definingClass == method.definingClass && it.type.startsWith("Landroidx/compose/runtime/internal/")
                    } == true
            }
            val followButtonFieldIdx = ownSingletonReads.singleOrNull()?.index
                ?: error("[Timeline UI] Expected one follow button lambda read in $topBarName, found ${ownSingletonReads.size}.")
            println("[Timeline UI] Resolved home top bar $topBarName (follow button ${(instructions[followButtonFieldIdx] as ReferenceInstruction).reference}).")
            if (followButtonFieldIdx > 0) {
                val ifEqzIdx = instructions.subList(0, followButtonFieldIdx).indexOfLast {
                    it.opcode == Opcode.IF_EQZ
                }
                if (ifEqzIdx >= 0) {
                    val reg = (instructions[ifEqzIdx] as OneRegisterInstruction).registerA
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    val paramRegs = method.parameterTypes.sumOf { if (it.toString() == "J" || it.toString() == "D") 2 else 1 } + (if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1)
                    val registerCount = method.implementation!!.registerCount
                    if (reg >= registerCount - paramRegs) {
                        method.addInstructions(0, constIns)
                        patched++
                    } else {
                        error("[Timeline UI] Top bar branch register v$reg is not a method parameter in $topBarName.")
                    }
                } else {
                    error("[Timeline UI] Preceding if-eqz branch not found before follow button field in $topBarName.")
                }
            } else {
                error("[Timeline UI] Follow button field not found in $topBarName.")
            }
        } else {
            println("[Timeline UI] Skipped timelineTopBar: disabled by patch option.")
        }

        // 20. hideLiveBanner
        if (hideLiveBanner ?: true) {
            if (neutralizeFlag(liveBannerFingerprint, "x_lite_livestream_pill_enabled", "Timeline UI")) {
                patched++
            } else {
                error("[Timeline UI] Failed to neutralize 'x_lite_livestream_pill_enabled' flag.")
            }
        } else {
            println("[Timeline UI] Skipped hideLiveBanner: disabled by patch option.")
        }

        if (patched > 0) {
            println("[Timeline UI] Applied $patched hooks -> timeline surfaces customized.")
        } else {
            println("[Timeline UI] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
