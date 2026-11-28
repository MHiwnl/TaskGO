"""Mixagem do filme TaskGO: narração (vo/), foley (core/audio/sfx.py) e música sintetizada a partir das notas da página.

Uso: python mix.py <events.json> <pasta_vo> <saida_mix.wav> <cues.json>
  events.json: {dur, ev} exportado por core/render/events.mjs (eventos do próprio filme)
  cues.json:   legendas [{t0, t1, text}] para core/render/srt.py
Ambiente: LIB aponta para a biblioteca lemo-opuscar (importa core/audio/sfx.py).
Música: sem amostras; pads e marimba sintetizados (numpy). Silêncios do filme ficam mudos.
"""
import json, os, sys
import numpy as np
import soundfile as sf
from scipy.ndimage import uniform_filter1d
from scipy.signal import resample_poly

LIB = os.environ.get('LIB', 'C:/Users/Mendes/lemo-opuscar')
sys.path.insert(0, os.path.join(LIB, 'core', 'audio'))
import sfx  # noqa: E402

ev_path, vo_dir, out_wav, cues_path = sys.argv[1:5]
data = json.load(open(ev_path, encoding='utf-8'))
DUR, EV = data['dur'], data['ev']
SR = sfx.SR
N = int(round((DUR + 0.5) * SR))
foley = np.zeros((N, 2))
music = np.zeros((N, 2))
voice = np.zeros((N, 2))
rng = np.random.default_rng(11)
idx = lambda t: int(round(t * SR))

silences = [(e['t'], e['t'] + e['d']) for e in EV if e['type'] == 'silencio']
def silenciado(t): return any(a <= t < b for a, b in silences)

# ── Narração: arquivos da ElevenLabs (24 kHz) no nível da voz ────────────────
for e in EV:
    if e['type'] != 'voz':
        continue
    x, sr = sf.read(os.path.join(vo_dir, e['id'] + '.wav'), dtype='float64')
    if x.ndim > 1: x = x.mean(axis=1)
    if sr != SR: x = resample_poly(x, SR // sr, 1)
    rms = np.sqrt(np.mean(x ** 2)) or 1.0
    x = x * (0.1 / rms)  # cerca de −20 dBFS de média por fala
    s = idx(e['t'])
    voice[s:s + len(x), 0] += x
    voice[s:s + len(x), 1] += x

# ── Foley: cada evento visível tem um som; a família do produto é uma só ─────
def fx(som, t, ganho=1.0, pan=0.0):
    if not silenciado(t): sfx.add(foley, som, t, ganho, pan)

def varredura(d=1.0):
    t = sfx.t_(d); f = 900 + 1700 * t / d
    return 0.25 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / d) ** 2

for e in EV:
    t, ty = e['t'], e['type']
    if ty in ('silencio', 'legenda', 'voz', 'nota'):
        continue
    if ty == 'tecla':
        fx(sfx.click(pitch=1.0, v=0.25), t, 0.5)
    elif ty == 'toque':
        fx(sfx.thump(v=0.7, f=70), t, 0.8); fx(sfx.click(pitch=0.9, v=0.5), t, 0.6)
    elif ty in ('expande', 'whoosh', 'retrai'):
        fx(sfx.whoosh(d=0.4, v=0.5), t, 0.5)
    elif ty == 'crt':
        fx(sfx.whoosh(d=0.6, v=0.6), t, 0.6)
    elif ty == 'vidro':  # cena da desordem: cada aviso com timbre e posição próprios
        fx(sfx.clack(pitch=0.8 + 0.6 * rng.random(), v=0.5), t, 0.5, e.get('pan', 0.0))
    elif ty == 'pouso':  # produto: toques de feltro centrados
        fx(sfx.click(pitch=1.25, v=0.5), t, 0.5)
    elif ty == 'tick':
        fx(sfx.click(pitch=1.5, v=0.4), t, 0.5)
    elif ty == 'varredura':
        s = idx(t); w = varredura(1.0)
        foley[s:s + len(w), 0] += w; foley[s:s + len(w), 1] += w
    elif ty == 'digito':  # grão de vidro por dígito
        fx(sfx.click(pitch=2.0, v=0.6), t, 0.6)
    elif ty == 'estrela':
        fx(sfx.click(pitch=1.0 + 0.12 * e.get('k', 0), v=0.4), t, 0.5)
    elif ty == 'chegada':
        fx(sfx.thump(v=0.8, f=60), t, 0.9); fx(sfx.ding(v=0.5), t, 0.5)
    elif ty in ('pix', 'acorde'):
        fx(sfx.ding(v=0.6), t, 0.6)
    else:
        raise SystemExit(f'tipo de evento sem som: {ty}')

# ── Música: notas da página, sintetizadas (marimba, pad, pulso) ──────────────
def marimba(f, d, a):
    sust = d > 1.5  # notas que sustentam até serem subtraídas
    n = int((d + 0.6 if sust else 2.2) * SR); t = np.arange(n) / SR
    if sust:
        env = np.minimum(1, t / 0.02) * np.exp(-np.maximum(0, t - d) / 0.2)
        x = np.sin(2 * np.pi * f * t) + 0.15 * np.sin(2 * np.pi * 2 * f * t)
    else:
        env = np.exp(-t / 0.35) * (1 - np.exp(-t / 0.003))
        x = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t / 0.08)
    return a * env * x

def pad(f, d, a):
    n = int((d + 1.2) * SR); t = np.arange(n) / SR
    env = np.minimum(1, t / 0.5) * np.exp(-np.maximum(0, t - d) / 0.5)
    return a * 0.5 * env * (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * f * 1.004 * t))

def bass(f, d, a):
    n = int(0.6 * SR); t = np.arange(n) / SR
    return a * np.exp(-t / 0.25) * np.sin(2 * np.pi * f * (1 + 0.3 * np.exp(-t / 0.02)) * t)

for e in EV:
    if e['type'] != 'nota':
        continue
    k = e['k']
    w = {'marimba': marimba, 'pad': pad, 'bass': bass}[k](e['f'], e['d'], e['a'])
    sfx.add(music, w, e['t'], 1.0, e.get('p', 0.0))

mask = np.ones(N)
for a, b in silences:
    mask[idx(a):idx(b)] = 0.0
music *= mask[:, None]

# ── Equilíbrio: música abaixo da voz; trilha ~8 dB e foley ~4 dB sob a fala ──
env = uniform_filter1d(np.abs(voice.mean(axis=1)), size=int(0.05 * SR))
ativa = np.clip(env * 12.0, 0.0, 1.0)
gm = 1.0 - 0.6 * ativa          # −8 dB sob a voz
gf = 1.0 - 0.37 * ativa         # −4 dB sob a voz
musica_rms = np.sqrt(np.mean(music[music != 0] ** 2)) or 1.0
music *= 0.03 / musica_rms      # trilha cerca de 10 dB abaixo da voz

mix = music * gm[:, None] + foley * gf[:, None] + voice
for c in range(2):
    mix[:, c] = sfx.limit(mix[:, c], ceil=0.89)
sf.write(out_wav, mix.astype(np.float32), SR, subtype='PCM_24')

cues = [{'t0': e['t'], 't1': e['t1'], 'text': e['text']} for e in EV if e['type'] == 'legenda']
json.dump(cues, open(cues_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(f'mix {out_wav}: {N / SR:.2f} s, {sum(1 for e in EV if e["type"] == "voz")} falas, {len(cues)} legendas, '
      f'{sum(1 for e in EV if e["type"] == "nota")} notas, silêncios {len(silences)}')
