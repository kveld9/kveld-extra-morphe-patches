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
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val DM_CALL_STATE_PACKAGE = "Lcom/x/dm/api/av/"

// Conversation header actions composable: (chat state, show calls, ...). It is the only one with this
// signature that branches on the audio/video call state.
private val dmCallButtonsFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Lcom/x/dms/components/chat/", "Z", "Lkotlin/jvm/functions/Function0;",
        "Lkotlin/jvm/functions/Function1;", "Landroidx/compose/ui/Modifier;",
        "Landroidx/compose/runtime/Composer;", "I",
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.INSTANCE_OF &&
                ((instruction as ReferenceInstruction).reference as TypeReference).type.startsWith(DM_CALL_STATE_PACKAGE)
        } == true
    },
)

@Suppress("unused")
val twitterDmDebloatPatch = bytecodePatch(
    name = "DM Debloat",
    description = "Hides call buttons and composer actions in direct message conversations.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideDmCalls by booleanOption(
        key = "hideDmCalls",
        default = true,
        title = "Hide DM Calls",
        description = "Hide audio and video call buttons in direct message conversations (warning: disables outgoing direct message calls).",
        required = false,
    )

    execute {
        var patched = 0

        // Call buttons are hidden by forcing the composable's show-calls parameter to false.
        if (hideDmCalls ?: true) {
            val method = dmCallButtonsFingerprint.method
            method.addInstructions(0, "const/16 p1, 0x0")
            patched++
            println("[DM Debloat] Hid audio and video call buttons in '${method.definingClass}->${method.name}'.")
        } else {
            println("[DM Debloat] Skipped call buttons: hideDmCalls disabled by patch option.")
        }

        if (patched > 0) {
            println("[DM Debloat] Applied $patched hooks -> direct message conversation surfaces debloated.")
        } else {
            println("[DM Debloat] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
