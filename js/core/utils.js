/* Leaflet: um mapa removido no meio da animação de zoom não pode terminar a animação (erro conhecido da biblioteca). */
if(window.L&&L.Map&&!L.Map.prototype._taskgoSeguro){const fimZoom=L.Map.prototype._onZoomTransitionEnd;L.Map.prototype._onZoomTransitionEnd=function(){if(!this._mapPane)return;return fimZoom.apply(this,arguments);};const ajustar=L.Map.prototype.invalidateSize;L.Map.prototype.invalidateSize=function(){if(!this._mapPane)return this;return ajustar.apply(this,arguments);};L.Map.prototype._taskgoSeguro=true;}
/* ---------- UTIL ---------- */
const $=(s,r)=>(r||document).querySelector(s);
const $$=(s,r)=>Array.from((r||document).querySelectorAll(s));
const money=n=>'R$ '+Number(n||0).toLocaleString('pt-BR',{minimumFractionDigits:2,maximumFractionDigits:2});
const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const uid=()=>Math.random().toString(36).slice(2,10);
const rand=(a,b)=>Math.floor(Math.random()*(b-a+1))+a;
const initials=n=>String(n||'?').trim().split(/\s+/).slice(0,2).map(w=>w[0]).join('').toUpperCase();
const dt=ts=>new Date(ts).toLocaleDateString('pt-BR',{day:'2-digit',month:'short'})+' · '+new Date(ts).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
function maskCPF(v){v=String(v).replace(/\D/g,'').slice(0,11);return v.replace(/(\d{3})(\d)/,'$1.$2').replace(/(\d{3})(\d)/,'$1.$2').replace(/(\d{3})(\d{1,2})$/,'$1-$2');}
function maskPhone(v){v=String(v).replace(/\D/g,'').slice(0,11);if(v.length<=10)return v.replace(/(\d{2})(\d)/,'($1) $2').replace(/(\d{4})(\d)/,'$1-$2');return v.replace(/(\d{2})(\d)/,'($1) $2').replace(/(\d{5})(\d)/,'$1-$2');}
function avatar(user,size){
  size=size||40;
  const st='width:'+size+'px;height:'+size+'px;font-size:'+Math.max(10,size*.36)+'px';
  if(user&&user.photo)return '<img class="avatar" style="'+st+'" src="'+esc(user.photo)+'" alt="" onerror="this.replaceWith(Object.assign(document.createElement(\'div\'),{className:\'avatar\',style:this.getAttribute(\'style\'),textContent:'+esc(JSON.stringify(initials(user.name||'?')))+'}))">';
  return '<div class="avatar" style="'+st+'">'+esc(initials(user?user.name:'?'))+'</div>';
}
/* Liga cada rótulo ao campo seguinte, para leitores de tela e para o clique no rótulo. */
let rotuloSeq=0;
function ligarRotulos(raiz){
  (raiz||document).querySelectorAll('.field>label:not([for])').forEach(rotulo=>{
    /* Rótulo que já envolve o próprio campo (opções de rádio e caixas) não pode apontar para outro. */
    if(rotulo.querySelector('input,select,textarea'))return;
    const campo=rotulo.parentElement.querySelector('input,select,textarea');
    if(!campo)return;
    if(!campo.id)campo.id='campo-'+(++rotuloSeq);
    rotulo.htmlFor=campo.id;
  });
}
/* ---------- Busca aproximada ("Você quis dizer?") ----------
   Mistura quatro medidas e fica com a melhor: distância de edição, Jaro-Winkler, começo da palavra,
   som em português (ch=x, ç=s, gue=je...) e letras em comum na mesma ordem. Abaixo de 0,68 não sugere nada. */
const semAcentoBusca=t=>String(t||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
function distEdicao(a,b){const m=a.length,n=b.length,d=Array.from({length:m+1},(_,i)=>[i]);for(let j=1;j<=n;j++)d[0][j]=j;
  for(let i=1;i<=m;i++)for(let j=1;j<=n;j++){d[i][j]=Math.min(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+(a[i-1]===b[j-1]?0:1));if(i>1&&j>1&&a[i-1]===b[j-2]&&a[i-2]===b[j-1])d[i][j]=Math.min(d[i][j],d[i-2][j-2]+1);}return d[m][n];}
function jaroWinkler(s1,s2){if(s1===s2)return 1;const l1=s1.length,l2=s2.length,md=Math.max(0,Math.floor(Math.max(l1,l2)/2)-1),m1=Array(l1).fill(false),m2=Array(l2).fill(false);let m=0;
  for(let i=0;i<l1;i++)for(let j=Math.max(0,i-md);j<Math.min(l2,i+md+1);j++)if(!m2[j]&&s1[i]===s2[j]){m1[i]=m2[j]=true;m++;break;}
  if(!m)return 0;let t=0,k=0;for(let i=0;i<l1;i++)if(m1[i]){while(!m2[k])k++;if(s1[i]!==s2[k])t++;k++;}
  const j=(m/l1+m/l2+(m-t/2)/m)/3;let p=0;while(p<4&&s1[p]===s2[p])p++;return j+p*.1*(1-j);}
function somPT(t){return semAcentoBusca(t).replace(/ph/g,'f').replace(/ch|sh|x/g,'s').replace(/c(?=[ei])/g,'s').replace(/c|q/g,'k').replace(/g(?=[ei])/g,'j').replace(/z/g,'s').replace(/h/g,'').replace(/y/g,'i').replace(/w/g,'v').replace(/(.)\1+/g,'$1');}
function emComum(a,b){const d=Array.from({length:a.length+1},()=>Array(b.length+1).fill(0));for(let i=1;i<=a.length;i++)for(let j=1;j<=b.length;j++)d[i][j]=a[i-1]===b[j-1]?d[i-1][j-1]+1:Math.max(d[i-1][j],d[i][j-1]);return d[a.length][b.length];}
function semelhanca(q,t){
  q=semAcentoBusca(q);t=semAcentoBusca(t);if(!q||!t)return 0;
  const a=1-distEdicao(q,t)/Math.max(q.length,t.length),b=jaroWinkler(q,t);
  const pref=t.slice(0,q.length+2),c=1-distEdicao(q,pref)/Math.max(q.length,pref.length);
  const sq=somPT(q),st=somPT(t),f=sq.length>=3?jaroWinkler(sq,st):0;
  const L=emComum(q,t),g=q.length>=4?(L/q.length)*.75+(q[0]===t[0]?.15:0)+(L/t.length)*.1:0;
  return Math.max(a,b*.92,c*.95,f*.95,g);
}
/* opcoes: [{rotulo, termos:[...], valor}] -> a opção mais parecida com o que foi digitado, ou null. */
function voceQuisDizer(q,opcoes,corte){
  q=semAcentoBusca(q);if(q.length<3)return null;
  let melhor=null;
  for(const o of opcoes)for(const t of [o.rotulo].concat(o.termos||[])){const s=semelhanca(q,t);if(!melhor||s>melhor.nota)melhor={opcao:o,termo:t,nota:s};}
  return melhor&&melhor.nota>=(corte||.68)&&semAcentoBusca(melhor.termo)!==q?melhor.opcao:null;
}
/* ---------- Senha forte ----------
   As mesmas regras do servidor (PoliticaSenha): 12+ caracteres, 3 de 4 tipos, nada comum nem pessoal.
   A checagem de senhas vazadas acontece só no servidor. */
const SENHAS_COMUNS_FRONT=['123456','1234567','12345678','123456789','1234567890','senha','minhasenha','mudar123','password','passw0rd','qwerty','qwertyuiop','asdfgh','abc123','abcdef','iloveyou','teamo','brasil','flamengo','corinthians','palmeiras','saopaulo','santos','vasco','gremio','cruzeiro','sport','nautico','santacruz','recife','pernambuco','jesus','deus','amor','familia','futebol','taskgo','admin','welcome','letmein','dragon','master','superman','batman'];
function regrasSenha(s,email,nome){
  s=String(s||'');const base=semAcentoBusca(s),letras=base.replace(/[^a-z]+$/,'').replace(/^[^a-z]+/,'');
  const tipos=[/[a-zà-ÿ]/.test(s),/[A-ZÀ-Þ]/.test(s),/[0-9]/.test(s),/[^A-Za-zÀ-ÿ0-9]/.test(s)].filter(Boolean).length;
  const usuario=semAcentoBusca(String(email||'').split('@')[0]).replace(/[^a-z]/g,'');
  const pessoal=(usuario.length>=4&&base.includes(usuario))||semAcentoBusca(nome).split(/\s+/).some(p=>p.length>=4&&base.includes(p));
  const comum=SENHAS_COMUNS_FRONT.includes(base)||SENHAS_COMUNS_FRONT.includes(letras)||new Set(base).size<=3||/^(0123456789|9876543210|abcdefghij|qwerty)/.test(base);
  return [
    {ok:s.length>=12,txt:'12 caracteres ou mais'},
    {ok:tipos>=3,txt:'3 destes: minúscula, maiúscula, número, símbolo'},
    {ok:s.length>0&&!comum,txt:'Não ser uma senha comum'},
    {ok:s.length>0&&!pessoal,txt:'Não ter o seu nome nem o seu e-mail'}
  ];
}
function blocoRegrasSenha(id){return '<ul class="regras-senha" id="regras-'+id+'" aria-live="polite">'+regrasSenha('').map(r=>'<li>'+ic('check')+r.txt+'</li>').join('')+'</ul>';}
function conferirSenhaCampo(campo,email,nome){
  const ul=document.getElementById('regras-'+campo.id);if(!ul)return;
  regrasSenha(campo.value,email,nome).forEach((r,i)=>{const li=ul.children[i];if(li)li.classList.toggle('ok',r.ok);});
}
function faltaNaSenha(s,email,nome){const f=regrasSenha(s,email,nome).filter(r=>!r.ok);return f.length?'A senha precisa ter: '+f.map(r=>r.txt.toLowerCase()).join('; ')+'.':'';}
function stars(n){let h='';for(let i=0;i<5;i++)h+='<svg viewBox="0 0 24 24" style="opacity:'+(i<Math.round(n)?1:.25)+'">'+I.star+'</svg>';return '<span class="stars">'+h+'</span>';}
function toast(msg,type){
  if(type==='err'&&window.__sessaoVencida)return;
  const t=document.createElement('div');
  t.className='toast '+(type||'');
  t.innerHTML=ic(type==='err'?'x':'checkCircle')+'<span>'+esc(msg)+'</span>';
  $('#toasts').appendChild(t);
  /* Aviso que diz o que falta: leva direto ao lugar onde se resolve. */
  if(type==='err'&&typeof destinoDoAviso==='function'&&destinoDoAviso(msg))t.classList.add('toast-leva');
  setTimeout(()=>{t.style.transition='.3s';t.style.opacity='0';t.style.transform='translateX(20px)';setTimeout(()=>t.remove(),320);},3200);
}

