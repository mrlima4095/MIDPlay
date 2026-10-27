# MIDPlay

![AppIcon](/res/Icon.png)

An online music player and offline toolkit for J2ME (Java ME) devices — CLDC 1.1 / MIDP 2.0. It streams from several sources, keeps a local library, and embeds companion apps (Telegram, GitHub, Discord, Wattpad, Pinterest, Reddit) while playback continues in the background.

## Features

- **Multi-source streaming** — NCT, SoundCloud, YT Music and Spotify, at 128 or 320 kbps.
- **Discovery** — browse new playlists and search tracks, playlists and albums, with recent-search history.
- **Playback** — play/pause/stop, previous/next, seek, volume, repeat (off/one/all), shuffle, and resume position.
- **Sleep timer** — stop playback or close the app after a set time.
- **Queue and library** — play queue (add, add all, sort, save as playlist), favorites, custom playlists, recent history and track details.
- **Lyrics** — fetch lyrics for the current or a downloaded track, search by artist/title, paginate, and save them to the notepad.
- **Equalizer** — presets and manual bands through JSR-234 (AMMS), with a graceful fallback on devices that do not support it.
- **Themes** — light/dark mode and 16 accent colors.
- **Localization** — English, Brazilian Portuguese, Turkish, Vietnamese, Polish and Hebrew.
- **Updates** — automatic or manual update check that opens the download link.

## Fork Features

This fork builds on upstream MIDPlay 1.7.1:

- **Downloads** — save tracks to the memory card for offline playback, with a download queue, bulk selection, reordering, deletion and a configurable folder (JSR-75).
- **File explorer** — browse and search storage, rename and delete files, jump to a path, preview text and images, play local audio, and add local tracks to playlists.
- **Notepad** — multiple notes with custom titles, pinning, search, bulk selection, VNOTE import/export, translation, and a password-protected vault.
- **Tasks** — to-do list with priorities, due dates, overdue filters, search, pinning and bulk delete.
- **Open...** — open any URI from one screen, with history, provider links and an **Economy Mode** that pauses background tasks.
- **Embedded MPGram** — a Telegram client inside the app. Return to MIDPlay without stopping playback, send notes to chats, import an MPGram session from a note, and share links from the other subapps.
- **Embedded GitHub client** — browse GitHub inside MIDPlay.
- **Embedded Discord client** — read and send Discord messages, browse servers and DMs, and receive live updates while MIDPlay keeps running in the background. The login token can be imported from a file or a MIDPlay note.
- **Wattpad** — search stories and authors, keep a reading library, download whole stories for offline reading, and resume where you left off, with archive and mature-content filters.
- **Pinterest** — search pins, boards and users, save favorites into local folders, import a whole board, and download images.
- **Reddit** — browse and search subreddits, users and posts, read comments, follow subreddits and users, save favorites, and optionally translate posts and comments. Uses the Arctic Shift archive, so no Reddit login is required.
- **Translation** — translate notes, Reddit posts and comments through the built-in endpoint.
- **Menu customization** — hide entries and reorder the main menu.
- **Playlist improvements** — reorder tracks and create playlists directly from the playlist picker.
- **Configurable services** — pick the music service, and change the HTTP proxy plus the lyrics, translation, Pinterest and Reddit endpoints in Settings.

## Requirements

- J2ME device with MIDP 2.0 / CLDC 1.1
- Network connectivity for streaming and online services
- JSR-75 (FileConnection) for downloads, the file explorer and offline story reading
- JSR-234 (AMMS) for the equalizer — optional
- Socket connectivity for Discord live updates — optional, requested with `MIDlet-Permissions-Opt`

## Install

1. Download the latest `MIDPlay.jar` and `MIDPlay.jad` from the [Releases](https://github.com/mrlima4095/MIDPlay/releases) page, or build them from source (see below).
2. Keep both files together and install them on the device, or load them in an emulator such as KEmulator.
3. On first launch, grant the requested network and file permissions.

## Build

```bash
git submodule update --init --recursive
./build.sh
```

Requires **JDK 8** (the last toolchain that emits CLDC-compatible bytecode) and **Python 3** for the embedded MPGram, GitHub and Discord sources. ProGuard is bundled in `lib/`, so no separate install is needed. Output: `dist/MIDPlay.jar` + `dist/MIDPlay.jad`.

The script compiles with `-source/-target 1.3` against the CLDC/MIDP bootclasspath, packages the MIDlet, runs ProGuard with `-microedition` (shrink, obfuscate and preverify), then writes the JAD with the final JAR size. Set `JAVA8_HOME` if JDK 8 is not auto-detected.

To regenerate the translations after editing `langs/*.json`, run:

```bash
node tools/build_lang.mjs
```

See `build.sh` for the full pipeline and `tools/` for the subapp preparation scripts.

## Tech Stack

- Java ME, MIDP 2.0 / CLDC 1.1, JSR-75 (FileConnection) and JSR-234 (AMMS)
- Record Management System (RMS) with a small JSON layer for local storage
- Embedded [MPGram client](https://github.com/shinovon/mpgram-client), [GitHub-j2me](https://github.com/mrlima4095/GitHub-j2me) and [Discord for J2ME](https://github.com/mrlima4095/discord-j2me) as git submodules
- REST endpoints for streaming, lyrics, translation and the Reddit/Pinterest proxies

Third-party notices are listed in [`res/THIRD_PARTY_NOTICES.txt`](/res/THIRD_PARTY_NOTICES.txt).

## Contributing

**Code:** fork → feature branch → commit → push → open a Pull Request.

**Language:** copy `langs/en.json`, translate the values (keep the keys), add `language.<code>` with the language's native name to `en.json` and the translated file, then run `node tools/build_lang.mjs` and commit both the new JSON and the regenerated `src/midplay/util/Lang.java`. Missing keys fall back to English.

## Credits

Upstream MIDPlay by Duy Pham, with contributions from symbuzzer, GoldenDragon, Spajciuch and gtrxAC. MPGram by Arman Jussupgaliyev. Discord for J2ME by gtrxAC.

## License

MIT — see [LICENSE](/LICENSE).
