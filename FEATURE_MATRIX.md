# File Sizer — Feature Matrix

| Feature ID | Description | Screen / Entry Point | Dependencies | Status | Test Coverage | Known Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `FEAT-001` | Adaptive Brand Identity & Icons | System Launcher / TopBar | Android Vector Drawables | **VERIFIED** | Visual check & manifest validation | None |
| `FEAT-002` | English & Bengali Localization | All Screens | Android Resources (`values`, `values-bn`) | **VERIFIED** | Resource compilation check | Additional dialects can be added later |
| `FEAT-003` | Real Hardware Storage Telemetry | Home / Storage Analyzer | `android.os.StatFs` | **VERIFIED** | Unit tested via `FormatUtilsTest` | Inaccessible system partitions excluded |
| `FEAT-004` | Navigation Shell & Routes | `MainActivity` | Navigation Compose | **VERIFIED** | `assembleDebug` clean compile | Deep-links require granted file permissions |
| `FEAT-005` | Permission Center Coordinator | Home / Browser | Android Settings Intent / SAF | **VERIFIED** | Runtime permission lifecycle check | Android 11+ requires special Settings page |
| `FEAT-006` | Recursive File Listing & Sorting | Browse (`FileManagerScreen`) | Filesystem API | **IMPLEMENTED** | UI tested | Empty state shown when permission missing |
| `FEAT-007` | Storage Category Breakdown | Storage Analyzer | Android Environment | **IMPLEMENTED** | UI tested | Full content scan planned for Phase 4 |
| `FEAT-008` | Local Text & Code Editor View | Code Editor (`CodeEditorScreen`) | Compose Text APIs | **IMPLEMENTED** | UI tested | Advanced syntax tokenization in Phase 6 |
| `FEAT-009` | APK Package Inspector View | APK Studio (`ApkStudioScreen`) | Android Archive API | **IMPLEMENTED** | UI tested | Binary XML decoding planned for Phase 7 |
| `FEAT-010` | App Preferences & Hidden Files | Settings (`SettingsScreen`) | Compose State | **IMPLEMENTED** | UI tested | Room persistence planned for Phase 4 |
