"""Mixagem do Palito: narração (ElevenLabs), música sintetizada e efeitos, a partir de events-<fmt>.json.
Uso: python mix.py <h|v> <pasta do projeto> <pasta lib-opuscar>   ->  out/mix-<fmt>.wav
Tudo é determinístico (sem aleatoriedade fora da semente fixa da biblioteca)."""
import json, os, sys
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

FMT, PROJ, LIB = sys.argv[1], sys.argv[2], sys.argv[3]
sys.path.insert(0, os.path.join(LIB, 'core', 'audio'))
import sfx  # helpers da biblioteca: whoosh, filtros e envelopes (SR = 48000)

SR = sfx.SR
V = FMT == 'v'
DUR = 31.5 if V else 54.5
LINES = json.load(open(os.path.join(PROJ, 'lines_9x16.json' if V else 'lines.json'), encoding='utf-8'))
EV = json.load(open(os.path.join(PROJ, f'events-{FMT}.json'), encoding='utf-8'))['ev']
SHARED = {'V04': 'L09', 'V08': 'L15', 'V09': 'L16', 'V10': 'L17'}   # mesma fala, mesmo áudio da 16:9

N = int((DUR + 2) * SR)
voice = np.zeros(N); music = np.zeros(N); fx = np.zeros(N)
duck = np.ones(N)


def add(buf, sig, t):
    i = int(round(t * SR))
    if i >= len(buf) or i < 0:
        return
    k = min(len(sig), len(buf) - i)
    buf[i:i + k] += sig[:k]


# —— narração: uma vez por fala; a vertical reaproveita as da 16:9 ——
def load_vo(path):
    x, sr = sf.read(path)
    if x.ndim > 1:
        x = x.mean(axis=1)
    return resample_poly(x, SR // sr, 1) if sr != SR else x


for L in LINES:
    if L['id'] in SHARED:
        wav = os.path.join(PROJ, 'vo', f"{SHARED[L['id']]}.wav")
    elif V:
        wav = os.path.join(PROJ, 'vo-9x16', f"{L['id']}.wav")
    else:
        wav = os.path.join(PROJ, 'vo', f"{L['id']}.wav")
    x = load_vo(wav)
    x = np.tanh(x * 1.3) / np.tanh(1.3)                      # compressão suave (picos do TTS)
    x = x / (np.sqrt(np.mean(x ** 2)) + 1e-9) * .12            # nível uniforme entre falas
    add(voice, x, L['t'])
    a, b = int(max(0, (L['t'] - .15) * SR)), int(min(N, (L['t'] + len(x) / SR + .15) * SR))
    duck[a:b] = .5                                             # a música abaixa sob a fala
ramp = int(.1 * SR)
duck = np.convolve(duck, np.ones(ramp) / ramp, mode='same')    # bordas suaves


# —— instrumentos (síntese aditiva, afinação em MIDI) ——
def tone(midi, dur, parts, amps, taus, vel, trem=0.0):
    n = int((dur + 1.5) * SR); t = np.arange(n) / SR; f0 = 440 * 2 ** ((midi - 69) / 12)
    out = sum(a * np.sin(2 * np.pi * f0 * p * t) * np.exp(-t / tau) for p, a, tau in zip(parts, amps, taus))
    if trem:
        out = out * (1 + trem * np.sin(2 * np.pi * 4.5 * t))
    out = out * np.minimum(1, t / .004)
    return out * vel * .3


def drone(midi, dur, vel):
    n = int(dur * SR); t = np.arange(n) / SR; f0 = 440 * 2 ** ((midi - 69) / 12)
    out = sum(np.sin(2 * np.pi * f0 * k * t) / k for k in range(1, 8))
    env = np.minimum(1, t / .8) * np.minimum(1, (dur - t) / .6)
    return out * np.clip(env, 0, 1) * vel * .22


INST = {
    'pz': lambda m, d, v: tone(m, d, [1, 2, 3], [1, .45, .2], [.5, .25, .12], v),
    'kal': lambda m, d, v: tone(m, d, [1, 2.76, 5.4], [1, .25, .08], [.9, .3, .1], v),
    'mar': lambda m, d, v: tone(m, d, [1, 4, 10], [1, .22, .05], [.35, .08, .03], v),
    'harp': lambda m, d, v: tone(m, d, [1, 2, 3, 4], [1, .4, .2, .1], [1.4, .6, .3, .15], v),
    'vib': lambda m, d, v: tone(m, d, [1, 4], [1, .18], [2.2, 1.0], v, trem=.2),
    'drone': lambda m, d, v: drone(m, d, v),
}

# —— efeitos (síntese procedural; cada um sai com nível fixo) ——
def tap(f, tau, amp, noise_hp=None):
    n = int(.08 * SR); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) * np.exp(-t / tau) * amp
    if noise_hp:
        s = s + sfx.hp(sfx.noise(.08), noise_hp)[:n] * np.exp(-t / (tau / 3)) * amp * .6
    return s


def clack(f0, f1, tau, amp, n_s=.06):
    n = int(n_s * SR); t = np.arange(n) / SR
    f = np.linspace(f0, f1, n)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / tau) * amp


def sfx_for(e):
    k = e['type']
    if k == 'drip':   return tap(1800, .012, .35, 3000)
    if k == 'bucket': return tap(320, .18, .45) + tap(1100, .05, .18)
    if k == 'cut':    return tap(90, .09, .45)
    if k == 'ring':   return tap(1050, .08, .12) + tap(1300, .08, .12)
    if k == 'letgo':  return sfx.bp(sfx.noise(.25), 1500, 3500) * np.exp(-np.arange(int(.25 * SR)) / SR / .05) * .25
    if k == 'clatter':
        s = np.zeros(int(.5 * SR))
        for d in (0, .09, .17):
            i = int(d * SR); c = sfx.hp(sfx.noise(.05), 2500) * np.exp(-np.arange(int(.05 * SR)) / SR / .008) * .3
            s[i:i + len(c)] += c[:len(s) - i]
        b = tap(180, .08, .3)
        s[:len(b)] += b
        return s
    if k == 'pop':    return clack(600, 1400, .05, .3, .08)
    if k == 'tick':   return sfx.hp(sfx.noise(.02), 4000) * np.exp(-np.arange(int(.02 * SR)) / SR / .006) * .12
    if k == 'whoosh': return sfx.whoosh(.4) * .3
    if k == 'step':   return sfx.bp(sfx.noise(.05), 300, 900) * np.exp(-np.arange(int(.05 * SR)) / SR / .02) * .15
    if k == 'knock':  return tap(180, .05, .6, 1000)
    if k == 'code':   return tap(1200, .4, .25) + tap(2400, .1, .08)
    if k == 'clasp':  return sfx.lp(sfx.noise(.08), 900) * np.exp(-np.arange(int(.08 * SR)) / SR / .03) * .5
    if k == 'paper':  return sfx.bp(sfx.noise(.4), 2000, 5000) * np.sin(np.pi * np.arange(int(.4 * SR)) / (.4 * SR)) * .25
    if k == 'star':   return tap(1600, .3, .2)
    return None


for e in EV:
    if e['type'] == 'mus':
        f = INST.get(e['inst'])
        if f:
            sig = f(e['midi'], e['dur'], e['vel'])
            add(music, sig, e['t'])
        continue
    s = sfx_for(e)
    if s is not None:
        add(fx, s, e['t'])

# —— balanço: música ~10 dB abaixo da voz (RMS), efeitos a −6 dB da voz ——
vrms = np.sqrt(np.mean(voice[voice != 0] ** 2))
mrms = np.sqrt(np.mean(music[music != 0] ** 2)) + 1e-9
music *= (vrms / 3.16) / mrms
frms = np.sqrt(np.mean(fx[fx != 0] ** 2)) + 1e-9
fx *= (vrms / 2) / frms
mix = voice + music * duck + fx
mix = mix[:int(DUR * SR)]
peak = np.abs(mix).max()
if peak > .89:
    mix *= .89 / peak
os.makedirs(os.path.join(PROJ, 'out'), exist_ok=True)
out = os.path.join(PROJ, 'out', f'mix-{FMT}.wav')
sf.write(out, np.stack([mix, mix], axis=1), SR, subtype='PCM_24')
print('mix', out, 'dur', round(len(mix) / SR, 2), 'pico', round(float(np.abs(mix).max()), 3), 'voz RMS', round(float(vrms), 3))
