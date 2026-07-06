# Deep links: shared schedules

The web planner ([atcupps/Jupiterp](https://github.com/atcupps/Jupiterp), PR #933) shares a
schedule as a compact token in the `s` query parameter:

```
https://jupiterp.com/?s=2~CMSC4Aq8z.MATH8nP2k.ENGL1xR0w
```

Opening one of these links on a device with the app installed opens the app instead of the
browser. The app decodes the token, re-fetches the courses from the API, saves the result as a
new schedule named **"Shared schedule"** (numbered if that name is taken), and opens the
saved-schedules sheet so the user can switch to it. The user's current schedule is never
replaced.

## Token format (schema v2)

`2` `~` segment `.` segment `.` … — one course per segment, `~` separates the schema version,
`.` separates segments.

A standard course (`DEPT` + 3-digit number + optional 1-letter suffix, 4-digit section) packs
into `DEPT` + a fixed 5-char base62 token holding a 29-bit integer laid out as
`[number:10 | suffix:5 | section:14]` — e.g. `CMSC131` section `0101` → `CMSC4Aq8z`. Anything
else falls back to the literal `courseCode-sectionCode` form (which is also all schema v1
emits). The decoder in
`composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/deeplink/ShareLink.kt` is a
direct port of the web app's `ShareLink.ts` and understands both versions.

## What the app registers

- **Android** (`composeApp/src/androidMain/AndroidManifest.xml`): an
  `autoVerify` App Links intent filter for `https://jupiterp.com` and
  `https://www.jupiterp.com`, plus a `jupiterp://` custom-scheme fallback.
- **iOS** (`iosApp/`): Associated Domains entitlement `applinks:jupiterp.com` /
  `applinks:www.jupiterp.com` (Universal Links), plus a `jupiterp://` custom URL scheme in
  `Info.plist`.

Both platforms funnel the URL into `DeepLinkHandler.onDeepLink(url)` in common code
(`MainActivity` on Android, `iOSApp.swift` on iOS), and `MainViewModel` performs the import.

## Server-side requirement (jupiterp.com)

OS-level link interception only activates once jupiterp.com publishes the association files.
Until then, `jupiterp://` links still work everywhere, Android < 12 offers the app in the
open-with chooser, and users can enable the domain manually under
*App info → Open by default*. These files belong in the website repo
(`atcupps/Jupiterp`), served over HTTPS with no redirects:

### `https://jupiterp.com/.well-known/assetlinks.json` (Android)

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.jupiterp.jupiterpmobile",
      "sha256_cert_fingerprints": ["<SHA-256 fingerprint of the release signing cert>"]
    }
  }
]
```

Get the fingerprint from Play Console → *Setup → App signing* (use the **app signing key**
certificate, since Play re-signs uploads), or for a local keystore:
`keytool -list -v -keystore <keystore> | grep SHA256`.

### `https://jupiterp.com/.well-known/apple-app-site-association` (iOS)

Served as `application/json`, **without** a file extension:

```json
{
  "applinks": {
    "details": [
      {
        "appIDs": ["7P2ZTVF379.com.jupiterp.Jupiterpmobile"],
        "components": [
          { "/": "/", "?": { "s": "?*" }, "comment": "Shared schedule links" }
        ]
      }
    ]
  }
}
```

`7P2ZTVF379` is the Apple Developer Team ID already configured in the Xcode project. The
`components` rule scopes Universal Links to `/?s=...` so ordinary browsing of jupiterp.com is
left to the browser; broaden it if the app should capture more routes later. Serve the same
files on `www.jupiterp.com` (or redirect-free equivalents) since both hosts are registered.

## Testing without the association files

```bash
# Android emulator/device (custom scheme, works immediately):
adb shell am start -a android.intent.action.VIEW \
  -d "jupiterp://open?s=2~CMSC4Aq8z.MATH8nP2k"

# Android App Link, forcing verification off (or enable the domain in App info):
adb shell am start -a android.intent.action.VIEW \
  -d "https://jupiterp.com/?s=2~CMSC4Aq8z.MATH8nP2k" com.jupiterp.jupiterpmobile

# iOS simulator (custom scheme):
xcrun simctl openurl booted "jupiterp://open?s=2~CMSC4Aq8z.MATH8nP2k"
```
