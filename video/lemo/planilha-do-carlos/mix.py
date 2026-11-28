"""Mixagem do filme Planilha do Carlos (16:9 e 9:16).

Uso (Python da biblioteca, com numpy/scipy):
  python mix.py <events.json> <pasta_vo> <out.wav> <out.srt> <cues.json> <dur>

events.json = saída de core/render/events.mjs (window.EV da página).
Trilha: office-pop sintetizado (sem samples), corte de música antes da virada, ducking sob a voz.
Efeitos: core/audio/sfx.py da biblioteca. A normalização (-14 LUFS) é feita depois, pelo mux.sh.
"""
import sys, json, wave, subprocess
import numpy as np
from scipy.signal import resample_poly, butter, sosfilt

LIB = 'C:/Users/Mendes/lemo-opuscar'
sys.path.insert(0, LIB)
from core.audio.sfx import SR, add, click, clack, thump, whoosh, pop, ding, limit, t_, env_exp, norm, noise, hp, lp

ev_path, vo_dir, out_wav, out_srt, cues_path, dur_s = sys.argv[1:7]
DUR = float(dur_s)
EV = json.load(open(ev_path, encoding='utf-8'))['ev']
N = int((DUR + 0.5) * SR)
voice = np.zeros((N, 2))        # fala + efeitos
music = np.zeros((N, 2))        # trilha
voice_mask = np.zeros(N)        # onde há fala (para o ducking)


def rd_wav(path):
    with wave.open(path, 'rb') as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768.0
    return resample_poly(x, 2, 1)  # 24 kHz -> 48 kHz


# ---- Voz: cada fala no instante da linha do tempo
for e in EV:
    if e['type'] != 'voice':
        continue
    x = rd_wav(f"{vo_dir}/{e['id']}.wav")
    add(voice, x, e['t'], 1.0, 0.0)
    s = int(e['t'] * SR)
    voice_mask[s:s + len(x)] = 1.0
voice_env = np.clip(sosfilt(butter(2, 6.0, 'low', fs=SR, output='sos'), voice_mask), 0, 1)

# ---- Trilha: acordes C – Am – F – G, 96 BPM (compasso 2,5 s)
F = {'C2': 65.41, 'A1': 55.0, 'F2': 87.31, 'G2': 98.0,
     'A3': 220.0, 'B3': 246.94, 'C4': 261.63, 'D4': 293.66, 'E4': 329.63, 'F3': 174.61,
     'G4': 392.0, 'A4': 440.0, 'C5': 523.25}
CHORDS = [(['C4', 'E4', 'G4'], 'C2'), (['A3', 'C4', 'E4'], 'A1'),
          (['F3', 'A3', 'C4'], 'F2'), (['B3', 'D4', 'G4'], 'G2')]
BAR, BEAT, ARP = 2.5, 0.625, [0, 1, 2, 1, 0, 1, 2, 1]


def kick(v):
    d = .25; tt = t_(d)
    return norm(np.sin(2 * np.pi * (45 + 75 * np.exp(-tt / .03)) * tt) * env_exp(d, .08)) * v


def snare(v):
    d = .2; tt = t_(d)
    return norm(hp(noise(d), 1500) * env_exp(d, .05) + np.sin(2 * np.pi * 190 * tt) * env_exp(d, .04) * .6) * v


def hat(v):
    d = .05
    return norm(hp(noise(d), 7000) * env_exp(d, .02)) * v


def marimba(f, v):
    d = .9; tt = t_(d)
    return norm(np.sin(2 * np.pi * f * tt) * env_exp(d, .18) + .25 * np.sin(2 * np.pi * 4 * f * tt) * env_exp(d, .05)) * v


def bass(f, v, d):
    tt = t_(d)
    return norm(lp(np.sin(2 * np.pi * f * tt) + .3 * np.sin(2 * np.pi * 2 * f * tt), 600) * env_exp(d, .6)) * v


def stab(fs, v):
    d = 1.2; tt = t_(d)
    return norm(sum(np.sin(2 * np.pi * f * tt) * env_exp(d, .25) for f in fs)) * v


cut = next((e['t'] for e in EV if e['type'] == 'cut'), None)
musicon = next((e['t'] for e in EV if e['type'] == 'musicon'), None)
card = next((e['t'] for e in EV if e['type'] == 'whoosh'), DUR)

def section_a(t0, tones, root):
    # metade do tempo: bumbo no 1, caixa no 3, chimbal, baixo sustentado e arpejo
    add(music, kick(.45), t0, 1.0)
    add(music, snare(.3), t0 + 2 * BEAT, 1.0)
    add(music, bass(F[root], .35, BAR), t0, 1.0)
    for i in range(8):
        if i % 2 == 0:
            add(music, hat(.12), t0 + i * .3125, 1.0)
        add(music, marimba(F[tones[ARP[i]]], .32), t0 + i * .3125, 1.0)


def section_b(t0, tones, root):
    # mais grosso, não mais rápido: bumbo a cada tempo, caixa no 2 e 4, baixo de colcheia
    for beat in range(4):
        add(music, kick(.5), t0 + beat * BEAT, 1.0)
        if beat % 2 == 1:
            add(music, snare(.28), t0 + beat * BEAT, 1.0)
        add(music, hat(.14), t0 + beat * BEAT + .3125, 1.0)
    add(music, bass(F[root], .4, .6), t0, 1.0)
    add(music, bass(F[root], .3, .6), t0 + 1.25, 1.0)
    for i in range(8):
        add(music, marimba(F[tones[ARP[i]]], .3), t0 + i * .3125, 1.0)
    add(music, stab([F[x] for x in tones], .22), t0 + 1.25, 1.0)


# seção A: a grade começa em 0; para no corte
for k in range(int(DUR / BAR) + 2):
    t0 = k * BAR
    if t0 >= (cut if cut is not None else DUR):
        break
    section_a(t0, *CHORDS[k % 4])
# seção B: a grade começa exatamente na entrada da música (musicon) e termina no cartão
if musicon is not None:
    for j in range(int(DUR / BAR) + 2):
        tb = musicon + j * BAR
        if tb >= card:
            break
        section_b(tb, *CHORDS[j % 4])
# acorde final: C maior sustentado após o cartão
for f in ['C4', 'E4', 'G4', 'C5']:
    add(music, marimba(F[f], .45), card, 1.0)
if cut is not None and musicon is not None:
    music[int(cut * SR):int(musicon * SR)] = 0  # silêncio de música antes da virada

# ---- Efeitos de planilha (foley), um por evento
rng = np.random.default_rng(3)
blip_k = 0
for e in EV:
    t, typ = e['t'], e['type']
    if typ == 'key':
        add(voice, click(1.0 + rng.random() * .2, .32), t, 1.0)
    elif typ == 'enter':
        add(voice, clack(1.0, .55), t, 1.0)
        add(voice, thump(.25, 80), t + .01, 1.0)
    elif typ == 'hop':
        add(voice, click(1.6, .22), t, 1.0)
    elif typ == 'blip':
        d = .05; tt = t_(d)
        add(voice, norm(np.sin(2 * np.pi * (600 + 45 * blip_k) * tt) * env_exp(d, .015)) * .14, t, 1.0)
        blip_k += 1
    elif typ == 'ratchet':
        add(voice, click(1.2, .18), t, 1.0)
    elif typ == 'fly':
        add(voice, whoosh(.35, .16), t, 1.0)
    elif typ == 'land':
        d = .08; tt = t_(d)
        add(voice, norm(np.sin(2 * np.pi * 520 * tt) * env_exp(d, .03)) * .18, t, 1.0)
    elif typ == 'slide':
        add(voice, click(1.1, .08), t, 1.0)
    elif typ == 'hora':
        add(voice, click(1.3, .16), t, 1.0)
    elif typ == 'pop':
        add(voice, pop(.4), t, 1.0)
    elif typ == 'ding':
        add(voice, ding(.55), t, 1.0)
    elif typ == 'whoosh':
        add(voice, whoosh(.6, .3), t, 1.0)
    elif typ == 'cardpop':
        add(voice, pop(.5), t, 1.0)

# ---- Mistura: ducking sob a fala; trilha ~10 dB abaixo da voz (RMS)
music *= (1.0 - 0.65 * voice_env)[:, None]
vo_rms = np.sqrt(np.mean(voice[voice_mask > .5] ** 2))
mu_mask = (np.abs(music).sum(1) > 0) & (voice_mask < .5)
mu_rms = np.sqrt(np.mean(music[mu_mask] ** 2))
music *= (vo_rms / mu_rms) * 10 ** (-10 / 20)
mix = voice + music
mix = np.stack([limit(mix[:, 0], .89), limit(mix[:, 1], .89)], 1)
pcm = (np.clip(mix, -1, 1) * 32767).astype(np.int16)
with wave.open(out_wav, 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())

# ---- Legendas (srt.py da biblioteca)
cues = [{'t0': round(e['t'], 3), 't1': round(e['t1'], 3), 'text': e['text']} for e in EV if e['type'] == 'sub']
json.dump(cues, open(cues_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
subprocess.run([sys.executable, f'{LIB}/core/render/srt.py', cues_path, out_srt], check=True)
print('mix ok', out_wav, 'voz_rms', round(float(vo_rms), 4), 'corte', cut, 'musica', musicon, 'legendas', len(cues))
