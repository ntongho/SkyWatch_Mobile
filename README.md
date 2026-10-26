<div align="center">

<img src="apkpure_feature_graphic_1024x500.png" alt="Sky Watch: weather, air quality and the night sky" width="100%">

# Sky Watch for Android

**A mobile companion for weather, air quality, astronomy and the International Space Station.**

[![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Package](https://img.shields.io/badge/package-com.harbour.skywatch-102A43)](#app-identity)

[Privacy policy](https://ntongho.github.io/skywatch-privacy/) · [Support](mailto:harbouronsupport@gmail.com)

</div>

---

## Explore the sky

Sky Watch brings useful sky and weather information together in one Android app. Search for a city to view weather and air quality, browse NASA’s Astronomy Picture of the Day, follow the ISS, and revisit recent location lookups.

### What you can do

- **Search cities** and view current weather and air-quality information.
- **Browse NASA APOD** and move through available dates.
- **Track the ISS** and view position and predicted passes for a selected location.
- **Review History and Stats** for saved place lookups.
- **Get clear network feedback** for offline, connection, secure-connection and timeout problems.
- Enjoy a **branded launch screen**, adaptive launcher icon and dark, glass-inspired interface.

> Sky Watch uses its hosted HTTPS service for app data. The Android client does not connect directly to the database.

## Preview

The feature graphic above is included as `apkpure_feature_graphic_1024x500.png`. The selected app icon is `app_icon.png`, with Android launcher assets in `app/src/main/res/mipmap-*`.

## Built with

| Area | Implementation |
|---|---|
| Language | Kotlin 2.1.20 |
| UI | Jetpack Compose and Material 3 |
| Android build plugin | Android Gradle Plugin 8.9.2 |
| Minimum Android version | API 26 |
| Compile / target SDK | API 35 / API 35 |
| Networking | OkHttp 4.12 with bounded connect, read and total-call timeouts |
| Images | Coil Compose |
| Backdrop blur | Haze 1.6.5 |
| Backend | Hosted Sky Watch API over HTTPS |

## How the app is structured

```text
app/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/harbour/skywatch/
    │   ├── MainActivity.kt   # Compose screens, navigation and presentation
    │   └── SkyApi.kt         # Hosted API requests and client ID
    └── res/
        ├── drawable-nodpi/world_map.png
        ├── mipmap-*/         # Launcher icon densities
        └── values/           # Splash theme and icon colors
```

The app talks to `https://skywatch-x6yn.onrender.com/`. The hosted service handles database access and requests to data providers; credentials belong on the server and must not be embedded in the Android app.

## Getting started in Android Studio

> **Repository note:** This source bundle contains the Android `app` module and project documentation, but not the root Gradle wrapper/settings files. To build it, merge the `app/` directory into a complete Android Studio project, or add the missing root project files before opening it as a standalone repository.

1. Install Android Studio and a **JDK 17** Gradle runtime; install **Android SDK 35**.
2. Open a complete Android Studio project with the package ID `com.harbour.skywatch` (or create an **Empty Activity** project with that ID).
3. Copy this repository’s `app/` directory into that project, replacing the generated `app/` module. Keep the parent project’s `settings.gradle.kts`, root build file and Gradle wrapper.
4. In `settings.gradle.kts`, make sure plugin and dependency repositories include `google()`, `mavenCentral()` and `gradlePluginPortal()` as appropriate.
5. Turn **Gradle Offline Mode** off, then choose **File → Sync Project with Gradle Files**. Allow Gradle to download dependencies.
6. Build and run on an Android 8.0+ device or emulator with internet access.

The module is configured with `namespace` and `applicationId` set to `com.harbour.skywatch`. Keep this application ID unchanged after publishing; Android treats a changed ID as a different app.

## Data and privacy

The app creates a random installation-specific client ID and sends it to the hosted service for lookup history. City searches and selected place coordinates are used to provide weather, air-quality and location-specific features. The app does not request Android device-location permission and does not require an account.

**Important current limitations:** lookup history is retained by the service until an operator manually deletes it; the app has no in-app history deletion control; and the Android app currently does not show or copy the client ID needed to identify a history record for manual deletion. The ID is stored in app-private preferences, not encrypted preferences, and acts as the only access check for that history. Review the [published privacy policy](https://ntongho.github.io/skywatch-privacy/) for providers, retention, deletion and security details.

## Release checklist

- [ ] Sync and build successfully with Gradle online.
- [ ] Test the installed app, hosted API requests and offline/timeout messages.
- [ ] Verify the permanent package ID is `com.harbour.skywatch`.
- [ ] Increment `versionCode` for each release and set the intended `versionName` in `app/build.gradle.kts`.
- [ ] Generate a **signed release APK** in Android Studio and test that exact APK.
- [ ] Keep the release keystore and passwords backed up securely; reuse the same key for updates.
- [ ] Add real screenshots and listing materials; use the published privacy-policy URL.



## App identity

- **App name:** Sky Watch
- **Publisher:** harbour Team
- **Application ID:** `com.harbour.skywatch`
- **Support:** [harbouronsupport@gmail.com](mailto:harbouronsupport@gmail.com)
- **Website / API:** [skywatch-x6yn.onrender.com](https://skywatch-x6yn.onrender.com/)
- **Privacy policy:** [ntongho.github.io/skywatch-privacy](https://ntongho.github.io/skywatch-privacy/)
- Download link: https://apkpure.com/p/com.harbour.skywatch

---
**Look up. Stay curious.**
<div align="center">


<img src="https://github.com/user-attachments/assets/c6055c20-711a-426b-8a34-8e10d7dc3548" width="350" alt="01_overview" />
<img src="https://github.com/user-attachments/assets/8595faca-88c9-466f-9bb4-d71896792da9" width="350" alt="02_iss_tracker" />
<img src="https://github.com/user-attachments/assets/337ef065-ab4d-4971-901e-ff1ffb65c23a" width="350" alt="03_photo_of_the_day" />
<img src="https://github.com/user-attachments/assets/4be60ae2-6958-495c-9b3e-b1c510787e8a" width="350" alt="04_history" />
<img src="https://github.com/user-attachments/assets/9f51559e-c08b-43f3-abec-7b7f1f7cd661" width="350" alt="05_statistics" />

</div>
