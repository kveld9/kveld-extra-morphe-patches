package app.morphe.patches.capcut

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags

private const val BUILDER_CLASS = "Lcom/ss/android/ugc/effectmanager/EffectConfiguration\$Builder;"
private const val EFFECT_CONFIG_CLASS = "Lcom/ss/android/ugc/effectmanager/EffectConfiguration;"
private const val EFFECT_CONFIG_INTERNAL_CLASS = "Lcom/ss/ugc/effectplatform/EffectConfig;"
private const val PREFS_FILE = "morphe_capcut_effects"
private const val PREFS_KEY = "effect_device_id"

private object EffectConfigurationBuilderPlatformFingerprint : Fingerprint(
    definingClass = BUILDER_CLASS,
    name = "platform",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = BUILDER_CLASS,
    parameters = listOf("Ljava/lang/String;"),
)

private object EffectConfigurationBuilderDeviceIdFingerprint : Fingerprint(
    definingClass = BUILDER_CLASS,
    name = "deviceId",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = BUILDER_CLASS,
    parameters = listOf("Ljava/lang/String;"),
)

private object EffectConfigurationSetDeviceIdFingerprint : Fingerprint(
    definingClass = EFFECT_CONFIG_CLASS,
    name = "setDeviceId",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
)

@Suppress("unused")
val capcutBypassEffectsPatch = bytecodePatch(
    name = "Bypass Effects Region Restriction",
    description = "Fixes effects, transitions, and templates failing to load (ByteDance Shark WAF block) by spoofing device_platform to 'windows' and resetting deviceId in effect requests.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    execute {
        var patched = 0

        val builderClassDef = classDefByOrNull(BUILDER_CLASS)
            ?: error("[Bypass Effects Region Restriction] Class '$BUILDER_CLASS' not found in DEX.")

        val builderContextField = builderClassDef.fields.firstOrNull {
            it.name == "context" && it.type == "Landroid/content/Context;"
        } ?: error("[Bypass Effects Region Restriction] Field 'context:Landroid/content/Context;' not found in '$BUILDER_CLASS'.")

        val effectConfigClassDef = classDefByOrNull(EFFECT_CONFIG_CLASS)
            ?: error("[Bypass Effects Region Restriction] Class '$EFFECT_CONFIG_CLASS' not found in DEX.")

        effectConfigClassDef.fields.firstOrNull {
            it.name == "mEffectConfig" && it.type == EFFECT_CONFIG_INTERNAL_CLASS
        } ?: error("[Bypass Effects Region Restriction] Field 'mEffectConfig:$EFFECT_CONFIG_INTERNAL_CLASS' not found in '$EFFECT_CONFIG_CLASS'.")

        val effectConfigInternalClassDef = classDefByOrNull(EFFECT_CONFIG_INTERNAL_CLASS)
            ?: error("[Bypass Effects Region Restriction] Class '$EFFECT_CONFIG_INTERNAL_CLASS' not found in DEX.")

        effectConfigInternalClassDef.fields.firstOrNull {
            it.name == "e" && it.type == "Ljava/lang/String;"
        } ?: error("[Bypass Effects Region Restriction] Field 'e:Ljava/lang/String;' not found in '$EFFECT_CONFIG_INTERNAL_CLASS'.")

        // Prepend const-string p1, "windows" so both the local platform field
        // and mKNEffectConfigBuilder receive "windows" instead of "android".
        EffectConfigurationBuilderPlatformFingerprint.method.addInstructions(
            0,
            """
                const-string p1, "windows"
            """,
        )
        patched++

        // Inject per-install stable UUID into EffectConfiguration$Builder.deviceId
        // Scratch registers: v0 and v1 (locals overwritten at 0x0007 and 0x0002 before read),
        // plus p1 (incoming parameter discarded and replaced with UUID).
        // p0 (v2, this) is strictly read-only and never clobbered.
        val builderDeviceIdSmali = """
            iget-object v0, p0, $BUILDER_CLASS->${builderContextField.name}:Landroid/content/Context;
            if-nez v0, :cond_has_context
            invoke-static {}, Landroid/app/ActivityThread;->currentApplication()Landroid/app/Application;
            move-result-object v0
            :cond_has_context
            if-eqz v0, :cond_skip
            const-string v1, "$PREFS_FILE"
            const/4 p1, 0x0
            invoke-virtual {v0, v1, p1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
            move-result-object v0
            const-string v1, "$PREFS_KEY"
            const/4 p1, 0x0
            invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
            move-result-object v1
            if-eqz v1, :cond_create
            move-object p1, v1
            goto :cond_skip
            :cond_create
            invoke-static {}, Ljava/util/UUID;->randomUUID()Ljava/util/UUID;
            move-result-object v1
            invoke-virtual {v1}, Ljava/util/UUID;->toString()Ljava/lang/String;
            move-result-object p1
            invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
            move-result-object v0
            const-string v1, "$PREFS_KEY"
            invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences${'$'}Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences${'$'}Editor;
            move-result-object v0
            invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
            :cond_skip
        """.trimIndent()

        EffectConfigurationBuilderDeviceIdFingerprint.method.addInstructions(
            0,
            builderDeviceIdSmali,
        )
        patched++

        // Inject UUID consistency into EffectConfiguration.setDeviceId
        // Scratch registers: v0 (local overwritten at 0x0000 by iget-object v0, v1, ...->mEffectConfig),
        // plus p1 (incoming parameter discarded and replaced with UUID).
        // p0 (v1, this) is strictly read-only and never clobbered.
        val setDeviceIdSmali = """
            iget-object v0, p0, $EFFECT_CONFIG_CLASS->mEffectConfig:$EFFECT_CONFIG_INTERNAL_CLASS
            if-eqz v0, :cond_skip
            iget-object v0, v0, $EFFECT_CONFIG_INTERNAL_CLASS->e:Ljava/lang/String;
            if-eqz v0, :cond_skip
            move-object p1, v0
            :cond_skip
        """.trimIndent()

        EffectConfigurationSetDeviceIdFingerprint.method.addInstructions(
            0,
            setDeviceIdSmali,
        )
        patched++

        println("[Bypass Effects Region Restriction] Applied $patched hooks -> Shark WAF block bypassed.")
    }
}
