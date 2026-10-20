<div align="center">
  <img src="https://raw.githubusercontent.com/shilapi/xcertplay/refs/heads/master/asset/xcertplay_small.png" width="180" height="180" alt="xcertplay icon" />
  <h1>xcertplay Personal</h1>
  <a href="README.md">English</a> | <a href="README.zh-CN.md">中文</a>
  <p>A CarPlay receiver for Android head units, supporting wired and wireless connections.</p>
</div>

This personal fork of [shilapi/xcertplay](https://github.com/shilapi/xcertplay) is maintained on [`personal-dev`](https://github.com/whoyang233/xcertplay/tree/personal-dev). It adds Chinese settings, app-switch resizing safeguards, Wi-Fi startup recovery, optional scan suppression, and configurable channel requests.

## Download and install

**[Download the verified 1.4.2B automotive APK ZIP (four DiPlay feature ports)](https://github.com/whoyang233/xcertplay/actions/runs/39352998865/artifacts/12234476798)** · [Verified build](https://github.com/whoyang233/xcertplay/actions/runs/39352998865) · [Personal branch builds](https://github.com/whoyang233/xcertplay/actions?query=branch%3Apersonal-dev)

Sign in to GitHub to download Actions artifacts. If the direct link fails, open the build page and select `personal-apks-<commit>` under **Artifacts**. Extract the ZIP and choose the APK for your device. Artifacts expire; use a later successful build or select `personal-dev` under **Android Build → Run workflow** to rebuild.

| APK | Target |
| --- | --- |
| `automotive-debug.apk` | Automotive variant, matching the upstream automotive release. |

Requires **Android 9 (API 28)** or newer and a configured MFi authentication source: CH341, native I2C, local certificate files, or a remote service.

| Identity | Personal build |
| --- | --- |
| Launcher name | xcertplay 个人版 |
| Application ID | `com.whoyang233.xcertplay` |
| Current upstream base version / versionCode | `1.4.2` / `1402` |
| Current debug version name | `1.4.2B` |

The personal app can be installed alongside upstream `com.shilapi.xcertplay`; **keep the original installed** if desired. Storage, permissions, and pairing records are separate, so configure and pair the personal app on first installation. Subsequent personal builds retain the application ID and signing key for updates that preserve data. This personal branch builds only the automotive APK, following the upstream automotive variant. Use one CarPlay app at a time because they cannot simultaneously own the same Wi-Fi, USB, or VPN resources.

## BYD vehicle data over ADB

The personal vehicle backend now uses read-only local ADB/autoservice reads adapted from DiPlay 0.2.21. The old JSON import, standard Android Car backend and permissions are removed. Speed, gear, battery percentage, range, remaining energy and charging state feed the existing/extended CarPlay reporting. Four-wheel pressure/temperature are diagnostic only. Reporting is off by default; old source settings cannot silently enable the new backend. See the [fields, authorization, protocols, freshness and compatibility notes (Chinese)](docs/VEHICLE_PROFILES.zh-CN.md).

## Startup sizing and display controls

New handshakes wait for stable window/system-bar dimensions. Physical references use the actual screen long/short edges instead of mixed historical maxima. Single-area compatibility is the default; optional fast multi-area switching and Restore display defaults are available under Display. Local rendering and touch share the negotiated aspect ratio. Existing upstream display improvements and personal audio/Wi-Fi/BYD ADB behavior remain available. See the [fix scope, controls and vehicle checks (Chinese)](docs/DISPLAY_STARTUP.zh-CN.md).

## Targeted DiPlay ports and upstream comparison

The upstream base remains 1.4.2. This update adds an opt-in BYD call-popup guard, software Opus frame recovery, focus recovery on fresh iPhone playback, and wired start timeouts with protection for recovered sessions. Temporary system bars are excluded. See the [personal/upstream comparison, provenance and limitations (Chinese)](docs/PERSONAL_DIFF_AND_DIPLAY.zh-CN.md).

## Core features

- Wired and wireless CarPlay receivers for Android and Android Automotive OS, with CH341, native I2C, local MFi files, and remote authentication.
- Separate voice, navigation, and music audio channels, media buffering, and microphone gain settings.
- Dynamic resolution, HEVC and safe areas; browser main/instrument output is an optional build feature omitted from normal car APKs.
- Optional head-unit location and vehicle data reporting, subject to available interfaces, permissions, and configuration.
- Upstream's experimental CarPlay Ultra trigger is retained; the protocol stack is incomplete and does not provide full Ultra support.

## Personal changes

| Area | Behavior |
| --- | --- |
| BYD call popup | Off by default; Android 10+ with the BYD call component and approved local ADB. Suppresses the overlay only during connected foreground CarPlay and attempts exact permission restoration on background/disconnect. |
| Audio recovery | Failed software Opus frames are dropped/reset; a fresh iPhone playback transition retries focus when the existing focus setting permits it. |
| Wired recovery | A 30-second session-start watchdog, a 10-second USB mode recheck, and delayed-retry protection for newly recovered sessions. |
| BYD vehicle data | Six read-only ADB measurements feed normalized CarPlay reporting; tyres are diagnostic only. Replaces the old JSON/Android Car backend. |
| Startup/display sizing | Stable-layout negotiation, real long/short-edge references, single-area compatibility by default, optional fast areas and display-default recovery. |
| Optional web display | Off at build time by default; excludes the server, certificate implementation, HTML and HTTP/WebSocket dependencies. Its entry is hidden and saved enabled states cannot redirect local playback. |
| Upstream 1.4.2 | Merged microphone uplink fixes, the audio quality menu, video-buffer reuse and display performance work while retaining personal settings, audio gain and Wi-Fi behavior. |
| App switching | Reserves configured system-bar space outside fullscreen and ignores transient background/unfocused size changes to reduce unnecessary resolution handshakes. Real split-screen and rotation changes still apply. |
| Settings | Simplified Chinese by default, grouped categories, and fixed top-right Restart CarPlay / Exit app buttons, with shorter labels in narrow windows. |
| Full-HD settings layout | Dedicated compact density and typography, adaptive navigation width, independently scrollable panes, and scrolling for long channel-choice dialogs. |
| Applying edits | Restart CarPlay validates, saves, and applies settings; closing settings discards unsaved edits. |
| Music and navigation | Separate Media/Navigation usage bindings, DiPlay-style music/navigation stream numbers 0–20, and 0%–200% software navigation gain, without changing system volume or music/call/Siri gain. |
| About | Shows the installed app version, Android version, and API level. |
| Wi-Fi startup recovery | Releases the first failed automatic hotspot, attempts a Wi-Fi off/on cycle, then retries once with a fresh manager. If switching is denied, only rebuilds the hotspot. Manual hotspots are excluded. |
| Channel strategy | Wi-Fi Direct supports automatic 5 GHz, channel 36 or 149 preferences, and specific non-DFS channel requests, with optional fallback on explicit rejection. |
| Scan suppression | Opt-in framework autojoin suppression adapted from DiPlay; requires approved local ADB. |
| Installation and updates | Independent package and fixed APK signing identity for coexistence and subsequent personal updates. |

Startup recovery is enabled by default. On cancellation or exit, it attempts to restore Wi-Fi that it disabled, and the next hotspot startup waits for restoration. Android and firmware permissions determine whether switching is allowed. Connection stability and RF behavior still require vehicle testing.

## Usage

1. For wireless CarPlay, pair the iPhone and head unit over Bluetooth.
2. Before video starts, tap the lower-right Settings button or swipe down with three fingers.
3. Under Connection, configure the MFi target and wired/wireless mode, then adjust audio, display, and window settings. An additional edge gesture can be enabled under Startup & window.
4. Tap **Restart CarPlay** at the top right to save and apply. There is no need to scroll to the bottom. Closing settings discards unsaved edits.
5. Provide the hardware, files, or service required by your selected authentication method and wait for CarPlay to connect.

The personal build prefers Simplified Chinese independently of the head unit's system language.

## Music, navigation, and in-app volume

DiPlay-style numbered stream bindings are available separately for music and navigation: 0 preserves usage routing; 1–20 request the corresponding Android/vendor stream (14 is navigation on some ROMs). Unsupported or uninitialized streams are released and fall back to usage routing. Save with Restart CarPlay; closing settings discards edits. Existing installations default to 0 to preserve routing.

Under Settings → Audio → Music and navigation channels, music defaults to Media and navigation to Navigation. If guidance is silent on a particular head unit, try binding navigation to Media. Quality-menu channel counts refer to mono/stereo, separately from output bindings. These settings request Android audio usages; firmware decides the physical buses, speakers, and system volume groups.

Navigation volume ranges from 0% to 200% in 5% steps: 0% mutes and 100% preserves the original signal; boosting can clip. Gain is applied to decoded navigation/alert PCM before either local playback or web forwarding, without calling system volume APIs. Rebinding navigation to Media still changes only guidance, preserving music, calls, Siri, and the separate microphone gain. Tap Restart CarPlay to save and apply; closing settings discards edits.

Generic high-quality music (102) and alternate guidance (101) streams are classified as music and navigation respectively; explicit roles, calls, and Siri take precedence. The existing AAOS Advanced channel mapping option still determines how ordinary default/compatibility streams are classified. Guidance already mixed into music by the phone cannot be separated. Vehicle checks should play music alongside guidance at 0%, 50%, and 100%, also after rebinding guidance to Media, while checking calls and Siri remain unaffected.

## Wi-Fi scan suppression and channels

These options are under Settings → Connection and apply after saving and restarting CarPlay. **Channel requests and scan suppression are limited to Wi-Fi Direct on Android 10+.** Android 9 uses LocalOnlyHotspot instead.

### Channel strategy

| Strategy | First request |
| --- | --- |
| System automatic (default) | System-selected 5 GHz channel. |
| Prefer low channel | 36. |
| Prefer high channel | 149. |
| Specific channel | 36, 40, 44, 48, 149, 153, 157, 161, or 165. |

Requests use the public Android API and **do not require ADB**. Firmware, regional rules, or Wi-Fi concurrency can reject or override a request. Trust the last observed hotspot channel shown in settings and the connection log. These options do not change channel width or intentionally retune an existing connection.

Fallback to automatic is enabled by default. An explicit rejection of a requested-channel group allows one retry with system-selected 5 GHz. Busy errors, timeouts, permission failures, and cancellation do not trigger channel fallback; an explicit channel rejection also does not trigger a Wi-Fi power cycle. Automatic hotspots remain system-controlled. For manual hotspots, change channels in the head unit's system settings; entering a channel in this app does not retune them.

### Scan suppression

Pause automatic network search is **off by default**. Adapted from [DiPlay](https://github.com/shihabal3amri/DiPlay/blob/1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70/shared/src/main/java/com/shilapi/xcertplay/network/WifiScanPause.kt), it switches framework Wi-Fi autojoin through approved local ADB to reduce off-channel searches on some single-radio head units.

Park first, enable legacy network ADB at `127.0.0.1:5555`, tap Authorize / check local ADB, approve the system debugging prompt, then enable the option. TLS pairing-code-only wireless debugging is unsupported. Background connections and restoration never offer a new key. ADB approval does not establish firmware support for this switch.

A durable marker precedes changes. The last controller closing, or a reported connection failure, requests restoration; failures retry every 30 seconds and on the next app launch. A force-stopped app cannot restore immediately. If ADB is unavailable, restore ADB and reopen the app, or restart the head unit. Android 13+ can preserve autojoin already disabled by another tool when its state is queryable; older firmware cannot report the original value, so use this option only when autojoin was originally enabled.

This can affect automatic joining of a phone hotspot. It cannot block all app-initiated scans or eliminate external RF interference. Compare automatic, channel 36, and channel 149 first, then test scan suppression separately while recording observed channels and disconnect times.

## Web displays (optional, off by default)

Normal personal APKs omit browser main/instrument output. The default `xcertplay.webDisplay=false` excludes the server, NanoHTTPD/WebSocket dependencies, TLS certificate implementation and HTML, and hides the settings entry. Saved enabled states are ignored in this build; saving other settings preserves the optional preferences for a later explicitly enabled build. The main screen, local touch and audio, and navigation gain remain available on the head unit.

To restore the optional feature with the existing fixed signing environment:

```bash
./gradlew :automotive:assembleDebug -Pxcertplay.webDisplay=true
```

With that build feature enabled, the in-app main/instrument switches still default to off. Then:

1. Under Web displays, enable the browser display for Main and/or Instrument, set the resolution, then tap Restart CarPlay at the top right.
2. Connect the browser device to the same LAN and open the address shown in settings (`https://<IP>:8080/main` or `/cluster`). Pages remain connected through CarPlay reconnects and resume automatically.
3. The certificate is self-signed; accept the browser's first-visit prompt.
4. Main display supports up to two touch points and plays the phone's audio after an initial tap to enable sound. The microphone remains on Android. The instrument page is view-only and requires the iPhone to provide its second display stream.

Use a recent Chrome, Edge, or Safari. Disable `HEVC (H.265)` in settings if the browser cannot decode it.

## Fixed APK signing

Personal debug and release APKs share one dedicated RSA key. Its public certificate SHA-256 is pinned in [`signing/personal-certificate.sha256`](signing/personal-certificate.sha256). Gradle checks before packaging, and CI verifies the resulting APK before upload. **Normal builds fail if the key is missing or mismatched; they never generate a temporary key or fall back to a default debug signer.**

The repository Actions secret **`PERSONAL_ANDROID_SIGNING`** holds the complete JSON from the offline backup's `github-secret.txt`: PKCS#12 Base64, passwords, and alias. Save it once under Settings → Secrets and variables → Actions and reuse it. The private key is excluded from source and public artifacts; the maintainer keeps the offline backup.

APK signing keys and MFi authentication keys serve different purposes and are not interchangeable.

## Media buffer and low-latency presets

Settings → Audio → Main media buffer (latency) offers **20, 50, 100, 150, 200, 250, 300…1000 ms**, in 50 ms steps after 50. Existing saved values remain unchanged; a fresh installation still defaults to **100 ms**. Restart CarPlay saves and applies; closing settings discards the draft.

This controls main-media AudioTrack PCM capacity, not an audio/video synchronization offset or an end-to-end latency guarantee. Smaller buffers can reduce queued audio but tolerate less network jitter. Try 50 ms before 20 ms, and return to a larger buffer if playback becomes intermittent. Android may enforce a larger minimum; `actualBufferFrames` in the audio log reports the allocated capacity. Video keeps its existing immediate decode/render path.

The startup threshold and blocking pre-play writes stay within the track capacity, avoiding a full unstarted 20 ms track. The setting also covers identified 102/default music streams; calls, Siri and navigation retain their own paths.

## Build

### Requirements

- JDK 25 is recommended to match CI; launching Gradle requires at least JDK 17, and the daemon toolchain uses Java 25.
- Gradle Wrapper 9.5.0, Android SDK Platform 37, Build Tools `36.0.0`, and NDK `28.2.13676358`.
- Python 3 for CI signing and APK verification helpers.
- An iPhone and compatible head unit for device testing; USB/CH341 or native I2C authentication also requires the appropriate MFi hardware.

### GitHub Actions

Select `personal-dev` under **Android Build → Run workflow**, leaving `bootstrap_unsigned` off. Normal builds use the fixed key, run unit tests and lint, build both APKs, and verify package and signing identity before uploading:

| Artifact | Contents |
| --- | --- |
| `personal-apks-<commit>` | Signed automotive APK and package/signature verification metadata. |
| `validation-reports-<commit>` | Unit test, lint, and settings-layout screenshot reports. |
| `unsigned-apks-<commit>` | Unsigned inputs for explicit bootstrap or PR validation without a key; not installable. |

### Local builds

Set these environment variables to the original PKCS#12 key from the offline backup, using an absolute keystore path:

| Variable | Value |
| --- | --- |
| `ANDROID_KEYSTORE_PATH` | Absolute path to `xcertplay-personal.p12`. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | Backup alias `xcertplay-personal`. |
| `ANDROID_KEY_PASSWORD` | Private key password. |

macOS / Linux:

```bash
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :common:lintDebug :automotive:lintDebug :automotive:assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :common:lintDebug :automotive:lintDebug :automotive:assembleDebug
```

Signed release builds require the same fixed key:

```bash
./gradlew :automotive:assembleRelease
```

Without the key, only an explicit `-Pxcertplay.allowUnsigned=true` can produce unsigned debug inputs. CI exposes this as manual `bootstrap_unsigned` or PR validation without a key. This never generates a new signing key or makes an unsigned APK installable.

## Verification and feedback

The [verified build from 2026-10-20](https://github.com/whoyang233/xcertplay/actions/runs/39352998865), commit `09ebf54bd09f8cdd5ec48e201eefe0dfe01138be`, passed **388 unit tests with no failures, errors or skips; lint had no errors and retained 83 common / 3 automotive warnings**. The 25 BYD source/migration tests cover CAN/CANFD, conversions, partial/invalid readings, firmware catalog caching, failure recovery, freshness, subscriptions, diagnostic isolation, cancellation and migration. iAP2 tests verify remaining-energy and charging parameter encoding. The 38 additional executions cover popup leases/cancellation/restoration, eligibility and UI, Opus failures, focus and playback transitions, wired timeouts and recovery protection. Navigation gain, audio bindings, microphone, Wi-Fi recovery and scan-suppression regressions passed. Settings checks cover Full-HD at multiple densities, narrow windows with 150% fonts and short channel dialogs. The [validation artifact](https://github.com/whoyang233/xcertplay/actions/runs/39352998865/artifacts/12234681416) includes fixture screenshots. Actual head-unit ADB, available fields, navigation use, installation and connection behavior still require vehicle testing.

This run produces only the automotive APK. Package/version (1.4.2B / 1402), the pinned signer, and provider/custom-permission identities are verified. Startup sizing, touch adaptation, numbered audio binding and BYD ADB code are included. Old vehicle JSON assets/parser and Android Car property backend, browser assets/server/certificate and NanoHTTPD/NanoWSD remain excluded. The separate optional web job passes **83 common tests, including 15 web tests**, without packaging another APK.

Vehicle checks should cover rapid app switching outside fullscreen, split-screen and rotation, settings save/cancel, first-hotspot-failure recovery, channel strategies, and scan suppression. Include the head unit model, Android/API, app and iOS versions, hotspot mode, observed channel, reproduction steps, disconnect time, and relevant logs when reporting a problem.

The usual personal-app log path is:

```text
/sdcard/Android/data/com.whoyang233.xcertplay/files/logs/xcertplay.log
```

When external app storage is unavailable, logs use internal app storage; the startup log reports the actual path. Android may restrict file-manager access to this directory. Review logs before attaching them to a [repository issue](https://github.com/whoyang233/xcertplay/issues).

Adapter board: [CH341-to-MFI](https://github.com/shilapi/ch341-to-mfi-chip)

## Local MFI files

Choose `Local files` under `MFI certificate & signing target`, then use the two
`Choose` buttons to select the certificate and private key with Android's system
document picker. The supported formats are a DER PKCS#7 certificate (`.p7b`)
and its matching, unencrypted DER PKCS#8 private key (`.pk8`). The app validates
that the files match before starting the phone connection and reloads them on
MFI reconnect.

Store the private key in a protected location. Neither file is copied into app
preferences; only Android's persistent read permission and document URI are
saved.

## Remote MFI

The Remote MFi client treats a remote service as an MFi chip for remote calls,
or uses BAA authentication. Remote authentication avoids the process of
connecting to a local MFi chip for authentication.

### Endpoints

| Method | Path | Purpose | Request body | Success response | Failure response |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/mfi/certificate` | Get the MFi chip version, certificate type, and certificate contents; cached by the client after the first call | None | Certificate JSON | `{"detail":"..."}` |
| `POST` | `/mfi/sign` | Sign the challenge | `{"challenge":"...","requestId":"..."}` | `{"signature":"..."}` | `{"detail":"..."}` |
| `POST` | `/mfi/reset` | Request a reset of the remote MFi chip | `{}` | `{"detail":""}` | `{"detail":"..."}` |

(Optional) Standard Bearer Authentication can be used for verification.

**Upstream reports testing BAA authentication; validate your authentication setup on the target hardware.**

## Project structure

| Path | Purpose |
| --- | --- |
| `common/` | Shared host UI, Chinese settings, persistence, and resources. |
| `mobile/` | Standard Android head-unit target. |
| `automotive/` | Native Android Automotive OS target. |
| `shared/` | CH341, I2C, MFi, iAP2, VPN, AirPlay, media, and Wi-Fi implementations. |
| `ci/` | Fixed signing setup and APK identity verification scripts. |
| `signing/` | Public certificate fingerprint only. |

## Acknowledgements and license

Based on [shilapi/xcertplay](https://github.com/shilapi/xcertplay). Thanks to [LIVI](https://github.com/f-io/LIVI) for implementation references and [showcase](https://github.com/amineross/showcase) for BAA authentication references.

Vehicle reads additionally adapt DiPlay `6d3b8a8ce927cb4b115b8c3881f17619433e93b1` (0.2.21); see the [vehicle-data provenance](docs/VEHICLE_PROFILES.zh-CN.md). Scan suppression, the ADB protocol implementation, and associated tests adapt [DiPlay](https://github.com/shihabal3amri/DiPlay) at `1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70`, preserving GPL-3.0 attribution. This port does not include its cluster-control features or AGPL UI code.

Licensed under the [GNU General Public License v3.0](LICENSE).

The call-popup lease/helper and wired watchdog port DiPlay `6d3b8a8ce927cb4b115b8c3881f17619433e93b1` (GPL-3.0); Opus and playback-resume recovery are adapted to this project. See the [port notes](docs/PERSONAL_DIFF_AND_DIPLAY.zh-CN.md).
