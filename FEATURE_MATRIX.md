# File Sizer — Feature Matrix

`IMPLEMENTED` means a code path exists. It does not mean every device/provider edge case is verified. `VERIFIED` requires the PRD's acceptance evidence; no end-to-end Android feature is currently marked verified because this workspace has no emulator or connected device.

| Feature ID | Description | Entry point | Status | Evidence | Known limits |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `FEAT-001` | Brand identity and launcher icon | Launcher / toolbar | IMPLEMENTED | Vector resources are present and resource processing succeeds | No device/launcher visual review |
| `FEAT-002` | English and Bengali localization | Android resources / settings | PARTIAL | English and Bengali resource bundles compile | Many UI labels remain literals; no in-app language selector |
| `FEAT-003` | Storage capacity summary | Home | IMPLEMENTED | Uses filesystem capacity APIs | No device measurement; inaccessible locations may be unavailable |
| `FEAT-004` | Navigation and external file intents | App shell | PARTIAL | Navigation routes accept local and `content://` inputs | No navigation UI tests; process recreation and malformed intents not device-verified |
| `FEAT-005` | Permission-aware storage access | Browser / SAF picker | PARTIAL | Legacy/all-files access and persisted user-selected document-tree URI | `MANAGE_EXTERNAL_STORAGE` remains; protected paths and providers vary; no MediaStore/Shizuku |
| `FEAT-006` | Browse, sort, search, select | File manager | PARTIAL | Direct filesystem and SAF tree listings use provider metadata; list work runs off the UI thread | Search is limited to the opened location; no pagination or cross-provider browse |
| `FEAT-007` | Multi-selection actions | File manager | PARTIAL | Selection and supported operation controls exist | Cross-provider copy/move and a general destination picker are unavailable |
| `FEAT-008` | Create/copy/move/rename/delete | File manager / operation service | PARTIAL | Staging, verification, safer replacement, cancellation, progress, and partial results; local operation tests | No persistent queue/recovery; provider behavior requires device checks |
| `FEAT-009` | File checksums | Details / developer tools | IMPLEMENTED | SHA-256 and selected-file hash helpers stream data | No device/UI verification |
| `FEAT-010` | Destination conflict handling | File operations | PARTIAL | Local rename/replace/skip policies and SAF rename-on-conflict behavior | SAF does not expose the full local policy set |
| `FEAT-011` | Storage scan and categories | Storage analyzer | PARTIAL | Bounded-memory traversal, depth/cycle limits, unreadable-directory counts | Filesystem path scan only; permission-limited directories may be absent |
| `FEAT-012` | Largest files and duplicate detection | Storage analyzer | PARTIAL | Largest 25 kept in bounded memory; duplicate groups compare SHA-256 | Candidate scan is capped; inaccessible and beyond-depth files are omitted |
| `FEAT-013` | Recents, favorites, operation history | Home / details | PARTIAL | SQLite v1→v2 additive migration; favorite actions and bounded recent history | SQLiteOpenHelper rather than Room; only wired operation paths are recorded; no device migration test |
| `FEAT-014` | Archive listing | Archive viewer | PARTIAL | ZIP entries are read without extracting | ZIP only; folder hierarchy display is flat |
| `FEAT-015` | Safe archive extraction | Archive viewer | PARTIAL | Traversal checks, expansion limits, staging/cleanup, cancellation and progress; unit tests | SAF destination extraction, encrypted archives and other formats are unsupported |
| `FEAT-016` | Archive creation | File manager | PARTIAL | ZIP output uses temporary staging and refuses overwrite | ZIP only; no password, split volumes, or device verification |
| `FEAT-017` | Archive integrity testing | Archive viewer | PARTIAL | ZIP CRC/stream checks and bounded decompression | ZIP only; no fuzz or Android-device tests |
| `FEAT-018` | Text/code editing | Editor | PARTIAL | Bounded local/content URI reads, syntax highlighting, Save As, dirty-state handling, undo/redo | UTF-8 and small documents only; provider save atomicity varies |
| `FEAT-019` | Line numbers, find/replace, tabs | Editor | PARTIAL | Multiple documents, line numbers, search/replacement, bounded history | Line-number rendering is capped; no UI interaction tests |
| `FEAT-020` | APK metadata, manifest and resources | APK Studio | PARTIAL | PackageManager metadata plus ARSCLib binary manifest/resource-table inspection | Split/obfuscated APK edge cases and device execution unverified; archive list is bounded |
| `FEAT-021` | Read-only hex inspection | Hex viewer | PARTIAL | Bounded 64 KiB pages, offset jump, visible-page search, signatures | No hex editing or Save As; search covers the loaded page only |
| `FEAT-022` | Hash tools | Developer tools | PARTIAL | File/content URI hashing streams data and offers several algorithms | No device/UI verification |
| `FEAT-023` | Base64 and URL tools | Developer tools | IMPLEMENTED | Text encode/decode helpers have round-trip tests | Text only; no device/UI verification |
| `FEAT-024` | JSON/XML utilities and text diff | Developer tools | PARTIAL | JSON format, XXE-hardened XML validation/formatting, line-based diff | Diagnostics and diff algorithm are basic; UI not device-verified |
| `FEAT-025` | APK label edit, rebuild and sign | APK Studio | PARTIAL | Private copy, selected JKS/PKCS12 key, stale-signature removal, apksig signature verification and package/label checks | Only the application label is editable; no image/generic XML/DEX editing; no Android-device execution test |

## Not implemented

Shizuku, MediaStore-based browsing, durable background operations, ZIP passwords/encryption/split volumes, TAR/7z/RAR, general APK resource/image editing, DEX/Smali editing, generic hex editing, signing-key creation, and Compose/instrumentation tests are not implemented.
