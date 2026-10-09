package app.morphe.patches.powerpoint

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_POWERPOINT
import app.morphe.patches.shared.clearTryBlocks
import app.morphe.patches.shared.ensureRegisterCount
import app.morphe.patches.shared.replaceWithReturnVoid

@Suppress("unused")
val powerPointUnlock365FamilyPatch = bytecodePatch(
    name = "Unlock 365 Family",
    description = "Unlocks Microsoft 365 Family subscription features locally.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_POWERPOINT)

    execute {
        getLicensingStateFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                sget-object v0, Lcom/microsoft/office/licensing/LicensingState;->ConsumerPremium:Lcom/microsoft/office/licensing/LicensingState;
                return-object v0
            """)
        }
        licenseSessionStateFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                sget-object v0, Lcom/microsoft/office/licensing/LicensingState;->ConsumerPremium:Lcom/microsoft/office/licensing/LicensingState;
                return-object v0
            """)
        }

        hasFamilyPlanFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }
        hasPersonalPlanFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }
        hasPremiumPlanFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }

        licensingFGFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(2)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                const/4 v0, 0x0
                new-array v0, v0, [Lcom/microsoft/office/licensing/OlsEntitlement;
                new-instance v1, Lcom/microsoft/office/licensing/LicenseInfo;
                invoke-direct {v1, v0}, Lcom/microsoft/office/licensing/LicenseInfo;-><init>([Lcom/microsoft/office/licensing/OlsEntitlement;)V
                return-object v1
            """)
        }

        subscriptionStatusYFingerprint.method.replaceWithReturnVoid()

        isPremiumPlanUpsellEnabledFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        }
        isEnterpriseViewOLSCheckEnabledFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        }

        subscriptionDataIsTrialFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        }

        licenseStatusIsPremiumFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }

        accountProfileInfoHasProfileFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }

        storageQuotaCheckFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        }

        accountSwitcherRunnableFingerprint.apply {
            val getActiveIdentityIdx = instructionMatches[1].index
            val moveResultIdx = getActiveIdentityIdx + 1
            method.addInstructionsWithLabels(moveResultIdx + 1, """
                if-nez v12, :has_identity
                return-void
                :has_identity
                nop
            """)
        }
    }
}
