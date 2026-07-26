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
  const POWER_SCALE = 0.16;
  const TANK_W = 34;
  const TANK_H = 16;
  const BARREL_LEN = 26;

  const turnIndicatorEl = document.getElementById("turn-indicator");
  const windIndicatorEl = document.getElementById("wind-indicator");
  const winsIndicatorEl = document.getElementById("wins-indicator");
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
      damage: 40,
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

  function rand(min, max) {
    return min + Math.random() * (max - min);
  }

  // ---------------------------------------------------------------------
  // Terrain generation (sum of smooth sine waves -> continuous heightmap)
  // ---------------------------------------------------------------------
  function generateTerrain() {
    const baseY = H * 0.62;
    const waves = [
      { amp: rand(20, 45), freq: rand(0.004, 0.008), phase: rand(0, Math.PI * 2) },
      { amp: rand(10, 25), freq: rand(0.01, 0.02), phase: rand(0, Math.PI * 2) },
      { amp: rand(4, 10), freq: rand(0.03, 0.05), phase: rand(0, Math.PI * 2) },
    ];
    const t = new Array(W + 1);
    for (let x = 0; x <= W; x++) {
      let y = baseY;
      for (const w of waves) y += w.amp * Math.sin(x * w.freq + w.phase);
      t[x] = y;
    }
    return t;
  }

  function terrainAt(x) {
    const xi = Math.max(0, Math.min(W, Math.round(x)));
    return terrain[xi];
  }

  function carveCrater(cx, cy, radius) {
    const r = radius;
    const from = Math.max(0, Math.floor(cx - r));
    const to = Math.min(W, Math.ceil(cx + r));
    for (let x = from; x <= to; x++) {
      const dx = x - cx;
      const inside = r * r - dx * dx;
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
    };
  }

  function settleTankToTerrain(tank) {
    tank.y = terrainAt(tank.x);
  }

  function facingSign(tank) {
    return tank.side === "left" ? 1 : -1;
  }

  // ---------------------------------------------------------------------
  // Round / battle setup
  // ---------------------------------------------------------------------
  function newBattle() {
    terrain = generateTerrain();
    const p1 = makeTank("left", "Player 1", "#e63946", false);
    const p2 =
      mode === "ai"
        ? makeTank("right", "CPU", "#457b9d", true)
        : makeTank("right", "Player 2", "#457b9d", false);
    tanks = [p1, p2];
    currentTurn = 0;
    wind = Math.round(rand(-25, 25));
    selectedWeapon = "standard";
    projectiles = [];
    particles = [];
    turnState = "aiming";
    roundOver = false;
    dragging = false;
    aiTimer = 0;
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
    const t = tanks[currentTurn];
    turnIndicatorEl.textContent = t.label;
    windIndicatorEl.textContent = (wind >= 0 ? "-> " : "<- ") + Math.abs(wind);
    winsIndicatorEl.textContent = `${wins[0]} - ${wins[1]}`;
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
    particles.push({ x, y, r: 4, maxR: weapon.blastRadius * 1.3, life: 1 });
    for (const tank of tanks) settleTankToTerrain(tank);
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
      p.life -= dt * 0.06;
      p.r = p.maxR * (1 - Math.max(0, p.life));
    }
    particles = particles.filter((p) => p.life > 0);
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

    updateParticles(dt);
  }

  // ---------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------
  function drawBackground() {
    const sky = ctx.createLinearGradient(0, 0, 0, H);
    sky.addColorStop(0, "#6ba3c9");
    sky.addColorStop(1, "#cfe8f2");
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = "#ffe66d";
    ctx.beginPath();
    ctx.arc(820, 70, 36, 0, Math.PI * 2);
    ctx.fill();
  }

  function terrainPath() {
    ctx.beginPath();
    ctx.moveTo(0, H);
    ctx.lineTo(0, terrain[0]);
    for (let x = 0; x <= W; x += 2) ctx.lineTo(x, terrain[x]);
    ctx.lineTo(W, H);
    ctx.closePath();
  }

  function drawTerrain() {
    terrainPath();
    ctx.fillStyle = "#6b4423";
    ctx.fill();

    // Grass cap: clip to the terrain shape and stroke the surface line with a
    // thick green stroke, so only the half of it below the surface shows,
    // giving a consistent-thickness grass band regardless of hill height.
    ctx.save();
    terrainPath();
    ctx.clip();
    ctx.beginPath();
    ctx.moveTo(0, terrain[0]);
    for (let x = 0; x <= W; x += 2) ctx.lineTo(x, terrain[x]);
    ctx.strokeStyle = "#7cb95a";
    ctx.lineWidth = 22;
    ctx.lineJoin = "round";
    ctx.stroke();
    ctx.restore();
  }

  function drawTank(tank) {
    if (!tank.alive) return;
    const { x, y, color } = tank;

    // Health bar
    const hbW = 40;
    ctx.fillStyle = "rgba(0,0,0,0.4)";
    ctx.fillRect(x - hbW / 2, y - TANK_H - 20, hbW, 6);
    ctx.fillStyle = tank.hp > 40 ? "#06d6a0" : "#ef476f";
    ctx.fillRect(x - hbW / 2, y - TANK_H - 20, hbW * (tank.hp / 100), 6);

    // Body
    ctx.fillStyle = color;
    ctx.fillRect(x - TANK_W / 2, y - TANK_H, TANK_W, TANK_H);
    // Tracks
    ctx.fillStyle = "rgba(0,0,0,0.35)";
    ctx.fillRect(x - TANK_W / 2 - 3, y - 6, TANK_W + 6, 6);

    // Turret + barrel
    const isCurrent = tanks[currentTurn] === tank && turnState === "aiming" && !tank.isAI;
    let angleRad;
    if (isCurrent && dragging) {
      angleRad = Math.atan2(dragPos.y - (y - TANK_H), dragPos.x - x);
    } else {
      const dir = facingSign(tank);
      angleRad = Math.atan2(-Math.sin((tank.angle * Math.PI) / 180), dir * Math.cos((tank.angle * Math.PI) / 180));
    }
    const pivotX = x;
    const pivotY = y - TANK_H;
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(pivotX, pivotY, 9, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#222";
    ctx.lineWidth = 6;
    ctx.beginPath();
    ctx.moveTo(pivotX, pivotY);
    ctx.lineTo(pivotX + Math.cos(angleRad) * BARREL_LEN, pivotY + Math.sin(angleRad) * BARREL_LEN);
    ctx.stroke();

    // Label
    ctx.fillStyle = "#fff";
    ctx.font = "12px Trebuchet MS";
    ctx.textAlign = "center";
    ctx.fillText(tank.label, x, y - TANK_H - 26);
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
    for (const p of particles) {
      const alpha = Math.max(0, p.life);
      ctx.fillStyle = `rgba(255,150,60,${alpha})`;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
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

  function render() {
    ctx.clearRect(0, 0, W, H);
    if (!mode) return;
    drawBackground();
    drawTerrain();
    drawWindArrow();
    for (const tank of tanks) drawTank(tank);
    drawAimPreview();
    drawProjectiles();
    drawParticles();
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
