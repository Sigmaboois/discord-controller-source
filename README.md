<div align="center">

<img src="src/main/resources/assets/discordcontroller/icon.png" width="96" height="96" alt="Discord Controller"/>

# Discord Controller

**Get backup the instant you're jumped in PvP** — your team gets pinged on Discord with your coords, the server IP, and who's attacking you.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.9%20–%201.21.11-brightgreen?style=for-the-badge)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue?style=for-the-badge)
![Environment](https://img.shields.io/badge/Side-Client-orange?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-lightgrey?style=for-the-badge)

</div>

---

Setup is a single **webhook URL** — copy it from your Discord channel, paste it in. No bot, no login, nothing to host.

## ✨ Features

- 🚨 **Automatic attack alert** — fires when the same player lands enough hits + damage in a short window.
- 🩸 **Low-health emergency** — an instant `@everyone` the moment your HP drops critically mid-fight.
- 🆘 **Manual SOS** — one keybind (or command) to call for help any time.
- ☠️ **Death alert** *(optional)*.
- 🎯 **Smart attacker detection** — inferred from knockback + proximity; flags when you're ganged.
- ⚔️ **Clan war posts** — announce your clan vs an enemy + the time in one command.
- 🖥️ **Sleek OLED + neon UI** — glowing HUD (armed / cooldown / live combat) and an animated menu with a custom font.

## 🚀 Setup (30 seconds)

1. Discord: **Channel → Edit → Integrations → Webhooks → New Webhook → Copy URL**.
2. In-game: **`/dcontroller`** → **Webhooks** tab → paste → **Add**.
3. **Tuning** → arm the auto-alert → **Send Test**.

## 💬 Commands

| Command | What it does |
|---|---|
| `/dcontroller` | Open the menu |
| `/dcontroller alert now` · `test` | Manual / test alert |
| `/dcontroller alert auto\|lowhealth\|death\|sound\|ping on\|off` | Toggle alert types |
| `/dcontroller alert webhook add\|remove\|list\|clear <url>` | Manage webhooks |
| `/dcontroller alert threshold hits\|health\|radius\|window\|cooldown <v>` | Tune the detector |
| `/dcontroller clan war <enemy> <time> [details]` | Post a clan-war call |

Keybinds: **Options → Controls → Discord Controller**.

## 📦 Compatibility

- **Fabric** 1.21.9 – 1.21.11 · requires **Fabric API** · **Java 21** · client-side.

## 🛠️ Building

No Gradle wrapper is committed — use **Gradle 9.5+** with **JDK 21**:

```bash
gradle build
```

Jars land in `build/libs/`. Bundled UI font: **Poppins** (SIL Open Font License).
