"""Trilha, foley e mixagem de "A mesa do pedido" (54 s, 48 kHz estéreo).

Uso: python mix.py   → out/mix.wav
Partitura em sol maior, I–V–vi–IV, 120 BPM (um acorde por compasso de 2 s), marimba + baixo + pulso.
A música abaixa cerca de 16 dB sob a voz e some no silêncio do código (18,8–19,5 s).
Foley nos tempos dos saltos, do aceite, dos dígitos, do pagamento e do fechamento (roteiro em src/film.js).
"""
import pathlib, wave
import numpy as np

HERE = pathlib.Path(__file__).resolve().parent
SR, DUR = 48000, 55.0
N = int(SR * DUR)
t_all = np.arange(N) / SR
music = np.zeros(N)
foley = np.zeros(N)
voz = np.zeros(N)
rng = np.random.default_rng(7)

LINHAS = [0.0, 4.5, 9.5, 14.5, 21.5, 26.0, 31.5, 35.5, 47.0]   # t0 de cada fala (src/film.js)


def add(buf, sig, at, gain=1.0):
    i = int(at * SR)
    if i >= N or i < 0:
        return
    j = min(N, i + len(sig))
    buf[i:j] += gain * sig[: j - i]


def tom(f, dur, env_tau=None, harm=0.18):
    """Nota de marimba: seno com harmônico alto e queda exponencial."""
    t = np.arange(int(dur * SR)) / SR
    tau = env_tau or dur / 4
    s = np.sin(2 * np.pi * f * t) + harm * np.sin(2 * np.pi * 4 * f * t)
    s *= np.exp(-t / tau) * np.minimum(1, t / .003)
    return s


def pulso(f0, f1, dur, tau):
    """Pulso (bumbo) ou tique: senoide com varredura de frequência."""
    t = np.arange(int(dur * SR)) / SR
    f = f0 + (f1 - f0) * np.minimum(1, t / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t / tau)


def ruido(dur, tau, lo=None):
    t = np.arange(int(dur * SR)) / SR
    x = rng.standard_normal(len(t)) * np.exp(-t / tau)
    if lo:   # passa-alta simples
        a = np.exp(-2 * np.pi * lo / SR)
        y = np.zeros_like(x); acc = 0.0
        for k in range(len(x)):
            acc = a * acc + (1 - a) * x[k]
            y[k] = x[k] - acc
        x = y
    return x


# ---- partitura -------------------------------------------------------------
ACORDES = [  # G, D, Em, C (notas da marimba) e a fundamental do baixo
    ([392.0, 493.9, 587.3], 98.0),
    ([293.7, 370.0, 440.0], 73.4),
    ([329.6, 392.0, 493.9], 82.4),
    ([261.6, 329.6, 392.0], 65.4),
]
for bar in range(int(DUR // 2) + 1):
    t0 = bar * 2.0
    if t0 >= 47.0:
        break
    notas, raiz = ACORDES[bar % 4]
    for b in range(4):   # quatro tempos, marimba arpejada
        f = notas[(b + bar) % 3] * (2 if b == 3 else 1)
        add(music, tom(f, 1.4, env_tau=.45), t0 + b * .5, .16)
    add(music, pulso(raiz * 1.5, raiz, .35, .12), t0, .35)          # baixo pizzicato, tempo 1
    add(music, pulso(raiz * 1.5, raiz, .25, .08), t0 + 1.0, .22)    # tempo 3
    add(music, pulso(120, 45, .18, .05), t0, .45)                   # bumbo, tempos 1 e 3
    add(music, pulso(120, 45, .18, .05), t0 + 1.0, .40)
    for b in (1, 3):                                                # palmas, tempos 2 e 4
        add(music, ruido(.08, .02, lo=1500), t0 + b * .5, .10)

# fechamento: acorde pleno que segura até o fim
for n, f in enumerate([196.0, 392.0, 493.9, 587.3]):
    add(music, tom(f, 8.0, env_tau=3.5, harm=.1), 47.0, .10 if n else .14)

# ---- foley -------------------------------------------------------------
add(foley, pulso(300, 900, .22, .08), 0.0, .30)                    # o pedido nasce: varredura para cima
for t in (4.5, 13.5, 22.0, 36.0):                                  # saltos: tique na saída
    add(foley, ruido(.02, .004, lo=2500), t, .25)
    add(foley, pulso(1800, 1800, .03, .01), t, .12)
for t in (5.5, 15.0, 23.5, 37.5):                                  # e um baque suave na aterrissagem
    add(foley, pulso(160, 80, .12, .04), t, .25)
add(foley, pulso(120, 40, .35, .12), 10.0, .55)                    # aceite: impacto no papel
add(foley, ruido(.02, .004, lo=2000), 10.0, .35)                   # dois toques no botão
add(foley, ruido(.02, .004, lo=2000), 10.12, .30)
for t in (3.9, 14.0, 31.5, 35.5):                                  # whips: assobio medido
    add(foley, ruido(.45, .22, lo=800) * np.sin(np.linspace(0, np.pi, int(.45 * SR))), t, .20)
for i in range(4):                                                 # dígitos do código: um tique por colcheia
    add(foley, pulso(2100, 2100, .04, .012), 19.5 + i * .125, .30)
for k in range(4):                                                 # dados do pagamento
    add(foley, pulso(600, 900, .12, .05), 31.5 + k * .25, .12)
add(foley, pulso(900, 300, .30, .12), 40.0, .28)                   # registro salvo: nota descendente
add(foley, pulso(90, 35, .40, .15), 47.0, .60)                     # título cai: baque
add(foley, ruido(.25, .08, lo=300), 47.0, .30)

# ---- voz ---------------------------------------------------------------
for i, t0 in enumerate(LINHAS):
    p = HERE / "vo" / f"v{i + 1}.wav"
    with wave.open(str(p), "rb") as w:
        raw = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64) / 32768.0
        sr_in = w.getframerate()
    x = raw
    dur_in = len(x) / sr_in
    t_in = np.arange(len(x)) / sr_in
    t_out = np.arange(int(dur_in * SR)) / SR
    y = np.interp(t_out, t_in, x)                                  # 24 kHz → 48 kHz
    add(voz, y, t0, 1.0)

# ---- ganho da música: sob a voz e no silêncio do código -----------------
ativa = np.abs(voz) > 1e-4
env = np.ones(N) * .9
env[ativa] = .16                                                   # −16 dB sob a fala
janela = int(.08 * SR)
env = np.convolve(env, np.ones(janela) / janela, mode="same")
env[(t_all >= 18.8) & (t_all < 19.5)] = 0.0                        # silêncio antes do código
music *= env

mix = music * .9 + foley * .9 + voz * 1.0
mix = np.tanh(mix * 1.1) / np.tanh(1.1)                            # limitador suave, sem cortar
mix *= 10 ** (-3 / 20) / max(1e-9, np.max(np.abs(mix)))            # pico em −3 dBFS (o mux masteriza para −14 LUFS)
st = np.stack([mix, mix], axis=1)
out = HERE / "out"
out.mkdir(exist_ok=True)
pcm = (np.clip(st, -1, 1) * 32767).astype("<i2")
with wave.open(str(out / "mix.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
rms = lambda a: 20 * np.log10(np.sqrt(np.mean(a[a != 0] ** 2)) + 1e-12)
print(f"mix.wav: {DUR:.1f} s, pico {np.max(np.abs(mix)):.3f}, RMS voz {rms(voz):.1f} dB, RMS música {rms(music):.1f} dB")
