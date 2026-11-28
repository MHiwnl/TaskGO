// Câmera do filme "A mesa do pedido": [t, alvo (Vector3 | nome de cartão), distância, azimute, elevação, fov, deslocamento?, {ease}?].
// Azimute 0 = câmera no lado +z olhando para −z. Elevação em radianos. Ease = o alvo para (começo de um whip).
// Os tempos seguem TREATMENT.md (§4). Na vertical (9:16) as distâncias são recalculadas: a janela é mais estreita.
import { V } from './anim.js';
const R = { ease: 1 };
const H = [
  // P1 abertura: macro sobre Contratar, pouso no tempo 1
  [0.0, 'con', .62, .36, .6, 34],
  [1.2, 'con', .6, .3, .62, 34],
  // P2: whip de Contratar para Oportunidades
  [3.9, 'con', .6, .3, .62, 34, null, R],
  [4.4, 'opo', .6, -.25, .66, 34],
  // P3: aceite em Oportunidades
  [9.0, 'opo', .6, -.2, .66, 34, null, R],
  // P4: cavalgada até o cartão do pedido, subindo para quase o topo
  [14.0, 'ped', .9, .3, 1.0, 34, null, R],
  // P5: assinatura, o código sobe do cartão do pedido
  [19.5, 'ped', .5, .12, 1.02, 34],
  // P6 e P7: cavalgada para Agenda
  [21.5, 'ped', .6, .2, .8, 34, null, R],
  [26.0, 'age', .6, .25, .7, 34, null, R],
  // P8: pagamento em Pedidos
  [31.5, 'pds', .6, .2, .7, 34, null, R],
  // P9: whip para Avaliações
  [35.5, 'pds', .6, .2, .7, 34, null, R],
  [36.0, 'ava', .6, .3, .7, 34],
  [40.0, 'ava', .6, .3, .7, 34, null, R],
  // P10 e P11: recuo sobre a mesa inteira
  [47.0, V(0, .05, 0), 2.5, 0, 1.1, 34, null, R],
  [54.0, V(0, .05, 0), 2.6, 0, 1.15, 34, null, R],
];
// vertical: cartão estreito, então a câmera recua; o recuo final enquadra a mesa mais alta
const P = H.map(k => typeof k[1] === 'string'
  ? [k[0], k[1], Math.max(1.0, k[2] * 1.7), k[3], k[4], k[5], k[6], k[7]]
  : [k[0], k[1], 5.2, k[3], k[4], k[5], k[6], k[7]]);
export const KEYS = innerHeight > innerWidth ? P : H;
// impactos: [t, amplitude, queda] (aceite no tempo 10, título no 47)
export const SHAKES = [[10.0, .004, 9], [47.0, .006, 10]];
