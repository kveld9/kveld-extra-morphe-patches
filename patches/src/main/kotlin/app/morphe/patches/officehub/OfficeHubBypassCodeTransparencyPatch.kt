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
                const/4 v0, 0x0
                const-string p3, "com.microsoft.android.codetransparencyvalidator"
                invoke-virtual {p1, p3, v0}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object p3
                invoke-interface {p3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
                move-result-object p3
                invoke-virtual {p1}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
                move-result-object v0
                invoke-virtual {p0, p1}, Landroidx/camera/camera2/internal/o1;->A(Landroid/content/Context;)J
                move-result-wide p0
                invoke-interface {p3, v0, p0, p1}, Landroid/content/SharedPreferences${'$'}Editor;->putLong(Ljava/lang/String;J)Landroid/content/SharedPreferences${'$'}Editor;
                invoke-interface {p3}, Landroid/content/SharedPreferences${'$'}Editor;->commit()Z
                invoke-virtual {p2}, Lcom/microsoft/office/tsl/b;->transparencyVerificationSucceeded()V
                return-void
            """)
            patched++
        }

        println("[Bypass Code Transparency] Applied $patched hooks -> code transparency dialog suppressed.")
    }
}
