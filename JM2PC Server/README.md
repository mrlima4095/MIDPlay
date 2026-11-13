# JM2PC Server

PC side of JM2PC — Java Mobile to PC — version 3.1.0, by Walter Alves
Wanderley (Recife, Brazil). Upstream project: http://jm2pc.sourceforge.net

This is the original binary distribution that pairs with the JM2PC client
embedded in MIDPlay. It is here for convenience; it is not built or packaged
into the MIDlet.

## Running

- Linux: `./runServer_Linux.sh`
- Windows: `runServer_Windows.bat`

The scripts expect a Java runtime on `PATH`. Default settings live in
`jm2pc_server.properties` (port 8888, no authentication).

## Notes

- `ssl/M2PC.jks` is not included: the original distribution shipped a
  self-signed keystore with a well-known password. Generate your own keystore
  if you enable SSL; the certificate `ssl/M2PC.cer` is kept for reference.
- `lib/comm` holds the serial-port natives (Linux `.so` and Windows `.dll`);
  `lib/jsr-82` holds the BlueCove Bluetooth stack.
- Licensed under the GNU GPL v3; see the upstream README and source headers.
