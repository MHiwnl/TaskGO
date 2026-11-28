// Pixel art por código: tela de pixels, rampas de cor com tramado Bayer 4×4, contorno pela cor vizinha (selout)
import * as THREE from 'three';

const B4 = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
export const bayer = (x, y) => (B4[(y & 3) * 4 + (x & 3)] + .5) / 16;
export const hex = c => { const n = parseInt(c.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; };
// Escolhe a cor da rampa pelo brilho v (0..1); dz estreito = tramado só numa faixa curta
export function ramp(cols, dz = .22) {
  const C = cols.map(hex);
  return (v, x, y) => {
    v = Math.max(0, Math.min(.9999, v)) * (C.length - 1);
    const i = Math.floor(v), f = Math.max(0, Math.min(1, (v - i - .5) / dz + .5));
    return C[Math.min(C.length - 1, i + (bayer(x, y) < f ? 1 : 0))];
  };
}
// Ruído determinístico (sem Math.random): mesma imagem em qualquer quadro
export const hash = (x, y) => { const s = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453; return s - Math.floor(s); };

export class PX {
  constructor(w, h) {
    this.w = w; this.h = h;
    this.c = document.createElement('canvas'); this.c.width = w; this.c.height = h;
    this.g = this.c.getContext('2d'); this.d = this.g.createImageData(w, h);
  }
  set(x, y, col, a = 255) {
    x |= 0; y |= 0; if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4, c = typeof col === 'string' ? hex(col) : col;
    this.d.data[i] = c[0]; this.d.data[i + 1] = c[1]; this.d.data[i + 2] = c[2]; this.d.data[i + 3] = a;
  }
  rect(x, y, w, h, col) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, typeof col === 'function' ? col(x + i, y + j) : col); }
  ellipse(cx, cy, rx, ry, col) {
    for (let y = Math.floor(cy - ry); y <= cy + ry; y++) for (let x = Math.floor(cx - rx); x <= cx + rx; x++) {
      const dx = (x + .5 - cx) / rx, dy = (y + .5 - cy) / ry;
      if (dx * dx + dy * dy <= 1) this.set(x, y, typeof col === 'function' ? col(x, y) : col);
    }
  }
  poly(pts, col) {   // preenchimento por linha de varredura
    const ys = pts.map(p => p[1]), y0 = Math.floor(Math.min(...ys)), y1 = Math.ceil(Math.max(...ys));
    for (let y = y0; y <= y1; y++) {
      const xs = [], yc = y + .5;
      for (let i = 0; i < pts.length; i++) {
        const [ax, ay] = pts[i], [bx, by] = pts[(i + 1) % pts.length];
        if ((ay <= yc && by > yc) || (by <= yc && ay > yc)) xs.push(ax + (yc - ay) / (by - ay) * (bx - ax));
      }
      xs.sort((a, b) => a - b);
      for (let k = 0; k + 1 < xs.length; k += 2)
        for (let x = Math.round(xs[k]); x < Math.round(xs[k + 1]); x++) this.set(x, y, typeof col === 'function' ? col(x, y) : col);
    }
  }
  // Traço grosso (membro): quadrados de w×w ao longo do segmento
  stroke(x0, y0, x1, y1, col, w = 2) {
    const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0), 1);
    for (let i = 0; i <= n; i++) {
      const x = Math.round(x0 + (x1 - x0) * i / n), y = Math.round(y0 + (y1 - y0) * i / n);
      this.rect(x, y, w, w, col);
    }
  }
  // Contorno: pixel transparente vizinho de um opaco recebe a cor do vizinho, escurecida
  outline(k = .32, tint = [22, 14, 30]) {
    const src = new Uint8ClampedArray(this.d.data), W = this.w, H = this.h, add = [];
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      if (src[(y * W + x) * 4 + 3]) continue;
      for (const [dx, dy] of [[0, 1], [0, -1], [1, 0], [-1, 0]]) {
        const X = x + dx, Y = y + dy; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
        const i = (Y * W + X) * 4;
        if (src[i + 3] > 128) { add.push([x, y, [0, 1, 2].map(c => Math.round(tint[c] + (src[i + c] - tint[c]) * k))]); break; }
      }
    }
    for (const [x, y, c] of add) this.set(x, y, c);
  }
  done() { this.g.putImageData(this.d, 0, 0); return this.c; }
}

// Textura de pixels: sem suavização (NearestFilter), repetição opcional
export function tex(canvas, repeat) {
  const t = new THREE.CanvasTexture(canvas);
  t.magFilter = THREE.NearestFilter; t.minFilter = THREE.LinearFilter; t.generateMipmaps = false;
  t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4;
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat[0], repeat[1]); }
  return t;
}
