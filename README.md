**Bug Reports and Feature Requests:** https://github.com/Artillex-Studios/Issues

**Support:** https://dc.artillex-studios.com/

![axgraves-banner](https://github.com/Artillex-Studios/AxGraves/assets/52270269/771b2e74-58e4-4128-b822-099ec20802b0)

---

## 🚀 Improvements by dei0

**VERSION:** v1.0.0 - dei0  
**IMPROVED BY:** dei0 (dei2004)  
**GITHUB:** https://github.com/dei2004

### ✨ New Features Added:

#### 1. **Safe Grave Spawning System** 🛡️
- Graves now intelligently spawn in safe locations when players die in dangerous areas
- **Dangerous areas detected:**
  - Lava and magma blocks
  - Fire and soul fire
  - Void (prevents graves spawning mid-air in The End)
  - Cactus, berry bushes, and wither roses
  - Deep void (below world minimum height)
- **Smart search algorithm:**
  - Searches up to 100 blocks horizontally
  - Searches up to 100 blocks vertically
  - Prioritizes same Y-level, then upward, then downward
  - Emergency surface and ground finder as fallback
- **Player notification:** Displays message with coordinates when grave is relocated
- **Configuration:** Fully configurable via `safe-grave-spawn.enabled` in config.yml

#### 2. **Teleport Countdown System** ⏱️
- Added countdown delay before teleporting to graves
- **Features:**
  - Configurable delay (default: 5 seconds)
  - Displays countdown as large title on screen
  - Cancels if player moves during countdown
  - Cooldown between teleport uses
  - Permission bypass: `axgraves.tp.bypass.cooldown` for instant teleport
- **Configuration:**
  - `teleport-cooldown: 5` - delay in seconds
  - `teleport-countdown-display` - customizable countdown message with hex color support
- **Messages:**
  - Countdown display: "✈ Teleporting in Xs"
  - Movement cancellation: "✘ Cancelled - You moved!"
  - Cooldown message when trying to teleport too soon

#### 3. **Auto-Equip Armor on Grave Pickup** 🛡️
- Armor automatically equips to empty slots when shift-right-clicking graves
- **Features:**
  - Detects all armor types (helmet, chestplate, leggings, boots)
  - Works with all armor materials (leather, iron, diamond, netherite, etc.)
  - Special support for turtle helmets and elytra
  - Only equips to empty slots (won't replace existing armor)
  - Single-pass processing prevents item duplication
- **Configuration:** `auto-equip-armor: true` in config.yml

#### 4. **Improved Command System** 🎮
- Removed world autocomplete from `/axgraves tp` command for cleaner interface
- Command now shows no suggestions for world parameter

### 🔧 Technical Improvements:
- Enhanced void detection with 10-block solid ground validation
- Dimension-aware search distances (special handling for The End)
- Optimized item processing to prevent duplication bugs
- Material name pattern matching for reliable armor detection
- Movement detection for teleport cancellation
- Countdown task management with proper cleanup

### 📝 Configuration Options:
```yaml
# Safe grave spawning
safe-grave-spawn:
  enabled: true

# Teleport countdown (in seconds)
teleport-cooldown: 5

# Countdown display message
teleport-countdown-display: "&#00FF00✈ Teleporting in &#FFFFFF%time%s"

# Auto-equip armor on shift-right-click
auto-equip-armor: true
```

### 🎯 Permissions:
- `axgraves.tp.bypass.cooldown` - Bypass teleport countdown (instant teleport)

---
