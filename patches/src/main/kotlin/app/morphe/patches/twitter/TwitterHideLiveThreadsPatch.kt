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

private val fleetlineApiResponseFingerprint = Fingerprint(
    strings = listOf("FleetlineApiResponse(threads="),
)

@Suppress("unused")
val twitterHideLiveThreadsPatch = bytecodePatch(
    name = "Hide Live Threads",
    description = "Hides live threads",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val stateMethod = fleetlineApiResponseFingerprint.method
        val stateClassDescriptor = stateMethod.definingClass
        val stateClass = mutableClassDefBy(stateClassDescriptor)

        val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
            ?: error("[Hide Live Threads] toString not found in '$stateClassDescriptor'.")

        val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
            ?: error("[Hide Live Threads] toString in '$stateClassDescriptor' has no instructions.")

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
            error("[Hide Live Threads] threads field not found in '$stateClassDescriptor'.")
        }

        val initMethods = stateClass.methods.filter { it.name == "<init>" }
        if (initMethods.isEmpty()) {
            error("[Hide Live Threads] No constructors found in '$stateClassDescriptor'.")
        }

        for (initMethod in initMethods) {
            val initInstructions = initMethod.implementation?.instructions?.toList()
                ?: error("[Hide Live Threads] Constructor in '$stateClassDescriptor' has no instructions.")

            val targets = mutableListOf<Pair<Int, Int>>()
            for ((index, ins) in initInstructions.withIndex()) {
                if (ins.opcode == Opcode.IPUT_OBJECT) {
                    val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                    if (fieldRef?.name == threadsFieldName) {
                        val reg = (ins as? TwoRegisterInstruction)?.registerA
                            ?: error("[Hide Live Threads] Failed to resolve value register in constructor.")
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
                patched++
            }
        }

        if (patched == 0) {
            error("[Hide Live Threads] No iput-object instructions found for '$threadsFieldName' in '$stateClassDescriptor' constructors.")
        }

        println("[Hide Live Threads] Applied $patched hooks -> live threads suppressed.")
    }
}
