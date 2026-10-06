# DUO Recreate

Independent GPL-3.0 fork of [Duo Status Bar](https://github.com/kvmy666/duoStatusBar) by kvmy666. Android Canvas replaces the Rive renderer while preserving the original ring and all features developed in this fork.

Android package: `io.github.RECREATE.statusbar`. Source namespaces retain upstream provenance. Updates come only from this repository. Original developer contact/support and automatic log delivery are disabled; original authorship is preserved.

## Install

Android 13+ and a working LSPosed environment are required for SystemUI injection. Root is not required **by the app's diagnostic export**; that does not remove LSPosed's environment requirements.

1. Keep the earlier Canvas app installed for the first launch if you want its preferences imported. Only our locally signed Canvas editions qualify; import is one-time, without logs or privileged grants.
2. Disable the old Duo module in LSPosed. Install DUO Recreate, open it and choose Portuguese, English or Spanish.
3. Enable **DUO Recreate**, select System UI as scope and restart the phone. Turn customization on in the app.
4. Use the visual/effects preview before applying changes. Hold the ring to open a small island, drag down to expand, drag up on the header to collapse.

Do not enable both original and fork modules at once. The new package installs separately; it does not silently replace the original app.

## Features and evidence

[Complete preserved requirements and history](docs/fork/HISTORICO-COMPLETO.md) · [Ten-perspective audit](docs/fork/AUDITORIA.md) · [Runtime/battery findings](docs/fork/RUNTIME-E-BATERIA.md) · [Validation](docs/fork/VALIDACAO.md).

The app groups settings into Home, Visual, Effects and Settings, with explanations in compact cards and system/light/dark themes. Twenty requested status categories and nine approved ideas include sequential fades, persistent newest media/recording event, exclusive 3-second unlock check, music/progress, charge estimate/effects, screenshot shutter, volume, recording timer, GPS compass, earbud battery and interactive preview.

Rootless reports separate configured, detected, executed, inaccessible and unverified features. Battery percentages describe the whole phone; no unsupported module-specific mAh claim is made. Detailed device facts exclude private hardware identifiers and content. Share/save logs locally; review free-form text before posting it publicly.

## Build and maintain

Java 17+, Android SDK 36, Build Tools 36.0.0, Gradle wrapper 8.13. The default settings select `app/build-canvas.gradle` (Kotlin 2.1.0, AGP 8.13.2). Run:

```
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 tools/route-module-logs.py --check
```

The debug APK is unsigned. Sign release artifacts using your own protected release key; never commit a signing key, password or token. The update installer requires package and signer to match the installed app, a higher versionCode and SHA-256 from this repository's release asset. A differently signed build cannot update an installed copy.

Retained Rive project files and upstream documents are historical; they are not packaged or used by the Canvas renderer. Nine archived source deliveries were reconstructed as clearly identified historical commits. The original upstream history remains intact. No private conversations, original device logs or keys belong in Git.

## Device validation

Local automated tests and native renders do not certify an OEM phone, sensors, GPU blur or measured battery autonomy. See the validation document for what was actually run and the remaining on-device checks. Do not claim “bug-free” without such evidence.

## License

GPL-3.0. See [LICENSE](LICENSE), original authors and recovered change history. [Original README](docs/fork/UPSTREAM-README.md) is retained as upstream documentation; its old support/update links do not configure this fork.
