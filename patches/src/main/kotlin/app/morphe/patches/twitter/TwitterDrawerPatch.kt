/*
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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

private val drawerDiscountPillFingerprint = Fingerprint(
    strings = listOf("DrawerDiscountPillAlpha"),
)

private val sideBarFingerprint = Fingerprint(
    strings = listOf("android_grok_bot_sidebar_enabled"),
)


private val drawerResourceIds = mutableMapOf<String, Int>()

private const val GET_GROK_STRING = "drawer_get_grok"

private val drawerIconNames = setOf(
    "ic_vector_feedback_stroke",
    "ic_vector_help_circle",
    "ic_vector_person_stroke",
    "ic_vector_verified_stroke",
    "ic_vector_communities_stroke",
    "ic_vector_bookmark_stroke",
    "ic_vector_lists_stroke",
    "ic_vector_spaces_stroke",
    "ic_vector_rocket_stroke",
)

private val drawerResourceNames = mapOf(
    "drawable" to drawerIconNames,
    "string" to setOf(GET_GROK_STRING),
)

// Resolve drawer icon and label IDs by name because X renumbers resources on every release.
private val twitterDrawerIconIdsPatch = resourcePatch {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        val publicXml = get("res/values/public.xml")
        if (!publicXml.exists()) {
            error("[Drawer Items] res/values/public.xml not found; cannot resolve drawer resource IDs.")
        }

        val typeRegex = Regex("""type="([^"]+)"""")
        val nameRegex = Regex("""name="([^"]+)"""")
        val idRegex = Regex("""id="(0x[0-9a-fA-F]+)"""")

        drawerResourceIds.clear()
        publicXml.useLines { lines ->
            for (line in lines) {
                val type = typeRegex.find(line)?.groupValues?.get(1) ?: continue
                val names = drawerResourceNames[type] ?: continue
                val name = nameRegex.find(line)?.groupValues?.get(1) ?: continue
                if (name in names) {
                    val id = idRegex.find(line)?.groupValues?.get(1) ?: continue
                    drawerResourceIds[name] = java.lang.Long.decode(id).toInt()
                }
            }
        }

        val missing = drawerResourceNames.values.flatten().filter { it !in drawerResourceIds }
        if (missing.isNotEmpty()) {
            error("[Drawer Items] Resource '${missing.joinToString(", ")}' not found in public.xml.")
        }

        println("[Drawer Items] Resolved ${drawerResourceIds.size} drawer resource IDs.")
    }
}

private fun drawerResourceId(name: String): Int =
    drawerResourceIds[name] ?: error("[Drawer Items] Unresolved drawer resource ID: $name")

private fun Method.hasLiteral(value: Int) = implementation?.instructions?.any {
    (it as? WideLiteralInstruction)?.wideLiteral == value.toLong()
} == true

// Drawer row composables: (label, icon, onClick, modifier, ...). The plain row has 8 parameters,
// the row with a badge and trailing content has 10.
private fun MutableClass.drawerRowMethod(parameterCount: Int): MutableMethod {
    val candidates = methods.filter { method ->
        method.returnType == "V" &&
            method.parameterTypes.size == parameterCount &&
            method.parameterTypes.first().toString() == "Ljava/lang/String;" &&
            method.parameterTypes[1].startsWith("Lcom/x/icons/")
    }
    return candidates.singleOrNull()
        ?: error("[Drawer Items] Expected one $parameterCount-parameter drawer row in '$type', found ${candidates.map { it.name }}.")
}

// Icon model passed to the drawer rows (com.x.icons.b in 12.34.0 and 12.35.0); its only int field is the drawable ID.
private fun BytecodePatchContext.iconIdField(iconType: String): String {
    val fields = classDefBy(iconType).instanceFields.filter { it.type == "I" }.map { it.name }
    return fields.singleOrNull()
        ?: error("[Drawer Items] Expected one int field in icon model '$iconType', found $fields.")
}


@Suppress("unused")
val twitterDrawerPatch = bytecodePatch(
    name = "Drawer Items",
    description = "Hides individual menu items, badges, and sidebar entries in the navigation drawer.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)
    dependsOn(twitterDrawerIconIdsPatch)

    val hideProfile by booleanOption(
        key = "hideProfile",
        default = true,
        title = "Hide Profile",
        description = "Hide Profile entry in navigation drawer.",
        required = false,
    )

    val hidePremium by booleanOption(
        key = "hidePremium",
        default = true,
        title = "Hide Premium",
        description = "Hide Premium subscription entry and discount badges in navigation drawer (for profile and home upsells, see Remove Premium Upsell).",
        required = false,
    )

    val hideCommunities by booleanOption(
        key = "hideCommunities",
        default = true,
        title = "Hide Communities",
        description = "Hide Communities entry in navigation drawer.",
        required = false,
    )

    val hideBookmarks by booleanOption(
        key = "hideBookmarks",
        default = true,
        title = "Hide Bookmarks",
        description = "Hide Bookmarks entry in navigation drawer.",
        required = false,
    )

    val hideLists by booleanOption(
        key = "hideLists",
        default = true,
        title = "Hide Lists",
        description = "Hide Lists entry in navigation drawer.",
        required = false,
    )

    val hideSpaces by booleanOption(
        key = "hideSpaces",
        default = true,
        title = "Hide Spaces",
        description = "Hide Spaces entry in navigation drawer.",
        required = false,
    )

    val hideCreatorStudio by booleanOption(
        key = "hideCreatorStudio",
        default = true,
        title = "Hide Creator Studio",
        description = "Hide Creator Studio entry in navigation drawer.",
        required = false,
    )

    val hideGetGrok by booleanOption(
        key = "hideGetGrok",
        default = true,
        title = "Hide Grok",
        description = "Hide Grok assistant entry in navigation drawer and sidebar (for Grok navigation tab and post actions, see Navigation UI and Post & Profile UI).",
        required = false,
    )

    val hideHelpCenter by booleanOption(
        key = "hideHelpCenter",
        default = true,
        title = "Hide Help Center",
        description = "Hide Help Center and feedback entries in navigation drawer.",
        required = false,
    )

    execute {
        val discountPillMethod = drawerDiscountPillFingerprint.method
        val classDescriptor = discountPillMethod.definingClass
        val classDef = mutableClassDefBy(classDescriptor)

        val methodE = discountPillMethod
        val getGrokId = drawerResourceId(GET_GROK_STRING)
        val grokCandidates = classDef.methods.filter { it.hasLiteral(getGrokId) }
        val methodO = grokCandidates.singleOrNull()
            ?: error("[Drawer Items] Expected one Get Grok method in '$classDescriptor', found ${grokCandidates.map { it.name }}.")
        val methodP = classDef.drawerRowMethod(8)
        val methodQ = classDef.drawerRowMethod(10)
        val iconType = methodP.parameterTypes[1].toString()
        val iconIdField = "$iconType->${iconIdField(iconType)}:I"
        println(
            "[Drawer Items] Resolved '$classDescriptor': pill=${methodE.name}, grok=${methodO.name}, " +
                "row=${methodP.name}, badgedRow=${methodQ.name}, icon id=$iconIdField.",
        )

        var patched = 0

        // 1. Hide Premium discount pill badge
        if (hidePremium ?: true) {
            methodE.addInstructions(0, "return-void")
            patched++
        }

        // 2. Hide Grok (drawer item and sidebar bot entry)
        if (hideGetGrok ?: true) {
            methodO.addInstructions(0, "return-void")
            patched++
            if (neutralizeFlag(sideBarFingerprint, "android_grok_bot_sidebar_enabled", "Drawer Items")) {
                patched++
            } else {
                error("[Drawer Items] Failed to neutralize 'android_grok_bot_sidebar_enabled' flag.")
            }
        }

        // 3. Hide Help Center / Feedback via method p
        if (hideHelpCenter ?: true) {
            val smaliP = """
                move-object/from16 v0, p1
                if-eqz v0, :cond_keep_p
                iget v1, v0, $iconIdField
                const v2, ${drawerResourceId("ic_vector_feedback_stroke")}
                if-ne v1, v2, :cond_check_p_feedback
                return-void
                :cond_check_p_feedback
                const v2, ${drawerResourceId("ic_vector_help_circle")}
                if-ne v1, v2, :cond_keep_p
                return-void
                :cond_keep_p
            """.trimIndent()
            methodP.addInstructions(0, smaliP)
            patched++
        }

        // 4. Hide item entries via method q
        val qChecks = mutableListOf<String>()
        if (hideProfile ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, ${drawerResourceId("ic_vector_person_stroke")}
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hidePremium ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, ${drawerResourceId("ic_vector_verified_stroke")}
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideCommunities ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, ${drawerResourceId("ic_vector_communities_stroke")}
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideBookmarks ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, ${drawerResourceId("ic_vector_bookmark_stroke")}
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideLists ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, ${drawerResourceId("ic_vector_lists_stroke")}
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideSpaces ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, ${drawerResourceId("ic_vector_spaces_stroke")}
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideCreatorStudio ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, ${drawerResourceId("ic_vector_rocket_stroke")}
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }

        if (qChecks.isNotEmpty()) {
            val smaliQ = """
                move-object/from16 v0, p1
                if-eqz v0, :cond_keep_q
                iget v1, v0, $iconIdField
                ${qChecks.joinToString("\n")}
                :cond_keep_q
            """.trimIndent()
            methodQ.addInstructions(0, smaliQ)
            patched++
        }

        if (patched > 0) {
            println("[Drawer Items] Applied $patched hooks -> drawer items customized.")
        } else {
            println("[Drawer Items] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
