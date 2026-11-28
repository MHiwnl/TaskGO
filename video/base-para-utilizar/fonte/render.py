"""Renderiza os clipes de referência (fonte/*.html) em MP4 1080p60 na pasta de cima.

Uso (de qualquer pasta):
  python video/base-para-utilizar/fonte/render.py                 # todos os que mudaram
  python video/base-para-utilizar/fonte/render.py 03-logo-reveal  # só esses
  python video/base-para-utilizar/fonte/render.py --forcar         # refaz todos
  python video/base-para-utilizar/fonte/render.py --quadros 03-logo-reveal  # só uma folha de conferência (PNG)
Precisa de Python com playwright (Chromium) e Pillow, e de ffmpeg no PATH.
"""
import asyncio, io, subprocess, sys
from pathlib import Path
from playwright.async_api import async_playwright

FONTE = Path(__file__).resolve().parent
SAIDA = FONTE.parent
FPS = 60


async def renderizar(navegador, html: Path, so_quadros: bool):
    pagina = await navegador.new_page(viewport={'width': 1920, 'height': 1080})
    erros = []
    pagina.on('pageerror', lambda e: erros.append(str(e)))
    await pagina.goto(html.as_uri())
    await pagina.evaluate('window.__ready')
    if erros:
        raise RuntimeError(f'{html.name}: {erros[0]}')
    dur = await pagina.evaluate('window.__meta.dur')
    if so_quadros:
        from PIL import Image
        tempos = [dur * k / 6 for k in range(1, 6)]
        folha = Image.new('RGB', (960 * 3, 540 * 2), 'black')
        for i, t in enumerate(tempos + [dur - 0.02]):
            await pagina.evaluate(f'window.__seek({t})')
            img = Image.open(io.BytesIO(await pagina.screenshot(type='png'))).resize((960, 540))
            folha.paste(img, ((i % 3) * 960, (i // 3) * 540))
        destino = FONTE / 'conferencia' / f'{html.stem}.png'
        destino.parent.mkdir(exist_ok=True)
        folha.save(destino)
        await pagina.close()
        return destino
    filme = html.stem.startswith('filme-')
    destino = (SAIDA / 'filmes' if filme else SAIDA) / f'{html.stem}.mp4'
    destino.parent.mkdir(exist_ok=True)
    eventos = await pagina.evaluate('window.__events().length')
    ff = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', str(FPS), '-i', '-',
                           '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(destino)],
                          stdin=subprocess.PIPE)
    for f in range(round(dur * FPS)):
        await pagina.evaluate(f'window.__seek({f / FPS})')
        ff.stdin.write(await pagina.screenshot(type='jpeg', quality=95))
    ff.stdin.close()
    if ff.wait() != 0:
        raise RuntimeError(f'ffmpeg falhou em {html.name}')
    await pagina.close()
    if eventos:
        # Filme com som: o score dos filmes onetake sintetiza os eventos; o áudio entra no mesmo MP4.
        som = destino.with_suffix('.wav')
        subprocess.run([sys.executable, str(FONTE.parent.parent / 'onetake' / 'score.py'), str(html), str(som)], check=True)
        mudo = destino.with_name(destino.stem + '-mudo.mp4')
        destino.replace(mudo)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(mudo), '-i', str(som), '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                        '-shortest', '-movflags', '+faststart', str(destino)], check=True)
        mudo.unlink(); som.unlink()
    return destino


async def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    forcar, so_quadros = '--forcar' in sys.argv, '--quadros' in sys.argv
    todos = sorted(p for p in FONTE.glob('*.html'))
    lista = [p for p in todos if not args or p.stem in args]
    if not so_quadros and not forcar and not args:
        def mp4(p): return (SAIDA / 'filmes' if p.stem.startswith('filme-') else SAIDA) / f'{p.stem}.mp4'
        lista = [p for p in lista if not mp4(p).exists() or mp4(p).stat().st_mtime < max(p.stat().st_mtime, (FONTE / 'base.js').stat().st_mtime)]
    sem = asyncio.Semaphore(3)
    async with async_playwright() as pw:
        navegador = await pw.chromium.launch(args=['--allow-file-access-from-files'])

        async def um(p):
            async with sem:
                try:
                    print('ok', await renderizar(navegador, p, so_quadros), flush=True)
                except Exception as e:
                    print('ERRO', p.name, e, flush=True)
        await asyncio.gather(*(um(p) for p in lista))
        await navegador.close()


asyncio.run(main())
