# File Sizer

File Sizer is an offline-first Android file utility built with Kotlin, Jetpack Compose and Material 3. The repository contains a working implementation of filesystem/SAF browsing, safer file operations, ZIP tools, a bounded text editor, APK analysis with application-label rebuild/signing, a read-only paged hex viewer and developer utilities.

**Project state:** Work in progress. Several screens and services are prototypes or partial implementations; consult [FEATURE_MATRIX.md](FEATURE_MATRIX.md) and [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) before relying on a capability. No emulator or device verification was available for the current audit.

APK rebuilding currently changes only the application label. It requires a keystore supplied by the user, signs with that certificate, and cannot preserve the original developer signature. General resource/image editing, DEX/Smali editing, and release signing of File Sizer itself are not available.

## Build from source

### Requirements

- JDK 17 or newer. The current workspace was built with Android Studio JBR 25.
- Android SDK Platform 35 and its build tools.
- The checked-in Gradle wrapper (Gradle 9.3.1).

### Windows PowerShell

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\OLI\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for local installation only; it is not a release signing configuration.

## Project documents

- [Product requirements](PRD.md)
- [Architecture audit](ARCHITECTURE.md)
- [Implementation plan](IMPLEMENTATION_PLAN.md)
- [Feature status](FEATURE_MATRIX.md)
- [Permission matrix](PERMISSION_MATRIX.md)
- [Dependency inventory](DEPENDENCY_LICENSES.md)
- [Test report](TEST_REPORT.md)
- [Security review](SECURITY_REVIEW.md)
- [Known limitations](KNOWN_LIMITATIONS.md)
- [Changelog](CHANGELOG.md)
