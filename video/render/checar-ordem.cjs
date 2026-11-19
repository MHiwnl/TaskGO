// O vídeo final mostra, em cada instante, o quadro certo? Compara o quadro tirado do MP4 com o mesmo t renderizado à parte (PSNR).
// node render/checar-ordem.cjs master_16x9.mp4 16x9 [reduzido]
const fs = require('node:fs'), path = require('node:path');
const { RAIZ, ffmpegErr, ffmpeg } = require('./ferramentas.cjs');
const { abrirFilme } = require('./quadros.cjs');
const [arq = 'master_16x9.mp4', fmt = '16x9', red] = process.argv.slice(2);
(async () => {
  const { b } = await abrirFilme(fmt, !!red, true);
  const cenas = await b.js('TIMELINE.cenas.map(c=>c.inicio+c.dur*0.6)');
  const dir = path.join(RAIZ, 'build/ordem'); fs.mkdirSync(dir, { recursive: true });
  let ruins = 0; const linhas = [];
  for (const [k, t0] of cenas.entries()) {
    const t = Math.round(t0 * 60) / 60;   /* um quadro exato do vídeo */
    const ref = path.join(dir, k + '_ref.png'), vid = path.join(dir, k + '_vid.png');
    fs.writeFileSync(ref, await b.quadro(t));
    ffmpeg(['-i', path.join(RAIZ, 'out', arq), '-vf', `trim=start_frame=${Math.round(t * 60)}:end_frame=${Math.round(t * 60) + 1}`, '-frames:v', '1', vid]);  /* o quadro pelo número, sem arredondamento de tempo */
    const r = ffmpegErr(['-i', ref, '-i', vid, '-lavfi', 'psnr', '-f', 'null', '-']);
    const psnr = parseFloat((r.match(/average:([\d.]+|inf)/) || [])[1]) || 99;
    const ok = psnr >= 30; if (!ok) ruins++;
    linhas.push(`t=${t.toFixed(2)}s  PSNR ${psnr.toFixed(1)} dB  ${ok ? 'ok' : 'ERRADO'}`);
  }
  await b.fechar();
  linhas.push(`${arq}: ${cenas.length - ruins} de ${cenas.length} batidas com o quadro certo`);
  console.log(linhas.join('\n'));
  fs.appendFileSync(path.join(RAIZ, 'verificacao/ordem.txt'), linhas.join('\n') + '\n');
  process.exit(ruins ? 1 : 0);
})();
