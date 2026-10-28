# File Sizer — Implementation Roadmap

Status is based on code in this workspace. `PARTIAL` means the requested capability has a working implementation with known scope or verification gaps; it does not mean the phase meets release acceptance.

| Phase | Title | Status | Evidence and remaining work |
| :--- | :--- | :--- | :--- |
| **0** | Workspace audit | **COMPLETED** | Existing Android project was preserved. Toolchain, architecture, Git state, and existing features were inspected. No emulator or device is attached. |
| **1** | Foundation and design system | **PARTIAL** | Existing Compose shell and theme remain. Theme and hidden-file preferences persist. Many screen labels are still hard-coded; device and accessibility review remain. |
| **2** | Permission-aware file browsing | **PARTIAL** | Direct filesystem browsing and user-selected SAF tree browsing use real metadata/URIs; persisted tree access and permission error states exist. MediaStore, cross-provider operations, full search scope controls, and device verification remain. |
| **3** | Safe file operations | **PARTIAL** | Local and same-tree SAF operations include staged/verified copies, safer replace, move fallback verification, progress/cancellation, and partial-failure reporting. No durable operation queue, process-death recovery, general destination picker, or provider interoperability. |
| **4** | History and storage analysis | **PARTIAL** | Recent files, favorites, and bounded operation history persist in a non-destructive SQLite migration. Storage scans bound memory, detect unreadable directories, and confirm duplicate groups by SHA-256. Room, broader repository tests, and device scan verification remain. |
| **5** | Archive engine | **PARTIAL** | ZIP list/create/extract/test use staging, traversal checks, expansion limits, progress, and cancellation. ZIP is the only format; SAF extraction/creation, password/encryption, split volumes, and navigable archive hierarchy remain unsupported. |
| **6** | Text/code editor | **PARTIAL** | Content/local URI reads, bounded text size, Save As, dirty-state protection, find/replace, bounded undo/redo, tabs, syntax highlighting, and available external-change checks exist. Encoding controls, draft recovery, full large-file support, device/UI verification remain. |
| **7** | APK read-only analysis | **PARTIAL** | Package metadata, declarations, signing fingerprints, hash, bounded entry list, binary manifest decoding, and resource-table preview exist. APK variants, obfuscated/resource edge cases, and device verification remain. |
| **8** | Resource editing and rebuild | **PARTIAL** | The application-label field is editable. The service rebuilds a private copy, removes stale signatures, signs with a user-selected JKS/PKCS12 key, verifies the signature and package identity, and checks the label. General image/XML/resource editing, DEX/Smali work, signing-key creation, and Android-device execution tests remain. |
| **9** | Hex Editor and developer tools | **PARTIAL** | Read-only 64 KiB hex pages, visible-page search, offset navigation, signature inspection, streaming file hashes, Base64/URL tools, JSON/XML utilities, and line diff exist. Hex byte editing/Save As and UI/device verification remain. |
| **10** | Shizuku integration | **NOT STARTED** | No Shizuku dependency or adapter. |
| **11** | Regression, performance, and security | **PARTIAL** | Selected JVM business-logic tests and static lint/security review exist. No instrumentation/UI tests, device profiling, complete dependency/SBOM audit, or device permission matrix. |
| **12** | Release readiness | **NOT STARTED** | Release signing still uses the debug signing configuration. Store-policy, privacy, device compatibility, and release artifact verification remain. |

## Verification policy

- Keep implementation status separate from device verification status.
- The current test report is in `TEST_REPORT.md`; lint and packaging results must be recorded only from completed Gradle task outputs.
- Do not mark Android storage, APK rebuild/signing, or Compose interactions verified without supported-device evidence.
- Phase 8 is deliberately limited to a label change. The original APK remains untouched and the new certificate is not the original developer's key.
