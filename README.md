# VirtualAndro

VirtualAndro is a no-root Android work-profile manager for running untrusted APKs in a separate Android managed profile.

This is not a full emulator or hardware VM. Android does not
It may harm your device make sure before install.
# Disclaimer (Important)
in development stage..
Its not fully optimze for android,we are not taking any responsbility of any hardware or software damage.

## Links

- GitHub: https://github.com/Lovelydehar3
- LinkedIn: https://www.linkedin.com/in/lovepreet-singh-6200a8287/
- Email: lovepreetsingh73437@gmail.com


 let a normal third-party app securely run arbitrary APKs inside its own process with zero host risk. The closest supported no-root model is a managed work profile, where Android separates apps, app data, accounts, storage, and many cross-profile sharing paths from the personal profile.

## Security Model

- Internet is allowed in the sandbox profile so manga, streaming, and other content apps can work.
- Runtime permissions are denied by default through `DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY`.
- The work profile has camera, microphone unmute, screenshots, SMS, calls, account changes, location sharing, clipboard copy/paste, USB file transfer, debugging features, and personal-to-work sharing restricted where Android allows.
- Apps installed from the work-profile copy of Sandbox Vault are installed into the work profile, not the personal profile.
- Personal contacts, accounts, files, app data, and most profile-local package visibility are separated by Android's user/profile boundary.

## Limits

- This is not a malware-proof VM. The same physical device, OS kernel, firmware, and hardware are still shared.
- A malicious app with a kernel, driver, WebView, browser, or Android system exploit may still be able to escape profile boundaries.
- Network traffic is not MITM-inspected. Android work profiles isolate data, but this app does not decrypt or inspect HTTPS requests.
- APK installation may require enabling "Install unknown apps" for the work-profile copy of Sandbox Vault.

## Flow

1. Install and open Sandbox Vault in the personal profile.
2. Tap "Create sandbox work profile".
3. After provisioning, open the work-profile copy of Sandbox Vault.
4. Apply restrictions.
5. Pick and scan an APK.
6. Open the Android installer from inside the work profile.

## Build

The project targets Android 16 APIs:

- `compileSdk = 36`
- `targetSdk = 36`
- Android Gradle Plugin `8.9.1`
- Gradle `8.11.1`
- Kotlin `2.0.21`

You need JDK 17 or newer available through `JAVA_HOME`.
