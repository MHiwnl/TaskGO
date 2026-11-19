// Checagem do som: volume final (EBU R128), abaixamento da trilha sob a voz e forma de onda com as falas marcadas.
const fs = require('node:fs'), path = require('node:path');
const { RAIZ, ffmpeg, ffmpegErr } = require('./ferramentas.cjs');
const { montarTimeline } = require('../src/timeline.js');
const T = montarTimeline(JSON.parse(fs.readFileSync(path.join(RAIZ, 'narracao/duracoes.json'), 'utf8')));
const B = p => path.join(RAIZ, 'build', p);
/* volume "momentâneo" (janela de 400 ms) a cada 100 ms */
function momentaneo(arq) {
  const r = ffmpegErr(['-i', arq, '-af', 'ebur128', '-f', 'null', '-']);
  return [...r.matchAll(/t:\s*([\d.]+).*?M:\s*(-?[\d.]+|-inf)/g)].map(m => ({ t: +m[1], M: m[2] === '-inf' ? -70 : +m[2] }));
}
const media = l => l.length ? l.reduce((a, x) => a + x.M, 0) / l.length : NaN;
const naFala = t => T.falas.some(f => t >= f.inicio + 0.2 && t <= f.fim - 0.1);
const foraFala = t => T.falas.every(f => t < f.inicio - 0.5 || t > f.fim + 0.6) && t > 0.5 && t < T.total - 0.5;
const tr = momentaneo(B('trilha.wav')).filter(x => x.M > -60);
const sob = media(tr.filter(x => naFala(x.t))), livre = media(tr.filter(x => foraFala(x.t)));
const mix = momentaneo(B('mix.wav'));
const loud = JSON.parse(fs.readFileSync(B('loudness.json'), 'utf8'));
const linhas = [
  `Volume integrado: ${loud.integrado} LUFS (alvo cerca de -16)  ${Math.abs(loud.integrado + 16) <= 1 ? 'ok' : 'FORA'}`,
  `Pico real: ${loud.picoReal} dBTP (máximo -1,5)  ${loud.picoReal <= -1.5 ? 'ok' : 'FORA'}`,
  `Faixa de volume (LRA): ${loud.LRA} LU`,
  `Trilha sob a voz: ${sob.toFixed(1)} LUFS momentâneo; sem voz: ${livre.toFixed(1)} → desce ${(livre - sob).toFixed(1)} dB  ${livre - sob >= 6 ? 'ok' : 'POUCO'}`,
  `Mix mais alto momentâneo: ${Math.max(...mix.map(x => x.M)).toFixed(1)} LUFS`,
];
console.log(linhas.join('\n'));
fs.mkdirSync(path.join(RAIZ, 'verificacao'), { recursive: true });
fs.writeFileSync(path.join(RAIZ, 'verificacao/audio.txt'), linhas.join('\n') + '\n');
/* forma de onda do mix com as falas em azul claro por baixo */
const W = 2000, H = 360, px = t => Math.round(t / T.total * W);
const caixas = T.falas.map(f => `drawbox=x=${px(f.inicio)}:y=0:w=${Math.max(1, px(f.fim) - px(f.inicio))}:h=${H}:color=0x2b59ff@0.12:t=fill`).join(',');
ffmpeg(['-i', B('mix.wav'), '-filter_complex', `showwavespic=s=${W}x${H}:colors=0x16130f:split_channels=0,format=rgba[w];color=c=0xf2ede4:s=${W}x${H}[f];[f]${caixas}[f2];[f2][w]overlay=format=auto`, '-frames:v', '1', path.join(RAIZ, 'verificacao/forma-de-onda.png')]);
console.log('forma de onda: verificacao/forma-de-onda.png');
