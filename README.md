# File Sizer

> **Advanced Android File Manager, Archive Studio, Code Editor & Developer Utilities**

File Sizer is an offline-first, native Android utility built using Kotlin, Jetpack Compose, and Material 3. It combines practical everyday file management with advanced archive processing, APK package analysis, and local text/code editing.

---

## Key Highlights
- **Vibrant & Polished UI:** Material 3 design system with dynamic theme switching, 8dp grid spacing, and responsive layouts.
- **Bilingual from the Core:** Native English and Bengali (`values-bn`) support across all screens.
- **Contextual Permission Handling:** Transparent explanation of storage requirements without annoying permission spam.
- **Real Storage Metrics:** Hardware metrics calculated via `StatFs` rather than fabricated mock data.
- **Zero Internet Requirement:** Completely offline for core storage management, safeguarding user privacy.

---

## Building from Source

### Prerequisites
- JDK 17 or JDK 21+ (bundled with Android Studio)
- Android SDK 35 with Build Tools 34.0.0+
- Gradle 9.3+ (handled via `gradlew`)

### Build Commands
```powershell
# Set environment variables if needed
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\OLI\AppData\Local\Android\Sdk"

# Run unit tests
.\gradlew.bat testDebugUnitTest

# Assemble debug APK
.\gradlew.bat assembleDebug
```
Output APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

---

## Documentation Index
- [Product Requirements Document (PRD)](PRD.md)
- [Architecture & Design Decisions](ARCHITECTURE.md)
- [Implementation Roadmap](IMPLEMENTATION_PLAN.md)
- [Feature Status Matrix](FEATURE_MATRIX.md)
- [Permission Matrix](PERMISSION_MATRIX.md)
- [Dependency Licenses Audit](DEPENDENCY_LICENSES.md)
