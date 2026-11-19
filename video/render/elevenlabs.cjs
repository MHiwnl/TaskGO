// Narração pela ElevenLabs: o texto inteiro numa tomada só (entonação contínua), cortado depois nas 10 falas
// pelos tempos de cada letra que a API devolve. Grava narracao/voz/NN.wav, que o resto do filme já usa.
//   node render/elevenlabs.cjs --vozes        lista as vozes da sua conta (nome, id, sotaque)
//   node render/elevenlabs.cjs --amostra      só a primeira fala, para ouvir a voz antes de gastar créditos
//   node render/elevenlabs.cjs                narração completa
// Chave e voz ficam em video/.env.elevenlabs (fora do git):
//   ELEVENLABS_API_KEY=...
//   ELEVENLABS_VOICE_ID=...
const fs = require('node:fs'), path = require('node:path');
const { RAIZ, ffmpeg } = require('./ferramentas.cjs');
const arg = k => process.argv.includes('--' + k);

const ENV = path.join(RAIZ, '.env.elevenlabs');
if (!fs.existsSync(ENV)) {
  fs.writeFileSync(ENV, 'ELEVENLABS_API_KEY=\nELEVENLABS_VOICE_ID=\n');
  console.log('Criei video/.env.elevenlabs. Cole a sua chave nele (não no chat) e rode de novo.');
  process.exit(1);
}
const env = Object.fromEntries(fs.readFileSync(ENV, 'utf8').split(/\r?\n/).filter(l => l.includes('=')).map(l => { const i = l.indexOf('='); return [l.slice(0, i).trim(), l.slice(i + 1).trim()]; }));
const CHAVE = env.ELEVENLABS_API_KEY, VOZ = env.ELEVENLABS_VOICE_ID;
if (!CHAVE) { console.log('Falta ELEVENLABS_API_KEY em video/.env.elevenlabs.'); process.exit(1); }
const API = 'https://api.elevenlabs.io/v1';

async function chamar(rota, corpo) {
  const r = await fetch(API + rota, { method: corpo ? 'POST' : 'GET', headers: { 'xi-api-key': CHAVE, 'content-type': 'application/json' }, body: corpo ? JSON.stringify(corpo) : undefined });
  if (!r.ok) throw new Error(`ElevenLabs respondeu ${r.status}: ${(await r.text()).slice(0, 300)}`);
  return r.json();
}

/* --take: a tomada foi gerada no site (plano grátis não usa voz da biblioteca pela API).
   Corta narracao/elevenlabs/take.mp3 nas falas pelos respiros: entre os silêncios achados, escolhe os
   N-1 cortes cuja posição mais se aproxima da proporção de texto de cada fala. */
function cortarPorRespiro() {
  const { ffmpegErr, duracao } = require('./ferramentas.cjs');
  const falas = JSON.parse(fs.readFileSync(path.join(RAIZ, 'narracao/falas.json'), 'utf8'));
  const pasta = path.join(RAIZ, 'narracao/elevenlabs'), achado = fs.existsSync(pasta) && fs.readdirSync(pasta).find(x => x.toLowerCase() === 'take.mp3');
  const take = path.join(pasta, achado || 'take.mp3');
  if (!fs.existsSync(take)) { console.log('Falta narracao/elevenlabs/take.mp3 (baixe do site com esse nome).'); process.exit(1); }
  const total = duracao(take);
  const log = ffmpegErr(['-i', take, '-af', 'silencedetect=n=-38dB:d=0.12', '-f', 'null', '-']);
  const ini = [...log.matchAll(/silence_start: ([\d.]+)/g)].map(m => +m[1]), fim = [...log.matchAll(/silence_end: ([\d.]+)/g)].map(m => +m[1]);
  const respiros = fim.map((f, i) => ({ meio: (ini[i] + f) / 2, dur: f - ini[i] })).filter(r => r.meio > 0.3 && r.meio < total - 0.3);
  /* onde cada corte "deveria" cair, pela quantidade de letras faladas */
  const letras = falas.map(f => f.texto.replace(/[^\p{L}\d]/gu, '').length), soma = letras.reduce((a, b) => a + b, 0);
  let acc = 0; const alvo = letras.slice(0, -1).map(n => (acc += n) / soma * total);
  /* programação dinâmica: cortes em ordem, cada um num respiro diferente, pena = distância ao alvo - bônus de respiro longo */
  const R = respiros.length, N = alvo.length;
  if (R < N) { console.log(`Achei só ${R} respiros para ${N} cortes. Gere a tomada com pausas entre as frases.`); process.exit(1); }
  const custo = (k, r) => Math.abs(respiros[r].meio - alvo[k]) - Math.min(respiros[r].dur, 0.6) * 1.5;
  const D = Array.from({ length: N }, () => new Array(R).fill(Infinity)), P = Array.from({ length: N }, () => new Array(R).fill(-1));
  for (let r = 0; r < R; r++) D[0][r] = custo(0, r);
  for (let k = 1; k < N; k++) for (let r = k; r < R; r++) for (let q = k - 1; q < r; q++) { const v = D[k - 1][q] + custo(k, r); if (v < D[k][r]) { D[k][r] = v; P[k][r] = q; } }
  let r = D[N - 1].indexOf(Math.min(...D[N - 1])); const cortes = [];
  for (let k = N - 1; k >= 0; k--) { cortes.unshift(respiros[r].meio); r = P[k][r]; }
  const voz = path.join(RAIZ, 'narracao/voz'); fs.mkdirSync(voz, { recursive: true });
  for (const x of fs.readdirSync(voz)) fs.rmSync(path.join(voz, x));
  /* nivela a tomada inteira de uma vez (sem degrau de volume entre as frases), depois corta */
  const nivelada = path.join(RAIZ, 'narracao/elevenlabs/take_nivelada.wav');
  const m = ffmpegErr(['-i', take, '-af', 'highpass=f=70,loudnorm=I=-19:TP=-2:LRA=9:print_format=json', '-f', 'null', '-']);
  const j = JSON.parse(m.slice(m.lastIndexOf('{'), m.lastIndexOf('}') + 1));
  ffmpeg(['-i', take, '-ac', '1', '-af', `highpass=f=70,loudnorm=I=-19:TP=-2:LRA=9:measured_I=${j.input_i}:measured_TP=${j.input_tp}:measured_LRA=${j.input_lra}:measured_thresh=${j.input_thresh}:offset=${j.target_offset}:linear=true,aresample=48000`, '-c:a', 'pcm_s16le', nivelada]);
  const pontos = [0, ...cortes, total];
  fs.writeFileSync(path.join(voz, 'take.json'), JSON.stringify({ origem: 'ElevenLabs (site), voz Lucas', pontos }));
  falas.forEach((f, i) => {
    ffmpeg(['-ss', pontos[i].toFixed(3), '-to', pontos[i + 1].toFixed(3), '-i', nivelada, '-ac', '1', '-ar', '48000', '-c:a', 'pcm_s16le', path.join(voz, f.id + '.wav')]);
    console.log(`${f.id}  ${pontos[i].toFixed(2)}–${pontos[i + 1].toFixed(2)} s  (alvo ${i ? alvo[i - 1].toFixed(2) : '0.00'})  ${f.texto.slice(0, 40)}`);
  });
  console.log(`pronto: ${falas.length} falas de uma tomada de ${total.toFixed(2)} s`);
}

(async () => {
  if (arg('take')) return cortarPorRespiro();
  if (arg('vozes')) {
    const { voices } = await chamar('/voices');
    for (const v of voices) console.log(`${v.voice_id}  ${v.name}  ${Object.values(v.labels || {}).join(', ')}`);
    return;
  }
  if (!VOZ) { console.log('Falta ELEVENLABS_VOICE_ID em video/.env.elevenlabs (veja as opções com --vozes).'); process.exit(1); }

  const falas = JSON.parse(fs.readFileSync(path.join(RAIZ, 'narracao/falas.json'), 'utf8'));
  const usadas = arg('amostra') ? falas.slice(0, 1) : falas;
  /* um texto só; guarda onde cada fala começa e termina dentro dele */
  let texto = '', faixas = [];
  for (const f of usadas) { if (texto) texto += ' '; const de = texto.length; texto += f.tts || f.texto; faixas.push({ id: f.id, de, ate: texto.length }); }

  const pasta = path.join(RAIZ, 'narracao/elevenlabs'); fs.mkdirSync(pasta, { recursive: true });
  console.log(`gerando ${texto.length} caracteres (${usadas.length} fala${usadas.length > 1 ? 's' : ''})...`);
  const r = await chamar(`/text-to-speech/${VOZ}/with-timestamps?output_format=mp3_44100_128`, {
    text: texto, model_id: 'eleven_multilingual_v2', language_code: 'pt',
    /* estabilidade média-baixa = mais expressiva, menos "locutor de URA" */
    voice_settings: { stability: 0.4, similarity_boost: 0.8, style: 0.35, use_speaker_boost: true }
  });
  const take = path.join(pasta, arg('amostra') ? 'amostra.mp3' : 'take.mp3');
  fs.writeFileSync(take, Buffer.from(r.audio_base64, 'base64'));
  fs.writeFileSync(path.join(pasta, 'alinhamento.json'), JSON.stringify(r.alignment));
  if (arg('amostra')) { console.log('amostra: ' + path.relative(RAIZ, take)); return; }

  /* corte: no meio do respiro entre o fim de uma fala e o começo da seguinte */
  const A = r.alignment, ini = A.character_start_times_seconds, fim = A.character_end_times_seconds;
  const limites = faixas.map(f => ({ id: f.id, de: ini[f.de], ate: fim[f.ate - 1] }));
  const voz = path.join(RAIZ, 'narracao/voz'); fs.mkdirSync(voz, { recursive: true });
  for (const x of fs.readdirSync(voz)) fs.rmSync(path.join(voz, x));
  limites.forEach((l, i) => {
    const de = i === 0 ? 0 : (limites[i - 1].ate + l.de) / 2;
    const ate = i === limites.length - 1 ? l.ate + 0.6 : (l.ate + limites[i + 1].de) / 2;
    ffmpeg(['-ss', de.toFixed(3), '-to', ate.toFixed(3), '-i', take, '-ac', '1', '-ar', '48000', '-c:a', 'pcm_s16le', path.join(voz, l.id + '.wav')]);
    console.log(`${l.id}  ${de.toFixed(2)}–${ate.toFixed(2)} s`);
  });
  console.log('pronto: narracao/voz/01..' + limites[limites.length - 1].id + '.wav (tomada inteira em narracao/elevenlabs/take.mp3)');
})().catch(e => { console.error(e.message); process.exit(1); });
