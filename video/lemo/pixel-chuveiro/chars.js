// Personagens em pixel art desenhados por código: um quadro 34×50 por vista, pose e expressão.
// O mesmo código gera a folha de personagens (sheet) e os sprites 3D da cena.
import { PX, ramp, hex } from './px.js';

export const FW = 34, FH = 50;   // pixels do quadro; os pés ficam em y 47
const INK = hex('#1a1016');
const LIT = (f, base, cx) => (x, y) => f(base + (cx - x) * .05, x, y);   // a luz vem da esquerda

const SOCORRO = {
  robe: ['#3b1f2a', '#6b3c48', '#97606a', '#c9949a'],
  hair: ['#3a3a44', '#6e6c76', '#9a97a2', '#bdbac4'],
  skin: ['#7a4a34', '#a06b4c', '#c48c68', '#e3b493'],
  shoe: ['#2a1812', '#4a2e20', '#6a4632'],
  towel: ['#7d6e5c', '#c2b19a', '#eee3d0'],
};
const CARLOS = {
  coat: ['#0b1640', '#1b3a94', '#2b59ff', '#8fb0ff'],
  skin: ['#5a3626', '#8a5a3e', '#b07a55', '#d9a27c'],
  hair: ['#14101a', '#2e2a34', '#4a4552'],
  pants: ['#12121a', '#24242e', '#3a3a48'],
  boot: ['#1a100c', '#2e1d15', '#4a3022'],
  cap: ['#14141e', '#26263a', '#3d3d58'],
  shirt: ['#4a5470', '#7f8aa8', '#b7c2dc'],
  metal: ['#2a2c33', '#50545e', '#8a909c', '#c6ccd6'],
  screen: ['#4a7bd6', '#9fd6ff', '#e6f5ff'],
};
// Cores da folha (paleta) para desenhar as amostras
export const PALETA = { socorro: SOCORRO, carlos: CARLOS };

const R = {};   // rampas, criadas uma vez por personagem
for (const [who, P] of [['socorro', SOCORRO], ['carlos', CARLOS]]) {
  R[who] = Object.fromEntries(Object.entries(P).map(([k, cols]) => [k, ramp(cols)]));
}

// Um quadro: who 'socorro'|'carlos'; o: {view:'front'|'34'|'side'|'back', pose, expr, walk 0..3|-1}
// Retorna {c, e}: cor e emissão (tela do celular)
export function figure(who, o = {}) {
  const P = new PX(FW, FH), E = new PX(FW, FH);
  const view = o.view || 'front', pose = o.pose || 'stand', expr = o.expr || 'neutral', k = o.walk ?? -1;
  const sw = { front: 1, '34': .9, side: .72, back: 1 }[view];
  const fx = { front: 0, '34': 1, side: 2, back: 0 }[view];
  const cx = 17, EX = cx + fx, ey = 11, my = 15;
  const Rm = R[who], soc = who === 'socorro';
  const f = k < 0 ? 0 : [-3, 0, 3, 0][k];   // passada: pernas se afastam e voltam
  const sk = LIT(Rm.skin, .5, cx);

  // Pernas e sapatos
  if (soc) {
    P.rect(11 + (f >> 1), 45, 5, 2, LIT(Rm.shoe, .5, cx));
    P.rect(18 - (f >> 1), 45, 5, 2, LIT(Rm.shoe, .5, cx));
  } else {
    P.poly([[12, 38], [16, 38], [16 + f, 46], [12 + f, 46]], LIT(Rm.pants, .5, cx));
    P.poly([[18, 38], [22, 38], [22 - f, 46], [18 - f, 46]], LIT(Rm.pants, .35, cx));
    P.rect(11 + f, 46, 6, 2, LIT(Rm.boot, .5, cx));
    P.rect(18 - f, 46, 6, 2, LIT(Rm.boot, .5, cx));
  }

  // Tronco: robe da Socorro (até o chão, com pregas) ou capa azul do Carlos (até o joelho)
  if (soc) {
    const hem = k < 0 ? 0 : [0, 1, 0, -1][k];
    P.poly([[cx - 5 * sw, 19], [cx + 5 * sw, 19], [cx + 7 * sw + hem, 45], [cx - 7 * sw - hem, 45]], (x, y) => {
      let v = .62 - (x - cx) * .03 + (y - 19) * .004;
      if ((x + (y >> 2)) % 5 === 0 && y > 24) v -= .18;   // pregas
      return Rm.robe(v, x, y);
    });
    P.rect(cx - 6 * sw, 28, 12 * sw, 1, LIT(Rm.robe, .85, cx));   // cordão da cintura
  } else {
    P.poly([[cx - 5.5 * sw, 19], [cx + 5.5 * sw, 19], [cx + 6.5 * sw, 40], [cx - 6.5 * sw, 40]], LIT(Rm.coat, .55, cx));
    if (view !== 'side' && view !== 'back') {
      P.rect(cx - 1, 19, 2, 21, LIT(Rm.shirt, .8, cx));   // camisa aberta
      if (!soc) P.rect(cx - 4, 20, 2, 1, LIT(Rm.coat, .95, cx));   // reflexo da capa molhada
      P.rect(cx - 5, 31, 10, 1, LIT(Rm.metal, .6, cx));   // cinto de ferramentas
      P.rect(cx + 2, 30, 2, 2, LIT(Rm.metal, .85, cx));
    }
  }

  // Cabeça
  if (soc) {
    P.ellipse(cx, 10.5, 6.4 * sw, 7.2, LIT(Rm.hair, .6, cx));   // cabelo grisalho
    P.ellipse(cx - 6 * sw, 6.5, 2.4, 2.2, LIT(Rm.hair, .75, cx)); // coque atrás
    if (view !== 'back') P.ellipse(cx + fx * .4, 12, 5.2 * sw, 6, sk);
  } else {
    P.ellipse(cx, 12, 5.4 * sw, 6, sk);
    P.ellipse(cx, 7.8, 6.2 * sw, 3.6, LIT(Rm.cap, .45, cx));   // boné
    if (view === 'front') P.rect(cx - 5, 8, 10, 1, LIT(Rm.cap, .3, cx));
    else if (view !== 'back') P.rect(cx + 1, 8, 6, 1, LIT(Rm.cap, .3, cx));
  }

  // Braços: o ombro fica no tronco; o da frente pega o objeto
  const shL = [cx - 5.5 * sw, 21], shR = [cx + 5.5 * sw, 21];
  const sw2 = f > 0 ? 1 : f < 0 ? -1 : 0;   // balanço do braço na passada
  const armCol = soc ? LIT(Rm.robe, .7, cx) : LIT(Rm.coat, .7, cx);
  if (soc) {
    if (pose === 'towel') {
      P.stroke(shL[0], shL[1], cx - 6 * sw, 27, armCol, 2);
      P.stroke(shR[0], shR[1], cx + 6 * sw, 27, armCol, 2);
      P.rect(cx - 6 * sw, 23, 12 * sw, 5, LIT(Rm.towel, .75, cx));   // toalha no peito
      P.rect(cx - 4, 27, 2, 2, sk); P.rect(cx + 2, 27, 2, 2, sk);
    } else if (pose === 'gesto') {   // gesticula com a mão direita; a esquerda segura a toalha
      P.stroke(shL[0], shL[1], cx - 6 * sw, 27, armCol, 2);
      P.stroke(shR[0], shR[1], cx + 8 * sw, 18, armCol, 2);
      P.stroke(cx + 8 * sw, 18, cx + 9 * sw, 11, armCol, 2);
      P.rect(cx + 9 * sw, 9, 2, 2, sk);
    } else {   // 'mãos' : mãos juntas no peito, aliviada
      P.stroke(shL[0], shL[1], cx - 5, 29, armCol, 2);
      P.stroke(shR[0], shR[1], cx + 5, 29, armCol, 2);
      P.rect(cx - 2, 28, 4, 3, sk);
    }
  } else {
    if (pose === 'phone') {
      P.stroke(shR[0], shR[1], cx + 7 * sw, 28, armCol, 2);
      P.stroke(cx + 7 * sw, 28, cx + 3 * sw, 23, armCol, 2);
      P.rect(cx + 2, 16, 4, 7, LIT(Rm.metal, .3, cx));      // celular
      E.rect(cx + 2, 17, 4, 5, Rm.screen(.9, 0, 0));       // tela acesa
    } else if (pose === 'breaker') {
      P.stroke(shR[0], shR[1], cx + 8 * sw, 17, armCol, 2);
      P.stroke(cx + 8 * sw, 17, cx + 9 * sw, 12, armCol, 2);
      P.rect(cx + 9 * sw, 10, 2, 2, sk);
    } else {
      const hx = cx - 7 * sw + sw2, hx2 = cx + 7 * sw - sw2;
      P.stroke(shL[0], shL[1], cx - 7 * sw, 28, armCol, 2); P.stroke(cx - 7 * sw, 28, hx, 34, armCol, 2);
      P.stroke(shR[0], shR[1], cx + 7 * sw, 28, armCol, 2); P.stroke(cx + 7 * sw, 28, hx2, 34, armCol, 2);
      P.rect(hx, 34, 2, 2, sk); P.rect(hx2, 34, 2, 2, sk);
      P.rect(hx2 - 1, 36, 5, 4, LIT(Rm.metal, .45, cx)); P.rect(hx2, 35, 3, 1, LIT(Rm.metal, .8, cx));   // caixa de ferramentas
    }
  }

  // Rosto: olhos, sobrancelhas e boca por expressão (sem rosto de costas)
  if (view !== 'back') {
    const eyes = view === 'side' ? [EX + 2] : [EX - 2, EX + 2];
    const px = (a) => a.forEach(([x, y]) => P.set(x, y, INK));
    if (expr === 'neutral') {
      eyes.forEach(x => P.set(x, ey, INK));
      P.rect(EX - 1, my, 2, 1, INK);
    } else if (expr === 'worry') {
      eyes.forEach(x => P.set(x, ey, INK));
      px([[EX - 3, ey - 2], [EX - 2, ey - 3], [EX + 1, ey - 3], [EX + 2, ey - 2]]);   // sobrancelhas erguidas no meio
      px([[EX - 2, my + 1], [EX - 1, my], [EX, my], [EX + 1, my + 1]]);            // boca torta
    } else {   // relief: olhos fechados em arco e sorriso
      px([[EX - 3, ey], [EX - 2, ey - 1], [EX - 1, ey]]);
      px([[EX + 1, ey], [EX + 2, ey - 1], [EX + 3, ey]]);
      px([[EX - 2, my - 1], [EX - 1, my], [EX, my], [EX + 1, my], [EX + 2, my - 1]]);
    }
    if (o.blink) eyes.forEach(x => P.rect(x - 1, ey, 3, 1, INK));   // piscar: olhos fechados
  }

  P.outline(.32);
  return { c: P.done(), e: E.done() };
}

// Folha de personagens em um canvas 1920×1080: giro (4 vistas), 3 expressões, 2 poses e a paleta
export function drawSheet(g) {
  const S = 3;   // escala: cada pixel do sprite vira 3×3 na folha
  g.imageSmoothingEnabled = false;
  g.fillStyle = '#14191c'; g.fillRect(0, 0, 1920, 1080);
  g.fillStyle = '#e8e2d2'; g.font = '22px sans-serif';
  const cell = (who, o, x, y, label) => {
    const { c, e } = figure(who, o);
    const w = FW * S, h = FH * S;
    g.fillStyle = '#26303a'; g.fillRect(x - 6, y - 6, w + 12, h + 12);
    g.drawImage(c, x, y, w, h);
    g.globalCompositeOperation = 'lighter'; g.drawImage(e, x, y, w, h); g.globalCompositeOperation = 'source-over';
    g.fillStyle = '#e8e2d2'; g.font = '18px sans-serif'; g.fillText(label, x, y + h + 26);
  };
  const names = { socorro: 'Dona Socorro', carlos: 'Carlos Andrade' };
  const rows = [['socorro', 70], ['carlos', 320]];
  const views = [['front', 'frente'], ['34', '3/4'], ['side', 'perfil'], ['back', 'costas']];
  for (const [who, y] of rows) {
    g.fillStyle = '#e8e2d2'; g.font = '24px sans-serif'; g.fillText(names[who] + ' · giro', 60, y - 18);
    views.forEach(([v, lab], i) => cell(who, { view: v, walk: -1 }, 60 + i * 180, y, lab));
  }
  // Expressões (vista de frente)
  g.font = '24px sans-serif'; g.fillStyle = '#e8e2d2'; g.fillText('Expressões', 60, 580);
  ['neutral', 'worry', 'relief'].forEach((ex, i) => {
    cell('socorro', { expr: ex }, 60 + i * 180, 610, 'Socorro · ' + ex);
    cell('carlos', { expr: ex }, 660 + i * 180, 610, 'Carlos · ' + ex);
  });
  // Poses
  g.font = '24px sans-serif'; g.fillStyle = '#e8e2d2'; g.fillText('Poses', 60, 830);
  cell('socorro', { pose: 'towel', expr: 'worry' }, 60, 860, 'toalha · preocupada');
  cell('socorro', { pose: 'hands', expr: 'relief' }, 240, 860, 'mãos no peito · aliviada');
  cell('carlos', { pose: 'phone', walk: -1 }, 660, 860, 'mostra o código');
  cell('carlos', { pose: 'breaker', walk: -1 }, 840, 860, 'disjuntor');
  // Paleta: amostras das rampas usadas em cada personagem (coluna à direita)
  g.font = '24px sans-serif'; g.fillStyle = '#e8e2d2'; g.fillText('Paleta', 1180, 60);
  let sx = 1180, sy = 100;
  for (const [who, P] of [['socorro', SOCORRO], ['carlos', CARLOS]]) {
    for (const [part, cols] of Object.entries(P)) {
      cols.forEach((c, i) => { g.fillStyle = c; g.fillRect(sx + i * 44, sy, 40, 40); });
      g.fillStyle = '#e8e2d2'; g.font = '14px sans-serif'; g.fillText(who + ' ' + part, sx, sy + 58);
      sy += 76;
      if (sy > 960) { sy = 100; sx += 330; }
    }
  }
}
