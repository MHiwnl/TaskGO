/* Filme da TaskGO. window.seek(t) pinta o quadro no tempo t (segundos). Cada quadro é função pura de t:
   nenhum estado passa de um quadro para o outro, e a aleatoriedade usa semente fixa.
   Parâmetros: ?fmt=16x9|9x16|1x1  &reduzido=1 (movimento reduzido)  &legenda=0 (sem legenda gravada) */
(function () {
  const P = new URLSearchParams(location.search);
  const FMT = P.get('fmt') || '16x9', RED = P.get('reduzido') === '1', LEG = P.get('legenda') !== '0';
  const [W, H] = { '16x9': [1920, 1080], '9x16': [1080, 1920], '1x1': [1080, 1080] }[FMT];
  const T = FILME.montarTimeline(window.DURACOES); window.TIMELINE = T;
  const C = T.C, em = (id, f) => C[id].inicio + C[id].dur * f;

  /* Composição por formato: âncora em cima; pedido e palco abaixo (lado a lado no 16:9 e no 1:1, empilhados no 9:16). */
  const L = {
    '16x9': { ancora: { x: 120, y: 84, fs: 70 }, pedido: { x: 120, y: 318, w: 700, fs: 28, row: 60 }, palco: { x: 900, y: 300, w: 900, h: 620 }, legenda: { x: 120, b: 44, fs: 34, chars: 64 }, balaoFs: 44, rotFs: 22, cartaoFs: 30 },
    '9x16': { ancora: { x: 72, y: 120, fs: 74 }, pedido: { x: 72, y: 1110, w: 936, fs: 40, row: 64 }, palco: { x: 72, y: 340, w: 936, h: 740 }, legenda: { x: 72, b: 64, fs: 44, chars: 32 }, balaoFs: 62, rotFs: 34, cartaoFs: 46 },
    '1x1': { ancora: { x: 64, y: 52, fs: 54 }, pedido: { x: 64, y: 224, w: 450, fs: 24, row: 50 }, palco: { x: 540, y: 216, w: 476, h: 660 }, legenda: { x: 64, b: 36, fs: 30, chars: 50 }, balaoFs: 36, rotFs: 21, cartaoFs: 26 }
  }[FMT];
  const PW = L.palco.w, PH = L.palco.h, U = Math.min(PW, PH) * 0.42;

  /* ---------- matemática ---------- */
  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const span = (t, a, b) => clamp((t - a) / (b - a));
  const lerp = (a, b, p) => a + (b - a) * p;
  function bezier(x1, y1, x2, y2) {
    const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx, cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
    const sx = u => ((ax * u + bx) * u + cx) * u, sy = u => ((ay * u + by) * u + cy) * u, dx = u => (3 * ax * u + 2 * bx) * u + cx;
    return x => { if (x <= 0) return 0; if (x >= 1) return 1; let u = x; for (let i = 0; i < 10; i++) { const d = sx(u) - x, dd = dx(u); if (Math.abs(d) < 1e-7 || Math.abs(dd) < 1e-7) break; u = clamp(u - d / dd); } return sy(u); };
  }
  const E = bezier(.16, 1, .3, 1);           /* curva do site (motion.css): assenta sem quicar */
  const EIO = bezier(.65, 0, .35, 1);        /* câmera: começa e termina devagar */
  /* Movimento e opacidade. No movimento reduzido nada se desloca: o estado final entra num esmaecer curto. */
  const mov = (t, a, d) => RED ? (t >= a ? 1 : 0) : E(span(t, a, a + d));
  const fad = (t, a, d) => RED ? span(t, a, a + 0.3) : E(span(t, a, a + d));
  const sai = (t, a, d) => 1 - fad(t, a, d);
  function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let x = Math.imul(seed ^ seed >>> 15, 1 | seed); x = x + Math.imul(x ^ x >>> 7, 61 | x) ^ x; return ((x ^ x >>> 14) >>> 0) / 4294967296; }; }

  /* ---------- DOM ---------- */
  const $ = s => document.querySelector(s);
  const filme = $('#filme'); filme.style.width = W + 'px'; filme.style.height = H + 'px';
  const el = (tag, cls, pai, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; (pai || filme).appendChild(e); return e; };
  const px = v => v + 'px';
  const pos = (e, x, y) => { e.style.left = px(x); e.style.top = px(y); };
  const CHECK = '<svg class="check" viewBox="0 0 24 24"><path d="M4 12.5l5 5L20 6.5" pathLength="1" stroke-dasharray="1" stroke-dashoffset="0"/></svg>';
  const XIS = '<svg class="check xis" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>';
  const desenhaCheck = (raiz, p) => raiz.querySelectorAll('svg.check:not(.xis) path').forEach(c => c.setAttribute('stroke-dashoffset', String(1 - p)));

  /* âncora */
  const ancora = $('#ancora'); pos(ancora, L.ancora.x, L.ancora.y); ancora.style.fontSize = px(L.ancora.fs);
  const al = [...ancora.querySelectorAll('.l > span')];

  /* palco */
  const palco = $('#palco'); pos(palco, L.palco.x, L.palco.y); palco.style.width = px(PW); palco.style.height = px(PH);
  const rotulos = $('#rotulos'); rotulos.style.fontSize = px(L.rotFs);
  const NS = 'http://www.w3.org/2000/svg', cam = $('#cam');
  const sv = (tag, attrs, pai) => { const e = document.createElementNS(NS, tag); for (const k in attrs) e.setAttribute(k, attrs[k]); (pai || cam).appendChild(e); return e; };

  /* mundo: a casa (você) na origem; U = raio do radar */
  const r = rng(418);
  const ruas = sv('g', { opacity: 0 });
  for (let k = -7; k <= 7; k++) {
    const d = k * 0.32 * U;
    sv('line', { x1: -3 * U, y1: d, x2: 3 * U, y2: d, stroke: 'rgba(22,19,15,.16)', 'stroke-width': k === 0 ? 2 : 1, 'vector-effect': 'non-scaling-stroke' }, ruas);
    sv('line', { x1: d, y1: -3 * U, x2: d, y2: 3 * U, stroke: 'rgba(22,19,15,.16)', 'stroke-width': k === 2 ? 2 : 1, 'vector-effect': 'non-scaling-stroke' }, ruas);
  }
  const aneis = [0, 1, 2].map(() => sv('circle', { cx: 0, cy: 0, r: 0, fill: 'none', stroke: '#2b59ff', 'stroke-width': 2, 'vector-effect': 'non-scaling-stroke', opacity: 0 }));
  const circ150 = sv('circle', { cx: 0, cy: 0, r: 0.22 * U, fill: 'rgba(43,89,255,.06)', stroke: '#2b59ff', 'stroke-width': 2, 'stroke-dasharray': '7 6', 'vector-effect': 'non-scaling-stroke', opacity: 0 });
  /* Rota pelas ruas: do Rafael até a casa. */
  const RAF = { x: 0.64 * U, y: -0.64 * U };
  const rota = sv('path', { d: `M${RAF.x} ${RAF.y} L${RAF.x} 0 L${0.06 * U} 0`, fill: 'none', stroke: '#2b59ff', 'stroke-width': 4, 'stroke-linecap': 'square', 'vector-effect': 'non-scaling-stroke', pathLength: 1, 'stroke-dasharray': 1, 'stroke-dashoffset': 1, opacity: 0 });
  const distLinha = sv('line', { x1: 0, y1: 0, x2: 0, y2: 0, stroke: '#4f4940', 'stroke-width': 1.5, 'stroke-dasharray': '4 6', 'vector-effect': 'non-scaling-stroke', opacity: 0 });
  /* Profissionais: 10 disponíveis perto (a 1ª onda), outros disponíveis mais longe e alguns indisponíveis. */
  const pros = [];
  const longeDe = (x, y) => pros.every(p => Math.hypot(p.x - x, p.y - y) > 0.13 * U) && Math.hypot(x, y) > 0.16 * U;
  const sorteia = (n, dmin, dmax, sy, on) => { for (let k = 0, tent = 0; k < n && tent < 4000; tent++) { const a = r() * Math.PI * 2, d = (dmin + (dmax - dmin) * r()) * U, x = Math.cos(a) * d, y = Math.sin(a) * d * sy; if (longeDe(x, y)) { pros.push({ x, y, on }); k++; } } };
  pros.push({ x: RAF.x, y: RAF.y, on: true, raf: true });   /* o Rafael: um dos 10 da 1ª onda */
  sorteia(9, 0.26, 0.86, 1, true);                          /* os outros 9 disponíveis perto */
  sorteia(7, 1.04, 1.34, 0.8, true);                        /* disponíveis mais longe */
  sorteia(8, 0.36, 1.25, 0.85, false);                      /* indisponíveis */
  /* Rafael é um dos 10 da 1ª onda (o 4º mais perto), numa esquina: assim a rota segue as ruas. */
  const perto = pros.filter(p => p.on).sort((a, b) => Math.hypot(a.x, a.y) - Math.hypot(b.x, b.y)).slice(0, 10);
  perto.forEach((p, k) => p.ordem = k);
  const rafael = pros.find(p => p.raf);
  for (const p of pros) p.e = sv('circle', { cx: p.x, cy: p.y, r: 0.024 * U, fill: p.on ? '#16130f' : '#f2ede4', stroke: p.on ? 'none' : '#4f4940', 'stroke-width': 1.5, 'vector-effect': 'non-scaling-stroke', opacity: 0 });
  const casa = sv('rect', { x: -0.036 * U, y: -0.036 * U, width: 0.072 * U, height: 0.072 * U, fill: '#16130f', opacity: 0 });
  const pino = sv('circle', { cx: 0, cy: 0, r: 0.034 * U, fill: '#2b59ff', stroke: '#f2ede4', 'stroke-width': 2, 'vector-effect': 'non-scaling-stroke', opacity: 0 });

  /* rótulos do palco (HTML, posicionados pela câmera) */
  const rot = (html, cls) => el('div', 'rot ' + (cls || ''), rotulos, html);
  const rVoce = rot('você'), rLegenda = rot('<b style="color:#16130f">●</b> disponível&nbsp;&nbsp;&nbsp;○ indisponível', 'sm');
  const rOnda = rot('<b style="color:#2b59ff">●</b> avisados: os 10 mais perto', 'sm');
  const rCaminho = rot('a caminho', 'sm'), rChegou = rot(CHECK + '<span>chegada conferida:<br>dentro de 150 m</span>', 'ok');
  const rRaio = rot('raio de 150 m');
  const rDist = rot('1,1 km', 'sm'), rLonge = rot(XIS + '<span>longe:<br>não vale</span>'), rVale = rot(CHECK + ' 80 m: vale', 'ok');
  rLonge.style.color = 'var(--vermelho-texto)';
  rChegou.style.color = 'var(--verde-texto)';
  for (const e of [rChegou, rLonge, rVale, rRaio]) { e.style.display = 'flex'; e.style.alignItems = 'center'; e.style.gap = '.35em'; e.style.fontWeight = '600'; }

  /* balões da pergunta (e da volta, na recompensa) */
  const fsB = L.balaoFs;
  const bPalavra = el('div', 'balao', null, '<div class="de">contato desconhecido</div><div class="msg">Pode deixar, chego em 10 min.</div><div class="meta"><span class="hora">19:02</span><span class="nota"></span></div>');
  bPalavra.style.fontSize = px(fsB); bPalavra.style.maxWidth = px(FMT === '9x16' ? 900 : FMT === '1x1' ? 600 : 760);
  const bSorte = el('div', 'balao', palco, '<div class="de">grupo do condomínio</div><div class="msg">Alguém conhece um eletricista?</div><div class="meta"><span>visto por todos · sem resposta</span></div>');
  bSorte.style.fontSize = px(fsB * 0.72); bSorte.style.maxWidth = px(PW * 0.92);
  const bVolta = el('div', 'balao', palco, '<div class="de">Rafael</div><div class="msg">Pode deixar, chego em 10 min.</div><div class="meta ok" style="color:var(--verde-texto);font-weight:700">' + CHECK + '<span>chegada conferida no mapa</span></div>');
  bVolta.style.fontSize = px(fsB * (FMT === '1x1' ? 0.72 : 0.86)); bVolta.style.maxWidth = px(PW * 0.95);

  /* Rafael: o ponto vira avatar e cartão */
  const fsC = L.cartaoFs;
  const avatar = el('div', '', palco); Object.assign(avatar.style, { position: 'absolute', borderRadius: '50%', background: 'var(--azul)', color: '#fff', fontFamily: 'var(--mono)', fontWeight: 700, display: 'grid', placeItems: 'center' });
  avatar.innerHTML = '<span>R</span>';
  const cartao = el('div', 'cartao', palco, '<div class="nome">Rafael</div><div class="selo">' + CHECK + 'verificado</div><div class="item i1">' + CHECK + '<span>documento conferido</span></div><div class="item i2">' + CHECK + '<span>selfie conferida</span></div>');
  cartao.style.fontSize = px(fsC);
  const botao = el('div', 'botao', palco, 'confirmar Rafael'); botao.style.fontSize = px(fsC * 0.82);
  const rVeEnd = rot('agora ele vê o endereço', 'sm');

  /* código de chegada */
  const pVoce = el('div', 'painel', palco, '<div class="t">no seu celular</div><div class="digitos"><span>4</span><span>8</span><span>2</span><span>1</span></div>');
  const pRaf = el('div', 'painel', palco, '<div class="t">Rafael digita</div><div class="digitos"><span></span><span></span><span></span><span></span></div>');
  const rConfere = rot(CHECK + ' confere', 'ok'); Object.assign(rConfere.style, { display: 'flex', alignItems: 'center', gap: '.35em', fontWeight: '700' });
  for (const p of [pVoce, pRaf]) { p.style.fontSize = px(fsC * 1.5); }

  /* Pix */
  const pix = el('div', 'painel', palco); pix.style.fontSize = px(fsC);
  const q = rng(120), N = 25, cel = Math.round((FMT === '1x1' ? 200 : 260) / N);
  let qr = '<svg width="' + cel * N + '" height="' + cel * N + '" viewBox="0 0 ' + N + ' ' + N + '" shape-rendering="crispEdges" style="display:block;background:#fff;padding:0;outline:' + cel + 'px solid #fff"><g fill="#16130f">';
  const finder = (x, y) => { qr += `<rect x="${x}" y="${y}" width="7" height="7"/><rect x="${x + 1}" y="${y + 1}" width="5" height="5" fill="#fff"/><rect x="${x + 2}" y="${y + 2}" width="3" height="3"/>`; };
  finder(0, 0); finder(N - 7, 0); finder(0, N - 7);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) { const naFinder = (x < 8 && y < 8) || (x > N - 9 && y < 8) || (x < 8 && y > N - 9); if (!naFinder && q() < 0.47) qr += `<rect x="${x}" y="${y}" width="1" height="1"/>`; }
  qr += '</g></svg>';
  pix.innerHTML = '<div class="t">Pix para Rafael · exemplo</div><div style="display:flex;gap:1em;align-items:center;margin-top:.5em">' + qr + '<div><div style="font-family:var(--mono);font-weight:700;font-size:1.6em">R$ 120</div><div class="pst" style="font-family:var(--mono);font-size:.8em;margin-top:.4em"></div></div></div>';
  const pst = pix.querySelector('.pst');

  /* recompensa: carimbo e marca */
  const carimbo = el('div', 'carimbo', null, 'conferido'); carimbo.style.fontSize = px(L.pedido.fs * 1.5);
  const marca = el('div', '', palco); marca.id = 'marca';
  marca.innerHTML = '<img src="../../assets/marca/taskgo-simbolo.png" alt=""><span class="w">Task<b>GO</b></span><span style="color:var(--tinta-2);margin-left:.8em;font-size:.62em">taskgohq.com.br</span>';
  marca.style.fontSize = px(FMT === '1x1' ? 38 : 60);

  /* pedido: ordem de serviço */
  const pedido = $('#pedido'); pos(pedido, L.pedido.x, L.pedido.y); pedido.style.width = px(L.pedido.w); pedido.style.fontSize = px(L.pedido.fs);
  const cab = el('div', 'cab', pedido, '<b>pedido · elétrica</b><span class="estado"><i></i><span></span></span>');
  const estadoTxt = cab.querySelector('.estado span');
  const LINHAS = [
    ['serviço', [[0, 'chuveiro queimado']]],
    ['quando', [[0, 'agora']]],
    ['valor', [[0, 'R$ 120']]],
    ['endereço', [[0, 'Rua das Flores, 48']]],
    ['profissional', [[0, '—', 'vazio'], [em('verificada', 0.62), 'Rafael · verificado ' + CHECK]]],
    ['chegada', [[0, '—', 'vazio'], [em('mapa', 0.10), 'a caminho'], [em('codigo', 0.16), 'conferida ' + CHECK],
      [em('virada', 0.18), XIS + ' longe: não vale', 'nao'], [em('virada', 0.72), '80 m: vale ' + CHECK], [C.final.inicio, 'conferida ' + CHECK]]],
    ['código', [[0, '—', 'vazio'], [em('codigo', 0.82), '4821 ' + CHECK]]],
    ['pagamento', [[0, '—', 'vazio'], [em('paga', 0.55), 'depois do serviço ' + CHECK]]]
  ];
  const linhas = LINHAS.map(([k, estados], i) => {
    const e = el('div', 'linha', pedido, '<span class="k">' + k + '</span><span class="v"></span><span class="fio"></span>');
    e.style.height = px(L.pedido.row);
    const v = e.querySelector('.v');
    let tarja = null; if (k === 'endereço') { tarja = el('span', 'tarja', v, '<i>oculto</i>'); }
    return { e, k, estados, v, fio: e.querySelector('.fio'), tarja, i, ultimo: -1 };
  });
  const tPrint = [0.16, 0.28, 0.40, 0.52].map(f => em('pedido', f));
  const tVazias = em('pedido', 0.66);

  /* legenda */
  const legenda = $('#legenda'), legSpan = legenda.querySelector('span');
  legenda.style.left = px(L.legenda.x); legenda.style.bottom = px(L.legenda.b); legenda.style.fontSize = px(L.legenda.fs); legenda.style.maxWidth = px(W - 2 * L.legenda.x);
  function quebra(txt, max) { if (txt.length <= max) return txt; const ws = txt.split(' '); let best = null; for (let i = 1; i < ws.length; i++) { const a = ws.slice(0, i).join(' '), b = ws.slice(i).join(' '); const s = Math.max(a.length, b.length); if (a.length <= max && b.length <= max && (!best || s < best.s)) best = { s, txt: a + '\n' + b }; } return best ? best.txt : txt; }
  window.LEGENDAS = T.falas.map(f => ({ inicio: f.inicio, fim: f.fim, texto: quebra(f.texto, L.legenda.chars) }));

  /* Sem grão: o papel é liso (o grão custava metade do tempo de render; ver DECISIONS.md). */

  /* ---------- câmera ---------- */
  /* Enquadramentos por ideia; entre eles a câmera muda devagar, e fica parada enquanto o texto é lido. */
  const QUADROS = [
    { t: 0, f: { x: 0, y: 0, s: 1 } },
    { t: C.verificada.inicio, d: 0.8, f: FMT === '16x9' ? { x: RAF.x, y: RAF.y, s: 1.25 } : { x: RAF.x, y: RAF.y + PH * 0.33 / 1.25, s: 1.25 } },
    { t: C.mapa.inicio, d: 0.8, f: { x: RAF.x * 0.5, y: RAF.y * 0.5, s: 1.9 } },
    { t: em('codigo', 0.02), d: 0.7, f: { x: 0, y: 0, s: 2.2 } },
    { t: em('codigo', 0.30), d: 0.6, f: FMT === '16x9' ? { x: -0.62 * U, y: 0, s: 2.0 } : { x: 0, y: -(FMT === '1x1' ? 0.5 : 0.45) * U, s: 1.6 } },
    { t: C.virada.inicio, d: 0.01, f: { x: 0.62 * U, y: -0.36 * U, s: 0.92 } },
    { t: em('virada', 0.50), d: 0.6, f: { x: 0, y: 0, s: 1.7 } }
  ];
  function camera(t) {
    let f = QUADROS[0].f;
    for (let i = 1; i < QUADROS.length; i++) { const qd = QUADROS[i]; if (t < qd.t) break; const p = RED ? (t >= qd.t ? 1 : 0) : EIO(span(t, qd.t, qd.t + qd.d)); f = { x: lerp(f.x, qd.f.x, p), y: lerp(f.y, qd.f.y, p), s: lerp(f.s, qd.f.s, p) }; }
    return f;
  }
  let CAM = { x: 0, y: 0, s: 1 };
  const tela = (x, y) => ({ x: PW / 2 + (x - CAM.x) * CAM.s, y: PH / 2 + (y - CAM.y) * CAM.s });
  const poe = (e, x, y, dx = 0, dy = 0) => { const p = tela(x, y); pos(e, p.x + dx, p.y + dy); };
  const op = (e, v) => { e.style.opacity = String(clamp(v)); e.style.visibility = v <= 0.001 ? 'hidden' : 'visible'; };

  /* ---------- quadro ---------- */
  function seek(t) {
    t = Math.max(0, Math.min(T.total, t));
    const ci = Math.max(0, T.cenas.findIndex(c => t >= c.inicio && t < c.fim)); const cena = T.cenas[ci === -1 ? T.cenas.length - 1 : ci];

    /* âncora: entra subindo do recorte; sai pelo alto no fim da cena */
    {
      const c = cena, atraso = c.id === 'pergunta' ? 1.2 : 0.05;
      al[0].textContent = c.ancora[0]; al[1].textContent = c.ancora[1];
      const ultima = c.i === T.cenas.length - 1;
      const sl = c.id === 'pergunta' ? c.fala.inicio + c.fala.dur * 0.45 : c.inicio + atraso + 0.08;
      [[0, c.inicio + atraso], [1, sl]].forEach(([k, a]) => {
        const p = mov(t, a, 0.55), s = ultima ? 0 : mov(t, c.fim - 0.26, 0.24);
        if (RED) { al[k].style.transform = 'none'; op(al[k], fad(t, a, 0.3) * (ultima ? 1 : sai(t, c.fim - 0.3, 0.3))); }
        else { al[k].style.transform = `translateY(${(1 - p) * 110 - s * 110}%)`; op(al[k], 1); }
      });
    }

    /* câmera */
    CAM = camera(t);
    cam.setAttribute('transform', `translate(${PW / 2} ${PH / 2}) scale(${CAM.s}) translate(${-CAM.x} ${-CAM.y})`);

    /* PERGUNTA: o grupo pergunta e ninguém responde; um desconhecido promete e não chega */
    {
      const c = C.pergunta, f = c.fala;
      const ent = mov(t, 0.15, 0.7), entOp = fad(t, 0.15, 0.6);
      bSorte.style.left = px(0); bSorte.style.top = px(PH * (FMT === '9x16' ? 0.08 : 0.30));
      bSorte.style.transform = `translateY(${(1 - ent) * 30}px)`;
      op(bSorte, entOp * sai(t, C.pedido.inicio, 0.4));
      /* a promessa entra quando a fala chega em "confiar" */
      const tp = f.inicio + f.dur * 0.42;
      const pe = mov(t, tp, 0.7);
      const bx = FMT === '9x16' ? L.palco.x : L.pedido.x, by = FMT === '9x16' ? L.palco.y + PH * 0.48 : L.pedido.y + (FMT === '1x1' ? 60 : 130);
      /* no pedido o balão se fecha numa linha: vira o cabeçalho da ordem de serviço */
      const fecha = mov(t, C.pedido.inicio + 0.02, 0.45);
      pos(bPalavra, bx, by);
      bPalavra.style.transformOrigin = 'left top';
      bPalavra.style.transform = `translateY(${(1 - pe) * 30 + (L.pedido.y - by) * fecha}px) scaleY(${lerp(1, 0.04, fecha)})`;
      op(bPalavra, fad(t, tp, 0.5) * (1 - fad(t, C.pedido.inicio + 0.3, 0.2)));
      /* o tempo passa: 19:02, 19:31, 20:15 · não chegou */
      const hora = bPalavra.querySelector('.hora'), nota = bPalavra.querySelector('.nota');
      const marcas = [[em('pergunta', 0.30), '19:31'], [em('pergunta', 0.48), '19:58'], [em('pergunta', 0.66), '20:15']];
      let h = '19:02'; for (const [mt, v] of marcas) if (t >= mt) h = v;
      hora.textContent = h;
      const naoChegou = t >= em('pergunta', 0.66);
      nota.innerHTML = naoChegou ? '<span style="color:var(--vermelho-texto);font-weight:700">· não chegou</span>' : '· visto';
    }

    /* PEDIDO: imprime linha por linha */
    {
      const hOp = fad(t, C.pedido.inicio + 0.25, 0.35);
      const escurece = t >= C.virada.inicio && t < C.final.inicio ? lerp(1, 0.32, fad(t, C.virada.inicio, 0.5)) : (t >= C.final.inicio ? lerp(0.32, 1, fad(t, C.final.inicio, 0.5)) : 1);
      op(pedido, 1);
      cab.style.opacity = String(hOp * escurece);
      cab.style.borderTopColor = 'var(--tinta)';
      /* estado do pedido, em palavras */
      const est = [[C.pedido.inicio, 'procurando'], [em('verificada', 0.2), 'aceito'], [em('mapa', 0.1), 'a caminho'], [em('codigo', 0.82), 'em andamento'], [em('paga', 0.12), 'concluído']];
      let s = ''; for (const [et, v] of est) if (t >= et) s = v; estadoTxt.textContent = s;
      for (const li of linhas) {
        const tl = li.i < 4 ? tPrint[li.i] : tVazias;
        const p = mov(t, tl, 0.45), o = fad(t, tl + 0.08, 0.4);
        li.fio.style.transform = `scaleX(${RED ? (t >= tl ? 1 : 0) : p})`;
        li.e.querySelector('.k').style.opacity = String(o);
        li.e.style.opacity = String(li.k === 'chegada' ? 1 : escurece);
        /* valor: o último estado que já começou */
        let k = 0; li.estados.forEach((es, j) => { if (t >= es[0]) k = j; });
        const es = li.estados[k];
        if (li.ultimo !== k) {
          li.ultimo = k;
          li.v.className = 'v' + (es[2] === 'vazio' ? ' vazio' : '') + (es[2] === 'nao' ? ' nao' : '') + (es[1].includes('class="check"') && es[2] !== 'nao' ? ' ok' : '');
          li.v.innerHTML = '<span class="txt">' + es[1] + '</span>';
          if (li.k === 'endereço') { li.tarja = el('span', 'tarja', li.v, '<i>oculto</i>'); }
        }
        const tIn = k === 0 ? tl + 0.12 : es[0];
        const vo = fad(t, tIn, 0.4);
        li.v.style.opacity = String(o * (k === 0 ? 1 : vo));
        li.v.style.transform = RED ? 'none' : `translateX(${(1 - (k === 0 ? 1 : mov(t, es[0], 0.4))) * -10}px)`;
        desenhaCheck(li.v, k === 0 ? 1 : (RED ? (t >= es[0] ? 1 : 0) : E(span(t, es[0] + 0.1, es[0] + 0.5))));
        if (li.tarja) {
          /* a tarja azul cobre o endereço até você confirmar; então se recolhe para a direita */
          const tr = em('endereco', 0.50);
          li.tarja.style.transform = `scaleX(${RED ? (t >= tr ? 0 : 1) : 1 - E(span(t, tr, tr + 0.7))})`;
          li.tarja.style.opacity = String(RED ? sai(t, tr, 0.3) : 1);
        }
      }
    }

    /* PERTO: ondas saem de você e acendem os 10 mais perto */
    {
      const c = C.perto, ini = em('perto', 0.12);
      const mundoOn = fad(t, c.inicio, 0.4);
      const casaOn = t < C.paga.inicio ? mundoOn : (t < C.virada.inicio ? sai(t, C.paga.inicio, 0.3) : (t < C.final.inicio ? fad(t, C.virada.inicio, 0.3) : sai(t, C.final.inicio, 0.4)));
      casa.setAttribute('opacity', casaOn);
      aneis.forEach((a, k) => {
        const a0 = ini + k * 0.22, p = RED ? 1 : E(span(t, a0, a0 + 1.6));
        a.setAttribute('r', (RED ? 1 : p) * 1.02 * U);
        const vis = RED ? (k === 0 ? fad(t, ini, 0.3) * sai(t, C.verificada.inicio, 0.3) * 0.7 : 0) : (t >= a0 ? (1 - p) * 0.85 + 0.15 * (k === 0 ? 1 : 0) : 0) * sai(t, C.verificada.inicio, 0.4);
        a.setAttribute('opacity', vis);
      });
      const someOutros = sai(t, C.verificada.inicio + 0.1, 0.5);
      for (const pr of pros) {
        const aceso = pr.ordem != null && t >= em('perto', 0.22 + pr.ordem * 0.045);
        const ehRaf = pr === rafael;
        let o = mundoOn * (ehRaf ? sai(t, C.verificada.inicio + 0.35, 0.3) : someOutros);
        if (t >= C.mapa.inicio && ehRaf) o = 0;
        pr.e.setAttribute('opacity', o);
        pr.e.setAttribute('fill', aceso ? '#2b59ff' : (pr.on ? '#16130f' : '#f2ede4'));
        const pulso = aceso && !RED ? 1 + 0.6 * (1 - E(span(t, em('perto', 0.22 + pr.ordem * 0.045), em('perto', 0.22 + pr.ordem * 0.045) + 0.4))) : 1;
        pr.e.setAttribute('r', 0.024 * U * (aceso ? 1.2 : 1) * pulso);
      }
      poe(rVoce, 0, 0, -L.rotFs * 1.1, 0.05 * U * CAM.s + 6);
      op(rVoce, casaOn);
      pos(rLegenda, 0, PH - L.rotFs * 1.4); op(rLegenda, mundoOn * sai(t, C.verificada.inicio, 0.3));
      pos(rOnda, 0, PH - L.rotFs * 2.8); op(rOnda, fad(t, em('perto', 0.62), 0.4) * sai(t, C.verificada.inicio, 0.3));
    }

    /* VERIFICADA: o ponto do Rafael vira avatar e cartão */
    {
      const c = C.verificada, ta = c.inicio + 0.3;
      const g0 = mov(t, ta, 0.6), sp = tela(RAF.x, RAF.y);
      const R0 = 0.03 * U * CAM.s, R1 = (FMT === '1x1' ? 42 : 58);
      const rad = lerp(R0, R1, g0);
      const fora = t >= C.mapa.inicio ? mov(t, C.mapa.inicio, 0.45) : 0;
      const rr = lerp(rad, 0.034 * U, fora);
      Object.assign(avatar.style, { width: px(2 * rr), height: px(2 * rr), left: px(sp.x - rr), top: px(sp.y - rr), fontSize: px(rr * 0.95) });
      op(avatar, (t >= ta ? 1 : 0) * sai(t, C.mapa.inicio + 0.35, 0.2));
      avatar.querySelector('span').style.opacity = String(span(rad, R1 * 0.5, R1));
      /* cartão ao lado do avatar (embaixo no 1:1 e no 9:16) */
      /* ao lado do avatar se couber; senão, embaixo dele, sempre dentro do palco */
      const cw = Math.max(cartao.offsetWidth, botao.offsetWidth, rVeEnd.offsetWidth) || 320;
      const lado = sp.x + R1 + 22 + cw <= PW - 8;
      const cx = lado ? sp.x + R1 + 22 : Math.max(8, Math.min(PW - cw - 8, sp.x - cw * 0.25));
      const cy = lado ? sp.y - R1 : sp.y + R1 + 18;
      const cp = mov(t, ta + 0.35, 0.55);
      pos(cartao, cx, cy); cartao.style.transform = `translateY(${(1 - cp) * 14}px)`;
      op(cartao, fad(t, ta + 0.35, 0.45) * sai(t, C.mapa.inicio, 0.35));
      desenhaCheck(cartao.querySelector('.selo'), RED ? (t >= ta + 0.5 ? 1 : 0) : E(span(t, ta + 0.55, ta + 0.95)));
      cartao.querySelector('.i1').style.opacity = String(fad(t, em('verificada', 0.48), 0.3));
      cartao.querySelector('.i2').style.opacity = String(fad(t, em('verificada', 0.58), 0.3));
      desenhaCheck(cartao.querySelector('.i1'), RED ? 1 : E(span(t, em('verificada', 0.5), em('verificada', 0.75))));
      desenhaCheck(cartao.querySelector('.i2'), RED ? 1 : E(span(t, em('verificada', 0.6), em('verificada', 0.85))));
      /* ENDEREÇO: botão confirmar; ao tocar, a tarja do endereço se recolhe no pedido */
      const tb = em('endereco', 0.18), tt = em('endereco', 0.42);
      const bb = cartao.getBoundingClientRect(), pb = palco.getBoundingClientRect();
      pos(botao, cx, cy + (bb.height || 200) + 18);
      const apertado = t >= tt && t < tt + 0.22;
      botao.style.background = apertado ? 'var(--tinta)' : 'var(--azul)';
      botao.style.transform = `translateY(${(1 - mov(t, tb, 0.5)) * 12}px) scale(${apertado && !RED ? 0.97 : 1})`;
      op(botao, fad(t, tb, 0.4) * sai(t, C.mapa.inicio, 0.35));
      pos(rVeEnd, cx, cy + (bb.height || 200) + 18 + botao.offsetHeight + 14);
      op(rVeEnd, fad(t, em('endereco', 0.62), 0.4) * sai(t, C.mapa.inicio, 0.35));
    }

    /* MAPA: as ruas aparecem; o Rafael segue a rota até você */
    {
      const c = C.mapa, cc = C.codigo;
      const ruasOn = t < C.paga.inicio ? fad(t, c.inicio + 0.1, 0.5) * (t >= cc.inicio ? lerp(1, 0.35, fad(t, em('codigo', 0.34), 0.4)) : 1) : (t < C.virada.inicio ? sai(t, C.paga.inicio, 0.3) : (t < C.final.inicio ? fad(t, C.virada.inicio, 0.4) : sai(t, C.final.inicio, 0.4)));
      ruas.setAttribute('opacity', ruasOn);
      const pr = RED ? (t >= em('codigo', 0.16) ? 1 : (t >= c.inicio ? 0.5 : 0)) : (t < cc.inicio ? 0.72 * EIO(span(t, c.inicio + 0.3, c.fim)) : 0.72 + 0.28 * E(span(t, cc.inicio, em('codigo', 0.16))));
      rota.setAttribute('stroke-dashoffset', String(1 - pr));
      rota.setAttribute('opacity', t >= C.mapa.inicio && t < C.paga.inicio ? (t >= cc.inicio ? sai(t, em('codigo', 0.22), 0.4) : fad(t, c.inicio, 0.3)) : 0);
      /* posição do pino ao longo da rota (comprimentos dos dois trechos) */
      const l1 = Math.abs(RAF.y), l2 = RAF.x - 0.06 * U, d = pr * (l1 + l2);
      let px_ = RAF.x, py_ = RAF.y + Math.min(d, l1); if (d > l1) { py_ = 0; px_ = RAF.x - (d - l1); }
      /* virada: longe (1,1 km) e depois a 80 m */
      const v = C.virada, LONGE = { x: 1.40 * U, y: -0.80 * U }, PERTO = { x: 0.10 * U, y: -0.06 * U };
      if (t >= v.inicio && t < C.final.inicio) {
        const m = RED ? (t >= em('virada', 0.58) ? 1 : 0) : E(span(t, em('virada', 0.58), em('virada', 0.70)));
        px_ = lerp(LONGE.x, PERTO.x, m); py_ = lerp(LONGE.y, PERTO.y, m);
      }
      pino.setAttribute('cx', px_); pino.setAttribute('cy', py_);
      const pinoOn = t >= C.mapa.inicio + 0.35 && t < C.paga.inicio ? (t >= cc.inicio ? lerp(1, 0.4, fad(t, em('codigo', 0.34), 0.4)) : 1) * sai(t, C.paga.inicio, 0.3) : (t >= v.inicio && t < C.final.inicio ? fad(t, v.inicio, 0.4) * sai(t, C.final.inicio, 0.3) : 0);
      pino.setAttribute('opacity', pinoOn);
      { const pc = tela(px_, py_), wc = rCaminho.offsetWidth || 140; pos(rCaminho, Math.min(pc.x + 0.06 * U * CAM.s, PW - wc - 8), pc.y - L.rotFs * 1.5); }
      op(rCaminho, t >= c.inicio + 0.5 && t < cc.inicio + 0.1 ? fad(t, c.inicio + 0.5, 0.3) * sai(t, cc.inicio, 0.2) : 0);
      /* chegada: círculo de 150 m */
      const tc = em('codigo', 0.05);
      const cOn = (t >= tc && t < C.paga.inicio ? fad(t, tc, 0.4) * (t >= cc.inicio ? lerp(1, 0.35, fad(t, em('codigo', 0.34), 0.4)) : 1) * sai(t, C.paga.inicio, 0.3) : 0) + (t >= v.inicio && t < C.final.inicio ? fad(t, v.inicio, 0.4) * sai(t, C.final.inicio, 0.3) : 0);
      circ150.setAttribute('opacity', cOn);
      circ150.setAttribute('r', 0.22 * U * (RED ? 1 : lerp(0.6, 1, mov(t, tc, 0.5))));
      { const pc = tela(0, 0), wc = rChegou.offsetWidth || 260; pos(rChegou, Math.max(8, Math.min(pc.x - wc / 2, PW - wc - 8)), pc.y + 0.24 * U * CAM.s); }
      op(rChegou, fad(t, em('codigo', 0.18), 0.35) * sai(t, em('codigo', 0.34), 0.3));
      desenhaCheck(rChegou, RED ? 1 : E(span(t, em('codigo', 0.2), em('codigo', 0.3))));
      /* virada: distância, "Cheguei", longe não vale, 80 m vale */
      const dOn = t >= v.inicio && t < em('virada', 0.6) ? fad(t, v.inicio + 0.2, 0.4) * sai(t, em('virada', 0.52), 0.3) : 0;
      distLinha.setAttribute('x2', LONGE.x); distLinha.setAttribute('y2', LONGE.y); distLinha.setAttribute('opacity', dOn);
      poe(rDist, LONGE.x * 0.5, LONGE.y * 0.5, -L.rotFs * 3.2, -L.rotFs * 1.6); op(rDist, dOn);
      { const pl = tela(LONGE.x, LONGE.y), wl = rLonge.offsetWidth || 260, dx = 0.05 * U * CAM.s + 14;
        if (pl.x + dx + wl <= PW - 8) pos(rLonge, pl.x + dx, pl.y - L.rotFs * 0.6);
        else pos(rLonge, Math.max(8, Math.min(pl.x - wl / 2, PW - wl - 8)), pl.y + 0.05 * U * CAM.s + 12); }
      /* o raio de 150 m é a régua da virada */
      { const pr = tela(0, 0), rr = 0.22 * U * CAM.s; pos(rRaio, Math.max(8, Math.min(pr.x - (rRaio.offsetWidth || 160) / 2, PW - (rRaio.offsetWidth || 160) - 8)), pr.y - rr - L.rotFs * 1.7);
        op(rRaio, t >= C.virada.inicio && t < C.final.inicio ? fad(t, C.virada.inicio + 0.2, 0.4) * sai(t, C.final.inicio, 0.3) : 0); }
      op(rLonge, t >= v.inicio ? fad(t, em('virada', 0.18), 0.3) * sai(t, em('virada', 0.56), 0.25) : 0);
      poe(rVale, PERTO.x, PERTO.y, -0.2 * U * CAM.s, 0.28 * U * CAM.s);
      op(rVale, t >= v.inicio && t < C.final.inicio ? fad(t, em('virada', 0.72), 0.35) * sai(t, C.final.inicio, 0.3) : 0);
      desenhaCheck(rVale, RED ? 1 : E(span(t, em('virada', 0.74), em('virada', 0.86))));
    }

    /* "Cheguei" do Rafael, longe: é só uma frase */
    {
      const v = C.virada, LONGE = { x: 1.40 * U, y: -0.80 * U };
      if (!window.__cheguei) { window.__cheguei = el('div', 'balao', palco, '<div class="msg">Cheguei!</div>'); window.__cheguei.style.fontSize = px(fsB * 0.6); }
      const b = window.__cheguei;
      poe(b, LONGE.x, LONGE.y, -70, -(fsB * 0.6) * 3.2);
      op(b, t >= v.inicio && t < C.final.inicio ? fad(t, em('virada', 0.08), 0.3) * sai(t, em('virada', 0.56), 0.25) : 0);
      b.style.transform = `translateY(${(1 - mov(t, em('virada', 0.08), 0.4)) * 10}px)`;
    }

    /* CÓDIGO: no seu celular 4821; o Rafael digita; confere */
    {
      const c = C.codigo, tp = em('codigo', 0.36);
      const lado = FMT !== '9x16' ? false : true;
      const x0 = FMT === '16x9' ? PW * 0.08 : PW * 0.04, y0 = PH * 0.10;
      pos(pVoce, x0, y0);
      const h1 = pVoce.offsetHeight || 140;
      pos(pRaf, x0, y0 + h1 + 24);
      const o = fad(t, tp, 0.45) * sai(t, C.paga.inicio, 0.3);
      op(pVoce, o); op(pRaf, fad(t, tp + 0.15, 0.45) * sai(t, C.paga.inicio, 0.3));
      pVoce.style.transform = pRaf.style.transform = `translateY(${(1 - mov(t, tp, 0.5)) * 14}px)`;
      const dig = pRaf.querySelectorAll('.digitos span'), alvo = '4821';
      dig.forEach((s, k) => { s.textContent = t >= em('codigo', 0.48 + k * 0.08) ? alvo[k] : ''; });
      const okc = t >= em('codigo', 0.82);
      for (const p of [pVoce, pRaf]) p.style.borderColor = okc ? 'var(--verde-texto)' : 'var(--tinta)';
      pos(rConfere, x0, y0 + h1 + 24 + (pRaf.offsetHeight || 140) + 16);
      op(rConfere, fad(t, em('codigo', 0.82), 0.3) * sai(t, C.paga.inicio, 0.3));
      desenhaCheck(rConfere, RED ? 1 : E(span(t, em('codigo', 0.84), em('codigo', 0.96))));
    }

    /* PAGA: o serviço termina e você paga depois, por Pix, direto ao Rafael */
    {
      const c = C.paga, tq = em('paga', 0.22), tpg = em('paga', 0.55);
      pos(pix, FMT === '16x9' ? PW * 0.08 : PW * 0.02, PH * 0.14);
      op(pix, fad(t, tq, 0.45) * sai(t, C.virada.inicio, 0.3));
      pix.style.transform = `translateY(${(1 - mov(t, tq, 0.5)) * 14}px)`;
      pst.innerHTML = t >= tpg ? '<span class="ok" style="display:inline-flex;gap:.3em;align-items:center;font-weight:700">' + CHECK + 'pago, depois do serviço</span>' : '<span style="color:var(--tinta-2)">serviço concluído</span>';
      desenhaCheck(pst, RED ? 1 : E(span(t, tpg + 0.05, tpg + 0.4)));
    }

    /* FINAL: a promessa do começo, agora conferida; carimbo no pedido; marca */
    {
      const c = C.final;
      pos(bVolta, 0, PH * (FMT === '9x16' ? 0.06 : 0.14));
      op(bVolta, fad(t, em('final', 0.08), 0.5));
      bVolta.style.transform = `translateY(${(1 - mov(t, em('final', 0.08), 0.6)) * 18}px)`;
      desenhaCheck(bVolta, RED ? 1 : E(span(t, em('final', 0.18), em('final', 0.32))));
      const tc = em('final', 0.35), pc = RED ? (t >= tc ? 1 : 0) : E(span(t, tc, tc + 0.35));
      const cw = carimbo.offsetWidth || 260;
      pos(carimbo, L.pedido.x + L.pedido.w - cw, L.pedido.y - (carimbo.offsetHeight || 60) * 0.35);
      cab.querySelector('.estado').style.opacity = String(t >= tc ? 0 : 1);
      carimbo.style.transform = `scale(${RED ? 1 : lerp(1.35, 1, pc)})`;
      op(carimbo, RED ? fad(t, tc, 0.3) : (t >= tc ? Math.min(1, pc * 1.6) : 0));
      pos(marca, 0, PH * (FMT === '9x16' ? 0.62 : 0.64));
      op(marca, fad(t, em('final', 0.55), 0.5));
      marca.style.transform = `translateY(${(1 - mov(t, em('final', 0.55), 0.6)) * 12}px)`;
    }

    /* legenda gravada: a fala desta hora */
    if (LEG) {
      const lg = window.LEGENDAS.find(x => t >= x.inicio && t < x.fim + 0.15);
      legSpan.textContent = lg ? lg.texto : '';
      legSpan.style.whiteSpace = 'pre-line';
      op(legenda, lg ? 1 : 0);
    } else op(legenda, 0);
    return t;
  }
  window.seek = seek;
  window.PRONTO = document.fonts.ready.then(() => Promise.all([...document.images].map(i => i.complete ? 1 : new Promise(r => { i.onload = i.onerror = r; })))).then(() => { seek(0); return { W, H, total: T.total, quadros: T.quadros, fmt: FMT, reduzido: RED }; });
})();
