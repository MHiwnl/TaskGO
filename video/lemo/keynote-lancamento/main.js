// Filme TaskGO (Dark Tech Keynote). Um código para os dois formatos: 1920×1080 (16:9) e 1080×1920 (9:16).
import { W, H, setSize, setAccent, bg, grid, vignette, panel, badge, toast, scramble, glow, caret, lightReveal, flyPose, buf, font, PAL } from './engine.js';
import { clamp, seg, ss, eo, back, TAU } from '/core/lib.js';

const cv = document.getElementById('c');
cv.width = innerWidth; cv.height = innerHeight;
setSize(innerWidth, innerHeight);
const g = cv.getContext('2d');
const VERT = innerHeight > innerWidth;
setAccent('#2b59ff'); // ponto azul e a letra GO; o halo é o único brilho

const TYPED = 'Tomada queimou. Preciso de um eletricista.';
const PROB = [
  ['Sem resposta', 'Eletricista · Boa Viagem'], ['Orçamento sem retorno', 'Hidráulica · Pina'],
  ['Ninguém aceitou', 'Limpeza · Graças'], ['Ligue de novo', 'Ar-condicionado · Madalena'],
  ['Visita não confirmada', 'Montagem · Olinda'], ['Mensagem sem resposta', 'Pintura · Casa Amarela'],
  ['Pedido parado', 'Eletricista · Casa Amarela'], ['Sem retorno', 'TI · Recife'],
  ['Ninguém disponível', 'Hidráulica · Boa Viagem'], ['Sem resposta', 'Limpeza · Pina'],
  ['Orçamento pendente', 'Montagem · Graças'], ['Ligue de novo', 'Eletricista · Olinda'],
];
const PROS = [
  ['Carlos Andrade', 'ELETRICISTA', 'Boa Viagem', '4,9', 'R$ 120'],
  ['Ana Beatriz Souza', 'PINTURA', 'Casa Amarela', '4,8', 'R$ 260'],
  ['Juliana Ramos', 'LIMPEZA', 'Graças', '5,0', 'R$ 180'],
  ['Rafael Souza', 'HIDRÁULICA', 'Pina', '4,7', 'R$ 220'],
  ['Roberto Farias', 'AR-CONDICIONADO', 'Madalena', '4,9', 'R$ 320'],
  ['Marcos Lima', 'MONTAGEM', 'Olinda', '4,6', 'R$ 150'],
];
const BAIRROS = ['BOA VIAGEM', 'CASA AMARELA', 'PINA', 'GRAÇAS', 'MADALENA', 'OLINDA'];
// duração real de cada fala (vo/dur.json, ElevenLabs): define o tempo de legenda
const DUR_VOZ = { '1': 3.62, '2': 4.37, '3': 4.68, '4': 2.24, '5': 4.74, '6': 1.92, '7': 1.75, '8': 3.36, v3: 3.65, v5: 3.02, v8: 0.81 };

// ── Layouts (coordenadas em pixels do formato) ──────────────────────────────
const LAND_L = {
  field: [560, 440, 800, 90], end: [560, 380, 800, 90], board: [200, 120, 1520, 780], ctr: [960, 480],
  card: { cols: 3, w: 440, h: 120, gx: 30, gy: 30, x0: 270, y0: 250, n: 12 },
  pro: { cols: 3, w: 360, h: 210, gx: 36, gy: 36, x0: 384, y0: 330 },
  cell: { cols: 3, w: 400, h: 350, gx: 27, gy: 30, x0: 320, y0: 150 },
  word: [960, 540, 120], stars: [560, 300, 800, 440], star: 96, starGap: 150,
  price: [960, 400, 210], pill: [960, 610, 340, 84], code: [960, 640, 360], check: { x: 384, y: 850, dx: 384, dy: 0 },
};
const VERT_L = {
  field: [90, 420, 900, 120], end: [90, 700, 900, 120], board: [60, 300, 960, 840], ctr: [540, 720],
  card: { cols: 2, w: 440, h: 150, gx: 20, gy: 22, x0: 90, y0: 340, n: 8 },
  pro: { cols: 2, w: 430, h: 250, gx: 40, gy: 40, x0: 90, y0: 300 },
  cell: { cols: 2, w: 430, h: 250, gx: 40, gy: 20, x0: 90, y0: 200 },
  word: [540, 960, 110], stars: [60, 640, 960, 520], star: 110, starGap: 170,
  price: [540, 800, 180], pill: [540, 1000, 420, 96], code: [540, 1080, 300], check: { x: 90, y: 1210, dx: 0, dy: 64 },
};

// ── Linhas do tempo (segundos) ──────────────────────────────────────────────
const TL = {
  dur: 52, press: 2.4, board: [2.4, 3.0], accum: [3.0, 9.0], put: 9.0, beats: 4, shrink: [11.4, 12.0],
  freeze: [[20.1, 20.7], [29.9, 30.5]], silence: [[12.0, 12.6], [20.1, 20.7], [29.9, 30.5]],
  point: [[12.0, 29.6]], pts: [[12.0, 12.0, 'ctr'], [13.2, 14.2, 'bv'], [16.4, 17.0, 'carlos'], [18.8, 19.4, 'pill'], [23.4, 24.0, 'bv'], [24.4, 29.0, 'ca']],
  city: [12.6, 16.6], map: [24.0, 29.6], grid: [16.8, 17.2], sweep: [17.4, 18.4], collapse: [23.4, 24.0],
  pillIn: 18.6, tap: 20.0, rows: [21.0, 21.8, 22.6], checkEnd: 24.0, client: 29.0,
  code: [36.0, 40.8], locks: [36.0, 36.15, 36.3, 36.45],
  dive: [41.0, 41.6], pay: [41.0, 43.6], stars: [43.6, 46.2], starsAt: [43.9, 44.3, 44.7, 45.1, 45.5], starsOut: [45.8, 46.2],
  endIn: [46.2, 46.8], endOut: [48.0, 48.6], word: [48.6, 49.6], wordOut: [51.2, 52.0],
  voz: [['1', 2.6], ['2', 12.8], ['3', 18.2], ['4', 23.6], ['5', 30.6], ['6', 41.0], ['7', 43.6], ['8', 46.6]],
  music: { phase: [2.4, 12.0], add: [12.6, 20.0], pulse: [20.7, 29.9], arrive: 29.0, code: [36.0, 40.8], stars: [43.9, 44.3, 44.7, 45.1, 45.5], subtr: [45.8, 51.2], end: 51.2 },
};
const TV = {
  dur: 30, press: 2.4, board: [2.4, 3.0], accum: [3.0, 6.4], put: 6.4, beats: 4, shrink: [8.2, 8.8],
  freeze: [[12.0, 12.5], [16.1, 16.5]], silence: [[8.8, 9.2], [12.0, 12.5], [16.1, 16.5]],
  point: [[8.8, 16.1]], pts: [[8.8, 8.8, 'ctr'], [10.0, 10.6, 'carlos'], [11.2, 11.6, 'pill'], [12.6, 13.2, 'bv'], [13.6, 16.0, 'ca']],
  city: null, map: [13.0, 16.2], grid: [9.4, 9.8], sweep: [10.0, 11.0], collapse: [12.6, 13.2],
  pillIn: 11.0, tap: 11.9, rows: [12.1, 12.6, 13.1], checkEnd: 16.2, client: 16.0,
  code: [20.25, 23.2], locks: [20.25, 20.4, 20.55, 20.7],
  dive: [23.2, 23.7], pay: [23.2, 25.6], stars: [25.6, 26.8], starsAt: [25.6, 25.9, 26.2, 26.5, 26.7], starsOut: [26.8, 27.0],
  endIn: [27.0, 27.4], endOut: [28.1, 28.5], word: [28.3, 28.9], wordOut: [29.4, 30.0],
  voz: [['1', 2.6], ['v3', 9.2], ['v5', 16.6], ['6', 23.4], ['v8', 26.2]],
  music: { phase: [2.4, 8.8], add: [9.4, 12.0], pulse: [12.6, 16.0], arrive: 16.0, code: [20.25, 23.2], stars: [25.6, 25.9, 26.2, 26.5, 26.7], subtr: [27.0, 29.4], end: 29.4 },
};
const T = VERT ? TV : TL;
const L = VERT ? VERT_L : LAND_L;
const PT = {
  ctr: L.ctr,
  bv: [L.cell.x0 + L.cell.w / 2, L.cell.y0 + L.cell.h / 2],
  ca: [L.cell.x0 + L.cell.w + L.cell.gx + L.cell.w / 2, L.cell.y0 + L.cell.h / 2],
  carlos: [L.pro.x0 - 22, L.pro.y0 + L.pro.h / 2],
  pill: [L.pro.x0 + L.pro.w - 134, L.pro.y0 + 136], // borda esquerda do botão: o ponto não cobre o texto
};
// legendas: [id da fala, início, texto]; o fim é início + max(1,8 s; fala + 0,6 s)
const CAPS = (VERT ? [
  ['1', 2.6, 'Quando algo quebra em casa, o problema começa antes do conserto.'],
  ['v3', 9.2, 'Perto de você, com nota e preço, antes de chamar.'],
  ['v5', 16.6, 'Na chegada, você mostra o código de quatro dígitos.'],
  ['6', 23.4, 'Pague só quando terminar, por Pix.'],
  ['v8', 26.2, 'TaskGO.'],
] : [
  ['1', 2.6, 'Quando algo quebra em casa, o problema começa antes do conserto.'],
  ['2', 12.8, 'Depois vem a parte difícil: achar alguém de confiança, perto de casa.'],
  ['3', 18.2, 'Aqui você vê quem está perto, com nota e preço, antes de chamar.'],
  ['4', 23.6, 'Você chama, e acompanha a chegada no mapa.'],
  ['5', 30.6, 'Na chegada, você mostra o código de quatro dígitos. Só então o serviço começa.'],
  ['6', 41.0, 'Pague só quando terminar, por Pix.'],
  ['7', 43.6, 'Depois, avalie com estrelas.'],
  ['8', 46.6, 'TaskGO. Achar quem resolve, perto de casa.'],
]).map(([id, t0, text]) => ({ id, t0, t1: t0 + Math.max(1.8, DUR_VOZ[id] + 0.6), text }));

// ── Utilidades ──────────────────────────────────────────────────────────────
const lerp = (a, b, p) => a + (b - a) * p;
const rectLerp = (A, B, p) => A.map((v, i) => lerp(v, B[i], p));
const rnd = (i) => { const x = Math.sin(i * 12.9898 + 78.233) * 43758.5453; return x - Math.floor(x); };

function nVis(c) {
  const [a, b] = T.accum, N = L.card.n;
  if (c < a) return 0;
  if (c >= T.put) { const step = Math.min(T.beats, Math.floor((c - T.put) / 0.6) + 1); return N - Math.round(N * step / T.beats); }
  const k = clamp((c - a) / (b - a));
  return Math.floor(N * (0.35 * k + 0.65 * k * k));
}
const SPAWNS = (() => { const out = []; let prev = 0; for (let c = T.accum[0]; c < T.accum[1]; c += 0.01) { const n = nVis(c); while (prev < n) { out.push(c); prev++; } } return out; })();

function boardRect(c) {
  const [b0, b1] = T.board, [s0, s1] = T.shrink;
  if (c < b0) return null;
  if (c < s0) return rectLerp(L.field, L.board, back(seg(c, b0, b1), 1.1));
  if (c <= s1) return rectLerp(L.board, [L.ctr[0] - 8, L.ctr[1] - 8, 16, 16], ss(seg(c, s0, s1)));
  return null;
}

function drawCard(x, y, w, h, [titulo, corpo]) {
  panel(g, x, y, w, h, { r: 20 });
  g.textBaseline = 'alphabetic';
  g.fillStyle = PAL.text; g.font = font(28, 600); g.fillText(titulo, x + 30, y + 54);
  g.fillStyle = PAL.dim; g.font = font(22, 400); g.fillText(corpo, x + 30, y + 92);
  badge(g, x + w - 40, y + h - 32, '!', 1.1); // cor de problema: só enquanto o problema existe
}

function hook(c) {
  const f = L.field;
  panel(g, f[0], f[1], f[2], f[3], { r: 16 });
  const txt = TYPED.slice(0, Math.floor(seg(c, 0.3, 2.2) * TYPED.length));
  g.font = font(34, 400); g.fillStyle = PAL.text; g.textBaseline = 'middle';
  g.fillText(txt, f[0] + 30, f[1] + f[3] / 2);
  const cx = f[0] + 30 + g.measureText(txt).width + 6;
  if (c > 2.2 || Math.floor(c / 0.3) % 2 === 0) caret(g, cx, f[1] + f[3] / 2, 40);
  g.textBaseline = 'alphabetic';
}

function cards(c) {
  const C = L.card, n = nVis(c);
  for (let i = 0; i < n && i < PROB.length; i++) {
    drawCard(C.x0 + (i % C.cols) * (C.w + C.gx), C.y0 + Math.floor(i / C.cols) * (C.h + C.gy), C.w, C.h, PROB[i]);
  }
}

// Ponto azul: cursor da busca, depois a escolha, depois a pessoa que chega
function bez(A, B, q) {
  const dx = B[0] - A[0], dy = B[1] - A[1];
  const cx = (A[0] + B[0]) / 2 - 0.22 * dy, cy = (A[1] + B[1]) / 2 + 0.22 * dx;
  const u = 1 - q;
  return [u * u * A[0] + 2 * u * q * cx + q * q * B[0], u * u * A[1] + 2 * u * q * cy + q * q * B[1]];
}
function pointAt(c) {
  let pos = null;
  for (const [a, b, to] of T.pts) {
    if (c < a) break;
    const target = PT[to];
    if (c < b) return bez(pos || target, target, back(seg(c, a, b), 1.2));
    pos = target;
  }
  return pos;
}
function ponto(x, y) {
  glow(g, x, y, 110, PAL.accent, 0.22);
  g.fillStyle = PAL.accent; g.beginPath(); g.arc(x, y, 8, 0, TAU); g.fill();
}
function point(c) {
  if (!T.point.some(([a, b]) => c >= a && c < b)) return;
  const p = pointAt(c); if (p) ponto(p[0], p[1]);
}

// Cidade e mapa: a mesma grade de bairros; a rota sai de Boa Viagem até a cliente
function cells(c) {
  const C = L.cell;
  const wins = [T.map, T.city].filter(Boolean);
  if (!wins.some(([a, b]) => c >= a && c < b)) return;
  const [a0] = wins.find(([a, b]) => c >= a && c < b);
  for (let i = 0; i < 6; i++) {
    const x = C.x0 + (i % C.cols) * (C.w + C.gx), y = C.y0 + Math.floor(i / C.cols) * (C.h + C.gy);
    const al = ss(seg(c, a0 + i * 0.04, a0 + 0.4 + i * 0.04));
    panel(g, x, y, C.w, C.h, { r: 14, alpha: 0.35 + 0.65 * al });
    g.fillStyle = PAL.mute; g.font = font(18, 500, 'mono'); g.textBaseline = 'alphabetic';
    g.globalAlpha = al; g.fillText(BAIRROS[i], x + 22, y + 40); g.globalAlpha = 1;
  }
  const seg2 = T.pts.find(([, , to]) => to === 'ca');
  if (seg2 && c >= seg2[0]) {
    const u = clamp(seg(c, seg2[0], seg2[1]));
    g.strokeStyle = 'rgba(255,255,255,.28)'; g.lineWidth = 2; g.beginPath();
    for (let s = 0; s <= 24; s++) { const p = bez(PT.bv, PT.ca, u * s / 24); s ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); }
    g.stroke();
  }
  if (c >= T.client) {
    const s = back(seg(c, T.client, T.client + 0.3), 1.3);
    g.fillStyle = PAL.text; g.fillRect(PT.ca[0] - 11 * s, PT.ca[1] - 11 * s, 22 * s, 22 * s);
  }
}

// Desenha um cartão de profissional no contexto c2 (a tela ou o buffer da varredura de luz)
function drawTile(c2, x, y, data, alpha, s) {
  const { w, h } = L.pro;
  c2.save(); c2.globalAlpha *= alpha;
  c2.translate(x + w / 2, y + h / 2); c2.scale(s, s); c2.translate(-w / 2, -h / 2);
  panel(c2, 0, 0, w, h, { r: 20 });
  const [nome, servico, bairro, nota, preco] = data;
  c2.textBaseline = 'alphabetic';
  c2.fillStyle = PAL.text; c2.font = font(28, 600); c2.fillText(nome, 32, 56);
  c2.fillStyle = PAL.mute; c2.font = font(16, 500, 'mono'); c2.fillText(servico, 32, 86);
  c2.fillStyle = PAL.dim; c2.font = font(22, 400); c2.fillText(bairro, 32, 150);
  c2.fillStyle = PAL.text; c2.font = font(22, 600); c2.fillText('★ ' + nota, 32, 186);
  c2.textAlign = 'right'; c2.font = font(40, 600, 'disp'); c2.letterSpacing = '-1.5px';
  c2.fillText(preco, w - 32, 186); c2.letterSpacing = '0px'; c2.textAlign = 'left';
  c2.restore();
}

// Grade de profissionais: voa para os lugares, luz varre, só a escolhida acende, depois recolhe no ponto
function gradePros(c) {
  const P = L.pro, w = P.w, h = P.h;
  const slot = (i) => ({ x: P.x0 + (i % P.cols) * (w + P.gx), y: P.y0 + Math.floor(i / P.cols) * (h + P.gy) });
  const [g0, g1] = T.grid, [s0, s1] = T.sweep, [k0, k1] = T.collapse;
  if (c < g0) return;
  if (c >= k0) {
    const tgt = PT.pill;
    for (let i = 0; i < PROS.length; i++) {
      const s0_ = slot(i), f = flyPose({ x: s0_.x, y: s0_.y, rot: 0, s: 1 }, { x: tgt[0] - w / 2, y: tgt[1] - h / 2, rot: 0, s: 0.02 }, seg(c, k0 + i * 0.01, k1));
      if (f.s > 0.03) drawTile(g, f.x, f.y, PROS[i], 1, f.s);
    }
    return;
  }
  if (c < g1) {
    for (let i = 0; i < PROS.length; i++) {
      const sl = slot(i), f = flyPose({ x: L.ctr[0] - w / 2, y: L.ctr[1] - h / 2, rot: 0, s: 0.15 }, { x: sl.x, y: sl.y, rot: 0, s: 1 }, seg(c, g0, g1));
      drawTile(g, f.x, f.y, PROS[i], 1, f.s);
    }
    return;
  }
  if (c < s1) {
    // luz varre a grade: buffer da região e revelação só pelos pixels que a luz passou
    const rx = L.pro.x0 - 10, ry = L.pro.y0 - 10;
    const rw = Math.ceil((P.cols - 1) * (w + P.gx) + w + 20), rh = Math.ceil((Math.ceil(PROS.length / P.cols) - 1) * (h + P.gy) + h + 20);
    const [cb, gb] = buf('grade', rw, rh);
    gb.clearRect(0, 0, rw, rh); gb.setTransform(1, 0, 0, 1, -rx, -ry);
    for (let i = 0; i < PROS.length; i++) { const sl = slot(i); drawTile(gb, sl.x, sl.y, PROS[i], 1, 1); }
    lightReveal(g, cb, rx, ry, rw, rh, seg(c, s0, s1), { base: 0.3 });
    return;
  }
  for (let i = 0; i < PROS.length; i++) { const sl = slot(i); drawTile(g, sl.x, sl.y, PROS[i], i === 0 ? 1 : 0.3, 1); }
  if (c >= T.pillIn) pill(c);
}

function pill(c) {
  const P = L.pro, s = c < T.tap + 0.1 ? back(seg(c, T.pillIn, T.pillIn + 0.25), 1.3) : 1;
  const x = P.x0 + P.w - 120, y = P.y0 + 120, w = 100, h = 32;
  const done = c >= T.tap + 0.1;
  g.save(); g.translate(x + w / 2, y + h / 2); g.scale(s, s); g.translate(-(x + w / 2), -(y + h / 2));
  g.fillStyle = done ? '#2A2F3B' : PAL.text;
  g.beginPath(); g.roundRect(x, y, w, h, h / 2); g.fill();
  g.fillStyle = done ? PAL.text : '#0A0B0F'; g.font = font(17, 600); g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText(done ? 'Enviado' : 'Chamar', x + w / 2, y + h / 2 + 1);
  g.restore(); g.textAlign = 'left';
}

function checklist(c) {
  const C = L.check, labels = ['PEDIDO ENVIADO', 'CARLOS ACEITOU', 'A CAMINHO'];
  const end = T.checkEnd;
  labels.forEach((txt, i) => {
    if (c < T.rows[i] || c >= end) return;
    const a = ss(seg(c, T.rows[i], T.rows[i] + 0.2)), x = C.x, y = C.y;
    const rx = x + i * C.dx, ry = y + i * C.dy;
    g.globalAlpha = a; g.strokeStyle = PAL.text; g.lineWidth = 2; g.beginPath(); g.arc(rx + 9, ry - 7, 8, 0, TAU); g.stroke();
    g.beginPath(); g.moveTo(rx + 5, ry - 7); g.lineTo(rx + 8, ry - 4); g.lineTo(rx + 13, ry - 11); g.stroke();
    g.fillStyle = PAL.dim; g.font = font(20, 500, 'mono'); g.fillText(txt, rx + 30, ry);
    g.globalAlpha = 1;
  });
}

function code(c) {
  if (c < T.code[0] || c > T.code[1]) return;
  const [cx, by, px] = L.code;
  scramble(g, '4719', cx, by, c, T.locks, { px, wt: 600, fam: 'disp', align: 'center', tracking: -0.045 });
}

function pay(c) {
  if (c < T.pay[0] || c >= T.pay[1]) return;
  const [cx, cy, px] = L.price, [pcx, pcy, pw, ph] = L.pill;
  const s = lerp(0.25, 1, back(seg(c, T.dive[0], T.dive[1]), 1.1));
  g.save(); g.translate(cx, cy); g.scale(s, s); g.translate(-cx, -cy);
  g.fillStyle = PAL.text; g.font = font(px, 600, 'disp'); g.letterSpacing = '-4px'; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText('R$ 120', cx, cy); g.letterSpacing = '0px';
  g.fillStyle = PAL.text; g.beginPath(); g.roundRect(pcx - pw / 2, pcy - ph / 2, pw, ph, ph / 2); g.fill();
  g.fillStyle = '#0A0B0F'; g.font = font(30, 600); g.fillText('Pagar com Pix', pcx, pcy + 1);
  g.restore(); g.textAlign = 'left';
}

function star(cx, cy, R, lit) {
  g.beginPath();
  for (let k = 0; k < 10; k++) {
    const r = k % 2 ? R * 0.45 : R, a = -Math.PI / 2 + k * Math.PI / 5;
    g[k ? 'lineTo' : 'moveTo'](cx + r * Math.cos(a), cy + r * Math.sin(a));
  }
  g.closePath();
  if (lit) { g.fillStyle = PAL.text; g.fill(); } else { g.strokeStyle = 'rgba(255,255,255,.22)'; g.lineWidth = 2; g.stroke(); }
}

function stars(c) {
  const [s0, s1] = T.stars, [o0, o1] = T.starsOut;
  if (c < s0 || c >= o1) return;
  const R = L.stars;
  let r = c < s0 + 0.4 ? rectLerp(L.ctr.concat([16, 16]), R, back(seg(c, s0, s0 + 0.4), 1.1)) : R;
  if (c >= o0) r = rectLerp(R, L.ctr.concat([16, 16]), ss(seg(c, o0, o1)));
  panel(g, r[0], r[1], r[2], r[3], { r: 24 });
  if (c < o0) for (let k = 0; k < 5; k++) star(R[0] + R[2] / 2 + (k - 2) * L.starGap, R[1] + R[3] / 2, L.star / 2, c >= T.starsAt[k]);
}

function ending(c) {
  const f = L.field, e = L.end;
  const [i0, i1] = T.endIn, [o0, o1] = T.endOut;
  if (c >= i0 && c < o1) {
    const r = c < o0 ? rectLerp([L.ctr[0] - 8, L.ctr[1] - 8, 16, 16], e, back(seg(c, i0, i1), 1.1))
      : rectLerp(e, [L.ctr[0] - 8, L.ctr[1] - 8, 16, 16], ss(seg(c, o0, o1)));
    panel(g, r[0], r[1], r[2], r[3], { r: 16 });
    if (c >= i1 && r[2] > 300) { // o texto some junto com o campo, nunca antes
      g.font = font(34, 400); g.fillStyle = PAL.text; g.textBaseline = 'middle';
      const txt = 'Concluído'; g.fillText(txt, r[0] + 30, r[1] + r[3] / 2);
      caret(g, r[0] + 30 + g.measureText(txt).width + 6, r[1] + r[3] / 2, 40);
      g.textBaseline = 'alphabetic';
    }
  }
}

function word(c) {
  const [cx, cy, px] = L.word, [w0, w1] = T.word, [o0, o1] = T.wordOut;
  if (c < w0 || c >= o1) return;
  let sx = 1, sy = 1;
  if (c < w1) { sx = sy = Math.max(0.01, back(seg(c, w0, w1), 1.2)); }
  if (c >= o0) { sy = Math.max(0.01, 1 - ss(seg(c, o0, o0 + 0.4))); sx = Math.max(0.01, 1 - ss(seg(c, o0 + 0.4, o1))); }
  g.save(); g.translate(cx, cy); g.scale(sx, sy);
  g.font = font(px, 600); g.letterSpacing = '-3px';
  const w1x = g.measureText('Task').width, w2x = g.measureText('GO').width, x0 = -(w1x + w2x) / 2;
  g.textBaseline = 'middle'; g.textAlign = 'left';
  g.fillStyle = PAL.text; g.fillText('Task', x0, 0);
  g.fillStyle = PAL.accent; g.fillText('GO', x0 + w1x, 0);
  g.letterSpacing = '0px'; g.restore();
}

// Tempo congelado (contagem parada) e empurrão na moldura congelada
function contentTime(t) { for (const [a, b] of T.freeze) if (t >= a && t < b) return a; return t; }
function pushZoom(t) {
  for (const [a, b] of T.freeze) {
    if (t >= a && t < b) return 1 + 0.05 * ss(seg(t, a, b));
    if (t >= b && t < b + 0.3) return 1 + 0.05 * (1 - ss(seg(t, b, b + 0.3)));
  }
  return 1;
}

function stage(c) {
  if (c < T.press) hook(c);
  const br = boardRect(c);
  if (br) { panel(g, br[0], br[1], br[2], br[3], { r: 28, fill: '#11141B' }); if (c >= T.board[1] && c < T.shrink[0]) cards(c); }
  cells(c);
  gradePros(c);
  checklist(c);
  point(c);
  code(c);
  pay(c);
  stars(c);
  ending(c);
  word(c);
}

function caption(t) {
  for (const k of CAPS) if (t >= k.t0 && t < k.t1) { toast(g, k.text, eo(clamp((t - k.t0) / 0.16)), { px: VERT ? 32 : 40 }); return; }
}

// Notas da música (mix.py sintetiza): marimba na fase, pads no acorde, pulso, subtração no fim
function musica(M) {
  const N = [];
  const P = [293.66, 349.23, 392.0, 440.0, 523.25], PAT = [0, 2, 4, 2, 1, 3, 0, 2];
  for (let t = M.phase[0], i = 0; t < M.phase[1]; t += 0.3, i++) {
    N.push({ t, f: P[PAT[i % 8]], d: 0.5, a: 0.22, p: -0.35, k: 'marimba' });
    N.push({ t: t + (t - M.phase[0]) * 0.01, f: P[PAT[i % 8]], d: 0.5, a: 0.18, p: 0.35, k: 'marimba' });
  }
  for (let t = M.add[0], j = 0; t < M.add[1]; t += 2.4, j++) {
    N.push({ t, f: 146.83, d: 2.4, a: 0.12, p: 0, k: 'pad' });
    N.push({ t, f: P[j % 5], d: 1.2, a: 0.28, p: 0.2, k: 'marimba' });
  }
  for (let t = M.pulse[0]; t < M.pulse[1]; t += 0.6) N.push({ t, f: 55, d: 0.35, a: 0.3, p: 0, k: 'bass' });
  for (const f of [146.83, 369.99, 440]) N.push({ t: M.arrive, f, d: 3.4, a: 0.14, p: 0, k: 'pad' });
  N.push({ t: M.code[0], f: 73.42, d: M.code[1] - M.code[0], a: 0.12, p: 0, k: 'pad' });
  const S = [523.25, 587.33, 698.46, 783.99, 880];
  M.stars.forEach((t, k) => N.push({ t, f: S[k], d: 1.2, a: 0.35, p: (k - 2) * 0.2, k: 'marimba' }));
  const [a, b] = M.subtr, base = [293.66, 440, 587.33, 349.23];
  base.forEach((f, k) => N.push({ t: a, f, d: k === 0 ? b - a : 0.8 * (k + 1), a: 0.2, p: 0, k: 'marimba' }));
  for (const f of [146.83, 369.99, 440]) N.push({ t: M.end, f, d: 3.0, a: 0.16, p: 0, k: 'pad' });
  return N;
}

// Eventos de som e de silêncio: a mixagem lê este mesmo conjunto
const EV = [];
const ev = (t, type, o = {}) => EV.push({ t: +t.toFixed(3), type, ...o });
TYPED.split('').forEach((_, i) => ev(0.3 + i * (1.9 / TYPED.length), 'tecla'));
ev(T.press, 'toque');
ev(T.board[0], 'expande');
ev(T.shrink[0], 'retrai');
SPAWNS.forEach((t, i) => ev(t, 'vidro', { pan: rnd(i) * 1.6 - 0.8 }));
for (let j = 0; j < T.beats; j++) ev(T.put + j * 0.6, 'whoosh');
if (!VERT) { ev(T.grid[1], 'pouso'); ev(T.sweep[0], 'varredura'); ev(T.collapse[0], 'whoosh'); ev(T.map[0], 'expande'); }
else { ev(T.grid[1], 'pouso'); ev(T.sweep[0], 'varredura'); ev(T.collapse[0], 'whoosh'); ev(T.map[0], 'expande'); }
ev(T.pillIn, 'tick');
ev(T.tap, 'toque'); // contato antes do congelamento (o silêncio começa depois)
T.rows.forEach((t) => ev(t, 'tick'));
ev(T.client, 'chegada');
T.locks.forEach((t) => ev(t, 'digito'));
ev(T.dive[1], 'pix');
T.starsAt.forEach((t, k) => ev(t, 'estrela', { k }));
ev(T.endIn[0], 'whoosh');
ev(T.word[0], 'acorde');
ev(T.wordOut[0], 'crt');
T.voz.forEach(([id, t]) => ev(t, 'voz', { id }));
T.silence.forEach(([a, b]) => EV.push({ t: a, type: 'silencio', d: +(b - a).toFixed(3) }));
CAPS.forEach((k) => EV.push({ t: k.t0, type: 'legenda', t1: +k.t1.toFixed(3), text: k.text }));
musica(T.music).forEach((n) => EV.push({ t: +n.t.toFixed(3), type: 'nota', f: n.f, d: n.d, a: n.a, p: n.p, k: n.k }));

window.DUR = T.dur;
window.EV = EV;
window.render = (t) => {
  const c = contentTime(t);
  bg(g); grid(g); vignette(g, 0.55);
  const z = pushZoom(t);
  g.save();
  if (z !== 1) { g.translate(W / 2, H / 2); g.scale(z, z); g.translate(-W / 2, -H / 2); }
  stage(c);
  g.restore();
  caption(t);
};

Promise.all([
  document.fonts.load('600 40px InterTight'),
  document.fonts.load('500 40px Inter'),
  document.fonts.load('400 24px Inter'),
  document.fonts.load('700 16px JBMono'),
]).then(() => { window.READY = true; });
