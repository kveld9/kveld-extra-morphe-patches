## [1.4.0](https://github.com/kveld9/kveld-extra-morphe-patches/compare/v1.3.0...v1.4.0) (2026-10-09)

### Bug Fixes

* **moovit:** neutralize in-process telemetry dispatchers and strip AppsFlyer key ([2870b6c](https://github.com/kveld9/kveld-extra-morphe-patches/commit/2870b6ceab7b47443867ef6b6c62b737d693b23a))
* **powerpoint:** add diagnostic telemetry logging to new patches ([e7f096c](https://github.com/kveld9/kveld-extra-morphe-patches/commit/e7f096c64c16066edd2dea08ce6764b0499cd919))
* **tooling:** exempt declaration-only files from patch logging gate ([532cd62](https://github.com/kveld9/kveld-extra-morphe-patches/commit/532cd624e491ad9fb13100135f5c582fd1493ed8))

### New Features

* **moovit:** add custom maps API key option to Fix Google Maps patch ([fbdec42](https://github.com/kveld9/kveld-extra-morphe-patches/commit/fbdec428bc214aa5c45e40f2678b338c977b31e0))
* **moovit:** add Fix Google Maps patch to restore map rendering ([7560d48](https://github.com/kveld9/kveld-extra-morphe-patches/commit/7560d48e313bd15f837a312cd19cc6f3d9a683e5))
* **moovit:** add Remove Ads patch to suppress banner and inline ads ([438dd61](https://github.com/kveld9/kveld-extra-morphe-patches/commit/438dd61673dc6dd45de9bbc4f96cf1cc415105b5))
* **moovit:** add Suppress Paywalls patch to disable upgrade dialogs and cards ([a70634c](https://github.com/kveld9/kveld-extra-morphe-patches/commit/a70634cd9b3b7301a40495c22fcf3f2aaa24832d))
* **moovit:** add Unlock Moovit+ patch for premium features and address search ([1c4554b](https://github.com/kveld9/kveld-extra-morphe-patches/commit/1c4554b00630a2aff7f36187322d2398d79eade3))

### Code Refactoring

* **moovit:** dynamically resolve paywall skip methods in Suppress Paywalls ([fa2e65f](https://github.com/kveld9/kveld-extra-morphe-patches/commit/fa2e65f4e7fa4bc7575cfe7e797286aa6476281b))

## [1.3.0](https://github.com/kveld9/kveld-extra-morphe-patches/compare/v1.2.0...v1.3.0) (2026-10-09)

### Bug Fixes

* **powerpoint:** resolve obfuscated LicenseStatus class in licenseStatusIsPremiumFingerprint ([9e2cde0](https://github.com/kveld9/kveld-extra-morphe-patches/commit/9e2cde07213bf65a8507cbea06cfa8a8e8530fb7))

### New Features

* **powerpoint:** add Bypass Code Transparency patch ([7b8f540](https://github.com/kveld9/kveld-extra-morphe-patches/commit/7b8f5401f5cebc3fedbc3f85e95405a33c6c1ba1))
* **powerpoint:** add Disable Ads patch ([08459c7](https://github.com/kveld9/kveld-extra-morphe-patches/commit/08459c77b2b603427a7f8d50e208f1bee5a6467f))
* **powerpoint:** add Disable Login Requirement patch ([26f46a5](https://github.com/kveld9/kveld-extra-morphe-patches/commit/26f46a56c9bbb10b77ab500bb9e0159315edfc9b))
* **powerpoint:** add fingerprint definitions for core patches ([231a655](https://github.com/kveld9/kveld-extra-morphe-patches/commit/231a6553c18b35b255038a723150880fbbaf3ce8))
* **powerpoint:** add Remove Shared User ID patch ([756ec78](https://github.com/kveld9/kveld-extra-morphe-patches/commit/756ec78caaab35619f2aafd4c5d8a85d6c652930))
* **powerpoint:** add Unlock 365 Family patch ([bd3d3c6](https://github.com/kveld9/kveld-extra-morphe-patches/commit/bd3d3c67c14d000495e6fc84473f365e94a70847))

### Code Refactoring

* **powerpoint:** merge ad measurement nullification into telemetry blocking patch ([90e6559](https://github.com/kveld9/kveld-extra-morphe-patches/commit/90e6559c008632e53781c899500f26bb5228abd8))

## [1.2.0](https://github.com/kveld9/kveld-extra-morphe-patches/compare/v1.1.0...v1.2.0) (2026-10-09)

### Bug Fixes

* **powerpoint:** preserve crash delegation in telemetry block ([e37c4ba](https://github.com/kveld9/kveld-extra-morphe-patches/commit/e37c4ba2ec32f6b835e7fabf989a7429b877343b))
* **powerpoint:** preserve RTL mirrors in DPI slimmer ([dfed437](https://github.com/kveld9/kveld-extra-morphe-patches/commit/dfed43783fb50eaed19267fbe795185fab8726c7))
* **powerpoint:** state exact telemetry coverage in descriptions ([c329fc9](https://github.com/kveld9/kveld-extra-morphe-patches/commit/c329fc964570b978a485de52034756a13fd0c041))
* **powerpoint:** warn of crash mode in native slimmer ([28da4ce](https://github.com/kveld9/kveld-extra-morphe-patches/commit/28da4ce3540393202c4e4c235552798e75b78407))

### New Features

* **moovit:** add Block Telemetry and Trackers patch ([0d299ee](https://github.com/kveld9/kveld-extra-morphe-patches/commit/0d299ee4dc52a86a4efdddf3932cd6b69ba4bd2d))
* **moovit:** add Locale Slimmer patch ([a85c748](https://github.com/kveld9/kveld-extra-morphe-patches/commit/a85c7489f3b7eb665dff590fdf335d7bb9bba02c))
* **moovit:** add Screen Density Slimmer patch ([1cb8fb6](https://github.com/kveld9/kveld-extra-morphe-patches/commit/1cb8fb663a7826a4be6e98044f8ed3ad16001eaf))
* **powerpoint:** add Block Telemetry and Trackers patch ([4fa1484](https://github.com/kveld9/kveld-extra-morphe-patches/commit/4fa14847adc7ac1cea3dea18a52133669c196b6b))
* **powerpoint:** add Companion Native Slimmer patch ([676f133](https://github.com/kveld9/kveld-extra-morphe-patches/commit/676f133318f137841ca15059cb62550363f54822))
* **powerpoint:** add DPI Slimmer patch ([2723dfd](https://github.com/kveld9/kveld-extra-morphe-patches/commit/2723dfd7336acc86cb5c2860d3256e0e35614ce8))
* **powerpoint:** add Locale Slimmer patch ([b9a9de5](https://github.com/kveld9/kveld-extra-morphe-patches/commit/b9a9de5255762033f6467da3057ffa1c04f1ebf0))
* **tooling:** add telemetry-app command for parallel patch onboarding ([9cdc0a3](https://github.com/kveld9/kveld-extra-morphe-patches/commit/9cdc0a3d084f524087dd415811b3d82b99ad1140))
* **twitter:** add block telemetry patch ([19c53df](https://github.com/kveld9/kveld-extra-morphe-patches/commit/19c53dfb578e68448318f6015d8c913735ac3d98))
* **twitter:** add mlkit vision slimmer patch ([44eb9ce](https://github.com/kveld9/kveld-extra-morphe-patches/commit/44eb9ce5a6967fff4265732848fe12dcdf8f43b5))

## [1.1.0](https://github.com/kveld9/kveld-extra-morphe-patches/compare/v1.0.0...v1.1.0) (2026-10-09)

### New Features

* **instagram:** add opt-in MLKit vision slimmer ([38d891f](https://github.com/kveld9/kveld-extra-morphe-patches/commit/38d891f437a4fcdc9c101a4f54f4bf16a09349d4))

## 1.0.0 (2026-10-09)

### Bug Fixes

* **instagram:** neutralize analytics upload dispatchers at bytecode level ([67cfa6e](https://github.com/kveld9/kveld-extra-morphe-patches/commit/67cfa6ef59c88cd8246919dd9690802375e32601))
* **tooling:** append missing .apk extension to -DoutputApk base output ([3608f71](https://github.com/kveld9/kveld-extra-morphe-patches/commit/3608f71227963c766ed1c9e0bfd16950b5e4c1df))

### New Features

* **instagram:** block telemetry and trackers via manifest purge ([f316cca](https://github.com/kveld9/kveld-extra-morphe-patches/commit/f316ccaf63ad5a4f15da4c368b06dd8ee604ce92))
* **shared:** port generic bytecode, locale, manifest, and resource helpers ([a1cbb85](https://github.com/kveld9/kveld-extra-morphe-patches/commit/a1cbb8530794c944dfbd5e419fac9c504996923c))

### Code Refactoring

* **tooling:** port generic runner gates and timing telemetry to test harness ([3e8f630](https://github.com/kveld9/kveld-extra-morphe-patches/commit/3e8f6305e3adafe5608cec51d9439e82868fc56a))
