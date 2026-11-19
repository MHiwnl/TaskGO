/* TIMELINE do filme: cada cena, cada fala, cada efeito sonoro e cada motivo da trilha.
   Função pura das durações das falas (narracao/duracoes.json): o mesmo cálculo serve à imagem (film.js),
   ao som (audio.js) e à legenda (render/srt.cjs). Funciona no navegador e no Node. */
(function (raiz) {
  const DURACAO_ALVO = 40;
  /* Arco: pergunta (0–10%), modelo (10–35%), prova (35–75%), virada (75–90%), recompensa (90–100%). */
  const CENAS = [
    { id: 'pergunta',   parte: 'pergunta',   antes: 0.4, depois: 0.4, minimo: 4.6, ancora: ['pediu indicação?', 'agora é torcer.'] },
    { id: 'pedido',     parte: 'modelo',     antes: 0.3, depois: 0.4, minimo: 2.9, ancora: ['com a TaskGO,', 'é diferente.'] },
    { id: 'perto',      parte: 'modelo',     antes: 0.3, depois: 0.4, minimo: 3.3, ancora: ['um pedido só.', 'os 10 mais perto, na hora.'] },
    { id: 'verificada', parte: 'modelo',     antes: 0.3, depois: 0.4, minimo: 3.0, ancora: ['identidade conferida.', 'zero surpresa.'] },
    { id: 'endereco',   parte: 'prova',      antes: 0.3, depois: 0.4, minimo: 3.6, ancora: ['seu endereço,', 'só pra quem você quer.'] },
    { id: 'mapa',       parte: 'prova',      antes: 0.2, depois: 0.4, minimo: 3.2, ancora: ['ele chegando,', 'ao vivo.'] },
    { id: 'codigo',     parte: 'prova',      antes: 0.3, depois: 0.4, minimo: 4.0, ancora: ['4 números.', 'é ele.'] },
    { id: 'paga',       parte: 'prova',      antes: 0.3, depois: 0.4, minimo: 3.0, ancora: ['pagar?', 'só no final.'] },
    { id: 'virada',     parte: 'virada',     antes: 1.0, depois: 0.4, minimo: 5.4, ancora: ['“tô chegando”?', 'o mapa confere.'] },
    { id: 'final',      parte: 'recompensa', antes: 0.4, depois: 1.2, minimo: 5.2, ancora: ['confiança', 'na hora.'] }
  ];

  function montarTimeline(duracoes) {
    if (!duracoes || duracoes.length !== CENAS.length) throw new Error('Esperava ' + CENAS.length + ' falas.');
    let t = 0;
    /* Tomada contínua: a voz corre sem parar; cada cena dura exatamente a sua fala (só a primeira ganha
       um respiro antes e a última um respiro depois). Sem mínimo e sem completar até 40 s. */
    const continuo = duracoes.every(d => d.continuo);
    const cenas = CENAS.map((c, i) => {
      const fala = duracoes[i];
      const antes = continuo ? (i === 0 ? 0.5 : 0) : c.antes, depois = continuo ? (i === CENAS.length - 1 ? 2.2 : 0) : c.depois;
      c = Object.assign({}, c, { antes, depois });
      const dur = continuo ? antes + fala.dur + depois : Math.max(c.minimo, c.antes + fala.dur + c.depois);
      const cena = Object.assign({}, c, { i, inicio: t, dur, fim: t + dur, fala: { id: fala.id, texto: fala.texto, inicio: t + c.antes, fim: t + c.antes + fala.dur, dur: fala.dur, arquivo: fala.arquivo, origem: fala.origem } });
      t += dur;
      return cena;
    });
    /* Sobrou tempo até 40 s: vai para o respiro de cada cena, na proporção da cena (a fala não muda de lugar dentro dela). */
    if (!continuo && t < DURACAO_ALVO) {
      const extra = DURACAO_ALVO - t, fator = extra / t;
      let a = 0;
      for (const c of cenas) {
        const mais = c.dur * fator, desloca = a - c.inicio;
        c.inicio += desloca; c.fala.inicio += desloca + mais * 0.35; c.fala.fim += desloca + mais * 0.35;
        c.dur += mais; c.fim = c.inicio + c.dur; a = c.fim;
      }
      t = DURACAO_ALVO;
    }
    const total = Math.round(t * 1000) / 1000;
    const C = Object.fromEntries(cenas.map(c => [c.id, c]));
    /* Batidas que pedem som: efeito de interface só onde a ação tem significado. motivo = a ideia principal (conferido). */
    const em = (id, frac) => C[id].inicio + C[id].dur * frac;
    const efeitos = [
      { t: em('pergunta', 0.30), som: 'relogio' }, { t: em('pergunta', 0.48), som: 'relogio' }, { t: em('pergunta', 0.66), som: 'relogio' },
      { t: em('pedido', 0.16), som: 'imprime' }, { t: em('pedido', 0.28), som: 'imprime' }, { t: em('pedido', 0.40), som: 'imprime' }, { t: em('pedido', 0.52), som: 'imprime' },
      { t: em('perto', 0.12), som: 'onda' },
      ...Array.from({ length: 10 }, (_, k) => ({ t: em('perto', 0.22 + k * 0.045), som: 'acende', k })),
      { t: em('verificada', 0.20), som: 'aceita' }, { t: em('verificada', 0.62), som: 'motivo' },
      { t: em('endereco', 0.42), som: 'toque' }, { t: em('endereco', 0.50), som: 'tarja' },
      { t: em('mapa', 0.10), som: 'rota' },
      { t: em('codigo', 0.16), som: 'chegou' },
      ...[0, 1, 2, 3].map(k => ({ t: em('codigo', 0.48 + k * 0.08), som: 'digito', k })), { t: em('codigo', 0.82), som: 'motivo' },
      { t: em('paga', 0.55), som: 'pago' },
      { t: em('virada', 0.18), som: 'erro' }, { t: em('virada', 0.72), som: 'certo' },
      { t: C.final.fala.inicio + 0.8, som: 'carimbo' }, { t: C.final.fala.inicio + 1.05, som: 'motivo_final' }
    ].sort((x, y) => x.t - y.t);
    return { total, fps: 60, quadros: Math.round(total * 60), cenas, C, efeitos, falas: cenas.map(c => c.fala) };
  }

  const api = { CENAS, DURACAO_ALVO, montarTimeline };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else raiz.FILME = api;
})(typeof window !== 'undefined' ? window : globalThis);
