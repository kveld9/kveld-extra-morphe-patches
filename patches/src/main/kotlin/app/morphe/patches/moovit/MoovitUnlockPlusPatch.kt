package app.morphe.patches.moovit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.shared.Constants.COMPATIBILITY_MOOVIT
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private val subscriptionStateFingerprint = Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("subscribed_skus"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
)

private val subscriptionPackageStateFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/subscription/premium/packages/a;",
    name = "b",
    returnType = "Lcom/moovit/app/subscription/premium/packages/SubscriptionPackageState;",
    parameters = emptyList(),
)

private val safeRideCalculateStateFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/subscription/premium/packages/safety/b;",
    name = "a",
    returnType = "Ljava/lang/Enum;",
    parameters = listOf("Lkotlin/coroutines/jvm/internal/ContinuationImpl;"),
)

private val favoriteLocationAddressSearchFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/app/home/dashboard/FavoriteLocationEditorActivity;",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("favorites_editor"),
    ),
)

@Suppress("unused")
val moovitUnlockPlusPatch = bytecodePatch(
    name = "Unlock Moovit+",
    description = "Unlocks Moovit+ premium subscription features locally, including Safe Ride and address search in favorites.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MOOVIT)

    execute {
        var patched = 0

        // 1. Subscription state gate -> always subscribed
        subscriptionStateFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x1
                return p0
            """.trimIndent(),
        )
        patched++

        // 2. Package state -> always ACTIVE
        subscriptionPackageStateFingerprint.method.addInstructions(
            0,
            """
                sget-object p0, Lcom/moovit/app/subscription/premium/packages/SubscriptionPackageState;->ACTIVE:Lcom/moovit/app/subscription/premium/packages/SubscriptionPackageState;
                return-object p0
            """.trimIndent(),
        )
        patched++

        // 3. SafeRide calculate state -> always ACTIVE
        safeRideCalculateStateFingerprint.method.addInstructions(
            0,
            """
                sget-object p0, Lcom/moovit/app/subscription/premium/packages/SubscriptionPackageState;->ACTIVE:Lcom/moovit/app/subscription/premium/packages/SubscriptionPackageState;
                return-object p0
            """.trimIndent(),
        )
        patched++

        // 4. Favorite location address search -> flip address provider flag (c = true)
        val instructions = favoriteLocationAddressSearchFingerprint.method.implementation!!.instructions
        val callbackInitIndex = instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString().contains("Lcom/moovit/app/search/AppSearchLocationCallback;-><init>")
        }
        check(callbackInitIndex > 0) { "AppSearchLocationCallback constructor invocation not found." }

        val constZeroIndex = instructions.subList(0, callbackInitIndex).indexOfLast {
            it.opcode == Opcode.CONST_4 && (it as? OneRegisterInstruction)?.registerA == 5
        }
        check(constZeroIndex >= 0) { "const/4 v5, 0x0 instruction not found before callback constructor." }

        favoriteLocationAddressSearchFingerprint.method.replaceInstruction(
            constZeroIndex,
            "const/4 v5, 0x1",
        )
        patched++

        println("[Unlock Moovit+] Applied $patched hooks -> Moovit+ premium features unlocked.")
    }
}
