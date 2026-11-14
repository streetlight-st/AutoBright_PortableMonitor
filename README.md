# AutoBright PortableMonitor — S15T brightness probe

Android 17 / Samsung DeX hardware-brightness experiment for an external USB-C portable monitor. This is **a diagnostic app**, not yet an automatic brightness controller.

## Test sequence
1. Connect portable monitor while parked.
2. Open the app, tap **① 외부 디스플레이 검색**.
3. Tap **② 시스템 설정 수정 권한 허용** and grant permission.
4. Tap **③ 모니터 밝기 100%** and **모니터 밝기 20%**; visually check the monitor.
5. Optionally start the ambient light sensor, and copy diagnostic logs.

## GitHub Actions APK build
A push to main or manual workflow dispatch starts **Build S15T 0.3 APK**. Its result is available on Actions > corresponding run > Artifacts > **S15T-v03-installable-debug-apk**.
Unzip to get `app-debug.apk`. This app is for Android 17/API 37 and is signed with the standard debug key for testing.

Even if Android accepts a brightness command, Samsung DeX may not forward it to the portable monitor's actual backlight. Test in person. No Internet permission, analytics, or telemetry.
