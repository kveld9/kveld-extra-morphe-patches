package app.morphe.patches.capcut

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.childrenNamed
import app.morphe.patches.shared.disableComponentsByName
import app.morphe.patches.shared.removeComponentDiscoveryRegistrarsWhere
import app.morphe.patches.shared.setApplicationMetaData
import app.morphe.patches.shared.stripPermissionsWhere
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val capcutTelemetryResourcePatch = resourcePatch(
    name = "CapCut Telemetry Manifest Purge",
    description = "Strips tracking/advertising permissions, disables measurement, attribution, and analytics components (Vungle, BDInstall, AppLog, LogService), and injects opt-out metadata in AndroidManifest.xml.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

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
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
            "com.applovin.array.apphub.permission.BIND_APPHUB_SERVICE",
        )

        val blockedComponents = setOf(
            // Providers
            "com.applovin.sdk.AppLovinInitProvider",
            "com.google.android.gms.ads.MobileAdsInitProvider",
            "com.facebook.internal.FacebookInitProvider",
            "com.vungle.ads.VungleProvider",
            "com.bytedance.bdinstall.BDInstallProvider",

            // Services
            "com.google.android.gms.measurement.AppMeasurementService",
            "com.google.android.gms.measurement.AppMeasurementJobService",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
            "com.applovin.impl.adview.activity.FullscreenAdService",
            "com.ss.android.message.log.LogService",
            "com.bytedance.sdk.openadsdk.multipro.aidl.BinderPoolService",

            // Receivers
            "com.google.android.gms.measurement.AppMeasurementReceiver",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
            "com.appsflyer.SingleInstallBroadcastReceiver",
            "com.bytedance.applog.collector.Collector",

            // Activities
            "com.ss.android.deviceregister.AActivity",
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
        var removedRegistrars = 0
        var injectedMetadata = 0

        document(manifestFile.absolutePath).use { doc ->
            val root = doc.documentElement
            val application = root.getElementsByTagName("application").item(0) as? org.w3c.dom.Element

            // 1. Remove advertising and tracking permissions
            removedPermissions = root.stripPermissionsWhere { it in blockedPermissions }.size

            if (application != null) {
                // Collect dynamic exact names for AppLovin/Vungle activities
                val dynamicActivities = application.childrenNamed("activity")
                    .map { it.getAttribute("android:name").ifBlank { it.getAttributeNS("http://schemas.android.com/apk/res/android", "name") } }
                    .filter { name ->
                        name.contains("AppLovinFullscreen", ignoreCase = true) ||
                        name.contains("AppLovinWebView", ignoreCase = true) ||
                        name.contains("MaxDebugger", ignoreCase = true) ||
                        name.contains("MaxCreativeDebugger", ignoreCase = true) ||
                        name.contains("VungleActivity", ignoreCase = true)
                    }
                    .toSet()

                val toDisable = blockedComponents + dynamicActivities

                // 2. Disable verified measurement and tracking components
                disabledComponents = application.disableComponentsByName(*toDisable.toTypedArray())

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

        println("[Block Telemetry & Trackers] Stripped $removedPermissions permissions, disabled $disabledComponents tracking components, removed $removedRegistrars discovery registrars, and injected $injectedMetadata opt-out flags in AndroidManifest.xml.")
    }
}

private fun isSafeVoidDispatcher(method: Method): Boolean {
    if (method.implementation == null) {
        return false
    }
    if (method.name == "<init>" || method.name == "<clinit>") {
        return false
    }
    if (method.returnType != "V") {
        return false
    }
    val callbackParams = method.parameterTypes.map { it.toString() }.filter { param ->
        param.contains("Callback", ignoreCase = true) ||
            param.contains("Listener", ignoreCase = true) ||
            param.contains("Continuation", ignoreCase = true) ||
            param.contains("Observer", ignoreCase = true) ||
            param.contains("Handler", ignoreCase = true)
    }
    if (callbackParams.isNotEmpty()) {
        return false
    }
    val lifecycleNames = setOf(
        "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
        "onReceive", "onBind", "onStartCommand", "attachBaseContext",
    )
    if (method.name in lifecycleNames) {
        return false
    }
    val returnType = method.returnType
    if (returnType.contains("Task") ||
        returnType.contains("Future") ||
        returnType.contains("Completable") ||
        returnType.contains("Observable") ||
        returnType.contains("Deferred")
    ) {
        return false
    }
    return true
}

private fun isSafeBooleanDispatcher(method: Method): Boolean {
    if (method.implementation == null) {
        return false
    }
    if (method.name == "<init>" || method.name == "<clinit>") {
        return false
    }
    if (method.returnType != "Z") {
        return false
    }
    val callbackParams = method.parameterTypes.map { it.toString() }.filter { param ->
        param.contains("Callback", ignoreCase = true) ||
            param.contains("Listener", ignoreCase = true) ||
            param.contains("Continuation", ignoreCase = true) ||
            param.contains("Observer", ignoreCase = true) ||
            param.contains("Handler", ignoreCase = true)
    }
    if (callbackParams.isNotEmpty()) {
        return false
    }
    return true
}

private val appsFlyerDataflowFingerprint = Fingerprint(
    strings = listOf("DataflowID"),
)

private val appLogEventFingerprint = Fingerprint(
    strings = listOf("event_v3"),
)

private fun BytecodePatchContext.hookAppsFlyerDelegate(
    ref: MethodReference,
    hookedMethods: MutableList<String>,
    hookedSignatures: MutableSet<String>,
) {
    val delegateClassType = ref.definingClass
    val delegateClass = classDefByOrNull(delegateClassType) ?: return
    val mutableDelegate = mutableClassDefBy(delegateClass)
    val delegateMethod = mutableDelegate.methods.firstOrNull {
        it.name == ref.name && it.parameterTypes == ref.parameterTypes
    } ?: return

    val delegateSig = "$delegateClassType->${delegateMethod.name}"
    if (delegateSig in hookedSignatures) return

    if (isSafeVoidDispatcher(delegateMethod)) {
        delegateMethod.addInstructions(0, "return-void")
        hookedSignatures.add(delegateSig)
        hookedMethods.add("AppsFlyerDelegate.${delegateMethod.name}")
    } else if (isSafeBooleanDispatcher(delegateMethod)) {
        delegateMethod.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
        )
        hookedSignatures.add(delegateSig)
        hookedMethods.add("AppsFlyerDelegate.${delegateMethod.name}")
    }
}

private fun BytecodePatchContext.hookAppsFlyer(
    hookedMethods: MutableList<String>,
    hookedSignatures: MutableSet<String>,
) {
    val analyticsClassType = "Lcom/vega/launcher/report/AppsFlyerAnalytics;"
    val analyticsClass = classDefByOrNull(analyticsClassType)
    if (analyticsClass != null) {
        val mutableAnalytics = mutableClassDefBy(analyticsClass)
        for (method in mutableAnalytics.methods) {
            val sig = "$analyticsClassType->${method.name}"
            if (sig !in hookedSignatures && isSafeVoidDispatcher(method)) {
                method.addInstructions(0, "return-void")
                hookedSignatures.add(sig)
                hookedMethods.add("AppsFlyerAnalytics.${method.name}")
            }

            method.implementation?.instructions?.forEach { ins ->
                val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: return@forEach
                val targetClass = ref.definingClass
                if (targetClass != analyticsClassType &&
                    (targetClass.startsWith("Lcom/appsflyer/") ||
                        targetClass.startsWith("Lcom/vega/") ||
                        targetClass.contains("AppsFlyer", ignoreCase = true))
                ) {
                    hookAppsFlyerDelegate(ref, hookedMethods, hookedSignatures)
                }
            }
        }
    }

    val dataflowMatches = appsFlyerDataflowFingerprint.matchAllOrNull() ?: emptyList()
    for (match in dataflowMatches) {
        val method = match.method
        val defClass = method.definingClass
        if (defClass.startsWith("Lcom/appsflyer/") ||
            defClass.startsWith("Lcom/vega/launcher/report/") ||
            defClass.contains("AppsFlyer", ignoreCase = true)
        ) {
            val sig = "$defClass->${method.name}"
            if (sig !in hookedSignatures) {
                if (isSafeVoidDispatcher(method)) {
                    method.addInstructions(0, "return-void")
                    hookedSignatures.add(sig)
                    hookedMethods.add("AppsFlyerDataflow.${method.name}")
                } else if (isSafeBooleanDispatcher(method)) {
                    method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x0
                            return v0
                        """.trimIndent(),
                    )
                    hookedSignatures.add(sig)
                    hookedMethods.add("AppsFlyerDataflow.${method.name}")
                }
            }
        }
    }
}

private fun BytecodePatchContext.hookAppLog(
    hookedMethods: MutableList<String>,
    hookedSignatures: MutableSet<String>,
) {
    val appLogInstanceType = "Lcom/bytedance/applog/AppLogInstance;"
    val appLogInstanceClass = classDefByOrNull(appLogInstanceType)
    if (appLogInstanceClass != null) {
        val mutableAppLog = mutableClassDefBy(appLogInstanceClass)
        for (method in mutableAppLog.methods) {
            val sig = "$appLogInstanceType->${method.name}"
            if (sig !in hookedSignatures &&
                (method.name.startsWith("onEvent") || method.name.contains("event", ignoreCase = true)) &&
                isSafeVoidDispatcher(method)
            ) {
                method.addInstructions(0, "return-void")
                hookedSignatures.add(sig)
                hookedMethods.add("AppLogInstance.${method.name}")
            }
        }
    }

    val eventMatches = appLogEventFingerprint.matchAllOrNull() ?: emptyList()
    for (match in eventMatches) {
        val method = match.method
        val defClass = method.definingClass
        if (defClass.startsWith("Lcom/bytedance/applog/") ||
            defClass.startsWith("Lcom/ss/android/common/applog/")
        ) {
            val sig = "$defClass->${method.name}"
            if (sig !in hookedSignatures && isSafeVoidDispatcher(method)) {
                method.addInstructions(0, "return-void")
                hookedSignatures.add(sig)
                hookedMethods.add("AppLog.${method.name}")
            }
        }
    }
}

@Suppress("unused")
val capcutBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Neutralizes Google AppMeasurement, AppsFlyer, and ByteDance AppLog event dispatchers.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)
    dependsOn(capcutTelemetryResourcePatch)

    execute {
        val hookedMethods = mutableListOf<String>()
        val hookedSignatures = mutableSetOf<String>()

        Fingerprint(
            definingClass = "Lcom/google/android/gms/measurement/AppMeasurement;",
            name = "logEventInternal",
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/os/Bundle;"),
            returnType = "V",
        ).method.apply {
            addInstructions(0, "return-void")
            hookedMethods.add("AppMeasurement.logEventInternal")
            hookedSignatures.add("Lcom/google/android/gms/measurement/AppMeasurement;->logEventInternal")
        }

        hookAppsFlyer(hookedMethods, hookedSignatures)
        hookAppLog(hookedMethods, hookedSignatures)

        if (hookedMethods.isEmpty()) {
            println("[Block Telemetry & Trackers] WARNING: applied 0 hook(s) -> this feature is NOT active on this APK; its targets moved upstream.")
        } else {
            println("[Block Telemetry & Trackers] Neutralized ${hookedMethods.size} telemetry dispatch methods: ${hookedMethods.joinToString(", ")}.")
        }
    }
}
