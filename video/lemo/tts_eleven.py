"""Voz do ElevenLabs com a mesma interface de core/tts/tts.py da biblioteca Lemo-Opuscar.

Uso:  python video/lemo/tts_eleven.py <lines.json> <pasta_saida> <voice_id> [--refazer]
lines.json = [{"id": ..., "text": ...}, ...]  (um "voice" por linha substitui o voice_id)
Grava <pasta_saida>/<id>.wav (24 kHz, mono, silêncio das pontas cortado) e <pasta_saida>/dur.json.
A chave fica em ~/elevenlabs-key.txt (fora do repositório; nunca imprime nem grava a chave).
Linhas já geradas são puladas (cada geração gasta créditos); --refazer gera de novo.
"""
import json, os, sys, wave, urllib.request, urllib.error, pathlib, array

if len(sys.argv) < 4:
    print(__doc__); sys.exit(2)
linhas = json.load(open(sys.argv[1], encoding='utf-8'))
saida, voz_padrao, refazer = sys.argv[2], sys.argv[3], '--refazer' in sys.argv
chave = pathlib.Path.home().joinpath('elevenlabs-key.txt').read_text(encoding='utf-8').strip()
SR = 24000
os.makedirs(saida, exist_ok=True)


def falar(texto, voz, antes, depois):
    corpo = {'text': texto, 'model_id': 'eleven_multilingual_v2', 'language_code': 'pt',
             'voice_settings': {'stability': 0.5, 'similarity_boost': 0.8, 'style': 0.15, 'use_speaker_boost': True}}
    if antes: corpo['previous_text'] = antes
    if depois: corpo['next_text'] = depois
    req = urllib.request.Request(f'https://api.elevenlabs.io/v1/text-to-speech/{voz}?output_format=pcm_24000',
                                 data=json.dumps(corpo).encode(), headers={'xi-api-key': chave, 'Content-Type': 'application/json'})
    try:
        return urllib.request.urlopen(req, timeout=120).read()
    except urllib.error.HTTPError as e:
        sys.exit(f'ElevenLabs recusou a linha "{texto[:40]}…": {e.code} {e.read()[:300].decode("utf-8", "ignore")}')


def aparar(pcm):
    a = array.array('h', pcm)
    limiar = 400
    ini = next((i for i, x in enumerate(a) if abs(x) > limiar), 0)
    fim = next((i for i in range(len(a) - 1, -1, -1) if abs(a[i]) > limiar), len(a) - 1)
    ini, fim = max(0, ini - int(0.03 * SR)), min(len(a), fim + int(0.06 * SR))
    return a[ini:fim]


dur_path = os.path.join(saida, 'dur.json')
dur = json.load(open(dur_path, encoding='utf-8')) if os.path.exists(dur_path) else {}
for i, L in enumerate(linhas):
    arq = os.path.join(saida, f"{L['id']}.wav")
    if os.path.exists(arq) and not refazer and str(L['id']) in dur:
        continue
    antes = linhas[i - 1]['text'] if i > 0 else None
    depois = linhas[i + 1]['text'] if i + 1 < len(linhas) else None
    a = aparar(falar(L['text'], L.get('voice') or voz_padrao, antes, depois))
    with wave.open(arq, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(a.tobytes())
    dur[str(L['id'])] = round(len(a) / SR, 3)
    print(f"{L['id']}: {dur[str(L['id'])]} s", flush=True)
json.dump(dur, open(dur_path, 'w', encoding='utf-8'), indent=1)
print('total', round(sum(dur.values()), 2), 's em', len(dur), 'linhas')
