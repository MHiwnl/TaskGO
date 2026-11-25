"""Som dos filmes onetake da TaskGO: lê window.__events() da composição e põe cada evento num material da paleta.
Uso:  python score.py a-ponto.html a-som.wav
Menos sons que batidas; os toques de dedo e as estrelas sobem uma escala; tudo na mesma sala (reverb única)."""
import os, sys, asyncio
import numpy as np
sys.path.insert(0, os.path.expanduser("~/.claude/skills/onetake/scripts"))
from sfx_palette import Score, air, glass, wood, sub, bubble, wobble, pan_of
from playwright.async_api import async_playwright


async def read_events(comp):
    async with async_playwright() as p:
        br = await p.chromium.launch(); pg = await br.new_page(viewport={"width": 1920, "height": 1080})
        await pg.goto("file:///" + os.path.abspath(comp).replace("\\", "/")); await pg.evaluate("window.__ready")
        ev, dur = await pg.evaluate("[window.__events(), window.__meta.dur]"); await br.close(); return ev, dur


def build(ev, dur):
    rng = np.random.default_rng(7); s = Score(dur=dur, T60=1.2); P = s.place
    STAR = (523, 587, 659, 784, 1046)
    for e in ev:
        t, k, pan = e["t"], e["kind"], pan_of(e.get("x", 960))
        if k == "drop": P(sub(70, 0.6), t, 0.42, pan, 0.35); P(bubble(430, 0.25), t, 0.18, pan, 0.3)
        elif k == "word": P(glass(659 + rng.uniform(-30, 30), 0.5, 0.5), t, 0.07, pan, 0.4)
        elif k == "open": P(air(0.38, 260, 2400, 1.3, 0.4), t - 0.05, 0.22, pan, 0.45)
        elif k == "close": P(air(0.3, 2200, 300, 1.4), t, 0.18, pan, 0.4)
        elif k == "land": P(bubble(470 + rng.uniform(-60, 60), 0.22), t, 0.2, pan, 0.3)
        elif k == "whip": P(air(0.55, 200, 3200, 1.2, 0.5), t - 0.1, 0.42, -0.3, 0.5, pan_to=0.4)
        elif k == "dive": P(air(2.2, 160, 2600, 1.0, 0.75), t, 0.34, 0.0, 0.6)
        elif k == "rise": P(air(1.6, 2600, 220, 1.1, 0.3), t, 0.3, 0.0, 0.6)
        elif k == "tap": P(wood(240, 0.08), t, 0.3, pan, 0.2); P(wood(190, 0.07), t + 0.07, 0.2, pan, 0.2)
        elif k == "type":
            n = int(e.get("dur", 1.2) * 36)
            for i in range(n):
                if rng.uniform() < 0.4: continue
                P(wood(170 + rng.uniform(-20, 20), 0.08), t + i / 36, 0.08 * rng.uniform(0.7, 1.3), pan, 0.15)
        elif k == "hit": P(sub(62, 0.8), t, 0.5, pan, 0.4); P(glass(784, 1.0, 0.6), t + 0.02, 0.14, pan, 0.55)
        elif k == "qr":
            for i in range(6): P(wood(300 + i * 40, 0.05), t + i * 0.06, 0.07, pan, 0.25)
        elif k == "paid": P(glass(1046, 1.3, 0.5), t, 0.18, pan, 0.6); P(glass(1568, 1.4, 0.4), t + 0.09, 0.14, pan, 0.6); P(sub(55, 0.9), t, 0.25, pan, 0.4)
        elif k == "chime": P(glass(880, 1.0, 0.5), t, 0.13, pan, 0.55)
        elif k == "star": P(glass(STAR[e.get("i", 0)], 0.7, 0.7), t, 0.12, pan, 0.45)
        elif k == "gather": P(air(0.7, 300, 2400, 1.2, 0.6), t, 0.28, 0.0, 0.5)
        elif k == "lights":
            for i in range(9): P(glass(STAR[i % 5] * (2 if i > 4 else 1), 0.6, 0.4), t + i * 0.13, 0.06, rng.uniform(-0.6, 0.6), 0.6)
        elif k == "logo": P(sub(48, 1.4), t, 0.5, 0.0, 0.5); P(glass(659, 1.6, 0.4), t + 0.05, 0.12, -0.1, 0.7); P(glass(988, 1.6, 0.3), t + 0.07, 0.09, 0.1, 0.7)
    return s


if __name__ == "__main__":
    comp, out = sys.argv[1], sys.argv[2]
    ev, dur = asyncio.run(read_events(comp))
    build(ev, dur).write(out)
