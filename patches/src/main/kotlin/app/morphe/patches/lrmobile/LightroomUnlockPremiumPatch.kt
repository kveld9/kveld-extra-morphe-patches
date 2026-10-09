package app.morphe.patches.lrmobile

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private object IsLTPUActiveFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("LTPU test is not enabled for this user, skipping initialization."),
    ),
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    filters = listOf(
        opcode(Opcode.SGET_BOOLEAN, InstructionLocation.MatchFirst()),
        opcode(Opcode.RETURN, InstructionLocation.MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val lightroomUnlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium Features",
    description = "Enables app features locked behind subscription paywalls by activating the internal Limited-Time Premium Unlock (LTPU) gate.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_LRMOBILE)

    execute {
        var patched = 0

        IsLTPUActiveFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
        patched++

        println("[Unlock Premium Features] Applied $patched hooks -> LTPU gate forced to true.")
    }
}
