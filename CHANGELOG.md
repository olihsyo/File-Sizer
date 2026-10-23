# Changelog

All notable changes to **File Sizer** are documented here.

## [1.0.0] - 2026-10-23
### Added
- **Phase 0 & 1:**
  - Modern Kotlin & Jetpack Compose single-activity architecture.
  - Material 3 Design System with brand green color scheme (`#16A765`, `#0B2B20`, `#E7F7EE`).
  - Adaptive launcher icons with measurement notch motif.
  - Full bilingual localization in English and Bengali (`values-bn`).
  - Home dashboard with real hardware storage telemetry via `StatFs`.
- **Phase 2 & 3:**
  - Full-featured recursive file browser with breadcrumbs, grid/list toggle, and live search.
  - Multi-selection mode with action toolbar (Copy, Move, Rename, Delete, Details).
  - Memory-safe streaming file operation engine with conflict policies (`RENAME_NEW`, `REPLACE`, `SKIP`).
  - SHA-256 calculation dialog with clipboard copy.
- **Phase 4:**
  - Deep recursive storage analyzer with category breakdown (Images, Videos, Audio, Docs, Archives, APKs, Others).
  - Largest files detector (Top 25) and duplicate file candidate detector (> 1MB).
  - Persistent SQLite database repository for Recent Files and Favorites.
- **Phase 5:**
  - Advanced ZIP archive engine for listing entries without unpacking to disk.
  - Zip Slip vulnerability protection with canonical path validation.
  - Configurable archive creation (Fast, Normal, Max compression).
  - CRC32 integrity check for archives.
- **Phase 6:**
  - Multi-document code & text editor with tab bar.
  - Syntax highlighting for Kotlin, Java, JSON, XML, and HTML.
  - Line numbers, font zoom controls, and Find & Replace bar.
- **Phase 7:**
  - APK Studio dashboard parsing package info, SDK versions, permissions, activities, and internal DEX files.
- **Phase 8:**
  - Memory-safe 64KB Hex Viewer with offset, hex bytes, and ASCII representation.
  - Developer Tools: MD5/SHA-1/SHA-256/SHA-512 generator, Base64 & URL encoder/decoder, JSON Formatter.
- **Phase 9:**
  - Comprehensive unit test suite (20 tests passed).
  - Production-ready debug APK packaging (`app-debug.apk`).
