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

private val focalPostStateFingerprint = Fingerprint(
    strings = listOf("FocalPostState(timelinePostState="),
)

@Suppress("unused")
val twitterHideNudgeButtonPatch = bytecodePatch(
    name = "Hide Nudge Button",
    description = "Hides follow/subscribe/follow back buttons on posts",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val stateMethod = focalPostStateFingerprint.method
        val stateClassDescriptor = stateMethod.definingClass
        val stateClass = mutableClassDefBy(stateClassDescriptor)

        val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
            ?: error("[Hide Nudge Button] toString not found in '$stateClassDescriptor'.")

        val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
            ?: error("[Hide Nudge Button] toString in '$stateClassDescriptor' has no instructions.")

        var followFieldName: String? = null
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
                                followFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
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
            error("[Hide Nudge Button] followButtonState field not found in '$stateClassDescriptor'.")
        }
        if (subscribeFieldName == null) {
            error("[Hide Nudge Button] isSubscribeEligible field not found in '$stateClassDescriptor'.")
        }

        val initMethod = stateClass.methods.firstOrNull { it.name == "<init>" }
            ?: error("[Hide Nudge Button] Constructor not found in '$stateClassDescriptor'.")

        val initInstructions = initMethod.implementation?.instructions?.toList()
            ?: error("[Hide Nudge Button] Constructor in '$stateClassDescriptor' has no instructions.")

        val followIputIndex = initInstructions.indexOfFirst { ins ->
            ins.opcode == Opcode.IPUT_OBJECT &&
                ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == followFieldName
        }
        if (followIputIndex < 0) {
            error("[Hide Nudge Button] iput-object for '$followFieldName' not found in '$stateClassDescriptor' constructor.")
        }

        val subscribeIputIndex = initInstructions.indexOfFirst { ins ->
            ins.opcode == Opcode.IPUT_BOOLEAN &&
                ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == subscribeFieldName
        }
        if (subscribeIputIndex < 0) {
            error("[Hide Nudge Button] iput-boolean for '$subscribeFieldName' not found in '$stateClassDescriptor' constructor.")
        }

        val statefulIputIndex = statefulFollowFieldName?.let { name ->
            initInstructions.indexOfFirst { ins ->
                ins.opcode == Opcode.IPUT_BOOLEAN &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == name
            }
        }

        val followReg = (initInstructions[followIputIndex] as? TwoRegisterInstruction)?.registerA
            ?: error("[Hide Nudge Button] Failed to resolve follow register in constructor.")
        val subscribeReg = (initInstructions[subscribeIputIndex] as? TwoRegisterInstruction)?.registerA
            ?: error("[Hide Nudge Button] Failed to resolve subscribe register in constructor.")

        val targets = mutableListOf(
            followIputIndex to followReg,
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

        println("[Hide Nudge Button] Applied $patched hooks -> follow/subscribe/follow back buttons suppressed.")
    }
}
