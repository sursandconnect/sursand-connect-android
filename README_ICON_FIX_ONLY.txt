SURSAND CONNECT — UNCROPPED ICON FIX ONLY

Base:
Sursand_Connect_Android_Project_FINAL_SHARE_FIXED.zip (the working APK project).

Only launcher-icon resources were changed.

Source logo:
The project's original full Sursand Connect logo:
app/src/main/res/drawable-nodpi/sursand_connect_logo.png

Fix:
- Full logo is centered on a white square.
- Legacy launcher icons use 72% of the canvas.
- Adaptive launcher foreground uses only 56% of the canvas so Android's
  circle/squircle/rounded-square mask cannot cut the outer logo.
- No part of the source logo is cropped.
- APK file sharing and every other working app feature remain unchanged.

Version 1.0.8 / versionCode 9.
