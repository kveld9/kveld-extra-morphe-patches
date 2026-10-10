# Lightroom Patches Guide

Technical documentation and patch catalog for Lightroom on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Lightroom |
| **Package Name** | `com.adobe.lrmobile` |
| **Target Version** | `11.6.01` |
| **Target Package Format** | APK (PairIP-free variant, arm64-v8a) |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | Uptodown (APK variant, not the Play APKM bundle: the Play bundle ships PairIP integrity which kills resigned builds at boot) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Unlock Premium Features** | `bytecodePatch` | `true` (Enabled) | None | Activates internal Limited-Time Premium Unlock (LTPU) gate to bypass subscription requirements. |

---

## 3. Deep Technical Breakdown

Technical breakdown is pending patch implementation.

---

## 4. Behavioral Hazards Warning

Users and packagers must observe the following risks before applying resource slimmer patches:

1. **Opt-in Status**:
   Telemetry and resource slimmer patches will be opt-in and disabled by default (`default = false`).
2. **Screen Density Slimmer**:
   Strips unselected density drawable and mipmap directories, and purges non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`). While launcher icons, nodpi/anydpi, and single-density orphans are guarded, stripping density resources may degrade visual assets, alter icon rendering, or cause layout clipping on devices whose display densities or display scaling settings differ from the preserved density.
3. **Locale Slimmer**:
   Recursively deletes unselected language-specific `values-*` directories. While base fallback strings (`res/values`) and English are retained, all stripped languages become unavailable. If the host system language is stripped, the application falls back to base or English strings.
4. **Mandatory Backup & Verification**:
   Users must retain a clean backup copy of the original Lightroom APK/APKM bundle and test the patched package thoroughly before replacing their active daily installation.
