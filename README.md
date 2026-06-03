# RewardHologram 🎁

A Paper 1.21.11 plugin that displays per-player floating reward holograms with bobbing animation, skull rotation, individual reward chances, and cooldowns that persist across server restarts.

## ✨ Features

- **Per-player** — each player sees their own hologram, protected from other players
- **Bobbing animation** — smooth up/down movement with configurable amplitude and speed
- **Skull auto-rotation** — the hologram's head can spin continuously
- **Custom skull** — Base64 texture with configurable height and initial angle
- **Reward list with chances** — each reward has its own probability; use `pick` to control how many are given
- **Hex color support** — use `&#RRGGBB` alongside classic `&` color codes
- **Configurable click type** — `RIGHT`, `LEFT`, or `BOTH` per hologram
- **Click cooldown** — prevents reward spam
- **Persistent cooldowns** — intervals are respected across server restarts via `cooldowns.yml`
- **No external dependencies** — only requires Paper 1.21.11
- **Auto cleanup** — removes orphaned armor stands on startup and when players disconnect
- **Admin commands** — manual spawn/remove, live reload and list

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

## ⚙️ Full Configuration

```yaml
settings:
  debug: false
  # Milliseconds between allowed clicks per player (prevents spam)
  click-cooldown-ms: 500

holograms:
  my_reward:
    # Spawn probability (0.0 - 100.0)
    chance: 25.0
    # Seconds between evaluations per player
    interval: 600
    # Seconds until it disappears if not claimed
    despawn-after: 20

    # Position offset relative to the player (in front of them)
    offset:
      x: 0.0
      y: 1.5
      z: 2.5

    # Up/down bobbing animation
    bobbing:
      enabled: true
      amplitude: 0.15   # how much it moves in blocks
      speed: 0.05       # higher = faster

    # Spacing between hologram lines
    line-spacing: 0.28

    # Click type to claim: RIGHT, LEFT, BOTH
    click-type: RIGHT

    # Lines — supports & codes and &#RRGGBB hex colors
    lines:
      - "&#FFD700&l✦ REWARD ✦"
      - "&eClick to claim"
      - "&#00BFFFHex blue text"

    # Reward list with individual chances
    rewards:
      # -1 = evaluate all | 1+ = pick N at random and evaluate them
      pick: -1
      list:
        - command: "give %player% diamond 1"
          chance: 100.0   # always given
        - command: "give %player% emerald 1"
          chance: 50.0    # 50% probability
        - command: "give %player% gold_ingot 3"
          chance: 25.0    # 25% probability

    # Chat message on appear ("" to disable)
    appear-message: "&6You have a reward available!"

    # Chat message on claim
    claim-message: "&aYou claimed your reward!"

    # Title shown when the hologram appears
    title:
      enabled: true
      title: "&6&l✦ REWARD ✦"
      subtitle: "&eClick to claim"
      fade-in: 10    # ticks (20 ticks = 1 second)
      stay: 60
      fade-out: 20

    # Sound on appear
    sound:
      enabled: true
      name: "ENTITY_EXPERIENCE_ORB_PICKUP"
      volume: 1.0
      pitch: 1.2

    # Sound on claim
    claim-sound:
      enabled: true
      name: "ENTITY_PLAYER_LEVELUP"
      volume: 1.0
      pitch: 1.5

    # Custom skull at the top
    skull:
      enabled: true
      texture: "eyJ0ZXh0dXJlcy..."   # Base64 from minecraft-heads.com
      head-yaw: 0.0                   # initial angle (0-360)
      height-offset: 0.7              # extra height above the lines
      rotation:
        enabled: true
        speed: 2.0                    # degrees per tick
```

## 🔧 Commands

| Command | Permission | Description |
|---|---|---|
| `/rh reload` | `rewardhologram.admin` | Live reload of config.yml |
| `/rh list` | `rewardhologram.admin` | List holograms with chance and interval |
| `/rh spawn <id> <player>` | `rewardhologram.admin` | Force-spawn a hologram for a player |
| `/rh remove <id> <player>` | `rewardhologram.admin` | Remove a player's active hologram |

## 🎨 Color Support

```yaml
lines:
  - "&6Orange text"             # Classic color code
  - "&#FF5733Hex red text"      # Hex color
  - "&l&#00FFFFCyan bold text"  # Combined
```

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

With `pick: 1`, one reward is chosen at random and its `chance` is evaluated. With `pick: -1`, all rewards are evaluated independently.

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
Player joins   →  CooldownManager loads cooldowns.yml
Every second   →  HologramTask evaluates chance + interval per player
Player clicks  →  HologramListener detects click → executeCommands → pick + chance
Player quits   →  removeAllForPlayer() removes entities from the world
Server restart →  cleanOrphanedArmorStands() in onEnable() cleans up leftovers
```

## 📝 Getting Skull Textures

Go to [minecraft-heads.com](https://minecraft-heads.com), find the head you want and copy the **"Value"** field into `skull.texture`.
