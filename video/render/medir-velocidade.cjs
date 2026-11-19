// Mede o custo da captura com e sem o grão (mistura multiply em tela cheia) e a máscara do mapa.
const { abrirFilme } = require('./quadros.cjs');
const ms = async (b, n, f) => { const a = Date.now(); for (let i = 0; i < n; i++) await f(i); return (Date.now() - a) / n; };
(async () => {
  const { b } = await abrirFilme('16x9', false, true);
  const foto = () => b.cmd('Page.captureScreenshot', { format: 'png', optimizeForSpeed: true });
  await b.js('seek(25);1');
  const base = await ms(b, 8, foto);
  await b.js("document.getElementById('grao').style.display='none';1"); const semGrao = await ms(b, 8, foto);
  await b.js("document.querySelector('svg.mundo').style.maskImage='none';document.querySelector('svg.mundo').style.webkitMaskImage='none';1"); const semNada = await ms(b, 8, foto);
  console.log(`png: tudo ${base.toFixed(0)} ms | sem grão ${semGrao.toFixed(0)} ms | sem grão e sem máscara ${semNada.toFixed(0)} ms`);
  await b.fechar();
  console.log('núcleos de CPU: ' + require('node:os').cpus().length);
})();
