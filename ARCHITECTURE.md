# File Sizer — Architectural Specification

## Architectural Pattern: Clean Architecture + Unidirectional Data Flow (UDF)

### 1. Presentation Layer (Jetpack Compose + Material 3)
* **Single Activity:** `MainActivity.kt` sets up edge-to-edge system insets and delegates to `FileSizerApp.kt`.
* **Navigation:** `NavHost` driven by typed `Screen` routes with saved state restoration.
* **Design System:** Custom token system based on Brand Green (`#16A765`, `#0B2B20`, `#E7F7EE`) with comprehensive Dark/Light theme switching and font scaling.
* **Localization:** English and Bengali string resources mapped across all UI components.

### 2. Core & Operations Layer
* **Storage Abstraction:** Separation between direct `java.io.File` / NIO operations and Android Storage Access Framework (`DocumentFile` / `DocumentsContract`).
* **Operation Coordinator:** Dedicated service handling asynchronous queueing, bounded streaming buffers, progress estimation, cancellation tokens, and collision policies (Replace, Rename, Skip).
* **Security & Path Resolution:** Canonical path verification preventing Zip Slip attacks during archive extractions.

### 3. Data & Platform Layer
* **Storage Providers:**
  * Direct Filesystem Provider (for accessible external and primary storage).
  * SAF DocumentTree Provider (for user-selected directories and removable SD cards).
  * MediaStore Provider (for shared media querying).
* **Database (Room/SQLite):** Stores persistent bookmarks, favorites, operation records, and recent metadata.
