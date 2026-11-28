// Legendas do roteiro: mesma regra da página (mín. 1,8 s; fala + 0,6 s; nunca passa da próxima fala).
// Uso: node cues.cjs <lines.json> <cues.json>
const fs = require('fs');
const [, , entrada, saida] = process.argv;
const L = JSON.parse(fs.readFileSync(entrada, 'utf8'));
const cues = L.map((l, i) => {
  const prox = L[i + 1] ? L[i + 1].t : Infinity;
  return { t0: l.t, t1: Math.min(l.t + Math.max(1.8, l.dur + 0.6), prox), text: l.text };
});
fs.writeFileSync(saida, JSON.stringify(cues, null, 1));
console.log('legendas', cues.length);
