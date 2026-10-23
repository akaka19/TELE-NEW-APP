# HiPath Directory — Phone Management System

Native Kotlin Android app for offline telephone-extension management using SQLite.

## Features
- Add, view, edit and delete contact records.
- Required unique Phone Number / Extension.
- Name, Beneficiary, Service / Department, Building, Floor, Office, Boîte / Mailbox, HiPath Port and Port H.
- Instant search across every contact field.
- Dial through Android's dialer and copy numbers.
- SIP audio calling screen powered by Liblinphone: account credentials, server/domain, port, UDP/TCP/TLS transport, outgoing calls, incoming call controls, mute and speaker.
- Dark / Light theme toggle.
- Optional PIN protection using a random salt and PBKDF2-HMAC-SHA256 hash; PIN is not stored in plain text.
- Optional AndroidX biometric prompt when the device supports enrolled biometrics.

## GitHub Actions build
1. Extract `HiPathDirectory.zip`.
2. Create a GitHub repository named `HiPathDirectory`.
3. Upload the **contents** of the extracted folder. Ensure `.github/workflows/android.yml` is at the repository root.
4. Open **Actions** → **Build Android APK** → **Run workflow**.
5. Wait for the workflow to finish successfully.
6. Download the `HiPathDirectory-debug-apk` artifact from the run and extract it.
7. Install `app-debug.apk` on your Android phone. If asked, allow installs from the app used to open the APK.

## Local build
Requires JDK 17, Gradle 8.9 and Android SDK platform 35:
`gradle assembleDebug`

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Compile SDK 35, target SDK 34, minimum SDK 24. No INTERNET permission is declared. GitHub Actions uses internet only to fetch build dependencies. Debug APK is for testing; a production release should be signed.


## SIP calling setup

Open the side menu → **SIP Calling**, then enter your SIP username/extension, SIP server or domain, password, port (commonly 5060), and transport supported by your PBX. Tap **Save account & Connect**, wait for the registration status, then enter an extension and tap **Call**. From a contact card, **Call with SIP** opens the SIP screen with that extension filled in. Android will ask for microphone permission before audio calling.

The SIP library is Liblinphone (`org.linphone:linphone-sdk-android`). Review its GPLv3/commercial licensing terms before distributing this build. Standard SIP servers must allow the selected transport and compatible audio codecs. Incoming calls while the app is closed/backgrounded are not guaranteed in this initial integration; reliable background ringing generally needs push notifications and/or a properly configured foreground calling service.
