"""Mixagem do filme Chuveiro: voz (ElevenLabs, nos tempos de lines.json), trilha original e efeitos.
Uso: <venv python> mix.py 16x9|9x16 <saida.wav>
Lê plano.json (compasso, seções, efeitos) e lines.json (tempos da voz). Sai estéreo 48 kHz.
Trilha: rabeca (pad e melodia), alfaia e caixa (maracatu), gonguê/sino. Sem amostras de terceiros.
"""
import json, os, sys
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly, butter, sosfilt

HERE = os.path.dirname(os.path.abspath(__file__))
fmt, saida = sys.argv[1], sys.argv[2]
os.makedirs(os.path.join(HERE, 'out'), exist_ok=True)
P = json.load(open(os.path.join(HERE, 'plano.json'), encoding='utf-8'))
F = P['formatos'][fmt]
LINHAS = json.load(open(os.path.join(HERE, 'lines.json'), encoding='utf-8'))
BEAT = 60.0 / P['bpm']
SR = 48000
N = int(F['dur'] * SR) + SR
rng = np.random.default_rng(7)
t_all = np.arange(N) / SR


def lp(x, hz):
    return sosfilt(butter(2, hz, 'lowpass', fs=SR, output='sos'), x)


def hp(x, hz):
    return sosfilt(butter(2, hz, 'highpass', fs=SR, output='sos'), x)


def bp(x, a, b):
    return sosfilt(butter(2, [a, b], 'bandpass', fs=SR, output='sos'), x)


def ruido(n):
    return rng.standard_normal(n)


# Voz: cada linha no seu tempo (t_16x9 ou t_9x16); o áudio vem de vo/<id>.wav a 24 kHz
voz = np.zeros(N)
cues = []
for L in LINHAS:
    t = L.get('t_' + fmt)
    if t is None:
        continue
    w, sr_w = sf.read(os.path.join(HERE, 'vo', L['id'] + '.wav'), dtype='float64')
    w = resample_poly(w, SR, sr_w)
    i0 = int(t * SR)
    voz[i0:i0 + len(w)] += w[:N - i0]
    cues.append({'t0': round(t, 3), 't1': round(t + len(w) / SR, 3), 'text': L['text'], 'id': L['id']})
voz /= max(1e-9, np.sqrt(np.mean(voz[voz != 0] ** 2)))   # RMS da voz
voz = np.tanh(voz * .35) / .35 * .2                       # compressão suave (pico ~ −14 dBFS)
json.dump(cues, open(os.path.join(HERE, 'out', 'cues-' + fmt + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

# Ducking: a música abaixa enquanto a voz fala
env = lp(np.abs(voz), 12)
ativo = (env > .004).astype(float)
duck = 1 - .62 * np.clip(np.convolve(ativo, np.ones(int(.2 * SR)) / (.2 * SR), 'same'), 0, 1)


def nota(f, t0, dur, amp, vib=.004):
    """Rabeca: onda dente-de-serra suavizada, vibrato lento, ataque e soltura."""
    i0 = int(t0 * SR); n = int(dur * SR) + int(.3 * SR)
    if i0 >= N or n <= 0:
        return np.zeros(0)
    t = np.arange(n) / SR
    fv = f * (1 + vib * np.sin(2 * np.pi * 5.2 * t) * np.clip(t / .6, 0, 1))
    ph = 2 * np.pi * np.cumsum(fv) / SR
    s = sum(np.sin(k * ph) / k for k in range(1, 9)) * .6
    env_ = np.clip(t / .09, 0, 1) * np.exp(-np.maximum(t - dur, 0) / .18) * (t < dur + .3)
    return s * env_ * amp


def soma(buf, i0, x, pan=0.0):
    if len(x) == 0:
        return
    i1 = min(N, i0 + len(x))
    g = np.array([np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)])
    buf[i0:i1] += x[:i1 - i0, None] * g[None, :]


def golpe_alfaia(t0, amp):
    n = int(.35 * SR); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * (110 * np.exp(-t * 14) + 45) * t) * np.exp(-t * 9) + .08 * ruido(n) * np.exp(-t * 60)
    return s * amp


def golpe_caixa(t0, amp):
    n = int(.09 * SR); t = np.arange(n) / SR
    return hp(ruido(n), 3500) * np.exp(-t * 55) * amp


def sino(f, dur, amp):
    n = int(dur * SR); t = np.arange(n) / SR
    return (np.sin(2 * np.pi * f * t) + .4 * np.sin(2 * np.pi * 2.76 * f * t) * np.exp(-t * 2)) * np.exp(-t * 1.6) * amp


MUS = np.zeros((N, 2))
MEL_A = [[69, 72, 74, 72], [76, 74, 72, 69]]   # frase da rabeca, em duas barras (original)

for sec in F['secoes']:
    a, b = sec['t0'], min(sec['t1'], F['dur'])
    if sec.get('pad'):
        soma(MUS, int(a * SR), nota(110, 0, b - a, .10, vib=.002), -.2)
        soma(MUS, int(a * SR), nota(220, 0, b - a, .05, vib=.002), .2)
    for t in sec.get('batida_em', []):
        if a <= t < b:
            soma(MUS, int(t * SR), golpe_alfaia(t, .5), 0)
    mel = sec.get('melodia')
    if mel:
        oit = 12 if mel == 'B' else 0
        k = 0
        t = a
        while t < b - .2:
            bar = int(t / (4 * BEAT)); pat = MEL_A[bar % 2]
            f = 440 * 2 ** ((pat[k % 4] + oit - 69) / 12)
            d = min(BEAT * .95, b - t)
            soma(MUS, int(t * SR), nota(f, 0, d, .16), .25)
            t += BEAT; k += 1
    if sec.get('batida'):
        leve = sec['batida'] == 'leve'
        t = np.ceil(a / (BEAT / 4)) * BEAT / 4   # primeiro dezesseis-avos da seção
        while t < b:
            step = int(round(t / (BEAT / 4))) % 16
            if step in ([0, 8] if leve else [0, 6, 8, 14]):
                soma(MUS, int(t * SR), golpe_alfaia(t, .3 if leve else .55), .1)
            if not leve and step in [4, 12]:
                soma(MUS, int(t * SR), golpe_caixa(t, .22), -.3)
            if not leve and step in [2, 10]:
                soma(MUS, int(t * SR), golpe_caixa(t, .10), .3)
            t += BEAT / 4
    if sec.get('gonguê'):
        soma(MUS, int(a * SR), sino(1318, 2.5, .18), .4)
    if sec.get('acorde'):
        ta = sec['acorde']
        for f, pan in [(220, -.3), (329.6, 0), (440, .3)]:
            soma(MUS, int(ta * SR), sino(f, 5, .16), pan)
MUS *= duck[:, None] * .75
MUS = lp(MUS.T, 9000).T

# Efeitos (sfx): chuva de fundo, faísca, passos, respingos, portão, luz, tiques, acorde
FX = np.zeros((N, 2))
ch = rng.standard_normal((N, 2))
chuva = lp(ch[:, 0], 2600) * .03
FX[:, 0] += chuva; FX[:, 1] += lp(ch[:, 1], 2600) * .03
E = F['efeitos']
for t in E['pa']:
    i0 = int(t * SR); n = int(.5 * SR); tt = np.arange(n) / SR
    faisca = bp(ruido(n), 2000, 6000) * np.exp(-tt * 40) * .4
    estalo = np.sin(2 * np.pi * 70 * tt) * np.exp(-tt * 12) * .5
    soma(FX, i0, faisca + estalo, 0)
    soma(FX, i0 + int(.12 * SR), bp(ruido(int(.3 * SR)), 1500, 5000) * np.exp(-np.arange(int(.3 * SR)) / SR * 14) * .15, -.5)
for t in E['fala']:
    soma(FX, int(t * SR), sino(1046, .6, .12), .3)
for t in E['pix']:
    soma(FX, int(t * SR), sino(1318, .5, .1) + sino(1760, .5, .08), -.3)
ta, tb = E['passos_de'], E['passos_ate']
k = 0
for t in np.arange(ta, tb, .36):
    soma(FX, int(t * SR), lp(bp(ruido(int(.05 * SR)), 300, 1200), 1200) * np.exp(-np.arange(int(.05 * SR)) / SR * 60) * .5, -.4 if k % 2 else .4)
    k += 1
for t in E['respingos']:
    soma(FX, int(t * SR), hp(ruido(int(.25 * SR)), 1500) * np.exp(-np.arange(int(.25 * SR)) / SR * 12) * .25, 0)
for t in E['portao']:
    soma(FX, int(t * SR), sino(320, 1.2, .14), -.5)
    soma(FX, int(t * SR), sino(640, 1.0, .05), -.5)
    soma(FX, int(t * SR), hp(ruido(int(.04 * SR)), 2000) * .3, -.5)
for t in E['clique_luz']:
    i0 = int(t * SR)
    soma(FX, i0, hp(ruido(int(.01 * SR)), 2500) * .6, 0)                       # clique do disjuntor
    soma(FX, i0 + int(.02 * SR), sino(1760, 2.4, .18), 0)  # ding da luz
    soma(FX, i0 + int(.02 * SR), sino(2637, 2.0, .06), 0)
    zum = np.arange(int(3 * SR)) / SR
    soma(FX, i0, (np.sin(2 * np.pi * 120 * zum) * .02 + np.sin(2 * np.pi * 240 * zum) * .01) * np.exp(-zum * .6), 0)
for t in E['tiques']:
    soma(FX, int(t * SR), bp(ruido(int(.02 * SR)), 1200, 4000) * .35 * np.exp(-np.arange(int(.02 * SR)) / SR * 150), .3)
for t in E['acorde']:
    soma(FX, int(t * SR), sino(440, 6, .07) + sino(220, 6, .05), 0)

mix = voz[:, None] * np.array([1, 1]) + MUS + FX
mix = mix[:int(F['dur'] * SR)]
pico = np.max(np.abs(mix))
if pico > .89:
    mix = mix * (.89 / pico)
os.makedirs(os.path.dirname(os.path.abspath(saida)), exist_ok=True)
sf.write(saida, mix, SR, subtype='PCM_24')
print('mix', fmt, round(len(mix) / SR, 2), 's, pico', round(float(pico), 3), '->', saida)
print('legendas', len(cues), 'linhas de voz')
