# File Sizer — Current Architecture

This describes code that exists in the workspace, including deliberate limits. It is not a claim that every Android storage provider has been device-tested.

## Implemented structure

- **Application shell:** `MainActivity` hosts one Compose application, receives supported file intents, and routes external `content://`/`file://` inputs to the relevant screen. `FileSizerApp` owns navigation and permission lifecycle checks.
- **Presentation:** Compose feature screens hold most UI state directly. ViewModels, a formal domain/use-case layer, and lifecycle-resilient work coordination are not yet in place.
- **Design/settings:** Material 3 theme values are centralized. Theme, hidden-file preference, and last SAF tree URI are persisted with app preferences.
- **Storage:** Direct local paths and a `DocumentsContract` SAF tree provider coexist. SAF uses document URIs and provider metadata; it does not translate a document ID into a filesystem path. MediaStore is not implemented.
- **Operations:** `FileOperationService` performs streamed local create/copy/move/rename/delete/hash operations with staged writes, progress/cancellation, verification, conflict handling, and partial-failure reporting. SAF same-tree operations live in `DocumentTreeProvider`. There is no durable queue or process-death recovery.
- **Persistence:** `SQLiteOpenHelper` stores recents, favorites, and a bounded operation history. Schema v2 migrates additively; this is not Room and is not backed up by Android.
- **Storage analysis:** `StorageScanEngine` bounds top-file and duplicate candidates, limits traversal depth, detects cycles, reports unreadable directories, and hashes duplicate candidates before grouping.
- **Archives:** `ArchiveService` uses Java ZIP APIs, staged output, preflight path/resource limits, cancellation and integrity checking. ZIP is the only supported format; archive output/extraction is currently file-path based.
- **Editor:** Compose editor supports bounded UTF-8 documents, multiple tabs, syntax highlighting, undo/redo, find/replace, dirty-close protection, Save As, and local/content URI handling.
- **APK inspection/edit:** `ApkParser` uses `PackageManager` and ZIP metadata for APK analysis. `ApkResourceService` uses Apache-2.0 ARSCLib to decode binary manifests and preview resource values. Its only edit is the application label; it rebuilds a private copy and uses Android apksig for selected-key signing and verification.
- **Developer tools:** Hashing supports selected files/documents; Base64/URL, JSON/XML and line-diff tools operate on user-provided text. Hex display is paged and read-only.
- **Tests:** JVM unit tests cover selected service/validation paths. There are no Compose UI or instrumentation tests, and no Android device is attached.

## Remaining target architecture

The PRD calls for ViewModels, domain/use-case boundaries, storage/operation interfaces, lifecycle-aware background work, a full operation coordinator, Room or equivalent migration verification, and broader capability abstractions. Add those where a real feature requires them; do not treat empty package shells as implementation.
