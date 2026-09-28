# Discord Controller

**Get backup the instant you're jumped.** Discord Controller watches your health in PvP and, the moment a player is beating you down, automatically posts to your team's Discord — with your **coordinates**, the **server IP**, and **who's attacking you**.

Setup is a single **webhook URL** — copy it from your Discord channel, paste it in. No bot, no login, nothing to host.

---

## Features

- 🚨 **Automatic attack alert** — fires when the same player lands enough hits and damage in a short window.
- 🩸 **Low-health emergency** — an instant `@everyone` the moment your HP drops critically mid-fight.
- 🆘 **Manual SOS** — one keybind (or command) to call for help any time.
- ☠️ **Death alert** *(optional)* — fires when you go down.
- 🎯 **Smart attacker detection** — inferred from knockback + proximity, and it flags when you're ganged by several players.
- ⚔️ **Clan war posts** — announce your clan vs an enemy and the time with a single command.
- 🖥️ **Sleek OLED + neon UI** — a glowing HUD (armed / cooldown / live combat) and an animated in-game menu with a custom font.

---

## Setup — 30 seconds

1. In Discord: **Channel → Edit Channel → Integrations → Webhooks → New Webhook → Copy URL**.
2. In-game: open **`/dcontroller`** → **Webhooks** tab → paste the URL → **Add**.
3. **Tuning** tab → arm the auto-alert, hit **Send Test**. Done.

> Everything is configured in the in-game menu — no config files to edit.

---

## Commands

| Command | What it does |
|---|---|
| `/dcontroller` | Open the menu |
| `/dcontroller alert now` · `test` | Manual / test alert |
| `/dcontroller alert auto\|lowhealth\|death\|sound\|ping on\|off` | Toggle alert types |
| `/dcontroller alert webhook add\|remove\|list\|clear <url>` | Manage webhooks |
| `/dcontroller alert threshold hits\|health\|radius\|window\|cooldown <v>` | Tune the detector |
| `/dcontroller clan war <enemy> <time> [details]` | Post a clan-war call |

Keybinds live in **Options → Controls → Discord Controller** (open menu, send SOS, toggle auto-alert, answer call).

---

## Compatibility

- **Fabric** 1.21.9 – 1.21.11
- Requires **Fabric API** · **Java 21**
- **Client-side** — install it yourself; no server mod needed.
