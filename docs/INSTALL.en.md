# Install DUO Recreate

[Português](INSTALL.pt-BR.md) · [English](INSTALL.en.md) · [Español](INSTALL.es.md) · [Home](README.en.md)

## 1. Check the requirements

- **Android 13+**, **arm64** device, matching this APK distribution.
- An **active LSPosed framework or implementation compatible with the legacy Xposed API**, supporting your Android version and ROM.
- Only **one Duo module** enabled for System UI. Disable the original Duo or another fork first.

**Installing the APK alone does not modify the status bar. Shizuku does not replace LSPosed.** System UI injection requires the framework. Without it, you can use the settings app and preview, but the injected ring will not appear.

## 2. Prepare the framework

If LSPosed already works, proceed to step 3. Otherwise follow the official instructions for your framework and compatible root manager. Typically this involves Magisk or KernelSU with a suitable Zygisk implementation, installing the framework ZIP through the root manager, and rebooting. Confirm the framework manager reports **active** before enabling DUO.

The [original LSPosed project](https://github.com/LSPosed/LSPosed) documents Android 8.1–14 support. For newer Android versions, choose a maintained implementation that explicitly supports your version, such as [Vector](https://github.com/JingMatrix/Vector), which supports the legacy API. Framework compatibility does not certify DUO integration on your ROM. The APK's Android 13+ requirement is not a guarantee that an old framework supports newer Android versions.

Bootloader unlocking and rooting depend on the device; this guide assumes that environment is prepared. DUO does not perform those operations. **Granting root access to DUO itself is optional:** Canvas and basic diagnostic export do not require it. This is separate from the environment needed to install the framework.

## 3. Install and enable

1. Open [our fork's latest release](https://github.com/edugrepo1-jpg/duoStatusBar/releases/latest). Download **DUO-Recreate-…apk** from **Assets**; source archives are not installers.
2. Install the APK. Install over an existing DUO Recreate with the same signature to preserve settings.
3. Open the app and choose Portuguese, English or Spanish.
4. In LSPosed/the framework manager, open **Modules → DUO Recreate**, enable it and select **System UI (`com.android.systemui`)** as the scope. Do not select every app or the `android` process.
5. Reboot the phone so System UI loads the module. Reopening the settings app is insufficient.
6. Open **Home → Customize status bar → Configure**, enable the switch and check module activation on Home.

## 4. Customize

| Tab | Controls |
|---|---|
| **Visual** | Size, position, stroke, percentage, icon scale, battery colors and panel headers. |
| **Effects** | Displayed states, universal/individual dwell, fades, charging, unlock and interactive preview. |
| **Settings** | Language, theme, optional access, Shizuku, updates and diagnostics. |

Use **Visual → System panels** to enable notifications and Quick Settings separately and set their sizes. Header space limits actual size. Orientation settings can be shared or independent; check this before adjusting landscape.

The preview uses simulated states; use **Apply to status bar** to transfer changes. Media, screenshots, location and privacy depend on Android signals and optional access. Grant only the access needed for the features you use.

**Shizuku is optional:** it provides additional stock-icon hiding and other authorized actions. The module handles primary hiding. Android's icon blacklist is global: if you disable DUO in one orientation or panel, also disable additional Shizuku hiding to avoid missing indicators there.

## 5. Update or restore

Install a newer fork APK over the existing app and restart System UI or reboot. Package: `io.github.RECREATE.statusbar`. A different signing key cannot update this distribution. The original app uses a different package/signature.

Before uninstalling, restore stock icons through the Shizuku option if used. Disable DUO Recreate in the framework and reboot. For a severe System UI failure, follow your root manager/framework's documented recovery or safe-mode procedure; there is no universal button sequence.

## Troubleshooting

| Symptom | Check |
|---|---|
| App opens but no ring | Framework active, System UI scope, DUO switch enabled and reboot after activation. |
| Duplicate ring or overlapping icons | Another Duo module remains enabled; check optional additional hiding. |
| Missing ring in a panel | Panel enabled and a recognized anchor on the ROM. Unknown headers preserve stock icons. |
| Landscape differs | Independent orientation settings and header dimensions. |
| Missing media/headset battery | Required optional access, active playback and data provided by Android/accessory. Invalid headset battery keeps phone battery. |

Open the affected screen, then export **Settings → Diagnostics**. App root permission is not required; the report distinguishes configuration, detection, execution and unavailable access. A working preview does not certify physical ROM integration.

README badges query asset downloads and the latest release from **this fork**. Downloads are not installations or unique users. Android 13+ is the APK minimum (`minSdk 33`), not universal ROM certification.
