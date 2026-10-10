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
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private val urtShowInstructionsStateFingerprint = Fingerprint(
    strings = listOf("UrtShowInstructionsState(showInstructions="),
)

@Suppress("unused")
val twitterHideBannerPatch = bytecodePatch(
    name = "Hide Banner",
    description = "Hides new post banner",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val stateMethod = urtShowInstructionsStateFingerprint.method
        val stateClassDescriptor = stateMethod.definingClass
        val stateClass = mutableClassDefBy(stateClassDescriptor)

        val initMethod = stateClass.methods.firstOrNull { it.name == "<init>" }
            ?: error("[Hide Banner] Constructor not found in '$stateClassDescriptor'.")

        val instructions = initMethod.implementation?.instructions?.toList()
            ?: error("[Hide Banner] Constructor in '$stateClassDescriptor' has no instructions.")

        val iputIndex = instructions.indexOfFirst { it.opcode == Opcode.IPUT_OBJECT }
        if (iputIndex < 0) {
            error("[Hide Banner] iput-object instruction not found in '$stateClassDescriptor' constructor.")
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

        println("[Hide Banner] Applied $patched hooks -> new post banner suppressed.")
    }
}
