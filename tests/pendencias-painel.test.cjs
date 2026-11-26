/* Regressões do painel: data da verificação no fuso local e avisos oferecidos no recurso de moderação. */
process.env.TZ='America/Recife';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');const vm=require('node:vm');
const raiz=path.join(__dirname,'..');
const carregar=arquivo=>{const ctx={esc:s=>String(s),dataHora:()=>'25/11 10:00'};vm.createContext(ctx);vm.runInContext(fs.readFileSync(path.join(raiz,arquivo),'utf8'),ctx);return ctx;};

test('data máxima do nascimento usa o dia de Recife, não o de UTC',()=>{
  const {dataLocalISO}=carregar('js/painel/verificacao.js');
  /* 23h30 em Recife já é 02h30 do dia seguinte em UTC: o limite não pode passar para amanhã. */
  assert.equal(dataLocalISO(new Date('2026-11-25T23:30:00-03:00')),'2026-11-25');
  assert.equal(dataLocalISO(new Date('2026-11-25T00:10:00-03:00')),'2026-11-25');
});

test('recurso só leva pedido de aviso ligado a um pedido',()=>{
  const ctx=carregar('js/painel/moderacao.js');
  const html=ctx.opcoesAvisoRecurso([{pedidoId:'p1',texto:'Aviso do pedido',quando:1},{pedidoId:null,texto:'Aviso geral',quando:2}]);
  const opcoes=[...html.matchAll(/<option value="([^"]*)">([^<]*)<\/option>/g)].map(m=>[m[1],m[2]]);
  assert.deepEqual(opcoes.map(o=>o[0]),['p1',''],'um aviso com pedido e a opção sem pedido');
  assert.ok(!opcoes.some(o=>o[1].includes('Aviso geral')),'aviso sem pedido não aparece como se fosse de um pedido');
  assert.equal(opcoes[1][1],'Não é sobre um pedido');
});
