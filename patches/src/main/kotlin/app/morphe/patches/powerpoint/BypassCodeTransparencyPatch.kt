package app.morphe.patches.powerpoint

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_POWERPOINT
import app.morphe.patches.shared.clearTryBlocks

@Suppress("unused")
val powerPointBypassCodeTransparencyPatch = bytecodePatch(
    name = "Bypass Code Transparency",
    description = "Bypasses the code transparency checks.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_POWERPOINT)

    execute {
        var patched = 0
        codeTransparencyCheckFingerprint.method.apply {
            clearTryBlocks()
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                    invoke-interface {p2}, Lcom/microsoft/office/apphost/CodeTransparencyCheckCallback;->transparencyVerificationSucceeded()V
                    return-void
                """)
            patched++
        }
        println("[Bypass Code Transparency] Applied $patched hooks -> code transparency checks bypassed.")
    }
}
