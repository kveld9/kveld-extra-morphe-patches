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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private val recentSearchRepoFingerprint = Fingerprint(
    strings = listOf("search_recent_v3"),
)

@Suppress("unused")
val twitterPauseSearchSuggestionsPatch = bytecodePatch(
    name = "Pause Search Suggestions",
    description = "Stops persisting search suggestions locally without breaking search",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val clinitMethod = recentSearchRepoFingerprint.method
        val repoClassDescriptor = clinitMethod.definingClass
        val repoClass = mutableClassDefBy(repoClassDescriptor)

        val persistMethod = repoClass.methods.firstOrNull { method ->
            method.parameterTypes.size == 3 &&
                method.parameterTypes[0].toString() == repoClassDescriptor &&
                method.returnType == "Ljava/lang/Object;"
        } ?: error("[Pause Search Suggestions] Recent search persist method not found in '$repoClassDescriptor'.")

        val persistInstructions = persistMethod.implementation?.instructions?.toList()
            ?: error("[Pause Search Suggestions] Method '${persistMethod.name}' in '$repoClassDescriptor' has no instructions.")

        val unitFieldRef = persistInstructions
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? FieldReference }
            .firstOrNull { it.definingClass == "Lkotlin/Unit;" }
            ?: error("[Pause Search Suggestions] Unit field reference not found in '${persistMethod.name}'.")

        val unitFieldName = unitFieldRef.name

        persistMethod.addInstructions(
            0,
            """
                sget-object v0, Lkotlin/Unit;->$unitFieldName:Lkotlin/Unit;
                return-object v0
            """.trimIndent(),
        )
        patched++

        println("[Pause Search Suggestions] Applied $patched hooks -> local search suggestions persistence disabled.")
    }
}
