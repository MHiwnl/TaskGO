package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class ConfirmacaoEmailServiceTest {
    private UsuarioRepository usuarios;
    private EmailRecuperacaoSenha email;
    private ConfirmacaoEmailService service;
    private Usuario ana;

    @BeforeEach void setup() {
        usuarios = mock(UsuarioRepository.class); email = mock(EmailRecuperacaoSenha.class);
        service = new ConfirmacaoEmailService(usuarios, email);
        ana = new Usuario(); ana.setId(1L); ana.setNome("Ana Souza"); ana.setEmail("ana@x.com");
        when(usuarios.findByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.of(ana));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private String codigoEnviado() {
        ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).enviarTexto(eq("ana@x.com"), assunto.capture(), anyString());
        return assunto.getValue().replaceAll("[^0-9]", "");
    }

    @Test void contaAntigaContaComoConfirmada() { assertTrue(ConfirmacaoEmailService.confirmado(new Usuario())); }

    @Test void semEmailConfiguradoConfirmaNaHora() {
        when(email.canalPronto()).thenReturn(false);
        assertFalse(service.iniciar(ana)); assertTrue(ConfirmacaoEmailService.confirmado(ana));
    }

    @Test void codigoCertoConfirmaEErradoContaTentativa() {
        when(email.canalPronto()).thenReturn(true);
        assertTrue(service.iniciar(ana)); assertFalse(ConfirmacaoEmailService.confirmado(ana));
        String codigo = codigoEnviado(); assertEquals(6, codigo.length());
        String errado = codigo.equals("000000") ? "111111" : "000000";
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.confirmar("ana@x.com", errado)).getReason().contains("Restam 4"));
        service.confirmar("ana@x.com", codigo.substring(0, 3) + " " + codigo.substring(3));
        assertTrue(ConfirmacaoEmailService.confirmado(ana)); assertNull(ana.getExtra().get(ConfirmacaoEmailService.HASH));
    }

    @Test void bloqueiaDepoisDeCincoErrosEReenvioSeguido() {
        when(email.canalPronto()).thenReturn(true);
        service.iniciar(ana);
        String errado = codigoEnviado().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertThrows(ResponseStatusException.class, () -> service.confirmar("ana@x.com", errado));
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.confirmar("ana@x.com", errado)).getStatusCode().value());
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.reenviar("ana@x.com")).getStatusCode().value());
    }
}
