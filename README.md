# Tank Duel

A lightweight, dependency-free artillery duel game that runs entirely in the
browser — no build step, no server, no external libraries.

## How to play

Open `index.html` in any modern browser (or serve the folder with any static
file server), then pick **2 Player (Hotseat)**, **Vs Computer**, or **Rescue
Mission (Solo)** — a no-opponent mode where a squad shelters inside a
bunker; blast open the sandbag walls and concrete roof surrounding them
so an evac chopper can fly in and save them. The room genuinely protects
them — shots outside it, even right up against a wall, can't reach the
squad — but a shot that actually lands inside the room (not just the
last block falling, but the blast itself getting past the walls) can
hurt them, so don't get sloppy once you're punching through the roof
right over their heads.

```
python3 -m http.server 8000
# then visit http://localhost:8000
```

Pick a weapon from the bar above the battlefield, then drag from your tank's
turret in the direction (and distance) you want to fire — farther drags mean
more power — and release to launch. Watch the wind arrow: it pushes shells
sideways every turn. Destroy the other tank before it destroys you.

## Weapons

- **Shell** — unlimited, balanced damage and blast radius.
- **Heavy Shell** — slower but hits much harder with a bigger blast (3 per
  battle).
- **Rocket** — fast and flat-flying, hits noticeably harder than the base
  Shell, good for direct hits (3 per battle).
- **Cluster Bomb** — splits into 5 bomblets at the top of its arc, showering
  a wide area (2 per battle).

## What's implemented

- Custom 2D physics (gravity, wind drift, projectile motion) written from
  scratch — no game engine dependency.
- Procedurally generated, fully destructible terrain: explosions carve
  permanent craters, and tanks settle onto the new surface.
- A crate pyramid guarding each tank in PvP, and a fully enclosed sandbag
  bunker (thick walls + a spanning concrete roof) around the trapped squad
  in Rescue Mission mode, all built on the same lightweight rigid-body-ish
  block physics: gravity, rotation, impact-driven toppling torque, and a
  connected-component sleep/wake check -- a roof block with nothing directly
  under it stays put as long as it's still structurally connected to the
  ground through the walls, so breaching one wall brings down a real
  domino-style collapse instead of the whole structure floating in place
  (a falling block can even crush a tank beneath it).
- Drag-to-aim controls (mouse or touch via Pointer Events) with a live
  trajectory preview and power meter.
- Four weapons with distinct speed, blast radius, and damage, including a
  cluster bomb that splits mid-flight into independent bomblets.
- Blast damage with distance falloff, per-tank ammo tracking, and a wins
  tally across rematches.
- Hotseat 2-player mode and a single-player mode with a simple AI opponent
  that searches candidate angles/power to aim at the player, then fires with
  some human-like inaccuracy.
- A third, opponent-free Rescue Mission mode: blow open the bunker
  enclosing a pinned-down squad, then watch a scripted evac chopper fly
  in, land, extract the troops, and fly back off before the mission is
  scored on shots fired. The chopper only needs a clear shaft of air
  directly above the squad to land -- leftover wall rubble elsewhere
  doesn't hold up the rescue. The room is a genuine shield: an explosion
  outside it does nothing to the squad no matter how close, even after
  every wall is gone, but a shot that actually detonates inside the
  room's air pocket can hurt them, so the last hit or two right over
  their heads is where care matters most.

## Files

- `index.html` — page shell, HUD, weapon bar, and mode-select/win overlays.
- `css/style.css` — layout and styling.
- `js/game.js` — the entire game: terrain, physics, weapons, AI, input, and
  rendering on an HTML5 canvas.
