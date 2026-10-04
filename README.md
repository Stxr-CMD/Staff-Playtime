# StaffPlaytime

Tracks how long your staff really play. Standing still (AFK) does not count.
Made for Paper 1.21.8, Java 21.

## Build

```
mvn clean package
```

The jar ends up in `target/StaffPlaytime.jar`. Put it in `plugins/` on your **backend servers**.

## Proxies

Velocity/BungeeCord do not need the plugin. Install it on every backend server and
set `storage.type: mysql` in `config.yml` with the **same database** on all of them.
Then playtime from every server is added together in one leaderboard.
With `sqlite` each server keeps its own separate file.

## How counting works

- A player counts as staff if they have the permission of a rank in `config.yml`
  (LuckPerms: `group.helper`, `group.mod`, ... `group.owner`).
- Every second the plugin adds +1 second for each staff member who moved in the last
  `tracking.idle-seconds` (default 30). Stand still for 30 seconds and counting stops
  until you move again.
- Walking, flying, riding boats/minecarts/horses all count. Tiny jitter does not
  (`tracking.min-move-distance`). Camera-only movement is off by default.
- Playtime is saved per week, so "this week" and "last week" are always available.

## Commands

Alias: `/spt`, `/splaytime`

| Command | What it does | Permission |
|---|---|---|
| `/staffplaytime help` | Command list | `staffplaytime.use` |
| `/staffplaytime check [player]` | Playtime in chat | `staffplaytime.check` / `.check.others` |
| `/staffplaytime gui` | Opens the GUI | `staffplaytime.gui` |
| `/staffplaytime leaderboard` | Opens the leaderboard GUI | `staffplaytime.leaderboard` |
| `/staffplaytime lastweek` | GUI on the previous week | `staffplaytime.lastweek` |
| `/staffplaytime reset <player> [all]` | Resets this week (or everything with `all`) | `staffplaytime.reset` |
| `/staffplaytime resetall [history]` | Resets all staff, run twice to confirm. GUI reloads after | `staffplaytime.resetall` (+ `.history`) |
| `/staffplaytime reload` | Reloads config.yml | `staffplaytime.reload` |

Other permissions: `staffplaytime.alltime` (All Time tab in the GUI),
`staffplaytime.exempt` (never tracked), `staffplaytime.admin` (everything).

## GUI

Player heads sorted by playtime, 28 per page. Bottom row: previous page, This Week,
Last Week, Close, All Time, Refresh, next page. Hover a head to see rank, this week,
last week, all time and online status.

## Changing things

- Ranks, messages, colors, GUI materials, week start day, timezone: `config.yml`
- Button positions in the GUI: the `SLOT_` numbers at the top of `LeaderboardGui.java`
- A new subcommand: add a `case` in `StaffPlaytimeCommand.onCommand`, write the method,
  add it to tab completion and `plugin.yml`
- A new config option: add it to `config.yml`, then add a field in `Settings.java`

## Code layout

```
StaffPlaytimePlugin     start/stop, tasks
Settings, StaffRank     config values and ranks
Messages                sends messages from config
tracking/               PlaytimeTracker (counting), MovementListener (movement)
storage/                Storage (SQLite/MySQL), StaffEntry
gui/                    LeaderboardGui, GuiListener
command/                StaffPlaytimeCommand
util/                   TimeFormat, WeekUtil
```

If your server jar does not include the SQLite or MySQL driver, shade the driver into the plugin.
