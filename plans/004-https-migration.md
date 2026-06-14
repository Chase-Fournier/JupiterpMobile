# Plan 004: Move the API to HTTPS and remove the cleartext-traffic allowances

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. **Step 1 is a hard gate** — if the API does not serve HTTPS, STOP
> and report; do not make any code changes. If anything in the "STOP
> conditions" section occurs, stop and report — do not improvise. When done,
> update the status row for this plan in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 2bc3c67..HEAD -- composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/api/JupiterpApiClient.kt composeApp/src/androidMain/AndroidManifest.xml composeApp/src/androidMain/res/xml/network_security_config.xml iosApp/iosApp/Info.plist`
> If any of these changed since this plan was written, compare against the
> "Current state" excerpts before proceeding; on a mismatch, STOP.

## Status

- **Priority**: P1
- **Effort**: S–M
- **Risk**: MED
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `2bc3c67`, 2026-06-14

## Why this matters

The app talks to `api.jupiterp.com` over plain **HTTP**, and both platforms are configured
to permit cleartext for it: Android via `usesCleartextTraffic="true"` plus a
network-security-config exception, and iOS via a blanket `NSAllowsArbitraryLoads` App
Transport Security bypass. Every course, section, seat-count, and professor-rating response
travels unencrypted, so anyone on the network path (public Wi-Fi, ISP, a hostile hotspot)
can read or **modify** it. Tampered responses flow straight into the user's schedule and
into the calendar events the app exports — silently wrong data the user trusts.

If the API serves the same content over HTTPS, switching the base URL and removing the two
cleartext escape hatches closes this with minimal code change. **The migration is only
valid if HTTPS works** — hence the hard gate in Step 1.

## Current state

- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/api/JupiterpApiClient.kt:34`:
  ```kotlin
  private const val BASE_URL = "http://api.jupiterp.com"
  ```
  (Doc comments referencing the HTTP URL: same file line 26, and
  `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/ApiModels.kt:10`.)

- `composeApp/src/androidMain/AndroidManifest.xml:11` (inside `<application>`):
  ```xml
  android:usesCleartextTraffic="true"
  android:networkSecurityConfig="@xml/network_security_config"
  ```

- `composeApp/src/androidMain/res/xml/network_security_config.xml`:
  ```xml
  <network-security-config>
      <domain-config cleartextTrafficPermitted="true">
          <domain includeSubdomains="true">api.jupiterp.com</domain>
      </domain-config>
  </network-security-config>
  ```

- `iosApp/iosApp/Info.plist` (the ATS bypass):
  ```xml
  <key>NSAppTransportSecurity</key>
  <dict>
      <key>NSAllowsArbitraryLoads</key>
      <true/>
  </dict>
  ```

## Commands you will need

| Purpose                 | Command                                                                                          | Expected on success            |
|-------------------------|--------------------------------------------------------------------------------------------------|--------------------------------|
| HTTPS gate              | `curl -sS -o /dev/null -w "%{http_code}\n" https://api.jupiterp.com/v0/deptList`                  | a `2xx`/`3xx` code (e.g. `200`) |
| HTTPS body sanity       | `curl -sS https://api.jupiterp.com/v0/deptList \| head -c 200`                                    | JSON, not an error/cert page    |
| Android compile         | `./gradlew :composeApp:assembleDebug --console=plain`                                             | `BUILD SUCCESSFUL`             |
| Unit tests              | `./gradlew :composeApp:testDebugUnitTest --console=plain`                                         | `BUILD SUCCESSFUL`             |

## Scope

**In scope**:
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/api/JupiterpApiClient.kt`
- `composeApp/src/androidMain/AndroidManifest.xml`
- `composeApp/src/androidMain/res/xml/network_security_config.xml`
- `iosApp/iosApp/Info.plist`
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/ApiModels.kt` (doc comment only)

**Out of scope** (do NOT touch):
- Ktor client configuration beyond the base URL (timeouts, logging, content negotiation).
- Any request path or query-parameter code.
- Certificate pinning — do not add it in this plan; it is a separate hardening decision.

## Git workflow

- Branch: `advisor/004-https-migration`
- One commit is fine (e.g. `Use HTTPS for the API; drop cleartext-traffic allowances`).
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1 (HARD GATE): Confirm the API serves HTTPS

Run both HTTPS commands:

```
curl -sS -o /dev/null -w "%{http_code}\n" https://api.jupiterp.com/v0/deptList
curl -sS https://api.jupiterp.com/v0/deptList | head -c 200
```

**Proceed only if** the first returns a 2xx/3xx status **and** the second returns JSON
(the same shape the app expects, e.g. a list of department objects). If you see a
connection failure, TLS/certificate error, or an HTML error page, **STOP and report** —
the rest of this plan must not be applied, because it would break the app.

### Step 2: Point the client at HTTPS

In `JupiterpApiClient.kt`, change line 34:

```kotlin
private const val BASE_URL = "https://api.jupiterp.com"
```

Update the doc comment on line 26 of the same file and line 10 of `ApiModels.kt` to use
`https://api.jupiterp.com/v0/` as well (comment-only edits).

**Verify**: `grep -rn "http://api.jupiterp.com" composeApp/src` → no matches.

### Step 3: Remove the Android cleartext allowances

In `AndroidManifest.xml`, delete the attribute line:

```xml
android:usesCleartextTraffic="true"
```

Leave the `android:networkSecurityConfig="@xml/network_security_config"` attribute in
place, and change `network_security_config.xml` to explicitly forbid cleartext:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.jupiterp.com</domain>
    </domain-config>
</network-security-config>
```

**Verify**: `grep -n "usesCleartextTraffic" composeApp/src/androidMain/AndroidManifest.xml`
→ no matches; `grep -n 'cleartextTrafficPermitted="false"' composeApp/src/androidMain/res/xml/network_security_config.xml`
→ matches.

### Step 4: Remove the iOS ATS bypass

In `iosApp/iosApp/Info.plist`, delete the `NSAppTransportSecurity` key and its `<dict>`
value (the two-line `NSAllowsArbitraryLoads`/`<true/>` block inside it). Leave every other
key (`CADisableMinimumFrameDurationOnPhone`, the two calendar usage descriptions) intact.

**Verify**: `grep -n "NSAllowsArbitraryLoads\|NSAppTransportSecurity" iosApp/iosApp/Info.plist`
→ no matches. Confirm the plist is still well-formed:
`plutil -lint iosApp/iosApp/Info.plist` → `OK` (if `plutil` is unavailable, visually confirm
the `<dict>`/`</dict>` and `<plist>` tags still balance).

### Step 5: Confirm the app still builds

**Verify**: `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`,
then `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.

## Test plan

This change is configuration, not logic, so there are no new unit tests (the network layer
is not unit-tested in this repo, and adding HTTP mocking is out of scope). Verification is:

- Step 1 gate proves the endpoint is reachable over HTTPS and returns expected JSON.
- The build and existing suite stay green.
- **Manual runtime check (note for the reviewer/operator, not the executor):** launch the
  app on a device/simulator and perform a course search to confirm live API calls succeed
  over HTTPS. A clean compile does not exercise the network at runtime.

## Done criteria

Machine-checkable. ALL must hold:

- [ ] Step 1 HTTPS gate returned a 2xx/3xx status and JSON body
- [ ] `grep -rn "http://api.jupiterp.com" composeApp/src` → no matches
- [ ] `grep -n "usesCleartextTraffic" composeApp/src/androidMain/AndroidManifest.xml` → no matches
- [ ] `grep -n 'cleartextTrafficPermitted="false"' composeApp/src/androidMain/res/xml/network_security_config.xml` → matches
- [ ] `grep -n "NSAllowsArbitraryLoads" iosApp/iosApp/Info.plist` → no matches
- [ ] `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- The Step 1 HTTPS gate fails for any reason (refused, TLS/cert error, non-JSON, wrong
  shape). Do not migrate; report what the endpoint returned.
- HTTPS works for `deptList` but you have reason to believe other endpoints differ — report
  before proceeding.
- The build fails after the changes in a way that points at the network/manifest config.

## Maintenance notes

- Once on HTTPS, consider (separately) certificate pinning if the threat model warrants it,
  and verify the iOS app still passes App Store review without the ATS exception (a blanket
  `NSAllowsArbitraryLoads` is itself an App Store review flag, so removing it is a net win).
- A reviewer should confirm there is no remaining cleartext path: the manifest default for
  `targetSdk` 28+ already blocks cleartext, and the network-security-config now states
  `false` explicitly, so any future `http://` call will fail fast rather than silently
  downgrade.
- If the API is ever moved behind a different host, both the `BASE_URL` and the
  network-security-config `<domain>` must be updated together.
