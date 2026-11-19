const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

test('Store sanitiza senha, token e identificador de sessão do armazenamento local',()=>{
  /* A demonstração só guarda as contas fictícias; qualquer segredo nelas some ao salvar. */
  const persisted={session:'u_cliente',users:[{id:'u_cliente',email:'ana@example.com',pass:'plain',senha:'plain',accessToken:'secret'},{id:'u_pro',email:'carlos@example.com',password:'plain'}],pros:[],clients:[],orders:[],tx:[],chats:[],notifs:[]};
  const localStorage={value:JSON.stringify(persisted),getItem(){return this.value;},setItem(_key,value){this.value=value;}};
  const context={
    window:{fetch:async()=>({}),__taskgoHydrating:true,TASKGO_CONFIG:{apiBaseUrl:''}},
    location:{href:'http://localhost:5500/',origin:'http://localhost:5500',hostname:'localhost',protocol:'http:',search:''},
    document:{cookie:''},
    localStorage,
    seed:()=>({session:null,users:[],pros:[],clients:[],orders:[],tx:[],chats:[],notifs:[]}),
    DEMO_MODE:true,
    console,URL,URLSearchParams,Headers,Request
  };
  const config=fs.readFileSync('js/core/config.js','utf8');
  vm.runInNewContext(config+'\n'+fs.readFileSync('js/core/storage.js','utf8')+'; Store.load(); Store.save();',context);
  const result=JSON.parse(localStorage.value);
  assert.equal(result.session,null);
  assert.deepEqual(result.users.map(u=>Object.keys(u).sort()),[['email','id'],['email','id']]);
  assert.doesNotMatch(localStorage.value,/plain|secret|accessToken|senha|password/);
});
