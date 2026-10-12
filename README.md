# RewardHologram 🎁

A Paper 1.21.11 plugin that displays per-player floating reward holograms with bobbing animation, skull rotation, individual reward chances, and cooldowns that persist across server restarts. Supports two rendering engines: classic **armor stands** (max compatibility) and modern **Display entities** (scale, glow, billboard, pop animations and advanced skull effects).

## ✨ Features

- **Per-player** — each player sees their own hologram, protected from other players
- **Dual renderer** — `ARMOR_STAND` (Java + Bedrock via Geyser) or `DISPLAY` (TextDisplay/ItemDisplay), selectable globally or per hologram
- **Bobbing animation** — smooth up/down movement with configurable amplitude and speed
- **Skull auto-rotation** — the hologram's head can spin continuously
- **Advanced skull effects** *(DISPLAY)* — cartoon squash & stretch and left/right tilt sway
- **Smooth POP animations** *(DISPLAY)* — scale-in on appear and scale-out on despawn
- **Billboard modes** *(DISPLAY)* — `CENTER`, `VERTICAL`, `HORIZONTAL`, `FIXED`
- **Global and per-line scale** *(DISPLAY)* — `display.scale` plus the inline `{line_scale:X}` placeholder
- **Glow with color** *(DISPLAY)* — configurable outline color
- **Text styling** *(DISPLAY)* — shadow, see-through and background style
- **Particles on appear/despawn** — work with both renderers
- **Custom skull** — Base64 texture with configurable height, scale and initial angle
- **Reward list with chances** — each reward has its own probability; use `pick` to control how many are rolled
- **Hex color support** — use `&#RRGGBB` alongside classic `&` color codes
- **Configurable click type** — `RIGHT`, `LEFT`, or `BOTH` per hologram
- **Click cooldown** — prevents reward spam
- **Persistent cooldowns** — intervals are respected across server restarts via `cooldowns.yml`
- **Chunk tickets** — keeps hologram chunks loaded to prevent ghost entities when players change worlds
- **World change cleanup** — holograms are removed immediately when a player changes worlds
- **No external dependencies** — only requires Paper 1.21.11
- **Auto cleanup** — removes orphaned entities on startup, shutdown, and when players disconnect
- **Admin commands** — spawn/remove, active hologram list with coordinates, teleport to hologram, and more

## 📦 Dependencies

| Dependency | Type | Link |
|---|---|---|
| Paper 1.21.11 | Required | [papermc.io](https://papermc.io) |

No additional plugins required.

## 🚀 Installation

1. Download or build the plugin (`mvn clean package`)
2. Place the `.jar` in your `/plugins/` folder
3. Start the server — `config.yml` will be generated automatically
4. Configure your holograms in `config.yml`
5. Use `/rh reload` to apply changes without restarting

## 🖼️ Renderers

| Renderer | Description |
|---|---|
| `ARMOR_STAND` | Classic armor stand holograms. Maximum compatibility (Java and Bedrock via Geyser). Ignores `display.*`, per-line scale, skull `scale`/`effects` and POP animations. |
| `DISPLAY` | TextDisplay/ItemDisplay entities (Minecraft 1.19.4+). Enables billboard, global and per-line scale, glow, text styling, skull squash/tilt and smooth POP appear/despawn. Bedrock support depends on your Geyser version. |

**Selection order:**

1. `settings.renderer` sets the default engine (`ARMOR_STAND` or `DISPLAY`).
2. Each hologram has its own `renderer` key:
   - `DEFAULT` — uses `settings.renderer`
   - `ARMOR_STAND` — forces armor stands for this hologram
   - `DISPLAY` — forces display entities for this hologram

If Bedrock compatibility matters, use `ARMOR_STAND`.

## ⚙️ Full Configuration

```yaml
settings:
  debug: false
  # Milliseconds between allowed clicks per player (prevents spam)
  click-cooldown-ms: 500
  # Default renderer for holograms with "renderer: DEFAULT"
  # ARMOR_STAND | DISPLAY
  renderer: DISPLAY

holograms:
  daily_reward:
    # DEFAULT | ARMOR_STAND | DISPLAY
    renderer: DEFAULT

    # Spawn probability (0.0 - 100.0). Evaluated once per second
    # after the interval has elapsed. 30.0 = 30% chance per second.
    chance: 30.0
    # Seconds between spawn evaluations per player. The hologram cannot
    # spawn again until this interval passes, even after being claimed.
    interval: 600
    # Seconds until it disappears if not claimed
    despawn-after: 20

    # Position offset relative to the player (z = in front of them)
    offset:
      x: 0.0
      y: 1.5
      z: 2.5

    # Up/down bobbing animation
    bobbing:
      enabled: true
      amplitude: 0.15   # blocks
      speed: 0.05       # higher = faster

    # Spacing between hologram lines
    line-spacing: 0.28

    # Click type to claim: RIGHT, LEFT, BOTH
    click-type: RIGHT

    # Lines — supports & codes and &#RRGGBB hex colors.
    # Optional per-line scale (DISPLAY only): "{line_scale:1.2}text"
    # Always wrap lines using the placeholder in quotes (YAML).
    lines:
      - "{line_scale:1.2}&#FFD700&l✦ DAILY REWARD ✦"
      - "&eClick to &aclaim"
      - "{line_scale:0.9}�BFFF&7Double XP for 10 minutes"
      - "&bJust for you!"

    # Reward list with individual chances
    rewards:
      # -1 = roll ALL rewards | N = pick N at random (capped at list size)
      pick: -1
      list:
        - command: "give %player% diamond 3"
          chance: 100.0   # always given
        - command: "effect give %player% speed 600 1"
          chance: 50.0
        - command: "give %player% emerald 1"
          chance: 25.0

    # Chat messages ("" to disable)
    appear-message: "&6&l[Reward] &eYou have a reward available! Look in front of you."
    claim-message: "&a&l[Reward] &fYou claimed your daily reward! &7Come back in 10 minutes."

    # Title shown when the hologram appears
    title:
      enabled: true
      title: "&6&l✦ REWARD ✦"
      subtitle: "&eA reward appeared in front of you"
      fade-in: 10    # ticks (20 ticks = 1 second)
      stay: 60
      fade-out: 20

    # Sound on appear
    sound:
      enabled: true
      name: "ENTITY_EXPERIENCE_ORB_PICKUP"
      volume: 1.0
      pitch: 1.2

    # ── DISPLAY-only options (ignored with ARMOR_STAND) ──
    display:
      # CENTER | VERTICAL | HORIZONTAL | FIXED
      billboard: CENTER
      # Global scale for every line and the skull (1.0 = normal)
      scale: 1.0
      glow:
        enabled: false
        color: YELLOW   # WHITE, RED, GOLD, YELLOW, GREEN, AQUA, BLUE, LIGHT_PURPLE, DARK_*, BLACK, GRAY...
      text:
        shadowed: true
        see-through: false        # visible through walls
        background: TRANSPARENT   # TRANSPARENT | DEFAULT

    # Effects when the hologram APPEARS
    appear:
      animation: POP          # NONE | POP (DISPLAY only)
      duration-ticks: 6
      particles:              # works with BOTH renderers
        enabled: true
        type: TOTEM_OF_UNDYING
        count: 25
        spread: 0.5
        speed: 0.1

    # Effects when the hologram DESPAWNS (timeout or claim)
    despawn:
      animation: POP          # NONE | POP (DISPLAY only)
      duration-ticks: 6
      particles:
        enabled: true
        type: CLOUD
        count: 15
        spread: 0.4
        speed: 0.05

    # Custom skull at the top
    skull:
      enabled: true
      texture: "eyJ0ZXh0dXJlcy..."   # Base64 from minecraft-heads.com
      head-yaw: 0.0                   # initial angle (0 = north, 90 = east, 180 = south, 270 = west)
      height-offset: 0.7              # extra height above the last line
      scale: 1.5                      # DISPLAY only (1.0 = item size)
      rotation:
        enabled: true
        speed: 2.0                    # degrees per tick
      # Advanced skull animations — DISPLAY only
      effects:
        squash:                       # cartoon squash & stretch
          enabled: true
          amplitude: 0.10             # 0.1 = subtle, 0.3 = very cartoonish
          speed: 0.15
        tilt:                         # left/right sway
          enabled: true
          amplitude: 8.0              # degrees each side
          speed: 0.06

    # Sound on claim
    claim-sound:
      enabled: true
      name: "ENTITY_PLAYER_LEVELUP"
      volume: 1.0
      pitch: 1.5
```

The default config also ships a second example, `bonus_reward`, using `click-type: BOTH`, `pick: 1`, glow enabled and `PORTAL` appear particles.

## 🔧 Commands

| Command | Permission | Description |
|---|---|---|
| `/rh reload` | `rewardhologram.admin` | Live reload of config.yml |
| `/rh list` | `rewardhologram.admin` | List defined holograms with chance and interval |
| `/rh active` | `rewardhologram.admin` | List all currently active holograms with owner and coordinates |
| `/rh tpto <player> <id>` | `rewardhologram.admin` | Teleport to an active hologram |
| `/rh spawn <id> <player>` | `rewardhologram.admin` | Force-spawn a hologram for a player |
| `/rh remove <id> <player>` | `rewardhologram.admin` | Remove a player's active hologram |
| `/rh removenear [radius]` | `rewardhologram.admin` | Remove all hologram entities within radius (default 5 blocks) |

All commands support **tab completion**. `/rh tpto` suggests only players with active holograms, then their active hologram IDs.

## 🔍 Debugging Stuck Holograms

If a hologram appears stuck (no animation, claimable infinitely):

1. Run `/rh active` to see all active holograms with coordinates
2. Run `/rh tpto <player> <id>` to teleport to it
3. Run `/rh removenear 5` to force-remove it from the world

These commands search the world directly by PDC key, so they catch orphaned entities that are no longer tracked by the plugin.

## 🎨 Color Support

```yaml
lines:
  - "&6Orange text"                   # Classic color code
  - "&#FF5733Hex red text"            # Hex color
  - "&l�FFFFCyan bold text"        # Combined
  - "{line_scale:1.3}&#FF00FF&lBig magenta"   # Per-line scale (DISPLAY only)
```

## 🔎 Per-line Scale

With the `DISPLAY` renderer, prefix a line with `{line_scale:X}` to resize it. Lines without the placeholder use scale `1.0`. It is ignored with `ARMOR_STAND`.

> ⚠️ Always put the line in quotes when using the placeholder — YAML treats a leading `{` as a mapping and would break the config.

## 🎬 Appear / Despawn Effects

- **Animation** (`DISPLAY` only): `NONE` shows/removes entities instantly; `POP` scales them up from ~0 on appear and down to ~0 on despawn, interpolated over `duration-ticks`.
- **Particles** (both renderers): spawned at the hologram's center. Use particle types that need no extra data — `DUST`, `ITEM`, `BLOCK`, `FALLING_*` and similar are skipped with a warning.
- While the despawn animation is playing, the hologram can no longer be claimed.

## 🏆 Reward System

```yaml
rewards:
  pick: 1       # Pick 1 reward at random from the list
  list:
    - command: "give %player% diamond 5"
      chance: 30.0
    - command: "give %player% gold_ingot 10"
      chance: 60.0
    - command: "give %player% iron_ingot 20"
      chance: 100.0
```

- `pick: -1` — every reward rolls its own chance independently.
- `pick: N` — N rewards are picked at random (capped at the list size) and each rolled by its `chance`.
- `%player%` is replaced with the claiming player's name.

## 📁 Generated Files

```
plugins/RewardHologram/
├── config.yml       # Hologram configuration
└── cooldowns.yml    # Per-player persistent cooldowns (auto-generated)
```

## 🏗️ Build

```bash
git clone https://github.com/yourusername/RewardHologram
cd RewardHologram
mvn clean package
# Output: target/RewardHologram-1.0.0.jar
```

## 📐 Architecture

```
Player joins        →  CooldownManager loads cooldowns.yml; initializes cooldowns for new players
Every second        →  HologramTask evaluates chance + interval per online player
Every tick          →  HologramTask ticks bobbing, skull rotation and (DISPLAY) squash/tilt
Hologram spawn      →  Renderer chosen (hologram → settings default) → appear animation + particles
Player clicks       →  HologramListener detects click → removeHologram first → executeCommands (pick + chance)
Hologram despawn    →  Despawn animation + particles (claim or timeout), then entities removed
Player quits        →  removeAllForPlayer() releases chunk tickets and removes entities
Player changes world→  PlayerChangedWorldEvent → removeAllForPlayer() cleans up before chunk unloads
Server shutdown     →  removeAll() releases all chunk tickets and removes all entities
Server startup      →  cleanOrphanedArmorStands() scans all worlds and removes leftover entities by PDC key
```

## 📝 Getting Skull Textures

Go to [minecraft-heads.com](https://minecraft-heads.com), find the head you want and copy the **"Value"** field into `skull.texture`.