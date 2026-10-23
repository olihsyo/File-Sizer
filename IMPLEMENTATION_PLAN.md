# File Sizer — Implementation Roadmap

| Phase | Title | Scope | Status | Verification Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **0** | Workspace Audit & Environment Setup | Toolchain setup, git repo, gradle wrapper, local SDK path | **COMPLETED** | Gradle initializes, git clean status |
| **1** | Foundation & Design System | Brand assets, adaptive launcher icon, typography, M3 color tokens, localization (EN/BN), navigation shell, baseline tests | **COMPLETED** | `assembleDebug` builds cleanly, Unit tests pass |
| **2** | Storage Abstraction & File Browser | Recursive file listing, breadcrumbs, sort/filter, file-type icons, multi-selection, contextual permissions | **COMPLETED** | Tested on real storage paths |
| **3** | Safe File Operations Engine | Copy, move, delete, rename, create folder, conflict resolution dialogs, background progress | **COMPLETED** | Integration tests with temporary file tree pass cleanly |
| **4** | Storage Analyzer & Room/SQLite DB | Capacity metrics, category analysis, duplicates, favorites, recent files | **COMPLETED** | Unit tests pass, real DB schema verified |
| **5** | Archive Engine | ZIP/TAR/GZ exploration, extraction, path traversal defense | **PLANNED** | Malformed archive and Zip Slip unit tests |
| **6** | Code & Text Editor | Multi-document tab state, syntax highlighting, dirty tracking | **PLANNED** | Document edit and save verification |
| **7** | APK Inspection Studio | Manifest parser, certificate fingerprints, resource explorer | **PLANNED** | Verified against sample APKs |
| **8** | Hex Viewer & Developer Tools | Byte view, hash calculators (SHA-256, MD5), Base64 tool | **PLANNED** | Input validation and hash tests |
| **9** | Release Readiness & Hardening | Lint audits, release packaging, license validation | **PLANNED** | Final release build signed & verified |
