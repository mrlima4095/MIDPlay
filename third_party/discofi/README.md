# Discofi

**This repo is for the J2ME version. For the Android version, [go here](https://github.com/gtrxAC/Discofi-Android).**

Discofi is a new Discord client for select Java devices (Nokia/Sony Ericsson), currently in development. Its goal is to be an evolution of [Discord J2ME](https://github.com/gtrxAC/discord-j2me) and [Droidcord](https://github.com/leap0x7b/Droidcord) with a focus on proxyless connectivity, user-friendly UI, and maintainability.

> [!IMPORTANT]
> **Discofi is not yet a fully featured replacement for Discord J2ME.**
>
> It lacks some features of Discord J2ME (like emojis) but also has some features not present in Discord J2ME (like the inbox).

### <div style="text-align: center;">[Downloads and Installation Guide](INSTALL.md)</div>
### <div style="text-align: center;">[Support Server](https://discord.gg/2GKuJjQagp) <small>(go to #discord-j2me-wap)</small></div>

![](/img/screenshots.png)

## Status
### Working
* Password/token login
* Multiple accounts
* Main menu with servers, DMs, inbox
* Channel list with categories
* Chat screen with pagination, avatars, replies, timestamps
* Sending messages and replies
* Viewing attachments
* Message editing and deleting
* Jumping to replies
* Settings for theme, message count, etc.
* Bluetooth tethering (BlueWAP)

### Planned for later
* Threads
* Forums
* Emojis
* Reactions
* Embeds
* Text formatting with [nnmarkdown](https://github.com/shinovon/nnmarkdown)
* Custom themes
* Languages
* Unread indicators

### Not planned
* Gateway (not practical without a proxy, which is unsafe)
* Sending attachments (unsafe for third-party clients)
* Friend requests (unsafe)
* Joining servers (unsafe)

## Why another Discord J2ME?
In 2024, I started Discord J2ME as my first big Java project. Throughout those two years, several new features have been added to it, and many new things have been discovered along the way, such as Discord's anti-spam measures, and ways to get HTTPS (TLS) connectivity on J2ME devices.

Discord J2ME's codebase has gotten too difficult to manage, and bugs in the client have become increasingly difficult to fix. Discord J2ME was built on false assumptions, like proxies being safe to use, and the system UI being consistent across devices.

Due to this, and the lack of a good Discord client for legacy Android, I decided to start fresh with Discofi. The goal was to build a new client to fix many of Discord J2ME's shortcomings from the very beginning.

**Discofi's main focuses are:**

* **Proxyless connectivity**
  * reduces the risk of account restrictions
* **User-friendly setup and UI**
  * no cluttered menus
  * more descriptive error messages
  * overall less confusion, leading to less support requests
* **Maintainability**
  * clean code
  * less build variants to maintain (midp2, midp2_alt, samsung, etc.)

## Officially supported devices
Any device meeting the [system requirements](#system-requirements) should be able to run the app, but technical support will mainly be provided for these officially supported devices, and the app will be developed mainly with them in mind.
* Symbian devices with the [nnproject TLS patch](https://nnproject.cc/tls/) installed
* Sony Ericsson devices running Java Platform 6 or later
  * This includes most devices from 2006 onwards; check your device [here](https://en.wikipedia.org/wiki/Sony_Ericsson_Java_Platform)
  * Data cable is required
* Nokia devices running Series 40 3rd Edition FP1 or later
  * TLS on these phones is quite CPU-intensive, so it's recommended to find one with a faster CPU or use a Symbian/Sony Ericsson device instead
  * Low-end devices with Series 40 Lite (DCT4 platform) are not supported

## System requirements
| Specification | Minimum | Recommended |
|-|-|-|
| **Java ME support** | MIDP 2.0, CLDC 1.0 | MIDP 2.0, CLDC 1.0
| **Extra APIs** | none\* | JSR-75 FileConnection<br>JSR-82 Bluetooth API
| **JAR size limit** | 200 kB | 1 MB
| **Heap size (RAM)** | 1.5 MB | 2 MB
| **Display** | any color/grayscale screen | 128 × 160 or higher
| **Others** | none | TLS 1.2 support

\* If the recommended APIs are not supported, you may have to use the Lite version.

## For developers
The build system is based on my [j2me-template](https://github.com/gtrxAC/j2me-template/).
