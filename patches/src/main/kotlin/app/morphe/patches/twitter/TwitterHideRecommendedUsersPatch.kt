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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val profileFollowRecommendationResponseFingerprint = Fingerprint(
    strings = listOf("ProfileFollowRecommendationResponse(style="),
)

@Suppress("unused")
val twitterHideRecommendedUsersPatch = bytecodePatch(
    name = "Hide Recommended Users",
    description = "Hides recommended users popup shown when following someone",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val stateMethod = profileFollowRecommendationResponseFingerprint.method
        val stateClassDescriptor = stateMethod.definingClass
        val stateClass = mutableClassDefBy(stateClassDescriptor)

        val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
            ?: error("[Hide Recommended Users] toString not found in '$stateClassDescriptor'.")

        val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
            ?: error("[Hide Recommended Users] toString in '$stateClassDescriptor' has no instructions.")

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
            error("[Hide Recommended Users] recommendedUsers field not found in '$stateClassDescriptor'.")
        }

        val initMethods = stateClass.methods.filter { it.name == "<init>" }
        if (initMethods.isEmpty()) {
            error("[Hide Recommended Users] No constructors found in '$stateClassDescriptor'.")
        }

        for (initMethod in initMethods) {
            val initInstructions = initMethod.implementation?.instructions?.toList()
                ?: error("[Hide Recommended Users] Constructor in '$stateClassDescriptor' has no instructions.")

            val targets = mutableListOf<Pair<Int, Int>>()
            for ((index, ins) in initInstructions.withIndex()) {
                if (ins.opcode == Opcode.IPUT_OBJECT) {
                    val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                    if (fieldRef?.name == recommendedUsersFieldName) {
                        val reg = (ins as? TwoRegisterInstruction)?.registerA
                            ?: error("[Hide Recommended Users] Failed to resolve value register in constructor.")
                        targets.add(index to reg)
                    }
                }
            }

            targets.sortByDescending { it.first }

            for ((index, reg) in targets) {
                val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                initMethod.addInstructions(index, constIns)
                patched++
            }
        }

        if (patched == 0) {
            error("[Hide Recommended Users] No iput-object instructions found for '$recommendedUsersFieldName' in '$stateClassDescriptor' constructors.")
        }

        println("[Hide Recommended Users] Applied $patched hooks -> recommended users popup suppressed.")
    }
}
