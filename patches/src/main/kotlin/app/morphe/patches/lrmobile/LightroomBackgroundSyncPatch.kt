package app.morphe.patches.lrmobile

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.disableComponentsByName
import app.morphe.patches.shared.stripPermissionsWhere
import org.w3c.dom.Element

@Suppress("unused")
val lightroomBackgroundSyncPatch = resourcePatch(
    name = "Lightroom Background Sync",
    description = "Disables WorkManager background sync services, scheduled jobs, and boot receiver. The WorkManager startup initializer is intentionally preserved because removing it prevents application boot. WARNING: Disabling WorkManager can affect background photo uploads, syncing, and exports.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_LRMOBILE)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[Lightroom Background Sync] Skipped: AndroidManifest.xml not found.")
            return@execute
        }

        var removedPermissions = 0
        var disabledComponents = 0

        document(manifestFile.absolutePath).use { doc ->
            val root = doc.documentElement
            val application = root.getElementsByTagName("application").item(0) as? Element
            if (application == null) {
                println("[Lightroom Background Sync] Skipped: application element not found in AndroidManifest.xml.")
                return@execute
            }

            // 1. Strip boot completed permission (strictly keep WAKE_LOCK intact)
            val stripped = root.stripPermissionsWhere { permission ->
                permission == TARGET_BOOT_PERMISSION
            }
            removedPermissions = stripped.size

            // 2. Disable WorkManager services and broadcast receivers
            disabledComponents = application.disableComponentsByName(*TARGET_WORKMANAGER_COMPONENTS)
        }

        println("[Lightroom Background Sync] Stripped $removedPermissions permissions and disabled $disabledComponents WorkManager components (startup initializer preserved).")
    }
}

private const val TARGET_BOOT_PERMISSION = "android.permission.RECEIVE_BOOT_COMPLETED"

private val TARGET_WORKMANAGER_COMPONENTS = arrayOf(
    "androidx.work.impl.foreground.SystemForegroundService",
    "androidx.work.impl.background.systemjob.SystemJobService",
    "androidx.work.impl.utils.ForceStopRunnable\$BroadcastReceiver",
    "androidx.work.impl.background.systemalarm.RescheduleReceiver",
    "androidx.work.impl.diagnostics.DiagnosticsReceiver",
)
