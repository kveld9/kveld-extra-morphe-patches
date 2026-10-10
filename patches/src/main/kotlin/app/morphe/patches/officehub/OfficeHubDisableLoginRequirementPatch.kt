package app.morphe.patches.officehub

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_OFFICEHUB
import app.morphe.patches.shared.clearTryBlocks
import app.morphe.patches.shared.ensureRegisterCount
import com.android.tools.smali.dexlib2.AccessFlags

private val firstRunN0Fingerprint = Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    strings = listOf("FRE Completed"),
)

private val getIdentityForSignInNameFingerprint = Fingerprint(
    definingClass = "Lcom/microsoft/office/identity/IdentityLiblet;",
    name = "GetIdentityForSignInName",
    returnType = "Lcom/microsoft/office/identity/Identity;",
    parameters = listOf("Ljava/lang/String;", "Z", "Z"),
)

private val checkAndStartSSOIfRequiredFingerprint = Fingerprint(
    definingClass = "Lcom/microsoft/office/docsui/common/FileActivationSSOManager;",
    name = "checkAndStartSSOIfRequired",
    returnType = "Z",
    parameters = listOf("Z"),
)

@Suppress("unused")
val officeHubDisableLoginRequirementPatch = bytecodePatch(
    name = "Disable Login Requirement",
    description = "Removes login requirement and FTUX paywall screens. Cloud-backed features still require sign-in server-side.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OFFICEHUB)

    execute {
        var patched = 0

        firstRunN0Fingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(2)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                invoke-static {}, Lcom/microsoft/office/apphost/OfficeActivityHolder;->GetActivity()Landroid/app/Activity;
                move-result-object v0
                const/4 v1, 0x1
                invoke-static {v0, v1}, Lcom/microsoft/office/officehub/util/OHubSharedPreferences;->setFTUXShown(Landroid/content/Context;Z)V
                return-void
            """)
            patched++
        }

        getIdentityForSignInNameFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
            patched++
        }

        checkAndStartSSOIfRequiredFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x0\nreturn v0")
            patched++
        }

        println("[Disable Login Requirement] Applied $patched hooks -> login requirement and FTUX paywalls removed.")
    }
}
