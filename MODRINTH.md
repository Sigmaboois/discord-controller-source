# Discord Controller

Control Discord from inside Minecraft. Send DMs, answer calls, and fire under-attack / help alerts that carry your coordinates, dimension and server IP to your friends. Link teammates' Discord accounts to their Minecraft accounts so their alerts reach you in-game with a one-click join button.

## How the token is handled

The mod drives your own Discord account with a **user token**. On Windows it can auto-detect that token by reading Discord's local desktop data (DPAPI + AES-GCM), or you can paste a token manually.

- **Read locally only** — the token is extracted from your own machine and stored in `config/discord-controller.json`.
- **Encrypted + password protected** — AES-256-GCM with a PBKDF2-HMAC-SHA256 master-password key. The raw token is never written to disk.
- **Never sent anywhere else** — the only network traffic is to Discord's own API to send the messages and alerts you request. No telemetry, no uploads, no third parties.

## Features

- 🚨 Automatic attack alert (hits + damage in a window)
- 🩸 Low-health emergency alert
- 🆘 Manual help alert
- ☠️ Optional death alert
- 🎯 Smart attacker detection
- 📨 In-game DMs (`/dmsg`)
- 📞 Answer-call hotkey
- 🔗 Discord ↔ Minecraft linking with in-game teammate alerts and a one-click join button
- 🖥️ Animated neon menu + HUD

## Compatibility

- Fabric · Minecraft 1.21.11 · Fabric API · Java 21 · client-side
