/* Funções do site (sem navegador): carrega os scripts num contexto isolado e testa as regras.
   Rodar: npm test */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

function carregar(arquivos){
  const ctx={window:{},document:{documentElement:{dataset:{}},getElementById:()=>null,querySelector:()=>null},navigator:{},
    location:{hash:''},console,setTimeout,clearTimeout,URL,atob:s=>Buffer.from(s,'base64').toString('binary'),
    TASKGO_DEMO_MODE:true,S:{orders:[]},route:{},me:()=>null,ic:()=>'',I:{star:''},$:()=>null};
  vm.createContext(ctx);
  for(const a of arquivos)vm.runInContext(fs.readFileSync(a,'utf8'),ctx,{filename:a});
  return ctx;
}
const site=carregar(['js/core/utils.js','js/core/data.js','js/painel/pagamento.js','js/painel/area-atendimento.js','js/painel/avisos-conta.js','js/painel/verificacao.js','js/painel/fotos.js']);
const f=nome=>vm.runInContext(nome,site);

test('esc escapa HTML (texto de usuário nunca vira tag)',()=>{
  assert.equal(f('esc')('<img src=x onerror="alert(1)">'),'&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
  assert.equal(f('esc')(null),'');
  assert.equal(f('esc')("O'Neil & cia"),'O&#39;Neil &amp; cia');
});

test('money formata em reais',()=>{
  assert.match(f('money')(1234.5),/^R\$ 1\.234,50$/);
  assert.match(f('money')(null),/^R\$ 0,00$/);
});

test('máscaras de CPF e telefone',()=>{
  assert.equal(f('maskCPF')('12345678901'),'123.456.789-01');
  assert.equal(f('maskPhone')('81999990001'),'(81) 99999-0001');
});

test('"Você quis dizer" acha a categoria com erro de digitação',()=>{
  const opcoes=[{rotulo:'Elétrica',termos:['eletricista'],valor:'eletrica'},{rotulo:'Pintura',termos:['pintor'],valor:'pintura'}];
  assert.equal(f('voceQuisDizer')('eletrisista',opcoes).valor,'eletrica');
  assert.equal(f('voceQuisDizer')('pintr',opcoes).valor,'pintura');
  assert.equal(f('voceQuisDizer')('xz',opcoes),null);
});

test('regras de senha iguais às do servidor',()=>{
  const falta=f('faltaNaSenha');
  assert.equal(falta('Caminho#Verde2026','ana@teste.test','Ana Lima'),'');
  assert.match(falta('curta1A!','',''),/12 caracteres/);
  assert.match(falta('senhasenhasenha','',''),/3 destes/);
  assert.match(falta('AnaLima#2026xyz','ana@teste.test','Ana Lima'),/nome/i);
});

test('bandeira do cartão pelo número',()=>{
  const b=f('bandeiraDoNumero');
  assert.equal(b('4111111111111111'),'Visa');
  assert.equal(b('5555555555554444'),'Mastercard');
  assert.equal(b('378282246310005'),'Amex');
  assert.equal(b('6362970000457013'),'Elo');
  assert.equal(b('9999'),'');
});

test('parcelas: até 6x abaixo de R$ 1.000, até 12x a partir dele, parcela mínima de R$ 50',()=>{
  const p=f('maxParcelas');
  assert.equal(p(80),1);assert.equal(p(150),3);assert.equal(p(999),6);assert.equal(p(1000),12);assert.equal(p(5000),12);
});

test('tempo restante do Pix em mm:ss',()=>{
  const t=f('tempoRestante');
  assert.equal(t(Date.now()+125000),'02:05');
  assert.equal(t(Date.now()-1000),'00:00');
});

test('distância da oportunidade',()=>{
  const d=f('distanciaOportunidade');
  assert.equal(d({km:0.4}),'a menos de 1 km');
  assert.equal(d({km:3.25}),'a 3,3 km');
  assert.equal(d({km:14}),'a 14 km');
  assert.equal(d({}),'');
});

test('chave do push em base64url vira bytes',()=>{
  const bytes=f('base64ParaBytes')('BAEC');
  assert.deepEqual(Array.from(bytes),[4,1,2]);
});

test('selo de verificado só aparece para conta verificada',()=>{
  assert.equal(f('seloVerificado')(false),'');
  assert.match(f('seloVerificado')(true),/Verificado/);
});

test('pagamento: rótulo do pedido conforme a situação',()=>{
  const r=f('rotuloPagamentoPedido');
  assert.equal(r({status:'aguardando_pagamento'}),'Esperando pagamento');
  assert.equal(r({status:'concluido',paid:'retido',liberacao:'aguardando'}),'Esperando confirmação');
  assert.equal(r({status:'concluido',paid:'retido',liberacao:'problema'}),'Retido, em análise');
  assert.equal(r({status:'a_caminho',paid:'retido'}),'Pago, com a TaskGO até concluir');
  assert.equal(r({status:'cancelado',paid:'estornado'}),'Devolvido');
});
