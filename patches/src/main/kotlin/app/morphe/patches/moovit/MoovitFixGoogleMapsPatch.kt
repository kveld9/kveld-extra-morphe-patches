package app.morphe.patches.moovit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants.COMPATIBILITY_MOOVIT
import app.morphe.patches.shared.setApplicationMetaData
import org.w3c.dom.Element

private val mapsApiKeyOption = stringOption(
    key = "mapsApiKey",
    default = null,
    title = "Google Maps Platform API key",
    description = "Optional custom Google Maps Platform key. When omitted, built-in keys work automatically via signature spoofing.",
    required = false,
)

private val moovitMapsApiKeyResourcePatch = resourcePatch(
    name = "Moovit Custom Maps API Key",
    description = "Replaces the Google Maps API key in AndroidManifest.xml and web-service strings when a custom key is provided.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MOOVIT)

    execute {
        val key = mapsApiKeyOption.value
        if (key.isNullOrBlank()) {
            println("[Fix Google Maps] Using signature spoofing for built-in Google Maps key.")
            return@execute
        }

        val manifestFile = get("AndroidManifest.xml")
        if (manifestFile.exists()) {
            document(manifestFile.absolutePath).use { doc ->
                val application = doc.getElementsByTagName("application").item(0) as? Element
                if (application != null) {
                    application.setApplicationMetaData("com.google.android.geo.API_KEY", key)
                }
            }
        }

        val stringsFile = get("res/values/strings.xml")
        if (stringsFile.exists()) {
            document(stringsFile.absolutePath).use { doc ->
                val strings = doc.getElementsByTagName("string")
                val webServicesKey = (0 until strings.length)
                    .mapNotNull { strings.item(it) as? Element }
                    .singleOrNull { it.getAttribute("name") == "google_wla_api_key" }
                if (webServicesKey != null) {
                    webServicesKey.textContent = key
                }
            }
        }

        println("[Fix Google Maps] Injected custom Google Maps API key into AndroidManifest.xml and strings.xml.")
    }
}

private val moovitApplicationOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/moovit/MoovitApplication;",
    name = "onCreate",
    returnType = "V",
)

@Suppress("unused")
val moovitFixGoogleMapsPatch = bytecodePatch(
    name = "Fix Google Maps",
    description = "Restores Google Maps rendering by spoofing the original package signature to Google Play Services.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MOOVIT)
    dependsOn(moovitMapsApiKeyResourcePatch)

    mapsApiKeyOption()

    extendWith("extensions/extension.mpe")

    execute {
        moovitApplicationOnCreateFingerprint.method.addInstructions(
            0,
            "invoke-static {}, Lcom/kveld9/morphe/extra/extension/MoovitHelper;->init()V",
        )
        println("[Fix Google Maps] Injected signature spoofing initializer into MoovitApplication.onCreate -> Maps rendering restored.")
    }
}
