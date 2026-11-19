// Renderiza os mesmos quadros duas vezes, em dois Chromes diferentes e em ordem diferente, e compara byte a byte.
const crypto = require('node:crypto');
const { abrirFilme } = require('./quadros.cjs');
const hash = b => crypto.createHash('sha256').update(b).digest('hex').slice(0, 16);
(async () => {
  const tempos = [2.5, 9.9, 13.3, 17.1, 20.0, 24.4, 27.8, 31.2, 37.6];
  const rodada = async ordem => { const { b } = await abrirFilme('16x9', false, true); const r = {}; for (const t of ordem) r[t] = hash(await b.quadro(t)); await b.fechar(); return r; };
  const a = await rodada(tempos), b = await rodada(tempos.slice().reverse());
  let iguais = 0;
  for (const t of tempos) { const ok = a[t] === b[t]; if (ok) iguais++; console.log((ok ? 'idêntico ' : 'DIFERENTE') + '  t=' + t + 's  ' + a[t] + ' / ' + b[t]); }
  console.log(iguais + ' de ' + tempos.length + ' quadros idênticos');
  process.exit(iguais === tempos.length ? 0 : 1);
})();
