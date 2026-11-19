/* =========================================================
   PIX COPIA E COLA (BR Code do Banco Central)
   Monta o código do Pix com a chave de quem recebe, o valor e o nome, igual ao QR que o app
   do banco gera. O dinheiro vai direto para a conta da chave; nada passa pela TaskGO.
   Formato EMV: cada campo é ID (2) + tamanho (2) + valor; no fim, CRC16-CCITT (0xFFFF).
   ========================================================= */
function campoEmv(id,valor){const v=String(valor);return id+String(v.length).padStart(2,'0')+v;}
function textoPix(t,max){return String(t||'').normalize('NFD').replace(/[̀-ͯ]/g,'').replace(/[^A-Za-z0-9 .-]/g,'').trim().toUpperCase().slice(0,max)||'TASKGO';}
function crc16Pix(s){
  let crc=0xFFFF;
  for(let i=0;i<s.length;i++){crc^=s.charCodeAt(i)<<8;for(let b=0;b<8;b++)crc=(crc&0x8000)?((crc<<1)^0x1021)&0xFFFF:(crc<<1)&0xFFFF;}
  return crc.toString(16).toUpperCase().padStart(4,'0');
}
function codigoPix({chave,nome,cidade,valor,txid}){
  const conta=campoEmv('00','br.gov.bcb.pix')+campoEmv('01',chave);
  const id=String(txid||'').replace(/[^A-Za-z0-9]/g,'').slice(0,25)||'***';
  const p=campoEmv('00','01')+campoEmv('26',conta)+campoEmv('52','0000')+campoEmv('53','986')+
    (Number(valor)>0?campoEmv('54',Number(valor).toFixed(2)):'')+
    campoEmv('58','BR')+campoEmv('59',textoPix(nome,25))+campoEmv('60',textoPix(cidade||'BRASIL',15))+
    campoEmv('62',campoEmv('05',id))+'6304';
  return p+crc16Pix(p);
}
/* QR em SVG (a mesma biblioteca da verificação em duas etapas). Devolve '' enquanto ela carrega. */
let qrPixCarregando=false;
function qrPixSvg(codigo){
  if(!window.qrcode){
    if(!qrPixCarregando&&typeof carregarQr==='function'){qrPixCarregando=true;carregarQr().then(()=>{qrPixCarregando=false;if(typeof render==='function')render();}).catch(()=>{qrPixCarregando=false;});}
    return '';
  }
  try{const q=qrcode(0,'M');q.addData(codigo);q.make();return q.createSvgTag({cellSize:4,margin:2,scalable:true});}catch(e){return '';}
}
if(typeof module!=='undefined')module.exports={codigoPix,crc16Pix};
