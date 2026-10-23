# File Sizer — Permission Matrix

| Permission | API Level | Purpose | Justification & User Experience | Alternative if Denied |
| :--- | :--- | :--- | :--- | :--- |
| `READ_EXTERNAL_STORAGE` | API 26 - 32 | Browse files on legacy Android versions | Required for indexing and reading files outside sandbox | App cannot list root files without SAF picker |
| `WRITE_EXTERNAL_STORAGE` | API 26 - 29 | Write/create/modify files on Android 9 and lower | Required for legacy file operations | Read-only mode |
| `MANAGE_EXTERNAL_STORAGE` | API 30+ (Android 11+) | Broad file manager access | Core function of app as an advanced file & archive manager | User prompted contextually to select specific folders via SAF |
| `POST_NOTIFICATIONS` | API 33+ (Android 13+) | Background operation progress & completion alerts | Allows long-running compressions and copy jobs to notify user | In-app snackbars and progress indicators only |

*Note: File Sizer does not ask for contacts, camera, audio, location, phone, or internet permissions.*
