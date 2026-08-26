SURSAND CONNECT ANDROID PROJECT v1.4 FIXED

WHY THE PREVIOUS v1.4 FAILED
The previous patch replaced too large a section of MainActivity.java and accidentally
removed the NativeBridge inner class. The compiler therefore failed at:
new NativeBridge()

THIS FIX
- Rebuilt from the known v1.3 base.
- NativeBridge and all existing Android functionality are preserved.
- Only the existing JavaScript injection was extended for the lower navigation.
- Shop -> Emergency.
- Services -> City Connect.
- City Connect gets the WhatsApp-style green circular phone icon in lower navigation only.
- Correct launcher logo retained.
- Status-bar safe-area fix retained.
- Login privacy-text cleanup retained.

Version: 1.0.4 / versionCode 5
