package app.morphe.patches.powerpoint

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
import org.w3c.dom.Element

private val blockedPermissions = setOf(
    "com.google.android.gms.permission.AD_ID",
    "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
    "android.permission.ACCESS_ADSERVICES_AD_ID",
    "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE",
    "android.permission.ACCESS_ADSERVICES_TOPICS",
)

private val blockedComponents = setOf(
    // Google DataTransport
    "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",

    // HockeyApp Crash & Feedback Reporting
    "com.microsoft.office.hockeyapp.activities.HockeyWebViewActivity",
    "net.hockeyapp.android.FeedbackActivity",
    "net.hockeyapp.android.UpdateActivity",

    // Install & Referrer Tracking
    "com.microsoft.office.asyncdatapointreporting.InstallBroadcastReceiver",
    "com.microsoft.office.officehub.util.OHubBroadcastReceiver",

    // Microsoft Cross-Sell & Campaign Targeting
    "com.microsoft.android.crosssell.CrossSellAgentMarker",
    "com.microsoft.android.crosssell.CrossSellReceiver",
    "com.microsoft.android.crosssell.PackageStateReceiver",
    "com.microsoft.android.crosssell.SelfReplacementReceiver",
    "com.microsoft.android.crosssell.activities.ExcelCrossSellHandlerActivity",
    "com.microsoft.android.crosssell.activities.PdfCrossSellHandlerActivity",
    "com.microsoft.android.crosssell.activities.PowerpointCrossSellHandlerActivity",
    "com.microsoft.android.crosssell.activities.WordCrossSellHandlerActivity",
    "com.microsoft.office.floodgate.launcher.FloodgateDynamicUxActivity",
)

private val optOutMetadata = listOf(
    "firebase_analytics_collection_enabled" to "false",
    "firebase_analytics_collection_deactivated" to "true",
    "firebase_crashlytics_collection_enabled" to "false",
    "firebase_performance_collection_enabled" to "false",
    "firebase_performance_collection_deactivated" to "true",
    "google_analytics_adid_collection_enabled" to "false",
    "google_analytics_default_allow_ad_personalization_signals" to "false",
)

private val powerPointTelemetryResourcePatch = resourcePatch(
    name = "PowerPoint Telemetry Manifest Purge",
    description = "Strips tracking/advertising permissions, disables telemetry, HockeyApp activity and DataTransport components, and injects opt-out metadata in AndroidManifest.xml.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_POWERPOINT)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[PowerPoint Telemetry] Skipped: AndroidManifest.xml not found.")
            return@execute
        }

        var removedPermissions = 0
        var disabledComponents = 0
        var removedRegistrars = 0
        var injectedMetadata = 0

        document(manifestFile.absolutePath).use { doc ->
            val root = doc.documentElement
            val application = root.getElementsByTagName("application").item(0) as? Element

            removedPermissions = root.stripPermissionsWhere { it in blockedPermissions }.size

            if (application != null) {
                disabledComponents = application.disableComponentsByName(*blockedComponents.toTypedArray())

                removedRegistrars = application.removeComponentDiscoveryRegistrarsWhere { name ->
                    (name.contains("TransportRegistrar", ignoreCase = true) ||
                        name.contains("datatransport", ignoreCase = true) ||
                        name.contains("analytics", ignoreCase = true) ||
                        name.contains("measurement", ignoreCase = true) ||
                        name.contains("crashlytics", ignoreCase = true)) &&
                        !name.contains("mlkit", ignoreCase = true)
                }

                optOutMetadata.forEach { (name, value) ->
                    application.setApplicationMetaData(name, value)
                    injectedMetadata++
                }
            }
        }

        println("[PowerPoint Telemetry] Stripped $removedPermissions permissions, disabled $disabledComponents tracking components, removed $removedRegistrars discovery registrars, and injected $injectedMetadata opt-out flags in AndroidManifest.xml.")
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

context(_: BytecodePatchContext)
private fun hookLifecycleCallbacks(hookedMethods: MutableList<String>) {
    val lifecycleClass = "Lcom/microsoft/applications/telemetry/core/LifecycleHandler;"
    val activityParam = listOf("Landroid/app/Activity;")
    val activityBundleParams = listOf("Landroid/app/Activity;", "Landroid/os/Bundle;")

    hookVoidMethod(lifecycleClass, "onActivityCreated", activityBundleParams, hookedMethods, "LifecycleHandler.onActivityCreated")
    hookVoidMethod(lifecycleClass, "onActivityStarted", activityParam, hookedMethods, "LifecycleHandler.onActivityStarted")
    hookVoidMethod(lifecycleClass, "onActivityResumed", activityParam, hookedMethods, "LifecycleHandler.onActivityResumed")
    hookVoidMethod(lifecycleClass, "onActivityPaused", activityParam, hookedMethods, "LifecycleHandler.onActivityPaused")
    hookVoidMethod(lifecycleClass, "onActivityStopped", activityParam, hookedMethods, "LifecycleHandler.onActivityStopped")
    hookVoidMethod(lifecycleClass, "onActivitySaveInstanceState", activityBundleParams, hookedMethods, "LifecycleHandler.onActivitySaveInstanceState")
    hookVoidMethod(lifecycleClass, "onActivityDestroyed", activityParam, hookedMethods, "LifecycleHandler.onActivityDestroyed")
}

context(_: BytecodePatchContext)
private fun hookTelemetryDispatchers(hookedMethods: MutableList<String>) {
    hookVoidMethod(
        definingClass = "Lcom/microsoft/applications/telemetry/AggregatedMetric\$SendAggregationTimerTask;",
        name = "run",
        parameters = emptyList(),
        hookedMethods = hookedMethods,
        label = "SendAggregationTimerTask.run",
    )
    hookVoidMethod(
        definingClass = "Lcom/microsoft/applications/telemetry/core/e0;",
        name = "logFailure",
        parameters = listOf(
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Lcom/microsoft/applications/telemetry/EventProperties;",
        ),
        hookedMethods = hookedMethods,
        label = "e0.logFailure5",
    )
    hookVoidMethod(
        definingClass = "Lcom/microsoft/applications/telemetry/core/e0;",
        name = "logFailure",
        parameters = listOf(
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Lcom/microsoft/applications/telemetry/EventProperties;",
        ),
        hookedMethods = hookedMethods,
        label = "e0.logFailure3",
    )
    hookVoidMethod(
        definingClass = "Lcom/microsoft/applications/telemetry/pal/hardware/HardwareInformationReceiver;",
        name = "onMAMReceive",
        parameters = listOf("Landroid/content/Context;", "Landroid/content/Intent;"),
        hookedMethods = hookedMethods,
        label = "HardwareInformationReceiver.onMAMReceive",
    )
    hookVoidMethod(
        definingClass = "Lcom/microsoft/unified/telemetry/mutsdk/PowerInfoReceiver;",
        name = "onMAMReceive",
        parameters = listOf("Landroid/content/Context;", "Landroid/content/Intent;"),
        hookedMethods = hookedMethods,
        label = "PowerInfoReceiver.onMAMReceive",
    )
}

@Suppress("unused")
val powerPointBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods plus MUTSDK receivers, disables HockeyApp activities and DataTransport components, and strips advertising permissions.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_POWERPOINT)
    dependsOn(powerPointTelemetryResourcePatch)

    execute {
        val hookedMethods = mutableListOf<String>()

        hookLifecycleCallbacks(hookedMethods)
        hookTelemetryDispatchers(hookedMethods)

        println("[PowerPoint Telemetry] Neutralized ${hookedMethods.size} telemetry dispatch methods: ${hookedMethods.joinToString(", ")}.")
    }
}
