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
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.wrapWithScratchRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Field
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val PATCH_TAG = "[Remove Ads]"
private const val STRING = "Ljava/lang/String;"
private const val URT_PACKAGE = "Lcom/x/urt/"

// Kotlin data-class toString prefixes survive obfuscation and name the timeline model classes.
private const val PROMOTED_METADATA = "TimelinePromotedMetadata("

/** A timeline item that carries promoted metadata itself, or in the model it wraps when [nested]. */
private class AdItem(val label: String, val toStringPrefix: String, val nested: Boolean)

private val AD_ITEMS = listOf(
    AdItem("post", "UrtTimelinePost(", nested = false),
    AdItem("trend", "UrtTimelineTrend(", nested = true),
    AdItem("rtb", "UrtTimelineRtbImageAd(", nested = false),
    AdItem("user", "UrtTimelineUser(", nested = false),
    AdItem("event", "UrtTimelineEventSummary(", nested = true),
)

/** An item of [type] is an ad when the field chain [path] ends in non-null promoted metadata. */
private class AdItemProbe(val label: String, val type: String, val path: List<Field>)

private val FieldReference.smali get() = "$definingClass->$name:$type"

private fun ClassDef.toStringLiterals(): List<String> =
    methods.firstOrNull { it.name == "toString" && it.parameterTypes.isEmpty() && it.returnType == STRING }
        ?.implementation?.instructions
        ?.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
        .orEmpty()

private fun BytecodePatchContext.classesByToStringPrefix(prefixes: List<String>): Map<String, ClassDef> =
    prefixes.associateWith { prefix ->
        val classes = classDefByStrings(prefix, StringComparisonType.STARTS_WITH)
            .filter { classDef -> classDef.toStringLiterals().any { it.startsWith(prefix) } }
        classes.singleOrNull()
            ?: error("$PATCH_TAG Expected one class whose toString starts with \"$prefix\", found ${classes.map { it.type }}.")
    }

private fun ClassDef.metadataField(metadata: String): Field {
    val fields = instanceFields.filter { it.type == metadata }
    return fields.singleOrNull() ?: error("$PATCH_TAG Expected one $metadata field in $type, found ${fields.map { it.name }}.")
}

private fun BytecodePatchContext.adItemProbe(item: AdItem, classDef: ClassDef, metadata: String): AdItemProbe {
    if (!item.nested) return AdItemProbe(item.label, classDef.type, listOf(classDef.metadataField(metadata)))
    val holders = classDef.instanceFields.filter { field ->
        classDefByOrNull(field.type)?.instanceFields?.any { it.type == metadata } == true
    }
    val holder = holders.singleOrNull()
        ?: error("$PATCH_TAG Expected one ${item.label} field holding $metadata in ${classDef.type}, found ${holders.map { it.type }}.")
    return AdItemProbe(item.label, classDef.type, listOf(holder, classDefBy(holder.type).metadataField(metadata)))
}

// The timeline item interface: the only interface every ad item implements.
private fun BytecodePatchContext.itemInterface(probes: List<AdItemProbe>): ClassDef {
    val shared = probes.map { classDefBy(it.type).interfaces.toSet() }.reduce { shared, next -> shared intersect next }
    return classDefBy(shared.singleOrNull() ?: error("$PATCH_TAG Expected one interface shared by every ad item, found $shared."))
}

// Entry id getter of the item interface; promoted entries carry "promoted" in their id.
private fun ClassDef.entryIdGetter(): Method {
    val getters = methods.filter { AccessFlags.ABSTRACT.isSet(it.accessFlags) && it.parameterTypes.isEmpty() && it.returnType == STRING }
    return getters.singleOrNull() ?: error("$PATCH_TAG Expected one String getter in $type, found ${getters.map { it.name }}.")
}

private fun Method.instanceOfTypes(): Set<String> =
    (implementation?.instructions ?: emptyList())
        .filter { it.opcode == Opcode.INSTANCE_OF }
        .mapTo(mutableSetOf()) { ((it as ReferenceInstruction).reference as TypeReference).type }

// Shared URT item presenter factory: (position, item, ...) -> presenter, dispatching on every ad item type.
private fun BytecodePatchContext.urtItemPresenterFingerprint(itemInterface: String, adItems: List<String>) = Fingerprint(
    custom = { method, _ ->
        method.parameterTypes.size > 2 &&
            method.parameterTypes[0] == "I" &&
            method.parameterTypes[1] == itemInterface &&
            method.instanceOfTypes().containsAll(adItems) &&
            classDefByOrNull(method.returnType)?.let { AccessFlags.INTERFACE.isSet(it.accessFlags) } == true
    },
)

// The presenter singleton the factory returns for items it renders as nothing.
private fun BytecodePatchContext.emptyPresenter(factory: Method, presenter: String): FieldReference {
    val singletons = (factory.implementation?.instructions ?: emptyList())
        .filter { it.opcode == Opcode.SGET_OBJECT }
        .map { (it as ReferenceInstruction).reference as FieldReference }
        .filter { it.type == it.definingClass && classDefByOrNull(it.type)?.interfaces?.contains(presenter) == true }
        .distinctBy { it.smali }
    return singletons.singleOrNull() ?: error("$PATCH_TAG Expected one empty $presenter singleton, found ${singletons.map { it.smali }}.")
}

private fun postDetailPresenterFingerprint(itemInterface: String, presenter: String) = Fingerprint(
    returnType = presenter,
    parameters = listOf("I", itemInterface),
    filters = listOf(string("defaultUrtTimelineComponent")),
)

private val googleMobileAdsInitFingerprint = Fingerprint(
    filters = listOf(string("DefaultMobileAdsInitializer")),
)

private val quickPromoteTrayFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lkotlin/jvm/functions/Function0;",
        "Lkotlin/jvm/functions/Function0;",
        "Lcom/x/quickpromote/",
        "Landroidx/compose/runtime/Composer;",
        "I",
    ),
)

// Without an exact string filter, matchAllOrNull checks every method in the app, so sites without one name their package.
private fun BytecodePatchContext.uniqueMatch(site: String, fingerprint: Fingerprint, packagePrefix: String? = null): Match {
    val matches = if (packagePrefix == null) {
        fingerprint.matchAllOrNull().orEmpty()
    } else {
        buildList { classDefForEach { if (it.type.startsWith(packagePrefix)) addAll(fingerprint.matchAllOrNull(it).orEmpty()) } }
    }
    return matches.singleOrNull()
        ?: error("$PATCH_TAG $site: expected one matching method, found ${matches.map { it.originalMethod }}.")
}

private fun AdItemProbe.smali(): String {
    val skip = ":rmad_cond_not_$label"
    val reads = path.joinToString("\nif-eqz v1, $skip\n") { "iget-object v1, v1, ${it.smali}" }
    return """
        instance-of v1, v0, $type
        if-eqz v1, $skip
        move-object v1, v0
        check-cast v1, $type
        $reads
        if-nez v1, :rmad_cond_suppress_ad
        $skip
    """.trimIndent()
}

@Suppress("unused")
val twitterRemoveAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Removes promoted posts, trends, and ads from timeline feeds.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        // Resolve every site before rewriting any, so a missing site leaves the app untouched.
        val models = classesByToStringPrefix(AD_ITEMS.map { it.toStringPrefix } + PROMOTED_METADATA)
        val metadata = models.getValue(PROMOTED_METADATA).type
        val probes = AD_ITEMS.map { adItemProbe(it, models.getValue(it.toStringPrefix), metadata) }
        val itemInterface = itemInterface(probes)
        val entryId = itemInterface.entryIdGetter()
        val urtItemPresenter = uniqueMatch("URT item presenter", urtItemPresenterFingerprint(itemInterface.type, probes.map { it.type }), packagePrefix = URT_PACKAGE)
        val presenter = urtItemPresenter.originalMethod.returnType
        val emptyPresenter = emptyPresenter(urtItemPresenter.originalMethod, presenter)
        val postDetailPresenter = uniqueMatch("Post-detail presenter", postDetailPresenterFingerprint(itemInterface.type, presenter))
        val googleMobileAdsInit = uniqueMatch("Google Mobile Ads init", googleMobileAdsInitFingerprint)
        val quickPromoteTray = uniqueMatch("QuickPromote tray", quickPromoteTrayFingerprint, packagePrefix = "Lcom/x/urt/items/post/quickpromote/")

        println("$PATCH_TAG Promoted metadata: $metadata")
        probes.forEach { probe -> println("$PATCH_TAG Ad item ${probe.label}: ${probe.type} via ${probe.path.joinToString(" -> ") { it.smali }}") }
        println("$PATCH_TAG Item interface: ${itemInterface.type}, entry id: ${entryId.name}()")
        println("$PATCH_TAG Presenter: $presenter, empty presenter: ${emptyPresenter.smali}")
        listOf(urtItemPresenter, postDetailPresenter, googleMobileAdsInit, quickPromoteTray).forEach {
            println("$PATCH_TAG Hook site: ${it.originalMethod}")
        }

        val adItemTestSmali = """
            :rmad_cond_check_item
            ${probes.joinToString("\n\n") { it.smali() }}

            invoke-interface {v0}, ${itemInterface.type}->${entryId.name}()$STRING
            move-result-object v1
            if-eqz v1, :rmad_cond_continue

            :rmad_cond_check_entry_id
            const-string v2, "promoted"
            invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
            move-result v1
            if-nez v1, :rmad_cond_suppress_ad
            goto :rmad_cond_continue

            :rmad_cond_suppress_ad
            sget-object v0, ${emptyPresenter.smali}
            return-object v0
        """.trimIndent()

        val inlineSmali = """
            move-object/from16 v0, p2
            if-nez v0, :rmad_cond_check_item
            goto :rmad_cond_continue

            $adItemTestSmali

            :rmad_cond_continue
            nop
        """.trimIndent()

        // 1. Intercept timeline items before presenter dispatch in the shared URT item presenter factory
        urtItemPresenter.method.addInstructionsWithLabels(0, inlineSmali)
        patched++

        // 2. Intercept thread reply items before presenter dispatch in the post-detail component
        val postDetail = postDetailPresenter.originalMethod
        val wrapperSmali = """
            move-object/from16 v0, p2
            if-nez v0, :rmad_cond_check_item
            goto :rmad_cond_continue

            $adItemTestSmali

            :rmad_cond_continue
            invoke-virtual {p0, p1, p2}, ${postDetail.definingClass}->morphePostDetailPresenterRemoveAds(${postDetail.parameterTypes.joinToString("")})${postDetail.returnType}
            move-result-object v0
            return-object v0
        """.trimIndent()

        val wrapped = postDetailPresenter.method.wrapWithScratchRegisters(
            postDetailPresenter.classDef,
            "morphePostDetailPresenterRemoveAds",
            3,
            wrapperSmali,
        )
        if (!wrapped) postDetailPresenter.method.addInstructionsWithLabels(0, inlineSmali)
        patched++

        // 3. Short-circuit Google Mobile Ads SDK initialization
        googleMobileAdsInit.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """.trimIndent(),
        )
        patched++

        // 4. Suppress QuickPromote booster composable surface
        quickPromoteTray.method.addInstructions(
            0,
            "return-void",
        )
        patched++

        println("$PATCH_TAG Applied $patched hooks -> promoted content and timeline ads suppressed.")
    }
}
