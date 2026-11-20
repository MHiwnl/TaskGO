package com.taskgo.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PedidoService {
    private final PedidoContratadoRepository repository;
    private final UsuarioRepository usuarios;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    public PedidoService(PedidoContratadoRepository repository) { this(repository, null); }
    @Autowired
    public PedidoService(PedidoContratadoRepository repository, UsuarioRepository usuarios) { this.repository = repository; this.usuarios = usuarios; }

    @Transactional
    public Pedido criar(Pedido pedido) { return criar(pedido, false); }

    /* aguardarPagamento: o pedido nasce "aguardando_pagamento" e só procura profissional depois de pago. */
    @Transactional
    public Pedido criar(Pedido pedido, boolean aguardarPagamento) {
        if (pedido.getId() == null || pedido.getId().isBlank() || pedido.getClientEmail() == null || pedido.getClientEmail().isBlank()
                || pedido.getService() == null || pedido.getService().isBlank() || pedido.getPrice() == null || pedido.getPrice() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido incompleto: informe id, cliente, serviço e valor.");
        }
        /* O id vai para HTML, atributos onclick e planilhas: só letras, números, _ e -. */
        if (!pedido.getId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de pedido inválido.");
        /* Mesmo limite da tela: de R$ 1 a R$ 100.000, com no máximo 2 casas decimais. */
        double preco = pedido.getPrice();
        if (!(preco >= 1 && preco <= 100_000) || Math.abs(preco * 100 - Math.round(preco * 100)) > 1e-6)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um valor de R$ 1 a R$ 100.000, com até 2 casas decimais.");
        if (!AjudaRapida.eh(pedido) && !CatalogoServicos.existe(pedido.getService()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha um serviço do catálogo.");
        boolean obra = "obra".equals(pedido.getExtra().get("modo"));
        /* Serviço de um dia vai até R$ 2.999,99; a partir de R$ 3.000 (ou de vários dias), só por orçamento (obra em etapas). */
        if (!obra && preco >= 3000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A partir de R$ 3.000, peça orçamento: escolha Obra ou serviço de vários dias.");
        /* Dinheiro só abaixo de R$ 1.000. */
        if (!obra && preco >= 1000 && "dinheiro".equals(pedido.getExtra().get("formaPagamento")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A partir de R$ 1.000, pague com Pix, cartão ou a carteira TaskGO. Dinheiro vale só abaixo de R$ 1.000.");
        if (obra && (pedido.getNotes() == null || pedido.getNotes().trim().length() < 20))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Descreva a obra em pelo menos 20 letras: o que precisa ser feito, medidas e o que já existe.");
        if (AjudaRapida.eh(pedido)) {
            if (preco < AjudaRapida.MINIMO) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Na ajuda rápida, o valor mínimo é R$ 50.");
            if (!"agora".equals(pedido.getExtra().get("modo"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A ajuda rápida é sempre para agora.");
            if (pedido.getNotes() == null || pedido.getNotes().trim().length() < 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conte em poucas palavras do que você precisa.");
        }
        /* Serviço recorrente: o preço é o valor de cada vez; só serviços cobrados por dia ou visita, agendados e pagos pela TaskGO. */
        RecorrenteService.Plano plano = null;
        if (pedido.getExtra().get("recorrente") != null) {
            if (obra || AjudaRapida.eh(pedido) || "agora".equals(pedido.getExtra().get("modo")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O serviço recorrente é sempre agendado.");
            if ("dinheiro".equals(pedido.getExtra().get("formaPagamento")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No serviço recorrente, cada vez é paga por Pix ou pela TaskGO, não em dinheiro.");
            plano = RecorrenteService.plano(pedido.getExtra().get("recorrente"), pedido.getService(), java.time.LocalDate.now(RecorrenteService.FUSO));
        }
        if (repository.existsById(pedido.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido já cadastrado.");
        /* Na criação, só os campos extras que o cliente pode escolher; os demais (nota do cliente, chegada,
           ondas do Chamar Agora, pagamento...) só o servidor escreve. */
        Map<String,Object> extrasSeguros=sanitizarExtras(pedido.getExtra());
        extrasSeguros.keySet().retainAll(EXTRAS_DO_CLIENTE);
        Map<String,String> respostas=CatalogoServicos.respostasValidas(pedido.getService(),extrasSeguros.get("respostas"));
        extrasSeguros.remove("respostas"); if (respostas!=null) extrasSeguros.put("respostas",respostas);
        if (obra) {
            /* Obra: o valor informado é o teto do cliente; o preço final vem do orçamento aceito. Sem dinheiro. */
            extrasSeguros.put("obra", true); extrasSeguros.put("obraTeto", preco); extrasSeguros.remove("formaPagamento");
            extrasSeguros.remove("urgente"); extrasSeguros.put("quando", "obra");
        } else extrasSeguros.remove("inicioDesejado");
        extrasSeguros.remove("recorrente");
        if (plano != null) {
            extrasSeguros.put("recorrente", plano.comoExtra(preco)); if (!java.util.Set.of("pix_direto", "cartao_maquininha").contains(String.valueOf(extrasSeguros.get("formaPagamento")))) extrasSeguros.remove("formaPagamento"); extrasSeguros.remove("urgente");
            extrasSeguros.put("quando", "recorrente");
            pedido.setSched(RecorrenteService.quando(plano.datas().get(0), plano.hora().toString()));
        }
        pedido.getExtra().clear();pedido.getExtra().putAll(extrasSeguros);
        /* Ponto marcado no mapa: os dois números, dentro do globo, ou nenhum. */
        Object lat=pedido.getExtra().get("lat"), lng=pedido.getExtra().get("lng");
        if (lat!=null || lng!=null) {
            if (!(lat instanceof Number a) || !(lng instanceof Number b) || !(Math.abs(a.doubleValue())<=90) || !(Math.abs(b.doubleValue())<=180))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ponto do mapa inválido. Marque de novo o local do atendimento.");
        }
        pedido.setStatus(aguardarPagamento && plano == null ? "aguardando_pagamento" : "aguardando");
        /* O código de chegada nasce no servidor e só o cliente o vê (PedidoRespostaAdvice). */
        pedido.setCode(String.format("%04d", CODIGOS.nextInt(10000)));
        pedido.setCreated(System.currentTimeMillis());
        pedido.setAcceptedAt(null); pedido.setCompletedAt(null); pedido.setIniciadoEm(null); pedido.setPaid(null); pedido.setRating(null); pedido.setCancelReason(null);
        if (pedido.getProgress() == null) pedido.setProgress(0.0);
        return repository.save(pedido);
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarDoUsuario(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o e-mail da conta.");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return repository.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(normalized, normalized);
    }

    @Transactional(readOnly = true)
    public Page<Pedido> listarDisponiveis(String email, String service, Pageable pageable) {
        return service == null || service.isBlank()
                ? repository.encontrarDisponiveis("aguardando", pageable)
                : repository.encontrarDisponiveisPorServico("aguardando", service.trim(), pageable);
    }
    /* Oportunidades das categorias do profissional (a principal e as outras que ele marcou). */
    @Transactional(readOnly = true)
    public Page<Pedido> listarDisponiveis(java.util.Collection<String> servicos, Pageable pageable) {
        return repository.encontrarDisponiveisPorServicos("aguardando", servicos, pageable);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Pedido> encontrarDisponivel(String id) {
        return repository.findByIdAndStatusIgnoreCaseAndProEmailIsNull(id, "aguardando");
    }

    @Transactional
    public Pedido avaliar(String id, String email, Double rating, String comment) {
        if (rating == null || rating < 1 || rating > 5 || rating % 1 != 0 || comment != null && comment.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma nota inteira de 1 a 5 e um comentário de até 2000 caracteres.");
        Pedido pedido=repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido não encontrado."));
        String author=email.trim().toLowerCase(Locale.ROOT);
        boolean client=equalsEmail(author,pedido.getClientEmail()), professional=equalsEmail(author,pedido.getProEmail());
        if (!client&&!professional) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente participantes deste pedido podem avaliá-lo.");
        if (!"concluido".equalsIgnoreCase(pedido.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"O pedido precisa estar concluído.");
        if(client){
            if(pedido.getRating()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Este pedido já foi avaliado por este cliente.");
            pedido.setRating(rating);pedido.getExtra().put("comment",comment==null?"":comment.trim());
        }else{
            if(pedido.getExtra().get("clientRating")!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Este pedido já foi avaliado por este profissional.");
            pedido.getExtra().put("clientRating",rating);pedido.getExtra().put("clientComment",comment==null?"":comment.trim());
        }
        return repository.save(pedido);
    }

    @Transactional
    public Pedido aceitar(String id, String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a conta profissional.");
        if (usuarios == null) throw new IllegalStateException("Repositório de usuários não configurado.");
        Usuario profissional = usuarios.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta profissional não encontrada."));
        boolean ajuda = repository.findById(id).map(AjudaRapida::eh).orElse(false);
        if (ajuda) {
            if (!AjudaRapida.podeAjudar(profissional)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ligue a ajuda rápida, com a identidade verificada, para aceitar.");
            if (repository.findById(id).map(p -> equalsEmail(profissional.getEmail(), p.getClientEmail())).orElse(false))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Você não pode aceitar o seu próprio pedido.");
        } else if (!"profissional".equalsIgnoreCase(profissional.getTipo()) || profissional.getProId() == null || profissional.getProId().isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente uma conta profissional válida pode aceitar pedidos.");
        int atualizados = repository.aceitarSeDisponivel(id, profissional.getEmail().trim().toLowerCase(Locale.ROOT),
                AjudaRapida.idAjudante(profissional), profissional.getNome(), System.currentTimeMillis());
        if (atualizados == 0) {
            if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido não está mais disponível.");
        }
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    }

    /* noRollbackFor: a recusa do código grava a contagem de erros; todas as recusas acontecem antes de qualquer mudança no pedido. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Pedido atualizar(String id, String email, Pedido atualizacao) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o e-mail da conta.");
        Pedido pedido = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        /* Linha travada: o contador do código de chegada não é furado por PUTs em paralelo, e cancelar e chegar não se sobrescrevem. */
        if (em != null) em.refresh(pedido, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        String usuario = email.trim().toLowerCase(Locale.ROOT);
        boolean cliente = equalsEmail(usuario, pedido.getClientEmail());
        boolean profissional = equalsEmail(usuario, pedido.getProEmail());
        if (!cliente && !profissional) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este pedido não pertence a esta conta.");
        String novoStatus = atualizacao.getStatus();
        if (ObraService.eh(pedido) && novoStatus != null && !novoStatus.equals(pedido.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A obra avança pelas etapas. Para cancelar, use Cancelar obra.");
        if (RecorrenteService.eh(pedido) && novoStatus != null && !novoStatus.equals(pedido.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O serviço recorrente avança pelas vezes. Para cancelar, use Cancelar.");
        if (cliente && novoStatus != null && !"cancelado".equals(novoStatus) && !novoStatus.equals(pedido.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O cliente só pode cancelar o próprio pedido.");
        if (novoStatus != null && !novoStatus.equals(pedido.getStatus()) && !transicaoPermitida(pedido.getStatus(), novoStatus))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transição de status inválida.");
        if ("chegou".equals(novoStatus) && "pendente".equals(pedido.getExtra().get("confirmacaoCliente")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Espere o cliente confirmar você antes de ir.");
        if ("chegou".equals(novoStatus) && Boolean.TRUE.equals(pedido.getExtra().get("pagamentoPendente")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Espere o cliente pagar a diferença do valor combinado.");
        if ("em_andamento".equals(novoStatus) && !"em_andamento".equals(pedido.getStatus())) {
            if (!profissional) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só o profissional inicia o serviço, com o código do cliente.");
            long agora = System.currentTimeMillis();
            if (LimiteErros.esperaMs(pedido.getExtra(), ERROS_CODIGO, agora) > 0)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Código errado muitas vezes. Espere 15 minutos e confira o código com o cliente.");
            if (pedido.getCode() == null || !pedido.getCode().equals(atualizacao.getCode())) {
                LimiteErros.errou(pedido.getExtra(), ERROS_CODIGO, MAX_ERROS_CODIGO, JANELA_CODIGO_MS, JANELA_CODIGO_MS, agora);
                repository.save(pedido);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Código incorreto. Peça ao cliente para conferir o código no aplicativo dele.");
            }
        }
        /* Início real do serviço: gravado uma vez, na primeira vez que o código de chegada é aceito. */
        if ("em_andamento".equals(novoStatus) && !"em_andamento".equals(pedido.getStatus()) && pedido.getIniciadoEm() == null) pedido.setIniciadoEm(System.currentTimeMillis());
        if (novoStatus != null) pedido.setStatus(novoStatus);
        if ("a_caminho".equals(novoStatus) && pedido.getAcceptedAt() == null) pedido.setAcceptedAt(System.currentTimeMillis());
        if (atualizacao.getProgress() != null && profissional) pedido.setProgress(atualizacao.getProgress());
        /* Nota só pela rota de avaliar (faixa, uma vez, só quem participou). */
        if (atualizacao.getCancelReason() != null && "cancelado".equals(pedido.getStatus())) pedido.setCancelReason(atualizacao.getCancelReason());
        if ("concluido".equals(novoStatus) && pedido.getCompletedAt() == null) pedido.setCompletedAt(System.currentTimeMillis());
        return repository.save(pedido);
    }

    static final Set<String> EXTRAS_DO_CLIENTE = Set.of("lat", "lng", "bairro", "urgente", "quando", "modo", "clientName", "formaPagamento", "inicioDesejado", "respostas");
    private static final java.security.SecureRandom CODIGOS = new java.security.SecureRandom();
    /* Erros do código de chegada, no extra do pedido: 5 seguidos bloqueiam a chegada por 15 min. */
    static final String ERROS_CODIGO = "codigoChegadaErros";
    static final int MAX_ERROS_CODIGO = 5;
    static final long JANELA_CODIGO_MS = 15 * 60_000L;
    private boolean equalsEmail(String a, String b) { return b != null && a.equals(b.trim().toLowerCase(Locale.ROOT)); }
    private Map<String, Object> sanitizarExtras(Map<String, ?> dados) {
        Map<String, Object> safe = new LinkedHashMap<>(); if (dados == null) return safe;
        Set<String> bloqueadas = Set.of("pass", "password", "senha", "passwordhash", "token", "accesstoken", "refreshtoken");
        dados.forEach((key, value) -> {
            String normalized=key.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (bloqueadas.contains(normalized)) return;
            if (value instanceof Map<?, ?> map) { Map<String,Object> nested=new LinkedHashMap<>();map.forEach((k,v)->nested.put(String.valueOf(k),v));safe.put(key,sanitizarExtras(nested)); }
            else if (value instanceof List<?> list) { List<Object> nested=new ArrayList<>();for(Object item:list){if(item instanceof Map<?,?> map){Map<String,Object> child=new LinkedHashMap<>();map.forEach((k,v)->child.put(String.valueOf(k),v));nested.add(sanitizarExtras(child));}else nested.add(item);}safe.put(key,nested); }
            else safe.put(key,value);
        });
        return safe;
    }
    private boolean transicaoPermitida(String atual, String novo) {
        if ("cancelado".equals(novo)) return !"concluido".equals(atual) && !"cancelado".equals(atual);
        return switch (atual == null ? "" : atual) {
            case "aguardando" -> "a_caminho".equals(novo);
            case "a_caminho" -> "chegou".equals(novo);
            case "chegou" -> "em_andamento".equals(novo);
            case "em_andamento" -> "concluido".equals(novo);
            default -> false;
        };
    }
}

