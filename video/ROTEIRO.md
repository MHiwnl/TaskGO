# Roteiro de gravação — narração do filme da TaskGO

São 10 falas curtas. O filme se ajusta ao seu ritmo: não precisa cronometrar.

## As falas

A narração é a voz clonada (XTTS-v2) de quem autorizou, a partir de `narracao/referencia/`. O texto vende: problema real, promessa, prova, chamada.

| Arquivo | Fala | Função |
|---|---|---|
| `01` | Chuveiro queimou. Você pede indicação no grupo, e torce pra alguém aparecer. | A dor: situação concreta que o público já viveu. |
| `02` | Com a TaskGO, um pedido resolve. | A promessa, em uma frase. |
| `03` | Na hora, os dez profissionais livres mais perto de você recebem. | Rapidez: resolve "é difícil achar alguém". |
| `04` | Todos com documento e rosto conferidos. Nada de estranho na sua porta. | Confiança: resolve "não dá pra confiar em qualquer um". |
| `05` | Seu endereço só aparece pra quem você escolher. | Privacidade. |
| `06` | Você vê ele chegando, ao vivo, no mapa. | Controle. |
| `07` | Na porta, um código de quatro números prova que é ele. | Prova na chegada. |
| `08` | E você só paga quando o serviço estiver pronto. | Remove o risco do dinheiro. |
| `09` | Disse que chegou e está a um quilômetro? Aqui, não cola. | A virada: a mentira comum que a TaskGO pega. |
| `10` | TaskGO. Confiança na hora que você precisa. Peça agora. | Assinatura + chamada para ação. |

Na síntese, "TaskGO" é escrito "Tésk Gôu" (campo `tts` de `narracao/falas.json`) para sair com a pronúncia certa.

## Se quiser trocar pela sua voz gravada (no celular serve)

1. Escolha um cômodo quieto, sem ventilador ou ar-condicionado ligado. Cômodo com cama, sofá ou cortina ajuda a não ter eco.
2. Segure o celular a um palmo da boca, um pouco de lado (assim o "p" e o "b" não estouram).
3. Use o gravador de voz do celular. Grave **um arquivo por fala**, com 1 segundo de silêncio antes e depois.
4. Fale um pouco mais devagar que numa conversa. Se errar, grave a fala de novo; pode mandar mais de uma tentativa (`03-a`, `03-b`).
5. Dê aos arquivos os nomes `01`, `02`… `10` (a extensão pode ser a do celular: `.m4a`, `.mp3`, `.ogg` ou `.wav`).

Se preferir, grave tudo num arquivo só, lendo as falas em ordem com 3 segundos de silêncio entre elas. Eu separo as falas.

## Onde colocar

Na pasta `video/narracao/voz/` do projeto. Quando os arquivos estiverem lá, me avise: eu limpo o ruído, acerto o volume e encaixo cada fala no tempo do filme.
