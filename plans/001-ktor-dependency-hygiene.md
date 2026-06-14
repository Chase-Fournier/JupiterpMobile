# Plan 001: Remove the unused server-side Ktor dependency and align Ktor client versions

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 2bc3c67..HEAD -- composeApp/build.gradle.kts gradle/libs.versions.toml`
> If either file changed since this plan was written, compare the "Current
> state" excerpts below against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: migration
- **Planned at**: commit `2bc3c67`, 2026-06-14

## Why this matters

This is a **client-only** Kotlin Multiplatform app, but its Gradle build pulls in
`ktor-server-content-negotiation` — a server-side library — into `commonMain`. The
networking code uses the **client** `ContentNegotiation` plugin, so the server
dependency is dead weight: it enlarges the dependency graph (and transitively the
app binary) and adds unused code to the attack surface for no benefit.

Separately, the Ktor artifacts are version-skewed: the Android and iOS HTTP clients
are pinned to `3.4.0` while the shared `ktor-client-core`, content-negotiation,
logging, and serialization artifacts are `3.3.3`. Ktor expects all of its artifacts
to share one version; a minor-version split between core and the platform engines is
a latent source of obscure runtime breakage. Aligning them removes that risk.

## Current state

- `composeApp/build.gradle.kts` — the app module build. Its `commonMain.dependencies`
  block (lines 38–58) lists the Ktor dependencies. Line 40 is the unused server one:

  ```kotlin
  // composeApp/build.gradle.kts:38-44
  commonMain.dependencies {
      implementation(libs.coil.svg)
      implementation(libs.ktor.server.content.negotiation)   // <-- line 40, REMOVE
      implementation(libs.ktor.serialization.kotlinx.json)
      implementation(libs.ktor.client.logging)
      implementation(libs.ktor.ktor.client.content.negotiation)
      implementation(libs.ktor.client.core)
  ```

  The Android/iOS engines are added in their own source sets:
  `androidMain` → `implementation(libs.ktor.client.android)` (line 33),
  `iosMain` → `implementation(libs.ktor.client.darwin)` (line 36).

- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/api/JupiterpApiClient.kt:13`
  imports **the client plugin**, confirming the server dep is unused:

  ```kotlin
  import io.ktor.client.plugins.contentnegotiation.*   // client, not server
  ```
  and at line 45 installs it: `install(ContentNegotiation) { json(json) }`.

- `gradle/libs.versions.toml` — version catalog. Relevant version refs (lines 19–27):

  ```toml
  ktorClientAndroid = "3.4.0"
  ktorClientCio = "3.3.3"
  ktorClientContentNegotiation = "3.3.3"
  ktorClientCore = "3.3.3"
  ktorClientDarwin = "3.4.0"
  ktorClientLogging = "3.3.3"
  ktorSerializationKotlinxJson = "3.3.3"
  ktorServerContentNegotiation = "3.3.3"
  ktorServerNetty = "3.3.3"
  ```

  Relevant library aliases (lines 60–68):

  ```toml
  ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktorClientAndroid" }
  ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktorClientCio" }
  ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktorClientCore" }
  ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktorClientDarwin" }
  ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktorClientLogging" }
  ktor-ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktorClientContentNegotiation" }
  ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktorSerializationKotlinxJson" }
  ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktorServerContentNegotiation" }
  ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktorServerNetty" }
  ```

  **Note**: `ktor-client-cio`, `ktor-server-content-negotiation`, and `ktor-server-netty`
  are catalog entries that are NOT referenced by `composeApp/build.gradle.kts` after this
  plan's first step. They are dead catalog entries.

## Commands you will need

| Purpose            | Command                                                   | Expected on success      |
|--------------------|-----------------------------------------------------------|--------------------------|
| Unit tests         | `./gradlew :composeApp:testDebugUnitTest --console=plain` | `BUILD SUCCESSFUL`       |
| Android compile    | `./gradlew :composeApp:assembleDebug --console=plain`     | `BUILD SUCCESSFUL`       |
| iOS compile (opt.) | `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --console=plain` | `BUILD SUCCESSFUL` |

(The first command was verified working at commit `2bc3c67` — existing tests pass.)

## Scope

**In scope** (the only files you should modify):
- `composeApp/build.gradle.kts`
- `gradle/libs.versions.toml`

**Out of scope** (do NOT touch):
- `JupiterpApiClient.kt` or any other Kotlin source — no code change is needed.
- The `ktor-client-cio` catalog entry — leave it as-is; it is unrelated to this finding
  and removing it is not required.
- Any non-Ktor dependency or version.

## Git workflow

- Branch: `advisor/001-ktor-dependency-hygiene`
- One commit is fine; message style matches the repo's short imperative log
  (e.g. `Remove unused ktor-server dep; align ktor client versions to 3.4.0`).
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Remove the unused server dependency from the build

In `composeApp/build.gradle.kts`, delete the single line in `commonMain.dependencies`:

```kotlin
        implementation(libs.ktor.server.content.negotiation)
```

**Verify**: `grep -n "ktor.server" composeApp/build.gradle.kts` → no matches.

### Step 2: Confirm the app still builds and tests pass without it

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.
Then `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

If either fails to compile because a symbol from `io.ktor.server.*` is now missing,
that means the server plugin was actually in use — STOP and report (see STOP conditions).

### Step 3: Align the skewed Ktor versions to 3.4.0

In `gradle/libs.versions.toml`, change these four version refs from `3.3.3` to `3.4.0`
so every Ktor **client** artifact matches the already-3.4.0 Android/Darwin engines:

```toml
ktorClientContentNegotiation = "3.4.0"
ktorClientCore = "3.4.0"
ktorClientLogging = "3.4.0"
ktorSerializationKotlinxJson = "3.4.0"
```

Leave `ktorClientAndroid` and `ktorClientDarwin` (already `3.4.0`) unchanged. Leave
`ktorClientCio` unchanged (out of scope). You may leave `ktorServerContentNegotiation`
and `ktorServerNetty` as-is, or do the optional cleanup in Step 4.

**Verify**: `grep -nE 'ktorClient(Core|ContentNegotiation|Logging)|ktorSerializationKotlinxJson' gradle/libs.versions.toml`
→ all show `= "3.4.0"`.

### Step 4 (optional cleanup): Remove dead server catalog entries

This step is optional and purely cosmetic. Only do it if Step 1–3 verification passed.
Remove the now-unreferenced catalog entries so the catalog reflects reality:

- In `[versions]`: delete the `ktorServerContentNegotiation` and `ktorServerNetty` lines.
- In `[libraries]`: delete the `ktor-server-content-negotiation` and `ktor-server-netty` lines.

**Verify**: `grep -n "ktorServer\|ktor-server" gradle/libs.versions.toml` → no matches,
**and** `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

If Gradle reports an "unresolved reference" for a removed catalog alias, you removed an
entry that is still referenced somewhere — restore it and STOP.

## Test plan

No new tests. This is a build-configuration change; the existing suite is the
regression guard. After all steps:

- `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`, same
  test count as before (the run at `2bc3c67` passed).

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `grep -n "ktor.server" composeApp/build.gradle.kts` → no matches
- [ ] `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`
- [ ] `grep -nE 'ktorClient(Core|ContentNegotiation|Logging)|ktorSerializationKotlinxJson' gradle/libs.versions.toml` → all `3.4.0`
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- Removing `libs.ktor.server.content.negotiation` causes a compile error referencing
  `io.ktor.server.*` — the server plugin was genuinely in use; do not "fix" it by
  re-adding the dependency without investigation.
- After bumping to `3.4.0`, the build fails with a Ktor API incompatibility — report the
  exact error; do not downgrade individual artifacts to patch it.
- The drift check shows `build.gradle.kts` or `libs.versions.toml` changed since `2bc3c67`
  and the excerpts above no longer match.

## Maintenance notes

- Future Ktor upgrades should bump **all** client artifacts together. Consider folding
  the per-artifact version refs into a single `ktor = "..."` version in a later cleanup.
- A reviewer should confirm the app still makes successful API calls on a real
  device/simulator — a clean compile does not exercise the network engine at runtime.
- The `ktor-client-cio` catalog entry remains unused; it was intentionally left out of
  scope and can be removed in a separate housekeeping change.
