# File Sizer — Test Report

**Execution date:** 2026-10-23
**Result:** Unit tests, Android lint and debug APK build completed successfully after the Phase 1 settings changes.
**Command:** `gradlew.bat testDebugUnitTest lintDebug assembleDebug --rerun-tasks`
**Environment:** Windows 10, Android Studio JBR 25.0.3, Gradle 9.3.1, compile SDK 35, Android SDK Build Tools 35.0.0.

## Unit tests

| Test class | Tests | Failures | Result |
| :--- | ---: | ---: | :--- |
| `ArchiveServiceTest` | 2 | 0 | PASS |
| `DevToolsServiceTest` | 4 | 0 | PASS |
| `FileOperationServiceTest` | 5 | 0 | PASS |
| `FormatUtilsTest` | 4 | 0 | PASS |
| `StorageScanEngineTest` | 2 | 0 | PASS |
| `SyntaxHighlighterTest` | 3 | 0 | PASS |
| **Total** | **20** | **0** | **PASS** |

These are JVM unit tests. They do not verify Android permissions, real shared storage, Compose interactions, or device behavior.

## Lint and build

- `lintDebug`: BUILD SUCCESSFUL; report contains **0 errors, 48 warnings and 1 hint**. Warnings include newer SDK/plugin/dependency suggestions, deprecated status/navigation bar APIs, and Compose parameter ordering.
- `assembleDebug`: BUILD SUCCESSFUL. APK: `app/build/outputs/apk/debug/app-debug.apk` (17,120,865 bytes).
- Build output warned that the configured NDK directory has no `source.properties`; the build still packaged `libandroidx.graphics.path.so` without stripping it.
- Gradle reported deprecated features that may be incompatible with Gradle 10.

## Not run / not verified

- No emulator or physical device was attached; no instrumentation or UI tests ran.
- No release APK was built. The release build type currently uses debug signing and is not ready for distribution.
- Performance, storage permission denial/revocation, SAF, Android/data, Shizuku and real archive/APK compatibility remain unverified.
