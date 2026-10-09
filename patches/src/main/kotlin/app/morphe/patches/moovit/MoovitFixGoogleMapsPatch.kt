package app.morphe.patches.moovit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_MOOVIT

private val moovitApplicationOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/MoovitApplication;",
    name = "onCreate",
    returnType = "V",
)

@Suppress("unused")
val moovitFixGoogleMapsPatch = bytecodePatch(
    name = "Fix Google Maps",
    description = "Restores Google Maps rendering by spoofing the original package signature to Google Play Services.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MOOVIT)

    extendWith("extensions/extension.mpe")

    execute {
        moovitApplicationOnCreateFingerprint.method.addInstructions(
            0,
            "invoke-static {}, Lcom/kveld9/morphe/extra/extension/MoovitHelper;->init()V",
        )
        println("[Fix Google Maps] Injected signature spoofing initializer into MoovitApplication.onCreate -> Maps rendering restored.")
    }
}
