package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/* Falhas altas da auditoria: imagem bomba, categoria do profissional e tentativas sem limite. */
class AuditoriaAltaTest {

    /* PNG só com cabeçalho: declara a resolução e não traz pixels, então só o cabeçalho é lido. */
    private static String pngDeclarado(int largura, int altura) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        chunk(out, "IHDR", ByteBuffer.allocate(13).putInt(largura).putInt(altura).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array());
        chunk(out, "IEND", new byte[0]);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static void chunk(ByteArrayOutputStream out, String tipo, byte[] dados) {
        byte[] nome = tipo.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32(); crc.update(nome); crc.update(dados);
        out.writeBytes(ByteBuffer.allocate(4).putInt(dados.length).array());
        out.writeBytes(nome); out.writeBytes(dados);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static UsernamePasswordAuthenticationToken logado(long id, String email, String tipo) {
        return new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(id, email, "Nome", tipo, null), null, List.of());
    }

    @Test void imagemComResolucaoAltaRecusadaAntesDeDecodificar() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> FotoPedidoService.processar(pngDeclarado(8000, 6000)));
        assertEquals(400, e.getStatusCode().value());
        assertEquals("Foto com resolução alta demais.", e.getReason());
    }

    @Test void fotoComumContinuaAceita() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", png);
        FotoPedidoService.Imagem i = FotoPedidoService.processar("data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
        assertEquals(20, i.largura()); assertEquals(10, i.altura());
    }

    @Test void profissionalDeOutraCategoriaNaoEntraNoPedidoNemNaProposta() {
        Usuario pro = new Usuario(); pro.setId(2L); pro.setNome("Pro"); pro.setEmail("pro@x.com"); pro.setTipo("profissional"); pro.setProId("pro_2"); pro.setService("hidraulica");
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.findByEmailIgnoreCase("pro@x.com")).thenReturn(Optional.of(pro));
        when(usuarios.findById(2L)).thenReturn(Optional.of(pro));
        ServicosProfissional servicos = mock(ServicosProfissional.class);
        when(servicos.faz(pro, "eletrica")).thenReturn(false);

        PedidoService service = mock(PedidoService.class);
        PedidoContratadoController c = new PedidoContratadoController(service, usuarios, mock(LocalizacaoPedidoService.class),
                mock(ModeracaoService.class), mock(EventosService.class), mock(PedidoContratadoRepository.class));
        c.setServicosPro(servicos);
        Pedido pedido = new Pedido(); pedido.setService("eletrica"); pedido.setPrice(150.0); pedido.setProEmail("pro@x.com");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> c.criar(pedido, logado(1L, "cli@x.com", "cliente"))).getStatusCode().value());
        verify(service, never()).criar(any(), anyBoolean());

        PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
        Pedido aberto = new Pedido(); aberto.setId("o_2"); aberto.setStatus("aguardando"); aberto.setClientEmail("cli@x.com"); aberto.setService("eletrica"); aberto.setPrice(150.0);
        when(pedidos.findById("o_2")).thenReturn(Optional.of(aberto));
        PropostaController propostas = new PropostaController(mock(PropostaRepository.class), pedidos, usuarios,
                mock(NotificacaoService.class), mock(ModeracaoService.class), mock(EventosService.class));
        propostas.setServicosPro(servicos);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> propostas.enviar("o_2", new PropostaController.NovaProposta(150.0, null), logado(2L, "pro@x.com", "profissional"))).getStatusCode().value());
    }

    @Test void doisFatoresBloqueiaAposCincoErrosMesmoComCodigoCerto() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        SegurancaContaController s = new SegurancaContaController(mock(SessaoContaRepository.class), usuarios, new BCryptPasswordEncoder(), mock(ModeracaoService.class));
        Usuario u = new Usuario(); u.setId(1L); u.setEmail("ana@x.com");
        String segredo = Totp.novoSegredo();
        u.getExtra().put(SegurancaContaController.TOTP, segredo);
        for (int i = 0; i < 5; i++) assertFalse(s.conferirSegundaEtapa(u, "xxxx"));
        String certo = Totp.codigo(segredo, System.currentTimeMillis() / 30_000);
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> s.conferirSegundaEtapa(u, certo)).getStatusCode().value());
    }

    @Test void reenvioDoCodigoDeEmailNaoZeraAsTentativas() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        EmailRecuperacaoSenha email = mock(EmailRecuperacaoSenha.class);
        when(email.canalPronto()).thenReturn(true);
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        ConfirmacaoEmailService s = new ConfirmacaoEmailService(usuarios, email);
        Usuario ana = new Usuario(); ana.setId(1L); ana.setNome("Ana"); ana.setEmail("ana@x.com");
        when(usuarios.findByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.of(ana));
        s.iniciar(ana);
        /* 00000 tem 5 dígitos: nunca bate com um código de 6, então é erro garantido. */
        for (int i = 0; i < 3; i++) assertThrows(ResponseStatusException.class, () -> s.confirmar("ana@x.com", "00000"));
        ana.getExtra().put(ConfirmacaoEmailService.ENVIADO, 0L);
        s.reenviar("ana@x.com");
        for (int i = 0; i < 2; i++) assertThrows(ResponseStatusException.class, () -> s.confirmar("ana@x.com", "00000"));
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> s.confirmar("ana@x.com", "00000")).getStatusCode().value());
    }

    @Test void codigoDeChegadaBloqueiaAposCincoErrosEGuardaContagemNoPedido() {
        PedidoContratadoRepository repo = mock(PedidoContratadoRepository.class);
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        Pedido p = new Pedido(); p.setId("o_9"); p.setClientEmail("cli@x.com"); p.setProEmail("pro@x.com"); p.setService("eletrica");
        p.setPrice(150.0); p.setStatus("chegou"); p.setCode("4821");
        when(repo.findById("o_9")).thenReturn(Optional.of(p));
        PedidoService s = new PedidoService(repo);

        Pedido errado = new Pedido(); errado.setStatus("em_andamento"); errado.setCode("0000");
        for (int i = 0; i < 5; i++)
            assertEquals(422, assertThrows(ResponseStatusException.class, () -> s.atualizar("o_9", "pro@x.com", errado)).getStatusCode().value());
        Pedido certo = new Pedido(); certo.setStatus("em_andamento"); certo.setCode("4821");
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> s.atualizar("o_9", "pro@x.com", certo)).getStatusCode().value());
        assertEquals("chegou", p.getStatus());
    }
}
