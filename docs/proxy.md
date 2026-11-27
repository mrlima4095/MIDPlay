# Discord HTTP proxy for Discofi

Discofi is designed for direct (TLS 1.2) connections, but many older J2ME devices
need an HTTP proxy. The public proxy used by Discord J2ME (`http://146.59.80.3`)
does not implement `/api/v9/users/@me/mentions`, so Discofi's inbox returns 404.

MIDPlay builds therefore import accounts with this proxy instead:

```
http://72.60.138.179:8091
```

It is hosted from this repository's vendored proxy source and includes the
mentions route. Existing accounts imported from the old proxy are upgraded
automatically when Discofi loads.

There is no official Discofi proxy: the project is intentionally proxyless, and
the legacy `discord-j2me` proxy (repo marked `[OLD]`) never implemented the
mentions route. Self-hosting that proxy with the small patch in this repo is the
practical workaround for devices without TLS 1.2.

> A third-party proxy sees your Discord token. Use your own instance whenever
> possible; direct connection is still the safest option on devices with TLS 1.2.

## Host your own

The source lives in `third_party/discord-j2me/proxy/` (a git submodule).

```bash
cd third_party/discord-j2me/proxy
npm ci --omit=dev
PORT=8091 node index.js
```

Then set the API URL in Discofi's account settings to
`http://<your-host>:8091`.

For a persistent service, a systemd user unit example:

```ini
[Unit]
Description=MIDPlay Discord HTTP proxy
After=network-online.target

[Service]
WorkingDirectory=/home/fetuber/MIDPlay/third_party/discord-j2me/proxy
Environment=PORT=8091
ExecStart=/usr/bin/node /home/fetuber/MIDPlay/third_party/discord-j2me/proxy/index.js
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
```

Enable lingering (`loginctl enable-linger <user>`) if it must survive logout.

The HTTP gateway proxy is a separate project (`discord-j2me-server`) and is only
needed by the legacy Discord client for live notifications.
