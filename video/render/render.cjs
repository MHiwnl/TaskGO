// Render do filme: narração → legenda → trilha e mixagem → vídeo quadro a quadro → junção.
// node render/render.cjs [--fmt 16x9|9x16|1x1|todos] [--reduzido] [--rapido] [--so-som] [--sem-som]
//   --rapido: animatic (30 q/s, metade da resolução) para conferir o tempo.
const fs = require('node:fs'), path = require('node:path');
const { spawn, execFileSync } = require('node:child_process');
const { RAIZ, FFMPEG, ffmpeg, ffmpegErr, duracao } = require('./ferramentas.cjs');
const { abrirFilme, gravarDuracoes, DIM } = require('./quadros.cjs');
const { montarTimeline } = require('../src/timeline.js');
const arg = k => process.argv.includes('--' + k);
const val = (k, d) => { const i = process.argv.indexOf('--' + k); return i < 0 ? d : process.argv[i + 1]; };
const B = p => path.join(RAIZ, 'build', p), O = p => path.join(RAIZ, 'out', p);
fs.mkdirSync(B(''), { recursive: true }); fs.mkdirSync(O(''), { recursive: true });

/* ---------- 1. narração e legenda ---------- */
function prepararNarracao() {
  execFileSync(process.execPath, [path.join(__dirname, 'narracao.cjs')], { stdio: 'inherit' });
  return montarTimeline(gravarDuracoes());
}
const hms = s => { const ms = Math.round(s * 1000), h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60, ss = Math.floor(ms / 1000) % 60; return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(ss).padStart(2, '0')},${String(ms % 1000).padStart(3, '0')}`; };
function quebra(txt, max = 42) { if (txt.length <= max) return txt; const ws = txt.split(' '); let best = null; for (let i = 1; i < ws.length; i++) { const a = ws.slice(0, i).join(' '), b = ws.slice(i).join(' '); const s = Math.max(a.length, b.length); if (!best || s < best.s) best = { s, t: a + '\n' + b }; } return best.t; }
function gravarSrt(T) {
  const srt = T.falas.map((f, i) => `${i + 1}\n${hms(f.inicio)} --> ${hms(f.fim + 0.15)}\n${quebra(f.texto)}\n`).join('\n');
  fs.writeFileSync(O('captions.srt'), srt); fs.copyFileSync(O('captions.srt'), path.join(RAIZ, 'captions.srt'));
  return srt;
}

/* ---------- 2. trilha, efeitos e mixagem ---------- */
function wav(arquivo, pcm, sr, canais) {
  const h = Buffer.alloc(44); h.write('RIFF', 0); h.writeUInt32LE(36 + pcm.length, 4); h.write('WAVE', 8); h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(canais, 22);
  h.writeUInt32LE(sr, 24); h.writeUInt32LE(sr * canais * 2, 28); h.writeUInt16LE(canais * 2, 32); h.writeUInt16LE(16, 34); h.write('data', 36); h.writeUInt32LE(pcm.length, 40);
  fs.writeFileSync(arquivo, Buffer.concat([h, pcm]));
}
async function som(T) {
  const { b } = await abrirFilme('16x9', false, false);
  const a = await b.js('renderAudio(TIMELINE)'); await b.fechar();
  wav(B('trilha.wav'), Buffer.from(a.b64, 'base64'), a.sr, a.canais);
  /* falas no lugar certo + trilha; depois normaliza em duas passadas (alvo -16 LUFS, pico real até -1,5 dBTP) */
  const ins = ['-i', B('trilha.wav')], fil = [];
  T.falas.forEach((f, i) => { ins.push('-i', path.join(RAIZ, f.arquivo)); const ms = Math.round(f.inicio * 1000); fil.push(`[${i + 1}]aformat=channel_layouts=stereo,adelay=${ms}|${ms}[f${i}]`); });
  fil.push('[0]' + T.falas.map((_, i) => `[f${i}]`).join('') + `amix=inputs=${T.falas.length + 1}:normalize=0:duration=first[m]`);
  ffmpeg([...ins, '-filter_complex', fil.join(';'), '-map', '[m]', '-ar', '48000', '-c:a', 'pcm_s24le', B('mix_bruto.wav')]);
  const medida = ffmpegErr(['-i', B('mix_bruto.wav'), '-af', 'loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json', '-f', 'null', '-']);
  const j = JSON.parse(medida.slice(medida.lastIndexOf('{'), medida.lastIndexOf('}') + 1));
  const ln = `loudnorm=I=-16:TP=-1.5:LRA=11:measured_I=${j.input_i}:measured_TP=${j.input_tp}:measured_LRA=${j.input_lra}:measured_thresh=${j.input_thresh}:offset=${j.target_offset}:linear=true`;
  ffmpeg(['-i', B('mix_bruto.wav'), '-af', ln + ',aresample=48000', '-c:a', 'pcm_s24le', B('mix.wav')]);
  /* trilha sem voz, para conferir "só o áudio" e o abaixamento */
  ffmpeg(['-i', B('trilha.wav'), '-af', 'volume=' + Math.pow(10, Number(j.target_offset) / 20 + (-16 - Number(j.input_i)) / 20).toFixed(4), '-c:a', 'pcm_s16le', B('trilha_no_nivel.wav')]);
  return medir(B('mix.wav'));
}
function medir(arq) {
  const r = ffmpegErr(['-i', arq, '-af', 'ebur128=peak=true', '-f', 'null', '-']);
  const resumo = r.slice(r.lastIndexOf('Summary:'));
  const I = parseFloat((resumo.match(/I:\s+(-?[\d.]+) LUFS/) || [])[1]), TP = parseFloat((resumo.match(/Peak:\s+(-?[\d.]+) dBFS/) || [])[1]);
  return { integrado: I, picoReal: TP, LRA: parseFloat((resumo.match(/LRA:\s+(-?[\d.]+) LU/) || [])[1]) };
}

/* ---------- 3. vídeo quadro a quadro ---------- */
/* Um trecho: um Chrome renderiza os quadros [de, ate) e manda cada PNG direto para o ffmpeg (nada vai para o disco). */
async function trecho(fmt, red, fps, escala, de, ate, arquivo, progresso) {
  const { b } = await abrirFilme(fmt, red, true);
  const [W, H] = DIM[fmt];
  /* marca do dono: cada trecho confere que está falando com o seu próprio Chrome */
  const dono = 'trecho-' + de + '-' + process.hrtime.bigint();
  await b.js(`window.__dono=${JSON.stringify(dono)};1`);
  const enc = spawn(FFMPEG, ['-hide_banner', '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'png', '-i', '-',
    '-c:v', 'libx264', '-preset', escala < 1 ? 'veryfast' : 'slow', '-crf', '16', '-pix_fmt', 'yuv420p', '-r', String(fps), arquivo], { stdio: ['pipe', 'inherit', 'inherit'] });
  const fimEnc = new Promise((ok, erro) => enc.on('close', c => c === 0 ? ok() : erro(new Error('ffmpeg saiu com ' + c))));
  for (let i = de; i < ate; i++) {
    /* cada quadro é função pura de t = i/fps; espera duas pinturas antes de capturar */
    const quem = await b.js(`(async()=>{seek(${i / fps});await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));return window.__dono})()`);
    if (quem !== dono) throw new Error('Trecho ' + de + ' falou com o Chrome de outro trecho.');
    const r = await b.cmd('Page.captureScreenshot', { format: 'png', optimizeForSpeed: true, clip: { x: 0, y: 0, width: W, height: H, scale: escala } });
    if (!enc.stdin.write(Buffer.from(r.result.data, 'base64'))) await new Promise(ok => enc.stdin.once('drain', ok));
    progresso();
  }
  enc.stdin.end(); await fimEnc;
  const erros = b.erros.slice(); await b.fechar();
  if (erros.length) throw new Error('Erros de JavaScript no filme: ' + erros.join(' | '));
}
async function video(fmt, red, rapido, partes = 4) {
  const fps = rapido ? 30 : 60, escala = rapido ? 0.5 : 1;
  const T = montarTimeline(JSON.parse(fs.readFileSync(path.join(RAIZ, 'narracao/duracoes.json'), 'utf8')));
  const n = Math.round(T.total * fps), nome = `v_${fmt}${red ? '_red' : ''}${rapido ? '_rapido' : ''}`, saida = B(nome + '.mp4');
  const t0 = Date.now(); let feitos = 0, ultimo = 0;
  const progresso = () => {
    feitos++;
    if (feitos - ultimo >= 240 || feitos === n) {
      ultimo = feitos;
      const fr = feitos / n, gasto = (Date.now() - t0) / 1000, falta = fr > 0 ? gasto / fr - gasto : 0, cheio = Math.round(fr * 30);
      process.stdout.write(`  ${fmt}${red ? ' reduzido' : ''} [${'#'.repeat(cheio)}${'-'.repeat(30 - cheio)}] ${(fr * 100).toFixed(0).padStart(3)}% · ${feitos}/${n} quadros · ${gasto.toFixed(0)} s · falta ~${Math.ceil(falta / 60)} min\n`);
    }
  };
  const cortes = Array.from({ length: partes + 1 }, (_, k) => Math.round(k * n / partes));
  const segs = cortes.slice(0, -1).map((de, k) => ({ de, ate: cortes[k + 1], arq: B(`${nome}_parte${k}.mp4`) }));
  await Promise.all(segs.map(s => trecho(fmt, red, fps, escala, s.de, s.ate, s.arq, progresso)));
  /* junta os trechos sem recodificar */
  const lista = B(nome + '_lista.txt'); fs.writeFileSync(lista, segs.map(s => "file '" + s.arq.replace(/\\/g, '/') + "'").join('\n'));
  ffmpeg(['-f', 'concat', '-safe', '0', '-i', lista, '-c', 'copy', '-movflags', '+faststart', saida]);
  for (const s of segs) fs.rmSync(s.arq, { force: true }); fs.rmSync(lista, { force: true });
  return { saida, n, fps };
}
function juntar(v, nome, total) {
  const final = O(nome);
  ffmpeg(['-i', v, '-i', B('mix.wav'), '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '256k', '-ar', '48000', '-t', total.toFixed(3), '-movflags', '+faststart', final]);
  return final;
}

(async () => {
  const T = prepararNarracao();
  gravarSrt(T);
  console.log(`timeline: ${T.total.toFixed(2)} s, ${T.quadros} quadros a 60 q/s; falas de ${[...new Set(T.falas.map(f => f.origem))].join(' e ')}`);
  if (!arg('sem-som') || !fs.existsSync(B('mix.wav'))) {
    const m = await som(T);
    fs.writeFileSync(B('loudness.json'), JSON.stringify(m, null, 1));
    console.log(`som: ${m.integrado} LUFS integrado, pico real ${m.picoReal} dBTP, LRA ${m.LRA} LU`);
  }
  if (arg('so-som')) return;
  const fmts = val('fmt', '16x9') === 'todos' ? ['16x9', '9x16', '1x1'] : [val('fmt', '16x9')];
  const nomes = { '16x9': 'master_16x9', '9x16': 'cut_9x16', '1x1': 'cut_1x1' };
  for (const fmt of fmts) {
    const red = arg('reduzido'), rapido = arg('rapido');
    const v = await video(fmt, red, rapido);
    const nome = (rapido ? 'animatic_' : '') + nomes[fmt] + (red ? '_movimento_reduzido' : '') + '.mp4';
    const f = juntar(v.saida, nome, T.total);
    console.log(`pronto: ${path.relative(RAIZ, f)} (${v.n} quadros, ${v.fps} q/s, ${duracao(f).toFixed(2)} s)`);
  }
})().catch(e => { console.error(e); process.exit(1); });
