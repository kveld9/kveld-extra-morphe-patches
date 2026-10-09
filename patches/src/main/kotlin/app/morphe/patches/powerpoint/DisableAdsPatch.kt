package app.morphe.patches.powerpoint

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_POWERPOINT
import app.morphe.patches.shared.clearTryBlocks
import app.morphe.patches.shared.ensureRegisterCount

@Suppress("unused")
val powerPointDisableAdsPatch = bytecodePatch(
    name = "Disable Ads",
    description = "Nullifies advertising IDs used for ad measurement.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_POWERPOINT)

    execute {
        getAIFAFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                const-string v0, ""
                return-object v0
            """)
        }
        getAppSetIdFingerprint.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                const-string v0, ""
                return-object v0
            """)
        }
    }
}
