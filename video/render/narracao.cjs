// Prepara a narração: para cada fala, usa a gravação do usuário (narracao/voz/NN*.*) ou, sem ela, a faixa-guia (narracao/guia/NN.wav).
// Converte para 48 kHz mono, tira ruído de fundo leve e o silêncio das pontas, nivela cada fala (-19 LUFS, voz em mono)
// e grava narracao/prep/NN.wav + narracao/duracoes.json (o tempo do filme sai daqui).
const fs = require('node:fs'), path = require('node:path');
const { RAIZ, ffmpeg, duracao } = require('./ferramentas.cjs');
const N = p => path.join(RAIZ, 'narracao', p);
const falas = JSON.parse(fs.readFileSync(N('falas.json'), 'utf8'));
fs.mkdirSync(N('prep'), { recursive: true });
const voz = fs.existsSync(N('voz')) ? fs.readdirSync(N('voz')).filter(f => /\.(m4a|mp3|ogg|opus|wav|aac|webm|flac|3gp|amr)$/i.test(f)) : [];
const saida = [];
/* Tomada contínua (render/elevenlabs.cjs --take): as falas já vêm niveladas juntas e com as pausas naturais.
   Não corta silêncio nem nivela frase por frase: só copia. O filme emenda as cenas sem respiro artificial. */
const TAKE = N('voz/take.json');
if (fs.existsSync(TAKE)) {
  for (const f of falas) {
    const destino = N('prep/' + f.id + '.wav');
    ffmpeg(['-i', N('voz/' + f.id + '.wav'), '-ac', '1', '-ar', '48000', '-c:a', 'pcm_s16le', destino]);
    const d = duracao(destino);
    saida.push({ id: f.id, texto: f.texto, origem: 'tomada', continuo: true, arquivo: 'narracao/prep/' + f.id + '.wav', dur: Math.round(d * 1000) / 1000 });
    console.log(f.id + '  tomada  ' + d.toFixed(2) + ' s');
  }
  fs.writeFileSync(N('duracoes.json'), JSON.stringify(saida, null, 1));
  console.log('total de fala: ' + saida.reduce((a, x) => a + x.dur, 0).toFixed(2) + ' s (tomada contínua)');
  process.exit(0);
}
for (const f of falas) {
  /* Mais de uma tentativa (03-a, 03-b): usa a última. */
  const minhas = voz.filter(x => x.startsWith(f.id)).sort();
  const origem = minhas.length ? N('voz/' + minhas[minhas.length - 1]) : N('guia/' + f.id + '.wav');
  const tipo = minhas.length ? 'voz' : 'guia';
  if (!fs.existsSync(origem)) throw new Error('Falta a fala ' + f.id);
  const destino = N('prep/' + f.id + '.wav');
  /* afftdn: ruído de fundo leve; highpass: tira ronco; silenceremove nas duas pontas; loudnorm por fala. */
  const corta = 'silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse';
  const filtro = (tipo === 'voz' ? 'highpass=f=80,' : '') + corta + ',loudnorm=I=-19:TP=-2:LRA=7,aresample=48000';
  ffmpeg(['-i', origem, '-ac', '1', '-af', filtro, '-ar', '48000', '-c:a', 'pcm_s16le', destino]);
  const d = duracao(destino);
  saida.push({ id: f.id, texto: f.texto, origem: tipo, arquivo: 'narracao/prep/' + f.id + '.wav', dur: Math.round(d * 1000) / 1000 });
  console.log(f.id + '  ' + tipo.padEnd(4) + '  ' + d.toFixed(2) + ' s  ' + path.basename(origem));
}
fs.writeFileSync(N('duracoes.json'), JSON.stringify(saida, null, 1));
console.log('total de fala: ' + saida.reduce((a, x) => a + x.dur, 0).toFixed(2) + ' s');
