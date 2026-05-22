# Tyr's Fluid Void Stuff

This mod is all about two things:
1. Fade out fluids that do not end on a block so that they look pretty in void environments (Clientside, does not require mod on server)
2. Limit how far a liquid can fall from its source block to prevent huge waterfalls (Serverside, does not require mod on client) 

The client renderer is ported from [FluidVoidFading](https://modrinth.com/mod/fluid-void-fading) (Neoforge 1.21.8) by DaFuqs under the GNU General Public License.

---

## What it does

**On the client**, the bottom of any fluid column fades out smoothly rather than ending with a hard edge. Works on water, lava, and modded fluids. Sodium is supported.

**On the server**, you can set a maximum vertical drop for fluid columns. Once a column hits the limit, it stops spreading downward and leaves air below. The limit is measured as straight vertical distance from the source, so it works the same whether the fluid falls straight down or zig-zags down i.e. a staircase. Individual fluids can be exempted from this via a blacklist.

Neither side requires the other. See below.

---

## The mod is optional on both ends of a connection (Server or Client)

| Setup           | What happens                                                                                                                                              |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Client + Server | Full functionality with the fade renderer on the client and the column cap on the server.                                                                 |
| Client only     | Fade renderer works for columns that end in air naturally (e.g. in the void). No other functionality.                                                     |
| Server only     | Vertical fluid limits only. Players without the mod just see normal fluid rendering, the liquid still stops, it just won't show the fade-out thingamabob. |

---

## Config

Config file: `config/tyrsfluidvoidstuff-server.toml`.

| Option | Default | What it does |
|---|---|---|
| `maxFluidColumnLength` | `0` | Max vertical drop in blocks from the source. `0` = disabled. |
| `fluidColumnLengthBlacklist` | *(empty)* | Fluid IDs to skip the cap for, e.g. `minecraft:water`. |

If you have the mod on the client, the config is also reachable from the Mods screen in-game.

---

## Credits

- **Tyrthurey** — column cap, NeoForge port, glue code
- **DaFuqs** — original fade renderer ([Modrinth](https://modrinth.com/mod/fluidvoidfading)) ([Github](https://github.com/DaFuqs/FluidVoidFading))
