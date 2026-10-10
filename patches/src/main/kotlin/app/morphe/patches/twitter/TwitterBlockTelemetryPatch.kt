package app.morphe.patches.twitter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.disableComponentsByName
import app.morphe.patches.shared.removeComponentDiscoveryRegistrarsWhere
import app.morphe.patches.shared.setApplicationMetaData
import app.morphe.patches.shared.stripPermissionsWhere

private val twitterTelemetryResourcePatch = resourcePatch(
    name = "X Telemetry Manifest Purge",
    description = "Strips tracking/advertising permissions, disables analytics services and receivers, and injects opt-out metadata in AndroidManifest.xml.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[X Telemetry] AndroidManifest.xml not found - skipping manifest purge.")
            return@execute
        }

        val blockedPermissions = setOf(
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
        )

        val blockedComponents = setOf(
            // Services
            "com.google.android.gms.measurement.AppMeasurementService",
            "com.google.android.gms.measurement.AppMeasurementJobService",
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",

            // Receivers
            "com.google.android.gms.measurement.AppMeasurementReceiver",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
            "com.twitter.analytics.tracking.InstallationReferrer\$OemIntentReceiver",
        )

        val optOutMetadata = listOf(
            "firebase_analytics_collection_enabled" to "false",
            "firebase_analytics_collection_deactivated" to "true",
            "firebase_crashlytics_collection_enabled" to "false",
            "firebase_performance_collection_enabled" to "false",
            "firebase_performance_collection_deactivated" to "true",
            "google_analytics_adid_collection_enabled" to "false",
            "google_analytics_default_allow_ad_personalization_signals" to "false",
        )

        var removedPermissions = 0
        var disabledComponents = 0
        var removedRegistrars = 0
        var injectedMetadata = 0

        document(manifestFile.absolutePath).use { doc ->
            val application = doc.documentElement.getElementsByTagName("application").item(0) as? org.w3c.dom.Element

            // 1. Remove advertising and tracking permissions
            removedPermissions = doc.documentElement.stripPermissionsWhere { it in blockedPermissions }.size

            if (application != null) {
                // 2. Disable verified measurement and tracking components
                disabledComponents = application.disableComponentsByName(*blockedComponents.toTypedArray())

                // 3. Remove discovery meta-data registrars (excluding MLKit, push messaging, and Crashlytics)
                removedRegistrars = application.removeComponentDiscoveryRegistrarsWhere { name ->
                    val matchesKeyword = listOf(
                        "analytics",
                        "measurement",
                        "crashlytics",
                        "perf",
                        "remoteconfig",
                        "sessions",
                        "abt",
                    ).any { name.contains(it, ignoreCase = true) }
                    val isExcluded = name.contains("mlkit", ignoreCase = true) ||
                        name.contains("messaging", ignoreCase = true) ||
                        name.contains("crashlytics", ignoreCase = true)
                    matchesKeyword && !isExcluded
                }

                // 4. Inject declarative SDK opt-out metadata flags
                optOutMetadata.forEach { (name, value) ->
                    application.setApplicationMetaData(name, value)
                    injectedMetadata++
                }
            }
        }

        println("[X Telemetry] Stripped $removedPermissions permissions, disabled $disabledComponents tracking components, removed $removedRegistrars discovery registrars, and injected $injectedMetadata opt-out flags in AndroidManifest.xml.")
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
val twitterBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Neutralizes Google AppMeasurement and Firebase Performance Trace dispatchers, and strips advertising identifiers.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)
    dependsOn(twitterTelemetryResourcePatch)

    execute {
        val hookedMethods = mutableListOf<String>()

        // Google AppMeasurement
        hookVoidMethod(
            definingClass = "Lcom/google/android/gms/measurement/AppMeasurement;",
            name = "logEventInternal",
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/os/Bundle;"),
            hookedMethods = hookedMethods,
            label = "AppMeasurement.logEventInternal",
        )

        // Firebase Performance
        val traceClass = "Lcom/google/firebase/perf/metrics/Trace;"
        hookVoidMethod(
            definingClass = traceClass,
            name = "start",
            parameters = emptyList(),
            hookedMethods = hookedMethods,
            label = "Trace.start",
        )
        hookVoidMethod(
            definingClass = traceClass,
            name = "stop",
            parameters = emptyList(),
            hookedMethods = hookedMethods,
            label = "Trace.stop",
        )
        hookVoidMethod(
            definingClass = traceClass,
            name = "putAttribute",
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
            hookedMethods = hookedMethods,
            label = "Trace.putAttribute",
        )
        hookVoidMethod(
            definingClass = traceClass,
            name = "removeAttribute",
            parameters = listOf("Ljava/lang/String;"),
            hookedMethods = hookedMethods,
            label = "Trace.removeAttribute",
        )
        hookVoidMethod(
            definingClass = traceClass,
            name = "putMetric",
            parameters = listOf("Ljava/lang/String;", "J"),
            hookedMethods = hookedMethods,
            label = "Trace.putMetric",
        )
        hookVoidMethod(
            definingClass = traceClass,
            name = "incrementMetric",
            parameters = listOf("Ljava/lang/String;", "J"),
            hookedMethods = hookedMethods,
            label = "Trace.incrementMetric",
        )

        println("[X Telemetry] Neutralized ${hookedMethods.size} telemetry dispatch methods: ${hookedMethods.joinToString(", ")}.")
    }
}
