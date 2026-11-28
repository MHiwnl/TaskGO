// A mesa do pedido (TaskGO): filme de 54 s em 16:9 e corte em 9:16 (mesmo roteiro; o layout muda pelo tamanho da janela).
import * as THREE from 'three';
import * as K from './cards.js';
import { card, pill, rbox } from './world.js';
import { makeMonster } from './voxel.js';
import { V, hops, beatBounce, squash, pop, seg, back, eo } from './anim.js';

const PORT = innerHeight > innerWidth;   // 9:16
export const DUR = 54, MAT = PORT ? [1.44, 3.0] : [2.4, 1.8];
export const END = { credit: 99 };       // sem assinatura da biblioteca: é filme do usuário
export const GLYPHS = 'áéíóúâêôãõçàÁÉÍÓÚÂÊÔÃÕÇ·—–“”’';

// narração (lines.json); a legenda fica o tempo real da fala + 0,6 s (mínimo 1,8 s)
const LINHAS = [
  [0.0, 'Um pedido de ar-condicionado começa no celular, com um toque.'],
  [4.5, 'Você descreve o serviço e chama um profissional perto de você.'],
  [9.5, 'Ele aceita pelo celular, e o pedido entra na agenda dele.'],
  [14.5, 'Você acompanha a chegada no mapa até ele estar na porta.'],
  [21.5, 'Quando ele chega, você informa o código de quatro dígitos.'],
  [26.0, 'Só então ele digita o código no app, e o serviço começa.'],
  [31.5, 'Depois, você paga o profissional por Pix ou dinheiro.'],
  [35.5, 'Por fim, você avalia com estrelas, e o profissional avalia você.'],
  [47.0, 'Do primeiro toque à avaliação: TaskGO.'],
];
const TELAS = src => new URL(`../telas/${src}`, import.meta.url).href;   // relativo a este módulo
const VOZ = src => new URL(`../vo/${src}`, import.meta.url).href;
const DURV = await fetch(VOZ('dur.json')).then(r => r.json());
export const VO = LINHAS.map(([t0, zh], i) => ({ t0, t1: t0 + Math.max(DURV[`v${i + 1}`] + .6, 1.8), zh, en: '' }));

// telas reais (capturar_telas.py) e o código gravado junto com a captura
const FOTO = { con: '02-cliente-contratar', opo: '08b-profissional-aceitar', age: '09-profissional-agenda',
  ped: '04b-cliente-pedido-codigo', pds: '03-cliente-pedidos', ava: '07-cliente-avaliacoes' };
const abrir = src => new Promise((ok, no) => { const im = new Image(); im.onload = () => ok(im); im.onerror = () => no(new Error('falta ' + src)); im.src = src; });
const IMG = {};
await Promise.all(Object.entries(FOTO).map(async ([k, f]) => { IMG[k] = await abrir(TELAS(`${f}.png`)); }));
const COD = (await fetch(TELAS('codigo.json')).then(r => r.json())).codigo;

const PW = .34, PH = .708, TH = .006;   // celular 0,34 × 0,708 unidades; espessura do papel
const STL = { con: [-.9, -.5], opo: [-.3, -.5], age: [.3, -.5], ped: [.9, 0], pds: [.3, .5], ava: [-.3, .5] };
const STP = { con: [-.22, -.9], opo: [.22, -.9], age: [.22, 0], ped: [-.22, 0], pds: [-.22, .9], ava: [.22, .9] };
const ST = PORT ? STP : STL;
const TENDA = PORT ? [-.55, 0] : [-.9, 0];
const TITULO = PORT ? [0, 1.40] : [0, 0];
const ROTULO = { con: '01 PEDIR', opo: '02 ACEITAR', age: '03 AGENDA', ped: '04 A CAMINHO', pds: '05 PAGAR', ava: '06 AVALIAR' };
const COR = { H: '#2f3a5a', S: '#f6c7a1', K: '#000000', B: '#f39a5b', P: '#3a4466', Q: '#2a2f3d' };   // K = olhos
const FIG = ['..HHHHHH..', '.HHHHHHHH.', '..SSSSSS..', '..SKSSKS..', '..SSSSSS..', '..BBBBBB..',
  '.BBBBBBBB.', '.BBBBBBBB.', '..BBBBBB..', '..PP..PP..', '..PP..PP..', '.QQ....QQ.'];

function celular(im) {   // tela inteira num celular: moldura escura e a captura real na proporção certa
  const [cv, c] = K.canvas(340, 708, 3);
  c.fillStyle = '#16191f'; K.rr(c, 0, 0, 340, 708, 40); c.fill();
  c.save(); K.rr(c, 12, 12, 316, 684, 28); c.clip(); c.drawImage(im, 12, 12, 316, 684); c.restore();
  return cv;
}
function cartaoTexto(w, h, draw) {   // cartão de papel com texto impresso
  const [cv, c] = K.canvas(w, h, 3);
  c.fillStyle = K.COL.card; K.rr(c, 0, 0, w, h, 14); c.fill();
  draw(c);
  return cv;
}

export function build(ctx) {
  const { world, subj, camera } = ctx, { scene } = world;
  ctx.fx = { flash: 0 }; ctx.read = [];
  const pills = [];

  for (const k in ST) {
    const [x, z] = ST[k];
    const g = card(world, celular(IMG[k]), PW, PH, TH); g.position.set(x, 0, z); scene.add(g);
    subj[k] = () => V(x, .18, z);
    const p = pill(world, ROTULO[k], { bg: K.COL.accent, fg: '#ffffff', h: .024, size: 36 });
    p.castShadow = false;   // rótulo flutuante não faz sombra sobre o texto
    const px = z === 0 ? x + Math.sign(x) * .2 : x, pz = z === 0 ? z : z + (z < 0 ? -.39 : .39);
    p.position.set(px, .03, pz); pills.push({ m: p, base: .03 });
  }

  // cartão-título em pé, ao lado do primeiro cartão
  const tc = cartaoTexto(300, 160, c => {
    const gw = K.txt(c, 'Task', 26, 66, { size: 60, wt: 700, font: K.SANS });
    K.txt(c, 'GO', 26 + gw, 66, { size: 60, wt: 700, font: K.SANS, col: K.COL.accent });
    K.tag(c, 'um pedido', 26, 122, 18);
  });
  const tent = new THREE.Mesh(new THREE.PlaneGeometry(.3, .16), new THREE.MeshStandardMaterial({ map: world.tex(tc), roughness: .8, side: THREE.DoubleSide }));
  tent.position.set(TENDA[0], .075, TENDA[1]); tent.rotation.x = -.22; tent.castShadow = true; scene.add(tent);

  // cartão-título final: cai sobre a mesa aberta no fim (curtain call)
  const fc = cartaoTexto(420, 140, c => {
    const gw = K.txt(c, 'Task', 34, 70, { size: 72, wt: 700, font: K.SANS });
    K.txt(c, 'GO', 34 + gw, 70, { size: 72, wt: 700, font: K.SANS, col: K.COL.accent });
    K.tag(c, 'do toque à avaliação', 34, 116, 18);
  });
  const title = new THREE.Mesh(new THREE.PlaneGeometry(.42, .14), new THREE.MeshStandardMaterial({ map: world.tex(fc), roughness: .8 }));
  title.rotation.x = -Math.PI / 2; title.position.set(TITULO[0], .002, TITULO[1]); title.visible = false; scene.add(title);

  // a figura do pedido: pixel art feita de cubos biselados
  const cells = [];
  FIG.forEach((row, j) => [...row].forEach((ch, i) => { if (ch !== '.') cells.push([i, j, COR[ch]]); }));
  const fig = makeMonster({ w: 10, h: 12, cells }, .0075, 'pedido');
  scene.add(fig.root);
  const spot = k => V(ST[k][0] + .11, TH, ST[k][1] - .22);   // canto do cartão, sem cobrir o código
  const L = [   // saltos em ordem: [t0, t1, de, para, altura]
    { t0: 4.5, t1: 5.5, from: spot('con'), to: spot('opo'), h: .09 },
    { t0: 13.5, t1: 15.0, from: spot('opo'), to: spot('ped'), h: .14 },
    { t0: 22.0, t1: 23.5, from: spot('ped'), to: spot('age'), h: .14 },
    { t0: 36.0, t1: 37.5, from: spot('age'), to: spot('ava'), h: .14 },
  ];

  // assinatura: os dígitos do código sobem da tela, um por colcheia (UI rises)
  const tileGeo = rbox(.026, 1, .026, .004); tileGeo.translate(0, .5, 0);
  const tiles = [...COD].slice(0, 4).map((d, i) => {
    const g = new THREE.Group();
    const body = new THREE.Mesh(tileGeo, new THREE.MeshStandardMaterial({ color: K.COL.accent, roughness: .6 }));
    body.castShadow = true; g.add(body);
    const lc = cartaoTexto(60, 60, c => { c.fillStyle = K.COL.accent; c.fillRect(0, 0, 60, 60); K.txt(c, d, 30, 32, { size: 44, wt: 700, col: '#ffffff', align: 'center' }); });
    const label = new THREE.Mesh(new THREE.PlaneGeometry(.026, .026), new THREE.MeshStandardMaterial({ map: world.tex(lc), transparent: true, roughness: .6 }));
    label.rotation.x = -Math.PI / 2; g.add(label);
    g.position.set(ST.ped[0] + (i - 1.5) * .033, TH, ST.ped[1] + .11); scene.add(g);
    return { body, label, t0: 19.5 + i * .125 };
  });

  // dados do pagamento: quatro cubos saem do cartão do pedido e chegam a Pedidos
  const cubeGeo = rbox(.018, .018, .018, .003);
  const cubos = [0, 1, 2, 3].map(i => {
    const m = new THREE.Mesh(cubeGeo, new THREE.MeshStandardMaterial({ color: K.COL.owl, roughness: .5 }));
    m.castShadow = true; m.visible = false; scene.add(m);
    return { m, s: 31.5 + i * .25 };
  });
  const A = V(ST.ped[0], TH + .01, ST.ped[1] + .2), B = V(ST.pds[0], TH + .01, ST.pds[1] - .2);

  // anel de impacto no botão Aceitar (tempo 10)
  const ring = new THREE.Mesh(new THREE.RingGeometry(.9, 1, 72), new THREE.MeshBasicMaterial({ color: K.COL.accent, transparent: true, depthWrite: false }));
  ring.rotation.x = -Math.PI / 2; ring.position.set(ST.opo[0], TH + .001, ST.opo[1]); scene.add(ring);

  return function update(t) {
    // o pedido: nasce (adesivo → figura), salta, espera no ritmo, e no fim vira registro impresso
    const hop = hops(t, L, spot('con'));
    const pu = pop(t, 0, .45, .05);
    const bn = beatBounce(t, 5.5, 13.5, .012);
    const sq = squash(t, hop.lands, hop.takeoffs, hop.air) * pu.sq * bn.sq;
    const volta = t >= 40;
    const e = volta ? 1 - seg(t, 40, 40.4) : pu.e;
    const hinge = volta ? 1 - seg(t, 40, 40.3) : pu.hinge;
    fig.set({ pos: hop.pos.clone().add(V(0, pu.y + bn.y, 0)), yaw: 0, e, hinge, sq });

    // cartões, legendas e tempo de cada rótulo: o curtain call faz cada um pular uma vez
    for (const { m, base } of pills) {
      m.face(camera);
      m.position.y = base;
    }
    pills.forEach((p, i) => { const s = 47 + i * .3; if (t >= s && t < s + .25) p.m.position.y = base0(p) + .03 * Math.sin(Math.PI * seg(t, s, s + .25)); });

    for (const k of tiles) {
      const hg = .002 + .016 * back(seg(t, k.t0, k.t0 + .35), 1.6);
      k.body.scale.y = hg; k.label.position.y = hg + .0006;
    }
    for (const c of cubos) {
      const on = t >= c.s && t < c.s + 1.2;
      c.m.visible = on;
      if (on) { const p = seg(t, c.s, c.s + 1.2); c.m.position.lerpVectors(A, B, p).add(V(0, .1 * 4 * p * (1 - p), 0)); }
    }
    const r = seg(t, 10, 11);
    ring.visible = t >= 10 && t < 11; ring.scale.setScalar(.02 + .1 * r); ring.material.opacity = 1 - r;

    // título final: cai com aceleração e assenta sobre a mesa
    title.visible = t >= 47;
    if (title.visible) title.position.y = .002 + .3 * (1 - eo(seg(t, 47, 47.45))) ** 2;
  };
}

// posição de repouso de um rótulo (o pulo do curtain call parte dela)
function base0(p) { return p.base; }
