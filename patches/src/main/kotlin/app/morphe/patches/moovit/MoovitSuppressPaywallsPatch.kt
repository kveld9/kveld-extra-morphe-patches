package app.morphe.patches.moovit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.shared.Constants.COMPATIBILITY_MOOVIT
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val blockPaywallGateFingerprint = Fingerprint(
    name = "a",
    returnType = "Z",
    parameters = listOf("Lcom/moovit/core/components/MoovitComponentActivity;"),
    filters = listOf(
        string("block_paywall"),
    ),
)

private val blockPaywallActivityOnReadyFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/plus/paywall/BlockPaywallActivity;",
    name = "onReady",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private val moovitPlusOnboardingActivityFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/plus/onboarding/MoovitPlusOnboardingActivity;",
    name = "onReady",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private val moovitPlusActivityOnReadyFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/plus/MoovitPlusActivity;",
    name = "onReady",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private val moovitPlusHelpCenterMenuItemFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/plus/MoovitPlusHelpCenterMenuItemFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

private val moovitPlusMenuItemFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/plus/MoovitPlusMenuItemFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

private val promoCellFragmentOnViewCreatedFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/subscription/MoovitSubscriptionsPromoCellFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

private val moovitPlusPackagePopupFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/plus/popup/MoovitPlusPackagePopupFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

private val moovitPlusPurchaseOffersFragmentFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/plus/MoovitPlusPurchaseOffersFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

private val myMoovitPlusGoPremiumCardFingerprint = Fingerprint(
    name = "emit",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "L"),
    strings = listOf("goPremiumCard"),
)

@Suppress("unused")
val moovitSuppressPaywallsPatch = bytecodePatch(
    name = "Suppress Paywalls",
    description = "Suppresses subscription paywalls, onboarding upgrade dialogs, and promotional cards.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MOOVIT)

    execute {
        var patched = 0

        // 1. Paywall gate -> return false (paywall disabled)
        blockPaywallGateFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """.trimIndent(),
        )
        patched++

        // 2. BlockPaywallActivity -> dynamically resolve skip method and relaunch calling activity
        val blockPaywallActivity = mutableClassDefBy("Lcom/moovit/app/plus/paywall/BlockPaywallActivity;")
        val blockPaywallSkip = blockPaywallActivity.methods.singleOrNull { method ->
            AccessFlags.PRIVATE.isSet(method.accessFlags) &&
                method.parameterTypes.isEmpty() && method.returnType == "V" &&
                method.implementation?.instructions?.any { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                        ?.name == "getActivityToStartOnFinish"
                } == true
        } ?: error("Moovit: BlockPaywall skip method referencing getActivityToStartOnFinish not found.")

        blockPaywallActivityOnReadyFingerprint.method.addInstructions(
            0,
            """
                invoke-direct {p0}, Lcom/moovit/app/plus/paywall/BlockPaywallActivity;->${blockPaywallSkip.name}()V
                return-void
            """.trimIndent(),
        )
        patched++

        // 3. Onboarding activity -> dynamically resolve skip method and relaunch calling activity
        val onboardingActivity = mutableClassDefBy("Lcom/moovit/app/plus/onboarding/MoovitPlusOnboardingActivity;")
        val onboardingSkip = onboardingActivity.methods.singleOrNull { method ->
            method.parameterTypes.isEmpty() && method.returnType == "V" &&
                method.implementation?.instructions?.any { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                        ?.string == "activity_to_start_on_finish"
                } == true
        } ?: error("Moovit: onboarding skip method referencing 'activity_to_start_on_finish' not found.")

        moovitPlusOnboardingActivityFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lcom/moovit/app/plus/onboarding/MoovitPlusOnboardingActivity;->${onboardingSkip.name}()V
                return-void
            """.trimIndent(),
        )
        patched++

        // 4. MoovitPlusActivity -> exit immediately
        moovitPlusActivityOnReadyFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
            """.trimIndent(),
        )
        patched++

        // 5. Help center menu item -> suppress view setup
        moovitPlusHelpCenterMenuItemFingerprint.method.addInstructions(0, "return-void")
        patched++

        // 6. Plus menu item -> set GONE
        moovitPlusMenuItemFingerprint.method.addInstructions(
            0,
            """
                const/16 v0, 0x8
                invoke-virtual {p1, v0}, Landroid/view/View;->setVisibility(I)V
                return-void
            """.trimIndent(),
        )
        patched++

        // 7. Promo cell -> set GONE
        promoCellFragmentOnViewCreatedFingerprint.method.addInstructions(
            0,
            """
                const/16 v0, 0x8
                invoke-virtual {p1, v0}, Landroid/view/View;->setVisibility(I)V
                return-void
            """.trimIndent(),
        )
        patched++

        // 8. Package popup -> dismiss dialog
        moovitPlusPackagePopupFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Landroidx/fragment/app/i;->dismiss()V
                return-void
            """.trimIndent(),
        )
        patched++

        // 9. Purchase offers fragment -> return early
        moovitPlusPurchaseOffersFragmentFingerprint.method.addInstructions(0, "return-void")
        patched++

        // 10. Go Premium card -> replace VISIBLE branch move with GONE branch move
        val goPremiumInstructions = myMoovitPlusGoPremiumCardFingerprint.method.implementation!!.instructions
        val goPremiumStringIndex = goPremiumInstructions.indexOfFirst { instruction ->
            (instruction as? ReferenceInstruction)?.reference.toString().contains("goPremiumCard")
        }
        check(goPremiumStringIndex > 0) { "goPremiumCard marker instruction not found." }

        val setVisibilityOffset = goPremiumInstructions
            .drop(goPremiumStringIndex)
            .indexOfFirst { instruction ->
                (instruction as? ReferenceInstruction)?.reference.toString().contains("Landroid/view/View;->setVisibility(I)V")
            }
        check(setVisibilityOffset > 0) { "setVisibility invocation not found after goPremiumCard marker." }

        myMoovitPlusGoPremiumCardFingerprint.method.replaceInstruction(
            goPremiumStringIndex + setVisibilityOffset - 3,
            "move v8, v2",
        )
        patched++

        println("[Suppress Paywalls] Applied $patched hooks -> Paywalls and upgrade dialogs suppressed.")
    }
}
