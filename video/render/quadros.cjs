// Quadros parados do filme em instantes escolhidos, e a folha de contato.
// node render/quadros.cjs --fmt 16x9 [--reduzido] [--t 1.2,3.4] [--cenas] [--saida frames/x] [--contato contact.png] [--largura 390]
const fs = require('node:fs'), path = require('node:path');
const { abrir } = require('./cdp.cjs');
const { RAIZ, ffmpeg } = require('./ferramentas.cjs');
const arg = (k, d) => { const i = process.argv.indexOf('--' + k); return i < 0 ? d : (process.argv[i + 1] && !process.argv[i + 1].startsWith('--') ? process.argv[i + 1] : true); };

function gravarDuracoes() {
  const d = JSON.parse(fs.readFileSync(path.join(RAIZ, 'narracao/duracoes.json'), 'utf8'));
  fs.writeFileSync(path.join(RAIZ, 'src/duracoes.js'), '/* gerado por render/narracao.cjs + render (não editar) */\nwindow.DURACOES = ' + JSON.stringify(d) + ';\n');
  return d;
}
const urlFilme = (fmt, red, leg = true) => 'file:///' + path.join(RAIZ, 'src/film.html').replace(/\\/g, '/') + '?fmt=' + fmt + (red ? '&reduzido=1' : '') + (leg ? '' : '&legenda=0');
const DIM = { '16x9': [1920, 1080], '9x16': [1080, 1920], '1x1': [1080, 1080] };

async function abrirFilme(fmt, red, leg) {
  const [w, h] = DIM[fmt];
  const b = await abrir({ largura: w, altura: h });
  await b.ir(urlFilme(fmt, red, leg), 1200);
  const info = await b.js('window.PRONTO');
  /* espera duas pinturas depois de cada seek: o quadro capturado é o do tempo pedido */
  b.quadro = async t => { await b.js(`(async()=>{seek(${t});await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));return 1})()`); return b.foto(null, { clip: { x: 0, y: 0, width: w, height: h, scale: 1 } }); };
  return { b, info, w, h };
}

if (require.main === module) (async () => {
  gravarDuracoes();
  const fmt = arg('fmt', '16x9'), red = !!arg('reduzido', false), saida = path.join(RAIZ, arg('saida', 'frames/' + fmt + (red ? '-red' : '')));
  fs.mkdirSync(saida, { recursive: true });
  const { b, info } = await abrirFilme(fmt, red, !arg('semlegenda', false));
  const T = await b.js('({cenas:TIMELINE.cenas.map(c=>({id:c.id,inicio:c.inicio,fim:c.fim,dur:c.dur})),total:TIMELINE.total})');
  let tempos;
  if (arg('t')) tempos = String(arg('t')).split(',').map(Number);
  else tempos = T.cenas.flatMap(c => [c.inicio + c.dur * 0.35, c.inicio + c.dur * 0.85]);
  const arquivos = [];
  for (const [i, t] of tempos.entries()) {
    const f = path.join(saida, String(i + 1).padStart(2, '0') + '_' + t.toFixed(2) + 's.png');
    fs.writeFileSync(f, await b.quadro(t)); arquivos.push(f);
  }
  console.log(arquivos.length + ' quadros em ' + path.relative(RAIZ, saida) + (b.erros.length ? '  ERROS JS: ' + b.erros.join(' | ') : '  sem erro de JS'));
  await b.fechar();
  /* folha de contato: 4 colunas, cada quadro reduzido a 480 de largura */
  if (arg('contato')) {
    const cont = path.join(RAIZ, String(arg('contato')));
    const cols = 4, linhas = Math.ceil(arquivos.length / cols);
    const lista = path.join(saida, 'lista.txt');
    fs.writeFileSync(lista, arquivos.map(f => "file '" + f.replace(/\\/g, '/') + "'").join('\n'));
    const larg = fmt === '16x9' ? 480 : fmt === '1x1' ? 360 : 270;
    ffmpeg(['-f', 'concat', '-safe', '0', '-i', lista, '-vf', `scale=${larg}:-1,pad=iw+12:ih+12:6:6:color=0xf2ede4,tile=${cols}x${linhas}:color=0xf2ede4`, '-frames:v', '1', cont]);
    console.log('folha de contato: ' + path.relative(RAIZ, cont));
  }
})().catch(e => { console.error(e); process.exit(1); });

module.exports = { abrirFilme, gravarDuracoes, urlFilme, DIM };
