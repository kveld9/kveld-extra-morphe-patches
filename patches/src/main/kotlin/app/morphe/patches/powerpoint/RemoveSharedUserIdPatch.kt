package app.morphe.patches.powerpoint

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_POWERPOINT
import org.w3c.dom.Element

@Suppress("unused")
val powerPointRemoveSharedUserIdPatch = resourcePatch(
    name = "Remove Shared User ID",
    description = "Removes the sharedUserId attribute from the manifest to prevent installation conflicts.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_POWERPOINT)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.getElementsByTagName("manifest").item(0) as Element
            manifest.removeAttribute("android:sharedUserId")
            manifest.removeAttribute("android:sharedUserLabel")
        }
    }
}
