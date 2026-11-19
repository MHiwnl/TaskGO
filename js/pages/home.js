/* =========================================================
   INÍCIO
   ========================================================= */
/* Foto principal do topo. Sem caminho, o topo mostra o desenho do raio de atendimento.
   Origem: imagem gerada no ChatGPT (gpt-image), conforme as credenciais C2PA do original em assets/topo/originais/alicate.jpg.
   O arquivo do site é uma versão reduzida do original. */
const FOTO_PRINCIPAL='assets/topo/alicate.jpg';
const FOTO_PRINCIPAL_ALT='Alicate de eletricista com marcas de uso, apoiado sobre cimento.';
function desenhoRaio(){
  return '<svg class="hero-desenho" viewBox="0 0 600 600" aria-hidden="true" focusable="false">'+
    [70,140,210,280].map((r,i)=>'<circle cx="300" cy="300" r="'+r+'"'+(i<3?' class="fino"':'')+'/>').join('')+
    '<line class="fino" x1="300" y1="0" x2="300" y2="600"/><line class="fino" x1="0" y1="300" x2="600" y2="300"/>'+
    '<path class="rota" d="M64 452H176V360H300V300"/>'+
    '<circle class="ponto" cx="300" cy="300" r="7"/><circle class="ponto" cx="64" cy="452" r="7"/>'+
  '</svg>';
}
/* Vídeo do topo: tem prioridade sobre a foto. Só fundo, sem legenda: as pessoas não representam profissionais da TaskGO.
   Origem: vídeo de banco de imagens que já estava no projeto (arquivo original 8853466-hd_1920_1080_24fps.mp4, nome no padrão do Pexels; licença a confirmar). */
const VIDEO_TOPO='assets/videos/topo-eletricistas.mp4';
/* Quem prefere menos movimento recebe o vídeo parado; qualquer pessoa pode pausar. */
let videoPausado=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
function rotuloVideo(){return ic(videoPausado?'play':'pause')+'<span>'+(videoPausado?'Reproduzir vídeo':'Pausar vídeo')+'</span>';}
function alternarVideo(botao){
  videoPausado=!videoPausado;
  const video=$('.hero-campo video');
  if(video){if(videoPausado)video.pause();else video.play().catch(()=>{});}
  botao.innerHTML=rotuloVideo();
  botao.setAttribute('aria-pressed',String(videoPausado));
}
/* O vídeo para quando sai da tela ou a aba fica oculta, e volta sozinho se não foi pausado. */
let observadorVideo=null;
function ligarVideoTopo(){
  const video=$('.hero-campo video');
  if(observadorVideo){observadorVideo.disconnect();observadorVideo=null;}
  if(!video)return;
  const ajustar=visivel=>{if(videoPausado||!visivel||document.hidden)video.pause();else video.play().catch(()=>{});};
  if('IntersectionObserver' in window){
    observadorVideo=new IntersectionObserver(itens=>ajustar(itens[0].isIntersecting),{threshold:.1});
    observadorVideo.observe(video);
  }else ajustar(true);
}
document.addEventListener('visibilitychange',()=>{const v=$('.hero-campo video');if(!v)return;if(document.hidden||videoPausado)v.pause();else v.play().catch(()=>{});});
function heroCampo(){
  let midia;
  if(VIDEO_TOPO)midia='<video class="hero-foto hero-video" muted loop playsinline preload="metadata" aria-hidden="true" tabindex="-1"><source src="'+VIDEO_TOPO+'" type="video/mp4"></video>';
  else if(FOTO_PRINCIPAL)midia='<img class="hero-foto" src="'+FOTO_PRINCIPAL+'" alt="'+esc(FOTO_PRINCIPAL_ALT)+'">';
  else midia=desenhoRaio();
  return '<div class="hero-campo">'+midia+'</div>';
}
/* A prévia do fluxo do profissional fica em js/pages/celular.js: um celular com o app simulado. */
function pageHome(){
  return '<section class="hero">'+heroCampo()+'<div class="wrap"><div class="hero-texto">'+
    duo('o profissional certo,','perto de você.',1)+
    '<p class="hero-sub">Diga o que precisa e quanto quer pagar. Profissionais verificados da sua região aceitam ou mandam proposta, você acompanha a chegada no mapa e paga direto a quem atendeu, só depois do serviço pronto.</p>'+
    '<div class="setas">'+linkSeta('#/servicos','Preciso de um serviço',1)+linkSeta('#/profissionais','Sou profissional')+'</div>'+
    '<div class="hero-rodape"><p class="hero-fase">Recife · '+SERVICES.length+' serviços</p>'+
    (VIDEO_TOPO?'<button type="button" class="hero-pausa" aria-pressed="'+videoPausado+'" onclick="alternarVideo(this)">'+rotuloVideo()+'</button>':'')+'</div>'+
  '</div></div></section>'+

  folha('o problema','<p class="manifesto">Achar alguém de confiança, combinar o preço, saber a hora que chega e pagar com segurança costuma levar uma tarde de mensagens. <strong>Na TaskGO, tudo acontece num pedido só.</strong></p>','folha-densa')+

  folha('para profissionais','<div class="fluxo"><div>'+
    duo('pedidos da sua região','direto no celular',2,'duo-az')+
    '<p class="corpo">Você vê o serviço, o bairro e o valor antes de aceitar. Combina pelo chat, segue a rota até o cliente e recebe dele mesmo, por Pix ou em dinheiro. Sem mensalidade obrigatória: a taxa da TaskGO só existe quando você recebe.</p>'+
    '<div class="setas">'+linkSeta('#/entrar?tipo=profissional','Criar conta de profissional',1)+'</div></div>'+
    celularSimulado()+'</div>')+

  folha('categorias',titulo('mais de 100 serviços num lugar só')+
    '<p class="corpo">Da tomada que não funciona à reforma da cozinha, da diarista da semana à aula de violão.</p>'+
    '<div class="cats">'+SERVICES.filter(s=>s.pop).map(s=>'<a href="#/servicos/'+s.id+'">'+ic(s.ico,'ic-svc-'+s.id)+'<span>'+s.n+'</span>'+ic('arrow')+'</a>').join('')+
    '<a href="#/servicos" class="cats-todos">'+ic('grid')+'<span>Ver todos os '+SERVICES.length+' serviços</span>'+ic('arrow')+'</a></div>')+

  folha('por que a TaskGO',titulo('feita para você contratar tranquilo')+
    '<div class="recursos">'+[
      ['Identidade verificada','Documento e selfie conferidos pela equipe antes de a pessoa pedir ou atender.'],
      ['Endereço protegido','O profissional só vê o seu endereço e telefone depois que você confirma quem vai atender.'],
      ['Chegada no mapa','Rota pelas ruas e minutos até a chegada, atualizados enquanto ele está a caminho.'],
      ['Código de chegada','O serviço só começa com os 4 números que aparecem no seu celular.'],
      ['Pague depois','Por Pix ou em dinheiro, direto ao profissional, só com o serviço pronto.'],
      ['Equipe de plantão','Botão de emergência em cada pedido, denúncias analisadas e contas suspensas quando preciso.']
    ].map(x=>'<div class="recurso"><h3 class="sub" style="margin-bottom:10px">'+x[0]+'</h3><p>'+x[1]+'</p></div>').join('')+'</div>'+
    '<div class="setas">'+linkSeta('#/seguranca','Como protegemos você')+'</div>','azul')+

  folha('dúvidas',titulo('perguntas frequentes')+
    '<div class="faq-lista" style="margin-top:36px">'+faqList([0,2,4,1,7,6].map(i=>FAQ[i]))+'</div>'+
    '<div class="setas">'+linkSeta('#/ajuda','Ver todas as perguntas')+'</div>')+

  '<section class="folha fecho"><div class="wrap fecho-in"><div>'+titulo('fale com a gente')+
    '<p class="corpo">Dúvida, parceria ou sugestão: mande sua mensagem e a equipe responde por e-mail.</p></div>'+
    formContato()+'</div></section>';
}
