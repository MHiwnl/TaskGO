package com.taskgo.api;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/* Fotos do pedido e da conversa: recebe a imagem, recodifica em JPEG (sem metadados), limita o tamanho e controla quem vê. */
@Service
public class FotoPedidoService {
    static final int LADO_MAX = 1600, MAX_POR_PEDIDO = 10;
    static final long BYTES_MAX = 1_500_000;
    private final FotoPedidoRepository fotos;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;

    public FotoPedidoService(FotoPedidoRepository fotos, PedidoContratadoRepository pedidos, UsuarioRepository usuarios) {
        this.fotos = fotos; this.pedidos = pedidos; this.usuarios = usuarios;
    }

    record Imagem(byte[] jpeg, int largura, int altura) {}

    /* data:image/...;base64,... → JPEG recodificado, no máximo 1600 px no lado maior. */
    static Imagem processar(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/") || !dataUrl.contains(";base64,"))
            throw erro(HttpStatus.BAD_REQUEST, "Envie a foto como imagem (JPEG ou PNG).");
        byte[] bruto;
        try { bruto = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1)); }
        catch (IllegalArgumentException e) { throw erro(HttpStatus.BAD_REQUEST, "Foto corrompida. Tente de novo."); }
        if (bruto.length > BYTES_MAX) throw erro(HttpStatus.PAYLOAD_TOO_LARGE, "Foto grande demais (máximo 1,5 MB).");
        BufferedImage img;
        try { img = ImageIO.read(new ByteArrayInputStream(bruto)); } catch (Exception e) { img = null; }
        if (img == null) throw erro(HttpStatus.BAD_REQUEST, "Formato não aceito. Use JPEG ou PNG.");
        if ((long) img.getWidth() * img.getHeight() > 40_000_000L) throw erro(HttpStatus.BAD_REQUEST, "Foto com resolução alta demais.");
        double escala = Math.min(1.0, (double) LADO_MAX / Math.max(img.getWidth(), img.getHeight()));
        int w = Math.max(1, (int) Math.round(img.getWidth() * escala)), h = Math.max(1, (int) Math.round(img.getHeight() * escala));
        BufferedImage saida = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE); g.fillRect(0, 0, w, h);
        g.drawImage(img, 0, 0, w, h, null); g.dispose();
        try {
            ImageWriter wr = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam p = wr.getDefaultWriteParam(); p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionQuality(0.82f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) { wr.setOutput(ios); wr.write(null, new IIOImage(saida, null, null), p); }
            wr.dispose();
            return new Imagem(out.toByteArray(), w, h);
        } catch (Exception e) { throw erro(HttpStatus.BAD_REQUEST, "Não foi possível processar a foto."); }
    }

    @Transactional
    public FotoPedido salvar(String pedidoId, Long autorId, String contexto, String dataUrl) {
        Imagem i = processar(dataUrl);
        return fotos.save(new FotoPedido(UUID.randomUUID().toString(), pedidoId, autorId, contexto, "image/jpeg", i.largura(), i.altura(), i.jpeg(), System.currentTimeMillis()));
    }

    /* Foto do pedido: cliente ou profissional do atendimento; até 10 por pedido. */
    @Transactional
    public FotoPedidoRepository.Resumo adicionarAoPedido(String pedidoId, TaskgoPrincipal quem, String dataUrl) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!participante(p, quem)) throw erro(HttpStatus.FORBIDDEN, "Só quem participa do pedido pode enviar fotos.");
        /* Fotos do pedido são do cliente; quem atende manda fotos pela conversa (ou, na obra, na entrega de cada etapa). */
        if (!String.valueOf(quem.id()).equals(p.getClientId()) && !quem.email().equalsIgnoreCase(String.valueOf(p.getClientEmail())))
            throw erro(HttpStatus.FORBIDDEN, "As fotos do pedido são enviadas pelo cliente.");
        if ("cancelado".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Este pedido foi cancelado.");
        if ("concluido".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Este pedido já foi concluído. As fotos ficam como estão.");
        if (fotos.countByPedidoIdAndContexto(pedidoId, "pedido") >= MAX_POR_PEDIDO) throw erro(HttpStatus.CONFLICT, "Este pedido já tem 10 fotos.");
        FotoPedido f = salvar(pedidoId, quem.id(), "pedido", dataUrl);
        return new FotoPedidoRepository.Resumo(f.getId(), f.getAutorId(), f.getLargura(), f.getAltura(), f.getCriadaEm());
    }

    @Transactional(readOnly = true)
    public java.util.List<FotoPedidoRepository.Resumo> doPedido(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!podeVerDoPedido(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        return fotos.resumos(pedidoId, "pedido");
    }

    @Transactional(readOnly = true)
    public FotoPedido abrir(String fotoId, TaskgoPrincipal quem) {
        FotoPedido f = fotos.findById(fotoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Foto não encontrada."));
        Pedido p = pedidos.findById(f.getPedidoId()).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Foto não encontrada."));
        boolean pode = "mensagem".equals(f.getContexto()) ? participante(p, quem) || equipe(quem) : podeVerDoPedido(p, quem);
        if (!pode) throw erro(HttpStatus.NOT_FOUND, "Foto não encontrada.");
        if (f.getDados() == null) throw erro(HttpStatus.NOT_FOUND, "Foto não encontrada."); /* carrega os bytes dentro da transação */
        return f;
    }

    @Transactional
    public void apagar(String fotoId, TaskgoPrincipal quem) {
        FotoPedido f = fotos.findById(fotoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Foto não encontrada."));
        if (!"pedido".equals(f.getContexto()) || !f.getAutorId().equals(quem.id())) throw erro(HttpStatus.FORBIDDEN, "Só quem enviou pode apagar esta foto.");
        /* Encerrado o pedido, as fotos viram histórico do serviço: ninguém apaga. */
        pedidos.findById(f.getPedidoId()).filter(p -> java.util.Set.of("concluido", "cancelado").contains(p.getStatus()))
                .ifPresent(p -> { throw erro(HttpStatus.CONFLICT, "O pedido já terminou: as fotos ficam guardadas no histórico do serviço."); });
        fotos.delete(f);
    }

    /* Do pedido: quem participa, a equipe e, enquanto o pedido está aberto, os profissionais da categoria. */
    private ServicosProfissional servicosPro;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setServicosPro(ServicosProfissional s) { this.servicosPro = s; }
    boolean podeVerDoPedido(Pedido p, TaskgoPrincipal quem) {
        if (participante(p, quem) || equipe(quem)) return true;
        if (AjudaRapida.eh(p)) return p.getProEmail() == null && ChamarAgoraService.convidado(p, quem.id());
        return "profissional".equalsIgnoreCase(quem.tipo()) && "aguardando".equals(p.getStatus()) && p.getProEmail() == null
                && usuarios.findById(quem.id()).map(u -> servicosPro != null ? servicosPro.faz(u, p.getService()) : u.getService() != null && u.getService().equalsIgnoreCase(String.valueOf(p.getService()))).orElse(false);
    }
    static boolean participante(Pedido p, TaskgoPrincipal quem) {
        return quem.email().equalsIgnoreCase(String.valueOf(p.getClientEmail())) || quem.email().equalsIgnoreCase(String.valueOf(p.getProEmail()));
    }
    static boolean equipe(TaskgoPrincipal quem) { return PapelEquipe.equipe(quem.tipo()); }
    private static ResponseStatusException erro(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
}
