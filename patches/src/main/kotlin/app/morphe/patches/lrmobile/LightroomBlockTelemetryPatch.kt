package app.morphe.patches.lrmobile

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.setApplicationMetaData
import app.morphe.patches.shared.stripPermissionsWhere
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
            println("[Lightroom Telemetry] Skipped: AndroidManifest.xml not found.")
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

        println("[Lightroom Telemetry] Stripped $removedPermissions permissions and injected $injectedMetadata opt-out flags in AndroidManifest.xml (analytics components preserved for boot stability).")
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

@Suppress("unused")
val lightroomBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Neutralizes Google AppMeasurement and Firebase Crashlytics dispatchers, strips advertising permissions, and injects analytics opt-out flags.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_LRMOBILE)
    dependsOn(lightroomTelemetryResourcePatch)

    execute {
        val hookedMethods = mutableListOf<String>()

        // Google AppMeasurement
        val appMeasurementClass = "Lcom/google/android/gms/measurement/AppMeasurement;"
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

        // Firebase Crashlytics
        val crashlyticsClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;"
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

        println("[Lightroom Telemetry] Neutralized ${hookedMethods.size} telemetry dispatch methods: ${hookedMethods.joinToString(", ")}.")
    }
}
