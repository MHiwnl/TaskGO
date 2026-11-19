// Em cada batida do corte 9:16, mede o tamanho de todo texto visível como ele aparece num celular de 390 px de largura,
// procura texto cortado pela borda e grava os quadros reduzidos a 390 px para leitura.
const fs = require('node:fs'), path = require('node:path');
const { RAIZ, ffmpeg } = require('./ferramentas.cjs');
const { abrirFilme } = require('./quadros.cjs');
const ESC = 390 / 1080, MIN = 9;   /* abaixo de 9 px num celular, o texto não se lê */
(async () => {
  const { b } = await abrirFilme('9x16', false, true);
  const cenas = await b.js('TIMELINE.cenas.map(c=>({id:c.id,t:c.inicio+c.dur*0.8}))');
  const dir = path.join(RAIZ, 'verificacao/390'); fs.mkdirSync(dir, { recursive: true });
  const linhas = []; let problemas = 0;
  for (const c of cenas) {
    const png = await b.quadro(c.t); const f = path.join(dir, c.id + '.png'); fs.writeFileSync(f, png);
    ffmpeg(['-i', f, '-vf', 'scale=390:-1:flags=lanczos', f.replace('.png', '_390.png')]); fs.rmSync(f);
    const r = await b.js(`(()=>{const out=[];const W=1080,H=1920;const w=document.createTreeWalker(document.getElementById('filme'),NodeFilter.SHOW_TEXT);
      while(w.nextNode()){const n=w.currentNode;if(!n.textContent.trim())continue;const e=n.parentElement;let p=e,vis=true,op=1;
        while(p&&p.id!=='filme'){const s=getComputedStyle(p);if(s.visibility==='hidden'||s.display==='none'){vis=false;break;}op*=+s.opacity;p=p.parentElement;}
        if(!vis||op<0.5)continue;const rg=document.createRange();rg.selectNodeContents(n);const bb=rg.getBoundingClientRect();if(!bb.width)continue;let q=e,clip=false;while(q&&q.id!=='filme'){const cs=getComputedStyle(q);if(cs.overflow==='hidden'){const r2=q.getBoundingClientRect();if(bb.bottom<=r2.top+1||bb.top>=r2.bottom-1||bb.right<=r2.left+1||bb.left>=r2.right-1){clip=true;break;}}q=q.parentElement;}if(clip)continue;
        const fs=parseFloat(getComputedStyle(e).fontSize);const sc=bb.height/(fs*1.0)||1;
        out.push({txt:n.textContent.trim().slice(0,40),fs:fs,corta:bb.left<-1||bb.right>W+1||bb.top<-1||bb.bottom>H+1});}
      return out})()`);
    const menor = r.reduce((m, x) => x.fs < m.fs ? x : m, { fs: 999 });
    const cortados = r.filter(x => x.corta).map(x => x.txt);
    const px390 = menor.fs * ESC;
    if (px390 < MIN) problemas++; if (cortados.length) problemas++;
    linhas.push(`${c.id.padEnd(11)} menor texto ${px390.toFixed(1)} px ("${menor.txt}")${px390 < MIN ? '  PEQUENO' : ''}${cortados.length ? '  CORTADO: ' + cortados.join(' | ') : ''}`);
  }
  await b.fechar();
  linhas.push(problemas ? problemas + ' problema(s)' : 'tudo legível a 390 px e nada cortado');
  console.log(linhas.join('\n')); fs.writeFileSync(path.join(RAIZ, 'verificacao/390.txt'), linhas.join('\n') + '\n');
})();
