# Discord In-Game Controller

A client-side Fabric mod (Minecraft 1.21.11) that puts Discord in your hands from
inside Minecraft — send DMs, answer calls, and fire "under attack" / help alerts
that carry your coordinates, dimension and server IP straight to your friends.

## Features

- **Animated in-game menu** — open with `/dcontroller` or the *Open Discord Controller Menu* keybind. Tabs: Account, Recipients, Targets, Delivery, Tuning, Alerts, Options. Themed, with fade-in, tab transitions, hover glows, click flashes, ambient particles, avatars and a custom logo.
- **DMs** — `/dmsg <recipient> <message>` sends a nicely formatted DM to a saved recipient, stamped as sent from the Discord Controller mod in-game.
- **Answer calls** — the *Answer Discord Call* keybind (or `/answer`) fires a configured hotkey; bind Discord's own "Answer Call" as a **Global** keybind to the same key.
- **Attack alerts** — when the same player lands more than *N* hits and takes significant health off you inside a time window, an alert fires with your coordinates, dimension, the server IP, the attacker (and how many others), and hit/damage stats.
- **Emergency (low-health) alert** — fires the instant your health drops to/below a threshold while a player is attacking, even if the normal auto-alert is off.
- **Death alert** (optional) — fires when you're killed.
- **Manual help alert** — the *Send Help Alert* keybind or `/dcontroller alert now`.
- **Smart attacker detection** — inferred from the synced attacker, then knockback direction, then proximity.
- **Delivery options** — DM individual user IDs (each gets pinged), post to a **group DM**, and/or post rich embeds to one or more **webhooks**.
- **HUD** — an armed/cooldown chip and a live "in combat" indicator, plus a toast when an alert fires, and an optional alert sound.
- **In-game teammate alerts** — link a recipient's Minecraft name to their Discord, and when they send you an alert you get a big coloured in-game chat message. It shows the coords and dimension, and — if you aren't already on their server — the server IP with a one-click **JOIN** button that connects you straight to that server.

## How the token works

The mod drives your own Discord account using a **user token**. On Windows it can
auto-detect that token by reading Discord's local desktop data (DPAPI-decrypting the
master key, then AES-GCM-decrypting the token). You can also paste a token manually.

- **Local only.** The token is read from your own machine and stored in
  `config/discord-controller.json`.
- **Encrypted + password protected.** The token is encrypted with AES-256-GCM using a
  key derived from a master password (PBKDF2-HMAC-SHA256, 200k rounds). The raw token
  is never written to disk.
- **Never shared.** The only network calls the mod makes are to Discord's own API to
  send the messages and alerts you ask for. Your token is not uploaded, logged, or
  sent to any third party.

Set your master password once, then unlock per session:

```
/dcontroller password <password>   set the master password
/dcontroller unlock <password>     decrypt the token for this session
/dcontroller lock                  clear the token from memory
```

## Keybinds (Options → Controls → Discord Controller)

- Open Discord Controller Menu
- Answer Discord Call
- Send Help Alert
- Toggle Auto Attack-Alert

All default to unbound; assign them in-game.

## Commands

```
/dcontroller                          open the menu
/dcontroller password <password>      set the master password
/dcontroller unlock <password>        unlock the token for this session
/dcontroller lock                     lock (clear token from memory)
/dcontroller token <token>            set + verify the token
/dcontroller detect                   auto-detect the token
/dcontroller account                  show the logged-in account
/dcontroller recipients add|remove|list
/dcontroller recipients mc <name> <mcName>   link a recipient to a Minecraft account
/dcontroller receive on|off           in-game teammate alerts
/dmsg <recipient> <message>           DM a recipient
/answer                               fire the answer hotkey
/djoin <server>                       connect to a server (also the chat JOIN button)

/dcontroller alert now|test
/dcontroller alert auto on|off
/dcontroller alert ping on|off        @everyone prefix
/dcontroller alert lowhealth on|off
/dcontroller alert death on|off
/dcontroller alert sound on|off
/dcontroller alert targets add|remove|enable|disable|list <ids...>
/dcontroller alert group <id> | group clear
/dcontroller alert webhook add|remove|list|clear <url>
/dcontroller alert threshold hits|health|radius|window|cooldown|manualcooldown|lowhealth <value>
/dcontroller alert config            print current settings
```

## Building

No Gradle wrapper is committed. Build with Gradle 9.5+ and JDK 21:

```
gradle build
```

The mod jar lands in `build/libs/`. To publish to Modrinth, set `MODRINTH_TOKEN`
and `modrinth_project_id` in `gradle.properties`, then run `gradle modrinth`.
