# Homa AI — Android Native App

Professional Android wrapper for the Homa AI web application with native features.

## Architecture

```
Android Native Shell (Kotlin)
    ↓
Professional WebView (Homa Web App)
    ↓
Homa Worker API (Cloudflare)
    ↓
AI Providers / Database / Billing
```

## Configuration

| Property | Value |
|---|---|
| Application ID | `ai.homa.app` |
| App Name | هُما AI |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 24 |
| versionCode | 6 |
| versionName | 1.4 |

## Build Flavors

| Flavor | Market | Billing |
|---|---|---|
| `googleplay` | Google Play | Google Play Billing |
| `myket` | Myket | Myket SDK |
| `bazaar` | Bazaar | IAB v3 (same as Google Play) |

## Build Commands

```bash
# Google Play Release (AAB)
./gradlew bundleGoogleplayRelease

# Myket Release (APK)
./gradlew assembleMyketRelease

# Bazaar Release (APK)
./gradlew assembleBazaarRelease

# Debug build
./gradlew assembleDebug
```

## Signing

Create a keystore and set environment variables:

```bash
export KEYSTORE_PATH=/path/to/homa-release.jks
export KEYSTORE_PASSWORD=your_keystore_password
export KEY_ALIAS=homa
export KEY_PASSWORD=your_key_password
```

## Firebase Setup

1. Create a Firebase project at https://console.firebase.google.com
2. Add Android app with package name `ai.homa.app`
3. Download `google-services.json` and replace the placeholder file
4. Enable Cloud Messaging

## Native Features

- ✅ Professional WebView (JS, DOM Storage, Cookies, HTTPS-only)
- ✅ Back button with WebView history
- ✅ File Picker (PDF, DOC, XLS, CSV, PPT, Images)
- ✅ Image Picker (Android Photo Picker)
- ✅ Push Notifications (Firebase Cloud Messaging)
- ✅ Local Notifications (4 channels)
- ✅ Deep Links (`homa://` and HTTPS App Links)
- ✅ External browser for unknown domains
- ✅ Offline detection with retry
- ✅ Safe Area handling
- ✅ Network security config (HTTPS only, cleartext blocked)
- ✅ Splash Screen (Android 12+ system splash)
- ✅ Adaptive Icon
- ✅ Google Play Billing
- ✅ Myket Billing
- ✅ R8/ProGuard minification
- ✅ GitHub Actions CI/CD

## JavaScript Bridge

The web app communicates with native via `window.HomaNative`:

```javascript
// Check if running in Android app
if (window.HomaNative) {
  const info = JSON.parse(window.HomaNative.getAppInfo());
  // { version: "1.4", market: "googleplay", platform: "android" }
}

// Open file picker
window.HomaNative.openFilePicker();
window.addEventListener('homa_file_selected', (e) => {
  const { data, type } = e.detail;
  // data = base64, type = MIME type
});

// Open image picker
window.HomaNative.openImagePicker();
window.addEventListener('homa_image_selected', (e) => {
  const { data, type } = e.detail;
});

// Show notification
window.HomaNative.showNotification('عنوان', 'متن', 'homa_general');

// Start payment (uses native billing)
window.HomaNative.startPayment('plus', 'monthly');
window.addEventListener('homa_payment_result', (e) => {
  const { status, data, plan, period } = e.detail;
  // status: 'success' | 'cancelled' | 'error'
});

// Share text
window.HomaNative.shareText('متن برای اشتراک‌گذاری');

// Open external URL
window.HomaNative.openExternal('https://example.com');

// Check network
const online = window.HomaNative.checkNetwork();

// Vibrate
window.HomaNative.vibrate(200);
```

## Deep Links

| Scheme | Example | Route |
|---|---|---|
| `homa://` | `homa://chat` | `/chat` |
| `homa://` | `homa://payment` | `/payment-history` |
| HTTPS | `https://capable-aria-chat-flow.base44.app/chat/new` | `/chat/new` |

**Security:** Deep links to `/admin` and `/admin-panel` are blocked —
they require in-app authorization.

## Permissions

| Permission | Why |
|---|---|
| INTERNET | WebView network access |
| ACCESS_NETWORK_STATE | Offline detection |
| POST_NOTIFICATIONS | Push & local notifications (Android 13+) |
| CAMERA | Image capture (optional, feature not required) |
| VIBRATE | Alarm/notification vibration |
| SCHEDULE_EXACT_ALARM | Alarm scheduling |
| RECEIVE_BOOT_COMPLETED | Restore alarms after reboot |

## Privacy & Security

- ❌ No cleartext HTTP traffic
- ✅ Network Security Config (HTTPS only)
- ✅ WebView restricted to allowed domains only
- ✅ External links open in browser, not WebView
- ✅ No secrets in APK
- ✅ R8 minification in release
- ✅ Debuggable disabled in release
- ✅ Backup excludes auth tokens
- ✅ Deep links to admin blocked