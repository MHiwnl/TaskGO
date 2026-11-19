# Clona a voz autorizada (narracao/referencia) e grava narracao/voz/NN.wav, uma por fala.
# .venv-voz/Scripts/python render/clonar.py [ids...]   (XTTS-v2, licença Coqui CPML não comercial, aceita pelo autor)
import os, sys, json, torch
os.environ['COQUI_TOS_AGREED'] = '1'
from TTS.api import TTS
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
refs = [os.path.join(RAIZ, 'narracao/prep', f) for f in ('ref1.wav', 'ref2.wav') if os.path.exists(os.path.join(RAIZ, 'narracao/prep', f))]
falas = json.load(open(os.path.join(RAIZ, 'narracao/falas.json'), encoding='utf-8'))
quais = set(sys.argv[1:])
tts = TTS('tts_models/multilingual/multi-dataset/xtts_v2').to('cpu')
for f in falas:
    if quais and f['id'] not in quais: continue
    torch.manual_seed(42)
    out = os.path.join(RAIZ, 'narracao/voz', f['id'] + '.wav')
    tts.tts_to_file(text=f.get('tts') or f['texto'], speaker_wav=refs, language='pt', file_path=out, temperature=0.65, speed=1.0, enable_text_splitting=False)
    print('fala', f['id'], 'ok', flush=True)
print('pronto', flush=True)
