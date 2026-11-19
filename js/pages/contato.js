/* ---------- CONTATO ---------- */
/* Assuntos do formulário. O link pode escolher um: #/contato?assunto=servico&categoria=eletrica. */
const ASSUNTOS_CONTATO=[['servico','Preciso de um serviço'],['profissional','Quero ser profissional'],['empresa','Quero contratar para a minha empresa'],['suporte','Suporte ao cliente'],['parcerias','Parcerias / Empresas'],['imprensa','Imprensa'],['outro','Outro']];
function formContato(){
  const pedido=route.q.assunto||'',cat=route.q.categoria?SERVICES.find(s=>s.id===route.q.categoria):null;
  const mensagem=cat?'Preciso de um serviço de '+cat.n+'. ':'';
  return '<form id="contact-form" onsubmit="enviarContato(event)">'+
  '<div class="form-duas"><div class="field"><label for="contact-name">Nome</label><input class="input" id="contact-name" name="nome" required maxlength="120" autocomplete="name" placeholder="Seu nome completo"></div>'+
  '<div class="field"><label for="contact-email">E-mail</label><input class="input" id="contact-email" name="email" type="email" required maxlength="254" autocomplete="email" placeholder="voce@email.com"></div></div>'+
  '<div class="field"><label for="contact-subject">Assunto</label><select class="select" id="contact-subject" name="assunto" required>'+ASSUNTOS_CONTATO.map(a=>'<option'+(a[0]===pedido?' selected':'')+'>'+a[1]+'</option>').join('')+'</select></div>'+
  '<div class="field"><label for="contact-message">Mensagem</label><textarea class="textarea" id="contact-message" name="mensagem" required maxlength="5000" placeholder="Como podemos ajudar?">'+esc(mensagem)+'</textarea></div>'+
  '<div aria-hidden="true" class="oculto-robo"><label>Website<input name="website" tabindex="-1" autocomplete="off"></label></div>'+
  '<div class="form-envio"><button id="contact-submit" class="btn btn-primary btn-lg"><span>Enviar mensagem</span>'+ic('arrow')+'</button><p id="contact-status" class="form-status" aria-live="polite"></p></div></form>';
}
function pageContato(){
  return '<section class="folha fecho"><div class="wrap fecho-in"><div>'+duo('fale com','a equipe',1)+
  '<p class="corpo">Mande sua mensagem pelo formulário ou escreva direto para o e-mail abaixo.</p>'+
  '<p class="corpo"><b>Quem lê:</b> a equipe fundadora da TaskGO, estudantes da UNIBRA em Recife. A resposta vem pelo e-mail que você informar, em até 2 dias úteis.</p>'+
  '<ul class="contatos">'+
    '<li><small>E-mail</small><a href="mailto:taskgo.adm@gmail.com">taskgo.adm@gmail.com</a></li>'+
    '<li><small>WhatsApp</small><a href="https://wa.me/5581973370508" target="_blank" rel="noopener noreferrer">(81) 97337-0508</a></li>'+
  '</ul></div>'+formContato()+'</div></section>';
}
async function enviarContato(e){
  e.preventDefault();const form=e.currentTarget,button=$('#contact-submit'),status=$('#contact-status');
  const rotulo=button?.querySelector('span');
  const data=Object.fromEntries(new FormData(form).entries());if(button)button.disabled=true;if(rotulo)rotulo.textContent='Enviando...';if(status)status.textContent='Enviando...';
  try{
    const response=await fetch(API_BASE_URL+'/api/contatos',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
    const result=await response.json().catch(()=>null);
    if(!response.ok)throw new Error(result?.detail||result?.message||(response.status===429?'Limite de envios atingido. Tente novamente mais tarde.':'Não foi possível registrar a mensagem.'));
    form.reset();const enviada=result?.emailStatus==='enviado';if(status)status.textContent=(enviada?'Mensagem enviada.':'Mensagem recebida.')+' A resposta vem no e-mail que você informou.';toast(enviada?'Mensagem enviada.':'Mensagem recebida.','ok');
  }catch(error){if(status)status.textContent=error.message||'Falha de conexão. Seus campos foram mantidos para tentar novamente.';toast(error.message||'Falha de conexão. Tente novamente.','err');}
  finally{if(button)button.disabled=false;if(rotulo)rotulo.textContent='Enviar mensagem';}
}
