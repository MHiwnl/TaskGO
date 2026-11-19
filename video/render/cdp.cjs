// Chrome sem janela controlado pelo protocolo de depuração (sem dependências).
const { spawn } = require('node:child_process');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const CHROME = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'].find(c => c && fs.existsSync(c));
const espera = ms => new Promise(r => setTimeout(r, ms));

async function abrir({ largura = 1920, altura = 1080 } = {}) {
  if (!CHROME) throw new Error('Google Chrome não encontrado (defina CHROME_PATH).');
  /* Porta 0: o Chrome escolhe uma porta livre e a escreve em DevToolsActivePort. Porta sorteada podia repetir
     entre Chromes em paralelo, e dois trechos passavam a mandar no mesmo Chrome (quadros misturados). */
  const perfil = fs.mkdtempSync(path.join(os.tmpdir(), 'filme-'));
  /* Mesma renderização sempre: sem GPU, sem rolagem animada, cores sRGB, fontes com hinting fixo. */
  const ch = spawn(CHROME, ['--headless=new', '--remote-debugging-port=0', '--user-data-dir=' + perfil, '--no-first-run', '--hide-scrollbars',
    '--disable-gpu', '--force-color-profile=srgb', '--font-render-hinting=none', '--disable-lcd-text', '--window-size=' + largura + ',' + altura, 'about:blank'], { stdio: 'ignore' });
  let porta = null; for (let i = 0; i < 80 && !porta; i++) { try { porta = fs.readFileSync(path.join(perfil, 'DevToolsActivePort'), 'utf8').split(/\s+/)[0] || null; } catch { } if (!porta) await espera(150); }
  if (!porta) throw new Error('Chrome não informou a porta de controle.');
  let alvo; for (let i = 0; i < 80 && !alvo; i++) { try { alvo = (await (await fetch('http://127.0.0.1:' + porta + '/json')).json()).find(t => t.type === 'page'); } catch { } if (!alvo) await espera(250); }
  if (!alvo) throw new Error('Chrome não respondeu.');
  const ws = new WebSocket(alvo.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
  let id = 0; const pend = {}, erros = [];
  ws.onmessage = m => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) { pend[d.id](d); delete pend[d.id]; }
    if (d.method === 'Runtime.exceptionThrown') erros.push(d.params.exceptionDetails.exception?.description || d.params.exceptionDetails.text); };
  const cmd = (method, params = {}) => new Promise(r => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
  const js = async e => { const r = await cmd('Runtime.evaluate', { expression: e, awaitPromise: true, returnByValue: true });
    if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text); return r.result?.result?.value; };
  await cmd('Runtime.enable'); await cmd('Page.enable');
  const tamanho = async (w, h) => cmd('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false });
  await tamanho(largura, altura);
  const ir = async (url, ms = 800) => { await cmd('Page.navigate', { url }); await espera(ms); for (let i = 0; i < 50; i++) { if (await js("document.readyState==='complete'")) break; await espera(100); } await js('document.fonts.ready.then(()=>1)'); };
  const foto = async (arquivo, opcoes = {}) => { const r = await cmd('Page.captureScreenshot', { format: 'png', captureBeyondViewport: !!opcoes.inteira, ...(opcoes.clip ? { clip: opcoes.clip } : {}) });
    const buf = Buffer.from(r.result.data, 'base64'); if (arquivo) fs.writeFileSync(arquivo, buf); return buf; };
  const fechar = async () => { try { ws.close(); } catch { } ch.kill(); await espera(300); try { fs.rmSync(perfil, { recursive: true, force: true }); } catch { } };
  return { cmd, js, ir, foto, tamanho, fechar, erros };
}
module.exports = { abrir, espera };

/* Uso direto: node cdp.cjs <url> <largura> <altura> <saida.png> [inteira] */
if (require.main === module) (async () => {
  const [url, w, h, out, inteira] = process.argv.slice(2);
  const b = await abrir({ largura: +w || 1280, altura: +h || 800 });
  await b.ir(url, 1500);
  if (inteira) { const alt = await b.js('document.documentElement.scrollHeight'); await b.tamanho(+w || 1280, alt); }
  await b.foto(out); console.log('foto: ' + out + (b.erros.length ? '  ERROS: ' + b.erros.join(' | ') : ''));
  await b.fechar();
})().catch(e => { console.error(e); process.exit(1); });
