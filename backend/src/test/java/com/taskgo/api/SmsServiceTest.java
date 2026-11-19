package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SmsServiceTest {
    @Test void desligadoPorPadraoNaoEnvia() {
        SmsService s = new SmsService("desligado", "", "");
        assertFalse(s.ligado());
        assertFalse(s.enviar("(81) 98765-4321", "teste"));
    }
    @Test void httpSemUrlOuTokenFicaDesligado() {
        assertFalse(new SmsService("http", "", "tok").ligado());
        assertFalse(new SmsService("http", "http://inseguro.example", "tok").ligado(), "só https");
        assertFalse(new SmsService("http", "https://sms.example", "").ligado());
        assertTrue(new SmsService("http", "https://sms.example", "tok").ligado());
    }
    @Test void modoLogSimulaOEnvio() {
        SmsService s = new SmsService("log", "", "");
        assertTrue(s.ligado());
        assertTrue(s.enviar("81987654321", "Pedido novo perto de você"));
        assertFalse(s.enviar("123", "número inválido não envia"));
    }
    @Test void telefoneViraFormatoInternacional() {
        assertEquals("+5581987654321", SmsService.telefoneInternacional("(81) 98765-4321"));
        assertEquals("+558133334444", SmsService.telefoneInternacional("81 3333-4444"));
        assertEquals("+5581987654321", SmsService.telefoneInternacional("+55 81 98765-4321"));
        assertNull(SmsService.telefoneInternacional("98765-4321"), "sem DDD");
        assertNull(SmsService.telefoneInternacional("(01) 98765-4321"), "DDD inválido");
        assertNull(SmsService.telefoneInternacional(null));
    }
    @Test void textoCabeEmUmSmsENumeroFicaMascaradoNoLog() {
        assertEquals(SmsService.MAX_TEXTO, SmsService.encurtar("x".repeat(400)).length());
        assertEquals("a b", SmsService.encurtar("  a \n b "));
        assertEquals("***4321", SmsService.mascarar("+5581987654321"));
    }
}
