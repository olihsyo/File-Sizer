# File Sizer — Dependency Inventory

Versions below are read from `gradle/libs.versions.toml` and Gradle configuration. This is a local inventory, not a complete transitive dependency or legal-license audit. The repository does not currently bundle third-party license texts.

| Artifact / tool | Configured version | Upstream license family (inventory only) | Notes |
| :--- | :--- | :--- | :--- |
| Android Gradle Plugin | 8.13.2 | Apache-2.0 | Build plugin |
| Kotlin Android / Compose plugins | 2.2.10 | Apache-2.0 | Build plugins |
| Gradle wrapper | 9.3.1 | Apache-2.0 | Distribution configured by wrapper |
| `androidx.core:core-ktx` | 1.15.0 | Apache-2.0 | Runtime |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | Apache-2.0 | Runtime |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | Apache-2.0 | Declared; ViewModels are not currently used |
| `androidx.activity:activity-compose` | 1.10.0 | Apache-2.0 | Runtime |
| `androidx.compose:compose-bom` | 2025.02.00 | Apache-2.0 | Resolves Compose artifacts |
| Compose UI / Material 3 / extended icons | BOM-managed | Apache-2.0 | Resolved versions should be captured from a dependency report for release |
| `androidx.navigation:navigation-compose` | 2.8.9 | Apache-2.0 | Runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `android` | 1.10.1 | Apache-2.0 | Runtime |
| `junit:junit` | 4.13.2 | EPL-1.0 | Unit tests |
| `org.json:json` | 20240303 | JSON license | Test dependency |
| AndroidX test JUnit / Espresso | 1.2.1 / 3.6.1 | Apache-2.0 | Declared; no instrumentation tests currently exist |
| `io.github.reandroid:ARSCLib` | 1.4.0 | Apache-2.0 | APK binary manifest/resource-table inspection and the bounded application-label rebuild. [Upstream](https://github.com/REAndroid/ARSCLib); [Maven Central license metadata](https://central.sonatype.com/artifact/io.github.reandroid/ARSCLib) |
| `com.android.tools.build:apksig` | 8.13.2 | Apache-2.0 | APK signing and signature verification; version matches the Android Gradle Plugin line. [AOSP source and API description](https://android.googlesource.com/platform/tools/apksig); [AOSP license metadata](https://android.googlesource.com/platform/tools/apksig/%2B/refs/heads/android13-s1-release/Android.bp) |

## Follow-up before release

- Generate a complete resolved dependency/SBOM report and verify every direct and transitive license.
- Include required license notices in the app or distribution materials.
- The APK edit/signing path has not been run on an Android device; the library documentation and artifact/license metadata do not establish runtime compatibility for every device or APK.
- Check maintenance, security advisories, Android compatibility and native-library packaging for the selected release versions.
- The current lint report identifies newer versions of several dependencies and SDK/plugin components. Do not upgrade without checking compatibility and rerunning the full build and test suite.
- `material3` and `materialIconsExtended` version aliases are present but unused; the Compose BOM currently controls resolution.
