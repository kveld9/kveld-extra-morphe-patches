package app.morphe.patches.officehub

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_OFFICEHUB
import app.morphe.patches.shared.clearTryBlocks

private val codeTransparencyDialogFingerprint = Fingerprint(
    definingClass = "Landroidx/camera/camera2/internal/o1;",
    name = "B",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/microsoft/office/tsl/b;",
        "Z",
    ),
    strings = listOf("com.microsoft.android.codetransparencyvalidator"),
)

@Suppress("unused")
val officeHubBypassCodeTransparencyPatch = bytecodePatch(
    name = "Bypass Code Transparency",
    description = "Skips the code-transparency failure dialog and reports local verification success, unblocking sideloaded installs.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OFFICEHUB)

    execute {
        var patched = 0

        codeTransparencyDialogFingerprint.method.apply {
            clearTryBlocks()
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                invoke-virtual {p2}, Lcom/microsoft/office/tsl/b;->transparencyVerificationSucceeded()V
                return-void
            """)
            patched++
        }

        println("[Bypass Code Transparency] Applied $patched hooks -> code transparency dialog suppressed.")
    }
}
