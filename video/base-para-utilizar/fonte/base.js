/* Base comum dos clipes de referência da TaskGO (5 s, 1920×1080).
   Cada clipe define window.CLIPE = { dur: 5, montar(palco), quadro(t) } e este arquivo cuida do resto:
   palco em escala, contrato do render (__meta, __seek, __ready), ?play (loop ao vivo) e ?t=2.5 (quadro fixo).
   Regra: tudo o que aparece é função pura de t (nada de setTimeout, animação CSS ou Math.random sem semente). */
(function () {
  const W = 1920, H = 1080;
  const M = {
    W, H,
    cores: { azul: '#2b59ff', azulEscuro: '#1a3fd6', azulClaro: '#8aa3ff', verde: '#35d08f', preto: '#000', quase: '#08080a', branco: '#fff', cinza: '#9a9aa6' },
    clamp: (x, a = 0, b = 1) => Math.min(b, Math.max(a, x)),
    lerp: (a, b, p) => a + (b - a) * p,
    /* progresso 0..1 de t entre a e b */
    p: (t, a, b) => Math.min(1, Math.max(0, (t - a) / (b - a))),
    outExpo: x => (x >= 1 ? 1 : 1 - Math.pow(2, -10 * x)),
    outCubic: x => 1 - Math.pow(1 - x, 3),
    inCubic: x => x * x * x,
    inOutCubic: x => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2),
    outBack: (x, s = 1.70158) => 1 + (s + 1) * Math.pow(x - 1, 3) + s * Math.pow(x - 1, 2),
    /* mola amortecida: 0 → 1 com overshoot; t em segundos desde o início */
    mola: (t, freq = 4.5, amort = 6) => (t <= 0 ? 0 : 1 - Math.exp(-amort * t) * Math.cos(freq * 2 * Math.PI * t)),
    /* gerador pseudoaleatório com semente (mesmo resultado em todo quadro) */
    rnd: seed => () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let x = Math.imul(seed ^ (seed >>> 15), 1 | seed); x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x; return ((x ^ (x >>> 14)) >>> 0) / 4294967296; },
    el: (tag, css, pai, html) => { const e = document.createElement(tag); if (css) e.style.cssText = css; if (html != null) e.innerHTML = html; (pai || document.body).appendChild(e); return e; },
    simbolo: '../../onetake/simbolo.png'
  };
  window.M = M;

  const css = document.createElement('style');
  css.textContent = `html,body{margin:0;background:#000;overflow:hidden}
#palco{position:absolute;left:0;top:0;width:${W}px;height:${H}px;transform-origin:0 0;overflow:hidden;font-family:'Spline Sans',system-ui,sans-serif;color:#fff;background:#000}
#palco *{box-sizing:border-box}`;
  document.head.appendChild(css);

  window.__ready = new Promise(ok => {
    window.addEventListener('load', async () => {
      const palco = M.el('div', '', document.body); palco.id = 'palco';
      const ajustar = () => { const s = Math.min(innerWidth / W, innerHeight / H); palco.style.transform = `scale(${s})`; };
      ajustar(); addEventListener('resize', ajustar);
      const C = window.CLIPE;
      await C.montar(palco);
      if (document.fonts) await document.fonts.ready;
      await Promise.all([...document.images].map(i => i.complete ? 0 : new Promise(r => { i.onload = i.onerror = r; })));
      window.__meta = { dur: C.dur || 5, w: W, h: H };
      window.__seek = async t => { C.quadro(t); return true; };
      /* Som (só nos filmes): [{t, kind, x}] com os kinds de video/onetake/score.py (tap, type, word, land, open, close, whip, dive, rise, hit, paid, drop, qr). */
      window.__events = () => C.eventos || [];
      C.quadro(0);
      const q = new URLSearchParams(location.search);
      if (q.has('t')) C.quadro(parseFloat(q.get('t')));
      if (q.has('play')) { const t0 = performance.now(); (function loop() { C.quadro(((performance.now() - t0) / 1000) % (C.dur || 5)); requestAnimationFrame(loop); })(); }
      ok(true);
    });
  });
})();
