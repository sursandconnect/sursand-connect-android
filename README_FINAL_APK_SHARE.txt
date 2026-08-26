SURSAND CONNECT ANDROID FINAL

Web baseline:
- Use Sursand_Connect_App_FINAL_FOR_APK.zip.
- It is the user-approved corrected web build.
- Representative label correction: "Representative (Name)" / "Representative Name" -> "Representative".
- Ward 1–19 and the 19 City Connect groups remain unchanged.

APK share:
- Native share now copies the currently installed APK from ApplicationInfo.sourceDir
  into the app cache and shares that actual .apk file using FileProvider.
- MIME type: application/vnd.android.package-archive.
- EXTRA_STREAM is used; this is not a text-only share.
- Recipient apps must themselves permit APK/file attachments.

No Home redesign is performed by this change.
