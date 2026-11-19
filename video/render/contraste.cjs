// Contraste WCAG dos pares de cor do filme (tokens.css). Texto precisa de 4,5:1; texto grande, 3:1.
const lum = h => { const c = [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16) / 255).map(v => v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; };
const razao = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
const pares = [
  ['tinta sobre papel', '#16130f', '#f2ede4', 4.5],
  ['tinta-2 sobre papel', '#4f4940', '#f2ede4', 4.5],
  ['azul-tinta sobre papel', '#1a3fd6', '#f2ede4', 4.5],
  ['branco sobre azul', '#ffffff', '#2b59ff', 4.5],
  ['verde-texto sobre papel', '#0a7a4b', '#f2ede4', 4.5],
  ['vermelho-texto sobre papel', '#c4262e', '#f2ede4', 4.5],
  ['tinta sobre papel-2', '#16130f', '#e8e1d5', 4.5],
  ['azul (só grande) sobre papel', '#2b59ff', '#f2ede4', 3],
  ['branco sobre tinta (legenda)', '#ffffff', '#16130f', 4.5],
];
let falhou = 0;
for (const [nome, a, b, min] of pares) { const r = razao(a, b); const ok = r >= min; if (!ok) falhou++; console.log((ok ? 'ok    ' : 'FALHOU') + ' ' + r.toFixed(2) + ':1  (mín. ' + min + ')  ' + nome); }
process.exit(falhou ? 1 : 0);
