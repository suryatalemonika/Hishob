# Hishob — personal Android ledger

This is a native Kotlin Android Studio project for a personal, offline-first khata app.

## Included
- Separate CREDIT and DEBIT buttons on each person's ledger
- Add a person from the phone's contact picker or enter details manually
- Per-person transaction history with date, amount, and optional note
- Create a PDF statement for one person
- Share that person's PDF through Android's Share menu (WhatsApp, email, etc., depending on apps installed)
- Local SQLite storage; no account or cloud service

## Build an APK
1. Install Android Studio on a computer and allow it to install Android SDK Platform 35.
2. Open this folder (`KhataPersonal`) in Android Studio.
3. Wait for Gradle sync to finish.
4. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Install the generated debug APK on your Redmi Note 15 Pro+ 5G. If Android asks, allow installation from the app you use to open the APK.

The project uses Android Gradle Plugin 8.7.3 and Kotlin 2.0.21. The first build requires internet access to download Gradle and dependencies.

## Notes
- Credit increases the amount receivable; debit decreases it. A negative balance is shown as payable.
- Amounts are stored as paise to avoid rounding issues.
- Contact selection uses Android's system contact picker and does not request broad contacts access.
- The PDF download button saves to the app-specific Documents folder. The share button opens Android's system share sheet and attaches only the selected person's PDF.
- This source project has not been compiled or tested on a physical device in this environment because Android SDK/Gradle are not installed here. Build and test it in Android Studio before relying on it for important records.

## Build without Android Studio
See `BUILD-APK-WITHOUT-ANDROID-STUDIO.md` for steps to build the APK using GitHub Actions in the cloud.
