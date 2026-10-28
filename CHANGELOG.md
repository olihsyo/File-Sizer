# Changelog

All notable changes to File Sizer are documented here. Unreleased work is not a release-readiness claim; see `FEATURE_MATRIX.md` and `TEST_REPORT.md`.

## [Unreleased]

### Phase 2 — Permission-aware browsing
- Added SAF document-tree browsing with persisted URI grants and provider-reported metadata.
- Added content-URI routing/import for supported APK, archive, editor, hex and details flows.
- Added background local directory listing and surfaced provider/listing errors.

### Phase 3 — Safe file operations
- Added staged and verified local copy/replace, safer move fallback, name validation, progress, cancellation, and partial-operation reporting.
- Added same-tree SAF create/rename/copy/move/delete operations; cross-provider operations remain unavailable.

### Phase 4 — History and storage analysis
- Added additive SQLite schema migration for bounded operation history and retained recents/favorites.
- Bounded scan memory, reported unreadable directories, and confirmed duplicate groups with SHA-256.

### Phase 5 — Archive engine
- Hardened ZIP extraction/creation with path validation, entry/size/ratio limits, staging, integrity checks, cancellation, and progress.
- Kept the supported archive matrix explicitly limited to ZIP.

### Phase 6 — Text/code editor
- Added content URI reads, bounded UTF-8 editing, Save As, syntax highlighting, dirty-state protection, find/replace, undo/redo, multiple tabs, and supported external-change checks.

### Phase 7 — APK read-only analysis
- Added certificate SHA-256/subject reporting, exported analysis reports, and ARSCLib-based binary manifest/resource inspection.

### Phase 8 — Resource editing and rebuild
- Added one supported edit: application display label.
- Added private-copy APK rebuilding, JKS/PKCS12 key selection, apksig signing and verification, package identity/label checks, and verified SAF export. Original APK remains unchanged.
- General resource/image and DEX/Smali editing remain unsupported.

### Phase 9 — Hex Editor and developer utilities
- Added bounded hex-page navigation/search, format signatures, file hashing, XXE-hardened XML utilities, and text diff support.
- Hex inspection stays read-only.

### Hardening and documentation
- Disabled Android backup and removed unused notification permission declaration.
- Updated the feature, permission, dependency, limitation, test, security, architecture, and implementation documents to reflect current scope.

## [Baseline]

- Existing Kotlin/Jetpack Compose Android project retained, with the original application ID and navigation.
- Existing file browser, ZIP helpers, text editor, APK summary, read-only hex screen, and developer utilities serve as the starting implementation.
