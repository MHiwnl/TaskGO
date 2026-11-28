"""Trilha com música real (Falling, michorvath, CC0) + chuva e 2 efeitos do earshot + narração (vo/).
Uso: python mix_real.py 16x9|9x16  ->  out/mix-real-<fmt>.wav (o mux.sh normaliza para -14 LUFS).
A música abaixa sob a voz (ducking); a chuva fica baixa e constante; efeitos só no conserto (faísca e disjuntor)."""
import json, subprocess, sys
import numpy as np, soundfile as sf

FMT = sys.argv[1]
SR = 48000
plano = json.load(open('plano.json', encoding='utf-8'))['formatos'][FMT]
DUR = plano['dur']; N = int(round(DUR * SR))
LUZ = next(p['luz'] for p in plano['planos'] if 'luz' in p)   # a luz volta; o disjuntor religa 3,2 s antes (set.js)
RELIGA = LUZ - 3.2


def ler(caminho):   # qualquer arquivo -> estéreo 48 kHz float
    bruto = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', caminho, '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-'],
                           capture_output=True, check=True).stdout
    return np.frombuffer(bruto, dtype=np.float32).reshape(-1, 2).copy()


def db(x): return 10 ** (x / 20)


def por(buf, x, t, g=1.0):
    i = int(t * SR)
    if i >= len(buf): return
    n = min(len(x), len(buf) - i); buf[i:i + n] += x[:n] * g


# narração
voz = np.zeros((N, 2), np.float32)
chave = 't_' + FMT
for L in json.load(open('lines.json', encoding='utf-8')):
    if L.get(chave) is not None: por(voz, ler(f"vo/{L['id']}.wav"), L[chave])

# envelope de voz para o ducking (ataque 80 ms, soltura 450 ms)
nivel = np.abs(voz).max(axis=1)
ativo = (nivel > .02).astype(np.float32)
env = np.zeros(N, np.float32); a, r, e = 1 - np.exp(-1 / (.08 * SR)), 1 - np.exp(-1 / (.45 * SR)), 0.0
for i in range(N):
    alvo = ativo[i]; e += (alvo - e) * (a if alvo > e else r); env[i] = e

# música: Falling, do começo, fade in 1,2 s e fade out 2,5 s; −13 dB sob a voz
mus = ler('musica-opcoes/falling-8-bit-music-loop-101c01.mp3')[:N]
if len(mus) < N: mus = np.concatenate([mus, np.zeros((N - len(mus), 2), np.float32)])
t = np.arange(N) / SR
fade = np.clip(t / 1.2, 0, 1) * np.clip((DUR - t) / 2.5, 0, 1)
mus *= (fade * (1 - env * (1 - db(-13))))[:, None] * db(-6)

# chuva no telhado (loop sem emenda), baixa; fica mais baixa depois que a luz volta (o filme esquenta)
ch = ler('som/chuva.wav'); ch = np.tile(ch, (N // len(ch) + 1, 1))[:N]
ch *= (db(-20) * np.where(t < LUZ, 1.0, db(-6)) * np.clip(t / 1.5, 0, 1))[:, None]

# efeitos: faíscas no chuveiro e o disjuntor religando
fx = np.zeros((N, 2), np.float32)
fa = ler('som/faisca.wav')
for k, dt in enumerate((-1.75, -1.4, -1.1)): por(fx, fa, RELIGA + dt, db(-8 - 2 * k))
por(fx, ler('som/disjuntor.wav'), RELIGA, db(-6))

mix = voz + mus + ch + fx
pico = np.abs(mix).max()
if pico > .95: mix *= .95 / pico
sf.write(f'out/mix-real-{FMT}.wav', mix, SR, subtype='PCM_24')
print(FMT, 'ok', round(DUR, 2), 's; religa', round(RELIGA, 2), 's; pico', round(float(pico), 3))
