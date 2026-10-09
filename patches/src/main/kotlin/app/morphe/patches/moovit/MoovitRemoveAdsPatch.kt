package app.morphe.patches.moovit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_MOOVIT
import com.android.tools.smali.dexlib2.AccessFlags

private val adUnitResolverFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Lcom/moovit/app/ads/AdSource;"),
    strings = listOf("is_interstitial_ads_free_version"),
)

private val moovitAdViewSetSourceFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/ads/MoovitAdView;",
    name = "setAdSource",
    returnType = "V",
    parameters = listOf("Lcom/moovit/app/ads/AdSource;"),
)

private val moovitBannerAdViewSetSourceFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/ads/MoovitBannerAdView;",
    name = "setAdSource",
    returnType = "V",
    parameters = listOf("Lcom/moovit/app/ads/AdSource;"),
)

private val adFreeMenuItemFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/subscription/AdFreeMenuItemFragment;",
    name = "onCreateView",
    returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;"),
)

@Suppress("unused")
val moovitRemoveAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Hides banner and inline ads and neutralizes ad unit ID lookups.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MOOVIT)

    execute {
        var patched = 0

        // 1. Ad unit resolver -> return empty string to suppress all ad unit lookups
        adUnitResolverFingerprint.method.addInstructions(
            0,
            """
                const-string v0, ""
                return-object v0
            """.trimIndent(),
        )
        patched++

        // 2. MoovitAdView -> hide before loading
        moovitAdViewSetSourceFingerprint.method.addInstructions(
            0,
            """
                const/16 v0, 0x8
                invoke-virtual {p0, v0}, Landroid/view/View;->setVisibility(I)V
                return-void
            """.trimIndent(),
        )
        patched++

        // 3. MoovitBannerAdView -> hide before loading
        moovitBannerAdViewSetSourceFingerprint.method.addInstructions(
            0,
            """
                const/16 v0, 0x8
                invoke-virtual {p0, v0}, Landroid/view/View;->setVisibility(I)V
                return-void
            """.trimIndent(),
        )
        patched++

        // 4. AdFreeMenuItemFragment -> hide item view after inflation
        val instructions = adFreeMenuItemFingerprint.method.implementation!!.instructions
        adFreeMenuItemFingerprint.method.addInstructions(
            instructions.lastIndex,
            """
                const/16 v0, 0x8
                invoke-virtual {v2, v0}, Landroid/view/View;->setVisibility(I)V
            """.trimIndent(),
        )
        patched++

        println("[Remove Ads] Applied $patched hooks -> Ads suppressed.")
    }
}
