# Trilhas reais dos filmes (lemo)

Música e efeitos são gravações reais licenciadas (Freesound CC0, Wikimedia CC BY 4.0, Jamendo CC BY-SA 3.0). A narração (vo/) não mudou. O vídeo não foi re-renderizado: o áudio novo foi montado sobre o MP4 anterior (cópia de vídeo, AAC 192k).

Regras aplicadas: música sob a fala com sidechain (ratio 6, limiar -30 dB, música -12 dB antes do ducking); efeitos no máximo 4 por filme; masterização loudnorm -14 LUFS com pico verdadeiro ≤ -1 dBTP (medido com ebur128 depois do mux).

Não ouvido: earshot instalado, mas os modelos de escuta ainda baixavam. Vocal, clima e ducking não foram conferidos por ouvido.

## Keynote-lancamento

| Formato | Música | Trecho e cortes | Entrada/saída | Narração (ducking) | Efeitos (tempo, ação, arquivo, licença) | Loudness/pico (medido) | Sincronia | Antigo |
|---|---|---|---|---|---|---|---|---|
| 16x9 (52 s) | Spiritual Ambient, MusicLFiles, CC BY 4.0, 2:29, clima ambient (BPM não medido) | 0 a 52 s, sem loop | fade in 0,6 s, fade out 3 s | falas em 2,6; 12,8; 18,2; 23,6; 30,6; 41,0; 43,6; 46,6 s | 2,4 s clique (Soft UI Button Click, CC0); 9,0 s whoosh (Deep Whoosh #1, CC0); 41,6 s pop (3 Popping Pops, CC0); 48,6 s chime (Magic Wings Soft, CC0) | -14,0 LUFS, TP -1,2 dBTP | falas nos tempos do events-16x9.json; vídeo copiado; áudio 52,0 s | keynote-lancamento-16x9.antigo.mp4 (ver nota) |
| 9x16 (30 s) | mesma faixa, 0 a 30 s | 0 a 30 s | fade in 0,6 s, fade out 3 s | falas em 2,6; 9,2; 16,6; 23,4; 26,2 s | 2,4 s clique; 6,4 s whoosh; 23,7 s pop; 28,3 s chime (mesmos sons) | -14,1 LUFS, TP -1,4 dBTP | falas do events-9x16.json; áudio 30,0 s | keynote-lancamento-9x16.antigo.mp4 |

Nota: a faixa é ambiente e não eletrônica minimalista, como pedia o brief. Não achei faixa melhor no free-audio.

## Mesa-do-pedido

| Formato | Música | Trecho e cortes | Entrada/saída | Narração | Efeitos | Loudness | Sincronia | Antigo |
|---|---|---|---|---|---|---|---|---|
| 16x9 (54 s) | Chill Lofi Melody Loop 95 BPM, holizna, Freesound CC0, 0:40 | loop 40 s repetido 2x, cortado em 54 s; a emenda não foi conferida por ouvido | fade in 0,6 s, fade out 3 s | falas v1 a v9 em 0; 4,5; 9,5; 14,5; 21,5; 26,0; 31,5; 35,5; 47,0 s | 4,5 s clique (Soft UI Button Click); 14,5 s pop; 31,5 s whoosh; 47,0 s chime. Os tempos são as entradas de fala, não eventos de tela | -14,0 LUFS, TP -1,1 dBTP | tempos das falas da lista do filme (LINHAS); vídeo copiado | mesa-do-pedido-16x9.antigo.mp4 |
| 9x16 (54 s) | mesma faixa e mesmos tempos | igual | igual | igual (assumido: o filme usa a mesma lista de falas nos dois formatos) | igual | -14,0 LUFS, TP -1,1 dBTP | assumido, não verificado no 9x16 | mesa-do-pedido-9x16.antigo.mp4 |

## Palito-como-funciona

| Formato | Música | Trecho e cortes | Entrada/saída | Narração | Efeitos | Loudness | Sincronia | Antigo |
|---|---|---|---|---|---|---|---|---|
| 16x9 (54,5 s) | ukulele/clarinet/percussion, Jamendo 1053995, CC BY-SA 3.0, 5:56 | 0 a 54,5 s, sem loop | fade in 0,6 s, fade out 3 s | falas de lines.json (t) | 0,5 s clique; 12,6 s whoosh; 31,0 s pop; 48,6 s chime (todos CC0 Freesound) | -14,1 LUFS, TP -1,0 dBTP | t de lines.json; vídeo copiado | palito-como-funciona-16x9.antigo.mp4 |
| 9x16 (31,5 s) | mesma faixa, 0 a 31,5 s | igual | igual | falas de lines_9x16.json, com os mapeamentos V04, V08, V09, V10 de mix.py | 0,2 s clique; 8,7 s whoosh; 18,2 s pop; 25,8 s chime | -14,1 LUFS, TP -1,1 dBTP | t de lines_9x16.json | palito-como-funciona-9x16.antigo.mp4 |

Vocal: não verificado. Licença BY-SA, a ser anotada no CREDITS.md da pasta.

## Planilha-do-carlos

| Formato | Música | Trecho e cortes | Entrada/saída | Narração | Efeitos | Loudness | Sincronia | Antigo |
|---|---|---|---|---|---|---|---|---|
| 16x9 (56 s) | Bossa Nova -reverie, Jamendo 393582, CC BY-SA 3.0, 5:20 | 0 a 56 s, sem loop | fade in 0,6 s, fade out 3 s | 11 falas l01 a l11 em 0,4; 6,152; 10,844; 14,779; 20,25; 25,109; 31,413; 36,71; 41,724; 46,414; 50,707 s (tempos das legendas) | 6,152 s clique; 25,109 s whoosh; 36,71 s pop; 50,707 s chime | -14,0 LUFS, TP -1,2 dBTP | legendas 16x9 (início de cada cue) | planilha-do-carlos-16x9.antigo.mp4 |
| 9x16 (27 s) | mesma faixa, 0 a 27 s | igual | igual | 6 falas v01 a v06 em 0,2; 2,772; 6,053; 9,241; 15,755; 20,048 s (mapeamento por ordem, assumido) | 2,772 s clique; 15,755 s whoosh; 20,048 s pop; 24,0 s chime | -14,0 LUFS, TP -1,3 dBTP | legendas 9x16 | planilha-do-carlos-9x16.antigo.mp4 |

Vocal: não verificado. Licença BY-SA, a ser anotada no CREDITS.md.

## Pixel-chuveiro (só opções, MP4 não alterado)

Pasta: pixel-chuveiro/musica-opcoes/ (com CREDITS.md). Todas CC0, Freesound, michorvath:
- Sequence (8-bit music loop), 1:54
- Falling (8-bit music loop), 2:40
- Rivalry (8-bit music loop), 3:23

Violão nordestino não encontrado: buscas "acoustic guitar brazil", "nylon guitar night", "baiao instrumental" e "forro instrumental" não trouxeram violão utilizável (o Freesound "forro" trouxe só percussão CC0).

## Cenários de erro (verificados na escolha)

| Cenário | Resultado |
|---|---|
| Faixa curta demais | Keynote e mesa usam loop curto (mesa: 40 s repetido). Keynote usa faixa de 2:29 |
| Faixa com vocal | Não verificado: earshot sem modelos. Planilha e palito pendentes |
| Licença NC/ND | Rejeitadas (várias Jamendo BY-NC-ND na busca) |
| BY-SA | Aceita para palito e planilha; anotar no CREDITS.md |
| Pico acima de -1 dBTP | Todos medidos entre -1,0 e -1,5 dBTP após o loudnorm |
| Fala mascarada | Ducking com sidechain; conferido só por medição, não por ouvido |
| Efeitos > 6 | Máximo 4 por filme |

## Arquivos antigos (.antigo.mp4)

Os MP4 anteriores foram apagados depois da conferência: número de quadros igual ao novo, duração de áudio igual e loudness medido. Os arquivos `.antigo.mp4` não existem mais.
