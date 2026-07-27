# Tank Duel

A lightweight, dependency-free artillery duel game that runs entirely in the
browser — no build step, no server, no external libraries.

## How to play

Open `index.html` in any modern browser (or serve the folder with any static
file server), then pick **2 Player (Hotseat)**, **Vs Computer**, or **Rescue
Mission (Solo)** — a no-opponent mode where you blast apart the debris
blocking the LZ so an evac chopper can fly in and save the trapped squad.

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
- A crate pyramid guarding each tank (and two much taller towers in
  Demolition mode), with lightweight rigid-body-ish block physics: gravity,
  rotation, impact-driven toppling torque, sleep/wake, and collision against
  terrain, other blocks, and tanks, so knocking out a support block can
  bring down a real domino-style cascade instead of the structure just
  vanishing (a falling block can even crush a tank beneath it).
- Drag-to-aim controls (mouse or touch via Pointer Events) with a live
  trajectory preview and power meter.
- Four weapons with distinct speed, blast radius, and damage, including a
  cluster bomb that splits mid-flight into independent bomblets.
- Blast damage with distance falloff, per-tank ammo tracking, and a wins
  tally across rematches.
- Hotseat 2-player mode and a single-player mode with a simple AI opponent
  that searches candidate angles/power to aim at the player, then fires with
  some human-like inaccuracy.
- A third, opponent-free Rescue Mission mode: clear the debris blocking a
  pinned-down squad, then watch a scripted evac chopper fly in, land,
  extract the troops, and fly back off before the mission is scored on
  shots fired.

## Files

- `index.html` — page shell, HUD, weapon bar, and mode-select/win overlays.
- `css/style.css` — layout and styling.
- `js/game.js` — the entire game: terrain, physics, weapons, AI, input, and
  rendering on an HTML5 canvas.
