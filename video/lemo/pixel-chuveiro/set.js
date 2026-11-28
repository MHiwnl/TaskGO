// Cenas 3D do filme (Recife à noite, chuva): rua com casas geminadas, caminho molhado com ponte e Casa Amarela,
// mapa de cima, a casa em corte que acende cômodo a cômodo, e a cidade ao fundo. Grupos mostram e escondem as luzes junto.
import * as THREE from 'three';
import { PX, tex, hash, ramp } from './px.js';
import { figure, FW, FH } from './chars.js';

const M = 1 / 30;            // metros por pixel (sprites e texturas: 30 px/m)
const CAS = { banheiro: 0, cozinha: .4, sala: .85, quarto: 1.3, corredor: 1.3 };   // acender em cascata, s após a virada
const AMARELA = ['#c49a1c', '#f0c93a', '#ffe783'];   // Casa Amarela de bairro
const OCRE = ['#5c4120', '#a77b34', '#e2b35a'], ROSA = ['#6a3440', '#a95e6c', '#e3a0a6'], AZUL = ['#20365a', '#3f6ca0', '#8fb3d9'],
  VERDE = ['#27452f', '#4f7d4f', '#93b97a'], TERRA = ['#5b2a22', '#9c4a36', '#d9875a'];

const cinza = c => new THREE.MeshLambertMaterial({ color: c });
const lamb = map => new THREE.MeshLambertMaterial({ map });
function bloco(w, h, d, x, y, z, mat) {   // caixa com sombra; mat = cor ou material
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), typeof mat === 'string' ? cinza(mat) : mat);
  m.position.set(x, y, z); m.castShadow = m.receiveShadow = true; return m;
}
function chaoPlano(w, d, cv, rep) {   // chão horizontal com textura de pixels; rep = metros por repetição da textura
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), lamb(tex(cv, [w / rep, d / rep])));
  m.rotation.x = -Math.PI / 2; m.receiveShadow = true; return m;
}
const piso = (x0, x1, z0, z1, cv) => { const m = chaoPlano(x1 - x0, z1 - z0, cv, 4); m.position.set((x0 + x1) / 2, .005, (z0 + z1) / 2); return m; };

// ——— texturas em pixel ———
function pedraTex() {   // pedra portuguesa de Recife, molhada: ondas pretas e brancas de pedrinhas com rejunte, e poças
  const S = 128, P = new PX(S, S), TAU = Math.PI * 2;
  const CLARA = ['#7f888b', '#9aa3a6', '#b3babc'], ESCURA = ['#1c2326', '#262f33', '#30393d'];
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const sx = x >> 2, sy = y >> 2;   // pedrinha de 4 px
    if (x % 4 === 0 || y % 4 === 0) { P.set(x, y, '#121719'); continue; }   // rejunte
    const onda = Math.sin(TAU * 3 * (sy * 4 + 7 * Math.sin(TAU * 2 * sx * 4 / S)) / S);   // faixas onduladas (repetem sem emenda)
    const pal = onda > 0 ? CLARA : ESCURA, h = hash(sx, sy);
    P.set(x, y, pal[h < .33 ? 0 : h < .7 ? 1 : 2]);
  }
  for (const [cx, cy, r] of [[34, 40, 9], [96, 98, 7]]) {   // poças com borda clara de reflexo
    for (let y = cy - r - 2; y <= cy + r + 2; y++) for (let x = cx - 2 * r - 3; x <= cx + 2 * r + 3; x++) {
      const d = Math.hypot((x - cx) / 2, y - cy);
      if (d < r) P.set((x + S) % S, (y + S) % S, d < r - 2 ? '#1d3644' : '#3c6274');
      else if (d < r + 1) P.set((x + S) % S, (y + S) % S, '#8fb4c4');
    }
  }
  return P.done();
}
function roadTex() {   // asfalto molhado: agregado, remendo, rachadura e brilho da luz
  const S = 96, P = new PX(S, S), R = ramp(['#20272b', '#2c353a', '#3a454c', '#4b575e']);
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const remendo = x > 54 && x < 80 && y > 10 && y < 30;
    let c = R((remendo ? .15 : .35) + .45 * hash(x, y) * hash(x >> 2, y >> 1), x, y);
    if (y % 13 === 5 && hash(x >> 3, y) > .6) c = '#6f8794';   // reflexo da luz no molhado
    P.set(x, y, c);
  }
  for (let i = 0; i < 40; i++) { const x = 12 + i, y = 60 + Math.round(4 * Math.sin(i / 5)); P.set(x, y, '#141a1d'); }   // rachadura
  return P.done();
}
// Meio-fio, faixa tracejada e bueiro: dão leitura de rua (calçada alta, pista, sarjeta)
function detalheRua(g, x0, x1, zMeioFio, zFaixa) {
  const w = x1 - x0, xc = (x0 + x1) / 2;
  for (const z of zMeioFio) g.add(bloco(w, .16, .24, xc, .08, z, '#8d9396'));
  if (zFaixa !== null) for (let x = x0 + .6; x < x1 - .6; x += 2.2) g.add(bloco(1.1, .012, .12, x, .02, zFaixa, '#c9a94a'));
  for (let x = x0 + 3; x < x1 - 1; x += 9) {   // bueiro com grelha na sarjeta
    const z = zMeioFio[0] + (zMeioFio[0] < 0 ? .45 : -.45);
    g.add(bloco(.9, .02, .45, x, .015, z, '#0e1214'));
    for (let k = -3; k <= 3; k++) g.add(bloco(.05, .025, .4, x + k * .12, .02, z, '#5d666b'));
  }
}
function aguaTex() {   // rio: ondulação quantizada com cristas claras (a água se lê como água, não como chão escuro)
  const P = new PX(32, 64), R = ramp(['#16384a', '#1f4c60', '#2c6680', '#3f84a0']);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 32; x++) {
    const crista = (y + (x >> 3) * 3) % 9 === 0 && hash(x >> 2, y) > .45;
    P.set(x, y, crista ? '#8fc2d4' : R(.35 + .45 * hash(x >> 1, y >> 2), x, y));
  }
  return P.done();
}
function tijoloTex() {   // parede da ponte: tijolo antigo com argamassa
  const P = new PX(64, 32), R = ramp(['#5a3a2c', '#734a36', '#8a5a40']);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) {
    const fila = y >> 3, off = fila % 2 ? 8 : 0;
    if (y % 8 === 0 || (x + off) % 16 === 0) P.set(x, y, '#3b332c');
    else P.set(x, y, R(.3 + .5 * hash((x + off) >> 4, fila), x, y));
  }
  return P.done();
}
function ladrilhoTex(c1, c2) {   // piso quadriculado com rejunte
  const P = new PX(96, 96), R = ramp([c1, c2]);
  for (let y = 0; y < 96; y++) for (let x = 0; x < 96; x++) {
    const junta = x % 24 === 0 || y % 24 === 0;
    P.set(x, y, junta ? '#3a3d42' : R(((x / 24 | 0) + (y / 24 | 0)) % 2 ? .8 : .2, x, y));
  }
  return P.done();
}
function carpeteTex() {
  const P = new PX(96, 96), R = ramp(['#4a2630', '#6a3644', '#8a4b56']);
  for (let y = 0; y < 96; y++) for (let x = 0; x < 96; x++) P.set(x, y, R(.3 + .4 * hash(x >> 1, y >> 1), x, y));
  return P.done();
}
function plankTex() {
  const P = new PX(96, 96), R = ramp(['#2b1a14', '#4a2c1c', '#6b4127', '#8a5a35']);
  for (let y = 0; y < 96; y++) for (let x = 0; x < 96; x++) {
    const row = Math.floor(y / 8), seam = y % 8 === 0, joint = (x + row * 13) % 40 === 0;
    P.set(x, y, seam || joint ? '#1a0f0b' : R(.3 + .5 * hash(Math.floor((x + row * 13) / 40), row), x, y));
  }
  return P.done();
}
function wallTex() {   // parede de reboco com friso
  const P = new PX(144, 120), R = ramp(['#262630', '#3f3f4a', '#64636b', '#8a8880']);
  for (let y = 0; y < 120; y++) for (let x = 0; x < 144; x++) P.set(x, y, R(.45 + .07 * hash(x >> 3, y >> 3) - (y > 100 ? .2 : 0), x, y));
  return P.done();
}
function azulejoTex() {   // azulejo de banheiro: quadrados brancos com borda azul
  const P = new PX(48, 48);
  for (let y = 0; y < 48; y++) for (let x = 0; x < 48; x++) {
    const borda = x % 12 < 1 || y % 12 < 1;
    P.set(x, y, borda ? '#2a4fa8' : (x % 12 < 4 && y % 12 < 4 ? '#8fc6cf' : '#e9f0f0'));
  }
  return P.done();
}
function mapaTex() {   // Recife de cima, 8 px por metro: quarteirões com telhados, praças, rio e ponte
  const N = 320, P = new PX(N, N), grama = ramp(['#1d2a24', '#273a31', '#334b40']), telhado = ramp(['#4b3428', '#7a4a36', '#9c5f42']);
  const agua = ramp(['#17394a', '#1f4a5c', '#2f6578']), copa = ramp(['#173222', '#24503a', '#3f7a4f']);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const X = x / 8 - 20, Z = y / 8 - 20, bx = Math.floor((X + 20) / 6), bz = Math.floor((Z + 20) / 6);
    const dx = X - (-16.4 + 6 * bx), dz = Z - (-16.4 + 6 * bz), h = hash(bx, bz);
    let c;
    if (X > 7.6 && X < 12.4 && Z > -8 && Z < -6.8) c = '#8a7d6a';                      // ponte (rua Z = -7,4)
    else if (X > 8 && X < 12) c = agua(.5 + .4 * hash(x >> 1, y >> 3), x, y);           // rio
    else if ((X + 20) % 6 < 1.2 || (Z + 20) % 6 < 1.2) c = '#4b535a';                   // ruas
    else if (h > .8) c = Math.hypot(dx, dz) < 1.7 ? copa(Math.hypot(dx, dz) < 1 ? .9 : .4, x, y) : grama(.4, x, y);   // praça
    else c = (x + y) % 4 === 0 ? '#2a1d18' : telhado(.3 + .5 * h, x, y);                 // telhados com telhas
    P.set(x, y, c);
  }
  return P.done();
}
function ceuTex() {   // céu noturno: gradiente e nuvens baixas
  const P = new PX(128, 64), R = ramp(['#0b1218', '#16242e', '#243a46', '#34505c']);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 128; x++) {
    const nuvem = y > 30 && y < 40 && hash(x >> 3, y >> 2) > .6;
    P.set(x, y, nuvem ? '#3c5a66' : R(y / 64 * .8 + .1, x, y));
  }
  return P.done();
}
function janelasCidadeTex() {   // silhueta da cidade: janelas acesas em grade
  const P = new PX(24, 32);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 24; x++) {
    const jan = x % 4 > 0 && x % 4 < 3 && y % 5 > 0 && y % 5 < 3 && hash(x, y) > .45;
    P.set(x, y, jan ? (hash(y, x) > .5 ? '#ffb35a' : '#ffd27a') : '#141d25');
  }
  return P.done();
}
function fachada(w, h, pal, janelas, portas) {   // fachada de Recife (30 px/m): azulejo no rodapé, platibanda, janelas de madeira, grade de ferro
  const pw = Math.round(w / M), ph = Math.round(h / M), P = new PX(pw, ph), R = ramp(pal);
  for (let y = 0; y < ph; y++) for (let x = 0; x < pw; x++) {
    let c = R(.5 - .25 * x / pw + .08 * hash(x >> 2, y >> 2), x, y);
    if (y < 7) c = R(.9, x, y);                                              // platibanda clara
    if (y >= 7 && y < 9) c = '#2a2420';                                      // friso
    if (y >= ph - 18) c = ((x >> 2) + (y >> 2)) % 2 ? '#2a4fa8' : '#e9e4d6';  // barra de azulejo
    P.set(x, y, c);
  }
  const px = m => Math.round(m / M), py = m => ph - Math.round(m / M);
  for (const [x0, y0, ww, wh] of janelas) {
    const X = px(x0), W = px(ww), Yt = py(y0 + wh), H = px(wh);
    P.rect(X - 1, Yt - 1, W + 2, H + 2, '#2a2420');
    P.rect(X, Yt, W, H, '#0c1214');
    P.rect(X, Yt, 2, H, '#6b4127'); P.rect(X + W - 2, Yt, 2, H, '#6b4127');      // postigos de madeira
    for (let k = 0; k < H; k += 3) P.rect(X, Yt + k, 2, 1, '#4a2c1c');
    P.rect(X + (W >> 1), Yt, 1, H, '#2a2420');
  }
  for (const [x0, y0, ww, wh] of portas) {   // portão de ferro: grade vertical
    const X = px(x0), W = px(ww), Yt = py(y0 + wh), H = px(wh);
    P.rect(X, Yt, W, H, (x) => (x % 3 === 0 ? '#16191e' : '#2f353c'));
    P.rect(X - 1, Yt, 1, H + 1, '#2a2420'); P.rect(X + W, Yt, 1, H + 1, '#2a2420');
  }
  return tex(P.done());
}
function persianaTex() {
  const P = new PX(8, 64);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 8; x++) P.set(x, y, y % 6 < 4 ? '#3a3f46' : '#262a30');
  return tex(P.done());
}
function toldoTex() {
  const P = new PX(16, 4);
  for (let y = 0; y < 4; y++) for (let x = 0; x < 16; x++) P.set(x, y, x % 8 < 4 ? '#a33a2e' : '#e9e4d6');
  return tex(P.done());
}
function tecidoTex(c1, c2) {   // tecido com trama
  const P = new PX(32, 32), R = ramp([c1, c2]);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) P.set(x, y, (x % 4 === 0 || y % 4 === 0) ? c1 : R(.5 + .3 * hash(x >> 1, y >> 1), x, y));
  return P.done();
}
function metalTex() {   // porta de metal com frisos
  const P = new PX(16, 16);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) P.set(x, y, x % 8 === 7 ? '#9aa3aa' : (y < 2 ? '#f2f5f7' : (x % 8 < 2 ? '#e4e9ec' : '#dfe5e9')));
  return P.done();
}
function telaTex() {   // tela da TV: gradiente com reflexo diagonal
  const P = new PX(32, 20), R = ramp(['#1b3a52', '#2f5a78', '#6fa0c0']);
  for (let y = 0; y < 20; y++) for (let x = 0; x < 32; x++) P.set(x, y, (x - y) % 11 === 0 ? '#e8f4fb' : R(y / 20, x, y));
  return P.done();
}
function quadroTex() {   // pintura de paisagem: céu, morros e sol
  const P = new PX(32, 24);
  for (let y = 0; y < 24; y++) for (let x = 0; x < 32; x++) {
    const morro = 14 + 4 * Math.sin(x / 4.5) + 2 * Math.sin(x / 1.7);
    P.set(x, y, y > morro ? (y > morro + 3 ? '#2a5a3a' : '#3f7a4f') : '#9cc9d6');
  }
  P.ellipse(24, 6, 2.5, 2.5, '#f2c14e');
  return P.done();
}
function folhasTex() {   // folhagem de vaso: verde com nervura
  const P = new PX(8, 16), R = ramp(['#1d4a2c', '#2f6e3f', '#4f9a57']);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 8; x++) P.set(x, y, x === 4 ? '#1d4a2c' : R(.4 + .5 * hash(x, y), x, y));
  return P.done();
}
function placaTex(txt) {   // placa de fachada: letras de caixa alta sobre papel (a fonte Cinzel já está carregada pela página)
  const c = document.createElement('canvas'); c.width = 320; c.height = 80;
  const g = c.getContext('2d'); g.fillStyle = '#f2ead8'; g.fillRect(0, 0, 320, 80);
  g.strokeStyle = '#8a5a1c'; g.lineWidth = 4; g.strokeRect(6, 6, 308, 68);
  g.fillStyle = '#2a1d18'; g.font = '34px Cinzel'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(txt, 160, 42);
  return tex(c);
}
function telhadoTex() {   // telhas cerâmicas vistas de cima
  const P = new PX(16, 16), R = ramp(['#4b3428', '#7a4a36', '#9c5f42']);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) P.set(x, y, (x + y) % 4 === 0 ? '#2a1d18' : R(.3 + .5 * hash(x >> 2, y >> 2), x, y));
  return P.done();
}
function palmeiraTex() {   // coqueiro de pixels: tronco curvo e palhas caídas
  const P = new PX(64, 128), tr = ramp(['#2b2420', '#4d3d30', '#7a6048']), fo = ramp(['#143a26', '#24603a', '#3f8a4f', '#7fb86a']);
  for (let y = 56; y < 128; y++) { const x = Math.round(30 + ((y - 56) / 72) ** 2 * 10); for (let k = 0; k < 3; k++) P.set(x + k, y, tr(.3 + .5 * hash(x, y), x, y)); }
  for (const a of [-172, -145, -115, -65, -35, -8, 12]) {
    const r = a * Math.PI / 180;
    for (let s = 0; s <= 30; s++) {
      const x = 33 + Math.cos(r) * s, y = 56 + Math.sin(r) * s * .6 + s * s * .03;
      for (let k = -1; k <= 1; k++) P.set(Math.round(x), Math.round(y) + k, fo(.25 + .6 * (1 - s / 30), x, y));
    }
  }
  return tex(P.done());
}
function bicicletaTex() {   // bicicleta encostada: rodas de anel e quadro vermelho
  const P = new PX(44, 32), cor = '#7a1f2b', roda = '#15181c';
  const anel = (x0, y0, r) => { for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) { const d = Math.hypot(x, y); if (d <= r && d >= r - 1.2) P.set(x0 + x, y0 + y, roda); } };
  anel(9, 23, 7); anel(35, 23, 7);
  P.stroke(9, 23, 17, 13, cor, 1); P.stroke(17, 13, 35, 23, cor, 1); P.stroke(17, 13, 13, 23, cor, 1);
  P.stroke(35, 23, 30, 11, cor, 1); P.stroke(28, 11, 33, 11, '#c6ccd6', 1);
  return tex(P.done());
}
function gatoTex() {   // gato sentado no muro, silhueta com olhos
  const P = new PX(16, 14), c = '#120e12';
  P.ellipse(6, 9, 4.2, 3.2, c); P.ellipse(12, 5, 2.4, 2.4, c);
  P.poly([[10, 3], [10.5, .5], [11.5, 3]], c); P.poly([[12.5, 3], [13.5, .5], [14, 3.2]], c);
  P.stroke(2, 9, 0, 3, c, 1);
  P.set(11, 4, '#f2c14e'); P.set(13, 4, '#f2c14e');
  return tex(P.done());
}

// ——— luzes e personagens ———
function halo(col, size, chao = false) {   // brilho: sprite que olha a câmera, ou plano no chão (reflexo)
  const c = document.createElement('canvas'); c.width = c.height = 64;
  const g = c.getContext('2d'), gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(.25, 'rgba(255,255,255,.4)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
  const mat = { map: tex(c), color: new THREE.Color(col), blending: THREE.AdditiveBlending, depthWrite: false, transparent: true };
  if (chao) { const m = new THREE.Mesh(new THREE.PlaneGeometry(size, size), new THREE.MeshBasicMaterial(mat)); m.rotation.x = -Math.PI / 2; return m; }
  const s = new THREE.Sprite(new THREE.SpriteMaterial(mat)); s.scale.set(size, size, 1); return s;
}
// Prédio em caixa com a fachada na frente (+z); cada janela tem um plano de luz que aparece quando acesa
function predio(grupo, x, z, w, h, d, pal, janelas, portas = []) {
  // Casa inteira: fachada na frente, laterais e fundo com janelas (mesma pintura), cimalha e telhado de duas águas com empena
  const jy = janelas.length ? janelas[0][1] : 1.4, jh = janelas.length ? janelas[0][3] : 1;
  const janLado = []; for (let k = .9; k + .8 < d - .4; k += 2.6) janLado.push([k, jy, .8, jh]);
  const lateral = lamb(fachada(d, h, pal, janLado, [])), fundo = lamb(fachada(w, h, pal, [[w / 2 - .4, jy, .8, jh]], []));
  grupo.add(bloco(w, h, d, x, h / 2, z, [lateral, lateral, cinza(pal[0]), cinza(pal[0]), lamb(fachada(w, h, pal, janelas, portas)), fundo]));   // volume fechado
  const cima = cinza(pal[2]), fz = z + d / 2;
  grupo.add(bloco(w + .3, .3, d + .3, x, h + .15, z, cima));                                         // cimalha
  const rh = Math.min(2.2, d * .32), perfil = new THREE.Shape();                                    // telhado: prisma de duas águas, cumeeira paralela à rua
  perfil.moveTo(-(d / 2 + .25), 0); perfil.lineTo(d / 2 + .25, 0); perfil.lineTo(0, rh); perfil.closePath();
  const tel = new THREE.Mesh(new THREE.ExtrudeGeometry(perfil, { depth: w + .3, bevelEnabled: false }), [cinza(pal[1]), lamb(tex(telhadoTex(), [w / 1.2, d / 1.2]))]);
  tel.rotation.y = Math.PI / 2; tel.position.set(x - (w + .3) / 2, h + .3, z); tel.castShadow = tel.receiveShadow = true; grupo.add(tel);
  for (const [jx, jy, jw, jh] of janelas) {   // peitoril e verga: janela com profundidade
    const cx = x - w / 2 + jx + jw / 2;
    grupo.add(bloco(jw + .2, .08, .2, cx, jy - .04, fz + .08, cima), bloco(jw + .2, .1, .14, cx, jy + jh + .05, fz + .06, cima));
  }
  for (const [px0, py0, pw, ph] of portas) {   // batente com recuo e soleira; a calçada corre na frente
    const cx = x - w / 2 + px0 + pw / 2;
    grupo.add(bloco(pw + .2, ph + .1, .14, cx, py0 + ph / 2, fz + .07, cinza('#2a1d18')), bloco(pw + .7, .12, .7, cx, .06, fz + .35, cinza('#6b6e74')));
  }
  return janelas.map(([jx, jy, jw, jh]) => {
    const g = new THREE.Mesh(new THREE.PlaneGeometry(jw * .8, jh * .8), new THREE.MeshBasicMaterial({ color: '#ffb35a', blending: THREE.AdditiveBlending, transparent: true, depthWrite: false }));
    g.position.set(x - w / 2 + jx + jw / 2, jy + jh / 2, z + d / 2 + .02); g.visible = false; grupo.add(g);
    return g;
  });
}
function luzPoste(grupo, x, z) {   // poste com lâmpada, brilho no alto e reflexo no molhado
  grupo.add(bloco(.1, 3.4, .1, x, 1.7, z, '#1e2428'));
  const luz = new THREE.PointLight('#ffb35a', 14, 11, 2); luz.position.set(x, 3.2, z + .3); grupo.add(luz);
  const h = halo('#ffb35a', 1.6); h.position.set(x, 3.3, z + .3); grupo.add(h);
  const r = halo('#ffb35a', 3.2, true); r.position.set(x, .03, z + 1.4); grupo.add(r);
  return luz;
}

// Personagem: um plano por variante (pose/expressão/passo) e a mesma variante com olhos fechados (sufixo _b)
function ator(who, variantes, x, z) {
  const g = new THREE.Group(), todas = { ...variantes };
  for (const [k, o] of Object.entries(variantes)) todas[k + '_b'] = { ...o, blink: true };
  for (const [chave, o] of Object.entries(todas)) {
    const { c, e } = figure(who, o);
    const m = new THREE.Mesh(new THREE.PlaneGeometry(FW * M, FH * M), new THREE.MeshLambertMaterial({
      map: tex(c), emissiveMap: tex(e), emissive: new THREE.Color(1, 1, 1), alphaTest: .5, side: THREE.DoubleSide,
    }));
    m.geometry.translate(0, FH * M / 2, 0);
    m.castShadow = true;
    m.customDepthMaterial = new THREE.MeshDepthMaterial({ depthPacking: THREE.RGBADepthPacking, map: tex(c), alphaTest: .5 });
    m.name = chave; m.visible = false; g.add(m);
  }
  g.position.set(x, 0, z); g.scale.setScalar(1.25);   // personagens maiores no quadro
  g.userData.var = null;
  return g;
}
export function variar(g, chave) {
  if (g.userData.var === chave) return;
  g.userData.var = chave;
  for (const m of g.children) m.visible = m.name === chave;
}

const clarao = t => { const d = t - 1.1; return d < 0 || d > .9 ? 0 : d < .08 ? d / .08 : Math.exp(-(d - .08) * 6); };   // relâmpago raro

// Cria as cenas dentro de `scene`; devolve os grupos e as funções de estado
export function criar(scene) {
  // ——— RUA: casa da Socorro no meio, vizinhas geminadas, poste e fios, palmeira, bicicleta e gato ———
  const rua = new THREE.Group();
  rua.add(chaoPlano(160, 160, pedraTex(), 3));   // calçadas e o resto do chão: pedra portuguesa
  const asfaltoRua = chaoPlano(80, 6.4, roadTex(), 4); asfaltoRua.position.set(0, .006, 1.2); rua.add(asfaltoRua);   // a rua em frente às casas
  detalheRua(rua, -40, 40, [-2.12, 4.52], 1.2);
  const janCasa = predio(rua, 0, -9.7, 6, 5, 8, TERRA, [[.6, 2.6, .67, 1], [4.0, 2.6, .67, 1]], [[2.35, 0, 1.0, 2.3]]);
  const vizE = predio(rua, -8.5, -9.7, 5, 6.5, 8, OCRE, [[.6, 2.2, .7, 1], [3.4, 2.2, .7, 1], [.6, 4.2, .7, 1], [3.4, 4.2, .7, 1]], [[1.9, 0, 1.2, 2.2]]);
  const vizD = predio(rua, 8.5, -9.7, 5, 7, 8, ROSA, [[.9, 2.4, .8, 1.2], [3.1, 2.4, .8, 1.2], [1.9, 4.6, .9, 1.1]]);
  predio(rua, -9, -20, 5, 6, 5, AZUL, [[.6, 2, .7, 1], [3.4, 2, .7, 1]]);
  predio(rua, 9, -20, 5, 7, 5, OCRE, [[.6, 2.4, .8, 1.2], [3.2, 2.4, .8, 1.2]]);
  const janVizinha = [vizE[1]];
  const luzCasa = new THREE.PointLight('#ffb35a', 0, 8, 2); luzCasa.position.set(1.3, 2.8, -4.4); rua.add(luzCasa);
  const luzCasa2 = new THREE.PointLight('#ffb35a', 0, 6, 2); luzCasa2.position.set(-2.0, 2.8, -4.4); rua.add(luzCasa2);
  luzPoste(rua, -3.6, -2.6); luzPoste(rua, 6.6, -2.6);
  rua.add(bloco(10.2, .03, .03, 1.5, 3.2, -2.6, '#0c0e10'), bloco(10.2, .03, .03, 1.5, 3.0, -2.45, '#0c0e10'));   // fios
  const palmeira = new THREE.Mesh(new THREE.PlaneGeometry(2.1, 4.3), new THREE.MeshLambertMaterial({ map: palmeiraTex(), alphaTest: .5, side: THREE.DoubleSide }));
  palmeira.position.set(5.5, 2.15, -3.2); rua.add(palmeira);
  const bicicleta = new THREE.Mesh(new THREE.PlaneGeometry(1.47, 1.07), new THREE.MeshBasicMaterial({ map: bicicletaTex(), alphaTest: .5, side: THREE.DoubleSide }));
  bicicleta.position.set(-1.9, .53, -4.6); rua.add(bicicleta);
  rua.add(bloco(1.6, .9, .35, -6.6, .45, -5.2, '#4a4d55'));   // muro baixo
  const gato = new THREE.Mesh(new THREE.PlaneGeometry(.53, .47), new THREE.MeshBasicMaterial({ map: gatoTex(), alphaTest: .5, side: THREE.DoubleSide }));
  gato.position.set(-6.6, 1.13, -5.2); rua.add(gato);
  const ceuMat = new THREE.MeshBasicMaterial({ map: tex(ceuTex()), fog: false });
  const hemiRua = new THREE.HemisphereLight('#8fa3ad', '#2d3d42', .9); rua.add(hemiRua);
  const solRua = new THREE.DirectionalLight('#9fb3bd', .6); solRua.position.set(-8, 14, 8); solRua.target.position.set(0, 0, -4);
  rua.add(solRua, solRua.target);

  // ——— CASA EM CORTE: sem parede da frente e sem teto; cômodos mobiliados que acendem em cascata ———
  const int = new THREE.Group();
  int.add(piso(-4.5, -1.2, -3.2, -0.6, carpeteTex()), piso(-1.2, 2.9, -3.2, -0.6, plankTex()), piso(2.9, 4.5, -3.2, -0.6, ladrilhoTex('#e6e2d6', '#9fd0d6')),
    piso(-4.5, -0.2, -0.6, 1.8, plankTex()), piso(-0.2, 4.5, -0.6, 1.8, ladrilhoTex('#e2d6c2', '#8a6a4a')));
  const parede = lamb(tex(wallTex())), azul = lamb(tex(azulejoTex()));
  int.add(bloco(3.3, 2.6, .3, -2.85, 1.3, -3.15, parede), bloco(4.1, 2.6, .3, .85, 1.3, -3.15, parede), bloco(1.6, 2.6, .3, 3.7, 1.3, -3.15, azul));   // fundo
  int.add(bloco(.3, 2.6, 5.0, -4.6, 1.3, -0.7, parede), bloco(.3, 2.6, 5.0, 4.6, 1.3, -0.7, parede));   // laterais
  int.add(bloco(.2, 2.6, 2.6, -1.2, 1.3, -1.9, parede), bloco(.2, 1.5, 2.6, 2.9, .75, -1.9, azul));       // divisórias (a do banheiro é baixa: o quadro de luz aparece)
  int.add(bloco(3.3, .5, .2, -2.85, .25, -0.6, parede), bloco(4.7, .5, .2, 2.15, .25, -0.6, parede));     // muretas da frente (0,5 m: a hall fica à vista)
  // quarto
  int.add(bloco(1.4, .4, 1.8, -2.6, .2, -2.1, '#4a2e22'), bloco(1.36, .2, 1.75, -2.6, .5, -2.1, '#e6e0d6'), bloco(1.38, .05, 1.0, -2.6, .63, -1.8, lamb(tex(tecidoTex('#8a2d3a', '#a33a48')))),
    bloco(1.4, .9, .1, -2.6, .45, -3.02, '#5a3620'), bloco(.5, .12, .3, -2.6, .66, -2.7, '#f2efe9'), bloco(.6, 2.1, 1.2, -4.2, 1.05, -2.0, lamb(tex(plankTex()))), bloco(.02, 2.0, .02, -3.88, 1.05, -2.0, '#2a1a10'), bloco(.03, .12, .03, -3.85, 1.0, -1.9, '#c6ccd6'),
    bloco(.4, .5, .4, -1.6, .25, -2.9, '#4a2e22'));
  const abajur = new THREE.Mesh(new THREE.CylinderGeometry(.14, .1, .22, 8), new THREE.MeshBasicMaterial({ color: '#ffd9a0' })); abajur.position.set(-1.6, .62, -2.9); int.add(abajur);
  // corredor: console com telefone, quadro e o quadro de luz (disjuntor)
  int.add(bloco(.8, .7, .4, 0, .35, -2.85, '#5a3620'), bloco(.25, .1, .2, 0, .75, -2.85, '#1b1f24'));
  int.add(bloco(.8, .6, .04, 1.0, 1.7, -3.0, '#c9a36b'));
  const quadro = new THREE.Mesh(new THREE.PlaneGeometry(.66, .46), new THREE.MeshBasicMaterial({ map: tex(quadroTex()) })); quadro.position.set(1.0, 1.7, -2.97); int.add(quadro);
  int.add(bloco(.62, .74, .12, 2.6, 1.5, -3.08, '#c9ced4'), bloco(.5, .05, .02, 2.6, 1.82, -3.01, '#eef2f5'));   // quadro de luz
  for (const dx of [-.2, -.1, 0, .1]) int.add(bloco(.07, .18, .04, 2.6 + dx, 1.62, -3.0, '#2b2f33'), bloco(.03, .07, .03, 2.6 + dx, 1.66, -2.97, '#e8ecef'));   // disjuntores ligados
  int.add(bloco(.09, .26, .05, 2.79, 1.5, -2.99, '#1b1f22'));   // o do chuveiro (alavanca vermelha em cima dele)
  const alavanca = new THREE.Group(); alavanca.position.set(2.79, 1.42, -2.95); alavanca.add(bloco(.07, .22, .05, 0, .11, 0, '#d32f22')); int.add(alavanca);   // disjuntor vermelho (sobe ao religar)
  // banheiro: chuveiro elétrico (caixa branca, fio queimado e faíscas), toalha no varão, pia
  int.add(bloco(.05, .5, .05, 4.3, 2.65, -3.1, '#b8c0c6'), bloco(.05, .05, .32, 4.3, 2.4, -2.95, '#b8c0c6'));   // cano saindo da parede
  int.add(bloco(.34, .26, .3, 4.3, 2.26, -2.8, '#f7f9fa'), bloco(.12, .04, .04, 4.3, 2.3, -2.64, '#c8ced2'));   // corpo do chuveiro e a chave de temperatura
  const crivo = new THREE.Mesh(new THREE.CylinderGeometry(.2, .16, .07, 16), cinza('#e9edef')); crivo.position.set(4.3, 2.1, -2.8); int.add(crivo);
  for (let i = 0; i < 6; i++) { const a = i / 6 * Math.PI * 2; int.add(bloco(.02, .01, .02, 4.3 + Math.cos(a) * .1, 2.062, -2.8 + Math.sin(a) * .1, '#7a8288')); }   // furos do crivo
  int.add(bloco(.5, .8, .04, 3.5, 1.2, -3.0, '#eee3d0'));
  int.add(bloco(.035, .42, .035, 4.43, 2.0, -2.72, '#101010'), bloco(.05, .06, .05, 4.43, 1.78, -2.72, '#3a1a0a'), bloco(.07, .02, .07, 4.43, 1.75, -2.72, '#8a4a20'));   // fio preto, ponta queimada
  const centelhas = [0, 1, 2].map(i => { const s = halo(i ? '#ffe9a8' : '#ffd27a', .45 - i * .1); s.position.set(4.43 + (i - 1) * .05, 1.76 + i * .05, -2.66); s.visible = false; int.add(s); return s; });
  const faisca = new THREE.PointLight('#ffd27a', 0, 3.5, 2); faisca.position.set(4.35, 1.9, -2.4); int.add(faisca);
  const lanternaCarlos = new THREE.PointLight('#e6eef5', 0, 6, 2); int.add(lanternaCarlos);   // lanterna do Carlos no conserto (S12): main.js põe perto da câmera   // a faísca ilumina o chuveiro
  int.add(bloco(.6, .85, .4, 3.9, .43, -0.9, '#d6dce0'));
  // sala: sofá, tapete, TV, estante, quadro, planta
  const tec = lamb(tex(tecidoTex('#6e3d2e', '#86492f'))), braco = lamb(tex(tecidoTex('#5a301f', '#6a3a26')));
  int.add(bloco(2.2, .45, .9, -2.6, .22, 1.2, tec), bloco(2.2, .5, .25, -2.6, .7, 1.6, tec), bloco(.2, .35, .9, -3.7, .5, 1.2, braco), bloco(.2, .35, .9, -1.5, .5, 1.2, braco));
  int.add(bloco(.9, .2, .7, -3.1, .58, 1.2, tec), bloco(.9, .2, .7, -2.1, .58, 1.2, tec));   // almofadas
  int.add(bloco(2.6, .02, 1.6, -2.6, .01, .8, '#7a2e33'));
  int.add(bloco(1.3, .75, .08, -2.6, 1.1, -.35, '#101216'), bloco(.6, .3, .5, -2.6, .15, -.3, '#101216'));
  const tela = new THREE.Mesh(new THREE.PlaneGeometry(1.2, .65), new THREE.MeshBasicMaterial({ map: tex(telaTex()) })); tela.position.set(-2.6, 1.1, -.3); int.add(tela);
  int.add(bloco(.4, 2.0, 1.4, -4.2, 1.0, .3, '#5a3620'), bloco(.3, .3, .2, -4.2, .6, 0, '#3f6a7e'), bloco(.3, .25, .2, -4.2, 1.25, .2, '#c9a36b'));
  int.add(bloco(.05, .9, 1.1, -4.45, 1.6, .6, '#c9a36b'));
  int.add(bloco(.4, .45, .4, -.6, .225, 1.5, lamb(tex(ladrilhoTex('#b5603f', '#8a4a30')))));   // vaso
  const verde = lamb(tex(folhasTex()));
  for (const [dx, dz, ry, rx] of [[0, 0, 0, .5], [.1, .05, 1.6, -.4], [-.1, -.05, 3.2, .5], [.05, -.1, 4.7, -.4], [-.05, .1, 2.4, .2]]) {
    const f = bloco(.12, .55, .03, -.6 + dx, .72, 1.5 + dz, verde); f.rotation.set(rx, ry, 0); int.add(f);   // folhas
  }
  // cozinha: balcão com fogão, geladeira, mesa e cadeiras
  int.add(bloco(1.2, .9, .6, .6, .45, -.3, '#8b5a3a'), bloco(1.25, .04, .62, .6, .92, -.3, '#2e2a26'));
  int.add(bloco(1.0, .9, .6, 2.2, .45, -.3, lamb(tex(metalTex()))), bloco(.8, 2.0, .8, 0.0, 1.0, 1.5, lamb(tex(metalTex()))));   // geladeira junto da sala (não tapa o banheiro nem o quadro de luz)
  for (const [bx, bz] of [[1.9, -.45], [2.5, -.45], [1.9, -.15], [2.5, -.15]]) int.add(bloco(.2, .02, .2, bx, .93, bz, '#111316'));   // bocas do fogão
  int.add(bloco(.04, .5, .04, .3, 1.2, 1.93, '#7d8790'), bloco(.1, .12, .02, -.2, 1.7, 1.92, '#d9534f'), bloco(.1, .1, .02, .15, 1.45, 1.92, '#f2c14e'));   // puxador e ímãs
  const panela = new THREE.Mesh(new THREE.CylinderGeometry(.14, .14, .12, 10), cinza('#9aa3ab')); panela.position.set(2.0, .96, -.3); int.add(panela);
  int.add(bloco(1.4, .75, .9, 3.4, .375, 1.1, '#6b4127'), bloco(.4, .45, .4, .75, .22, 1.1, '#4a2c1c'), bloco(.4, .45, .4, 2.45, .22, 1.1, '#4a2c1c'));
  int.add(bloco(1.3, .02, .8, 3.4, .77, 1.1, '#e9e4d6'), bloco(.4, .5, .04, .75, .8, .9, '#4a2c1c'), bloco(.4, .5, .04, 2.45, .8, .9, '#4a2c1c'));   // toalha de mesa, encostos
  // portas dos armários da cozinha e puxadores
  int.add(bloco(.02, .7, .02, .3, .45, .03, '#3a2418'), bloco(.02, .7, .02, .9, .45, .03, '#3a2418'), bloco(.02, .7, .02, 1.5, .45, .03, '#3a2418'), bloco(.12, .02, .02, .6, .75, .04, '#c6ccd6'));
  // vapor do chuveiro (sprites que sobem)
  const vapor = [0, 1, 2].map(() => { const s = halo('#e4eef2', 1.7); int.add(s); return s; });   // sai do chuveiro
  // luzes dos cômodos: um ponto de luz e um brilho no teto por cômodo
  const luzes = [['banheiro', 3.7, -1.9, '#ffc27a'], ['cozinha', 2.15, .6, '#ffb878'], ['sala', -2.6, .5, '#ffb35a'], ['quarto', -2.6, -1.9, '#ffb878'], ['corredor', .85, -1.9, '#ffb35a']]
    .map(([nome, x, z, cor]) => {
      const l = new THREE.PointLight(cor, 0, 7, 2); l.position.set(x, 2.2, z); l.castShadow = nome === 'sala'; int.add(l);
      const h = halo(cor, .9); h.position.set(x, 2.45, z); h.visible = false; int.add(h);
      return { nome, l, h };
    });
  const lanterna = new THREE.PointLight('#ffb35a', 0, 5, 2); lanterna.position.set(.1, 1.3, -1.2); int.add(lanterna);
  const lanternaHalo = halo('#ffb35a', .6); lanternaHalo.position.set(.1, 1.3, -1.2); int.add(lanternaHalo);
  // teto translúcido: a casa em corte mostra o telhado por fora sem esconder o que está dentro
  const teto = new THREE.Mesh(new THREE.BoxGeometry(9.6, .25, 5.3), new THREE.MeshLambertMaterial({ color: '#4a3a30', transparent: true, opacity: .2, depthWrite: false }));
  teto.position.set(0, 2.75, -.7); int.add(teto);
  const luzTelefone = new THREE.PointLight('#9fd6ff', 0, 6, 2); luzTelefone.position.set(0, 1.2, -2.4); int.add(luzTelefone);
  const solInt = new THREE.DirectionalLight('#8fa3ad', .05); solInt.position.set(-6, 9, 6); solInt.target.position.set(0, 0, -1);
  int.add(new THREE.HemisphereLight('#8fa3ad', '#2d3d42', .45), solInt, solInt.target);
  const preench = new THREE.PointLight('#c9d6dd', 12, 8, 2); preench.position.set(.6, 1.7, 2.6); int.add(preench);
  const socorro = ator('socorro', { parada_preocupada: { view: 'front', pose: 'towel', expr: 'worry' }, aliviada: { view: 'front', pose: 'hands', expr: 'relief' }, toalha: { view: 'front', pose: 'towel', expr: 'relief' }, gesto: { view: 'front', pose: 'gesto', expr: 'worry' } }, -.2, -1.6);   // dentro do corredor (a divisória fica em x -1,2)
  const carlosInt = ator('carlos', { parado: { view: 'front', pose: 'stand' }, celular: { view: 'front', pose: 'phone' }, disjuntor: { view: 'front', pose: 'breaker' } }, 1.2, -.9);
  int.add(socorro, carlosInt);

  // ——— MAPA: de cima, quarteirões, praças, rio e ponte; rota tracejada da casa até a base ———
  const mapa = new THREE.Group();
  mapa.add(chaoPlano(40, 40, mapaTex(), 40));
  // rota do Carlos: sai da porta dele, só pelas ruas (dobra nas esquinas), cruza o rio só pela ponte e para na porta da dona Socorro
  const rota = [[-10.4, 4.6], [-7.4, 4.6], [-7.4, -1.4], [-7.4, -7.4], [-1.4, -7.4], [4.6, -7.4], [10.6, -7.4], [16.6, -7.4], [16.6, -1.4], [17.1, 1.6]];
  const pontos = [];
  for (let i = 0; i < rota.length - 1; i++) {
    const [ax, az] = rota[i], [bx, bz] = rota[i + 1], n = Math.hypot(bx - ax, bz - az) / .5;
    for (let k = 0; k <= n; k++) pontos.push([ax + (bx - ax) * k / n, az + (bz - az) * k / n]);
  }
  const telhadoMat = new THREE.MeshLambertMaterial({ map: tex(telhadoTex()) }), paredeMat = cinza('#5a3a2e');
  // quarteirões: prédios fechados dentro do bloco (centro -16,4 + 6 m), fora das praças; a casa do Carlos fica no bloco (1,4)
  for (let k = 0; k < 4; k++) for (let l = 0; l < 6; l++) {
    if ((k === 1 && l === 4) || hash(k, l) > .8) continue;
    const h = 1 + 1.6 * hash(k + 7, l + 3);
    mapa.add(bloco(3.6, h, 3.6, -16.4 + 6 * k, h / 2, -16.4 + 6 * l, [paredeMat, paredeMat, telhadoMat, paredeMat, paredeMat, paredeMat]));
  }
  // casas da rota: a do Carlos (porta voltada para a rua Z = 4,6) e a da dona Socorro (porta voltada para a rua X = 16,6)
  mapa.add(bloco(2.4, 1.2, 2.4, -10.4, .6, 7.6, cinza('#c9a36b')), bloco(2.6, .25, 2.6, -10.4, 1.32, 7.6, telhadoMat));
  mapa.add(bloco(2.4, 1.4, 2.4, 18.4, .7, 1.6, cinza('#9c5f42')), bloco(2.6, .25, 2.6, 18.4, 1.52, 1.6, telhadoMat));
  const portaLuz = new THREE.Mesh(new THREE.PlaneGeometry(.9, 1.6), new THREE.MeshBasicMaterial({ color: '#ffc27a' }));   // porta da dona Socorro acende ao chegar
  portaLuz.rotation.y = -Math.PI / 2; portaLuz.position.set(17.15, .8, 1.6); portaLuz.visible = false; mapa.add(portaLuz);
  const dashes = pontos.map(([x, z]) => { const d = new THREE.Mesh(new THREE.PlaneGeometry(.22, .22), new THREE.MeshBasicMaterial({ color: new THREE.Color('#ffd27a').multiplyScalar(1.2) })); d.rotation.x = -Math.PI / 2; d.position.set(x, .05, z); return d; });
  dashes.forEach(d => mapa.add(d));
  const marcador = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 2.4), new THREE.MeshBasicMaterial({ map: tex(marcadorTex()), transparent: true, alphaTest: .5 }));
  marcador.rotation.x = -Math.PI / 2; marcador.position.y = .08; mapa.add(marcador);
  mapa.add(new THREE.HemisphereLight('#b8c9d2', '#1d2a24', 1.1));
  const solMapa = new THREE.DirectionalLight('#dfe6ea', .8); solMapa.position.set(-4, 20, 6); solMapa.target.position.set(0, 0, 0); mapa.add(solMapa, solMapa.target);

  // ——— CAMINHO: calçadas de pedra, rio com ponte (a pista atravessa), lojas e toldos, Casa Amarela ———
  const caminho = new THREE.Group(), lojasPingo = [];
  // Rio Capibaribe com 10 m de largura (x de -1,2 a 9,2), água 1 m abaixo da rua, muros de cais nas margens
  const RIO0 = -1.2, RIO1 = 9.2, RIOC = (RIO0 + RIO1) / 2, RIOW = RIO1 - RIO0, AGUA = -1.0;
  for (const zc of [-5.15, 5.15]) {   // calçadas de pedra portuguesa, cortadas pelo rio
    for (const [a, b] of [[-30, RIO0], [RIO1, 30]]) { const m = chaoPlano(b - a, 5.7, pedraTex(), 3); m.position.set((a + b) / 2, 0, zc); caminho.add(m); }
  }
  const rio = new THREE.Mesh(new THREE.PlaneGeometry(RIOW, 40), lamb(tex(aguaTex(), [3, 6])));
  rio.rotation.x = -Math.PI / 2; rio.position.set(RIOC, AGUA, 0); caminho.add(rio);
  for (const x of [RIO0 - .15, RIO1 + .15]) caminho.add(bloco(.3, -AGUA + .05, 40, x, AGUA / 2, 0, '#5e5a52'));   // muros do cais
  for (const [a, b] of [[-30, RIO0], [RIO1, 30]]) {   // a pista sobre a terra, com meio-fio, faixa e bueiros
    const p = chaoPlano(b - a, 4.6, roadTex(), 4); p.position.set((a + b) / 2, .01, 0); caminho.add(p);
    detalheRua(caminho, a, b, [-2.33, 2.33], 0);
  }
  // Ponte: laje grossa por cima do rio, com asfalto e faixa, e uma parede lateral de pedra com três arcos de cada lado
  caminho.add(bloco(RIOW + .4, .45, 4.8, RIOC, -.215, 0, '#6f665a'));
  const pistaPonte = chaoPlano(RIOW + .4, 4.4, roadTex(), 4); pistaPonte.position.set(RIOC, .012, 0); caminho.add(pistaPonte);
  for (let x = RIO0 + .6; x < RIO1 - .4; x += 2.2) caminho.add(bloco(1.1, .012, .12, x, .02, 0, '#c9a94a'));
  const paredePonte = () => {
    const s = new THREE.Shape(); s.moveTo(RIO0 - .2, AGUA - .05); s.lineTo(RIO1 + .2, AGUA - .05); s.lineTo(RIO1 + .2, 0); s.lineTo(RIO0 - .2, 0); s.closePath();
    for (const cx of [RIOC - 3.4, RIOC, RIOC + 3.4]) {   // vãos em arco (sólidos em volta, abertos por baixo)
      const v = new THREE.Path(); v.moveTo(cx - .9, AGUA); v.absarc(cx, AGUA, .9, Math.PI, 0, true); v.lineTo(cx - .9, AGUA); s.holes.push(v);
    }
    return new THREE.ExtrudeGeometry(s, { depth: .5, bevelEnabled: false, steps: 1 });
  };
  for (const z of [-2.4, 1.9]) { const m = new THREE.Mesh(paredePonte(), lamb(tex(tijoloTex(), [3, 1]))); m.position.z = z; m.castShadow = m.receiveShadow = true; caminho.add(m); }
  for (const z of [-2.35, 2.35]) {   // guarda-corpo, postes da ponte e o reflexo deles na água
    caminho.add(bloco(RIOW + .4, .55, .14, RIOC, .28, z, '#3a3530'));
    for (const x of [RIO0 + .4, RIOC, RIO1 - .4]) {
      caminho.add(bloco(.1, 1.6, .1, x, .8, z, '#1e2428')); const l = halo('#ffb35a', 1.0); l.position.set(x, 1.65, z); caminho.add(l);
      const r = halo('#ffb35a', 1.4, true); r.position.set(x, AGUA + .02, z + Math.sign(z) * 1.6); caminho.add(r);
    }
  }
  const LOJAS = [{ x: -10, w: 5, h: 4.2, pal: TERRA, vit: true }, { x: -4.4, w: 3.6, h: 3.8, pal: AZUL, fechada: true }, { x: 13.2, w: 4.6, h: 4.6, pal: AMARELA, casa: true }];
  for (const L of LOJAS) {
    const z = -9.0, vidros = L.casa ? predio(caminho, L.x, z, L.w, L.h, 6, L.pal, [[.5, 2.4, 1.1, 1.0], [2.8, 2.4, 1.1, 1.0]], [[1.7, 0, 1.0, 2.2]])
      : predio(caminho, L.x, z, L.w, L.h, 6, L.pal, [[.4, .5, L.w - .8, 1.9]]);
    vidros.forEach(g => { g.visible = !!L.vit; });
    if (L.casa) { const pl = new THREE.Mesh(new THREE.PlaneGeometry(3.2, .8), new THREE.MeshBasicMaterial({ map: placaTex('CASA AMARELA') })); pl.position.set(L.x, 3.8, -6.0 + .06); caminho.add(pl); }   // placa legível
    if (L.fechada) {   // bar fechando: persiana de ferro com luz por baixo
      const p = new THREE.Mesh(new THREE.PlaneGeometry(L.w - .3, 2.4), lamb(persianaTex())); p.position.set(L.x, 1.3, -6.0 + .03); caminho.add(p);
      const fresta = new THREE.Mesh(new THREE.PlaneGeometry(L.w - .4, .1), new THREE.MeshBasicMaterial({ color: '#ffb35a' })); fresta.position.set(L.x, .06, -6.0 + .05); caminho.add(fresta);
    }
    if (!L.casa) {
      const toldo = new THREE.Mesh(new THREE.BoxGeometry(L.w + .2, .08, 1.3), lamb(toldoTex()));
      toldo.material.map.repeat.set((L.w + .2) / .5, 1); toldo.position.set(L.x, 2.5, -5.45); toldo.rotation.x = .18; caminho.add(toldo);
      lojasPingo.push(L);
    }
  }
  [-5, -2.2, 11.4, 15.6].forEach(x => luzPoste(caminho, x, -2.6));   // nenhum poste sobre a água
  for (const x of [RIO0 + .4, RIOC, RIO1 - .4]) { const l = new THREE.PointLight('#ffb35a', 7, 9, 2); l.position.set(x, 1.9, 0); caminho.add(l); }   // luz dos postes da ponte na laje e na água
  const hemiCam = new THREE.HemisphereLight('#7f97a5', '#28363c', .8); caminho.add(hemiCam);
  const preenchCam = new THREE.PointLight('#c9d6dd', 9, 9, 2); preenchCam.position.set(-6, 1.6, 3.2); caminho.add(preenchCam);   // luz de frente para o Carlos (não fica silhueta)
  const carlosCam = ator('carlos', { andando0: { view: '34', pose: 'stand', walk: 0 }, andando1: { view: '34', pose: 'stand', walk: 1 }, andando2: { view: '34', pose: 'stand', walk: 2 }, andando3: { view: '34', pose: 'stand', walk: 3 }, parado: { view: '34', pose: 'stand' } }, -6, .6);
  caminho.add(carlosCam);

  // ——— CIDADE AO FUNDO: céu, silhueta com janelas acesas ———
  const fundo = new THREE.Group();
  const ceu = new THREE.Mesh(new THREE.PlaneGeometry(400, 200), ceuMat); ceu.position.set(0, 60, -150); fundo.add(ceu);
  const janTex = tex(janelasCidadeTex());
  for (let i = 0; i < 30; i++) {
    const w = 5 + hash(i, 5) * 5, h = 12 + hash(i, 6) * 22, x = -80 + i * 5.6 + hash(i, 7) * 3, z = -24 - hash(i, 8) * 9;
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, 4), new THREE.MeshBasicMaterial({ map: janTex }));
    m.position.set(x, h / 2, z); fundo.add(m);
  }

  // ——— CHUVA: traços em loop (perto e longe), respingos no chão e pingos dos toldos ———
  const N = 900, pos = new Float32Array(N * 6), g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const chuva = new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: '#b8c9d2', transparent: true, opacity: .28, depthWrite: false }));
  const semente = Array.from({ length: N }, (_, i) => [hash(i, 1), hash(i, 2), hash(i, 3)]);
  const RS = 120, rPos = new Float32Array(RS * 3), rg = new THREE.BufferGeometry();
  rg.setAttribute('position', new THREE.BufferAttribute(rPos, 3));
  const respingos = new THREE.Points(rg, new THREE.PointsMaterial({ color: '#dfe9ee', size: 2, sizeAttenuation: false, transparent: true, opacity: .7, depthWrite: false }));
  const PN = 36, pPos = new Float32Array(PN * 3), pg = new THREE.BufferGeometry();
  pg.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
  const pingos = new THREE.Points(pg, new THREE.PointsMaterial({ color: '#b9d7e6', size: 2, sizeAttenuation: false, transparent: true, opacity: .7, depthWrite: false }));
  const pingoX = Array.from({ length: PN }, (_, i) => { const L = lojasPingo[i % lojasPingo.length]; return [L.x + (hash(i, 12) - .5) * (L.w - .4), -4.7]; });
  scene.add(rua, int, mapa, caminho, fundo, chuva, respingos, pingos);
  for (const grupo of [rua, int, mapa, caminho]) grupo.visible = false;

  // ——— funções de estado ———
  const grupos = { rua, int, mapa, caminho };
  // Mostra só um grupo; chuva nos ambientes externos, cidade ao fundo na rua e no caminho
  function mostrar(nome) {
    for (const [k, grupo] of Object.entries(grupos)) grupo.visible = k === nome;
    chuva.visible = nome === 'rua' || nome === 'mapa' || nome === 'caminho';
    respingos.visible = nome === 'rua' || nome === 'caminho';
    pingos.visible = nome === 'caminho';
    fundo.visible = nome === 'rua' || nome === 'caminho';
    if (nome === 'int') { scene.background = new THREE.Color('#0b0d10'); scene.fog = new THREE.Fog('#0b0d10', 14, 42); }
    else if (nome === 'mapa') { scene.background = new THREE.Color('#0f1714'); scene.fog = new THREE.Fog('#16201c', 30, 80); }
    else if (nome === 'nada') { scene.background = new THREE.Color('#0b0d10'); scene.fog = null; }
    else { scene.background = new THREE.Color('#0f171d'); scene.fog = new THREE.Fog('#26363f', 46, 130); }
  }
  // Interior: cada cômodo acende CAS[nome] s depois de `virada` (com uma batida); antes, só a lanterna da dona Socorro
  function estadoInt(t, virada, emTelefone) {
    for (const { nome, l, h } of luzes) {
      const dt = t - virada - CAS[nome], k = dt <= 0 ? 0 : Math.min(1, dt / .12);
      l.intensity = k ? 12 * k * (1 + .35 * Math.exp(-Math.max(0, dt) * 14)) : 0;
      h.visible = k > 0; h.material.opacity = k;
    }
    const antes = t < virada;
    lanterna.intensity = antes ? 2.4 + .3 * Math.sin(t * 13) + .2 * Math.sin(t * 31) : 0;
    lanternaHalo.visible = antes;
    luzTelefone.intensity = emTelefone ? 2.4 : 0;
    solInt.intensity = antes ? .05 : .25;
    const religa = virada - 3.2;   // o disjuntor volta ~3,2 s antes da luz dos cômodos (S12 → S14)
    alavanca.rotation.z = -.9 * (1 - Math.min(1, Math.max(0, (t - religa) / .3)));   // sobe em 0,3 s
    centelhas.forEach((s, i) => { s.visible = t >= religa - 2.2 && t < religa && Math.sin(t * 40 + i * 2) > -.2; });
    faisca.intensity = centelhas[0].visible ? 1.6 : 0;
    vapor.forEach((s, i) => {
      const f = (t * .22 + i / 3) % 1;
      s.position.set(4.3 - f * .3, 1.9 + f * 1.1, -2.9 + f * .8); s.scale.setScalar(.5 + f * .7);
      s.material.opacity = (1 - f) * .45 * (t >= virada ? 1 : 0);
    });
  }
  // Rua: janelas da casa acesas (S01 e luz_janela); o vizinho acende uma janela depois; relâmpago raro
  function estadoRua(t, acesa) {
    for (const g of janCasa) g.visible = acesa;
    for (const g of janVizinha) g.visible = acesa && t >= 53;
    luzCasa.intensity = acesa ? 7 : 0; luzCasa2.intensity = acesa ? 3 : 0;
    const k = clarao(t);
    ceuMat.color.setScalar(1 + 2 * k); hemiRua.intensity = .9 + 1.8 * k; solRua.intensity = .6 + .9 * k;
  }
  // Chuva: traços em loop, deterministas; respingos piscam no chão; pingos caem dos toldos
  function atualizarChuva(t) {
    for (let i = 0; i < N; i++) {
      const [a, b, c] = semente[i], x = (a - .5) * 24, z = (b - .5) * 22;
      const yy = ((c * 9 - t * 9) % 9 + 9) % 9;
      pos.set([x, yy + .5, z, x + .12, yy, z], i * 6);
    }
    g.attributes.position.needsUpdate = true;
    for (let i = 0; i < RS; i++) {
      const fase = (t * 3.1 + hash(i, 9)) % 1, vis = fase < .35;
      rPos.set([(hash(i, 7) - .5) * 22, vis ? .02 : -5, (hash(i, 8) - .5) * 14 - 1], i * 3);
    }
    rg.attributes.position.needsUpdate = true;
    for (let i = 0; i < PN; i++) {
      const f = (t * 1.5 + hash(i, 11)) % 1, [x, z] = pingoX[i];
      pPos.set([x, 2.45 - f * 2.3, z], i * 3);
    }
    pg.attributes.position.needsUpdate = true;
  }
  // Mapa: marcador anda pela rota (p de 0 a 1)
  function estadoMapa(p) {
    let tot = 0; const seg = [];
    for (let i = 0; i < pontos.length - 1; i++) { const d = Math.hypot(pontos[i + 1][0] - pontos[i][0], pontos[i + 1][1] - pontos[i][1]); seg.push(d); tot += d; }
    let alvo = Math.min(1, Math.max(0, p)) * tot, i = 0;
    while (i < seg.length - 1 && alvo > seg[i]) { alvo -= seg[i]; i++; }
    const f = seg[i] ? alvo / seg[i] : 0;
    const [ax, az] = pontos[i], [bx, bz] = pontos[i + 1];
    marcador.position.set(ax + (bx - ax) * f, .08, az + (bz - az) * f);
    portaLuz.visible = p >= .999;   // chegou à porta da dona Socorro
  }
  // Caminho: Carlos anda de x=-8 até x=13 (passo a passo); depois para no portão
  function estadoCaminho(p, parado) {
    const x = -8 + 21 * Math.min(1, Math.max(0, p));   // atravessa a ponte inteira (rio de -1,2 a 9,2) e chega à Casa Amarela
    carlosCam.position.x = x; preenchCam.position.x = x + .3;
    if (parado) variar(carlosCam, 'parado');
    else variar(carlosCam, 'andando' + (Math.floor(p * 20) % 4));
  }
  function giraParaCamera(cam) {
    for (const a of [socorro, carlosInt, carlosCam, palmeira, bicicleta, gato]) a.rotation.y = Math.atan2(cam.position.x - a.position.x, cam.position.z - a.position.z);
  }
  return {
    grupos, mostrar, estadoInt, estadoRua, atualizarChuva, estadoMapa, estadoCaminho, giraParaCamera,
    socorro, carlosInt, carlosCam, marcador, lanternaCarlos,
  };
}
function marcadorTex() {   // marcador do mapa visto de cima: Carlos com capa azul, cabeça, boné e caixa de ferramentas
  const P = new PX(16, 16);
  P.ellipse(8, 8, 7.5, 7.5, '#f2f2f2'); P.ellipse(8, 8, 5.5, 5.5, '#2b59ff');
  P.ellipse(8, 7, 2, 2, '#c48c68'); P.rect(6, 5, 4, 1, '#14141e');
  P.rect(10, 10, 3, 2, '#8a909c');
  return P.done();
}
