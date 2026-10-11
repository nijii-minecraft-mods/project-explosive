# Project: Explosive (Fabric 1.21.1 + Polymer Port)

A complete server-side port of the Minecraft mod **Project: Explosive** to **Fabric 1.21.1** powered by **Polymer**.

Vanilla Minecraft clients can connect and experience realistic explosions, custom bomb models, sound delays, radiation sickness, and 3D tumbling physics debris without installing any client-side mods!

---

## 🚀 Key Features

### 1. Nuclear & Ballistic Weaponry
- **Nuclear Bomb**:
  - Blinding flash and acoustic ring effects.
  - Hemispherical crater excavation (`CraterUtil`) with ejecta block clusters.
  - True 3D mushroom cloud simulation (vertical rising stem, torus cap dome, and fireball core) rendered with high-visibility vanilla particles.
  - BFS structural integrity scan (`BlastCrumble`) causing unsupported buildings to collapse into physics rubble.
  - Delayed speed-of-sound shockwave audio propagation ($t = \text{distance} / 343\text{ m/s}$).
  - Long-term radiation fallout zones with ash deposition and terrain scorching (`EnvironmentalTransformer`).
- **ICBM (Intercontinental Ballistic Missile)**:
  - 4-phase flight model: `BOOST` (vertical ascent), `SEPARATION` (tumbling booster stage release), `CRUISE` (high-altitude trajectory), and `DIVE` (supersonic terminal plunge).
  - Force-loaded chunk ticketing along the missile corridor so trajectories never freeze in unloaded terrain.
- **Conventional Bombs**:
  - **Big Bomb (HE)**: Deep excavation crater and massive structural collapse.
  - **Small Bomb**: Compact tactical blast.
  - **Fire Bomb**: Incendiary napalm blast triggering dynamic wildfire simulation (`FirePropagationManager`).

### 2. Physical Structural Collapse & Debris
- **BFS Structural Graph Analysis**: Scans building support connections to ground bedrock. Detached sections collapse into falling blocks or tumbling debris clusters (`MultiBlockEntity`).
- **MultiBlock Physics**: Physical 3D block clusters with tumbling angular velocity, bounce physics, impact dust, and landing restitution.

### 3. Radiation & Environmental Systems
- **Radiation Fallout**: Persistent dimension-saved radiation fields decaying over time.
- **Geiger Counter**: Detects local radiation, ticks audibly with increasing tempo, and displays dose rate on the player's action bar.
- **Acute Radiation Sickness (ARS)**: Sickness progression inflicting nausea, hunger, weakness, slowness, poison, and blindness.

### 4. Arsenal & Equipment
- **Airstrike Caller**: Laser target designator summoning high-altitude gravity bombs.
- **Flamethrower**: Pressurized jet of flame and embers igniting targets and foliage.
- **Chemical Extinguisher**: Suppresses fires, cools lava into obsidian, and washes away radioactive ash.
- **Custom Blocks**: Radioactive Ash (multi-layer fallout), Rubble Debris, Dead Oak Logs & Planks, Dead Dirt.

---

## 🛠️ Polymer Server-Side Architecture

This port leverages [Polymer](https://github.com/Patbox/Polymer) to achieve full compatibility with unmodified vanilla clients:
- **Items & Weapons**: Registered with `PolymerItem`. Vanilla clients receive familiar base items with server-synchronized custom models, textures, and sounds via auto-generated server resource packs.
- **Entities**: Missiles, bombs, and tumbling blocks implement `PolymerEntity` with automatic client-side representation.
- **Zero Client Mods**: Works on standard Fabric / Paper-proxy servers without requiring Fabric Loader on joining players.

---

## ⚙️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/custombomb <scale: 1-10> [x y z]` | Op (Level 2) | Spawns a scaled bomb (1-4: Small, 5-7: Big, 8-10: Nuclear) at the specified coordinates. |
| `/radiation` | All | Displays the player's current accumulated radiation dose in Sieverts (Sv). |
| `/radiation clear` | Op (Level 2) | Clears all active radiation sources and resets player exposure. |
| `/radiation sources` | Op (Level 2) | Displays the count of active radiation sources in the world. |
| `/ignitefire [x y z]` | Op (Level 2) | Starts the dynamic wildfire propagation system at the target location. |
| `/stopfire [x y z] [radius]` | Op (Level 2) | Extinguishes active fire simulation cells globally or within a radius. |
| `/testmultiblock <x y z> <maxBlocks>` | Op (Level 2) | Extracts a connected block cluster into a 3D tumbling physics debris entity. |

---

## 📦 Building from Source

### Prerequisites
- JDK 21 or higher
- Git

### Build Command
```bash
./gradlew build
```

The compiled mod JAR will be located in:
```
build/libs/project-explosive-1.0.0.jar
```

---

## 🤖 CI / CD Pipeline

This repository includes automated GitHub Actions workflows:
- **`.github/workflows/build.yml`**: Runs `./gradlew build` on every push or pull request to `main`/`master`, ensuring compile safety and saving JAR build artifacts.
- **`.github/workflows/release.yml`**: Automatically builds and publishes a GitHub Release with attached JAR artifacts whenever a version tag (`v*`) is pushed.

---

## 📜 License & Credits

- Original mod concept & assets by the *Project: Explosive* development team.
- Fabric & Polymer server-side port created with automated agentic engineering.
