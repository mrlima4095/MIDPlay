# JM2PC Client (embedded)

JM2PC — Java Mobile to PC — client 3.1.0, by Walter Alves Wanderley
(Recife, Brazil). Upstream project: http://jm2pc.sourceforge.net

This directory vendors the client sources so MIDPlay can build them into its
own JAR. The code is licensed under the GNU GPL v3 (see `COPYING` and the
header of every source file). Unlike the other embedded subapps, JM2PC is not
a git submodule: the upstream SVN checkout is vendored here as-is.

## Layout

- `client/src/jm2pc` — client sources.
- `client/res/jm2pc/i18n/messages_*` — message catalogs, packaged under
  `/jm2pc/i18n/` in the JAR.

## Changes from upstream 3.1.0

- `JM2PCMIDlet` no longer extends `MIDlet`. It exposes
  `open(MIDlet host, Runnable exit)`, `isRunning()` and `kill()` so MIDPlay can
  run it as an embedded subapp (menu entry, htop monitor, display sharing).
- The constructor receives the host MIDlet and uses its `Display`.
- `destroyApp` returns to the MIDPlay menu instead of calling
  `notifyDestroyed()`, and `kill()` releases the socket without navigating.
- The About splash only switches screens while the instance is still alive.
- RMS records are prefixed with `jm2pc-` (`jm2pc-cfg`, `jm2pc-servers`,
  `jm2pc-i18n`) to avoid clashing with MIDPlay and other subapps.
- Message catalogs live under `/jm2pc/i18n/` instead of `/i18n/` and are
  ASCII-only (the original reader decodes raw bytes with the device default
  encoding).
- Sources and catalogs were converted from ISO-8859-1/CRLF to UTF-8/LF.

The PC-side server is distributed separately at the repository root in
`JM2PC Server/`.
