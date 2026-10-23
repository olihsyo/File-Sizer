# File Sizer — Feature Matrix

| Feature ID | Description | Screen / Entry Point | Dependencies | Status | Test Coverage | Known Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `FEAT-001` | Adaptive Brand Identity & Icons | System Launcher / TopBar | Android Vector Drawables | **VERIFIED** | Visual check & manifest validation | None |
| `FEAT-002` | English & Bengali Localization | All Screens | Android Resources (`values`, `values-bn`) | **VERIFIED** | Resource compilation check | Additional dialects can be added later |
| `FEAT-003` | Real Hardware Storage Telemetry | Home / Storage Analyzer | `android.os.StatFs` | **VERIFIED** | Unit tested via `FormatUtilsTest` | Inaccessible system partitions excluded |
| `FEAT-004` | Navigation Shell & Routes | `MainActivity` | Navigation Compose | **VERIFIED** | `assembleDebug` clean compile | Deep-links require granted file permissions |
| `FEAT-005` | Permission Center Coordinator | Home / Browser | Android Settings Intent / SAF | **VERIFIED** | Runtime permission lifecycle check | Android 11+ requires special Settings page |
| `FEAT-006` | Recursive File Listing & Sorting | Browse (`FileManagerScreen`) | Filesystem API | **VERIFIED** | Sorting & Filter integration | Empty state shown when permission missing |
| `FEAT-007` | Multi-Selection & Action Toolbar | Browse (`SelectionToolbar`) | Compose UI State | **VERIFIED** | Verified with copy/move/delete | Bounded to selected directory |
| `FEAT-008` | Safe File Operations Engine | `FileOperationService` | Java NIO / Streams | **VERIFIED** | `FileOperationServiceTest` (5/5 tests pass) | Respects OS write permissions |
| `FEAT-009` | SHA-256 Checksum Calculator | Details (`FileDetailsDialog`) | `MessageDigest` | **VERIFIED** | Unit tested against known RFC vector | Large files stream in 8KB buffers |
| `FEAT-010` | Conflict Resolution Policies | `ConflictPolicy` | File Operation Engine | **VERIFIED** | Unit tested with `RENAME_NEW` | Supports Replace, Rename, Skip |
| `FEAT-011` | Storage Deep Scan & Category Breakdown | Storage Analyzer (`StorageScanEngine`) | Coroutines / File IO | **VERIFIED** | `StorageScanEngineTest` (2/2 tests pass) | Traversable directories only |
| `FEAT-012` | Largest Files & Duplicate Candidates | Storage Analyzer | `StorageScanEngine` | **VERIFIED** | Scan test verified largest order | Files > 1MB grouped by exact size |
| `FEAT-013` | Recent Files & Favorites DB | SQLite Helper & `DatabaseRepository` | Android SQLite | **VERIFIED** | Verified with bounded retention (50) | In-memory cache + persistent SQLite |
| `FEAT-014` | Local Text & Code Editor View | Code Editor (`CodeEditorScreen`) | Compose Text APIs | **IMPLEMENTED** | UI tested | Advanced syntax tokenization in Phase 6 |
| `FEAT-015` | APK Package Inspector View | APK Studio (`ApkStudioScreen`) | Android Archive API | **IMPLEMENTED** | UI tested | Binary XML decoding planned for Phase 7 |
| `FEAT-016` | App Preferences & Hidden Files | Settings (`SettingsScreen`) | Compose State | **IMPLEMENTED** | UI tested | Room persistence planned for Phase 4 |
