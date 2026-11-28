// Página do filme (contrato da biblioteca: window.READY, window.render(t), window.DUR).
// Linha do tempo em plano.json (16:9 ou 9:16 pelo tamanho da janela); falas em lines.json.
// ?look=sheet → folha de personagens (prova do visual).
import * as THREE from 'three';
import { makePost } from './post.js';
import { drawSheet } from './chars.js';
import { criar, variar } from './set.js';

const Q = new URLSearchParams(location.search);
const stage = document.getElementById('stage');
if (Q.get('look') === 'sheet') {
  const cv = document.createElement('canvas'); cv.width = 1920; cv.height = 1080; stage.appendChild(cv);
  drawSheet(cv.getContext('2d'));
  window.DUR = 1; window.render = () => {}; window.READY = true;
} else {
  await main();
}

async function main() {
  const W = innerWidth, H = innerHeight, RET = H > W ? '9x16' : '16x9', PORT = RET === '9x16';
  const [plano, linhas] = await Promise.all([
    fetch('plano.json').then(r => r.json()), fetch('lines.json').then(r => r.json()),
  ]);
  const F = plano.formatos[RET], DUR = F.dur;
  window.DUR = DUR;
  await Promise.all([document.fonts.load('40px Cinzel'), document.fonts.load('italic 40px "Cormorant Garamond"'), document.fonts.load('40px "Cormorant Garamond"')]);

  // ——— 3D ———
  const renderer = new THREE.WebGLRenderer({ antialias: false, preserveDrawingBuffer: true });
  const RS = 2;   // pixel interno = metade da tela, ampliado por vizinho mais próximo (pixel nítido)
  renderer.setPixelRatio(1); renderer.setSize(W / RS, H / RS, false);
  renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.15;
  renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.domElement.style.width = W + 'px'; renderer.domElement.style.height = H + 'px'; renderer.domElement.style.imageRendering = 'pixelated';
  stage.appendChild(renderer.domElement);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(32, W / H, .3, 200);
  const S = criar(scene);
  const post = makePost(renderer, scene, camera, W / RS, H / RS, { ssaa: 1 });
  post.bloom.strength = .32; post.bloom.radius = .45; post.bloom.threshold = .8;
  const V = post.vig.uniforms; V.amt.value = .45; V.warm.value = .1; V.contrast.value = .15; V.sat.value = 1.1;

  // ——— sobreposição 2D (textos, caixa de diálogo, cartões, Pix, legendas) ———
  const ov = document.createElement('canvas'); ov.width = W; ov.height = H;
  ov.style.cssText = `position:absolute;left:0;top:0;width:${W}px;height:${H}px`;
  stage.appendChild(ov);
  const g = ov.getContext('2d');
  const PAPEL = '#f2ead8', OURO = '#d8b26a', VERDE = '#35d08f', AZUL = '#2b59ff';
  const sc = PORT ? W / 1080 : W / 1920;   // escala dos textos pela largura

  // Câmera de cada plano: [pos0, pos1, alvo0, alvo1] no mundo da cena 16:9
  const CAM = {
    alto_empurra: [[0, 21, 16], [0, 17.5, 11.5], [0, 2, -5.5], [0, 2, -5.5]],
    alto_volta: [[0, 14, 9], [0, 21, 16], [0, 2, -5.5], [0, 2, -5.5]],
    insert_janela: [[0.2, 3.6, -1.2], [0.2, 3.3, -1.8], [1.33, 3, -5.7], [1.33, 3, -5.7]],
    medio_porta: [[4.0, 2.4, 4.6], [3.6, 2.2, 4.2], [0.6, 1.3, -2], [0.6, 1.3, -2]],
    medio_34_dois: [[4.0, 2.5, 4.8], [3.8, 2.4, 4.6], [0.2, 1.2, -1.2], [0.2, 1.2, -1.2]],
    close_carlos: [[2.4, 1.9, 1.4], [2.2, 1.8, 1.2], [1.2, 1.25, -0.9], [1.2, 1.25, -0.9]],
    travelling_quadro: [[4.6, 2.3, 3.6], [3.2, 1.8, 0.4], [2.6, 1.5, -2.8], [2.6, 1.5, -2.8]],
    travado_largo: [[5.2, 4.6, 6.8], [5.2, 4.6, 6.8], [0.4, 1.1, -1.4], [0.4, 1.1, -1.4]],
    puxada_lampada: [[5.2, 4.6, 6.8], [7.6, 6.6, 10], [1.0, 1.2, -1.4], [1.0, 1.2, -1.4]],
    topo: [[0, 30, 0.01], [0, 28, 0.01], [0, 0, 0], [0, 0, 0]],
    topo_para_34: [[0, 28, 0.01], [7, 12, 12], [0, 0, 0], [10, 0, 0]],   // termina no fim da rota, com a porta da dona Socorro no quadro
    mergulho_portao: [[3.8, 2.6, 6], [5.4, 0.9, 2.6], [5.6, 1.0, 0], [5.6, 1.0, 0]],
  };
  const ease = p => p * p * (3 - 2 * p);
  const mix3 = (a, b, k) => a.map((v, i) => v + (b[i] - v) * k);
  const lerpCam = (nome, prog) => {
    const [p0, p1, a0, a1] = CAM[nome], k = ease(Math.min(1, Math.max(0, prog)));
    let pos = mix3(p0, p1, k), at = mix3(a0, a1, k);
    if (PORT && nome !== 'topo' && nome !== 'topo_para_34') {   // retrato: recua para caber a largura
      pos = at.map((v, i) => v + (pos[i] - v) * 1.7);
    }
    return { pos, at };
  };
  const LUZ = (F.planos.find(q => q.luz !== undefined) || {}).luz ?? Infinity;   // instante em que a luz volta (disjuntor religa LUZ − 3,2 s, ainda no S12)
  const planoEm = t => F.planos.find(p => t >= p.t0 && t < p.t1) || F.planos[F.planos.length - 1];

  // ——— falas e legendas (lines.json, tempos deste formato) ———
  const falas = linhas.filter(L => L['t_' + RET] != null);
  function quebrar(texto, max) {
    if (texto.length <= max) return [texto];
    const meio = texto.length / 2;
    let corte = texto.lastIndexOf(' ', meio + 10); if (corte < 0) corte = texto.indexOf(' ', meio);
    return [texto.slice(0, corte), texto.slice(corte + 1)];
  }
  function legenda(t) {
    const L = falas.find(l => l.tipo !== 'fala' && t >= l['t_' + RET] - .35 && t < l['t_' + RET] + l.dur_s + .15);
    if (!L) return;
    const linhasTxt = quebrar(L.text, PORT ? 30 : 200);
    const fs = Math.round((PORT ? 42 : 50) * sc);
    g.save();
    const base = H * .9;
    const grad = g.createLinearGradient(0, base - fs * 2.4, 0, H);
    grad.addColorStop(0, 'rgba(0,0,0,0)'); grad.addColorStop(1, 'rgba(0,0,0,.72)');
    g.fillStyle = grad; g.fillRect(0, base - fs * 2.4, W, H - (base - fs * 2.4));
    g.font = `italic ${fs}px "Cormorant Garamond"`; g.fillStyle = PAPEL; g.textAlign = 'center'; g.textBaseline = 'alphabetic';
    linhasTxt.forEach((s, i) => g.fillText(s, W / 2, base - (linhasTxt.length - 1 - i) * fs * 1.15));
    g.restore();
  }

  // Caixa de diálogo (f01): placa de nome, borda de metal, texto digitado sobre a fala
  function caixa(p, t) {
    const f = falas.find(l => l.tipo === 'fala');
    if (!p.dialogo || !f) return;
    const k = Math.min(1, Math.max(0, (t - f['t_' + RET]) / (.9 * f.dur_s)));
    const n = Math.floor(f.text.length * k), x = W * .08, w = W * .84, h = H * .2, y = H - h - H * .07;
    g.save();
    g.fillStyle = 'rgba(12,10,8,.9)'; g.fillRect(x, y, w, h);
    g.strokeStyle = OURO; g.lineWidth = 2 * sc; g.strokeRect(x + 6, y + 6, w - 12, h - 12);
    g.font = `${Math.round(30 * sc)}px Cinzel`; g.fillStyle = OURO; g.textAlign = 'left';
    const nomeW = g.measureText(f.falante).width + 40 * sc;
    g.fillStyle = '#1b1510'; g.fillRect(x + 30 * sc, y - 22 * sc, nomeW, 44 * sc);
    g.strokeRect(x + 30 * sc, y - 22 * sc, nomeW, 44 * sc);
    g.fillStyle = OURO; g.fillText(f.falante, x + 50 * sc, y + 10 * sc);
    g.font = `${Math.round(46 * sc)}px "Cormorant Garamond"`; g.fillStyle = PAPEL;
    g.fillText(f.text.slice(0, n) + (Math.floor(t * 2) % 2 === 0 && k < 1 ? '|' : ''), x + 40 * sc, y + h * .55);
    g.restore();
  }

  // Cartão do celular (S04): quem vem, sem tempo na tela
  function celular(t, p) {
    const k = Math.min(1, (t - p.t0) / .35), w = W * (PORT ? .8 : .46), h = H * (PORT ? .2 : .26), x = (W - w) / 2, y = H * .5 - h / 2;
    g.save(); g.globalAlpha = k;
    g.fillStyle = 'rgba(12,10,8,.92)'; g.fillRect(x, y, w, h);
    g.strokeStyle = OURO; g.lineWidth = 3 * sc; g.strokeRect(x + 10, y + 10, w - 20, h - 20);
    g.textAlign = 'center';
    g.font = `${Math.round(52 * sc)}px Cinzel`; g.fillStyle = PAPEL; g.fillText('Carlos Andrade', W / 2, y + h * .42);
    g.font = `${Math.round(40 * sc)}px "Cormorant Garamond"`; g.fillStyle = OURO; g.fillText('Eletricista · ★ 4,9', W / 2, y + h * .66);
    g.font = `italic ${Math.round(40 * sc)}px "Cormorant Garamond"`; g.fillStyle = VERDE; g.fillText('a caminho', W / 2, y + h * .86);
    g.restore();
  }

  // Cartão do Pix (S15): valor e cinco estrelas que acendem uma a uma
  function pix(t, p) {
    const w = W * (PORT ? .8 : .42), h = H * (PORT ? .3 : .34), x = PORT ? (W - w) / 2 : W * .06, y = PORT ? H * .08 : H * .08;   // em cima: não cobre os personagens
    const k = Math.min(1, (t - p.t0) / .35);
    g.save(); g.globalAlpha = k;
    g.fillStyle = 'rgba(12,10,8,.92)'; g.fillRect(x, y, w, h);
    g.strokeStyle = OURO; g.lineWidth = 3 * sc; g.strokeRect(x + 10, y + 10, w - 20, h - 20);
    g.textAlign = 'center';
    g.font = `${Math.round(44 * sc)}px Cinzel`; g.fillStyle = PAPEL; g.fillText('Pix ao profissional', x + w / 2, y + h * .3);
    g.font = `${Math.round(60 * sc)}px "Cormorant Garamond"`; g.fillStyle = OURO; g.fillText('R$ 180,00', x + w / 2, y + h * .56);
    g.font = `italic ${Math.round(40 * sc)}px "Cormorant Garamond"`; g.fillStyle = VERDE; g.fillText('Pago', x + w / 2, y + h * .72);
    const acesas = F.efeitos.tiques.filter(s => t >= s).length;
    for (let i = 0; i < 5; i++) {
      const cx = x + w / 2 + (i - 2) * 70 * sc, cy = y + h * .9, r = 22 * sc;
      g.beginPath();
      for (let j = 0; j < 10; j++) {
        const a = -Math.PI / 2 + j * Math.PI / 5, rr = j % 2 ? r * .45 : r;
        g.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
      }
      g.closePath(); g.fillStyle = i < acesas ? OURO : '#3a3f44'; g.fill();
    }
    g.restore();
  }

  // Cartão de capítulo (S02, S05): caixa alta com espaçamento largo
  function capitulo(p, t) {
    if (!p.cartao) return;
    const [a, b] = p.cartao_t, k = Math.min(1, (t - a) / .3, (b - t) / .3);
    if (t < a || t > b) return;
    g.save(); g.globalAlpha = Math.max(0, k);
    g.fillStyle = 'rgba(0,0,0,.55)'; g.fillRect(0, H * .4, W, H * .2);
    g.textAlign = 'center'; g.font = `${Math.round(44 * sc)}px Cinzel`; g.letterSpacing = `${Math.round(6 * sc)}px`;
    g.fillStyle = PAPEL; g.fillText(p.cartao, W / 2, H * .5 + 14 * sc);
    g.letterSpacing = '0px';
    g.restore();
  }

  // Título (S17 e último plano): TaskGO, com GO em azul
  function titulo(p, t) {
    if (!p.titulo) return;
    const k = Math.min(1, (t - p.t0) / .6);
    g.save(); g.globalAlpha = k; g.fillStyle = 'rgba(8,8,10,.6)'; g.fillRect(0, 0, W, H);
    g.font = `${Math.round(150 * sc)}px Cinzel`; g.textBaseline = 'middle';
    const a = 'Task', b = 'GO', wa = g.measureText(a).width, wb = g.measureText(b).width, x0 = W / 2 - (wa + wb) / 2;
    g.textAlign = 'left'; g.fillStyle = PAPEL; g.fillText(a, x0, H * .46);
    g.fillStyle = AZUL; g.fillText(b, x0 + wa, H * .46);
    g.textAlign = 'center'; g.font = `${Math.round(46 * sc)}px "Cormorant Garamond"`; g.fillStyle = OURO;
    g.fillText('Serviços locais em Recife', W / 2, H * .6);
    g.restore();
  }

  // ——— variantes dos personagens (poses e expressões) por plano ———
  // Socorro sempre no corredor; Carlos entra depois da porta (S03 só ela). Mostra o código em 31,43 s (S10)
  function atores(p, t) {
    const id = p.id;
    S.carlosInt.visible = id !== 'S03' && id !== 'V02';
    const poses = {
      S03: [t < 8.5 ? 'parada_preocupada' : 'gesto', 'parado'], V02: ['parada_preocupada', 'parado'],
      S10: ['parada_preocupada', t >= 31.43 ? 'celular' : 'parado'],
      S11: ['parada_preocupada', 'celular'],
      S12: ['parada_preocupada', 'disjuntor'], S13: ['parada_preocupada', 'disjuntor'],
      S14: ['aliviada', 'disjuntor'], V07: ['aliviada', 'disjuntor'],
      S15: ['aliviada', 'parado'], V08: ['aliviada', 'parado'],
      V06: ['parada_preocupada', 'disjuntor'],
    }[id];
    if (poses) { const b = t % 3.7 < .13 ? '_b' : ''; variar(S.socorro, poses[0] + b); variar(S.carlosInt, poses[1] + b); }   // piscar a cada ~3,7 s
  }

  // ——— quadro ———
  window.render = t => {
    const p = planoEm(t), prog = (t - p.t0) / (p.t1 - p.t0);
    let foco = new THREE.Vector3(0, 1.2, -2);
    const cena = p.cena;
    if (cena === 'rua') {
      S.mostrar('rua');
      S.estadoRua(t, t < 2.857 || !!p.luz_janela);
      const c = lerpCam(p.cam, prog); camera.position.set(...c.pos); camera.lookAt(...c.at);
      foco = p.cam === 'insert_janela' ? new THREE.Vector3(1.33, 3, -5.7) : new THREE.Vector3(0, 2.5, -6);
    } else if (cena === 'int' || cena === 'pix') {
      S.mostrar('int');
      const virada = p.luz ?? (cena === 'pix' ? 0 : LUZ);   // instante em que a luz volta (cômodos acendem a partir daí)
      const nome = cena === 'pix' ? 'medio_porta' : p.cam;
      const c = lerpCam(nome, prog); camera.position.set(...c.pos); camera.lookAt(...c.at);
      foco = nome === 'travelling_quadro' ? new THREE.Vector3(2.6 - .9 * prog, 1.5 - .3 * prog, -2.8 + .5 * prog) : new THREE.Vector3(1.2, 1.1, -0.9);
      // S12 (e V06): primeiro o chuveiro de perto, depois o quadro de luz com o Carlos inteiro no enquadramento
      const CHUV = { S12: 38.66, V06: 15.8 }[p.id];
      // 1º o chuveiro de perto (lanterna do Carlos ilumina, faísca no fio); 2º o Carlos ao lado do quadro, religando o disjuntor
      const religando = CHUV !== undefined && t >= CHUV;
      if (CHUV !== undefined) {
        const k = !religando ? [3.75, 1.95, -1.15, 4.3, 2.0, -2.85] : [2.6, 1.8, .4, 2.2, 1.35, -2.9], f = PORT ? 1.55 : 1;
        const at = [k[3], k[4], k[5]], pos = at.map((v, i) => v + (k[i] - v) * f);
        camera.position.set(...pos); camera.lookAt(...at); foco = new THREE.Vector3(...at);
        S.lanternaCarlos.position.set(pos[0], pos[1] + .2, pos[2] - .3); S.lanternaCarlos.intensity = religando ? 3 : 1.2;
      } else S.lanternaCarlos.intensity = 0;
      atores(p, t);
      if (religando) S.carlosInt.position.set(1.7, S.carlosInt.position.y, -2.35);   // de pé ao lado do quadro de luz
      else S.carlosInt.position.set(1.2, S.carlosInt.position.y, -.9);
      S.estadoInt(t, virada, p.id === 'S03' || p.id === 'V02');
    } else if (cena === 'mapa') {
      S.mostrar('mapa');
      S.estadoMapa(p.id === 'S07' ? 1 : prog);
      const c = lerpCam(p.cam, prog); camera.position.set(...c.pos); camera.lookAt(...c.at);
      foco = S.marcador.position.clone();
    } else if (cena === 'caminho') {
      S.mostrar('caminho');
      const parado = p.cam === 'mergulho_portao';
      S.estadoCaminho(parado ? 1 : prog, parado);
      if (parado) { const c = lerpCam(p.cam, prog); camera.position.set(...c.pos); camera.lookAt(...c.at); }
      else {
        const x = S.carlosCam.position.x;
        void x; const d = PORT ? 20 : 12.5, cx = PORT ? 1 + 11 * prog : 2 + 5 * prog;   // acompanha o Carlos: a ponte e, no fim, a Casa Amarela entram no quadro
        camera.position.set(cx, 4.6, d); camera.lookAt(cx + 1, -.3, -1.5);   // plano fixo e alto de 3/4: o rio inteiro, a ponte de arcos, o Carlos atravessando e a Casa Amarela depois da ponte
      }
      foco = S.carlosCam.position.clone().add(new THREE.Vector3(0, 1, 0));
    } else {
      S.mostrar('nada');   // cartões e celular: fundo escuro, só sobreposição
    }
    S.atualizarChuva(t);
    if (cena !== 'celular' && cena !== 'cartao') {
      S.giraParaCamera(camera);
    }
    // Foco e nitidez: distância até o assunto; faixa de nitidez na altura do assunto
    post.dof.focus = camera.position.distanceTo(foco);
    const pr = foco.clone().project(camera);
    post.dof.aper = 3; post.dof.tiltAmt = 2.4; post.dof.tiltC = Math.min(.8, Math.max(.2, pr.y * .5 + .5)); post.dof.tiltW = .3; post.dof.tiltF = .25; post.dof.maxCoc = 4;
    post.dof.scene = scene;
    if (Q.get('dbg')) { const v = Q.get('dbg').split(',').map(Number); camera.position.set(v[0], v[1], v[2]); camera.lookAt(v[3], v[4], v[5]); }   // conferência: câmera extra (só com ?dbg=px,py,pz,lx,ly,lz)
    post.composer.render();

    // Sobreposição
    g.clearRect(0, 0, W, H);
    capitulo(p, t);
    if (cena === 'celular') celular(t, p);
    if (cena === 'pix') pix(t, p);
    caixa(p, t);
    titulo(p, t);
    legenda(t);
  };
  window.READY = true;
}
