# Build Hishob APK without Android Studio

You can use GitHub Actions to build the APK in the cloud. You need a GitHub account and a browser; you do not need to install Android Studio on your computer.

1. Sign in to GitHub and create a **new private repository** (recommended because this is a personal ledger app).
2. Upload the contents of the `KhataPersonal` folder into the repository. Make sure `.github/workflows/build-apk.yml` is included. If GitHub's upload page hides dot-folders, create the workflow file in GitHub using **Add file → Create new file** and name it `.github/workflows/build-apk.yml`, then paste the workflow from this project.
3. Open the repository's **Actions** tab. If prompted, enable Actions.
4. Select **Build Hishob APK** from the workflows list and press **Run workflow**.
5. Wait for the run to finish successfully. Open the completed run and download the **Hishob-debug-apk** artifact. GitHub downloads it as a ZIP file.
6. Extract that ZIP. It contains `app-debug.apk`. Transfer it to your Redmi phone and open it to install. If prompted, allow your file manager/browser to install unknown apps.

Security notes:
- Keep the repository private. Do not upload real customer/ledger data; this repository should contain source code only.
- This creates a debug APK for personal use, not a Play Store release.
- The APK has not been compiled or device-tested in this environment. Install and test it with sample records before relying on it for important data.
