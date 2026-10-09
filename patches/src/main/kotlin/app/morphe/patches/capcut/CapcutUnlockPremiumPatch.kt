package app.morphe.patches.capcut

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags

private object SubscribeImplIsVipFingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscribe/SubscribeImpl;",
    name = "isVip",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

private object PayVipImplIsVipFingerprint : Fingerprint(
    definingClass = "Lcom/lemon/editor/proxy/PayVipImpl;",
    name = "isVip",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

private object UserVipInfoIsVipUserFingerprint : Fingerprint(
    definingClass = "Lcom/lm/components/subscribe/config/UserVipInfo;",
    name = "isVipUser",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

private object UserVipInfoGetFlagFingerprint : Fingerprint(
    definingClass = "Lcom/lm/components/subscribe/config/UserVipInfo;",
    name = "getFlag",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

private object UserDetailInfoIsVipUserFingerprint : Fingerprint(
    definingClass = "Lcom/lemon/lv/clipmonetize/data/UserDetailInfo;",
    name = "isVipUser",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

private object UserDetailInfoGetFlagFingerprint : Fingerprint(
    definingClass = "Lcom/lemon/lv/clipmonetize/data/UserDetailInfo;",
    name = "getFlag",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

private object VipUserServiceImplIsVipFingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscription/legacy/service/VipUserServiceImpl;",
    name = "isVip",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

private object SubscribeCloudImplIsVipFingerprint : Fingerprint(
    definingClass = "Lcom/lemon/editor/proxy/SubscribeCloudImpl;",
    name = "isVip",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

@Suppress("unused")
val capcutUnlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Forces CapCut VIP gates to return true (SubscribeImpl, PayVipImpl, UserVipInfo, UserDetailInfo, VipUserServiceImpl, SubscribeCloudImpl). Cloud-gated assets may still fail.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    execute {
        var patched = 0

        fun forceTrue(fp: Fingerprint) {
            fp.method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
            patched++
        }

        forceTrue(SubscribeImplIsVipFingerprint)
        forceTrue(PayVipImplIsVipFingerprint)
        forceTrue(UserVipInfoIsVipUserFingerprint)
        forceTrue(UserVipInfoGetFlagFingerprint)
        forceTrue(UserDetailInfoIsVipUserFingerprint)
        forceTrue(UserDetailInfoGetFlagFingerprint)
        forceTrue(VipUserServiceImplIsVipFingerprint)
        forceTrue(SubscribeCloudImplIsVipFingerprint)

        println("[Unlock Premium] Applied $patched hooks -> VIP gates forced to true.")
    }
}
