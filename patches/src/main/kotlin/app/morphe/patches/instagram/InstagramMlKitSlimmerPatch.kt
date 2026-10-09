package app.morphe.patches.instagram

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants

@Suppress("unused")
val instagramMlKitSlimmerPatch = resourcePatch(
    name = "MLKit Vision Slimmer",
    description = "Disable MLKit component discovery and registrars. WARNING: this breaks in-app QR and barcode scanning.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_INSTAGRAM)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[Instagram MLKit Slimmer] AndroidManifest.xml not found - skipping MLKit slimmer.")
            return@execute
        }

        var disabledServices = 0
        var removedRegistrars = 0

        document(manifestFile.absolutePath).use { doc ->
            val application = doc.documentElement.getElementsByTagName("application").item(0) as? org.w3c.dom.Element
            
            if (application != null) {
                val services = application.getElementsByTagName("service")
                for (i in 0 until services.length) {
                    val service = services.item(i) as? org.w3c.dom.Element ?: continue
                    val name = service.getAttribute("android:name").ifBlank { service.getAttributeNS("http://schemas.android.com/apk/res/android", "name") }
                    if (name == "com.google.mlkit.common.internal.MlKitComponentDiscoveryService") {
                        service.setAttribute("android:enabled", "false")
                        service.setAttribute("android:exported", "false")
                        disabledServices++

                        val metaDataNodes = service.getElementsByTagName("meta-data")
                        val toRemove = mutableListOf<org.w3c.dom.Element>()
                        for (j in 0 until metaDataNodes.length) {
                            val metaData = metaDataNodes.item(j) as? org.w3c.dom.Element ?: continue
                            val metaName = metaData.getAttribute("android:name").ifBlank { metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "name") }
                            if (metaName.startsWith("com.google.firebase.components:")) {
                                toRemove.add(metaData)
                            }
                        }
                        for (metaData in toRemove) {
                            service.removeChild(metaData)
                            removedRegistrars++
                        }
                    }
                }
            }
        }

        println("[Instagram MLKit Slimmer] Disabled $disabledServices MLKit service and removed $removedRegistrars discovery registrars in AndroidManifest.xml.")
    }
}
