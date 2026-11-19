/* Filme da TaskGO em 10 capítulos, cada um com uma técnica de movimento, costurados por um painel fixo.
   window.seek(t) pinta o quadro no tempo t (segundos). Cada quadro é função pura de t; aleatoriedade com semente.
   Cores: só as do site (css/base.css): preto, azul #2b59ff com grão, branco.
   Parâmetros: ?fmt=16x9|9x16|1x1  &reduzido=1 (movimento reduzido)  &legenda=0 */
(function () {
  const P = new URLSearchParams(location.search);
  const FMT = P.get('fmt') || '16x9', RED = P.get('reduzido') === '1', LEG = P.get('legenda') === '1';
  const [W, H] = { '16x9': [1920, 1080], '9x16': [1080, 1920], '1x1': [1080, 1080] }[FMT];
  const T = FILME.montarTimeline(window.DURACOES); window.TIMELINE = T;
  const C = T.C, em = (id, f) => C[id].inicio + C[id].dur * f;

  const L = {
    '16x9': { tit: { x: 96, base: 850, w: 740, fs1: 46, max2: 170 }, st: { x: 880, y: 150, w: 950, h: 760 }, leg: { x: 96, b: 112, fs: 32, chars: 60 }, hud: { m: 40, k: 34, fs: 18 }, par: { x: 96, y: 150 }, fs: 30 },
    '9x16': { tit: { x: 72, base: 1560, w: 936, fs1: 54, max2: 196 }, st: { x: 60, y: 300, w: 960, h: 920 }, leg: { x: 72, b: 210, fs: 42, chars: 30 }, hud: { m: 44, k: 40, fs: 26 }, par: { x: 72, y: 150 }, fs: 40 },
    '1x1': { tit: { x: 64, base: 905, w: 952, fs1: 36, max2: 128 }, st: { x: 64, y: 150, w: 952, h: 560 }, leg: { x: 64, b: 92, fs: 30, chars: 52 }, hud: { m: 30, k: 28, fs: 17 }, par: { x: 64, y: 78 }, fs: 26 }
  }[FMT];
  const SW = L.st.w, SH = L.st.h, MIN = Math.min(SW, SH), FS = L.fs;

  /* ---------- matemática ---------- */
  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const span = (t, a, b) => clamp((t - a) / (b - a));
  const lerp = (a, b, p) => a + (b - a) * p;
  function bezier(x1, y1, x2, y2) {
    const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx, cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
    const sx = u => ((ax * u + bx) * u + cx) * u, sy = u => ((ay * u + by) * u + cy) * u, dx = u => (3 * ax * u + 2 * bx) * u + cx;
    return x => { if (x <= 0) return 0; if (x >= 1) return 1; let u = x; for (let i = 0; i < 10; i++) { const d = sx(u) - x, dd = dx(u); if (Math.abs(d) < 1e-7 || Math.abs(dd) < 1e-7) break; u = clamp(u - d / dd); } return sy(u); };
  }
  const E = bezier(.16, 1, .3, 1), EIO = bezier(.65, 0, .35, 1);
  const mov = (t, a, d) => RED ? (t >= a ? 1 : 0) : E(span(t, a, a + d));
  const fad = (t, a, d) => RED ? span(t, a, a + 0.3) : E(span(t, a, a + d));
  const sai = (t, a, d) => 1 - fad(t, a, d);
  function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let x = Math.imul(seed ^ seed >>> 15, 1 | seed); x = x + Math.imul(x ^ x >>> 7, 61 | x) ^ x; return ((x ^ x >>> 14) >>> 0) / 4294967296; }; }

  /* ---------- DOM ---------- */
  const filme = document.getElementById('filme'); filme.style.width = W + 'px'; filme.style.height = H + 'px';
  const px = v => v + 'px';
  const el = (tag, css, pai, html) => { const e = document.createElement(tag); if (css) e.style.cssText = css; if (html != null) e.innerHTML = html; (pai || filme).appendChild(e); return e; };
  const op = (e, v) => { e.style.opacity = String(clamp(v)); e.style.visibility = v <= 0.001 ? 'hidden' : 'visible'; };
  const NS = 'http://www.w3.org/2000/svg';
  const sv = (tag, attrs, pai) => { const e = document.createElementNS(NS, tag); for (const k in attrs) e.setAttribute(k, attrs[k]); pai.appendChild(e); return e; };
  const CHECK = '<svg class="check" viewBox="0 0 24 24" style="width:1em;height:1em;vertical-align:-.12em"><path d="M4 12.5l5 5L20 6.5" stroke="currentColor"/></svg>';
  const XIS = '<svg class="check" viewBox="0 0 24 24" style="width:1em;height:1em;vertical-align:-.12em"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor"/></svg>';
  const mono = (fw, fs, extra = '') => `font-family:var(--mono);font-weight:${fw};font-size:${fs}px;${extra}`;

  /* grão do azul (uma textura de 200 px, com semente) */
  let GRAO = '';
  { const g = document.createElement('canvas'); g.width = g.height = 200; const ctx = g.getContext('2d'), img = ctx.createImageData(200, 200), r = rng(7);
    for (let i = 0; i < img.data.length; i += 4) { const v = r() < 0.5 ? 0 : 255; img.data[i] = img.data[i + 1] = img.data[i + 2] = v; img.data[i + 3] = 9 + (r() * 13 | 0); }
    ctx.putImageData(img, 0, 0); GRAO = 'url(' + g.toDataURL('image/png') + ')'; }

  /* ---------- título em duas vozes ---------- */
  const titulo = el('div', '', filme); titulo.id = 'titulo';
  titulo.innerHTML = '<span class="l l1"><span></span></span><span class="l l2"><span></span></span>';
  const tl = [...titulo.querySelectorAll('.l > span')];
  titulo.style.left = px(L.tit.x); titulo.style.width = px(L.tit.w);
  const medidor = document.createElement('canvas').getContext('2d');
  function caber(txt, peso, estilo, max, larg, track) {
    medidor.font = `${estilo} ${peso} 100px "Spline Sans Mono"`;
    const w = medidor.measureText(txt).width * 1.04;  /* folga: o espaçamento negativo nem sempre encurta tanto */
    return Math.min(max, Math.floor(larg / w * 100));
  }

  /* ---------- painel fixo ---------- */
  const hud = el('div', '', filme); hud.id = 'hud'; hud.style.fontSize = px(L.hud.fs); hud.style.display = 'none'; /* sem moldura de interface: o filme vende, não mostra régua, tempo nem BPM */
  const { m, k } = L.hud;
  [['left', 'top'], ['right', 'top'], ['left', 'bottom'], ['right', 'bottom']].forEach(([a, b]) => {
    el('div', `${a}:${m}px;${b}:${m}px;width:${k}px;height:${k}px;border-${a}-width:2px;border-${b}-width:2px;border-${a === 'left' ? 'right' : 'left'}-width:0;border-${b === 'top' ? 'bottom' : 'top'}-width:0`, hud).className = 'canto';
  });
  el('div', `position:absolute;left:${m + k + 14}px;top:${m + 6}px`, hud, 'TaskGO · filme ’26');
  const hTempo = el('div', `position:absolute;right:${m + k + 14}px;top:${m + 6}px;font-variant-numeric:tabular-nums`, hud);
  const reguaY = H - m - 12, reguaX = m + k + 14, reguaW = W - 2 * (m + k + 14);
  const hCap = el('div', `position:absolute;left:${reguaX}px;top:${reguaY - L.hud.fs * 1.9}px`, hud);
  const hBpm = el('div', `position:absolute;right:${reguaX}px;top:${reguaY - L.hud.fs * 1.9}px`, hud, '<i></i>96 BPM'); hBpm.className = 'bpm';
  const bolaBpm = hBpm.querySelector('i');
  const regua = el('div', `left:${reguaX}px;top:${reguaY}px;width:${reguaW}px`, hud); regua.className = 'regua';
  for (const c of T.cenas) el('i', `left:${(c.inicio / T.total) * 100}%`, regua);
  const reguaFill = el('b', '', regua);
  const param = el('div', `left:${L.par.x}px;top:${L.par.y}px`, hud); param.className = 'param';
  const NOMES = ['início', 'pedido', 'perto', 'identidade', 'endereço', 'a caminho', 'código', 'pagamento', 'chegada', 'conferido'];

  /* ---------- legenda ---------- */
  const legenda = el('div', `left:${L.leg.x}px;bottom:${L.leg.b}px;font-size:${L.leg.fs}px;max-width:${W - 2 * L.leg.x}px`, filme); legenda.id = 'legenda';
  const legSpan = el('span', '', legenda);
  function quebra(txt, max) { if (txt.length <= max) return txt; const ws = txt.split(' '); let best = null; for (let i = 1; i < ws.length; i++) { const a = ws.slice(0, i).join(' '), b = ws.slice(i).join(' '); const s = Math.max(a.length, b.length); if (!best || s < best.s) best = { s, t: a + '\n' + b }; } return best.t; }
  window.LEGENDAS = T.falas.map(f => ({ inicio: f.inicio, fim: f.fim, texto: quebra(f.texto, L.leg.chars) }));

  /* ---------- capítulos ---------- */
  const caps = [];
  function capitulo(fundo, monta) {
    const raiz = el('div', '', filme); raiz.className = 'cap';
    const palco = el('div', `position:absolute;left:${L.st.x}px;top:${L.st.y}px;width:${SW}px;height:${SH}px`, raiz);
    const up = monta(palco, raiz);
    caps.push({ fundo, raiz, up });
  }

  /* 01 · início: terminal, mira com o ponto orbitando, conversas que não dão em nada */
  capitulo('escuro', palco => {
    const cx = SW / 2, cy = SH * 0.42, r = MIN * 0.24;
    const s = sv('svg', { width: SW, height: SH, style: 'position:absolute;left:0;top:0;overflow:visible' }, palco);
    sv('line', { x1: 0, y1: cy, x2: SW, y2: cy, stroke: 'rgba(255,255,255,.17)' }, s);
    sv('line', { x1: cx, y1: 0, x2: cx, y2: SH, stroke: 'rgba(255,255,255,.17)' }, s);
    const anel = sv('circle', { cx, cy, r, fill: 'none', stroke: '#fff', 'stroke-width': 1.5, pathLength: 1, 'stroke-dasharray': 1, 'stroke-dashoffset': 1 }, s);
    for (let i = 0; i < 24; i++) { const a = i / 24 * Math.PI * 2; sv('line', { x1: cx + Math.cos(a) * (r + 10), y1: cy + Math.sin(a) * (r + 10), x2: cx + Math.cos(a) * (r + (i % 6 ? 16 : 26)), y2: cy + Math.sin(a) * (r + (i % 6 ? 16 : 26)), stroke: 'rgba(255,255,255,.35)' }, s); }
    const ponto = sv('circle', { r: MIN * 0.022, fill: '#8aa3ff' }, s);
    const term = el('div', mono(500, FS * 0.8, 'position:absolute;left:0;top:0;color:#fff;white-space:nowrap'), palco);
    const cA = el('div', `font-size:${FS}px;background:#000`, palco, '<div class="t">grupo do prédio</div><div style="font-weight:600">Alguém conhece um eletricista?</div><div class="t" style="margin:.5em 0 0">visto por todos · sem resposta</div>'); cA.className = 'cartao';
    const cB = el('div', `font-size:${FS}px;background:#000`, palco, '<div class="t">contato desconhecido</div><div style="font-weight:600">Pode deixar, chego em 10 min.</div><div class="t hora" style="margin:.5em 0 0"></div>'); cB.className = 'cartao';
    const hora = cB.querySelector('.hora');
    const linha = FMT !== '9x16';
    return t => {
      const c = C.pergunta, f = c.fala;
      anel.setAttribute('stroke-dashoffset', String(1 - mov(t, 0.1, 1.1)));
      const a = RED ? -Math.PI / 2 : -Math.PI / 2 + (t / 3.2) * Math.PI * 2;
      ponto.setAttribute('cx', cx + Math.cos(a) * r); ponto.setAttribute('cy', cy + Math.sin(a) * r);
      const txt = '> procurando eletricista', n = RED ? txt.length : clamp(Math.floor((t - 0.2) * 30), 0, txt.length);
      term.innerHTML = txt.slice(0, n) + ((t % 1) < 0.6 ? '<span style="background:#fff;color:#000">&nbsp;</span>' : '&nbsp;');
      const yA = SH * 0.68;
      cA.style.left = px(0); cA.style.top = px(yA); cA.style.transform = `translateY(${(1 - mov(t, em('pergunta', 0.12), 0.6)) * 40}px)`; op(cA, fad(t, em('pergunta', 0.12), 0.4));
      const tp = f.inicio + f.dur * 0.42;
      cB.style.left = px(linha ? Math.min(SW - (cB.offsetWidth || 420), (cA.offsetWidth || 420) + 36) : SW * 0.08); cB.style.top = px(linha ? yA - SH * 0.18 : yA + (cA.offsetHeight || 150) + 24);
      cB.style.transform = `translateY(${(1 - mov(t, tp, 0.6)) * 40}px)`; op(cB, fad(t, tp, 0.4));
      let h = '19:02'; for (const [mt, v] of [[em('pergunta', 0.30), '19:31'], [em('pergunta', 0.48), '19:58'], [em('pergunta', 0.66), '20:15']]) if (t >= mt) h = v;
      hora.innerHTML = h + (t >= em('pergunta', 0.66) ? ' · <b style="color:var(--erro)">não chegou</b>' : ' · visto');
    };
  });

  /* 02 · pedido: a ordem de serviço se imprime linha por linha */
  capitulo('azul', palco => {
    const w = Math.min(SW * 0.9, FMT === '1x1' ? 620 : 700), x0 = (SW - w) / 2;
    const tk = el('div', `position:absolute;left:${x0}px;top:${SH * 0.12}px;width:${w}px;background:#fff;color:#000;padding:${FS * 0.9}px ${FS}px;${mono(500, FS)}`, palco);
    const cab = el('div', 'display:flex;justify-content:space-between;align-items:center;border-bottom:3px solid #000;padding-bottom:.45em;margin-bottom:.2em', tk, '<b style="font-weight:700">pedido · elétrica</b><span style="font-size:.72em;letter-spacing:.08em;text-transform:uppercase;display:flex;align-items:center;gap:.45em"><i style="width:.75em;height:.75em;border-radius:50%;background:#2b59ff;display:inline-block"></i>procurando</span>');
    const LIN = [['serviço', 'chuveiro queimado'], ['quando', 'agora'], ['valor', 'R$ 120'], ['endereço', '<span style="background:#000;color:#fff;font-style:italic;font-weight:300;padding:0 .6em">oculto</span>']];
    const rows = LIN.map(([kk, v]) => {
      const r = el('div', 'position:relative;display:flex;justify-content:space-between;gap:1em;padding:.5em 0;overflow:hidden', tk, `<span style="color:#5a5d68">${kk}</span><span style="font-weight:600;white-space:nowrap">${v}</span>`);
      const fio = el('i', 'position:absolute;left:0;right:0;bottom:0;height:1px;background:rgba(0,0,0,.2);transform-origin:left', r);
      return { r, fio };
    });
    const bola = cab.querySelector('i');
    return t => {
      const c = C.pedido;
      tk.style.transform = `translateY(${(1 - mov(t, c.inicio + 0.05, 0.6)) * SH * 0.5}px)`;
      op(tk, fad(t, c.inicio + 0.05, 0.3));
      rows.forEach((x, i) => {
        const ti = em('pedido', [0.16, 0.28, 0.40, 0.52][i]);
        x.r.style.clipPath = RED ? (t >= ti ? 'none' : 'inset(0 100% 0 0)') : `inset(0 ${(1 - E(span(t, ti, ti + 0.35))) * 100}% 0 0)`;
        x.fio.style.transform = `scaleX(${mov(t, ti, 0.4)})`;
      });
      bola.style.transform = `scale(${RED ? 1 : 1 + 0.35 * Math.max(0, Math.sin(t * Math.PI * 3.2))})`;
    };
  });

  /* 03 · perto: partículas em 3D; a onda sai de você e acende os 10 mais perto */
  capitulo('escuro', palco => {
    const cv = el('canvas', '', palco); cv.width = SW; cv.height = SH;
    const ctx = cv.getContext('2d'), r = rng(418), N = 760, pts = [];
    for (let i = 0; i < N; i++) { const a = r() * Math.PI * 2, d = Math.sqrt(r()) * (0.12 + 0.88 * r() ** 0.15); pts.push({ x: Math.cos(a) * d, z: Math.sin(a) * d, y: (r() - 0.5) * 0.06, d }); }
    const perto = pts.filter(p => p.d > 0.12).sort((a, b) => a.d - b.d).slice(0, 10);
    perto.forEach((p, i) => p.ordem = i);
    const ESC = MIN * 0.72, CX = SW / 2, CY = SH * 0.48, TILT = 1.02;
    const proj = (x, y, z, a) => { const X = x * Math.cos(a) - z * Math.sin(a), Z = x * Math.sin(a) + z * Math.cos(a); const yy = y * Math.cos(TILT) - Z * Math.sin(TILT), zz = y * Math.sin(TILT) + Z * Math.cos(TILT); const f = 3 / (3 + zz); return { x: CX + X * ESC * f, y: CY + yy * ESC * f, f }; };
    const voce = el('div', mono(500, FS * 0.8, 'position:absolute;color:#fff'), palco, 'você');
    return t => {
      const c = C.perto, a = RED ? 0.4 : 0.4 + (t - c.inicio) * 0.16, t0 = em('perto', 0.12);
      ctx.clearRect(0, 0, SW, SH);
      const on = fad(t, c.inicio, 0.4);
      const pw = RED ? 1 : E(span(t, t0, t0 + 1.8));
      if (t >= t0) for (const [j, al] of [[0, 1], [0.18, 0.6]]) {
        const rr = Math.max(0, pw - j) * 1.05; ctx.beginPath();
        for (let s = 0; s <= 96; s++) { const q = proj(Math.cos(s / 96 * Math.PI * 2) * rr, 0, Math.sin(s / 96 * Math.PI * 2) * rr, a); s ? ctx.lineTo(q.x, q.y) : ctx.moveTo(q.x, q.y); }
        ctx.strokeStyle = `rgba(138,163,255,${(RED ? 0.5 : (1 - pw) * 0.9) * al})`; ctx.lineWidth = 2; ctx.stroke();
      }
      const o = proj(0, 0, 0, a);
      for (const p of pts) {
        const q = proj(p.x, p.y, p.z, a);
        const ti = p.ordem != null ? em('perto', 0.22 + p.ordem * 0.045) : 0;
        if (p.ordem != null && t >= ti) {
          ctx.strokeStyle = 'rgba(138,163,255,.45)'; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(o.x, o.y); ctx.lineTo(q.x, q.y); ctx.stroke();
          const pu = RED ? 1 : 1 + 0.8 * (1 - E(span(t, ti, ti + 0.5)));
          ctx.fillStyle = '#8aa3ff'; ctx.beginPath(); ctx.arc(q.x, q.y, 5.5 * q.f * pu, 0, Math.PI * 2); ctx.fill();
        } else { ctx.fillStyle = `rgba(211,213,220,${Math.min(1, 0.95 * q.f) * on})`; const s = 3.4 * q.f; ctx.fillRect(q.x - s / 2, q.y - s / 2, s, s); }
      }
      ctx.fillStyle = '#fff'; ctx.fillRect(o.x - 9, o.y - 9, 18, 18);
      voce.style.left = px(o.x - (voce.offsetWidth || 60) / 2); voce.style.top = px(o.y + 16); op(voce, on);
    };
  });

  /* 04 · identidade: formas montam o documento; o ponto vira o rosto */
  capitulo('claro', palco => {
    const dw = Math.min(SW * 0.86, 640), dh = dw * 0.6, x0 = (SW - dw) / 2, y0 = SH * 0.16;
    const s = sv('svg', { width: SW, height: SH, style: 'position:absolute;left:0;top:0;overflow:visible' }, palco);
    const doc = sv('rect', { x: x0, y: y0, width: dw, height: dh, fill: 'none', stroke: '#000', 'stroke-width': 3, pathLength: 1, 'stroke-dasharray': 1, 'stroke-dashoffset': 1 }, s);
    const fr = dh * 0.27, fx = x0 + dh * 0.42, fy = y0 + dh * 0.5;
    const rosto = sv('circle', { cx: fx, cy: fy, r: 4, fill: '#2b59ff' }, s);
    const barras = [0, 1, 2].map(i => sv('rect', { x: x0 + dh * 0.86, y: y0 + dh * (0.30 + i * 0.16), width: dw * [0.42, 0.34, 0.24][i], height: dh * 0.07, fill: '#000' }, s));
    const tri = sv('path', { d: 'M0 -1 L0.87 0.5 L-0.87 0.5 Z', fill: 'none', stroke: '#000', 'stroke-width': 0.06 }, s);
    const nome = el('div', mono(700, FS * 1.3, 'position:absolute;color:#000'), palco, 'Rafael');
    const itens = ['documento conferido', 'rosto conferido'].map(txt => el('div', mono(600, FS, 'position:absolute;color:#0a7a4b;white-space:nowrap'), palco, CHECK + ' ' + txt));
    const selo = el('div', mono(700, FS * 1.1, 'position:absolute;border:3px solid #0a7a4b;color:#0a7a4b;padding:.2em .6em;letter-spacing:.08em;background:#fff'), palco, 'VERIFICADO');
    return t => {
      const c = C.verificada;
      doc.setAttribute('stroke-dashoffset', String(1 - mov(t, c.inicio + 0.1, 0.9)));
      const g = mov(t, em('verificada', 0.12), 0.7);
      rosto.setAttribute('r', lerp(MIN * 0.02, fr, g));
      rosto.setAttribute('cx', lerp(SW * 0.9, fx, g)); rosto.setAttribute('cy', lerp(-20, fy, g));
      barras.forEach((b, i) => { const p = mov(t, em('verificada', 0.22 + i * 0.05), 0.5); b.setAttribute('transform', `translate(${(1 - p) * SW * 0.6} 0)`); b.setAttribute('opacity', p); });
      const pt = mov(t, em('verificada', 0.05), 0.9), tx = lerp(-80, x0 + dw - 26, pt), ty = lerp(SH * 0.8, y0 + 26, pt), rot = lerp(-160, 0, pt);
      tri.setAttribute('transform', `translate(${tx} ${ty}) rotate(${rot}) scale(${MIN * 0.03})`);
      nome.style.left = px(x0 + dh * 0.86); nome.style.top = px(y0 + dh * 0.72); op(nome, fad(t, em('verificada', 0.36), 0.4));
      itens.forEach((it, i) => { it.style.left = px(x0); it.style.top = px(y0 + dh + 30 + i * FS * 1.6); op(it, fad(t, em('verificada', 0.48 + i * 0.1), 0.35)); });
      const ts = em('verificada', 0.62), ps = RED ? (t >= ts ? 1 : 0) : E(span(t, ts, ts + 0.35));
      selo.style.left = px(Math.min(SW - (selo.offsetWidth || 260), x0 + dw - (selo.offsetWidth || 260) + 24)); selo.style.top = px(y0 - (selo.offsetHeight || 50) * 0.55);
      selo.style.transform = `rotate(-6deg) scale(${lerp(1.6, 1, ps)})`; op(selo, t >= ts ? Math.min(1, ps * 1.8) : 0);
    };
  });

  /* 05 · endereço: o endereço gigante tarjado; a chave vira no seu sim e a tarja se recolhe */
  capitulo('azul', palco => {
    const fs = caber('FLORES, 48', 700, 'normal', FMT === '9x16' ? 150 : 130, SW, -0.03);
    const end = el('div', mono(700, fs, 'position:absolute;left:0;top:' + SH * 0.1 + 'px;color:#fff;line-height:1;letter-spacing:-.03em;white-space:nowrap'), palco, 'RUA DAS<br>FLORES, 48');
    const tarja = el('div', 'position:absolute;background:#000;transform-origin:right center;display:flex;align-items:center;justify-content:flex-end;padding-right:.5em;overflow:hidden', palco, `<i style="${mono(300, FS * 1.1)};font-style:italic;color:#fff">oculto</i>`);
    const ch = el('div', 'position:absolute;color:#fff', palco); ch.className = 'chave';
    const bola = el('i', '', ch);
    const rot = el('div', mono(600, FS, 'position:absolute;color:#fff;white-space:nowrap'), palco, 'confirmar Rafael');
    const estado = el('div', mono(500, FS * 0.8, 'position:absolute;color:#fff;letter-spacing:.08em;text-transform:uppercase;white-space:nowrap'), palco);
    return t => {
      const c = C.endereco, tt = em('endereco', 0.42), tr = em('endereco', 0.50);
      const eh = (end.offsetHeight || fs * 2), ew = Math.min(SW, end.offsetWidth || SW);
      Object.assign(tarja.style, { left: px(-8), top: px(SH * 0.1 - 6), width: px(ew + 16), height: px(eh + 12) });
      tarja.style.transform = `scaleX(${RED ? (t >= tr ? 0 : 1) : 1 - E(span(t, tr, tr + 0.8))})`;
      op(end, fad(t, c.inicio, 0.3));
      const cw = FS * 4.2, chh = FS * 2.1, cy = SH * 0.1 + eh + FS * 2.4;
      Object.assign(ch.style, { left: px(0), top: px(cy), width: px(cw), height: px(chh) });
      const kk = RED ? (t >= tt ? 1 : 0) : E(span(t, tt, tt + 0.3));
      bola.style.left = px(5 + kk * (cw - chh - 4));
      ch.style.background = kk > 0.5 ? '#fff' : 'transparent'; bola.style.background = kk > 0.5 ? '#2b59ff' : '#fff';
      op(ch, fad(t, em('endereco', 0.15), 0.4));
      rot.style.left = px(cw + FS * 0.8); rot.style.top = px(cy + chh / 2 - FS * 0.65); op(rot, fad(t, em('endereco', 0.15), 0.4));
      estado.style.left = px(0); estado.style.top = px(cy + chh + FS * 0.9);
      estado.textContent = t >= tr ? 'agora só ele vê o endereço' : 'ninguém vê o endereço';
      op(estado, fad(t, em('endereco', 0.2), 0.4));
    };
  });

  /* 06 · a caminho: mapa em perspectiva 3D, a rota se desenha e o ponto vem até você */
  capitulo('escuro', palco => {
    const pw = MIN * 1.25, ph = MIN * 1.25;
    const cena = el('div', `position:absolute;left:${(SW - pw) / 2}px;top:${(SH - ph) / 2}px;width:${pw}px;height:${ph}px;perspective:${MIN * 2.2}px`, palco);
    const plano = el('div', 'position:absolute;inset:0;transform-origin:50% 55%', cena);
    const s = sv('svg', { width: pw, height: ph, style: 'position:absolute;left:0;top:0;overflow:visible' }, plano);
    const passo = pw / 9;
    for (let i = 0; i <= 9; i++) {
      const forte = i === 3 || i === 6, cor = forte ? 'rgba(255,255,255,.42)' : 'rgba(255,255,255,.17)', lw = forte ? 3 : 1.5;
      sv('line', { x1: 0, y1: i * passo, x2: pw, y2: i * passo, stroke: cor, 'stroke-width': lw }, s);
      sv('line', { x1: i * passo, y1: 0, x2: i * passo, y2: ph, stroke: cor, 'stroke-width': lw }, s);
    }
    const A = { x: 7 * passo, y: 1 * passo }, B = { x: 7 * passo, y: 6 * passo }, Cc = { x: 3 * passo, y: 6 * passo };
    const rota = sv('path', { d: `M${A.x} ${A.y} L${B.x} ${B.y} L${Cc.x} ${Cc.y}`, fill: 'none', stroke: '#2b59ff', 'stroke-width': 7, 'stroke-linecap': 'square', pathLength: 1, 'stroke-dasharray': 1, 'stroke-dashoffset': 1 }, s);
    sv('rect', { x: Cc.x - 14, y: Cc.y - 14, width: 28, height: 28, fill: '#fff' }, s);
    const pino = sv('circle', { r: 13, fill: '#8aa3ff', stroke: '#000', 'stroke-width': 4 }, s);
    const rv = sv('text', { x: Cc.x, y: Cc.y + 54, fill: '#fff', 'font-family': 'Spline Sans Mono', 'font-size': FS * 1.2, 'text-anchor': 'middle' }, s); rv.textContent = 'você';
    const rr = sv('text', { 'font-family': 'Spline Sans Mono', 'font-size': FS * 1.2, fill: '#8aa3ff' }, s); rr.textContent = 'Rafael';
    const l1 = Math.abs(B.y - A.y), l2 = Math.abs(B.x - Cc.x);
    return t => {
      const c = C.mapa;
      const rx = RED ? 52 : lerp(20, 56, mov(t, c.inicio, 1.2)), rz = RED ? -14 : lerp(0, -14, mov(t, c.inicio, 1.2)) - (t - c.inicio) * 1.2;
      plano.style.transform = `rotateX(${rx}deg) rotateZ(${rz}deg)`;
      const p = RED ? 0.8 : 0.85 * EIO(span(t, c.inicio + 0.3, c.fim));
      rota.setAttribute('stroke-dashoffset', String(1 - p));
      const d = p * (l1 + l2); let x = A.x, y = A.y + Math.min(d, l1); if (d > l1) { y = B.y; x = B.x - (d - l1); }
      pino.setAttribute('cx', x); pino.setAttribute('cy', y);
      rr.setAttribute('x', x + 22); rr.setAttribute('y', y - 18);
      op(cena, fad(t, c.inicio, 0.3));
    };
  });

  /* 07 · código: painel com contadores; os dígitos rolam e travam em 4-8-2-1 */
  capitulo('claro', palco => {
    const lado = FMT === '16x9', cw = lado ? SW * 0.46 : SW * 0.86, dfs = Math.min(FMT === '1x1' ? 90 : 140, cw / 4.6);
    const mk = (rotulo, x, y) => el('div', `position:absolute;left:${x}px;top:${y}px;width:${cw}px;border:3px solid #000;padding:${FS * 0.8}px;background:#fff`, palco, `<div style="${mono(500, FS * 0.8)};letter-spacing:.08em;text-transform:uppercase;color:#5a5d68">${rotulo}</div>`);
    const y1 = SH * 0.08, y2 = lado ? y1 : y1 + dfs * 1.25 + FS * 3.2;
    const cA = mk('no seu celular', 0, y1), cB = mk('Rafael digita', lado ? SW - cw : 0, y2);
    el('div', mono(700, dfs, 'display:flex;gap:.15em;line-height:1.1;margin-top:.15em;color:#000'), cA, '4821'.split('').map(d => `<span style="display:inline-block;width:.75em;text-align:center;border-bottom:3px solid #000">${d}</span>`).join(''));
    const colsB = el('div', mono(700, dfs, 'display:flex;gap:.15em;margin-top:.15em;color:#000'), cB);
    const cols = '4821'.split('').map(() => { const w = el('span', `display:inline-block;width:.75em;height:${dfs * 1.1}px;overflow:hidden;border-bottom:3px solid #000;position:relative`, colsB); return el('span', `position:absolute;left:0;right:0;top:0;text-align:center;line-height:${dfs * 1.1}px`, w, Array.from({ length: 80 }, (_, i) => i % 10).join('<br>')); });
    const bateu = el('div', mono(700, FS * 1.4, 'position:absolute;color:#0a7a4b;white-space:nowrap'), palco, CHECK + ' bateu: é ele');
    return t => {
      const c = C.codigo, t0 = em('codigo', 0.28), spd = 16;
      op(cA, fad(t, c.inicio + 0.05, 0.3)); op(cB, fad(t, c.inicio + 0.15, 0.3));
      cA.style.transform = `translateY(${(1 - mov(t, c.inicio + 0.05, 0.5)) * 30}px)`; cB.style.transform = `translateY(${(1 - mov(t, c.inicio + 0.15, 0.5)) * 30}px)`;
      cols.forEach((fita, kk) => {
        const tk = em('codigo', 0.48 + kk * 0.08), alvo = +'4821'[kk], ini = tk - 0.25;
        const raw = tt => Math.max(0, (tt - t0) * spd + kk * 3);
        const F = 10 * Math.ceil(raw(ini) / 10 + 1) + alvo;
        let pos = t < t0 ? 0 : t < ini ? raw(t) : t < tk ? lerp(raw(ini), F, E(span(t, ini, tk))) : F;
        if (RED) pos = t >= tk ? alvo : 0;
        fita.style.transform = `translateY(${-pos * dfs * 1.1}px)`;
        fita.style.opacity = t < t0 && !RED ? '0.25' : '1';
      });
      const ok = t >= em('codigo', 0.82);
      for (const e of [cA, cB]) e.style.borderColor = ok ? '#0a7a4b' : '#000';
      bateu.style.left = px(0); bateu.style.top = px(y2 + (cB.offsetHeight || 200) + FS * 1.1);
      op(bateu, fad(t, em('codigo', 0.82), 0.3)); bateu.style.transform = `translateY(${(1 - mov(t, em('codigo', 0.82), 0.4)) * 16}px)`;
    };
  });

  /* 08 · pagamento: a chave "serviço concluído" liga; o valor sobe até R$ 120 e fica pago */
  capitulo('azul', palco => {
    const ch = el('div', 'position:absolute;color:#fff', palco); ch.className = 'chave';
    const bola = el('i', '', ch);
    const rot = el('div', mono(600, FS, 'position:absolute;color:#fff;white-space:nowrap'), palco, 'serviço concluído');
    const vfs = Math.min(FMT === '9x16' ? 200 : 170, SW / 4.4);
    const valor = el('div', mono(700, vfs, 'position:absolute;color:#fff;letter-spacing:-.04em;font-variant-numeric:tabular-nums;white-space:nowrap'), palco);
    const pago = el('div', mono(600, FS * 1.0, 'position:absolute;color:#fff;max-width:' + SW + 'px'), palco, CHECK + ' pago depois do serviço,<br>direto ao Rafael');
    return t => {
      const c = C.paga, tt = em('paga', 0.22), tv = em('paga', 0.30), tp = em('paga', 0.55);
      const cw = FS * 4.2, chh = FS * 2.1, y0 = SH * 0.08;
      Object.assign(ch.style, { left: px(0), top: px(y0), width: px(cw), height: px(chh) });
      const kk = RED ? (t >= tt ? 1 : 0) : E(span(t, tt, tt + 0.3));
      bola.style.left = px(5 + kk * (cw - chh - 4)); ch.style.background = kk > 0.5 ? '#fff' : 'transparent'; bola.style.background = kk > 0.5 ? '#2b59ff' : '#fff';
      op(ch, fad(t, c.inicio, 0.3)); rot.style.left = px(cw + FS * 0.8); rot.style.top = px(y0 + chh / 2 - FS * 0.65); op(rot, fad(t, c.inicio, 0.3));
      const v = RED ? (t >= tp ? 120 : 0) : Math.round(120 * E(span(t, tv, tp)));
      valor.textContent = 'R$ ' + v; valor.style.left = px(0); valor.style.top = px(y0 + chh + FS * 1.2);
      op(valor, fad(t, tv, 0.3));
      pago.style.left = px(0); pago.style.top = px(y0 + chh + FS * 1.2 + vfs * 1.15); op(pago, fad(t, tp, 0.35));
    };
  });

  /* 09 · chegada: bolhas líquidas; longe, o ponto não se junta à sua casa; dentro de 150 m, funde */
  capitulo('escuro', palco => {
    const ESCALA = 3, cw = Math.ceil(SW / ESCALA), chh = Math.ceil(SH / ESCALA);
    const cv = el('canvas', `width:${SW}px;height:${SH}px;left:0;top:0`, palco); cv.width = cw; cv.height = chh;
    const ctx = cv.getContext('2d'), img = ctx.createImageData(cw, chh);
    const s = sv('svg', { width: SW, height: SH, style: 'position:absolute;left:0;top:0;overflow:visible' }, palco);
    const HX = SW * (FMT === '16x9' ? 0.34 : 0.32), HY = SH * 0.58, RH = MIN * 0.12, R150 = RH * 2.3;
    const anel = sv('circle', { cx: HX, cy: HY, r: R150, fill: 'none', stroke: '#a3a6b1', 'stroke-width': 2, 'stroke-dasharray': '8 8' }, s);
    const LONGE = { x: SW * 0.84, y: SH * 0.2 }, PERTO = { x: HX + RH * 1.25, y: HY - RH * 0.75 };
    const linha = sv('line', { x1: HX, y1: HY, x2: LONGE.x, y2: LONGE.y, stroke: '#a3a6b1', 'stroke-width': 2, 'stroke-dasharray': '4 8' }, s);
    const raio = el('div', mono(500, FS * 0.8, 'position:absolute;color:#a3a6b1;white-space:nowrap'), palco, 'raio de 150 m');
    const dist = el('div', mono(500, FS * 0.9, 'position:absolute;color:#a3a6b1;white-space:nowrap'), palco, '1,1 km');
    const cheguei = el('div', `font-size:${FS}px;background:#000`, palco, '<div style="font-weight:600">Cheguei!</div>'); cheguei.className = 'cartao';
    const nao = el('div', mono(700, FS * 1.1, 'position:absolute;color:#ff7d82;white-space:nowrap'), palco, XIS + ' longe: não vale');
    const vale = el('div', mono(700, FS * 1.1, 'position:absolute;color:#4be0a0;white-space:nowrap'), palco, CHECK + ' 80 m: vale');
    const voce = el('div', mono(600, FS * 0.8, 'position:absolute;color:#000;white-space:nowrap'), palco, 'você');
    return t => {
      const c = C.virada;
      const m2 = RED ? (t >= em('virada', 0.58) ? 1 : 0) : E(span(t, em('virada', 0.58), em('virada', 0.72)));
      const PX = lerp(LONGE.x, PERTO.x, m2), PY = lerp(LONGE.y, PERTO.y, m2), RP = RH * 0.6;
      const entra = fad(t, c.inicio, 0.4);
      const d = img.data, hx = HX / ESCALA, hy = HY / ESCALA, qx = PX / ESCALA, qy = PY / ESCALA, rh = RH / ESCALA, rp = RP / ESCALA;
      for (let yy = 0, i = 0; yy < chh; yy++) for (let xx = 0; xx < cw; xx++, i += 4) {
        const a1 = rh * rh / ((xx - hx) ** 2 + (yy - hy) ** 2 + 1e-3), a2 = rp * rp / ((xx - qx) ** 2 + (yy - qy) ** 2 + 1e-3), f = a1 + a2;
        const al = clamp((f - 0.9) / 0.2) * entra, w = a2 / f;
        d[i] = lerp(255, 43, w); d[i + 1] = lerp(255, 89, w); d[i + 2] = 255; d[i + 3] = al * 255;
      }
      ctx.putImageData(img, 0, 0);
      op(anel, entra);
      raio.style.left = px(HX - (raio.offsetWidth || 160) / 2); raio.style.top = px(HY + R150 + 10); op(raio, entra);
      voce.style.left = px(HX - (voce.offsetWidth || 50) / 2); voce.style.top = px(HY - FS * 0.5); op(voce, entra);
      const longe = t < em('virada', 0.58) ? 1 : sai(t, em('virada', 0.56), 0.25);
      op(linha, entra * longe * fad(t, c.inicio + 0.2, 0.4));
      dist.style.left = px((HX + LONGE.x) / 2 + 12); dist.style.top = px((HY + LONGE.y) / 2 + 8); op(dist, entra * longe);
      cheguei.style.left = px(Math.min(LONGE.x - 60, SW - (cheguei.offsetWidth || 160))); cheguei.style.top = px(LONGE.y - RP - (cheguei.offsetHeight || 70) - 16);
      op(cheguei, fad(t, em('virada', 0.08), 0.3) * longe);
      nao.style.left = px(Math.max(0, Math.min(LONGE.x - (nao.offsetWidth || 300) / 2, SW - (nao.offsetWidth || 300)))); nao.style.top = px(LONGE.y + RP + 16);
      op(nao, fad(t, em('virada', 0.18), 0.3) * longe);
      vale.style.left = px(Math.max(0, HX - (vale.offsetWidth || 260) / 2)); vale.style.top = px(HY - R150 - FS * 2.2);
      op(vale, fad(t, em('virada', 0.72), 0.35));
    };
  });

  /* 10 · conferido: faixas de "conferido" deslizando, depois TaskGO. com o ponto azul */
  capitulo('escuro', (palco, raiz) => {
    const nf = FMT === '9x16' ? 10 : 6, fh = H / nf, ffs = fh * 0.78;
    const faixas = Array.from({ length: nf }, (_, i) => {
      const f = el('div', `position:absolute;left:0;top:${i * fh}px;width:${W}px;height:${fh}px;overflow:hidden;background:${i % 2 ? '#000' : '#2b59ff'}`, raiz);
      const txt = i % 2 ? 'conferido · ' : 'CONFERIDO · ';
      const fita = el('div', mono(i % 2 ? 300 : 700, ffs, `position:absolute;top:${(fh - ffs) / 2 - ffs * 0.08}px;left:0;white-space:nowrap;color:#fff;line-height:1;${i % 2 ? 'font-style:italic;' : ''}letter-spacing:-.03em`), f, txt.repeat(8));
      return { f, fita, i, dir: i % 2 ? 1 : -1, vel: [0.7, 0.85, 1.0][i % 3] };
    });
    const marca = el('div', mono(400, Math.min(FMT === '9x16' ? 190 : 170, SW / 3.6), 'position:absolute;left:0;color:#fff;letter-spacing:-.04em;white-space:nowrap'), palco, 'Task<b style="font-weight:700;color:#8aa3ff">GO</b><span class="pt" style="display:inline-block;width:.22em;height:.22em;border-radius:50%;background:#2b59ff;margin-left:.04em"></span>');
    const pt = marca.querySelector('.pt');
    const PASSOS = ['pedido', 'perto', 'identidade', 'endereço', 'a caminho', 'código', 'pagamento'];
    const lista = el('div', mono(500, FS * 0.85, 'position:absolute;left:0;color:#4be0a0;display:grid;grid-template-columns:repeat(2,auto);gap:.35em 2em;width:fit-content'), palco, PASSOS.map(p => `<span>${CHECK} ${p}</span>`).join(''));
    const itens = [...lista.children];
    const site = el('div', mono(500, FS * 0.85, 'position:absolute;left:0;color:#a3a6b1'), palco, 'taskgohq.com.br');
    return t => {
      const c = C.final, corte = C.final.fala.inicio + 0.8; /* a marca entra quando a voz diz "TaskGO" */
      const listras = t < corte;
      for (const x of faixas) {
        x.f.style.display = listras ? 'block' : 'none';
        const w = x.fita.scrollWidth / 8;
        /* Entrada: cor sólida varre a faixa (cada ponto muda uma vez só). Depois o texto desliza devagar:
           no máximo ~0,8 da altura da letra por segundo, para cada ponto alternar menos de 3 vezes por segundo (WCAG). */
        const pe = mov(t, c.inicio + x.i * 0.05, 0.5);
        x.f.style.clipPath = x.dir > 0 ? `inset(0 ${(1 - pe) * 100}% 0 0)` : `inset(0 0 0 ${(1 - pe) * 100}%)`;
        const off = RED ? 0 : ((t - c.inicio) * ffs * 0.75 * x.vel) % w;
        x.fita.style.transform = `translateX(${x.dir > 0 ? off - w : -off}px)`;
      }
      palco.style.display = listras ? 'none' : 'block';
      const pm = mov(t, corte, 0.7);
      marca.style.top = px(SH * 0.06); marca.style.transform = `translateY(${(1 - pm) * 40}px)`; op(marca, fad(t, corte, 0.4));
      const pp = RED ? (t >= corte + 0.3 ? 1 : 0) : E(span(t, corte + 0.3, corte + 0.75));
      pt.style.transform = `scale(${pp})`;
      lista.style.top = px(SH * 0.06 + (marca.offsetHeight || 180) + FS * 1.2);
      itens.forEach((it, i) => op(it, fad(t, corte + 0.6 + i * 0.12, 0.25)));
      site.style.top = px(SH * 0.06 + (marca.offsetHeight || 180) + FS * 1.2 + (lista.offsetHeight || 140) + FS * 1.4); op(site, fad(t, corte + 2.0, 0.4));
    };
  });

  /* parâmetros do painel em cada capítulo (só dados com fonte, ver SOURCES.md) */
  const PARAMS = [
    [['canal', 'grupo de conversa'], ['garantia', 'nenhuma']],
    [['serviço', 'elétrica'], ['valor', 'R$ 120 · exemplo']],
    [['avisados', '10 por onda'], ['busca', 'até 40 km']],
    [['documento', 'conferido'], ['rosto', 'conferido']],
    [['endereço', t => t >= em('endereco', 0.5) ? 'liberado' : 'oculto'], ['quem vê', 'quem você confirma']],
    [['chegada', 'no mapa'], ['posição', 'GPS do celular']],
    [['código', '4 números'], ['onde', 'na porta']],
    [['pagamento', 'depois do serviço'], ['para quem', 'direto ao profissional']],
    [['raio da chegada', '150 m'], ['distância', t => t >= em('virada', 0.66) ? '80 m' : '1,1 km']],
    [['passos', t => t >= C.final.fala.inicio + 2.4 ? '7 de 7 conferidos' : 'conferindo']]
  ];

  /* ---------- quadro ---------- */
  const tc = t => { const f = Math.floor(t * 60 + 1e-6), s = Math.floor(f / 60); return `00:00:${String(s).padStart(2, '0')}:${String(f % 60).padStart(2, '0')}`; };
  function seek(t) {
    t = Math.max(0, Math.min(T.total, t));
    let ci = T.cenas.findIndex(c => t >= c.inicio && t < c.fim); if (ci < 0) ci = T.cenas.length - 1;
    const cena = T.cenas[ci], cap = caps[ci], ultima = ci === T.cenas.length - 1, listras = ultima && t < C.final.fala.inicio + 0.8;
    filme.className = cap.fundo;
    filme.style.backgroundImage = cap.fundo === 'azul' ? GRAO : 'none';
    caps.forEach((c2, j) => { c2.raiz.style.display = j === ci ? 'block' : 'none'; c2.raiz.style.visibility = 'visible'; });
    cap.up(t);

    /* título: entra subindo do recorte; sai pelo alto no fim do capítulo */
    tl[0].textContent = cena.ancora[0]; tl[1].textContent = cena.ancora[1];
    const f1 = caber(cena.ancora[0], 300, 'italic', L.tit.fs1, L.tit.w, -0.02);
    const f2 = caber(cena.ancora[1], 700, 'normal', L.tit.max2, L.tit.w, -0.045);
    tl[0].style.fontSize = px(f1); tl[1].style.fontSize = px(f2);
    titulo.style.top = px(L.tit.base - f1 * 1.05 - f2 * 1.04);
    [[0, cena.inicio + 0.05], [1, cena.id === 'pergunta' ? cena.fala.inicio + cena.fala.dur * 0.45 : cena.inicio + 0.16]].forEach(([kk, a]) => {
      const a2 = ultima ? C.final.fala.inicio + 0.86 + kk * 0.1 : a;
      const p = mov(t, a2, 0.55), s = ultima ? 0 : mov(t, cena.fim - 0.24, 0.22);
      if (RED) { tl[kk].style.transform = 'none'; op(tl[kk], (listras ? 0 : 1) * fad(t, a2, 0.3)); }
      else { tl[kk].style.transform = `translateY(${(1 - p) * 110 - s * 110}%)`; op(tl[kk], listras ? 0 : 1); }
    });

    /* painel */
    hTempo.textContent = tc(t);
    hCap.textContent = String(ci + 1).padStart(2, '0') + '/10 · ' + NOMES[ci];
    reguaFill.style.width = (t / T.total * 100) + '%';
    const batida = 60 / 96, b0 = C.pedido.inicio, ativa = t >= b0 && t < C.virada.inicio && !RED;
    const fr = ativa ? ((t - b0) % batida) / batida : 1;
    bolaBpm.style.transform = `scale(${ativa ? 1 + 0.6 * (1 - E(clamp(fr * 2.5))) : 1})`;
    param.innerHTML = PARAMS[ci].map(([kk, v]) => `<span>${kk}</span><b>${typeof v === 'function' ? v(t) : v}</b>`).join('');
    op(param, fad(t, cena.inicio + 0.1, 0.3) * (listras ? 0 : 1));
    hud.style.color = listras ? '#fff' : '';

    /* legenda */
    if (LEG) { const lg = window.LEGENDAS.find(x => t >= x.inicio && t < x.fim + 0.15); legSpan.textContent = lg ? lg.texto : ''; op(legenda, lg ? 1 : 0); } else op(legenda, 0);
    return t;
  }
  window.seek = seek;
  window.PRONTO = document.fonts.ready
    .then(() => Promise.all([document.fonts.load('700 100px "Spline Sans Mono"'), document.fonts.load('italic 300 100px "Spline Sans Mono"'), document.fonts.load('500 30px "Spline Sans"')]))
    .then(() => {
      /* sem a fonte certa o título é medido errado e sai cortado: melhor parar o render do que gravar o quadro errado */
      const faltam = ['700 100px "Spline Sans Mono"', 'italic 300 100px "Spline Sans Mono"', '500 30px "Spline Sans"'].filter(f => !document.fonts.check(f));
      if (faltam.length) throw new Error('Fonte não carregou: ' + faltam.join(', '));
      seek(0); return { W, H, total: T.total, quadros: T.quadros, fmt: FMT, reduzido: RED }; });
})();
