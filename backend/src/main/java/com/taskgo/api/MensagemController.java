package com.taskgo.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pedidos/contratados/{pedidoId}/mensagens")
public class MensagemController {
    private final PedidoContratadoRepository pedidos;
    private final MensagemRepository mensagens;
    private final UsuarioRepository usuarios;
    private final NotificacaoService notificacoes;
    private final ModeracaoService moderacao;
    private final EventosService eventos;

    public MensagemController(PedidoContratadoRepository pedidos, MensagemRepository mensagens, UsuarioRepository usuarios, NotificacaoService notificacoes,
                              ModeracaoService moderacao, EventosService eventos) {
        this.pedidos=pedidos; this.mensagens=mensagens; this.usuarios=usuarios; this.notificacoes=notificacoes; this.moderacao=moderacao; this.eventos=eventos;
    }

    @GetMapping
    @Transactional
    public List<MensagemDto> listar(@PathVariable String pedidoId, Authentication auth) {
        TaskgoPrincipal user=principal(auth); validarParticipante(pedidoId,user);
        List<Mensagem> historico=mensagens.findByPedidoIdOrderByCriadaEmAsc(pedidoId);
        long agora=System.currentTimeMillis();
        List<Mensagem> novasLidas=historico.stream().filter(m->user.id().equals(m.getDestinatarioId())&&m.getLidaEm()==null).toList();
        novasLidas.forEach(m->m.marcarLida(agora));
        if(!novasLidas.isEmpty())mensagens.saveAll(novasLidas);
        return historico.stream().map(MensagemDto::from).toList();
    }

    @PostMapping
    @Transactional
    public MensagemDto enviar(@PathVariable String pedidoId, @RequestBody NovaMensagem body, Authentication auth) {
        TaskgoPrincipal user=principal(auth); Pedido pedido=validarParticipante(pedidoId,user);
        /* A conversa existe só enquanto cliente e profissional estão conectados pelo atendimento; depois vira histórico. */
        if (!java.util.Set.of("a_caminho","chegou","em_andamento","agendado").contains(String.valueOf(pedido.getStatus()).toLowerCase(java.util.Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A conversa só está aberta durante o atendimento. Depois, fica só para consulta.");
        moderacao.exigirLiberado(user.id());
        boolean comFoto=body!=null&&body.foto()!=null&&!body.foto().isBlank();
        String texto=body==null||body.texto()==null?"":body.texto().trim();
        if (body==null || body.clientMessageId()==null || body.clientMessageId().isBlank() || body.clientMessageId().length()>80
                || (texto.isBlank() && !comFoto) || texto.length()>2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mensagem ou identificador inválido.");
        String motivo=texto.isBlank()?null:moderacao.motivoBloqueio(texto);
        if (motivo!=null) {
            ModeracaoService.Situacao s=moderacao.registrarInfracao(user.id(),motivo,pedidoId);
            eventos.enviar(user.id(),"moderacao",pedidoId);
            String punicao=s.banido()?"Com 3 avisos, a conta foi banida.":s.suspenso()?"Aviso 2 de 3: a conta fica suspensa por 24 horas.":"Aviso "+s.avisos()+" de 3.";
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Mensagem não enviada: ela tinha "+motivo+". Combinar contato, valores ou serviço por fora não é permitido. "+punicao+" Você pode recorrer da decisão.");
        }
        Mensagem existing=mensagens.findByAutorIdAndClientMessageId(user.id(),body.clientMessageId()).orElse(null);
        if(existing!=null){if(!existing.getPedidoId().equals(pedidoId))throw new ResponseStatusException(HttpStatus.CONFLICT);return MensagemDto.from(existing);}
        String emailDestino=user.email().equalsIgnoreCase(pedido.getClientEmail())?pedido.getProEmail():pedido.getClientEmail();
        Usuario destinatario=usuarios.findByEmailIgnoreCase(emailDestino==null?"":emailDestino).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Destinatario da conversa indisponivel."));
        long agora=System.currentTimeMillis();
        Mensagem nova=new Mensagem(java.util.UUID.randomUUID().toString(),pedidoId,user.id(),destinatario.getId(),body.clientMessageId(),texto,agora);
        if (comFoto) { if (fotos==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); nova.setFotoId(fotos.salvar(pedidoId,user.id(),"mensagem",body.foto()).getId()); }
        Mensagem saved=mensagens.save(nova);
        notificacoes.criar(destinatario.getId(),pedidoId,"mensagem","Nova mensagem",comFoto&&texto.isBlank()?"Você recebeu uma foto na conversa de um pedido.":"Você recebeu uma nova mensagem sobre um pedido.","mensagem:"+saved.getId());
        eventos.enviar(destinatario.getId(),"mensagem",pedidoId);
        return MensagemDto.from(saved);
    }

    private Pedido validarParticipante(String id,TaskgoPrincipal user){
        Pedido p=pedidos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean related=user.email().equalsIgnoreCase(p.getClientEmail()==null?"":p.getClientEmail())
                || user.email().equalsIgnoreCase(p.getProEmail()==null?"":p.getProEmail());
        if(!related)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return p;
    }
    private TaskgoPrincipal principal(Authentication a){if(a==null||!(a.getPrincipal() instanceof TaskgoPrincipal p))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return p;}
    public record NovaMensagem(String clientMessageId,String texto,String foto){public NovaMensagem(String clientMessageId,String texto){this(clientMessageId,texto,null);}}
    private FotoPedidoService fotos;
    @org.springframework.beans.factory.annotation.Autowired(required=false) void setFotos(FotoPedidoService fotos){this.fotos=fotos;}
    public record MensagemDto(String id,String pedidoId,Long autorId,Long destinatarioId,String texto,long criadaEm,Long lidaEm,boolean automatica,String fotoId){static MensagemDto from(Mensagem m){return new MensagemDto(m.getId(),m.getPedidoId(),m.getAutorId(),m.getDestinatarioId(),m.getTexto(),m.getCriadaEm(),m.getLidaEm(),m.isAutomatica(),m.getFotoId());}}
}
