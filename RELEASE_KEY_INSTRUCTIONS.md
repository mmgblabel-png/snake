Release key instructions

1) Generate a new release keystore (choose secure passwords and keep them safe):

# Run in PowerShell
keytool -genkeypair -v -keystore keystore/upload-keystore.jks -alias my_release_key -keyalg RSA -keysize 2048 -validity 9125

This will prompt you for passwords and certificate info. After generating, update `keystore/keystore.properties` (or create `keystore.properties` at project root) with the correct values.

2) Create `keystore.properties` (DO NOT commit it to git):

storeFile=keystore/upload-keystore.jks
storePassword=your_store_password
keyAlias=my_release_key
keyPassword=your_key_password

3) Recommended gitignore entries (add to .gitignore at repo root):

# Keystore and signing properties
keystore/upload-keystore.jks
keystore.properties

4) How build.gradle.kts uses this file
The project already reads `keystore.properties` at the project root and wires the `release` signing config if present. If you put `keystore.properties` in the repo root the signing will be used for release builds.

5) Uploading to Google Play
Use the generated AAB (`app/release/app-release.aab`) or run `./gradlew bundleRelease` and upload to Play Console. If you enrolled in Play App Signing, follow Play Console instructions to either upload the original key or let Play manage your key.

6) Keep backups
Store a secure offline backup of your keystore and passwords. Losing the keystore will prevent you from updating the app on Play Store with the same package name.

