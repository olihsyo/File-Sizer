# File Sizer — Known Limitations

These are limits of the code currently in this workspace. They are not promises that all supported devices/providers behave identically.

## Storage and operations

- Direct filesystem browsing still uses Android storage permissions, including restricted all-files access on newer Android. SAF covers a user-selected folder; MediaStore and Shizuku are not implemented.
- Android/data, Android/obb, removable storage, revoked grants, and provider-specific capabilities remain platform-dependent.
- SAF operations currently work inside the selected tree. Cross-provider copy/move and full destination conflict choices are unavailable.
- Search is limited to the opened location. There is no device-wide index, provider pagination, or durable operation queue.
- Operations can be canceled while active, but do not survive process termination and have no process-death recovery.
- Storage scans are depth/candidate bounded. Permission-limited directories may not be counted. Duplicate results require hash confirmation but candidate caps can omit files.
- Operation history stores bounded metadata in SQLite, not Room. It can include file names and URI/path references; Android backup is disabled.

## Archives

- ZIP is the only supported archive type. TAR, 7z, RAR, passwords, encryption, split volumes, and symlink preservation are unavailable.
- Archive creation/extraction uses private staging and expansion limits. SAF archive output/extraction is not implemented.
- Archive entries are listed flat rather than as a navigable folder tree.

## Editor and developer tools

- The text editor is UTF-8 and limited to 2 MiB. It has Save As and provider-aware reads/writes, but some providers cannot guarantee atomic replacement.
- Dirty-state confirmation and external-change checks cover supported flows; there is no draft recovery, encoding selector, or process-death recovery.
- Hex inspection is read-only, loads a 64 KiB page, and searches that page only. Hex editing and Save As are unavailable.
- XML diagnostics and line diff are basic. There is no programming-language compilation or execution.

## APK Studio

- Read-only analysis parses package metadata, signatures, binary manifest XML and a bounded resource-table preview. Obfuscated, split, protected, malformed, or novel APKs may fail or return incomplete metadata.
- APK modification is restricted to changing the application label. General image/XML/resource replacement and DEX/Smali editing are unsupported.
- Rebuild requires a user-selected JKS or PKCS12 keystore. The original developer signing key cannot be recovered; the rebuilt APK gets the selected certificate and normally cannot update an installation signed by the original developer.
- Rebuild is limited to 256 MiB inputs and base APKs. APK signing, package/label checks, and resource parsing compile, but have not been run on an Android device.
- Signing-key files and passwords are imported only for the operation. Password state is cleared after the attempt; the app does not save a keystore or credentials.

## App and release

- Many visible labels remain English literals despite English and Bengali resource bundles; there is no in-app language selector.
- No emulator or physical device was available. SAF, permission denial/revocation, archive compatibility, APK resource rebuild/signing, UI/accessibility, and performance behavior remain unverified on-device.
- Compose UI/instrumentation tests are not present. JVM tests cover selected service and validation paths.
- The release build type still uses debug signing and is not suitable for distribution.
- Transitive dependency licensing/SBOM, current store-policy eligibility, privacy review, and release signing remain open.
