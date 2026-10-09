package app.morphe.patches.moovit

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.ANDROID_XML_NAMESPACE
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.childrenNamed
import app.morphe.patches.shared.disableComponentsByName
import app.morphe.patches.shared.removeChildren
import app.morphe.patches.shared.setApplicationMetaData
import app.morphe.patches.shared.stripPermissionsWhere
import org.w3c.dom.Element

private val moovitTelemetryResourcePatch = resourcePatch(
    name = "Moovit Telemetry Manifest Purge",
    description = "Strips advertising and tracking permissions, disables analytics services, providers, and receivers, and injects opt-out metadata in AndroidManifest.xml.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_MOOVIT)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[Block Telemetry & Trackers] Skipped: AndroidManifest.xml not found.")
            return@execute
        }

        val blockedPermissions = setOf(
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
        )

        val blockedComponents = setOf(
            // Providers
            "com.facebook.FacebookContentProvider",
            "com.fairtiq.sdk.internal.telemetry.processTime.StartupTimeProvider",
            "com.facebook.ads.AudienceNetworkContentProvider",
            "com.facebook.internal.FacebookInitProvider",
            "com.vungle.ads.VungleProvider",

            // Services
            "com.fairtiq.sdk.internal.services.tracking.TrackingServiceImpl",
            "com.google.android.gms.measurement.AppMeasurementService",
            "com.google.android.gms.measurement.AppMeasurementJobService",
            "com.google.firebase.sessions.SessionLifecycleService",
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",

            // Receivers
            "com.usebutton.sdk.internal.receivers.LocaleChangedReceiver",
            "com.usebutton.sdk.internal.receivers.InstallNotificationReceiver",
            "com.google.android.gms.measurement.AppMeasurementReceiver",
            "com.facebook.CurrentAccessTokenExpirationBroadcastReceiver",
            "com.facebook.AuthenticationTokenManager\$CurrentAuthenticationTokenChangedBroadcastReceiver",
            "com.braze.BrazeFlushPushDeliveryReceiver",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
        )

        val optOutMetadata = listOf(
            "firebase_analytics_collection_enabled" to "false",
            "firebase_analytics_collection_deactivated" to "true",
            "firebase_crashlytics_collection_enabled" to "false",
            "firebase_performance_collection_enabled" to "false",
            "firebase_performance_collection_deactivated" to "true",
            "firebase_sessions_enabled" to "false",
            "google_analytics_adid_collection_enabled" to "false",
            "google_analytics_default_allow_ad_personalization_signals" to "false",
        )

        var removedPermissions = 0
        var disabledComponents = 0
        var removedInitializers = 0
        var injectedMetadata = 0

        document(manifestFile.absolutePath).use { doc ->
            val root = doc.documentElement
            val application = root.getElementsByTagName("application").item(0) as? Element

            // 1. Remove advertising and tracking permissions
            removedPermissions = root.stripPermissionsWhere { it in blockedPermissions }.size

            if (application != null) {
                // 2. Disable verified tracking components
                disabledComponents = application.disableComponentsByName(*blockedComponents.toTypedArray())

                // 3. Remove ButtonSDK startup initializer
                application.childrenNamed("provider")
                    .filter {
                        val name = it.getAttribute("android:name").ifBlank { it.getAttributeNS(ANDROID_XML_NAMESPACE, "name") }
                        name == "androidx.startup.InitializationProvider"
                    }
                    .forEach { provider ->
                        val toRemove = provider.childrenNamed("meta-data").filter {
                            val name = it.getAttribute("android:name").ifBlank { it.getAttributeNS(ANDROID_XML_NAMESPACE, "name") }
                            name.contains("ButtonSdkInitializer", ignoreCase = true)
                        }
                        provider.removeChildren(toRemove)
                        removedInitializers += toRemove.size
                    }

                // 4. Inject declarative SDK opt-out metadata flags
                optOutMetadata.forEach { (name, value) ->
                    application.setApplicationMetaData(name, value)
                    injectedMetadata++
                }
            }
        }

        println(
            "[Block Telemetry & Trackers] Stripped $removedPermissions permissions, " +
                "disabled $disabledComponents tracking components, " +
                "removed 0 discovery registrars (DI graph coupled; suppression via flags + disabled services), " +
                "removed $removedInitializers startup initializers, " +
                "and injected $injectedMetadata opt-out flags in AndroidManifest.xml."
        )
    }
}

@Suppress("unused")
val moovitBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Disables analytics and tracking services, providers, and receivers, and strips advertising permissions.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_MOOVIT)
    dependsOn(moovitTelemetryResourcePatch)

    execute {
        println(
            "[Block Telemetry & Trackers] Neutralized 0 bytecode dispatch methods " +
                "(no concrete analytics dispatch class in base DEX; manifest purge and opt-out flags carry the blocking)."
        )
    }
}
