# Project Scope

This repository holds standalone patches for individual apps, added as needed. Each target app is independent: its own package under `patches/`, its own `COMPATIBILITY_<APP>` entry in `Constants.kt`, and its own `TargetApp` entry in the patch runner.

## Principles

1. **Compile-time transformations**: patches are `bytecodePatch`, `resourcePatch` or `rawResourcePatch` edits applied at patch time.
2. **Zero runtime overhead**: dead paths are short-circuited instead of wrapped in runtime toggles or proxies.
3. **Single target version**: each app targets exactly one version, the latest supported release. Older versions are dropped on every bump.

## Out of Scope

### In-App Settings Screens & Dynamic UI Panels
Injected preference menus, overlays or settings screens to toggle patches at runtime. They break across obfuscation changes in each upstream release and keep unwanted code paths alive behind runtime guards. Configurable parameters are patch-time options in Morphe Manager / CLI (`stringOption`).

### Server-Side Bypasses, DRM, and Account Exploits
Server-enforced paywalls, cloud-restricted content, private accounts, and DRM. Patches operate on client-side bytecode and local assets only.

### Feature Bloat & In-App Download Managers
Embedded download engines, torrent clients, custom players or UI skins. Patches unlock native capabilities and remove bloat; they do not add third-party subsystems.

### Legacy & Multi-Version Support
Compatibility matrices or fallback code for older app versions.
