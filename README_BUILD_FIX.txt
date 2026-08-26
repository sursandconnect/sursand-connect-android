SURSAND CONNECT ANDROID — BUILD FIX

GitHub build #26 failure:
- MainActivity imported androidx.core.content.FileProvider.
- The actual Android project did not resolve androidx.core:core, so javac reported:
  package androidx.core.content does not exist / cannot find FileProvider.

Fix:
- Removed AndroidX FileProvider dependency entirely.
- Added a small native ApkShareProvider inside this project.
- Share App still copies the currently installed APK to app cache.
- Share intent attaches the REAL Sursand-Connect.apk through EXTRA_STREAM.
- MIME type remains application/vnd.android.package-archive.
- It does NOT fall back to text-only sharing.

ICON:
- NO launcher icon, mipmap, adaptive-icon, drawable logo, or icon XML resource was
  edited in this build fix. The icon remains exactly as in the supplied previous project.

Version: 1.0.7 / versionCode 8
