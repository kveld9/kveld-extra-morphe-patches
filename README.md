<p align="center">
  <a href="https://github.com/kveld9/kveld-extra-morphe-patches/releases/latest"><img src="https://img.shields.io/github/v/release/kveld9/kveld-extra-morphe-patches?color=7928CA&label=Release&logo=github&style=flat-square" alt="Latest Release" /></a>
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=flat-square" alt="License" />
</p>

<h1 align="center">kveld9 Extra Patches</h1>

<p align="center">
  Standalone, app-specific patches for use with the <b><a href="https://morphe.software">Morphe</a></b> patcher framework.
  Companion to <a href="https://github.com/kveld9/kveld-morphe-patches">kveld-morphe-patches</a>.
</p>

<p align="center">
  <a href="https://morphe.software/add-source?github=kveld9/kveld-extra-morphe-patches"><img src="https://img.shields.io/badge/Morphe_Manager-Add_Patch_Source-8A2BE2?style=for-the-badge&logo=android&logoColor=white" alt="Add Source to Morphe Manager" /></a>
</p>

---

## Supported Apps

| App | Package | Target Version |
| :--- | :--- | :--- |
| Instagram | `com.instagram.android` | 447.0.0.55.81 |
| X | `com.twitter.android` | 12.19.1-release.0 |
| Moovit | `com.tranzmate` | 5.201.1.1809 |
| PowerPoint | `com.microsoft.office.powerpoint` | 16.0.20527.20034 |

## Patches

<!-- PATCHES_START -->
<details open>
<summary>Instagram&nbsp;&nbsp;•&nbsp;&nbsp;<b>2 patches</b></summary>
<br>

**Supported versions:**

| 447.0.0.55.81 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Facebook Analytics2, DataTransport, FDID/PhoneId providers, and strips AD_ID permissions. |  |
| **MLKit Vision Slimmer** | Disable MLKit component discovery and registrars. WARNING: this breaks in-app QR and barcode scanning. |  |

</details>

<details open>
<summary>Moovit&nbsp;&nbsp;•&nbsp;&nbsp;<b>6 patches</b></summary>
<br>

**Supported versions:**

| 5.201.1.1809 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Disables analytics and tracking services, providers, and receivers, and strips advertising permissions. |  |
| **Fix Google Maps** | Restores Google Maps rendering by spoofing the original package signature to Google Play Services. |  |
| **Unlock Moovit+** | Unlocks Moovit+ premium subscription features locally, including Safe Ride and address search in favorites. |  |
| **Remove Ads** | Hides banner and inline ads and neutralizes ad unit ID lookups. |  |
| **Locale Slimmer** | Strips unselected language string tables and resources from base APK. Base fallback and English are always preserved. | • Locales to keep |
| **Screen Density Slimmer** | Strips unselected screen density assets and purges non-phone UI mode qualifiers. Launcher icons, nodpi/anydpi, and single-density orphans are always preserved. | • Target screen density |

</details>

<details open>
<summary>PowerPoint&nbsp;&nbsp;•&nbsp;&nbsp;<b>4 patches</b></summary>
<br>

**Supported versions:**

| 16.0.20527.20034 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods plus MUTSDK receivers, disables HockeyApp activities and DataTransport components, and strips advertising permissions. |  |
| **PowerPoint Companion Native Slimmer** | Strips optional companion native binaries (React Native and Hermes JavaScript runtime stack) via in-situ zeroing. WARNING: stripped libraries are load-bearing for React Native initialization - enabling this WILL crash the app with UnsatisfiedLinkError when Copilot or other React Native surfaces start, not merely hide those features. |  |
| **PowerPoint DPI Slimmer** | Strips drawables for unselected screen densities from PowerPoint base APK while preserving launcher icons and single-density assets. WARNING: Displays matching stripped densities will scale preserved assets. | • Target screen density |
| **PowerPoint Locale Slimmer** | Strips unselected localized resource directories (res/values-<locale>/) from PowerPoint base APK. English (en, en-us) is always retained. WARNING: Application strings for stripped locales will fall back to English. | • Locales to keep |

</details>

<details open>
<summary>X&nbsp;&nbsp;•&nbsp;&nbsp;<b>2 patches</b></summary>
<br>

**Supported versions:**

| 12.19.1-release.0 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Google AppMeasurement event dispatchers, and strips advertising identifiers. |  |
| **X MLKit Vision Slimmer** | Disables MLKit component discovery and registrars. WARNING: this breaks in-app QR and barcode scanning. |  |

</details>

<!-- PATCHES_END -->

## Documentation

- [Building & Development](docs/building.md)
- [Project Scope](docs/out-of-scope.md)

## License

GPLv3. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
