(() => {
  "use strict";

  // ---------------------------------------------------------------------
  // Setup
  // ---------------------------------------------------------------------
  const canvas = document.getElementById("game");
  const ctx = canvas.getContext("2d");
  const W = canvas.width;
  const H = canvas.height;

  const GRAVITY = 0.22;
  const SUBSTEPS = 4;
  const MAX_DRAG = 150;
  const POWER_SCALE = 0.1;
  const TANK_W = 44;
  const TANK_H = 20;
  const BARREL_LEN = 32;

  const BLOCK_W = 26;
  const BLOCK_H = 20;
  const BLOCK_HP = 28;
  const BLOCK_GRAVITY = GRAVITY * 1.2;
  const BLOCK_RESTITUTION = 0.3;
  const BLOCK_SLEEP_SPEED = 0.4;
  const BLOCK_SLEEP_FRAMES = 18;
  const BLOCK_BLAST_DAMAGE_MULT = 1.8;
  const BLOCK_BLAST_REACH_BONUS = 15;
  const TROOPS_X = W - 130;
  const BUNKER_HALF_WIDTH = 40;
  // The room's interior stays a fixed size, but the wall/roof blueprint
  // built around it varies by mission -- these track whichever blueprint
  // generateBunker() picked last, so the shielding/frame code always
  // matches what's actually standing.
  let bunkerWallHeight = 4;
  let bunkerWallThickness = 2;

  const hud = document.getElementById("hud");
  const turnIndicatorEl = document.getElementById("turn-indicator");
  const windIndicatorEl = document.getElementById("wind-indicator");
  const winsIndicatorEl = document.getElementById("wins-indicator");
  const blocksIndicatorEl = document.getElementById("blocks-indicator");
  const shotsIndicatorEl = document.getElementById("shots-indicator");
  const statusIndicatorEl = document.getElementById("status-indicator");
  const restartBtn = document.getElementById("restart-btn");
  const modeSelect = document.getElementById("mode-select");
  const overlay = document.getElementById("overlay");
  const overlayTitle = document.getElementById("overlay-title");
  const overlayMsg = document.getElementById("overlay-msg");
  const overlayBtn = document.getElementById("overlay-btn");
  const weaponButtons = Array.from(document.querySelectorAll(".weapon-btn"));

  // ---------------------------------------------------------------------
  // Weapons
  // ---------------------------------------------------------------------
  const WEAPONS = {
    standard: {
      name: "Shell",
      infinite: true,
      speedMult: 1.0,
      blastRadius: 36,
      damage: 32,
      color: "#333d4d",
      trailColor: "#333d4d",
    },
    heavy: {
      name: "Heavy Shell",
      infinite: false,
      speedMult: 0.82,
      blastRadius: 55,
      damage: 52,
      color: "#7a3b1e",
      trailColor: "#7a3b1e",
    },
    rocket: {
      name: "Rocket",
      infinite: false,
      speedMult: 1.32,
      blastRadius: 30,
      damage: 46,
      color: "#e63946",
      trailColor: "#f4a261",
    },
    cluster: {
      name: "Cluster Bomb",
      infinite: false,
      speedMult: 1.0,
      blastRadius: 24,
      damage: 20,
      color: "#ffb703",
      trailColor: "#ffb703",
      cluster: true,
      clusterCount: 5,
    },
  };
  const STARTING_AMMO = { heavy: 3, rocket: 3, cluster: 2 };

  // ---------------------------------------------------------------------
  // Game state
  // ---------------------------------------------------------------------
  let mode = null; // '2p' | 'ai'
  let terrain = [];
  let mountains = [];
  let clouds = [];
  let rocks = [];
  let birds = [];
  let strata = { p1: 0, p2: 0, p3: 0 };
  let grassTufts = [];
  let blocks = [];
  let tanks = []; // [player1, player2]
  let currentTurn = 0; // index into tanks
  let wind = 0;
  let selectedWeapon = "standard";
  let projectiles = [];
  let particles = [];
  let dragging = false;
  let dragStart = { x: 0, y: 0 };
  let dragPos = { x: 0, y: 0 };
  let turnState = "aiming"; // aiming | resolving | over
  let wins = [0, 0];
  let aiTimer = 0;
  let roundOver = false;
  let shotsFired = 0;
  let troops = [];
  let heli = null;
  let shakeTime = 0;
  let shakeMag = 0;

  function rand(min, max) {
    return min + Math.random() * (max - min);
  }

  function shadeColor(hex, percent) {
    const n = parseInt(hex.slice(1), 16);
    const amt = Math.round(2.55 * percent);
    const r = Math.max(0, Math.min(255, (n >> 16) + amt));
    const g = Math.max(0, Math.min(255, ((n >> 8) & 0xff) + amt));
    const b = Math.max(0, Math.min(255, (n & 0xff) + amt));
    return `rgb(${r},${g},${b})`;
  }

  // ---------------------------------------------------------------------
  // Terrain generation: midpoint displacement (1D fractal), the classic
  // technique for natural, non-repeating hill lines with detail at every
  // scale -- unlike stacked sine waves, it doesn't look like a wave.
  // ---------------------------------------------------------------------
  function generateFractalHeights(baseY, initialRange, persistence, minY, maxY) {
    const size = 256;
    const arr = new Array(size + 1);
    arr[0] = baseY + rand(-initialRange, initialRange);
    arr[size] = baseY + rand(-initialRange, initialRange);
    let range = initialRange;
    let step = size;
    while (step > 1) {
      const half = step / 2;
      for (let i = half; i < size; i += step) {
        const avg = (arr[i - half] + arr[i + half]) / 2;
        arr[i] = avg + rand(-range, range);
      }
      range *= persistence;
      step = half;
    }
    const out = new Array(W + 1);
    for (let x = 0; x <= W; x++) {
      const pos = (x / W) * size;
      const i0 = Math.floor(pos);
      const i1 = Math.min(size, i0 + 1);
      const frac = pos - i0;
      const y = arr[i0] * (1 - frac) + arr[i1] * frac;
      out[x] = Math.max(minY, Math.min(maxY, y));
    }
    return out;
  }

  function generateTerrain() {
    return generateFractalHeights(H * 0.6, 75, 0.54, 140, H - 55);
  }

  // A second, hazier heightmap sitting above and behind the real terrain,
  // purely decorative, to give the horizon some parallax depth.
  function generateMountains() {
    return generateFractalHeights(H * 0.4, 50, 0.6, 40, H * 0.62);
  }

  function generateStrata() {
    return { p1: rand(0, Math.PI * 2), p2: rand(0, Math.PI * 2), p3: rand(0, Math.PI * 2) };
  }

  function generateGrassTufts() {
    const tufts = [];
    for (let x = 3; x <= W; x += rand(5, 9)) {
      tufts.push({ x, h: rand(5, 12), lean: rand(-3, 3), shade: rand(-12, 14) });
    }
    return tufts;
  }

  function generateClouds() {
    const clouds = [];
    const count = Math.round(rand(3, 6));
    for (let i = 0; i < count; i++) {
      clouds.push({
        x: rand(0, W),
        y: rand(40, 150),
        scale: rand(0.6, 1.3),
        opacity: rand(0.35, 0.65),
      });
    }
    return clouds;
  }

  function generateBirds() {
    const birds = [];
    const count = Math.round(rand(0, 3));
    for (let i = 0; i < count; i++) {
      birds.push({ x: rand(0, W), y: rand(90, 220) });
    }
    return birds;
  }

  // The pinned-down squad waiting at the LZ in Rescue Mission mode.
  function generateTroops() {
    return [
      { x: TROOPS_X - 12, rescued: false, alive: true },
      { x: TROOPS_X, rescued: false, alive: true },
      { x: TROOPS_X + 12, rescued: false, alive: true },
    ];
  }

  function generateRocks() {
    const rocks = [];
    for (let i = 0; i < 70; i++) {
      rocks.push({ x: rand(0, W), depth: rand(6, 260), r: rand(1.5, 4), shade: rand(-14, 10) });
    }
    for (let i = 0; i < 7; i++) {
      rocks.push({ x: rand(0, W), depth: rand(40, 260), r: rand(7, 13), shade: rand(-10, 8), boulder: true });
    }
    return rocks;
  }

  function terrainAt(x) {
    const xi = Math.max(0, Math.min(W, Math.round(x)));
    return terrain[xi];
  }

  // Crater rims get a torn, irregular edge (two overlaid sine terms with a
  // per-explosion seed) instead of a perfect circular arc.
  function carveCrater(cx, cy, radius) {
    const r = radius;
    const from = Math.max(0, Math.floor(cx - r * 1.15));
    const to = Math.min(W, Math.ceil(cx + r * 1.15));
    const seed = rand(0, Math.PI * 2);
    for (let x = from; x <= to; x++) {
      const dx = x - cx;
      const jag = 1 + 0.12 * Math.sin(x * 0.35 + seed) + 0.06 * Math.sin(x * 0.9 + seed * 2);
      const rr = r * jag;
      const inside = rr * rr - dx * dx;
      if (inside <= 0) continue;
      const depth = cy + Math.sqrt(inside);
      if (depth > terrain[x]) terrain[x] = Math.min(H - 6, depth);
    }
  }

  // ---------------------------------------------------------------------
  // Tanks
  // ---------------------------------------------------------------------
  function makeTank(side, label, color, isAI) {
    const x = side === "left" ? rand(60, 120) : rand(W - 120, W - 60);
    const camo = [];
    for (let i = 0; i < 4; i++) {
      camo.push({
        dx: rand(-TANK_W * 0.4, TANK_W * 0.4),
        dy: rand(-TANK_H * 0.75, -TANK_H * 0.1),
        rx: rand(6, 11),
        ry: rand(3.5, 6),
        rot: rand(0, Math.PI),
        shade: rand(-26, -8),
      });
    }
    return {
      side,
      label,
      color,
      isAI,
      x,
      y: terrainAt(x),
      hp: 100,
      angle: 45, // degrees of elevation, 0 = flat toward enemy, 90 = straight up
      power: 60, // percent
      ammo: { ...STARTING_AMMO },
      alive: true,
      camo,
    };
  }

  function settleTankToTerrain(tank) {
    tank.y = terrainAt(tank.x);
  }

  function facingSign(tank) {
    return tank.side === "left" ? 1 : -1;
  }

  // ---------------------------------------------------------------------
  // Blocks: destructible crate structures with lightweight rigid-body-ish
  // physics -- gravity, rotation, collision with terrain/other blocks/tanks,
  // and a sleep/wake cycle so settled stacks stop needing simulation.
  // ---------------------------------------------------------------------
  function makeBlock(x, y, material = "crate") {
    return {
      x,
      y,
      w: BLOCK_W,
      h: BLOCK_H,
      angle: 0,
      vx: 0,
      vy: 0,
      av: 0,
      hp: BLOCK_HP,
      maxHp: BLOCK_HP,
      awake: false,
      settleTimer: 0,
      material,
    };
  }

  // A pyramid of crates resting on the terrain at centerX, row sizes from
  // bottom to top given by `rows` (defaults to a small 3-2-1 stack).
  function makeBlockStack(centerX, rows = [3, 2, 1]) {
    const stack = [];
    let rowBottom = terrainAt(centerX);
    for (const count of rows) {
      const rowCenterY = rowBottom - BLOCK_H / 2;
      const totalW = count * BLOCK_W;
      const startX = centerX - totalW / 2 + BLOCK_W / 2;
      for (let i = 0; i < count; i++) {
        stack.push(makeBlock(startX + i * BLOCK_W, rowCenterY));
      }
      rowBottom -= BLOCK_H;
    }
    return stack;
  }

  // Several distinct blueprints for the bunker enclosing the troops, so
  // replaying Rescue Mission doesn't always hand back the exact same
  // structure -- different wall thickness, height, material, and stacking
  // pattern, picked at random each time generateBunker() runs.
  const BUNKER_LAYOUTS = [
    // The original: thick twin sandbag walls, flat 2-row concrete roof.
    { name: "sandbag-fortress", thickness: 2, height: 4, wallMaterial: "sandbag", roofRows: 2, taper: false },
    // Thicker but shorter wooden crate barricades -- faster to punch
    // through wall-to-wall, but there's more of it to clear sideways.
    { name: "crate-barricade", thickness: 3, height: 3, wallMaterial: "crate", roofRows: 2, taper: false },
    // Thin single-file pillars holding up a heavy 3-row concrete slab --
    // easy to breach the walls, but the roof itself is the real obstacle.
    { name: "pillar-slab", thickness: 1, height: 6, wallMaterial: "sandbag", roofRows: 3, taper: false },
    // A stepped, ziggurat-style profile: the outer columns are shorter
    // than the inner one, so the wall rises in tiers toward the room
    // instead of presenting one flat face.
    { name: "stepped-ziggurat", thickness: 3, height: 5, wallMaterial: "sandbag", roofRows: 2, taper: true },
    // Alternating sandbag/crate rows for a mixed-material defense.
    { name: "mixed-defense", thickness: 2, height: 4, wallMaterial: "row", roofRows: 2, taper: false },
  ];

  function generateBunker(centerX) {
    const layout = BUNKER_LAYOUTS[Math.floor(rand(0, BUNKER_LAYOUTS.length))];
    bunkerWallThickness = layout.thickness;
    bunkerWallHeight = layout.height;

    const groundY = terrainAt(centerX);
    const blocks = [];

    for (let side = -1; side <= 1; side += 2) {
      for (let col = 0; col < layout.thickness; col++) {
        const bx = centerX + side * (BUNKER_HALF_WIDTH + BLOCK_W / 2 + col * BLOCK_W);
        // Outer columns step down for the ziggurat blueprint; every other
        // blueprint just uses the full wall height for every column.
        const colHeight = layout.taper
          ? Math.max(1, layout.height - (layout.thickness - 1 - col) * 2)
          : layout.height;
        for (let row = 0; row < colHeight; row++) {
          const by = groundY - BLOCK_H / 2 - row * BLOCK_H;
          const material = layout.wallMaterial === "row" ? (row % 2 === 0 ? "sandbag" : "crate") : layout.wallMaterial;
          blocks.push(makeBlock(bx, by, material));
        }
      }
    }

    const roofLeft = centerX - BUNKER_HALF_WIDTH - layout.thickness * BLOCK_W;
    const roofRight = centerX + BUNKER_HALF_WIDTH + layout.thickness * BLOCK_W;
    const roofBlockCount = Math.round((roofRight - roofLeft) / BLOCK_W);
    const roofStartX = roofLeft + BLOCK_W / 2;
    for (let row = 0; row < layout.roofRows; row++) {
      const by = groundY - BLOCK_H / 2 - layout.height * BLOCK_H - row * BLOCK_H;
      for (let i = 0; i < roofBlockCount; i++) {
        blocks.push(makeBlock(roofStartX + i * BLOCK_W, by, "concrete"));
      }
    }

    return blocks;
  }

  // The room's interior air pocket -- inside the walls, under the roof.
  // While any part of the enclosure is still standing, an explosion
  // outside this box is absorbed by the structure and can't reach the
  // troops directly; only a shot that actually gets inside (or debris
  // that physically falls on them, handled separately) puts them at risk.
  function isInsideBunker(x, y) {
    const groundY = terrainAt(TROOPS_X);
    const left = TROOPS_X - BUNKER_HALF_WIDTH;
    const right = TROOPS_X + BUNKER_HALF_WIDTH;
    const top = groundY - bunkerWallHeight * BLOCK_H;
    return x > left && x < right && y > top && y < groundY;
  }

  function blockCorners(b) {
    const hw = b.w / 2;
    const hh = b.h / 2;
    const cos = Math.cos(b.angle);
    const sin = Math.sin(b.angle);
    const local = [
      [-hw, -hh],
      [hw, -hh],
      [hw, hh],
      [-hw, hh],
    ];
    return local.map(([lx, ly]) => ({ x: b.x + lx * cos - ly * sin, y: b.y + lx * sin + ly * cos }));
  }

  function blockAABB(b) {
    const corners = blockCorners(b);
    const xs = corners.map((p) => p.x);
    const ys = corners.map((p) => p.y);
    return { minX: Math.min(...xs), maxX: Math.max(...xs), minY: Math.min(...ys), maxY: Math.max(...ys) };
  }

  function resolveBlockTerrain(b) {
    let maxPenetration = -Infinity;
    let contactX = b.x;
    for (const c of blockCorners(b)) {
      const pen = c.y - terrainAt(c.x);
      if (pen > maxPenetration) {
        maxPenetration = pen;
        contactX = c.x;
      }
    }
    if (maxPenetration > 0) {
      const incomingSpeed = b.vy;
      b.y -= maxPenetration;
      if (b.vy > 0) b.vy = -b.vy * BLOCK_RESTITUTION;
      b.vx *= 0.85;
      // Nudge rotation toward whichever side is digging in deepest, so a
      // block resting off-balance keeps tipping until it lies flat. Gated
      // to genuine landing impacts (real incoming downward speed) so a
      // block that's already settled doesn't get re-torqued every frame
      // forever, which would never let it fully come to rest.
      if (incomingSpeed > 0.3) {
        b.av += (contactX - b.x) * 0.002;
      }
      b.av *= 0.9;
    }
  }

  // Keeps the troops' room a genuinely clear space: any block drifting
  // toward the interior air pocket (from an explosion knocking it loose,
  // or a partial collapse) gets pushed back out along whichever boundary
  // it's closest to crossing -- the left wall line, the right wall line,
  // or the roof's underside -- so debris piles up around the room instead
  // of spilling into it and burying the troops in rubble.
  function resolveBlockRoom(b) {
    const groundY = terrainAt(TROOPS_X);
    const roomLeft = TROOPS_X - BUNKER_HALF_WIDTH;
    const roomRight = TROOPS_X + BUNKER_HALF_WIDTH;
    const roomTop = groundY - bunkerWallHeight * BLOCK_H;

    const bA = blockAABB(b);
    const overlapsX = bA.maxX > roomLeft && bA.minX < roomRight;
    const overlapsY = bA.maxY > roomTop && bA.minY < groundY;
    if (!overlapsX || !overlapsY) return;

    const options = [
      { amount: bA.maxX - roomLeft, dx: -(bA.maxX - roomLeft), dy: 0 },
      { amount: roomRight - bA.minX, dx: roomRight - bA.minX, dy: 0 },
      { amount: bA.maxY - roomTop, dx: 0, dy: -(bA.maxY - roomTop) },
    ];
    options.sort((a, c) => a.amount - c.amount);
    const push = options[0];
    b.x += push.dx;
    b.y += push.dy;
    if (push.dx !== 0) b.vx *= -0.35;
    else if (b.vy > 0) b.vy = -b.vy * 0.35;
  }

  function resolveBlockTank(b) {
    for (const tank of tanks) {
      if (!tank.alive) continue;
      const bA = blockAABB(b);
      const tTop = tank.y - TANK_H - 11;
      const tBottom = tank.y + 4;
      const tLeft = tank.x - TANK_W / 2 - 4;
      const tRight = tank.x + TANK_W / 2 + 4;
      const overlap = bA.minX < tRight && bA.maxX > tLeft && bA.minY < tBottom && bA.maxY > tTop;
      if (!overlap) continue;
      const speed = Math.hypot(b.vx, b.vy);
      if (speed > 1) tank.hp = Math.max(0, tank.hp - speed * 1.6);
      if (b.y < tank.y - TANK_H / 2) {
        b.y = tTop - b.h / 2;
        b.vy = -Math.abs(b.vy) * 0.2;
      } else {
        const dir = b.x < tank.x ? -1 : 1;
        b.x = tank.x + dir * (TANK_W / 2 + 4 + b.w / 2);
        b.vx *= -0.3;
      }
    }
  }

  // Approximate OBB-vs-OBB collision via each block's axis-aligned bounding
  // box (a rotated block's box grows with its tilt, which reads fine for
  // this purpose) -- resolved along whichever axis has the smaller overlap.
  function resolveBlockBlock(a, b) {
    const A = blockAABB(a);
    const B = blockAABB(b);
    const overlapX = Math.min(A.maxX, B.maxX) - Math.max(A.minX, B.minX);
    const overlapY = Math.min(A.maxY, B.maxY) - Math.max(A.minY, B.minY);
    if (overlapX <= 0 || overlapY <= 0) return;

    const aMovable = a.awake;
    const bMovable = b.awake;
    if (!aMovable && !bMovable) return;

    // Relative speed going into this resolution -- used to gate the torque
    // nudges below to genuine impacts. Without this gate, two blocks
    // resting in continuous contact (the normal steady state for a stack)
    // would get re-torqued every single frame forever and never settle.
    const preImpact = Math.abs(a.vx - b.vx) + Math.abs(a.vy - b.vy);

    if (overlapX < overlapY) {
      const dir = a.x < b.x ? -1 : 1;
      if (aMovable && bMovable) {
        a.x += (dir * overlapX) / 2;
        b.x -= (dir * overlapX) / 2;
      } else if (aMovable) {
        a.x += dir * overlapX;
      } else {
        b.x -= dir * overlapX;
      }
      const relVx = a.vx - b.vx;
      if (aMovable) a.vx -= relVx * 0.5;
      if (bMovable) b.vx += relVx * 0.5;
      // A side impact torques both blocks depending on whether it lands
      // above or below their centers, so a shove doesn't just slide --
      // it can start a block spinning/toppling.
      if (preImpact > 0.6) {
        const vOffset = a.y - b.y;
        const spin = Math.max(-0.4, Math.min(0.4, vOffset * 0.02 * dir));
        if (aMovable) a.av -= spin;
        if (bMovable) b.av += spin;
      }
    } else {
      const dir = a.y < b.y ? -1 : 1;
      if (aMovable && bMovable) {
        a.y += (dir * overlapY) / 2;
        b.y -= (dir * overlapY) / 2;
      } else if (aMovable) {
        a.y += dir * overlapY;
      } else {
        b.y -= dir * overlapY;
      }
      if (dir < 0) {
        if (aMovable && a.vy > 0) a.vy = 0;
      } else if (bMovable && b.vy > 0) {
        b.vy = 0;
      }
      if (aMovable) a.vx *= 0.9;
      if (bMovable) b.vx *= 0.9;
      // Whichever block is on top tips toward whichever side of its
      // support it overhangs, instead of balancing on it forever -- only
      // while it's actively landing, not every frame it merely rests there.
      if (preImpact > 0.6) {
        const hOffset = a.x - b.x;
        if (dir < 0 && aMovable) a.av += hOffset * 0.0025;
        if (dir > 0 && bMovable) b.av -= hOffset * 0.0025;
      }
    }

    const impactForce = Math.abs(a.vx) + Math.abs(a.vy) + Math.abs(b.vx) + Math.abs(b.vy);
    if (impactForce > 1.2) {
      a.awake = true;
      b.awake = true;
    }
  }

  // A block counts as supported if it (or the connected cluster of blocks
  // it's touching on any side) traces back to the ground -- so a roof
  // block bridging the gap between two walls stays up as long as the
  // walls under its neighbors are still standing, the way a real beam
  // spans an opening instead of needing something directly beneath it.
  function isBlockSupported(b) {
    const visited = new Set([b]);
    const stack = [b];
    while (stack.length) {
      const cur = stack.pop();
      const cA = blockAABB(cur);
      if (cA.maxY >= terrainAt(cur.x) - 1.5) return true;
      for (const other of blocks) {
        if (visited.has(other)) continue;
        const oA = blockAABB(other);
        const touchingX = cA.minX - 2 < oA.maxX && cA.maxX + 2 > oA.minX;
        const touchingY = cA.minY - 2 < oA.maxY && cA.maxY + 2 > oA.minY;
        if (touchingX && touchingY) {
          visited.add(other);
          stack.push(other);
        }
      }
    }
    return false;
  }

  function updateBlocks(dt) {
    for (const b of blocks) {
      if (!b.awake) continue;
      b.vy += BLOCK_GRAVITY * dt;
      b.vx *= 0.995;
      b.av *= 0.94;
      b.x += b.vx * dt;
      b.y += b.vy * dt;
      b.angle += b.av * dt;
      b.vx = Math.max(-22, Math.min(22, b.vx));
      b.vy = Math.max(-22, Math.min(22, b.vy));
      b.av = Math.max(-0.85, Math.min(0.85, b.av));
      resolveBlockTerrain(b);
      resolveBlockTank(b);
      if (mode === "demolition") resolveBlockRoom(b);
    }

    for (let i = 0; i < blocks.length; i++) {
      for (let j = i + 1; j < blocks.length; j++) {
        if (!blocks[i].awake && !blocks[j].awake) continue;
        resolveBlockBlock(blocks[i], blocks[j]);
      }
    }

    for (const b of blocks) {
      if (b.awake) {
        const speed = Math.abs(b.vx) + Math.abs(b.vy) + Math.abs(b.av) * 10;
        if (speed < BLOCK_SLEEP_SPEED) {
          b.settleTimer += dt;
          if (b.settleTimer > BLOCK_SLEEP_FRAMES) {
            b.awake = false;
            b.vx = 0;
            b.vy = 0;
            b.av = 0;
            b.settleTimer = 0;
          }
        } else {
          b.settleTimer = 0;
        }
      } else if (!isBlockSupported(b)) {
        b.awake = true;
      }
    }
  }

  function spawnBlockDebris(x, y) {
    for (let i = 0; i < 6; i++) {
      const ang = rand(-Math.PI, 0);
      const speed = rand(1, 4);
      particles.push({
        type: "debris",
        x,
        y,
        vx: Math.cos(ang) * speed,
        vy: Math.sin(ang) * speed,
        size: rand(2, 5),
        rot: rand(0, Math.PI * 2),
        vrot: rand(-0.3, 0.3),
        life: 1,
        decay: rand(0.014, 0.02),
        color: "160,110,60",
      });
    }
  }

  function applyExplosionToBlocks(x, y, weapon) {
    const survivors = [];
    for (const block of blocks) {
      const d = Math.hypot(block.x - x, block.y - y);
      const reach = weapon.blastRadius + Math.max(block.w, block.h) / 2 + BLOCK_BLAST_REACH_BONUS;
      if (d < reach) {
        const falloff = Math.max(0, 1 - d / reach);
        block.hp -= weapon.damage * falloff * BLOCK_BLAST_DAMAGE_MULT;
        const ang = Math.atan2(block.y - y, block.x - x);
        const force = falloff * (weapon.damage / 7);
        block.vx += Math.cos(ang) * force;
        block.vy += Math.sin(ang) * force - force * 0.5;
        block.av += rand(-0.35, 0.35) * falloff;
        block.awake = true;
      }
      if (block.hp > 0) survivors.push(block);
      else spawnBlockDebris(block.x, block.y);
    }
    blocks = survivors;
  }

  // ---------------------------------------------------------------------
  // Round / battle setup
  // ---------------------------------------------------------------------
  function newBattle() {
    terrain = generateTerrain();
    mountains = generateMountains();
    clouds = generateClouds();
    rocks = generateRocks();
    birds = generateBirds();
    strata = generateStrata();
    grassTufts = generateGrassTufts();
    hud.classList.toggle("demolition", mode === "demolition");

    const p1 = makeTank("left", "Player 1", "#e63946", false);
    if (mode === "demolition") {
      tanks = [p1];
      blocks = generateBunker(TROOPS_X);
      shotsFired = 0;
      troops = generateTroops();
      heli = null;
      statusIndicatorEl.textContent = "Clear the bunker";
      restartBtn.textContent = "New Mission";
    } else {
      const p2 =
        mode === "ai"
          ? makeTank("right", "CPU", "#457b9d", true)
          : makeTank("right", "Player 2", "#457b9d", false);
      tanks = [p1, p2];
      blocks = [...makeBlockStack(p1.x + 100, [4, 3, 2, 1]), ...makeBlockStack(p2.x - 100, [4, 3, 2, 1])];
      troops = [];
      heli = null;
      restartBtn.textContent = "New Battle";
    }

    currentTurn = 0;
    wind = Math.round(rand(-25, 25));
    selectedWeapon = "standard";
    projectiles = [];
    particles = [];
    turnState = "aiming";
    roundOver = false;
    dragging = false;
    aiTimer = 0;
    shakeTime = 0;
    shakeMag = 0;
    updateWeaponUI();
    updateHud();
    hideOverlay();
    maybeStartAiTurn();
  }

  function nextTurn() {
    if (roundOver) return;
    currentTurn = 1 - currentTurn;
    wind = Math.round(rand(-25, 25));
    turnState = "aiming";
    if (!tanks[currentTurn].ammo) tanks[currentTurn].ammo = { ...STARTING_AMMO };
    if (selectedWeapon !== "standard" && tanks[currentTurn].ammo[selectedWeapon] <= 0) {
      selectedWeapon = "standard";
    }
    updateWeaponUI();
    updateHud();
    maybeStartAiTurn();
  }

  function maybeStartAiTurn() {
    if (roundOver) return;
    if (tanks[currentTurn].isAI) {
      aiTimer = 55; // ~1 second "thinking" delay before firing
    }
  }

  // ---------------------------------------------------------------------
  // HUD
  // ---------------------------------------------------------------------
  function updateHud() {
    windIndicatorEl.textContent = (wind >= 0 ? "-> " : "<- ") + Math.abs(wind);
    if (mode === "demolition") {
      blocksIndicatorEl.textContent = String(blocks.length);
      shotsIndicatorEl.textContent = String(shotsFired);
    } else {
      const t = tanks[currentTurn];
      turnIndicatorEl.textContent = t.label;
      winsIndicatorEl.textContent = `${wins[0]} - ${wins[1]}`;
    }
  }

  function updateWeaponUI() {
    const t = tanks[currentTurn];
    document.getElementById("ammo-heavy").textContent = t.ammo.heavy;
    document.getElementById("ammo-rocket").textContent = t.ammo.rocket;
    document.getElementById("ammo-cluster").textContent = t.ammo.cluster;
    for (const btn of weaponButtons) {
      const wKey = btn.dataset.weapon;
      const w = WEAPONS[wKey];
      const outOfAmmo = !w.infinite && t.ammo[wKey] <= 0;
      btn.disabled = outOfAmmo || t.isAI;
      btn.classList.toggle("selected", wKey === selectedWeapon);
    }
  }

  weaponButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      if (btn.disabled) return;
      selectedWeapon = btn.dataset.weapon;
      updateWeaponUI();
    });
  });

  function hideOverlay() {
    overlay.classList.add("hidden");
  }

  function showOverlay(title, msg, btnLabel) {
    overlayTitle.textContent = title;
    overlayMsg.textContent = msg;
    overlayBtn.textContent = btnLabel;
    overlay.classList.remove("hidden");
  }

  restartBtn.addEventListener("click", () => {
    if (!mode) return;
    newBattle();
  });
  overlayBtn.addEventListener("click", () => newBattle());

  document.getElementById("mode-2p").addEventListener("click", () => {
    mode = "2p";
    modeSelect.classList.add("hidden");
    newBattle();
  });
  document.getElementById("mode-ai").addEventListener("click", () => {
    mode = "ai";
    modeSelect.classList.add("hidden");
    newBattle();
  });
  document.getElementById("mode-demo").addEventListener("click", () => {
    mode = "demolition";
    modeSelect.classList.add("hidden");
    newBattle();
  });

  // ---------------------------------------------------------------------
  // Input: drag from the active tank to aim & fire
  // ---------------------------------------------------------------------
  function canvasPoint(evt) {
    const rect = canvas.getBoundingClientRect();
    const scaleX = W / rect.width;
    const scaleY = H / rect.height;
    return {
      x: (evt.clientX - rect.left) * scaleX,
      y: (evt.clientY - rect.top) * scaleY,
    };
  }

  function turretPivot(tank) {
    return { x: tank.x, y: tank.y - TANK_H };
  }

  canvas.addEventListener("pointerdown", (evt) => {
    if (!mode || roundOver || turnState !== "aiming") return;
    const t = tanks[currentTurn];
    if (t.isAI) return;
    const p = canvasPoint(evt);
    const pivot = turretPivot(t);
    const d = Math.hypot(p.x - pivot.x, p.y - pivot.y);
    if (d < 60) {
      dragging = true;
      dragStart = pivot;
      dragPos = p;
      canvas.setPointerCapture(evt.pointerId);
      canvas.style.cursor = "grabbing";
    }
  });

  canvas.addEventListener("pointermove", (evt) => {
    if (!dragging) return;
    const p = canvasPoint(evt);
    const dx = p.x - dragStart.x;
    const dy = p.y - dragStart.y;
    const dist = Math.min(Math.hypot(dx, dy), MAX_DRAG);
    const ang = Math.atan2(dy, dx);
    dragPos = {
      x: dragStart.x + Math.cos(ang) * dist,
      y: dragStart.y + Math.sin(ang) * dist,
    };
  });

  function releaseAim() {
    if (!dragging) return;
    dragging = false;
    canvas.style.cursor = "grab";
    const dx = dragPos.x - dragStart.x;
    const dy = dragPos.y - dragStart.y;
    const dist = Math.hypot(dx, dy);
    if (dist < 10) return; // treat tiny drags as cancel
    fireShot(tanks[currentTurn], dx, dy, dist);
  }

  canvas.addEventListener("pointerup", releaseAim);
  canvas.addEventListener("pointercancel", releaseAim);

  // ---------------------------------------------------------------------
  // Firing
  // ---------------------------------------------------------------------
  function fireShot(tank, dx, dy, dist) {
    const weapon = WEAPONS[selectedWeapon];
    if (!weapon.infinite) {
      if (tank.ammo[selectedWeapon] <= 0) return;
      tank.ammo[selectedWeapon]--;
    }
    const speed = dist * POWER_SCALE * weapon.speedMult;
    const pivot = turretPivot(tank);
    const vx = (dx / dist) * speed;
    const vy = (dy / dist) * speed;
    projectiles.push({
      x: pivot.x,
      y: pivot.y,
      vx,
      vy,
      weaponKey: selectedWeapon,
      owner: tank,
      hasSplit: !weapon.cluster,
      trail: [],
    });
    turnState = "resolving";
    shotsFired++;
    updateWeaponUI();
  }

  // ---------------------------------------------------------------------
  // Trajectory simulation (shared by aim preview + simple AI targeting)
  // ---------------------------------------------------------------------
  function simulateLanding(startX, startY, vx, vy, maxSteps) {
    let x = startX;
    let y = startY;
    let svx = vx;
    let svy = vy;
    const points = [];
    for (let i = 0; i < maxSteps; i++) {
      svx += (wind * 0.0009);
      svy += GRAVITY;
      x += svx;
      y += svy;
      points.push({ x, y });
      if (x < 0 || x > W) break;
      if (y >= terrainAt(x)) break;
    }
    return { x, y, points };
  }

  // ---------------------------------------------------------------------
  // Explosions
  // ---------------------------------------------------------------------
  function explode(x, y, weapon, owner) {
    carveCrater(x, y, weapon.blastRadius);
    for (const tank of tanks) {
      if (!tank.alive) continue;
      const d = Math.hypot(tank.x - x, tank.y - TANK_H / 2 - y);
      if (d < weapon.blastRadius + TANK_W / 2) {
        const falloff = Math.max(0, 1 - d / (weapon.blastRadius + TANK_W / 2));
        tank.hp = Math.max(0, tank.hp - weapon.damage * falloff);
      }
    }
    // The room shields the troops from anything exploding outside it --
    // only a shot that actually gets inside the walls puts them at risk.
    // A blast landing just outside an intact wall does nothing to them,
    // no matter how close it is.
    if (isInsideBunker(x, y)) {
      for (const troop of troops) {
        if (!troop.alive || troop.rescued) continue;
        const troopY = terrainAt(troop.x) - 12;
        const d = Math.hypot(troop.x - x, troopY - y);
        if (d < weapon.blastRadius + 15) {
          troop.alive = false;
        }
      }
    }
    applyExplosionToBlocks(x, y, weapon);
    spawnExplosion(x, y, weapon);
    for (const tank of tanks) settleTankToTerrain(tank);
  }

  // A layered burst -- flash, fireball, shockwave ring, drifting smoke, and
  // flying dirt debris -- scaled by the weapon's blast radius, plus a bit
  // of screen shake for impact.
  function spawnExplosion(x, y, weapon) {
    const scale = weapon.blastRadius / 36;

    particles.push({ type: "flash", x, y, life: 1, decay: 0.22, maxR: weapon.blastRadius * 0.85 });
    particles.push({ type: "fire", x, y, life: 1, decay: 0.07, maxR: weapon.blastRadius * 1.05 });
    particles.push({ type: "ring", x, y, life: 1, decay: 0.06, maxR: weapon.blastRadius * 1.9 });

    const smokeCount = Math.round(rand(4, 6) * scale);
    for (let i = 0; i < smokeCount; i++) {
      particles.push({
        type: "smoke",
        x: x + rand(-8, 8) * scale,
        y: y + rand(-4, 4) * scale,
        vx: rand(-0.4, 0.4),
        vy: rand(-0.9, -0.4),
        r0: rand(4, 8) * scale,
        grow: rand(10, 18) * scale,
        life: 1,
        decay: rand(0.012, 0.02),
      });
    }

    const debrisCount = Math.round(rand(7, 11) * scale);
    for (let i = 0; i < debrisCount; i++) {
      const ang = rand(-Math.PI * 0.95, -Math.PI * 0.05);
      const speed = rand(2, 6) * Math.min(1.6, scale + 0.3);
      particles.push({
        type: "debris",
        x,
        y,
        vx: Math.cos(ang) * speed,
        vy: Math.sin(ang) * speed,
        size: rand(1.5, 4),
        rot: rand(0, Math.PI * 2),
        vrot: rand(-0.3, 0.3),
        life: 1,
        decay: rand(0.014, 0.024),
      });
    }

    shakeTime = Math.max(shakeTime, 0.35 + scale * 0.15);
    shakeMag = Math.max(shakeMag, Math.min(10, 3 + weapon.blastRadius * 0.1));
  }

  // ---------------------------------------------------------------------
  // Physics update
  // ---------------------------------------------------------------------
  function stepProjectiles(dt) {
    const survivors = [];
    for (const proj of projectiles) {
      let exploded = false;
      const weapon = WEAPONS[proj.weaponKey];
      for (let s = 0; s < SUBSTEPS && !exploded; s++) {
        const sdt = dt / SUBSTEPS;
        proj.vx += wind * 0.0009 * sdt;
        proj.vy += GRAVITY * sdt;
        proj.x += proj.vx * sdt;
        proj.y += proj.vy * sdt;

        if (proj.trail.length === 0 || Math.hypot(proj.x - proj.trail[proj.trail.length - 1].x, proj.y - proj.trail[proj.trail.length - 1].y) > 6) {
          proj.trail.push({ x: proj.x, y: proj.y });
          if (proj.trail.length > 40) proj.trail.shift();
        }

        // Cluster split at apex (vy transitions from negative to non-negative)
        if (weapon.cluster && !proj.hasSplit && proj.vy >= 0) {
          proj.hasSplit = true;
          for (let i = 0; i < weapon.clusterCount; i++) {
            const spread = (i - (weapon.clusterCount - 1) / 2) * 2.2;
            survivors.push({
              x: proj.x,
              y: proj.y,
              vx: proj.vx + spread,
              vy: proj.vy * 0.4,
              weaponKey: proj.weaponKey,
              owner: proj.owner,
              hasSplit: true,
              isBomblet: true,
              trail: [],
            });
          }
          exploded = true; // remove the parent shell; bomblets take over
          continue;
        }

        if (proj.x < -20 || proj.x > W + 20 || proj.y > H + 60) {
          exploded = true;
          break;
        }

        // Direct tank hits
        for (const tank of tanks) {
          if (!tank.alive) continue;
          if (
            proj.x > tank.x - TANK_W / 2 &&
            proj.x < tank.x + TANK_W / 2 &&
            proj.y > tank.y - TANK_H &&
            proj.y < tank.y
          ) {
            explode(proj.x, proj.y, weapon, proj.owner);
            exploded = true;
            break;
          }
        }
        if (exploded) break;

        // Direct block hits
        for (const block of blocks) {
          const aabb = blockAABB(block);
          if (proj.x > aabb.minX && proj.x < aabb.maxX && proj.y > aabb.minY && proj.y < aabb.maxY) {
            explode(proj.x, proj.y, weapon, proj.owner);
            exploded = true;
            break;
          }
        }
        if (exploded) break;

        if (proj.y >= terrainAt(proj.x)) {
          explode(proj.x, terrainAt(proj.x), weapon, proj.owner);
          exploded = true;
          break;
        }
      }
      if (!exploded) survivors.push(proj);
    }
    projectiles = survivors;
  }

  function updateParticles(dt) {
    for (const p of particles) {
      if (p.type === "smoke") {
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.vy -= 0.01 * dt;
      } else if (p.type === "debris") {
        p.vy += GRAVITY * 0.5 * dt;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.rot += p.vrot * dt;
      }
      p.life -= p.decay * dt;
    }
    particles = particles.filter((p) => p.life > 0);

    if (shakeTime > 0) {
      shakeTime = Math.max(0, shakeTime - dt * 0.04);
      if (shakeTime === 0) shakeMag = 0;
    }
  }

  // ---------------------------------------------------------------------
  // Simple AI — fires with a directly-computed velocity (searched for in
  // update()) instead of reusing the human drag-vector path in fireShot().
  // ---------------------------------------------------------------------
  function aiFireDirect(tank, vx, vy, weaponKey) {
    const weapon = WEAPONS[weaponKey];
    if (!weapon.infinite) tank.ammo[weaponKey]--;
    const pivot = turretPivot(tank);
    projectiles.push({
      x: pivot.x,
      y: pivot.y,
      vx,
      vy,
      weaponKey,
      owner: tank,
      hasSplit: !weapon.cluster,
      trail: [],
    });
    turnState = "resolving";
    updateWeaponUI();
  }

  // ---------------------------------------------------------------------
  // Main update
  // ---------------------------------------------------------------------
  function update(dt) {
    if (!mode || roundOver) return;

    if (turnState === "aiming" && tanks[currentTurn].isAI) {
      aiTimer -= dt;
      if (aiTimer <= 0) {
        const ai = tanks[currentTurn];
        const target = tanks[1 - currentTurn];
        const pivot = turretPivot(ai);
        const dir = facingSign(ai);
        let best = null;
        for (let deg = 15; deg <= 80; deg += 5) {
          const rad = (deg * Math.PI) / 180;
          for (const spd of [16, 20, 24, 28]) {
            const vx = dir * Math.cos(rad) * spd;
            const vy = -Math.sin(rad) * spd;
            const landing = simulateLanding(pivot.x, pivot.y, vx, vy, 260);
            const d = Math.abs(landing.x - target.x);
            if (!best || d < best.d) best = { d, vx, vy };
          }
        }
        const specials = ["heavy", "rocket", "cluster"].filter((k) => ai.ammo[k] > 0);
        let weaponKey = "standard";
        if (specials.length && Math.random() < 0.4) {
          weaponKey = specials[Math.floor(Math.random() * specials.length)];
        }
        selectedWeapon = weaponKey;
        const errX = rand(-0.1, 0.1);
        const errY = rand(-0.08, 0.08);
        aiFireDirect(ai, best.vx * (1 + errX), best.vy * (1 + errY), weaponKey);
      }
    }

    if (turnState === "resolving") {
      stepProjectiles(dt);
      if (projectiles.length === 0) {
        if (mode === "demolition") {
          if (isLZClear()) {
            if (!heli) {
              turnState = "cutscene";
              heli = { x: W + 70, y: 90, vx: -2.4, targetY: 90, phase: "inbound", extractTimer: 0 };
              statusIndicatorEl.textContent = "Chopper inbound...";
              updateHud();
            }
          } else {
            turnState = "aiming";
            wind = Math.round(rand(-25, 25));
            if (selectedWeapon !== "standard" && tanks[0].ammo[selectedWeapon] <= 0) {
              selectedWeapon = "standard";
            }
            updateWeaponUI();
            updateHud();
          }
        } else {
          const dead = tanks.filter((t) => t.hp <= 0);
          if (dead.length > 0) {
            for (const t of dead) t.alive = false;
            roundOver = true;
            const winnerIdx = tanks[0].hp <= 0 ? 1 : 0;
            wins[winnerIdx]++;
            winsIndicatorEl.textContent = `${wins[0]} - ${wins[1]}`;
            showOverlay(
              `${tanks[winnerIdx].label} Wins!`,
              `${tanks[1 - winnerIdx].label}'s tank was destroyed.`,
              "Rematch"
            );
          } else {
            nextTurn();
          }
        }
      }
    }

    updateBlocks(dt);
    updateParticles(dt);
    updateMission(dt);
  }

  // The chopper only needs a clear shaft of air directly above the troops
  // to descend through -- it doesn't care whether rubble from the side
  // walls is still lying around elsewhere. Checking "every block in the
  // whole bunker is gone" made the mission drag on well past the point
  // the LZ was actually flyable, since the side walls sit outside this
  // corridor and never blocked the vertical approach in the first place.
  function isLZClear() {
    const corridorHalfWidth = 22;
    const left = TROOPS_X - corridorHalfWidth;
    const right = TROOPS_X + corridorHalfWidth;
    const groundY = terrainAt(TROOPS_X);
    for (const block of blocks) {
      const aabb = blockAABB(block);
      const overlapsX = aabb.minX < right && aabb.maxX > left;
      const overlapsY = aabb.maxY > 60 && aabb.minY < groundY;
      if (overlapsX && overlapsY) return false;
    }
    return true;
  }

  // ---------------------------------------------------------------------
  // Rescue Mission: once the LZ is clear, a scripted chopper flies in from
  // the right, lands by the troops, extracts them, then flies back off --
  // only then does the round actually end.
  // ---------------------------------------------------------------------
  function updateMission(dt) {
    if (!heli) return;
    if (heli.phase === "inbound") {
      heli.x += heli.vx * dt;
      if (heli.x <= TROOPS_X + 4) {
        heli.phase = "landing";
        heli.targetY = terrainAt(TROOPS_X) - 34;
        statusIndicatorEl.textContent = "Touching down...";
      }
    } else if (heli.phase === "landing") {
      heli.y += (heli.targetY - heli.y) * 0.08 * dt;
      if (Math.abs(heli.y - heli.targetY) < 1) {
        heli.y = heli.targetY;
        heli.phase = "extract";
        heli.extractTimer = 0;
        statusIndicatorEl.textContent = "Extracting troops...";
      }
    } else if (heli.phase === "extract") {
      heli.extractTimer += dt;
      if (heli.extractTimer > 70) {
        for (const t of troops) if (t.alive) t.rescued = true;
        heli.phase = "departing";
        heli.vx = 2.4;
        heli.targetY = 90;
        statusIndicatorEl.textContent = "Evac in progress...";
      }
    } else if (heli.phase === "departing") {
      heli.y += (heli.targetY - heli.y) * 0.06 * dt;
      heli.x += heli.vx * dt;
      if (heli.x > W + 70) {
        heli.phase = "done";
        roundOver = true;
        const survivors = troops.filter((t) => t.alive).length;
        const total = troops.length;
        let title, msg;
        if (survivors === total) {
          title = "Troops Rescued!";
          msg = `Blew open the bunker and evac'd the squad in ${shotsFired} shot${shotsFired === 1 ? "" : "s"}.`;
        } else if (survivors > 0) {
          title = "Squad Extracted -- Losses Taken";
          msg = `${survivors} of ${total} made it out alive. Falling debris got the rest. ${shotsFired} shot${shotsFired === 1 ? "" : "s"} fired.`;
        } else {
          title = "Mission Failed";
          msg = `The whole squad was lost to falling debris before the chopper could land. ${shotsFired} shot${shotsFired === 1 ? "" : "s"} fired.`;
        }
        statusIndicatorEl.textContent = survivors > 0 ? "Mission Complete!" : "Mission Failed";
        showOverlay(
          title,
          msg,
          "New Mission"
        );
      }
    }
  }

  // ---------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------
  function drawBackground() {
    const sky = ctx.createLinearGradient(0, 0, 0, H);
    sky.addColorStop(0, "#3f7cad");
    sky.addColorStop(0.55, "#7fb4d8");
    sky.addColorStop(1, "#e3ecdf");
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, W, H);

    // Sun with a soft glow.
    const sunX = 820;
    const sunY = 75;
    const glow = ctx.createRadialGradient(sunX, sunY, 4, sunX, sunY, 70);
    glow.addColorStop(0, "rgba(255,247,214,0.9)");
    glow.addColorStop(1, "rgba(255,247,214,0)");
    ctx.fillStyle = glow;
    ctx.fillRect(sunX - 70, sunY - 70, 140, 140);
    ctx.fillStyle = "#fff3c4";
    ctx.beginPath();
    ctx.arc(sunX, sunY, 28, 0, Math.PI * 2);
    ctx.fill();

    // Soft clouds.
    for (const c of clouds) {
      ctx.fillStyle = `rgba(255,255,255,${c.opacity})`;
      ctx.beginPath();
      ctx.ellipse(c.x, c.y, 34 * c.scale, 14 * c.scale, 0, 0, Math.PI * 2);
      ctx.ellipse(c.x + 26 * c.scale, c.y + 4 * c.scale, 24 * c.scale, 12 * c.scale, 0, 0, Math.PI * 2);
      ctx.ellipse(c.x - 24 * c.scale, c.y + 5 * c.scale, 22 * c.scale, 11 * c.scale, 0, 0, Math.PI * 2);
      ctx.fill();
    }

    // A couple of distant birds for a touch of life on the horizon.
    ctx.strokeStyle = "rgba(60,70,85,0.55)";
    ctx.lineWidth = 1.4;
    for (const b of birds) {
      ctx.beginPath();
      ctx.moveTo(b.x - 7, b.y);
      ctx.quadraticCurveTo(b.x - 3, b.y - 5, b.x, b.y);
      ctx.quadraticCurveTo(b.x + 3, b.y - 5, b.x + 7, b.y);
      ctx.stroke();
    }

    // Hazy distant mountains for parallax depth behind the real terrain.
    ctx.beginPath();
    ctx.moveTo(0, H);
    ctx.lineTo(0, mountains[0]);
    for (let x = 0; x <= W; x += 4) ctx.lineTo(x, mountains[x]);
    ctx.lineTo(W, H);
    ctx.closePath();
    ctx.fillStyle = "rgba(99,120,140,0.45)";
    ctx.fill();

    // Atmospheric haze where the mountains fade into the nearer terrain,
    // giving the horizon a sense of distance.
    const hazeTop = Math.min(...mountains) - 10;
    const haze = ctx.createLinearGradient(0, hazeTop, 0, hazeTop + 90);
    haze.addColorStop(0, "rgba(226,235,230,0)");
    haze.addColorStop(1, "rgba(226,235,230,0.55)");
    ctx.fillStyle = haze;
    ctx.fillRect(0, hazeTop, W, 90);
  }

  function terrainPath() {
    ctx.beginPath();
    ctx.moveTo(0, H);
    ctx.lineTo(0, terrain[0]);
    for (let x = 0; x <= W; x += 2) ctx.lineTo(x, terrain[x]);
    ctx.lineTo(W, H);
    ctx.closePath();
  }

  // Absolute-canvas-Y band boundaries (not relative to the local surface),
  // so a valley exposes deeper strata immediately while a hilltop shows a
  // thick topsoil layer -- the way a real cross-section works. Each
  // boundary gets a gentle per-x wiggle so the layers read as folded rock
  // rather than perfectly flat lines.
  function stratumBoundary1(x) {
    return H * 0.72 + 14 * Math.sin(x * 0.015 + strata.p1);
  }
  function stratumBoundary2(x) {
    return H * 0.84 + 10 * Math.sin(x * 0.012 + strata.p2);
  }
  function stratumBoundary3(x) {
    return H * 0.93 + 8 * Math.sin(x * 0.02 + strata.p3);
  }

  const STRATUM_COLORS = ["#5c3a21", "#7a5433", "#6e6558", "#2b2019"];

  function stratumColorAt(x, y) {
    if (y < stratumBoundary1(x)) return STRATUM_COLORS[0];
    if (y < stratumBoundary2(x)) return STRATUM_COLORS[1];
    if (y < stratumBoundary3(x)) return STRATUM_COLORS[2];
    return STRATUM_COLORS[3];
  }

  function stratumPath(topFn, bottomFn) {
    ctx.beginPath();
    ctx.moveTo(0, topFn(0));
    for (let x = 0; x <= W; x += 4) ctx.lineTo(x, topFn(x));
    for (let x = W; x >= 0; x -= 4) ctx.lineTo(x, bottomFn(x));
    ctx.closePath();
  }

  function drawTerrain() {
    ctx.save();
    terrainPath();
    ctx.clip();

    // Layered soil/rock cross-section, each band an absolute-height slab so
    // valleys naturally cut into deeper layers than hilltops do.
    stratumPath(() => -50, stratumBoundary1);
    ctx.fillStyle = STRATUM_COLORS[0];
    ctx.fill();
    stratumPath(stratumBoundary1, stratumBoundary2);
    ctx.fillStyle = STRATUM_COLORS[1];
    ctx.fill();
    stratumPath(stratumBoundary2, stratumBoundary3);
    ctx.fillStyle = STRATUM_COLORS[2];
    ctx.fill();
    stratumPath(stratumBoundary3, () => H + 10);
    ctx.fillStyle = STRATUM_COLORS[3];
    ctx.fill();

    ctx.strokeStyle = "rgba(0,0,0,0.25)";
    ctx.lineWidth = 1.5;
    for (const b of [stratumBoundary1, stratumBoundary2, stratumBoundary3]) {
      ctx.beginPath();
      ctx.moveTo(0, b(0));
      for (let x = 0; x <= W; x += 8) ctx.lineTo(x, b(x));
      ctx.stroke();
    }

    // Scattered rock/pebble speckles (and a few larger boulders) embedded
    // in whichever layer they happen to sit in.
    for (const rk of rocks) {
      const y = terrainAt(rk.x) + rk.depth;
      if (y > H - 2) continue;
      ctx.fillStyle = shadeColor(stratumColorAt(rk.x, y), rk.shade);
      ctx.beginPath();
      ctx.ellipse(rk.x, y, rk.r, rk.r * 0.7, 0, 0, Math.PI * 2);
      ctx.fill();
      if (rk.boulder) {
        ctx.strokeStyle = "rgba(255,255,255,0.15)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.ellipse(rk.x - rk.r * 0.3, y - rk.r * 0.3, rk.r * 0.5, rk.r * 0.3, 0, 0, Math.PI * 2);
        ctx.stroke();
      }
    }

    // Grass cap: stroke the surface line with a thick green stroke while
    // clipped to the terrain shape, so only the half below the surface
    // shows, giving a consistent-thickness grass band regardless of hill
    // height, then add organic tufts for a less flat silhouette.
    ctx.beginPath();
    ctx.moveTo(0, terrain[0]);
    for (let x = 0; x <= W; x += 2) ctx.lineTo(x, terrain[x]);
    const grass = ctx.createLinearGradient(0, H * 0.3, 0, H * 0.55);
    grass.addColorStop(0, "#9ed373");
    grass.addColorStop(1, "#5e9440");
    ctx.strokeStyle = grass;
    ctx.lineWidth = 22;
    ctx.lineJoin = "round";
    ctx.stroke();

    ctx.lineWidth = 2;
    for (const tuft of grassTufts) {
      const ty = terrainAt(tuft.x) - 8;
      ctx.strokeStyle = shadeColor("#7cb95a", tuft.shade);
      ctx.beginPath();
      ctx.moveTo(tuft.x, ty);
      ctx.lineTo(tuft.x - 2 + tuft.lean, ty - tuft.h);
      ctx.moveTo(tuft.x, ty);
      ctx.lineTo(tuft.x + 2 + tuft.lean, ty - tuft.h * 0.7);
      ctx.stroke();
    }
    ctx.restore();
  }

  function drawTank(tank) {
    if (!tank.alive) return;
    const { x, y, color } = tank;
    const dir = facingSign(tank);
    const dark = shadeColor(color, -28);
    const darker = shadeColor(color, -45);
    const darkest = shadeColor(color, -60);
    const light = shadeColor(color, 18);

    // Health bar
    const hbW = 44;
    ctx.fillStyle = "rgba(0,0,0,0.4)";
    ctx.fillRect(x - hbW / 2, y - TANK_H - 24, hbW, 6);
    ctx.fillStyle = tank.hp > 40 ? "#06d6a0" : "#ef476f";
    ctx.fillRect(x - hbW / 2, y - TANK_H - 24, hbW * (tank.hp / 100), 6);

    // Contact shadow, soft and wider than the hull for a grounded feel.
    const shadowGrad = ctx.createRadialGradient(x, y + 2, 2, x, y + 2, TANK_W / 2 + 10);
    shadowGrad.addColorStop(0, "rgba(0,0,0,0.38)");
    shadowGrad.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = shadowGrad;
    ctx.beginPath();
    ctx.ellipse(x, y + 2, TANK_W / 2 + 10, 5, 0, 0, Math.PI * 2);
    ctx.fill();

    // Tracks: a dark rounded band wider than the hull, with road wheels
    // visible through gaps in a row of individual track links.
    const trackW = TANK_W + 10;
    const trackH = 11;
    const trackX = x - trackW / 2;
    const trackY = y - trackH;
    roundRect(trackX, trackY, trackW, trackH, 5);
    ctx.fillStyle = "#17181a";
    ctx.fill();

    ctx.fillStyle = "#3f4145";
    const wheelCount = 5;
    const wheelR = trackH / 2 - 1.4;
    for (let i = 0; i < wheelCount; i++) {
      const wx = trackX + trackW * ((i + 0.5) / wheelCount);
      ctx.beginPath();
      ctx.arc(wx, trackY + trackH / 2, wheelR, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = "#232427";
      ctx.beginPath();
      ctx.arc(wx, trackY + trackH / 2, wheelR * 0.4, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = "#3f4145";
    }

    // Individual track links along the top run.
    const linkCount = Math.round(trackW / 4.5);
    for (let i = 0; i < linkCount; i++) {
      const lx = trackX + (i + 0.5) * (trackW / linkCount);
      ctx.fillStyle = i % 2 === 0 ? "#4a4d52" : "#2a2c2f";
      ctx.fillRect(lx - 1.4, trackY + 0.5, 2.8, 2.5);
    }
    ctx.fillStyle = "rgba(255,255,255,0.1)";
    ctx.fillRect(trackX + 3, trackY + 0.5, trackW - 6, 1);

    // Hull: an asymmetric trapezoid with a sloped glacis plate facing the
    // direction the tank is pointed, shaded with a top-down gradient, then
    // camo blotches and weathering details clipped to the hull silhouette.
    const hullY = trackY + 1;
    const hullTop = hullY - TANK_H;
    const frontX = x + (dir * TANK_W) / 2;
    const rearX = x - (dir * TANK_W) / 2;
    const slopeInset = dir * 13;
    const hullPath = () => {
      ctx.beginPath();
      ctx.moveTo(rearX, hullY);
      ctx.lineTo(rearX, hullTop + TANK_H * 0.15);
      ctx.lineTo(rearX + dir * 4, hullTop);
      ctx.lineTo(frontX - slopeInset, hullTop);
      ctx.lineTo(frontX, hullTop + TANK_H * 0.5);
      ctx.lineTo(frontX, hullY);
      ctx.closePath();
    };
    hullPath();
    const hullGrad = ctx.createLinearGradient(0, hullTop, 0, hullY);
    hullGrad.addColorStop(0, light);
    hullGrad.addColorStop(0.5, color);
    hullGrad.addColorStop(1, dark);
    ctx.fillStyle = hullGrad;
    ctx.fill();

    ctx.save();
    hullPath();
    ctx.clip();
    for (const c of tank.camo) {
      ctx.fillStyle = shadeColor(color, c.shade);
      ctx.beginPath();
      ctx.ellipse(x + c.dx, hullTop + TANK_H * 0.5 + c.dy, c.rx, c.ry, c.rot, 0, Math.PI * 2);
      ctx.fill();
    }
    // Rim light along the top-facing edge (sun is upper-right).
    ctx.strokeStyle = "rgba(255,255,255,0.35)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(rearX + dir * 4, hullTop + 0.5);
    ctx.lineTo(frontX - slopeInset, hullTop + 0.5);
    ctx.stroke();
    // Lower hull ambient occlusion where it meets the tracks.
    const aoGrad = ctx.createLinearGradient(0, hullY - 5, 0, hullY);
    aoGrad.addColorStop(0, "rgba(0,0,0,0)");
    aoGrad.addColorStop(1, "rgba(0,0,0,0.32)");
    ctx.fillStyle = aoGrad;
    ctx.fillRect(rearX - 2, hullY - 5, TANK_W + 4, 5);
    ctx.restore();

    ctx.strokeStyle = darkest;
    ctx.lineWidth = 1.3;
    hullPath();
    ctx.stroke();

    // Headlight at the front.
    ctx.fillStyle = "#ffe9a8";
    ctx.beginPath();
    ctx.arc(frontX - dir * 4, hullTop + TANK_H * 0.62, 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = darkest;
    ctx.lineWidth = 0.8;
    ctx.stroke();

    // Painted star insignia on the hull side.
    drawStar(x - dir * TANK_W * 0.06, hullTop + TANK_H * 0.62, 4.2, "rgba(255,255,255,0.85)");

    // Hatch + rear exhaust with a soft rising smoke wisp.
    ctx.fillStyle = darkest;
    ctx.beginPath();
    ctx.arc(x - dir * 6, hullTop + TANK_H * 0.32, 3, 0, Math.PI * 2);
    ctx.fill();

    const exhaustX = rearX + dir * 3;
    ctx.fillStyle = "#232323";
    ctx.fillRect(exhaustX - 2, hullY - 4, 4, 4);
    const smokeT = (performance.now() / 900) % 1;
    ctx.fillStyle = `rgba(200,200,200,${0.22 * (1 - smokeT)})`;
    ctx.beginPath();
    ctx.arc(exhaustX - dir * 2, hullY - 6 - smokeT * 14, 2 + smokeT * 4, 0, Math.PI * 2);
    ctx.fill();

    // Turret + barrel, both rotated around the pivot to point at the aim angle.
    const isCurrent = tanks[currentTurn] === tank && turnState === "aiming" && !tank.isAI;
    let angleRad;
    if (isCurrent && dragging) {
      angleRad = Math.atan2(dragPos.y - (y - TANK_H), dragPos.x - x);
    } else {
      angleRad = Math.atan2(-Math.sin((tank.angle * Math.PI) / 180), dir * Math.cos((tank.angle * Math.PI) / 180));
    }
    const pivotX = x;
    const pivotY = hullTop + 1;

    // Turret body: an elongated, boat-shaped shell (wider at the back for the
    // bustle, tapering toward the mantlet) rather than a plain circle.
    ctx.save();
    ctx.translate(pivotX, pivotY);
    ctx.rotate(angleRad);
    const turretGrad = ctx.createLinearGradient(0, -8, 0, 8);
    turretGrad.addColorStop(0, light);
    turretGrad.addColorStop(0.5, color);
    turretGrad.addColorStop(1, dark);
    ctx.fillStyle = turretGrad;
    ctx.beginPath();
    ctx.moveTo(-11, -6.5);
    ctx.quadraticCurveTo(-13, 0, -11, 6.5);
    ctx.quadraticCurveTo(2, 8.5, 9, 5);
    ctx.quadraticCurveTo(13, 0, 9, -5);
    ctx.quadraticCurveTo(2, -8.5, -11, -6.5);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = darkest;
    ctx.lineWidth = 1.1;
    ctx.stroke();
    ctx.strokeStyle = "rgba(255,255,255,0.3)";
    ctx.lineWidth = 0.8;
    ctx.beginPath();
    ctx.moveTo(-9, -5.5);
    ctx.quadraticCurveTo(0, -7.2, 8, -4.3);
    ctx.stroke();

    // Mantlet (armored gun mount) where the barrel meets the turret.
    ctx.fillStyle = darker;
    ctx.beginPath();
    ctx.ellipse(9, 0, 4.5, 5, 0, 0, Math.PI * 2);
    ctx.fill();

    // Barrel: tapered with a highlight and a muzzle brake ring at the tip.
    ctx.fillStyle = dark;
    ctx.beginPath();
    ctx.moveTo(6, -3.4);
    ctx.lineTo(BARREL_LEN, -2.2);
    ctx.lineTo(BARREL_LEN, 2.2);
    ctx.lineTo(6, 3.4);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = "rgba(255,255,255,0.25)";
    ctx.fillRect(8, -2.6, BARREL_LEN - 12, 1.2);
    ctx.fillStyle = darkest;
    ctx.fillRect(BARREL_LEN - 4, -3.6, 4, 7.2);

    // Commander's cupola.
    ctx.fillStyle = dark;
    ctx.beginPath();
    ctx.arc(-6, -1, 3.4, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = darkest;
    ctx.lineWidth = 0.7;
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(-8, -1);
    ctx.lineTo(-4, -1);
    ctx.stroke();
    ctx.restore();

    // Antenna, anchored to the turret bustle.
    const bustleX = pivotX - Math.cos(angleRad) * 11;
    const bustleY = pivotY - Math.sin(angleRad) * 11;
    ctx.strokeStyle = darkest;
    ctx.lineWidth = 1.1;
    ctx.beginPath();
    ctx.moveTo(bustleX, bustleY);
    ctx.quadraticCurveTo(bustleX - dir * 6, bustleY - 18, bustleX - dir * 2, bustleY - 30);
    ctx.stroke();

    // Label
    ctx.fillStyle = "#fff";
    ctx.font = "12px Trebuchet MS";
    ctx.textAlign = "center";
    ctx.fillText(tank.label, x, y - TANK_H - 30);
  }

  function drawStar(cx, cy, r, fillStyle) {
    const inner = r * 0.45;
    ctx.beginPath();
    for (let i = 0; i < 10; i++) {
      const rad = i % 2 === 0 ? r : inner;
      const ang = (Math.PI / 5) * i - Math.PI / 2;
      const px = cx + Math.cos(ang) * rad;
      const py = cy + Math.sin(ang) * rad;
      if (i === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.fillStyle = fillStyle;
    ctx.fill();
  }

  function roundRect(x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function drawAimPreview() {
    if (!dragging) return;
    const tank = tanks[currentTurn];
    const pivot = turretPivot(tank);
    const dx = dragPos.x - pivot.x;
    const dy = dragPos.y - pivot.y;
    const dist = Math.hypot(dx, dy);
    if (dist < 5) return;
    const weapon = WEAPONS[selectedWeapon];
    const speed = dist * POWER_SCALE * weapon.speedMult;
    const vx = (dx / dist) * speed;
    const vy = (dy / dist) * speed;
    const sim = simulateLanding(pivot.x, pivot.y, vx, vy, 200);

    ctx.fillStyle = "rgba(255,255,255,0.75)";
    sim.points.forEach((pt, i) => {
      if (i % 3 !== 0) return;
      ctx.beginPath();
      ctx.arc(pt.x, pt.y, 2.5, 0, Math.PI * 2);
      ctx.fill();
    });

    // Power bar near tank
    const pct = Math.min(1, dist / MAX_DRAG);
    ctx.fillStyle = "rgba(0,0,0,0.4)";
    ctx.fillRect(pivot.x - 25, pivot.y - 30, 50, 6);
    ctx.fillStyle = "#ffd166";
    ctx.fillRect(pivot.x - 25, pivot.y - 30, 50 * pct, 6);
  }

  function drawProjectiles() {
    for (const proj of projectiles) {
      const weapon = WEAPONS[proj.weaponKey];
      ctx.strokeStyle = weapon.trailColor;
      ctx.lineWidth = 2;
      ctx.beginPath();
      proj.trail.forEach((pt, i) => {
        if (i === 0) ctx.moveTo(pt.x, pt.y);
        else ctx.lineTo(pt.x, pt.y);
      });
      ctx.stroke();

      ctx.fillStyle = weapon.color;
      ctx.beginPath();
      ctx.arc(proj.x, proj.y, proj.isBomblet ? 4 : 6, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function drawParticles() {
    // Smoke first so the fireball/flash read as on top of it, debris and
    // rings drawn last so they read crisply over everything else.
    for (const p of particles) {
      if (p.type !== "smoke") continue;
      const alpha = Math.max(0, p.life) * 0.45;
      const r = p.r0 + p.grow * (1 - p.life);
      const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, r);
      g.addColorStop(0, `rgba(90,90,90,${alpha})`);
      g.addColorStop(1, `rgba(90,90,90,0)`);
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
      ctx.fill();
    }

    for (const p of particles) {
      if (p.type !== "fire") continue;
      const alpha = Math.max(0, p.life);
      const r = p.maxR * (0.35 + 0.65 * (1 - p.life));
      const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, r);
      g.addColorStop(0, `rgba(255,241,189,${alpha})`);
      g.addColorStop(0.35, `rgba(255,160,60,${alpha * 0.9})`);
      g.addColorStop(0.7, `rgba(210,60,30,${alpha * 0.6})`);
      g.addColorStop(1, "rgba(120,30,20,0)");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
      ctx.fill();
    }

    for (const p of particles) {
      if (p.type !== "flash") continue;
      const alpha = Math.max(0, p.life);
      const r = p.maxR * (1 - p.life * 0.4);
      const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, r);
      g.addColorStop(0, `rgba(255,255,255,${alpha})`);
      g.addColorStop(1, "rgba(255,255,255,0)");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
      ctx.fill();
    }

    for (const p of particles) {
      if (p.type !== "debris") continue;
      ctx.save();
      ctx.translate(p.x, p.y);
      ctx.rotate(p.rot);
      ctx.fillStyle = `rgba(${p.color || "70,45,25"},${Math.max(0, p.life)})`;
      ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size);
      ctx.restore();
    }

    for (const p of particles) {
      if (p.type !== "ring") continue;
      const alpha = Math.max(0, p.life);
      const r = p.maxR * (1 - p.life);
      ctx.strokeStyle = `rgba(255,220,160,${alpha * 0.8})`;
      ctx.lineWidth = 2.5 * p.life + 0.5;
      ctx.beginPath();
      ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
      ctx.stroke();
    }
  }

  function drawWindArrow() {
    const cx = W / 2;
    const cy = 30;
    ctx.strokeStyle = "#fff";
    ctx.lineWidth = 3;
    const len = Math.min(60, Math.abs(wind) * 2 + 10);
    const dir = wind >= 0 ? 1 : -1;
    ctx.beginPath();
    ctx.moveTo(cx - (dir * len) / 2, cy);
    ctx.lineTo(cx + (dir * len) / 2, cy);
    ctx.stroke();
    ctx.beginPath();
    const tipX = cx + (dir * len) / 2;
    ctx.moveTo(tipX, cy);
    ctx.lineTo(tipX - dir * 8, cy - 5);
    ctx.lineTo(tipX - dir * 8, cy + 5);
    ctx.closePath();
    ctx.fillStyle = "#fff";
    ctx.fill();
  }

  function drawCrateBlock(b, dmgRatio) {
    const base = [150, 102, 58];
    const shade = base.map((c) => Math.round(c * (0.55 + 0.45 * dmgRatio)));
    ctx.fillStyle = `rgb(${shade[0]},${shade[1]},${shade[2]})`;
    ctx.fillRect(-b.w / 2, -b.h / 2, b.w, b.h);

    ctx.strokeStyle = "rgba(0,0,0,0.4)";
    ctx.lineWidth = 1.5;
    ctx.strokeRect(-b.w / 2, -b.h / 2, b.w, b.h);

    // Plank cross-bracing for a crate look, plus corner nail dots.
    ctx.strokeStyle = "rgba(0,0,0,0.22)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(-b.w / 2, -b.h / 2);
    ctx.lineTo(b.w / 2, b.h / 2);
    ctx.moveTo(b.w / 2, -b.h / 2);
    ctx.lineTo(-b.w / 2, b.h / 2);
    ctx.stroke();
    ctx.fillStyle = "rgba(0,0,0,0.3)";
    for (const [cx, cy] of [
      [-b.w / 2 + 3, -b.h / 2 + 3],
      [b.w / 2 - 3, -b.h / 2 + 3],
      [-b.w / 2 + 3, b.h / 2 - 3],
      [b.w / 2 - 3, b.h / 2 - 3],
    ]) {
      ctx.beginPath();
      ctx.arc(cx, cy, 1.2, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function drawSandbagBlock(b, dmgRatio) {
    const base = [148, 133, 84];
    const shade = base.map((c) => Math.round(c * (0.6 + 0.4 * dmgRatio)));
    const w = b.w;
    const h = b.h;
    ctx.beginPath();
    ctx.moveTo(-w / 2, -h / 2 + 3);
    ctx.quadraticCurveTo(-w / 4, -h / 2 - 2, 0, -h / 2 + 1);
    ctx.quadraticCurveTo(w / 4, -h / 2 - 2, w / 2, -h / 2 + 3);
    ctx.quadraticCurveTo(w / 2 + 2, 0, w / 2, h / 2 - 3);
    ctx.quadraticCurveTo(w / 4, h / 2 + 2, 0, h / 2 - 1);
    ctx.quadraticCurveTo(-w / 4, h / 2 + 2, -w / 2, h / 2 - 3);
    ctx.quadraticCurveTo(-w / 2 - 2, 0, -w / 2, -h / 2 + 3);
    ctx.closePath();
    ctx.fillStyle = `rgb(${shade[0]},${shade[1]},${shade[2]})`;
    ctx.fill();
    ctx.strokeStyle = "rgba(0,0,0,0.35)";
    ctx.lineWidth = 1;
    ctx.stroke();

    // Cinched center seam and end ties, like a tied-off sandbag.
    ctx.strokeStyle = "rgba(0,0,0,0.28)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(-w / 2 + 3, 0);
    ctx.lineTo(w / 2 - 3, 0);
    ctx.stroke();
    ctx.strokeStyle = "rgba(255,255,255,0.15)";
    ctx.beginPath();
    ctx.moveTo(-w / 3, -h / 2 + 4);
    ctx.lineTo(-w / 3, h / 2 - 4);
    ctx.moveTo(w / 3, -h / 2 + 4);
    ctx.lineTo(w / 3, h / 2 - 4);
    ctx.stroke();
  }

  function drawConcreteBlock(b, dmgRatio) {
    const base = [132, 132, 128];
    const shade = base.map((c) => Math.round(c * (0.6 + 0.4 * dmgRatio)));
    ctx.fillStyle = `rgb(${shade[0]},${shade[1]},${shade[2]})`;
    ctx.fillRect(-b.w / 2, -b.h / 2, b.w, b.h);
    ctx.strokeStyle = "rgba(0,0,0,0.4)";
    ctx.lineWidth = 1.3;
    ctx.strokeRect(-b.w / 2, -b.h / 2, b.w, b.h);

    // Speckled aggregate texture and a rebar hint along one edge.
    ctx.fillStyle = "rgba(0,0,0,0.18)";
    for (const [dx, dy] of [
      [-6, -4],
      [5, 2],
      [-2, 6],
      [7, -6],
      [-8, 2],
    ]) {
      ctx.beginPath();
      ctx.arc(dx, dy, 1, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.strokeStyle = "rgba(90,90,85,0.6)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(-b.w / 2 + 2, b.h / 2 - 2);
    ctx.lineTo(b.w / 2 - 2, b.h / 2 - 2);
    ctx.stroke();
  }

  function drawBlocks() {
    for (const b of blocks) {
      ctx.save();
      ctx.translate(b.x, b.y);
      ctx.rotate(b.angle);

      const dmgRatio = Math.max(0, b.hp / b.maxHp);
      if (b.material === "sandbag") drawSandbagBlock(b, dmgRatio);
      else if (b.material === "concrete") drawConcreteBlock(b, dmgRatio);
      else drawCrateBlock(b, dmgRatio);

      // Crack overlay once it's taken real damage, shared across materials.
      if (dmgRatio < 0.6) {
        ctx.strokeStyle = `rgba(20,10,5,${(0.6 - dmgRatio) * 1.2})`;
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        ctx.moveTo(-b.w / 4, -b.h / 2);
        ctx.lineTo(0, 0);
        ctx.lineTo(b.w / 3, b.h / 2);
        ctx.stroke();
      }
      ctx.restore();
    }
  }

  function drawTroops() {
    for (const t of troops) {
      if (t.rescued) continue;
      const gx = t.x;
      const gy = terrainAt(t.x);

      if (!t.alive) {
        // Down but still visible -- a prone silhouette instead of standing.
        ctx.strokeStyle = "#2f3b26";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(gx - 8, gy - 2);
        ctx.lineTo(gx + 8, gy - 2);
        ctx.stroke();
        ctx.fillStyle = "#4a5c34";
        ctx.fillRect(gx - 7, gy - 6, 9, 4);
        ctx.fillStyle = "#d9a066";
        ctx.beginPath();
        ctx.arc(gx + 8, gy - 4, 2.6, 0, Math.PI * 2);
        ctx.fill();
        continue;
      }

      ctx.strokeStyle = "#2f3b26";
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(gx - 2, gy - 2);
      ctx.lineTo(gx - 3, gy - 10);
      ctx.moveTo(gx + 2, gy - 2);
      ctx.lineTo(gx + 3, gy - 10);
      ctx.stroke();

      ctx.fillStyle = "#4a5c34";
      ctx.fillRect(gx - 3, gy - 18, 6, 9);

      ctx.strokeStyle = "#4a5c34";
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(gx + 3, gy - 16);
      ctx.lineTo(gx + 7, gy - 22);
      ctx.stroke();

      ctx.fillStyle = "#d9a066";
      ctx.beginPath();
      ctx.arc(gx, gy - 21, 3, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = "#3d4a2a";
      ctx.beginPath();
      ctx.arc(gx, gy - 22, 3.3, Math.PI, 0);
      ctx.fill();
    }
  }

  // A permanent steel reinforcement frame around the troops' room. Purely
  // a visual marker -- unlike the sandbag/concrete blocks it's never
  // destructible -- so the room's true boundary stays legible no matter
  // how much rubble is piled up or cleared.
  function drawRoomFrame() {
    const groundY = terrainAt(TROOPS_X);
    const roomLeft = TROOPS_X - BUNKER_HALF_WIDTH;
    const roomRight = TROOPS_X + BUNKER_HALF_WIDTH;
    const roomTop = groundY - bunkerWallHeight * BLOCK_H;
    const barW = 6;

    function steelBar(x, yTop, yBottom) {
      ctx.save();
      const grad = ctx.createLinearGradient(x - barW / 2, 0, x + barW / 2, 0);
      grad.addColorStop(0, "#2b333b");
      grad.addColorStop(0.4, "#c7d1da");
      grad.addColorStop(0.6, "#8b98a3");
      grad.addColorStop(1, "#232a30");
      ctx.fillStyle = grad;
      ctx.fillRect(x - barW / 2, yTop, barW, yBottom - yTop);
      ctx.strokeStyle = "#161b1f";
      ctx.lineWidth = 1;
      ctx.strokeRect(x - barW / 2, yTop, barW, yBottom - yTop);
      ctx.fillStyle = "#161b1f";
      for (let ry = yTop + 7; ry < yBottom - 3; ry += 13) {
        ctx.beginPath();
        ctx.arc(x, ry, 1.3, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    }

    // Vertical bars framing both sides of the room, ground to ceiling.
    steelBar(roomLeft, roomTop, groundY);
    steelBar(roomRight, roomTop, groundY);

    // Lintel tying the two bars together across the room's ceiling.
    ctx.save();
    const lintelGrad = ctx.createLinearGradient(0, roomTop - barW / 2, 0, roomTop + barW / 2);
    lintelGrad.addColorStop(0, "#c7d1da");
    lintelGrad.addColorStop(1, "#232a30");
    ctx.fillStyle = lintelGrad;
    ctx.fillRect(roomLeft - barW / 2, roomTop - barW / 2, roomRight - roomLeft + barW, barW);
    ctx.strokeStyle = "#161b1f";
    ctx.lineWidth = 1;
    ctx.strokeRect(roomLeft - barW / 2, roomTop - barW / 2, roomRight - roomLeft + barW, barW);
    ctx.restore();
  }

  function drawHeli() {
    if (!heli) return;
    ctx.save();
    ctx.translate(heli.x, heli.y);

    ctx.strokeStyle = "#5a6b78";
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.moveTo(-8, 0);
    ctx.lineTo(-34, -6);
    ctx.stroke();

    ctx.strokeStyle = "rgba(120,130,140,0.6)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(-34, -13);
    ctx.lineTo(-34, 1);
    ctx.stroke();

    const bodyGrad = ctx.createLinearGradient(0, -10, 0, 10);
    bodyGrad.addColorStop(0, "#7d8f9c");
    bodyGrad.addColorStop(1, "#4d5c68");
    ctx.fillStyle = bodyGrad;
    ctx.beginPath();
    ctx.ellipse(0, 0, 20, 10, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#33404a";
    ctx.lineWidth = 1.2;
    ctx.stroke();

    ctx.fillStyle = "rgba(180,220,235,0.75)";
    ctx.beginPath();
    ctx.ellipse(11, -2, 7, 6, 0, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = "#2c343b";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(-12, 11);
    ctx.lineTo(14, 11);
    ctx.moveTo(-8, 9);
    ctx.lineTo(-8, 13);
    ctx.moveTo(9, 9);
    ctx.lineTo(9, 13);
    ctx.stroke();

    ctx.strokeStyle = "#33404a";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, -9);
    ctx.lineTo(0, -12);
    ctx.stroke();

    const spin = (performance.now() / 40) % Math.PI;
    ctx.strokeStyle = "rgba(40,45,50,0.5)";
    ctx.lineWidth = 2;
    ctx.save();
    ctx.translate(0, -12);
    ctx.rotate(spin);
    ctx.beginPath();
    ctx.moveTo(-30, 0);
    ctx.lineTo(30, 0);
    ctx.stroke();
    ctx.rotate(Math.PI / 2);
    ctx.beginPath();
    ctx.moveTo(-30, 0);
    ctx.lineTo(30, 0);
    ctx.stroke();
    ctx.restore();

    ctx.restore();
  }

  function render() {
    ctx.clearRect(0, 0, W, H);
    if (!mode) return;

    ctx.save();
    if (shakeTime > 0) {
      const mag = shakeMag * Math.min(1, shakeTime / 0.3);
      ctx.translate(rand(-mag, mag), rand(-mag, mag));
    }

    drawBackground();
    drawTerrain();
    drawWindArrow();
    drawTroops();
    for (const tank of tanks) drawTank(tank);
    drawBlocks();
    if (mode === "demolition") drawRoomFrame();
    drawAimPreview();
    drawProjectiles();
    drawParticles();
    drawHeli();
    ctx.restore();
  }

  // ---------------------------------------------------------------------
  // Loop
  // ---------------------------------------------------------------------
  let lastTime = performance.now();
  function loop(now) {
    const dt = Math.min(2, (now - lastTime) / (1000 / 60));
    lastTime = now;
    update(dt);
    render();
    requestAnimationFrame(loop);
  }

  requestAnimationFrame(loop);
})();
