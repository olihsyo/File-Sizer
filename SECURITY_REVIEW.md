# File Sizer — Security Review

**Review type:** Static source review and selected JVM tests  
**Date:** 2026-10-23  
**Status:** Improvements are implemented; this is not a device or release security approval.

## Previously identified issues addressed

| Finding | Current handling | Remaining verification |
| :--- | :--- | :--- |
| Replace-copy could destroy the old destination before a new copy completed | Local copy stages and verifies data before replacement, with backup/restore behavior where rename is supported | Provider and device failure cases |
| Move fallback could delete source after an incomplete copy | Copy verification and incomplete directory errors happen before source deletion; partial move is reported | Device/provider behavior |
| ZIP extraction lacked limits and could overwrite existing output | ZIP preflight and streamed expansion limits; extraction stages output, checks integrity, and cleans on failure | Fuzzing and provider/device testing |
| ZIP creation wrote directly to the final path | Creation writes a staged file and refuses an existing output | Device/storage interruption testing |
| App backup could include paths/history | `allowBackup=false`; history migration preserves current local database but is not backed up | Confirm OEM backup behavior on devices |
| External `content://` inputs were passed as `File` paths | Content URIs are read through `ContentResolver`, imported to bounded private cache where required, and exported with read-back hash verification | Provider matrix and revocation handling |
| No user-selected storage alternative existed | SAF tree selection and persisted grants are available | Android/provider behavior varies; no device test |

## Current risks and release blockers

| Priority | Risk | Impact / handling |
| :--- | :--- | :--- |
| High | Broad storage access remains declared | `MANAGE_EXTERNAL_STORAGE` can expose broad shared storage and is policy-restricted. User-selected SAF access is available, but distribution eligibility and policy need review. |
| High | APK signing and resource rebuilding have no Android-device test | The implementation uses ARSCLib and apksig, stages a copy, removes old signatures, verifies the new signature, and checks package/label metadata. Until tested on supported Android versions and real APKs, treat the build/edit flow as experimental. |
| Medium | APK and archive parsers process untrusted files | APK editing is capped at 256 MiB and preflights entry counts, expanded size and manifest/resource table size. ZIP paths and expansion are limited. Fuzzing and adversarial corpus testing have not been done. |
| Medium | User keystore is sensitive | Keystore bytes and credentials are imported for the operation only; passwords are held transiently, cleared after use, and not saved by the app. Key material remains user-selected and its handling depends on the source document provider. |
| Medium | SAF write atomicity varies | Newly created outputs are streamed and compared by SHA-256 after write; cleanup is attempted on failure. Some providers may not offer transactional writes or reliable delete. |
| Medium | Storage scans and operations are bounded but not durable | Cancel works while the process is alive; Android process termination can interrupt work. History does not reconstruct an operation. |
| Medium | Third-party APK results vary | Android PackageManager and ARSCLib may not decode obfuscated, split, protected, malformed, or future APK formats completely. The UI displays only supported/parsed information and limits edit scope to application label. |
| Medium | Release build uses debug signing | Release APK must not be distributed until a production signing configuration and key-management plan are established. |

## Positive observations

- No network permission, analytics SDK, or cloud service is declared.
- Android backup is disabled.
- Local replace, move fallback, and archive output use staging or integrity checks instead of destructive direct writes.
- APK signing uses an explicitly selected key and the UI explains that the new certificate differs from the original.
- SAF content is kept as URI data; document IDs are not guessed into filesystem paths.

## Scope limits

This review did not run a mobile security scanner, fuzz parsers, test Android devices/providers, inspect every transitive dependency/license, or assess Google Play eligibility. Close the device, dependency, policy, and release-signing items before release.
