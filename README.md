<div align="center">
  <img src="https://raw.githubusercontent.com/shilapi/xcertplay/refs/heads/master/asset/xcertplay_small.png" width="180" height="180" alt="xcertplay icon" />
  <h1>xcertplay Personal</h1>
  <a href="README.md">English</a> | <a href="README.zh-CN.md">中文</a>
  <p>A CarPlay receiver for Android head units, supporting wired and wireless connections.</p>
</div>

This personal fork of [shilapi/xcertplay](https://github.com/shilapi/xcertplay) is maintained on [`personal-dev`](https://github.com/whoyang233/xcertplay/tree/personal-dev). It adds Chinese settings, app-switch resizing safeguards, Wi-Fi startup recovery, optional scan suppression, and configurable channel requests.

## Download and install

**[Download the 1.4.2B automotive APK directly (BYD car icon)](https://github.com/whoyang233/xcertplay/releases/download/personal-1.4.2B-20261021-byd-icon/xcertplay-personal-automotive-1.4.2B.apk)** · [SHA-256](https://github.com/whoyang233/xcertplay/releases/download/personal-1.4.2B-20261021-byd-icon/SHA256SUMS.txt) · [Verified build](https://github.com/whoyang233/xcertplay/actions/runs/39572068305) · [Personal branch builds](https://github.com/whoyang233/xcertplay/actions?query=branch%3Apersonal-dev)

The direct download is an installable APK. A [ZIP backup](https://github.com/whoyang233/xcertplay/actions/runs/39572068305/artifacts/12331569969) is available through Actions (GitHub sign-in required). This branch continues to build only the automotive variant.

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

## Connection overview and settings

[Complete settings screenshots, including all eight categories and the audio quality subpage](docs/SETTINGS_SCREENSHOTS.zh-CN.md).

[Configuration audit, 0 ms implementation, Web removal and screenshot notes (Chinese)](docs/UI_BUFFER_SETTINGS.zh-CN.md).

The UI fixes, audio/video 0 ms modes and complete Web-rendering removal are now included in the BYD car-icon APK. Manual Actions runs support `validation_only`, which runs tests/lint and skips signing, APK packaging and APK uploads.

The waiting page now shows a Chinese-friendly connection overview with four milestones and separate Wi-Fi/Bluetooth/CarPlay checks. Protocol exchanges and debug logs open through **Connection details**; **Connection settings** stays in the top toolbar. Wide windows use two columns, narrower windows scroll, and waiting-page typography avoids the old excessive enlargement. Failure guidance is readable; retry remains governed by the existing controller.

Connection settings prioritize transport, MFi authentication and channel policy. Performance/recovery/ADB scan suppression are under **Advanced wireless options**, hotspot information under **Connection diagnostics**, and long explanations expand on demand. Wide selectors put values on the right; narrow windows and large fonts stack them. The audio focus description explicitly states its immediate-save behavior. Restart/Exit remain fixed at the top right; validation, save/discard rules, defaults and stored connection/audio/ADB options are preserved. This UI is implemented in the personal fork.

## CarPlay identity and appearance

This page controls receiver information announced to iPhone: manufacturer/model (iAP2 identification and AirPlay `/info`), the return-to-car icon label (`oemIconLabel`), a custom square icon (`oemIcons`, with crop/preview/reset), and driving side (`rightHandDrive`, potentially affecting CarPlay sidebar placement). iPhone determines the display. It does not change the Android launcher icon, app theme or vehicle-data source. Save and reconnect with Restart CarPlay; closing settings restores unsaved edits, including icon changes. The DiPlay BYD car-button capability is now ported: a built-in BYD icon and default label, preserved custom icon/name, and Home intent handling when the active iPhone session requests the car UI. It opens the configured Android launcher while keeping CarPlay running; launcher failures do not tear down the session and stale/closed requests are ignored. Large custom icons are downscaled to at most 192×192 for both preview and handshake to avoid oversized `/info` replies. No ADB is needed. See [port notes](docs/BYD_CAR_ICON.zh-CN.md).

## BYD vehicle data over ADB

The personal vehicle backend now uses read-only local ADB/autoservice reads adapted from DiPlay 0.2.21. The old JSON import, standard Android Car backend and permissions are removed. Speed, gear, battery percentage, range, remaining energy and charging state feed the existing/extended CarPlay reporting. Four-wheel pressure/temperature are diagnostic only. Reporting is off by default; old source settings cannot silently enable the new backend. See the [fields, authorization, protocols, freshness and compatibility notes (Chinese)](docs/VEHICLE_PROFILES.zh-CN.md).

## Startup sizing and display controls

New handshakes wait for stable window/system-bar dimensions. Physical references use the actual screen long/short edges instead of mixed historical maxima. Single-area compatibility is the default; optional fast multi-area switching and Restore display defaults are available under Display. Local rendering and touch share the negotiated aspect ratio. Existing upstream display improvements and personal audio/Wi-Fi/BYD ADB behavior remain available. See the [fix scope, controls and vehicle checks (Chinese)](docs/DISPLAY_STARTUP.zh-CN.md).

## Targeted DiPlay ports and upstream comparison

The upstream base remains 1.4.2. This update adds an opt-in BYD call-popup guard, software Opus frame recovery, focus recovery on fresh iPhone playback, and wired start timeouts with protection for recovered sessions. Temporary system bars are excluded. See the [personal/upstream comparison, provenance and limitations (Chinese)](docs/PERSONAL_DIFF_AND_DIPLAY.zh-CN.md).

## Core features

- Wired and wireless CarPlay receivers for Android and Android Automotive OS, with CH341, native I2C, local MFi files, and remote authentication.
- Separate voice, navigation, and music audio channels, media buffering, and microphone gain settings.
- Dynamic resolution, HEVC and safe areas; browser main/instrument output has been removed from this personal branch.
- Optional head-unit location and vehicle data reporting, subject to available interfaces, permissions, and configuration.
- Upstream's experimental CarPlay Ultra trigger is retained; the protocol stack is incomplete and does not provide full Ultra support.

## Personal changes

| Area | Behavior |
| --- | --- |
| Low-latency buffer | Main media supports 0, 20 and 50–1000 ms while preserving saved values and the 100 ms default. Startup/blocking writes fit small tracks, including default high-quality music. |
| BYD call popup | Off by default; Android 10+ with the BYD call component and approved local ADB. Suppresses the overlay only during connected foreground CarPlay and attempts exact permission restoration on background/disconnect. |
| Audio recovery | Failed software Opus frames are dropped/reset; a fresh iPhone playback transition retries focus when the existing focus setting permits it. |
| Wired recovery | A 30-second session-start watchdog, a 10-second USB mode recheck, and delayed-retry protection for newly recovered sessions. |
| BYD vehicle data | Six read-only ADB measurements feed normalized CarPlay reporting; tyres are diagnostic only. Replaces the old JSON/Android Car backend. |
| Startup/display sizing | Stable-layout negotiation, real long/short-edge references, single-area compatibility by default, optional fast areas and display-default recovery. |
| Web rendering | Removed from source, settings and dependencies; old enabled preferences cannot redirect local playback. |
| Upstream 1.4.2 | Merged microphone uplink fixes, the audio quality menu, video-buffer reuse and display performance work while retaining personal settings, audio gain and Wi-Fi behavior. |
| App switching | Reserves configured system-bar space outside fullscreen and ignores transient background/unfocused size changes to reduce unnecessary resolution handshakes. Real split-screen and rotation changes still apply. |
| Settings | Simplified Chinese by default, grouped categories, and fixed top-right Restart CarPlay / Exit app buttons, with shorter labels in narrow windows. |
| Full-HD settings layout | Dedicated compact density and typography, adaptive navigation width, independently scrollable panes, and scrolling for long channel-choice dialogs. |
| Applying edits | Restart CarPlay validates, saves, and applies settings; closing settings discards unsaved edits. |
| Music and navigation | DiPlay-style music/navigation stream numbers 0–20; redundant usage selectors removed, and 0%–200% software navigation gain, without changing system volume or music/call/Siri gain. |
| About | Shows the installed app version, Android version, and API level. |
| Wi-Fi startup recovery | Releases the first failed automatic hotspot, attempts a Wi-Fi off/on cycle, then retries once with a fresh manager. If switching is denied, only rebuilds the hotspot. Manual hotspots are excluded. |
| Channel strategy | Wi-Fi Direct supports automatic 5 GHz, channel 36 or 149 preferences, and specific non-DFS channel requests, with optional fallback on explicit rejection. |
| Scan suppression | Opt-in framework autojoin suppression adapted from DiPlay; requires approved local ADB. |
| Installation and updates | Independent package and fixed APK signing identity for coexistence and subsequent personal updates. |

Startup recovery is enabled by default. On cancellation or exit, it attempts to restore Wi-Fi that it disabled, and the next hotspot startup waits for restoration. Android and firmware permissions determine whether switching is allowed. Connection stability and RF behavior still require vehicle testing.

## Usage

1. For wireless CarPlay, pair the iPhone and head unit over Bluetooth.
2. Before video starts, tap the top-right Connection settings button or swipe down with three fingers.
3. Under Connection, configure the MFi target and wired/wireless mode, then adjust audio, display, and window settings. An additional edge gesture can be enabled under Startup & window.
4. Tap **Restart CarPlay** at the top right to save and apply. There is no need to scroll to the bottom. Closing settings discards unsaved edits.
5. Provide the hardware, files, or service required by your selected authentication method and wait for CarPlay to connect.

The personal build prefers Simplified Chinese independently of the head unit's system language.

## Music, navigation, and in-app volume

DiPlay-style numbered stream bindings are available separately for music and navigation: 0 preserves usage routing; 1–20 request the corresponding Android/vendor stream (14 is navigation on some ROMs). Unsupported or uninitialized streams are released and fall back to usage routing. Save with Restart CarPlay; closing settings discards edits. Existing installations default to 0 to preserve routing.

The Music channel binding and Navigation channel binding usage selectors have been removed. Their saved overrides are ignored and deleted on save; numbered streams and navigation gain retain existing values. Channel 0 uses the original role (media for music, navigation for guidance); channel 3 can still request the media stream as a compatibility option. Quality-menu channel counts mean mono/stereo, separately from numbered streams. Firmware decides physical buses, speakers and system volume groups.

Navigation volume ranges from 0% to 200% in 5% steps: 0% mutes and 100% preserves the original signal; boosting can clip. Gain is applied to decoded navigation/alert PCM before local playback, without calling system volume APIs. Selecting stream 3 for navigation still changes only guidance, preserving music, calls, Siri, and the separate microphone gain. Tap Restart CarPlay to save and apply; closing settings discards edits.

Generic high-quality music (102) and alternate guidance (101) streams are classified as music and navigation respectively; explicit roles, calls, and Siri take precedence. The existing AAOS Advanced channel mapping option still determines how ordinary default/compatibility streams are classified. Guidance already mixed into music by the phone cannot be separated. Vehicle checks should play music alongside guidance at 0%, 50%, and 100%, also after selecting a numbered stream for guidance, while checking calls and Siri remain unaffected.

## Wi-Fi scan suppression and channels

Channel strategy is under Settings → Connection; ADB scan suppression is inside Advanced wireless options on that page. They apply after saving and restarting CarPlay. **Channel requests and scan suppression are limited to Wi-Fi Direct on Android 10+.** Android 9 uses LocalOnlyHotspot instead.

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

## Web rendering removed

The personal branch removes the web server, TLS certificates, HTML, NanoHTTPD/WebSocket dependencies, remote audio/video forwarding, Web settings and build flag. The instrument preview window that was only enabled with Web output is also removed. Local main-screen decoding, touch, startup geometry and audio routing remain. Old `web_display_*` preferences are never read and cannot redirect playback. The old `xcertplay.webDisplay` flag no longer provides any feature. The optional Web CI job is retired; tests verify the removed runtime classes are absent.

## Video buffer and 0 ms

Settings → Display & video → Video buffer limit offers Compatibility, **Immediate (0 ms), 20, 50, 100, 150, 200, 250 and 300 ms**. Compatibility preserves the original 8-frame input queue and output rendering. Other choices bound the compressed queue using frame rate and skip expired decoded pictures only when a newer output is already available. Compressed reference frames remain fully decoded; the final/static picture always renders. Immediate adds no delay and presents the latest ready output. This is a queue limit, not an A/V offset or guaranteed total latency; network, codec and system delays remain. Try 50 ms first. Audio and video are saved separately; Restart CarPlay applies and closing settings discards. Restore display defaults also restores Compatibility buffering.

## Fixed APK signing

Personal debug and release APKs share one dedicated RSA key. Its public certificate SHA-256 is pinned in [`signing/personal-certificate.sha256`](signing/personal-certificate.sha256). Gradle checks before packaging, and CI verifies the resulting APK before upload. **Normal builds fail if the key is missing or mismatched; they never generate a temporary key or fall back to a default debug signer.**

The repository Actions secret **`PERSONAL_ANDROID_SIGNING`** holds the complete JSON from the offline backup's `github-secret.txt`: PKCS#12 Base64, passwords, and alias. Save it once under Settings → Secrets and variables → Actions and reuse it. The private key is excluded from source and public artifacts; the maintainer keeps the offline backup.

APK signing keys and MFi authentication keys serve different purposes and are not interchangeable.

## Media buffer and low-latency presets

Settings → Audio → Main media buffer (latency) offers **0, 20, 50, 100, 150, 200, 250, 300…1000 ms**, in 50 ms steps after 50. Existing saved values remain unchanged; a fresh installation still defaults to **100 ms**. Restart CarPlay saves and applies; closing settings discards the draft.

This controls main-media AudioTrack PCM capacity, not an audio/video synchronization offset or an end-to-end latency guarantee. Smaller buffers can reduce queued audio but tolerate less network jitter. Try 50 ms before 20 ms, and return to a larger buffer if playback becomes intermittent. Android may enforce a larger minimum; `actualBufferFrames` in the audio log reports the allocated capacity. Video has its own independent queue limit. Zero audio buffering uses the minimum system AudioTrack capacity and calls play after the first PCM sample frame is written; Android 12+ also attempts a one-frame device start threshold. Driver and hardware latency remain.

The startup threshold and blocking pre-play writes stay within the track capacity, avoiding a full unstarted 20 ms track. The setting also covers identified 102/default music streams; calls, Siri and navigation retain their own paths.

## Build

### Requirements

- JDK 25 is recommended to match CI; launching Gradle requires at least JDK 17, and the daemon toolchain uses Java 25.
- Gradle Wrapper 9.5.0, Android SDK Platform 37, Build Tools `36.0.0`, and NDK `28.2.13676358`.
- Python 3 for CI signing and APK verification helpers.
- An iPhone and compatible head unit for device testing; USB/CH341 or native I2C authentication also requires the appropriate MFi hardware.

### GitHub Actions

Select `personal-dev` under **Android Build → Run workflow**, leaving `bootstrap_unsigned` off. Normal builds use the fixed key, run unit tests and lint, build only the automotive APK, and verify package and signing identity before uploading:

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

The [BYD car-icon build](https://github.com/whoyang233/xcertplay/actions/runs/39572068305) from source `1f69a4a6e7a6adc242c5378ccd7ba2902d8e642b` passes **431 tests with no failures, errors or skips; lint has no errors (83 common / 3 automotive warnings)**. The sole automotive APK is signed and verified as 1.4.2B / 1402. Package, pinned certificate, providers and permissions match the previous personal build. BYD icon/home handling, bounded icon payloads and video buffering are present; removed usage-binding classes, Web rendering and vehicle JSON resources are absent. Size: 47,755,047 bytes; SHA-256: `8656d2c4c34376d97ff94a52e18077f2bb7dd25a2e84a7b2f97c442f7542f69e`. Vehicle launcher behavior and audio routes need device validation.

The [screenshot-feedback validation](https://github.com/whoyang233/xcertplay/actions/runs/39567775549) from source `47d6c78538027ce20dd7389d7252677573bfa875` passes **421 tests with zero failures, errors or skips; lint has no errors and retains 82 common / 3 automotive warnings**. Coverage includes card separation, ADB button insets/actions, removed usage preference migration, numbered streams and navigation gain, plus existing audio/video/connection regressions. [Updated complete gallery](docs/SETTINGS_SCREENSHOTS.zh-CN.md) · [Reports](https://github.com/whoyang233/xcertplay/actions/runs/39567775549/artifacts/12331110976). Signing, APK assembly, verification and upload are skipped. Captures are test previews; vehicle routing and connections need device validation.

The [source-only UI/buffer validation from 2026-10-21](https://github.com/whoyang233/xcertplay/actions/runs/39561641865), source `3b228560ebb64c98b69d7d0fc72c24344102cfe6`, passes **418 tests with no failures, errors or skips; lint has no errors and retains 82 common / 3 automotive warnings**. Coverage includes actual settings pages, video preset draft/save/reload, immediate audio startup, decoded-output catch-up/static frames, one-frame backpressure, and absent Web classes/dependencies. Signing, APK assembly, APK verification and APK upload are explicitly skipped. [Screenshots and configuration notes](docs/UI_BUFFER_SETTINGS.zh-CN.md) · [Complete reports/captures](https://github.com/whoyang233/xcertplay/actions/runs/39561641865/artifacts/12328316122). Captures use an Android 13 / API 33 Full-HD test environment; vehicle latency and firmware behavior still need device validation.

The following record describes the previously downloadable APK; this update produces no new APK.

The [verified build from 2026-10-20](https://github.com/whoyang233/xcertplay/actions/runs/39354887281), commit `5b319fa45bf87afd47b8b5646933492341aea95d`, passed **396 unit tests with no failures, errors or skips; lint had no errors and retained 83 common / 3 automotive warnings**. The 25 BYD source/migration tests cover CAN/CANFD, conversions, partial/invalid readings, firmware catalog caching, failure recovery, freshness, subscriptions, diagnostic isolation, cancellation and migration. iAP2 tests verify remaining-energy and charging parameter encoding. The 8 additional executions cover preserved buffer settings, low-preset persistence and discard, large-font controls, and safe startup/blocking writes for small tracks. Prior popup, Opus, focus and wired-recovery regressions remain passing. Navigation gain, audio bindings, microphone, Wi-Fi recovery and scan-suppression regressions passed. Settings checks cover Full-HD at multiple densities, narrow windows with 150% fonts and short channel dialogs. The [validation artifact](https://github.com/whoyang233/xcertplay/actions/runs/39354887281/artifacts/12235570302) includes fixture screenshots. Actual head-unit ADB, available fields, navigation use, installation and connection behavior still require vehicle testing.

The historical verified run produces only the automotive APK. Package/version (1.4.2B / 1402), the pinned signer, and provider/custom-permission identities are verified. Startup sizing, touch adaptation, numbered audio binding and BYD ADB code are included. Old vehicle JSON assets/parser and Android Car property backend, browser assets/server/certificate and NanoHTTPD/NanoWSD remain excluded. That historical optional Web job passed 88 common tests. Current sources remove that module and CI job completely; this update validates sources and captures UI screenshots without packaging an APK.

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
