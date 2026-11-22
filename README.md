# Nimeko (stream) - Kotlin + Jetpack Compose

1. Create a PUBLIC GitHub repo and push this folder.
2. Make a signing key once:
   keytool -genkeypair -v -keystore ks.jks -alias nimeko -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 ks.jks
3. Repo Settings > Secrets > Actions: KEYSTORE_B64 (the base64 output) and KEYSTORE_PASS (your password).
4. git tag v1.0.0 && git push --tags -> the signed APK appears under Releases.
5. For updates, push v1.0.1 and so on. Installed apps check GitHub in the background and send a notification.

Local build: open the folder in Android Studio (Koala or newer) and run the app module.
