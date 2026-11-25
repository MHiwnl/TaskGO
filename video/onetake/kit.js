/* Kit compartilhado dos dois filmes onetake da TaskGO: canvas 1920×1080, desenho e o contrato do render.
   Tudo que desenha recebe t de fora; nada aqui guarda estado entre quadros (o render busca t fora de ordem). */
(function () {
  const W = 1920, H = 1080, DPR = window.devicePixelRatio || 1;
  const cv = document.createElement('canvas');
  cv.width = W * DPR; cv.height = H * DPR;
  cv.style.cssText = `position:fixed;left:0;top:0;width:${W}px;height:${H}px`;
  document.body.style.margin = '0'; document.body.style.background = '#000';
  document.body.appendChild(cv);
  const g = cv.getContext('2d');
  const FONT = "'Spline Sans', 'Segoe UI', sans-serif";

  const K = { W, H, DPR, g, cv, FONT };

  /* matriz do mundo → tela (já com o DPR) */
  K.mat = M => g.setTransform(M[0] * DPR, M[1] * DPR, M[2] * DPR, M[3] * DPR, M[4] * DPR, M[5] * DPR);
  K.screen = () => g.setTransform(DPR, 0, 0, DPR, 0, 0);
  K.mul = (A, B) => [A[0] * B[0] + A[2] * B[1], A[1] * B[0] + A[3] * B[1], A[0] * B[2] + A[2] * B[3], A[1] * B[2] + A[3] * B[3],
    A[0] * B[4] + A[2] * B[5] + A[4], A[1] * B[4] + A[3] * B[5] + A[5]];

  K.rr = (x, y, w, h, r) => {
    r = Math.max(0, Math.min(r, w / 2, h / 2));
    g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
    g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
  };
  K.fillRR = (x, y, w, h, r, fill, stroke, lw = 1) => {
    K.rr(x, y, w, h, r); if (fill) { g.fillStyle = fill; g.fill(); } if (stroke) { g.strokeStyle = stroke; g.lineWidth = lw; g.stroke(); }
  };
  K.dot = (x, y, r, fill) => { g.beginPath(); g.arc(x, y, Math.max(0, r), 0, Math.PI * 2); g.fillStyle = fill; g.fill(); };
  K.text = (s, x, y, { size = 32, weight = 500, color = '#fff', align = 'left', base = 'alphabetic', op = 1, track = 0 } = {}) => {
    if (op <= 0.002) return 0;
    g.save(); g.globalAlpha *= op; g.font = `${weight} ${size}px ${FONT}`; g.fillStyle = color; g.textAlign = align; g.textBaseline = base;
    if (track) g.letterSpacing = track + 'px';
    g.fillText(s, x, y); const w = g.measureText(s).width; g.restore(); return w;
  };
  K.measure = (s, size, weight = 500) => { g.save(); g.font = `${weight} ${size}px ${FONT}`; const w = g.measureText(s).width; g.restore(); return w; };

  /* estrela de 5 pontas, centro (x,y), raio r */
  K.star = (x, y, r, fill, stroke, lw = 2) => {
    g.beginPath();
    for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? r * 0.45 : r; g.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); }
    g.closePath(); if (fill) { g.fillStyle = fill; g.fill(); } if (stroke) { g.strokeStyle = stroke; g.lineWidth = lw; g.stroke(); }
  };
  /* ✓ desenhado até a fração k */
  K.check = (x, y, s, k, color, lw) => {
    const P = [[-0.42, 0.02], [-0.12, 0.3], [0.44, -0.3]], L1 = Math.hypot(0.3, 0.28), L2 = Math.hypot(0.56, 0.6), L = L1 + L2;
    let d = k * L; g.beginPath(); g.moveTo(x + P[0][0] * s, y + P[0][1] * s);
    const a = Math.min(1, d / L1); g.lineTo(x + (P[0][0] + (P[1][0] - P[0][0]) * a) * s, y + (P[0][1] + (P[1][1] - P[0][1]) * a) * s);
    if (d > L1) { const b = Math.min(1, (d - L1) / L2); g.lineTo(x + (P[1][0] + (P[2][0] - P[1][0]) * b) * s, y + (P[1][1] + (P[2][1] - P[1][1]) * b) * s); }
    g.strokeStyle = color; g.lineWidth = lw; g.lineCap = 'round'; g.lineJoin = 'round'; g.stroke();
  };

  /* QR do Pix: 25×25 módulos, decidido uma vez por semente (padrões de posição nos três cantos) */
  K.qr = (() => {
    const n = 25, r = OM.rng(280), M = [];
    for (let y = 0; y < n; y++) { M[y] = []; for (let x = 0; x < n; x++) M[y][x] = r() < 0.5; }
    const finder = (ox, oy) => { for (let y = -1; y < 8; y++) for (let x = -1; x < 8; x++) {
      const X = ox + x, Y = oy + y; if (X < 0 || Y < 0 || X >= n || Y >= n) continue;
      const e = Math.max(Math.abs(x - 3), Math.abs(y - 3)); M[Y][X] = x >= 0 && y >= 0 && x < 7 && y < 7 && (e === 3 || e <= 1); } };
    finder(0, 0); finder(n - 7, 0); finder(0, n - 7);
    for (let y = 10; y < 15; y++) for (let x = 10; x < 15; x++) M[y][x] = false;   // miolo livre para o ponto
    const cells = []; for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (M[y][x]) cells.push([x, y]);
    return { n, cells };
  })();
  /* desenha o QR em (x,y) lado s; k(i, cx, cy) → escala de cada módulo 0..1 (para ele surgir por ordem) */
  K.drawQR = (x, y, s, color, k) => {
    const c = s / K.qr.n; g.fillStyle = color;
    for (const [i, j] of K.qr.cells) { const e = k ? k(i, j) : 1; if (e <= 0.01) continue; const m = c * 0.92 * e, o = (c - m) / 2; g.fillRect(x + i * c + o, y + j * c + o, m, m); }
  };

  /* dedo (toque): anel que encolhe no clique */
  K.finger = (x, y, s, op, dark) => {
    if (op <= 0.01) return; g.save(); g.globalAlpha *= op;
    K.dot(x + 3, y + 5, 30 * s, 'rgba(0,0,0,0.18)');
    K.dot(x, y, 30 * s, dark ? 'rgba(255,255,255,0.92)' : 'rgba(20,24,36,0.88)');
    g.lineWidth = 3; g.strokeStyle = dark ? 'rgba(26,63,214,0.9)' : 'rgba(255,255,255,0.9)'; g.beginPath(); g.arc(x, y, 30 * s, 0, Math.PI * 2); g.stroke();
    g.restore();
  };

  /* granulação fixa contra faixas em gradiente escuro (a mesma em todo quadro) */
  const grain = (() => { const c = document.createElement('canvas'); c.width = c.height = 256; const x = c.getContext('2d'), d = x.createImageData(256, 256), r = OM.rng(7);
    for (let i = 0; i < d.data.length; i += 4) { const w = r() < 0.5 ? 255 : 0; d.data[i] = d.data[i + 1] = d.data[i + 2] = w; d.data[i + 3] = Math.floor(r() * 7); }
    x.putImageData(d, 0, 0); return c; })();
  K.grain = () => { g.save(); g.setTransform(1, 0, 0, 1, 0, 0); g.fillStyle = g.createPattern(grain, 'repeat'); g.fillRect(0, 0, cv.width, cv.height); g.restore(); };

  /* símbolo da marca (PNG 256²) */
  K.logo = new Image(); K.logo.src = 'simbolo.png';
  K.logoReady = new Promise(res => { if (K.logo.complete) res(); else K.logo.onload = res; });

  /* contrato: __meta, __seek, __ready, __events, __motion, __track; ?play e ?t= */
  K.run = ({ dur, draw, events = [], motion, track, inFrame = {} }) => {
    window.__meta = { dur, fps: 30, w: W, h: H, inFrame };
    let last = null;
    const seek = t => { last = draw(t); return last; };
    window.__seek = async t => { seek(t); return true; };
    window.__events = () => events;
    if (motion) window.__motion = motion;
    if (track) window.__track = track;
    window.__ready = Promise.all([document.fonts.load(`500 40px 'Spline Sans'`), document.fonts.load(`700 40px 'Spline Sans'`), K.logoReady])
      .then(() => document.fonts.ready).then(() => seek(0));
    const q = new URLSearchParams(location.search);
    if (q.has('play')) window.__ready.then(() => { const t0 = performance.now(); (function loop() { seek(((performance.now() - t0) / 1000) % dur); requestAnimationFrame(loop); })(); });
    else if (q.has('t')) window.__ready.then(() => { const t = +q.get('t'); seek(t); if (q.has('hud')) { K.screen(); K.text(t.toFixed(2) + ' s', 24, 1056, { size: 22, color: '#f0f' }); } });
  };
  window.K = K;
})();
