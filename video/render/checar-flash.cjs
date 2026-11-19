// Piscadas pelo critério da WCAG 2 (limite geral de flash):
// - flash = par de mudanças opostas de luminância relativa de 10% ou mais, com a mais escura abaixo de 0,80;
// - falha se um ponto tem mais de 3 flashes (mais de 6 mudanças) em 1 s numa área maior que 25% de um campo de 10°
//   de visão (341×256 numa tela de 1024×768, ou seja, 1/3 da largura × 1/3 da altura).
// Mudança única (corte de cena) não é flash. Analisa em 320 px de largura.
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { RAIZ, FFMPEG } = require('./ferramentas.cjs');
const arq = path.join(RAIZ, 'out', process.argv[2] || 'master_16x9.mp4');
const pr = spawnSync(require('./ferramentas.cjs').FFPROBE, ['-v', 'error', '-select_streams', 'v', '-show_entries', 'stream=width,height,r_frame_rate', '-of', 'csv=p=0', arq]).stdout.toString().trim().split(',');
const W0 = +pr[0], H0 = +pr[1], fps = Math.round(eval(pr[2]));
const w = 320, h = Math.round(320 * H0 / W0 / 2) * 2;
const r = spawnSync(FFMPEG, ['-hide_banner', '-loglevel', 'error', '-i', arq, '-vf', `scale=${w}:${h}:flags=area,format=rgb24`, '-f', 'rawvideo', '-'], { maxBuffer: 2 ** 31 });
const N = w * h, qb = N * 3, n = Math.floor(r.stdout.length / qb);
const lin = new Float32Array(256); for (let i = 0; i < 256; i++) { const c = i / 255; lin[i] = c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; }
const ext = new Float32Array(N), dir = new Int8Array(N), anel = Array.from({ length: fps }, () => new Uint8Array(N)), cont = new Uint8Array(N);
const jw = Math.round(w / 3), jh = Math.round(h / 3), lim = 0.25 * jw * jh;
let pior = { t: 0, frac: 0 }, falhas = 0;
for (let f = 0; f < n; f++) {
  const base = f * qb, slot = anel[f % fps];
  for (let p = 0; p < N; p++) { cont[p] -= slot[p]; slot[p] = 0; }
  for (let p = 0, o = base; p < N; p++, o += 3) {
    const Y = 0.2126 * lin[r.stdout[o]] + 0.7152 * lin[r.stdout[o + 1]] + 0.0722 * lin[r.stdout[o + 2]];
    if (f === 0) { ext[p] = Y; continue; }
    const d = Y - ext[p];
    if (dir[p] >= 0 && d <= -0.1 && Y < 0.8) { dir[p] = -1; ext[p] = Y; slot[p] = 1; cont[p]++; }
    else if (dir[p] <= 0 && d >= 0.1 && ext[p] < 0.8) { dir[p] = 1; ext[p] = Y; slot[p] = 1; cont[p]++; }
    else if ((dir[p] >= 0 && Y > ext[p]) || (dir[p] < 0 && Y < ext[p])) ext[p] = Y;
  }
  /* pontos com mais de 6 mudanças no último segundo; maior concentração numa janela de 1/3 × 1/3 da tela */
  const sat = new Int32Array((w + 1) * (h + 1));
  for (let y = 0; y < h; y++) { let lin_ = 0; for (let x = 0; x < w; x++) { lin_ += cont[y * w + x] > 6 ? 1 : 0; sat[(y + 1) * (w + 1) + x + 1] = sat[y * (w + 1) + x + 1] + lin_; } }
  let max = 0;
  for (let y = 0; y + jh <= h; y += 4) for (let x = 0; x + jw <= w; x += 4) { const s = sat[(y + jh) * (w + 1) + x + jw] - sat[y * (w + 1) + x + jw] - sat[(y + jh) * (w + 1) + x] + sat[y * (w + 1) + x]; if (s > max) max = s; }
  const frac = max / (jw * jh);
  if (frac > pior.frac) pior = { t: f / fps, frac };
  if (max > lim) falhas++;
}
console.log(`${path.basename(arq)}: ${n} quadros; quadros acima do limite: ${falhas}; pior: ${(pior.frac * 100).toFixed(1)}% da janela piscando em t=${pior.t.toFixed(2)} s (limite 25%)  ${falhas ? 'FALHOU' : 'ok'}`);
process.exitCode = falhas ? 1 : 0;
