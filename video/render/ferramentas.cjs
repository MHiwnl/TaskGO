// Onde estão o ffmpeg e o ffprobe (instalados pelo winget) e ajudas comuns.
const fs = require('node:fs'), path = require('node:path'), os = require('node:os');
const { execFileSync } = require('node:child_process');
function achar(nome) {
  const exe = nome + '.exe';
  for (const d of (process.env.PATH || '').split(path.delimiter)) { const p = path.join(d, exe); if (d && fs.existsSync(p)) return p; }
  const raiz = path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'), 'Microsoft', 'WinGet', 'Packages');
  if (fs.existsSync(raiz)) for (const pk of fs.readdirSync(raiz).filter(x => /FFmpeg/i.test(x))) {
    const base = path.join(raiz, pk); for (const sub of fs.readdirSync(base)) { const p = path.join(base, sub, 'bin', exe); if (fs.existsSync(p)) return p; }
  }
  throw new Error(nome + ' não encontrado. Instale com: winget install Gyan.FFmpeg');
}
const FFMPEG = achar('ffmpeg'), FFPROBE = achar('ffprobe');
const RAIZ = path.resolve(__dirname, '..');
const ffmpeg = (args, opcoes = {}) => String(execFileSync(FFMPEG, ['-hide_banner', '-y', ...args], { stdio: opcoes.saida ? 'pipe' : ['ignore', 'ignore', 'pipe'], maxBuffer: 1 << 28, ...opcoes }) ?? '');
const ffmpegLog = args => { try { return execFileSync(FFMPEG, ['-hide_banner', '-nostats', ...args], { stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 1 << 28 }).toString(); } catch (e) { return String(e.stderr || '') + String(e.stdout || ''); } };
/* ffmpeg escreve as medições no stderr: */
const ffmpegErr = args => { const r = require('node:child_process').spawnSync(FFMPEG, ['-hide_banner', '-nostats', ...args], { maxBuffer: 1 << 28 }); return String(r.stderr || ''); };
const duracao = arq => parseFloat(execFileSync(FFPROBE, ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', arq]).toString().trim());
module.exports = { FFMPEG, FFPROBE, RAIZ, ffmpeg, ffmpegLog, ffmpegErr, duracao };
