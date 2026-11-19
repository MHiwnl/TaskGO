/* ---------- DADOS ---------- */
/* Uma lista de <option> agrupada pelos grupos do catálogo. */
function opcoesServicos(atual){return GRUPOS_SERVICOS.map(g=>'<optgroup label="'+g[0]+'">'+g[1].map(id=>{const x=SERVICES.find(y=>y.id===id);return '<option value="'+id+'"'+(atual===id?' selected':'')+'>'+x.n+'</option>';}).join('')+'</optgroup>').join('');}
/* Ajuda rápida: não é categoria de profissional (não entra nas listas de serviços), mas tem nome e ícone próprios. */
const AJUDA_RAPIDA={id:'ajuda',n:'Ajuda rápida',ico:'boia',d:'Uma mão agora, de alguém verificado por perto: pneu furado, bateria, carregar algo, porta trancada.',cat:'Dia a dia'};
const svc=id=>id==='ajuda'?AJUDA_RAPIDA:SERVICES.find(s=>s.id===id)||SERVICES[0];
/* Palavras que as pessoas usam para cada categoria: a busca procura nelas e o "Você quis dizer?" compara com elas. */

const PRO_NAMES=[
  ['Carlos Andrade','eletrica',4.9,312,'Recife, PE'],['Marcos Vinícius','ar',4.8,207,'Olinda, PE'],
  ['Juliana Ramos','limpeza',5.0,418,'Recife, PE'],['Rafael Souza','hidraulica',4.7,163,'Jaboatão, PE'],
  ['Patrícia Lima','limpeza',4.9,289,'Recife, PE'],['Diego Ferreira','montagem',4.8,196,'Camaragibe, PE'],
  ['Anderson Cruz','reforma',4.6,131,'Recife, PE'],['Bruno Tavares','eletro',4.9,242,'Olinda, PE'],
  ['Felipe Nogueira','ti',4.8,178,'Recife, PE'],['Sandra Mendes','jardim',4.7,94,'Paulista, PE'],
  ['Thiago Barbosa','frete',4.8,221,'Recife, PE'],['Lucas Pereira','cftv',4.9,154,'Recife, PE'],
  ['Renata Alves','pintura',4.8,118,'Jaboatão, PE'],['Gustavo Rocha','marido',4.9,367,'Recife, PE'],
  ['Eduardo Martins','dedetiza',4.7,86,'Olinda, PE'],
  ['André Lima','eletrica',4.8,143,'Recife, PE'],['Paulo Henrique','eletrica',4.7,98,'Olinda, PE'],['Mateus Carvalho','eletrica',4.9,221,'Recife, PE'],
  ['Fábio Costa','ar',4.9,176,'Recife, PE'],['Eduardo Nascimento','ar',4.7,112,'Jaboatão, PE'],['Vinícius Barros','ar',5.0,263,'Olinda, PE'],
  ['João Pedro','hidraulica',4.8,137,'Recife, PE'],['Bruno Almeida','hidraulica',4.9,205,'Olinda, PE'],['Ricardo Santos','hidraulica',4.6,81,'Jaboatão, PE'],
  ['Beatriz Monteiro','limpeza',4.8,191,'Olinda, PE'],['Larissa Costa','limpeza',4.9,276,'Recife, PE'],
  ['Gabriel Rocha','pintura',4.8,119,'Recife, PE'],['Pedro Martins','pintura',4.9,204,'Olinda, PE'],['Thiago Oliveira','pintura',4.7,93,'Jaboatão, PE'],
  ['Daniel Ferreira','montagem',4.9,183,'Recife, PE'],['Leandro Souza','montagem',4.7,106,'Olinda, PE'],['Igor Mendes','montagem',4.8,152,'Jaboatão, PE'],
  ['Marcelo Santos','reforma',4.8,144,'Recife, PE'],['João Victor','reforma',4.9,217,'Olinda, PE'],['André Luiz','reforma',4.6,79,'Jaboatão, PE'],
  ['Vitor Hugo','jardim',4.9,189,'Recife, PE'],['Caio Nunes','jardim',4.7,101,'Olinda, PE'],['Felipe Costa','jardim',4.8,133,'Jaboatão, PE'],
  ['Rodrigo Alves','marido',4.8,158,'Recife, PE'],['Samuel Ribeiro','marido',4.9,234,'Olinda, PE'],['Alexandre Lima','marido',4.7,97,'Jaboatão, PE'],
  ['Henrique Gomes','eletro',4.9,214,'Recife, PE'],['João Carlos','eletro',4.7,118,'Olinda, PE'],['Renan Silva','eletro',4.8,164,'Jaboatão, PE'],
  ['Marcos Oliveira','frete',4.9,245,'Recife, PE'],['José Carlos','frete',4.7,127,'Olinda, PE'],['Wesley Santos','frete',4.8,193,'Jaboatão, PE'],
  ['Rafael Lima','dedetiza',4.8,132,'Recife, PE'],['Diego Costa','dedetiza',4.9,198,'Olinda, PE'],['Fernando Alves','dedetiza',4.7,88,'Jaboatão, PE'],
  ['Pedro Henrique','ti',4.9,223,'Recife, PE'],['Lucas Martins','ti',4.7,115,'Olinda, PE'],['Gustavo Silva','ti',4.8,172,'Jaboatão, PE'],
  ['Luiz Felipe','cftv',4.9,187,'Recife, PE'],['Carlos Eduardo','cftv',4.8,146,'Olinda, PE'],['André Rodrigues','cftv',4.7,103,'Jaboatão, PE']
];
function buildPros(){
  return PRO_NAMES.map((p,i)=>{
    const service=svc(p[1]);
    return {
    id:'pro'+i,name:p[0],service:p[1],rating:p[2],reviews:p[3],city:p[4],
    price:rand(service.min,service.max),dist:+(Math.random()*8+.4).toFixed(1),eta:rand(12,55),
    verified:Math.random()>.2,jobs:rand(60,900),since:rand(2018,2024),
    bio:'Perfil fictício do modo demonstração: '+svc(p[1]).n.toLowerCase()+'.',
    skills:['Orçamento grátis','Atende aos sábados','Leva as ferramentas','Atende em Recife'].slice(0,rand(2,4))
    };
  });
}

const DEMO_MODE=TASKGO_DEMO_MODE;
function seedDemo(){
  const now=Date.now(),d=864e5;
  const demoClient={
    id:'u_cliente',role:'cliente',name:'Ana Beatriz Souza',email:'cliente@taskgo.com',
    cpf:'123.456.789-00',phone:'(81) 99876-5432',photo:null,city:'Recife, PE',
    address:'Rua das Graças, 320 — Boa Viagem, Recife/PE',bio:'',
    balance:184.50,pending:0,rating:4.9,since:now-420*d,plan:'free',refCode:'ANA-TG482',
    pixKey:'cliente@taskgo.com',notif:{push:true,email:true,promo:false,sms:true},twofa:false
  };
  const demoPro={
    id:'u_pro',role:'profissional',name:'Carlos Andrade',email:'pro@taskgo.com',
    cpf:'987.654.321-00',phone:'(81) 98811-2233',photo:null,city:'Recife, PE',
    address:'Av. Conde da Boa Vista, 1200 — Recife/PE',bio:'Eletricista há 12 anos, especialista em quadros e automação residencial.',
    balance:2840.75,pending:620.00,rating:4.9,since:now-980*d,plan:'plus',refCode:'CARLOS-TG119',
    pixKey:'987.654.321-00',notif:{push:true,email:true,promo:true,sms:false},twofa:true,
    service:'eletrica',verified:true,jobs:312,acceptRadius:12,proId:'pro0',
    available:false,faceVerified:false,lastFace:null
  };
  const clients=[
    {id:'c1',name:'Roberto Farias',address:'Rua Sete de Setembro, 145 — Espinheiro, Recife/PE',phone:'(81) 99123-4455',rating:4.8,jobs:14,since:now-300*d},
    {id:'c2',name:'Camila Ribeiro',address:'Av. Boa Viagem, 3200 — Boa Viagem, Recife/PE',phone:'(81) 98822-3311',rating:5.0,jobs:7,since:now-180*d},
    {id:'c3',name:'Condomínio Aurora',address:'Rua Padre Carapuceiro, 890 — Pina, Recife/PE',phone:'(81) 3221-7788',rating:4.9,jobs:23,since:now-640*d},
    {id:'c4',name:'Juliana Prado',address:'Rua Benfica, 77 — Madalena, Recife/PE',phone:'(81) 99655-1020',rating:4.7,jobs:3,since:now-90*d}
  ];
  const pros=buildPros();
  const mk=(o)=>Object.assign({
    id:'o_'+uid(),code:String(rand(1000,9999)),created:now,status:'concluido',
    price:180,service:'eletrica',proId:'pro0',clientId:'u_cliente',
    address:'Rua das Graças, 320 — Boa Viagem, Recife/PE',rating:null,notes:'',paid:'pix',
    dist:2.4,eta:18,progress:1
  },o);
  const orders=[
    mk({id:'o_live',status:'a_caminho',service:'ar',proId:'pro1',price:320,created:now-15*6e4,dist:4.2,eta:14,progress:.12,code:String(rand(1000,9999)),notes:'Higienização de 2 splits 12.000 BTUs.'}),
    mk({id:'o_1',status:'agendado',service:'limpeza',proId:'pro2',price:210,created:now-2*36e5,sched:now+2*d,notes:'Faxina completa, apartamento 3 quartos.'}),
    mk({id:'o_2',status:'concluido',service:'hidraulica',proId:'pro3',price:260,created:now-9*d,rating:5,notes:'Troca de registro do chuveiro.'}),
    mk({id:'o_3',status:'concluido',service:'montagem',proId:'pro5',price:150,created:now-21*d,rating:4}),
    mk({id:'o_4',status:'cancelado',service:'ti',proId:'pro8',price:120,created:now-30*d}),
    mk({id:'o_5',status:'concluido',service:'eletrica',proId:'pro0',price:340,created:now-46*d,rating:5,comment:'Resolveu um problema que dois eletricistas não conseguiram.'}),
    /* pedidos em que Carlos Andrade (pro0) é o profissional */
    mk({id:'o_p1',status:'a_caminho',service:'eletrica',proId:'pro0',clientId:'c1',address:clients[0].address,price:280,created:now-9*6e4,dist:3.6,eta:12,progress:.06,code:String(rand(1000,9999)),notes:'Disjuntor desarma sempre que liga o chuveiro.'}),
    mk({id:'o_p2',status:'agendado',service:'eletrica',proId:'pro0',clientId:'c3',address:clients[2].address,price:620,created:now-6*36e5,sched:now+d,notes:'Revisão do quadro elétrico da área comum.'}),
    mk({id:'o_p3',status:'concluido',service:'eletrica',proId:'pro0',clientId:'c2',address:clients[1].address,price:340,created:now-5*d,rating:5,comment:'Serviço impecável, chegou no horário e explicou tudo. Recomendo!'}),
    mk({id:'o_p4',status:'concluido',service:'eletrica',proId:'pro0',clientId:'c4',address:clients[3].address,price:190,created:now-12*d,rating:4,comment:'Bom atendimento, só demorou um pouco mais que o previsto.'}),
    mk({id:'o_p5',status:'cancelado',service:'eletrica',proId:'pro0',clientId:'c1',address:clients[0].address,price:150,created:now-19*d})
  ];
  const tx=[
    {id:'t1',ownerId:'u_pro',type:'in',label:'Pagamento recebido — Elétrica',amount:340,ts:now-46*d,method:'PIX',status:'ok'},
    {id:'t2',ownerId:'u_pro',type:'out',label:'Saque para chave PIX',amount:500,ts:now-30*d,method:'PIX',status:'ok'},
    {id:'t3',ownerId:'u_pro',type:'in',label:'Pagamento recebido — Ar-condicionado',amount:620,ts:now-12*d,method:'PIX',status:'pendente'},
    {id:'t4',ownerId:'u_cliente',type:'in',label:'Bônus de indicação',amount:50,ts:now-8*d,method:'TaskGO',status:'ok'},
    {id:'t5',ownerId:'u_pro',type:'in',label:'Pagamento recebido — Hidráulica',amount:260,ts:now-9*d,method:'Cartão',status:'ok'},
    {id:'t6',ownerId:'u_pro',type:'out',label:'Taxa de serviço TaskGO (12%)',amount:31.2,ts:now-9*d,method:'Automático',status:'ok'}
  ];
  const chats=[
    {id:'ch1',orderId:'o_live',msgs:[
      {me:false,t:'Boa tarde! Estou a caminho, chego em aproximadamente 15 minutos.',ts:now-9*6e4},
      {me:true,t:'Perfeito! O portão é o de número 320, pode tocar no interfone 402.',ts:now-8*6e4},
      {me:false,t:'Anotado. Vou precisar de acesso à área externa da condensadora.',ts:now-6*6e4}
    ]},
    {id:'ch2',orderId:'o_p1',msgs:[
      {me:false,t:'Bom dia! O disjuntor desarma sempre que ligo o chuveiro.',ts:now-22*6e4},
      {me:true,t:'Bom dia, Roberto. Provavelmente é dimensionamento do disjuntor. Já estou a caminho.',ts:now-18*6e4},
      {me:false,t:'Ótimo, estou em casa. Pode subir direto no apartamento 501.',ts:now-12*6e4}
    ]},
    {id:'ch3',orderId:'o_p2',msgs:[
      {me:false,t:'Confirmamos a visita de amanhã às 9h para o quadro da área comum.',ts:now-5*36e5},
      {me:true,t:'Confirmado. Vou levar o alicate amperímetro para medir a carga.',ts:now-4*36e5}
    ]}
  ];
  return {users:[demoClient,demoPro],session:null,pros,clients,orders,tx,chats,
    notifs:[
      {id:'n1',for:'cliente',t:'Seu profissional está a caminho',d:'Marcos Vinícius marcou que saiu para o seu endereço.',ts:now-6e5,read:false},
      {id:'n2',for:'cliente',t:'Pedido aceito',d:'Marcos Vinícius aceitou seu pedido de ar-condicionado.',ts:now-9e5,read:false},
      {id:'n3',for:'cliente',t:'Avalie seu último serviço',d:'Conte como foi o atendimento de Diego Ferreira.',ts:now-3*864e5,read:true},
      {id:'n4',for:'profissional',t:'Novo pedido na sua categoria',d:'Elétrica no Espinheiro · referência de R$ 280.',ts:now-10*6e4,read:false},
      {id:'n5',for:'profissional',t:'Atendimento concluído',d:'O atendimento de Camila Ribeiro foi marcado como concluído.',ts:now-2*864e5,read:false},
      {id:'n6',for:'profissional',t:'Nova avaliação recebida',d:'Camila Ribeiro deu 5 estrelas ao seu atendimento.',ts:now-5*864e5,read:true}
    ]};
}

function seed(){
  if(DEMO_MODE)return seedDemo();
  return {users:[],session:null,pros:[],clients:[],orders:[],tx:[],chats:[],notifs:[]};
}

