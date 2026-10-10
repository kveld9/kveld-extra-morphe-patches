package app.morphe.patches.officehub

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.clearTryBlocks
import app.morphe.patches.shared.disableComponentsByName
import app.morphe.patches.shared.ensureRegisterCount
import app.morphe.patches.shared.removeComponentDiscoveryRegistrarsWhere
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
)

private val blockedComponents = setOf(
    // Google DataTransport
    "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",

    // HockeyApp Crash & Feedback Reporting
    "com.microsoft.office.hockeyapp.activities.HockeyWebViewActivity",

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

private val officeHubTelemetryResourcePatch = resourcePatch(
    name = "Copilot Telemetry Manifest Purge",
    description = "Strips advertising and tracking permissions, disables DataTransport, cross-sell, and HockeyApp components, and injects opt-out metadata in AndroidManifest.xml.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_OFFICEHUB)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[Copilot Telemetry] Skipped: AndroidManifest.xml not found.")
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

        println("[Copilot Telemetry] Stripped $removedPermissions permissions, disabled $disabledComponents tracking components, removed $removedRegistrars discovery registrars, and injected $injectedMetadata opt-out flags in AndroidManifest.xml.")
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
        definingClass = "Lcom/microsoft/applications/telemetry/core/v;",
        name = "logFailure",
        parameters = listOf(
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Lcom/microsoft/applications/telemetry/EventProperties;",
        ),
        hookedMethods = hookedMethods,
        label = "v.logFailure5",
    )
    hookVoidMethod(
        definingClass = "Lcom/microsoft/applications/telemetry/core/v;",
        name = "logFailure",
        parameters = listOf(
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Lcom/microsoft/applications/telemetry/EventProperties;",
        ),
        hookedMethods = hookedMethods,
        label = "v.logFailure3",
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

private val getAIFAFingerprint = Fingerprint(
    definingClass = "Lcom/microsoft/office/adsmobile_admeasurementpartner/admeasurement/AdMeasurementPlatformData;",
    name = "getAIFA",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
)

private val getAppSetIdFingerprint = Fingerprint(
    definingClass = "Lcom/microsoft/office/adsmobile_admeasurementpartner/admeasurement/AdMeasurementPlatformData;",
    name = "getAppSetId",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
)

context(_: BytecodePatchContext)
private fun hookAdMeasurementPlatformData(hookedMethods: MutableList<String>) {
    listOf(getAIFAFingerprint, getAppSetIdFingerprint).forEach { fp ->
        fp.method.apply {
            clearTryBlocks()
            ensureRegisterCount(1)
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(0, """
                const-string v0, ""
                return-object v0
            """.trimIndent())
        }
    }
    hookedMethods.add("AdMeasurementPlatformData.getAIFA")
    hookedMethods.add("AdMeasurementPlatformData.getAppSetId")
}

// -- Hosts Rewrite Layer (Second-Layer Defense) ------------------------------

private const val SINK_HOST = "0.0.0.0"

private val DEFAULT_BLOCKED_HOSTS = setOf(
    // Microsoft OneDS / Aria / Vortex telemetry endpoints
    "pipe.aria.microsoft.com",
    "mobile.pipe.aria.microsoft.com",
    "browser.pipe.aria.microsoft.com",
    "vortex.data.microsoft.com",
    "web.vortex.data.microsoft.com",
    "telemetry.microsoft.com",
    "watson.telemetry.microsoft.com",
    "onecollector.cloudapp.net",

    // Google / Firebase telemetry endpoints
    "app-measurement.com",
    "firebaselogging-pa.googleapis.com",
    "crashlyticsreports-pa.googleapis.com",
)

private val RESERVED_HOST_EXCLUSIONS = setOf(
    "localhost",
    "localhost6",
    "localhost.localdomain",
    "0.0.0.0",
    "127.0.0.1",
    "::1",
)

private val TELEMETRY_PACKAGE_PREFIXES = listOf(
    "Lcom/microsoft/applications/telemetry/",
    "Lcom/microsoft/unified/telemetry/",
    "Lcom/microsoft/office/adsmobile_admeasurementpartner/",
    "Lcom/microsoft/office/asyncdatapointreporting/",
    "Lcom/microsoft/office/hockeyapp/",
    "Lcom/microsoft/android/crosssell/",
    "Lcom/google/android/datatransport/",
    "Lcom/google/android/gms/measurement/",
    "Lcom/google/firebase/analytics/",
    "Lcom/google/firebase/crashlytics/",
    "Lcom/google/firebase/datatransport/",
)

private fun isTelemetryCandidateClass(type: String): Boolean {
    for (prefix in TELEMETRY_PACKAGE_PREFIXES) {
        if (type.startsWith(prefix)) return true
    }
    return false
}

private fun extractCandidateHost(raw: String): String {
    val withoutScheme = if (raw.contains("://")) raw.substringAfter("://") else raw
    return withoutScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore(':')
        .trim()
        .trimEnd('.')
}

private fun buildRewriteLiteral(literal: String, blockedHosts: Set<String>): Pair<String, String>? {
    if (literal.isBlank() || literal.length > 512) return null
    if (RESERVED_HOST_EXCLUSIONS.any { literal.equals(it, ignoreCase = true) }) return null

    val candidateHost = extractCandidateHost(literal)
    if (candidateHost.isEmpty() || candidateHost.length > 253) return null
    if (candidateHost in RESERVED_HOST_EXCLUSIONS) return null

    val matchedHost = blockedHosts.firstOrNull { blocked ->
        candidateHost.equals(blocked, ignoreCase = true) ||
            candidateHost.endsWith(".$blocked", ignoreCase = true)
    } ?: return null

    val replacement = literal.replace(candidateHost, SINK_HOST, ignoreCase = true)
    return Pair(replacement, matchedHost)
}

private data class PendingHostsRewrite(
    val index: Int,
    val register: Int,
    val replacement: String,
    val matchedRule: String,
)

private fun collectHostsRewrites(method: MutableMethod, blockedHosts: Set<String>): List<PendingHostsRewrite> {
    val instructions = method.instructionsOrNull?.toList() ?: return emptyList()
    val rewrites = mutableListOf<PendingHostsRewrite>()
    for ((index, instruction) in instructions.withIndex()) {
        val opcode = instruction.opcode
        if (opcode != Opcode.CONST_STRING && opcode != Opcode.CONST_STRING_JUMBO) continue
        val ref = (instruction as? ReferenceInstruction)?.reference as? StringReference ?: continue
        val original = ref.string
        val (replacement, matchedRule) = buildRewriteLiteral(original, blockedHosts) ?: continue
        val register = (instruction as? OneRegisterInstruction)?.registerA ?: continue
        rewrites.add(PendingHostsRewrite(index, register, replacement, matchedRule))
    }
    return rewrites
}

private fun escapeSmaliLiteral(value: String): String =
    value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

private fun applyHostsRewrites(method: MutableMethod, rewrites: List<PendingHostsRewrite>) {
    for (rewrite in rewrites.sortedByDescending { it.index }) {
        val opcode = if (rewrite.register > 255) "const-string/jumbo" else "const-string"
        method.replaceInstruction(
            rewrite.index,
            "$opcode v${rewrite.register}, \"${escapeSmaliLiteral(rewrite.replacement)}\"",
        )
    }
}

private data class HostRewriteStats(
    val rewrittenStrings: Int,
    val ruleCounts: Map<String, Int>,
)

private fun BytecodePatchContext.blockHostsInDex(
    blockedHosts: Set<String>,
    touchedClasses: MutableSet<String>,
    predicate: ((String) -> Boolean)? = null,
): HostRewriteStats {
    var rewrittenStrings = 0
    val ruleCounts = mutableMapOf<String, Int>()

    classDefForEach { classDef ->
        if (predicate != null && !predicate(classDef.type)) return@classDefForEach

        val mutableClass = mutableClassDefBy(classDef)
        var classModified = false

        for (method in mutableClass.methods) {
            val rewrites = collectHostsRewrites(method, blockedHosts)
            if (rewrites.isNotEmpty()) {
                applyHostsRewrites(method, rewrites)
                rewrittenStrings += rewrites.size
                classModified = true
                for (rw in rewrites) {
                    ruleCounts[rw.matchedRule] = (ruleCounts[rw.matchedRule] ?: 0) + 1
                }
            }
        }

        if (classModified) {
            touchedClasses.add(classDef.type)
        }
    }

    return HostRewriteStats(rewrittenStrings, ruleCounts)
}

// -- Primary Bytecode Patch --------------------------------------------------

@Suppress("unused")
val officeHubBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, nullifies ad measurement platform identifiers (AIFA, AppSetId), rewrites default telemetry endpoints inside telemetry packages and custom blocked hosts unscoped to 0.0.0.0, disables cross-sell, Floodgate, HockeyApp and DataTransport components, and strips advertising permissions.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_OFFICEHUB)
    dependsOn(officeHubTelemetryResourcePatch)

    val customBlockedHosts by stringOption(
        key = "custom-blocked-hosts",
        title = "Custom Blocked Hosts",
        description = "Comma-separated list of additional telemetry hostnames or domains to redirect to 0.0.0.0. Single-label, credentialed-URL, and IPv6 entries are skipped.",
        required = false,
    )

    execute {
        val hookedMethods = mutableListOf<String>()

        hookLifecycleCallbacks(hookedMethods)
        hookTelemetryDispatchers(hookedMethods)
        hookAdMeasurementPlatformData(hookedMethods)

        val userHosts = mutableListOf<String>()
        val skippedUserHosts = mutableListOf<Pair<String, String>>()

        customBlockedHosts?.split(",")?.forEach { rawToken ->
            val sanitizedRaw = rawToken.replace("\r", " ").replace("\n", " ").trim()
            if (sanitizedRaw.isEmpty()) return@forEach

            if (sanitizedRaw.contains('@')) {
                skippedUserHosts.add(sanitizedRaw to "credentials/userinfo not supported")
                return@forEach
            }

            if (sanitizedRaw.startsWith('[') || sanitizedRaw.contains("::")) {
                skippedUserHosts.add(sanitizedRaw to "IPv6 literals not supported")
                return@forEach
            }

            val candidate = extractCandidateHost(sanitizedRaw.lowercase())
            if (candidate.startsWith('[') || candidate.contains("::") || candidate.count { it == ':' } > 1) {
                skippedUserHosts.add(sanitizedRaw to "IPv6 literals not supported")
                return@forEach
            }

            if (candidate.isEmpty() || candidate.length > 253) {
                skippedUserHosts.add(sanitizedRaw to "invalid host format")
                return@forEach
            }

            if (candidate in RESERVED_HOST_EXCLUSIONS) {
                skippedUserHosts.add(sanitizedRaw to "reserved host")
                return@forEach
            }

            if (!candidate.contains('.')) {
                skippedUserHosts.add(sanitizedRaw to "single-label domain not supported")
                return@forEach
            }

            userHosts.add(candidate)
        }

        if (skippedUserHosts.isNotEmpty()) {
            val skipDetails = skippedUserHosts.joinToString(", ") { "${it.first} (${it.second})" }
            println("[Copilot Telemetry] Custom blocked hosts skipped (${skippedUserHosts.size}): $skipDetails.")
        }

        val distinctUserHosts = userHosts.distinct()
        val totalRuleCount = DEFAULT_BLOCKED_HOSTS.size + distinctUserHosts.size
        if (totalRuleCount > 100_000) {
            println("[Copilot Telemetry] Warning: Blocklist contains $totalRuleCount rules. Memory usage and patching latency may be high.")
        }

        val touchedClassTypes = mutableSetOf<String>()

        // Pass 1: Scoped pass for default telemetry endpoints within telemetry package prefixes
        val defaultStats = blockHostsInDex(DEFAULT_BLOCKED_HOSTS, touchedClassTypes) { isTelemetryCandidateClass(it) }

        // Pass 2: Unscoped pass for user-supplied custom blocked hosts across all classes
        val customStats = if (distinctUserHosts.isNotEmpty()) {
            blockHostsInDex(distinctUserHosts.toSet(), touchedClassTypes, predicate = null)
        } else {
            HostRewriteStats(0, emptyMap())
        }

        val totalRewritten = defaultStats.rewrittenStrings + customStats.rewrittenStrings
        val totalClasses = touchedClassTypes.size

        println("[Copilot Telemetry] Neutralized ${hookedMethods.size} telemetry dispatch methods and rewrote $totalRewritten host literals across $totalClasses classes -> 0.0.0.0.")

        if (distinctUserHosts.isNotEmpty()) {
            val appliedUser = distinctUserHosts.filter { (customStats.ruleCounts[it] ?: 0) > 0 }
            val unappliedUser = distinctUserHosts.filter { (customStats.ruleCounts[it] ?: 0) == 0 }

            if (appliedUser.isNotEmpty()) {
                val appliedSummary = appliedUser.joinToString(", ") { "$it (${customStats.ruleCounts[it]} rewrites)" }
                println("[Copilot Telemetry] Custom blocked hosts applied: $appliedSummary.")
            }
            if (unappliedUser.isNotEmpty()) {
                println("[Copilot Telemetry] Custom blocked hosts unapplied (0 rewrites): ${unappliedUser.joinToString(", ")}.")
            }
        }
    }
}
