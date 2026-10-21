package app.morphe.patches.lrmobile

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.setApplicationMetaData
import app.morphe.patches.shared.stripPermissionsWhere
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element

private val blockedPermissions = setOf(
    "com.google.android.gms.permission.AD_ID",
    "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
    "android.permission.ACCESS_ADSERVICES_AD_ID",
    "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE",
    "android.permission.ACCESS_ADSERVICES_TOPICS",
)

private val optOutMetadata = listOf(
    "firebase_analytics_collection_enabled" to "false",
    "firebase_analytics_collection_deactivated" to "true",
    "firebase_crashlytics_collection_enabled" to "false",
    "firebase_performance_collection_enabled" to "false",
    "firebase_performance_collection_deactivated" to "true",
    "firebase_sessions_enabled" to "false",
    "google_analytics_adid_collection_enabled" to "false",
    "google_analytics_default_allow_ad_personalization_signals" to "false",
)

private val lightroomTelemetryResourcePatch = resourcePatch(
    name = "Lightroom Telemetry Manifest Purge",
    description = "Strips advertising and tracking permissions and injects opt-out metadata in AndroidManifest.xml. Analytics components are intentionally preserved because disabling them prevents application boot.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_LRMOBILE)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[Block Telemetry & Trackers] Skipped: AndroidManifest.xml not found.")
            return@execute
        }

        var removedPermissions = 0
        var injectedMetadata = 0

        // NOTE: analytics/DataTransport components and discovery registrars are intentionally
        // left enabled. Device testing proved each removal breaks application boot in turn
        // (WorkManager-style init coupling); blocking happens at dispatcher call sites instead.
        document(manifestFile.absolutePath).use { doc ->
            val root = doc.documentElement
            val application = root.getElementsByTagName("application").item(0) as? Element

            removedPermissions = root.stripPermissionsWhere { it in blockedPermissions }.size

            if (application != null) {
                optOutMetadata.forEach { (name, value) ->
                    application.setApplicationMetaData(name, value)
                    injectedMetadata++
                }
            }
        }

        println("[Block Telemetry & Trackers] Stripped $removedPermissions permissions and injected $injectedMetadata opt-out flags in AndroidManifest.xml (analytics components preserved for boot stability).")
    }
}

context(_: BytecodePatchContext)
private fun hookVoidMethod(
    definingClass: String,
    name: String,
    parameters: List<String>,
    hookedMethods: MutableList<String>,
    label: String,
) {
    Fingerprint(
        definingClass = definingClass,
        name = name,
        parameters = parameters,
        returnType = "V",
    ).method.apply {
        addInstructions(0, "return-void")
        hookedMethods.add(label)
    }
}

private fun BytecodePatchContext.rewriteStringLiteralInClass(
    definingClass: String,
    targetString: String,
    replacementString: String,
    hookedMethods: MutableList<String>,
    label: String,
) {
    val classDef = classDefByOrNull(definingClass) ?: return
    val mutableClass = mutableClassDefBy(classDef)
    var count = 0
    for (method in mutableClass.methods) {
        val instructions = method.instructionsOrNull?.toList() ?: continue
        for ((index, instruction) in instructions.withIndex().reversed()) {
            val opcode = instruction.opcode
            if (opcode != Opcode.CONST_STRING && opcode != Opcode.CONST_STRING_JUMBO) continue
            val ref = (instruction as? ReferenceInstruction)?.reference as? StringReference ?: continue
            if (ref.string == targetString) {
                val register = (instruction as? OneRegisterInstruction)?.registerA ?: continue
                val op = if (register > 255) "const-string/jumbo" else "const-string"
                method.replaceInstruction(index, "$op v$register, \"${replacementString.replace("\"", "\\\"")}\"")
                count++
            }
        }
    }
    if (count > 0) {
        hookedMethods.add("$label ($count rewrites -> $replacementString)")
    } else {
        println("[Block Telemetry & Trackers] Skipped: $label literal \"$targetString\" absent in $definingClass.")
    }
}

@Suppress("unused")
val lightroomBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Neutralizes Google AppMeasurement, Firebase Crashlytics, Branch attribution, and Adobe Creative SDK analytics dispatchers, redirects Adobe Edge and DTM endpoints to 0.0.0.0, strips advertising permissions, and injects analytics opt-out flags.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_LRMOBILE)
    dependsOn(lightroomTelemetryResourcePatch)

    execute {
        val hookedMethods = mutableListOf<String>()

        // Google AppMeasurement
        val appMeasurementClass = "Lcom/google/android/gms/measurement/AppMeasurement;"
        if (classDefByOrNull(appMeasurementClass) != null) {
            hookVoidMethod(
                definingClass = appMeasurementClass,
                name = "logEventInternal",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/os/Bundle;"),
                hookedMethods = hookedMethods,
                label = "AppMeasurement.logEventInternal",
            )
            hookVoidMethod(
                definingClass = appMeasurementClass,
                name = "logEventInternalNoInterceptor",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/os/Bundle;", "J"),
                hookedMethods = hookedMethods,
                label = "AppMeasurement.logEventInternalNoInterceptor",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: AppMeasurement absent (analytics SDK stripped from input).")
        }

        // Firebase Crashlytics
        val crashlyticsClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;"
        if (classDefByOrNull(crashlyticsClass) != null) {
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "recordException",
                parameters = listOf("Ljava/lang/Throwable;"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.recordException(Throwable)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "recordException",
                parameters = listOf("Ljava/lang/Throwable;", "Lc70/g;"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.recordException(Throwable, CustomKeysAndValues)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "log",
                parameters = listOf("Ljava/lang/String;"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.log",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "sendUnsentReports",
                parameters = emptyList(),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.sendUnsentReports",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setUserId",
                parameters = listOf("Ljava/lang/String;"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setUserId",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setCustomKey",
                parameters = listOf("Ljava/lang/String;", "D"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setCustomKey(double)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setCustomKey",
                parameters = listOf("Ljava/lang/String;", "F"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setCustomKey(float)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setCustomKey",
                parameters = listOf("Ljava/lang/String;", "I"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setCustomKey(int)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setCustomKey",
                parameters = listOf("Ljava/lang/String;", "J"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setCustomKey(long)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setCustomKey",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setCustomKey(String)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setCustomKey",
                parameters = listOf("Ljava/lang/String;", "Z"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setCustomKey(boolean)",
            )
            hookVoidMethod(
                definingClass = crashlyticsClass,
                name = "setCustomKeys",
                parameters = listOf("Lc70/g;"),
                hookedMethods = hookedMethods,
                label = "FirebaseCrashlytics.setCustomKeys",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: FirebaseCrashlytics absent (analytics SDK stripped from input).")
        }

        // Branch Attribution
        val branchSessionBuilderClass = "Lio/branch/referral/c\$i;"
        if (classDefByOrNull(branchSessionBuilderClass) != null) {
            hookVoidMethod(
                definingClass = branchSessionBuilderClass,
                name = "b",
                parameters = emptyList(),
                hookedMethods = hookedMethods,
                label = "Branch.InitSessionBuilder.initSession",
            )
            hookVoidMethod(
                definingClass = branchSessionBuilderClass,
                name = "d",
                parameters = emptyList(),
                hookedMethods = hookedMethods,
                label = "Branch.InitSessionBuilder.reInitSession",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: Branch InitSessionBuilder absent (Branch SDK stripped from input).")
        }

        val branchServerRequestQueueClass = "Lio/branch/referral/n0;"
        if (classDefByOrNull(branchServerRequestQueueClass) != null) {
            hookVoidMethod(
                definingClass = branchServerRequestQueueClass,
                name = "v",
                parameters = listOf("Ljava/lang/String;"),
                hookedMethods = hookedMethods,
                label = "Branch.ServerRequestQueue.processNextQueueItem",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: Branch ServerRequestQueue absent (Branch SDK stripped from input).")
        }

        // Adobe Creative SDK Analytics (cc-api-data.adobe.io)
        val csdkAnalyticsEventClass = "Lcom/adobe/creativesdk/foundation/adobeinternal/analytics/a;"
        if (classDefByOrNull(csdkAnalyticsEventClass) != null) {
            hookVoidMethod(
                definingClass = csdkAnalyticsEventClass,
                name = "l",
                parameters = emptyList(),
                hookedMethods = hookedMethods,
                label = "AdobeInternalAnalytics.postEvent",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: AdobeInternalAnalytics absent (CSDK stripped from input).")
        }

        val csdkAnalyticsQueueClass = "Lcom/adobe/creativesdk/foundation/internal/analytics/e;"
        if (classDefByOrNull(csdkAnalyticsQueueClass) != null) {
            hookVoidMethod(
                definingClass = csdkAnalyticsQueueClass,
                name = "b",
                parameters = listOf("Lcom/adobe/creativesdk/foundation/internal/analytics/c;"),
                hookedMethods = hookedMethods,
                label = "CSDKAnalyticsQueue.enqueueEvent",
            )
            hookVoidMethod(
                definingClass = csdkAnalyticsQueueClass,
                name = "k",
                parameters = listOf("Z"),
                hookedMethods = hookedMethods,
                label = "CSDKAnalyticsQueue.sendEvents",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: CSDKAnalyticsQueue absent (CSDK stripped from input).")
        }

        // Adobe Experience Platform Edge Endpoints (edge.adobedc.net)
        val edgeEndpointClass = "Lcom/adobe/marketing/mobile/EdgeEndpoint;"
        if (classDefByOrNull(edgeEndpointClass) != null) {
            rewriteStringLiteralInClass(
                definingClass = edgeEndpointClass,
                targetString = "edge.adobedc.net",
                replacementString = "0.0.0.0",
                hookedMethods = hookedMethods,
                label = "EdgeEndpoint.edge.adobedc.net",
            )
            rewriteStringLiteralInClass(
                definingClass = edgeEndpointClass,
                targetString = "edge-int.adobedc.net",
                replacementString = "0.0.0.0",
                hookedMethods = hookedMethods,
                label = "EdgeEndpoint.edge-int.adobedc.net",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: EdgeEndpoint absent (AEP Edge SDK stripped from input).")
        }

        // Adobe Dynamic Tag Management (assets.adobedtm.com)
        val dtmConfigStateClass = "Llv/i;"
        if (classDefByOrNull(dtmConfigStateClass) != null) {
            rewriteStringLiteralInClass(
                definingClass = dtmConfigStateClass,
                targetString = "https://assets.adobedtm.com/%s.json",
                replacementString = "https://0.0.0.0/%s.json",
                hookedMethods = hookedMethods,
                label = "DTM.assetsUrl",
            )
        } else {
            println("[Block Telemetry & Trackers] Skipped: ConfigurationStateManager (lv.i) absent (DTM fetch stripped from input).")
        }

        if (hookedMethods.isEmpty()) {
            println("[Block Telemetry & Trackers] WARNING: applied 0 hook(s) -> this feature is NOT active on this APK; its targets moved upstream.")
        } else {
            println("[Block Telemetry & Trackers] Neutralized ${hookedMethods.size} telemetry dispatch methods and endpoints: ${hookedMethods.joinToString(", ")}.")
        }
    }
}
