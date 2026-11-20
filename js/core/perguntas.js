/* Perguntas do Contratar (agendar) por serviço: escolha única e opcional. Os ids são espelhados em CatalogoServicos.java.
   Regra: nunca reaproveitar nem apagar um id de opção, porque pedidos antigos guardam esses ids. */
const PERGUNTAS_SERVICO={
  eletrica:[
    {id:'tipo',texto:'O que precisa?',opcoes:[{id:'tomada',rotulo:'Tomada'},{id:'chuveiro',rotulo:'Chuveiro'},{id:'disjuntor',rotulo:'Disjuntor ou quadro'},{id:'fiacao',rotulo:'Fiação'},{id:'luminaria',rotulo:'Luminária'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'seguranca',texto:'Tem urgência de segurança?',opcoes:[{id:'queimado',rotulo:'Cheiro de queimado'},{id:'faisca',rotulo:'Faísca ou choque'},{id:'nao',rotulo:'Não'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  hidraulica:[
    {id:'tipo',texto:'O que precisa?',opcoes:[{id:'vazamento',rotulo:'Vazamento'},{id:'entupimento',rotulo:'Entupimento'},{id:'torneira',rotulo:'Torneira ou registro'},{id:'caixa',rotulo:'Caixa d’água'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'local',texto:'Onde está o problema?',opcoes:[{id:'banheiro',rotulo:'Banheiro'},{id:'cozinha',rotulo:'Cozinha'},{id:'area',rotulo:'Área de serviço'},{id:'externo',rotulo:'Área externa'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  marido:[
    {id:'tarefa',texto:'Qual é o serviço?',opcoes:[{id:'prateleira',rotulo:'Prateleira ou quadro'},{id:'fechadura',rotulo:'Fechadura ou maçaneta'},{id:'porta',rotulo:'Porta ou janela'},{id:'montagem',rotulo:'Pequena montagem'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'tempo',texto:'Quanto tempo deve levar?',opcoes:[{id:'ate2h',rotulo:'Até 2 horas'},{id:'meio_dia',rotulo:'Meio dia'},{id:'dia',rotulo:'O dia todo'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  reforma:[
    {id:'tipo',texto:'O que vai ser feito?',opcoes:[{id:'parede',rotulo:'Quebrar ou levantar parede'},{id:'piso',rotulo:'Piso ou azulejo'},{id:'reboco',rotulo:'Reboco ou muro'},{id:'banheiro',rotulo:'Reforma de banheiro'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'tamanho',texto:'Qual o tamanho aproximado?',opcoes:[{id:'um_comodo',rotulo:'Um cômodo'},{id:'dois_tres',rotulo:'De 2 a 3 cômodos'},{id:'casa',rotulo:'A casa toda'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  pintura:[
    {id:'tipo',texto:'O que pintar?',opcoes:[{id:'parede',rotulo:'Paredes'},{id:'teto',rotulo:'Teto'},{id:'portao',rotulo:'Portão, grade ou porta'},{id:'fachada',rotulo:'Fachada'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'comodos',texto:'Quantos cômodos?',opcoes:[{id:'um',rotulo:'Um cômodo'},{id:'dois_tres',rotulo:'De 2 a 3 cômodos'},{id:'casa',rotulo:'A casa toda'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  montagem:[
    {id:'tipo',texto:'O que precisa montar?',opcoes:[{id:'guarda_roupa',rotulo:'Guarda-roupa'},{id:'cama',rotulo:'Cama ou berço'},{id:'estante',rotulo:'Estante ou rack'},{id:'mesa',rotulo:'Mesa ou cadeira'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'pecas',texto:'As peças e o manual já estão com você?',opcoes:[{id:'sim',rotulo:'Sim'},{id:'nao',rotulo:'Não'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  ar:[
    {id:'servico',texto:'O que precisa?',opcoes:[{id:'instalar',rotulo:'Instalar'},{id:'manutencao',rotulo:'Limpeza ou manutenção'},{id:'conserto',rotulo:'Conserto'},{id:'desinstalar',rotulo:'Desinstalar ou mudar'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'aparelhos',texto:'Quantos aparelhos?',opcoes:[{id:'um',rotulo:'Um'},{id:'dois',rotulo:'Dois'},{id:'tres',rotulo:'Três ou mais'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  limpeza:[
    {id:'tipo',texto:'Que tipo de limpeza?',opcoes:[{id:'rotina',rotulo:'Rotina'},{id:'pesada',rotulo:'Pesada ou geral'},{id:'pos_obra',rotulo:'Depois de obra'},{id:'vidros',rotulo:'Vidros e janelas'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'tamanho',texto:'Tamanho do imóvel?',opcoes:[{id:'ate2',rotulo:'Até 2 cômodos'},{id:'tres_cinco',rotulo:'De 3 a 5 cômodos'},{id:'grande',rotulo:'Mais de 5 cômodos'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  frete:[
    {id:'itens',texto:'O que vai transportar?',opcoes:[{id:'pequenos',rotulo:'Caixas e pequenos volumes'},{id:'moveis',rotulo:'Móveis'},{id:'eletro',rotulo:'Eletrodomésticos'},{id:'mudanca',rotulo:'Mudança completa'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'ajudantes',texto:'Precisa de ajudantes para carregar?',opcoes:[{id:'sim',rotulo:'Sim'},{id:'nao',rotulo:'Não'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  chaveiro:[
    {id:'tipo',texto:'O que precisa?',opcoes:[{id:'abrir',rotulo:'Abrir porta'},{id:'trocar',rotulo:'Trocar fechadura'},{id:'copia',rotulo:'Cópia de chave'},{id:'cofre',rotulo:'Cofre'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'urgencia',texto:'Precisa agora?',opcoes:[{id:'agora',rotulo:'Sim, estou trancado do lado de fora'},{id:'depois',rotulo:'Pode ser depois'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  desentupidora:[
    {id:'local',texto:'Onde entupiu?',opcoes:[{id:'pia',rotulo:'Pia ou ralo'},{id:'vaso',rotulo:'Vaso sanitário'},{id:'esgoto',rotulo:'Esgoto ou caixa de gordura'},{id:'caixa',rotulo:'Caixa de inspeção'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'sintoma',texto:'O que está acontecendo?',opcoes:[{id:'transborda',rotulo:'Transbordando'},{id:'cheiro',rotulo:'Mau cheiro'},{id:'lento',rotulo:'Só escoa devagar'},{id:'outro',rotulo:'Não sei / Outro'}]}],
  eletro:[
    {id:'aparelho',texto:'Qual aparelho?',opcoes:[{id:'geladeira',rotulo:'Geladeira ou freezer'},{id:'lavadora',rotulo:'Máquina de lavar'},{id:'fogao',rotulo:'Fogão'},{id:'micro',rotulo:'Micro-ondas'},{id:'outro',rotulo:'Não sei / Outro'}]},
    {id:'problema',texto:'O que acontece?',opcoes:[{id:'nao_liga',rotulo:'Não liga'},{id:'barulho',rotulo:'Faz barulho'},{id:'vaza',rotulo:'Vaza água ou gás'},{id:'nao_esfria',rotulo:'Não esfria ou não lava'},{id:'outro',rotulo:'Não sei / Outro'}]}]
};
/* Perguntas do serviço; service vem da URL em ?servico=, então não pode cair em propriedades herdadas. */
const perguntasDoServico=service=>Object.hasOwn(PERGUNTAS_SERVICO,service)?PERGUNTAS_SERVICO[service]:[];
/* Pares [pergunta, rótulo] que o cliente respondeu e que ainda existem no catálogo. */
function respostasConhecidas(service,r){
  return perguntasDoServico(service).map(p=>{const o=p.opcoes.find(x=>x.id===(r||{})[p.id]);return o?[p.texto,o.rotulo]:null;}).filter(Boolean);
}
/* Para o profissional: "Pergunta: Rótulo", uma por linha. */
function textoRespostas(service,r){return respostasConhecidas(service,r).map(x=>esc(x[0])+': '+esc(x[1])).join('<br>');}
