<div align="center">

<img src="src/main/resources/assets/discordcontroller/icon.png" width="96" height="96" alt="Discord In-Game Controller"/>

# Discord In-Game Controller

**Get backup the instant you're jumped in PvP.** A Discord **bot** pings your team — with your coordinates, the server IP, and who's attacking you — and teammates running the mod see it pop up **in-game** too.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.9%20–%201.21.11-brightgreen?style=for-the-badge)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue?style=for-the-badge)
![Environment](https://img.shields.io/badge/Side-Client-orange?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-lightgrey?style=for-the-badge)

</div>

---

## ✨ What it does

- 🚨 **Automatic attack alert** — when the same player lands more than *N* hits and takes real health off you in a short window, it fires an alert on its own.
- 🩸 **Low-health emergency** — the moment your HP crosses a threshold while someone's on you, it screams for help (even if the auto-alert is off).
- 🆘 **Manual help** — a keybind or `/dcontroller alert now` to call for backup any time.
- ☠️ **Death alert** *(optional)* — fires when you go down.
- 🎯 **Smart attacker detection** — infers the attacker from knockback direction + proximity, and reports if you're ganged by several players.
- 📡 **Flexible delivery** — DM chosen teammates, post to a shared **alert channel** with `@everyone`, and/or fire **webhooks** — all through your bot.
- 💬 **In-game teammate alerts** — link a teammate's Minecraft name, and when they call for help you get a **big coloured chat message** in your own game — across servers and singleplayer. Same server? It just says *get to them*. Different server? It hands you the IP.
- ⚔️ **Clan war posts** — the clan owner posts **your clan vs an enemy + the time** (and any details) to a clan webhook with a single command.
- 🖥️ **Beautiful animated menu** — a themed, animated in-game GUI with tabs, avatars, toggles, a live combat HUD, and a bundled **Poppins** UI font. No config files required.

---

<details open>
<summary><b>🚀 Quick start (5 minutes)</b></summary>

1. **Create a bot** at the [Discord Developer Portal](https://discord.com/developers/applications) → *New Application* → *Bot* → **Reset Token** and copy it.
2. **Turn on** the *Message Content* intent (Bot page) and give it permission to send messages / mention everyone.
3. **Invite the bot** to a small server that you and your teammates are in (OAuth2 → URL Generator → `bot` scope).
4. In Minecraft, open the menu with **`/dcontroller`** (or bind a key in *Options → Controls*).
5. **Bot tab** → paste your bot token → *Save & Verify Bot*.
6. **Delivery tab** → set the **Alert channel** (right-click a channel → *Copy Channel ID*) and/or add **targets** (teammate user IDs) and **webhooks**.
7. **Tuning tab** → arm the auto attack-alert, tweak thresholds, hit *Send Test*. Done. 🎉

</details>

<details>
<summary><b>💬 Commands</b></summary>

| Command | What it does |
|---|---|
| `/dcontroller` | Open the animated menu |
| `/dcontroller token <bot token>` | Save + verify your bot token |
| `/dcontroller account` | Show the bot account |
| `/dcontroller recipients add\|remove\|list` | Manage contacts |
| `/dcontroller recipients mc <name> <mcName>` | Link a contact to a Minecraft account |
| `/dcontroller receive on\|off` | In-game teammate alerts |
| `/dmsg <recipient> <message>` | Send a DM through the bot |
| `/answer` | Fire the Discord "Answer Call" hotkey |
| `/dcontroller alert now\|test` | Manual / test alert |
| `/dcontroller alert auto\|lowhealth\|death\|sound\|ping on\|off` | Toggle alert types |
| `/dcontroller alert targets add\|remove\|enable\|disable\|list <ids…>` | Manage alert targets |
| `/dcontroller alert channel <id>` · `webhook add\|remove\|list\|clear <url>` | Delivery routes |
| `/dcontroller alert threshold hits\|health\|radius\|window\|cooldown\|manualcooldown\|lowhealth <v>` | Tune the detector |
| `/dcontroller alert config` | Print current settings |
| `/dcontroller clan name <name>` · `clan webhook <url>` | Set your clan + its webhook |
| `/dcontroller clan war <enemy> <time> [details]` | Post a clan-war call to the webhook |
| `/dcontroller clan announce <text>` | Post a freeform clan announcement |

</details>

<details>
<summary><b>⌨️ Keybinds (Options → Controls → Discord Controller)</b></summary>

- **Open Discord Controller Menu**
- **Send Help Alert**
- **Toggle Auto Attack-Alert**
- **Answer Discord Call**

All unbound by default — assign them in-game.

</details>

<details>
<summary><b>🤝 How the in-game teammate relay works</b></summary>

The bot posts alerts into your shared alert channel. Every teammate running the mod reads that channel and renders the alert in their own game:

- **Same server as the person in trouble?** → *"✔ Same server — get to them!"* + their coordinates.
- **Different server / singleplayer?** → it shows the **server IP** so you can hop over, plus coordinates and the attacker.

Link a teammate's Minecraft name with `/dcontroller recipients mc <name> <mcName>` so their alerts show their in-game name.

</details>

---

## 📦 Requirements

- **Fabric Loader** 0.19+ and **Fabric API**
- **Java 21**
- **Minecraft 1.21.9 – 1.21.11**  *(use the jar matching your version; 1.21.6–1.21.8 predate the input APIs this build uses)*

## 🛠️ Building from source

No Gradle wrapper is committed — use **Gradle 9.5+** with **JDK 21**:

```bash
gradle build
```

The jar is written to `build/libs/`.

---

<div align="center">
<sub>Made with ⛏️ by <b>Juicy Launcher</b> · MIT licensed</sub>
</div>
