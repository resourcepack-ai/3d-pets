# 3D Pets

A real 3D Labrador for Minecraft servers, not one made of blocks: it trots
after you, sits when you tell it to and plays fetch with a tennis ball.

It is an addon for [3D Engine](https://github.com/resourcepack-ai/3d-engine),
which draws, animates and walks the dog, and it uses nothing but the engine's
public API (`ai.resourcepack.engine3d.api`), so it doubles as a worked example
of an addon. Players need nothing installed.

## What it does

- **Follows you**: it walks to keep up, breaks into a run when you get far
  ahead, and catches up if you leave it behind.
- **Sits**: right-click it to sit, again to get up. Left alone it sometimes sits
  down by itself, and gets up when you move off.
- **Plays fetch**: `/pet ball` gives you a tennis ball. Throw it and the dog runs
  out, picks it up in its mouth, brings it back and drops it at your feet.
- **Potters about**: stand still and it wanders, sniffs the ground and now and
  then dashes off and back.
- **Looks at you**: when it is sitting or standing about, it turns its head to
  whoever is near.

## Requirements

- **Spigot or Paper on Minecraft 26.3.**
- **[3D Engine](https://github.com/resourcepack-ai/3d-engine) 0.1.1 or newer**,
  set up so that players get its resource pack.

## Installing

1. Install 3D Engine if you have not already.
2. Put `3DPets.jar` in `plugins/` beside `3DEngine.jar` and start the server.
3. The dog's model installs itself into `plugins/3DEngine/models/3dpets/`, and
   3D Engine bakes it into its pack.

## Commands and permissions

| Command | Does |
|---|---|
| `/pet` | Calls your dog, or sends it home |
| `/pet sit` | Sit or stand (or right-click the dog) |
| `/pet ball` | A tennis ball to throw |
| `/pet debug` | Op: logs every step of every fetch |
| `/pet throw [blocks]` | Op: lands a ball that far ahead and sends your dog for it |

From the console, `/pet <player> [subcommand]` acts for that player.

Every player can have one dog. It goes home when its owner leaves.

| Permission | Default | |
|---|---|---|
| `3dpets.use` | everyone | `/pet`, `/pet sit`, `/pet ball` |
| `3dpets.admin` | op | `/pet debug` and `/pet throw` |
| `3dpets.update` | op | Told when a newer version is out |

## Configuration

`plugins/3DPets/config.yml` has one switch. `update-check: true` asks SpigotMC
every six hours whether a newer 3D Pets is out, and if so tells the console and
anybody with `3dpets.update`. Set it to `false` to turn that off.

## Building

```
./gradlew build        # build/libs/3DPets-<version>.jar
```

Gradle 8.10.2 needs a JDK it can run on: 17 or 21 work, 24 does not. If your
default `java` is newer, `JAVA_HOME=/path/to/jdk-21 ./gradlew build`. The jar
itself targets Java 21 through a toolchain Gradle downloads on its own.

3D Engine is compile-only (the server already has it). The build uses, in order:

1. `ENGINE_JAR`, if set: the path to any 3D Engine jar.
2. An engine built beside this project, in `../3d-engine/engine/build/libs`
   (`-PengineDir=<dir>` looks somewhere else).
3. Otherwise 3D Engine 0.1.1, the oldest version 3D Pets runs on, downloaded
   once from `https://cdn.resourcepack.ai/3DEngine-0.1.1.jar` into
   `.gradle/3d-engine/` and checked against its SHA-256. Versioned engine jars
   there are never rewritten.

`ai.resourcepack:common`, a small shared library (the update check), comes from
`https://repo.resourcepack.ai`, and is shaded into the jar and relocated.

## Support

Questions, bugs and suggestions: [resourcepack.ai/discord](https://resourcepack.ai/discord),
or an issue on this repository.

## Licence

The code is GPL-3.0-or-later. See [`LICENSE`](LICENSE).

**The Labrador's model files are not GPL**: they are under
[CC BY 4.0](http://creativecommons.org/licenses/by/4.0/), as the work they are
made from is (see Credits). That is `src/main/resources/models/labrador.glb`;
`labrador-motion.glb`, the animations, is CC0. The tennis ball is ours, under the
GPL with the code.

## Credits

The Labrador is based on
["Labrador Dog"](https://sketchfab.com/3d-models/labrador-dog-1f56cfbab07e4fe49b5d9e521c82073a)
by [kenchoo](https://sketchfab.com/kenchoo), itself based on
["Dog"](https://skfb.ly/oHLVz) by all of life, both licensed under
[Creative Commons Attribution 4.0](http://creativecommons.org/licenses/by/4.0/).
We reduced it, retextured it smaller and re-animated it. Its animations are
Quaternius' "Shiba Inu" (CC0).

The same credits ship in the jar as `CREDITS.txt`, and are copied beside the
model into `plugins/3DEngine/models/3dpets/`.
