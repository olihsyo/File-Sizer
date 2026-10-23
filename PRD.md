# File Sizer — Product Requirements Document (PRD)

**Version:** 2.0  
**Status:** In Active Development (Phase 1 Completed)  
**Platform:** Native Android (Kotlin, Jetpack Compose, Material 3)  
**Target SDK:** 35 | **Minimum SDK:** 26  
**Primary Locales:** English (`en`), Bengali (`bn`)  
**Design Philosophy:** Offline-first, Zero-mockup, Safe file operations, Honest capability reporting.

---

## 1. Product Summary
File Sizer is an advanced, high-performance Android utility combining:
1. **Intelligent File Manager:** Breadcrumb navigation, recursive directory exploration, safe copy/move/delete, file details, and SHA-256 calculation.
2. **Advanced Archive Studio:** ZIP / TAR / GZIP / 7z exploration, validation, and safe extraction with strict path-traversal (Zip Slip) defenses.
3. **Local Text & Code Editor:** Multi-document state tracking, line numbers, dirty-state indicators, syntax highlighting, and atomic saving.
4. **APK Inspection Studio:** Read-only inspection of Android packages, decoded `AndroidManifest.xml`, declared permissions, activities, and signing certificates.
5. **Storage & Capacity Analyzer:** Real hardware/system disk space metrics via `StatFs`, category-wise distribution, and duplicate candidate identification.
6. **Permission Center:** Explicit, transparent permission management handling Scoped Storage, `MANAGE_EXTERNAL_STORAGE`, and SAF fallback trees.

---

## 2. Core User Personas
* **Everyday Android User:** Needs clear visual cues, responsive directory navigation, intuitive file searching, and safe operations.
* **Archive Power User:** Needs fast compression, extraction, archive exploration without full unpacking, and password support where verified.
* **Android Developer / Student:** Inspects APK manifests, debugs resource structures, checks certificate fingerprints, and edits configuration/code files.

---

## 3. Scope Boundaries & Honest Reporting
* File Sizer does **not** claim to bypass Android system protections or read protected sandboxes (`Android/data` on newer OS versions without system SAF or Shizuku).
* Unsupported actions (e.g. proprietary encrypted RAR modifications) are explicitly reported as unsupported with clear technical explanations.
* File Sizer operates completely offline with zero telemetry or network dependencies for core storage operations.
