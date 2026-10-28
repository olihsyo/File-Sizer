# File Sizer — Permission Matrix

This describes the manifest and user-driven document pickers in this workspace. Android/provider behavior still requires device verification.

| Permission or capability | Platform | Current use | If unavailable |
| :--- | :--- | :--- | :--- |
| `READ_EXTERNAL_STORAGE` | API 26–32 | Declared through API 32 for legacy direct-path browsing | Use a folder or file selected through Android's document picker where supported |
| `WRITE_EXTERNAL_STORAGE` | API 26–29 | Declared through API 29 for legacy direct-path operations | Direct-path writes may fail; use SAF for a selected destination when the action supports it |
| `MANAGE_EXTERNAL_STORAGE` | API 30+ | Declared; settings screen requests it only after a user action | Direct-path browsing is limited; the user-selected SAF folder remains available |
| SAF `ACTION_OPEN_DOCUMENT_TREE` | API 21+ | User selects a directory; read/write URI grants are persisted for future browsing | User can choose another folder; the app does not invent a filesystem path |
| SAF `ACTION_OPEN_DOCUMENT` | API 19+ | User selects an APK, keystore, or supported text/document file; URI streams are used | The feature reports that the selected document cannot be read |
| SAF `ACTION_CREATE_DOCUMENT` | API 19+ | User chooses destinations for reports, editor Save As, and signed APK export | Export is canceled; the source remains unchanged |

## Notes

- `MANAGE_EXTERNAL_STORAGE` is a restricted Android capability. Store distribution eligibility has not been reviewed.
- Selecting an SAF folder does not grant access to every location on the device. Provider flags, rename/delete support, and URI persistence vary.
- Copy/move between local filesystem paths and SAF trees is not implemented. The current browser supports same-provider operations.
- `POST_NOTIFICATIONS`, network, contacts, camera, microphone, and location permissions are not declared.
- The app disables Android backup because its history can contain user file names/URIs and local operation metadata.
