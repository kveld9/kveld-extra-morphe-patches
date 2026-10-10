/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private val apiTimelineUrlFingerprint = Fingerprint(
    strings = listOf("ApiTimelineUrl(url="),
)

private val graphqlUrlsEntityFingerprint = Fingerprint(
    strings = listOf("GraphqlUrlsEntity(display_url="),
)

@Suppress("unused")
val twitterNoShortenedUrlPatch = bytecodePatch(
    name = "No Shortened URL",
    description = "Expands t.co shortened URLs, removing the click-tracking intermediary",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        // 1. Unshorten TimelineRichText / URT post URLs in ApiTimelineUrl
        val timelineUrlClassDescriptor = apiTimelineUrlFingerprint.method.definingClass
        val timelineUrlClass = mutableClassDefBy(timelineUrlClassDescriptor)

        val timelineUrlInit = timelineUrlClass.methods.firstOrNull { it.name == "<init>" }
            ?: error("[No Shortened URL] Constructor not found in '$timelineUrlClassDescriptor'.")

        val timelineUrlInitInstructions = timelineUrlInit.implementation?.instructions?.toList()
            ?: error("[No Shortened URL] Constructor in '$timelineUrlClassDescriptor' has no instructions.")

        val iputsTimeline = timelineUrlInitInstructions.withIndex().filter { (_, ins) ->
            ins.opcode == Opcode.IPUT_OBJECT
        }
        if (iputsTimeline.size < 2) {
            error("[No Shortened URL] Expected at least 2 iput-object instructions in '$timelineUrlClassDescriptor'.")
        }

        val urlFieldRef = (iputsTimeline[0].value as? ReferenceInstruction)?.reference as? FieldReference
            ?: error("[No Shortened URL] Failed to resolve url field reference in '$timelineUrlClassDescriptor'.")
        val expFieldRef = (iputsTimeline[1].value as? ReferenceInstruction)?.reference as? FieldReference
            ?: error("[No Shortened URL] Failed to resolve expanded_url field reference in '$timelineUrlClassDescriptor'.")

        if (urlFieldRef.type != "Ljava/lang/String;" || expFieldRef.type != "Ljava/lang/String;") {
            error("[No Shortened URL] Unexpected field types in '$timelineUrlClassDescriptor': ${urlFieldRef.type}, ${expFieldRef.type}.")
        }

        val iputUrlIndex = iputsTimeline[0].index
        val regUrl = (iputsTimeline[0].value as? TwoRegisterInstruction)?.registerA
            ?: error("[No Shortened URL] Failed to resolve url register in '$timelineUrlClassDescriptor'.")
        val regExpanded = (iputsTimeline[1].value as? TwoRegisterInstruction)?.registerA
            ?: error("[No Shortened URL] Failed to resolve expanded_url register in '$timelineUrlClassDescriptor'.")

        val smaliTimeline = """
            if-eqz v$regExpanded, :cond_skip_timeline_url
            move-object v$regUrl, v$regExpanded
            :cond_skip_timeline_url
            nop
        """.trimIndent()

        timelineUrlInit.addInstructionsWithLabels(iputUrlIndex, smaliTimeline)
        patched++

        // 2. Unshorten EntitySet URLs in GraphqlUrlsEntity
        val graphqlUrlsClassDescriptor = graphqlUrlsEntityFingerprint.method.definingClass
        val graphqlUrlsClass = mutableClassDefBy(graphqlUrlsClassDescriptor)

        val graphqlUrlsInit = graphqlUrlsClass.methods.firstOrNull { it.name == "<init>" }
            ?: error("[No Shortened URL] Constructor not found in '$graphqlUrlsClassDescriptor'.")

        val graphqlUrlsInitInstructions = graphqlUrlsInit.implementation?.instructions?.toList()
            ?: error("[No Shortened URL] Constructor in '$graphqlUrlsClassDescriptor' has no instructions.")

        val iputsGraphql = graphqlUrlsInitInstructions.withIndex().filter { (_, ins) ->
            ins.opcode == Opcode.IPUT_OBJECT
        }
        if (iputsGraphql.size < 3) {
            error("[No Shortened URL] Expected at least 3 iput-object instructions in '$graphqlUrlsClassDescriptor'.")
        }

        val graphqlExpFieldRef = (iputsGraphql[1].value as? ReferenceInstruction)?.reference as? FieldReference
            ?: error("[No Shortened URL] Failed to resolve expanded_url field reference in '$graphqlUrlsClassDescriptor'.")
        val graphqlUrlFieldRef = (iputsGraphql[2].value as? ReferenceInstruction)?.reference as? FieldReference
            ?: error("[No Shortened URL] Failed to resolve url field reference in '$graphqlUrlsClassDescriptor'.")

        if (graphqlExpFieldRef.type != "Ljava/lang/String;" || graphqlUrlFieldRef.type != "Ljava/lang/String;") {
            error("[No Shortened URL] Unexpected field types in '$graphqlUrlsClassDescriptor': ${graphqlExpFieldRef.type}, ${graphqlUrlFieldRef.type}.")
        }

        val iputGraphqlUrlIndex = iputsGraphql[2].index
        val regGraphqlExpanded = (iputsGraphql[1].value as? TwoRegisterInstruction)?.registerA
            ?: error("[No Shortened URL] Failed to resolve expanded_url register in '$graphqlUrlsClassDescriptor'.")
        val regGraphqlUrl = (iputsGraphql[2].value as? TwoRegisterInstruction)?.registerA
            ?: error("[No Shortened URL] Failed to resolve url register in '$graphqlUrlsClassDescriptor'.")

        val smaliGraphql = """
            if-eqz v$regGraphqlExpanded, :cond_skip_graphql_url
            move-object v$regGraphqlUrl, v$regGraphqlExpanded
            :cond_skip_graphql_url
            nop
        """.trimIndent()

        graphqlUrlsInit.addInstructionsWithLabels(iputGraphqlUrlIndex, smaliGraphql)
        patched++

        println("[No Shortened URL] Applied $patched hooks -> t.co shortened URLs expanded.")
    }
}
