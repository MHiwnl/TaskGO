/* Trilha procedural e efeitos, renderizados fora do tempo real (OfflineAudioContext) e travados na TIMELINE.
   Um motivo de três notas ("conferido") volta nas verificações. A trilha desce 9 dB sob cada fala.
   Ruído só com semente: o mesmo áudio a cada render. Sai em PCM 16 bits, estéreo, 48 kHz. */
(function () {
  const SR = 48000;
  const nota = n => 440 * Math.pow(2, (n - 69) / 12);              /* MIDI → Hz */
  /* Um acorde por cena (tons de ré maior; a pergunta em si menor, a virada em mi menor suspenso). */
  const ACORDES = { pergunta: [47, 54, 59, 62], pedido: [50, 57, 62, 66], perto: [50, 57, 62, 66], verificada: [43, 55, 59, 62],
    endereco: [50, 57, 62, 66], mapa: [45, 57, 61, 64], codigo: [43, 55, 59, 62], paga: [50, 57, 62, 66], virada: [40, 52, 57, 59], final: [50, 57, 62, 66, 69] };
  function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let x = Math.imul(seed ^ seed >>> 15, 1 | seed); x = x + Math.imul(x ^ x >>> 7, 61 | x) ^ x; return ((x ^ x >>> 14) >>> 0) / 4294967296; }; }

  window.renderAudio = async function (T) {
    const dur = T.total + 0.6;
    const ctx = new OfflineAudioContext(2, Math.ceil(dur * SR), SR);
    const mestre = ctx.createGain(); mestre.gain.value = 0.9; mestre.connect(ctx.destination);
    /* barramento da trilha, com o abaixamento sob a voz */
    const trilha = ctx.createGain(); trilha.connect(mestre);
    const g = trilha.gain; g.setValueAtTime(1, 0);
    for (const f of T.falas) { g.setTargetAtTime(0.2, Math.max(0, f.inicio - 0.12), 0.06); g.setTargetAtTime(1, f.fim + 0.1, 0.18); }
    const efeitos = ctx.createGain(); efeitos.gain.value = 0.6; efeitos.connect(mestre);
    /* ruído branco com semente (um buffer só, reaproveitado) */
    const r = rng(2026), ruido = ctx.createBuffer(1, SR, SR); { const d = ruido.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = r() * 2 - 1; }

    /* ---------- trilha ---------- */
    /* pad: um acorde por cena, ataque e saída lentos, filtrado */
    for (const c of T.cenas) {
      const notas = ACORDES[c.id], t0 = c.inicio, t1 = c.fim + 0.6;
      const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = c.id === 'virada' ? 700 : 1300; lp.Q.value = 0.4;
      const env = ctx.createGain(); env.gain.setValueAtTime(0, t0); env.gain.linearRampToValueAtTime(c.id === 'final' ? 0.06 : 0.045, t0 + 0.7); env.gain.setValueAtTime(c.id === 'final' ? 0.06 : 0.045, c.fim - 0.1); env.gain.linearRampToValueAtTime(0, t1);
      lp.connect(env); env.connect(trilha);
      notas.forEach((n, i) => {
        for (const det of [-4, 4]) {
          const o = ctx.createOscillator(); o.type = i === 0 ? 'sine' : 'triangle'; o.frequency.value = nota(n); o.detune.value = det;
          const pan = ctx.createStereoPanner(); pan.pan.value = (i % 2 ? 0.25 : -0.25) * (det > 0 ? 1 : -1);
          const gg = ctx.createGain(); gg.gain.value = i === 0 ? 0.9 : 0.5;
          o.connect(gg); gg.connect(pan); pan.connect(lp); o.start(t0); o.stop(t1 + 0.1);
        }
      });
    }
    /* pulso: colcheias suaves do pedido até a virada (a 96 BPM, alinhado ao início do pedido); para na virada */
    const BPM = 96, colcheia = 60 / BPM / 2, ini = T.C.pedido.inicio, fim = T.C.virada.inicio;
    for (let k = 0, t = ini; t < fim; k++, t = ini + k * colcheia) {
      const c = T.cenas.find(x => t >= x.inicio && t < x.fim) || T.C.pedido;
      const ns = ACORDES[c.id], n = ns[1 + (k % (ns.length - 1))] + 12;
      const o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = nota(n);
      const e = ctx.createGain(); const forte = k % 4 === 0 ? 0.05 : 0.03;
      e.gain.setValueAtTime(0, t); e.gain.linearRampToValueAtTime(forte, t + 0.006); e.gain.exponentialRampToValueAtTime(0.0005, t + 0.22);
      const pan = ctx.createStereoPanner(); pan.pan.value = k % 2 ? 0.3 : -0.3;
      o.connect(e); e.connect(pan); pan.connect(trilha); o.start(t); o.stop(t + 0.25);
    }
    /* virada: uma nota grave sustentada (tensão) */
    { const v = T.C.virada, o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = nota(28);
      const e = ctx.createGain(); e.gain.setValueAtTime(0, v.inicio); e.gain.linearRampToValueAtTime(0.08, v.inicio + 0.5); e.gain.setValueAtTime(0.08, v.fim - 0.4); e.gain.linearRampToValueAtTime(0, v.fim + 0.3);
      o.connect(e); e.connect(trilha); o.start(v.inicio); o.stop(v.fim + 0.4); }

    /* ---------- efeitos ---------- */
    const sino = (t, notas, vol = 0.12, passo = 0.09) => notas.forEach((n, i) => {
      const tt = t + i * passo;
      for (const [mult, g0] of [[1, 1], [2.01, 0.28], [3.0, 0.08]]) {
        const o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = nota(n) * mult;
        const e = ctx.createGain(); e.gain.setValueAtTime(0, tt); e.gain.linearRampToValueAtTime(vol * g0, tt + 0.008); e.gain.exponentialRampToValueAtTime(0.0003, tt + 1.1);
        o.connect(e); e.connect(efeitos); o.start(tt); o.stop(tt + 1.2);
      }
    });
    const clique = (t, freq = 3000, vol = 0.18, d = 0.014, q = 3) => {
      const s = ctx.createBufferSource(); s.buffer = ruido; const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = freq; bp.Q.value = q;
      const e = ctx.createGain(); e.gain.setValueAtTime(vol, t); e.gain.exponentialRampToValueAtTime(0.0003, t + d);
      s.connect(bp); bp.connect(e); e.connect(efeitos); s.start(t, (t * 7.3) % 0.9, d + 0.01);
    };
    const tom = (t, f0, f1, d, vol, tipo = 'sine') => {
      const o = ctx.createOscillator(); o.type = tipo; o.frequency.setValueAtTime(f0, t); o.frequency.exponentialRampToValueAtTime(f1, t + d);
      const e = ctx.createGain(); e.gain.setValueAtTime(0, t); e.gain.linearRampToValueAtTime(vol, t + 0.01); e.gain.exponentialRampToValueAtTime(0.0003, t + d);
      o.connect(e); e.connect(efeitos); o.start(t); o.stop(t + d + 0.05);
    };
    const sopro = (t, d, f0, f1, vol) => {
      const s = ctx.createBufferSource(); s.buffer = ruido; const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.Q.value = 1.2;
      bp.frequency.setValueAtTime(f0, t); bp.frequency.exponentialRampToValueAtTime(f1, t + d);
      const e = ctx.createGain(); e.gain.setValueAtTime(0, t); e.gain.linearRampToValueAtTime(vol, t + d * 0.35); e.gain.linearRampToValueAtTime(0, t + d);
      s.connect(bp); bp.connect(e); e.connect(efeitos); s.start(t, 0.1, d + 0.05);
    };
    const MOTIVO = [69, 74, 78];                                      /* lá, ré, fá#: "conferido" */
    for (const ef of T.efeitos) {
      const t = ef.t;
      switch (ef.som) {
        case 'relogio': clique(t, 2200, 0.10, 0.02, 6); break;
        case 'imprime': clique(t, 3200, 0.16, 0.016, 2.5); clique(t + 0.03, 1800, 0.06, 0.02, 2); break;
        case 'onda': sopro(t, 1.4, 400, 1400, 0.05); break;
        case 'acende': tom(t, nota(86 + (ef.k % 3) * 2), nota(86 + (ef.k % 3) * 2), 0.12, 0.035); break;
        case 'aceita': sino(t, [74, 81], 0.08, 0.08); break;
        case 'motivo': sino(t, MOTIVO, 0.11, 0.11); break;
        case 'toque': clique(t, 1600, 0.2, 0.03, 1.5); break;
        case 'tarja': sopro(t, 0.6, 2200, 600, 0.05); break;
        case 'rota': tom(t, nota(62), nota(62), 0.6, 0.03); break;
        case 'chegou': sino(t, [74], 0.08); break;
        case 'digito': clique(t, 2600 + ef.k * 180, 0.16, 0.02, 4); break;
        case 'pago': sino(t, [81, 86], 0.08, 0.07); break;
        case 'erro': tom(t, 150, 110, 0.32, 0.09, 'triangle'); tom(t + 0.12, 150, 105, 0.28, 0.07, 'triangle'); break;
        case 'certo': sino(t, [74, 81], 0.1, 0.09); break;
        case 'carimbo': tom(t, 120, 60, 0.18, 0.2); clique(t, 900, 0.18, 0.04, 1); break;
        case 'motivo_final': sino(t, MOTIVO.concat([81]), 0.12, 0.13); break;
      }
    }
    const buf = await ctx.startRendering();
    /* PCM 16 bits intercalado, em base64 */
    const L = buf.getChannelData(0), Rr = buf.getChannelData(1), n = L.length, pcm = new Int16Array(n * 2);
    for (let i = 0; i < n; i++) { pcm[2 * i] = Math.max(-1, Math.min(1, L[i])) * 32767 | 0; pcm[2 * i + 1] = Math.max(-1, Math.min(1, Rr[i])) * 32767 | 0; }
    const bytes = new Uint8Array(pcm.buffer); let bin = ''; for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    return { sr: SR, canais: 2, amostras: n, b64: btoa(bin) };
  };
})();
