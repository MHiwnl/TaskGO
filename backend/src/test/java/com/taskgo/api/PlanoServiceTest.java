package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanoServiceTest {
    static final long DIA = PlanoService.DIA, AGORA = 1_800_000_000_000L;

    @Test void precoETaxaDoPlano() {
        assertEquals(new BigDecimal("29.90"), PlanoService.PRECO);
        assertEquals(new BigDecimal("0.10"), PlanoService.TAXA_PRO);
        assertEquals(20, PlanoService.CATEGORIAS_PRO);
    }

    @Test void mesSeguinteNoCalendario() {
        long jan31 = Instant.parse("2027-01-31T15:00:00Z").toEpochMilli();
        assertEquals("28/02/2027", PlanoService.data(PlanoService.proximoMes(jan31)), "31/01 + 1 mês = último dia de fevereiro");
        long mar10 = Instant.parse("2027-03-10T15:00:00Z").toEpochMilli();
        assertEquals("10/04/2027", PlanoService.data(PlanoService.proximoMes(mar10)));
    }

    @Test void valeSoAntesDoFimDoMesPago() {
        Assinatura a = new Assinatura("a", 1L, Assinatura.PIX, null, PlanoService.PRECO, AGORA);
        assertFalse(a.vale(AGORA), "sem pagamento não vale");
        a.pagoAte(AGORA + 30 * DIA);
        assertTrue(a.vale(AGORA));
        assertEquals(Assinatura.ATIVA, a.getStatus());
        a.cancelar(AGORA + DIA);
        assertTrue(a.vale(AGORA + 2 * DIA), "cancelar não tira o mês já pago");
        assertFalse(a.vale(AGORA + 31 * DIA));
    }

    @Test void cartaoRenovaNoUltimoDiaUmaVezPorDiaEEncerraDepoisDe3Dias() {
        long ate = AGORA + 10 * DIA;
        assertEquals(PlanoService.Decisao.NADA, PlanoService.decidir("cartao", true, ate, null, null, AGORA), "longe do fim");
        assertEquals(PlanoService.Decisao.COBRAR_CARTAO, PlanoService.decidir("cartao", true, ate, null, null, ate - DIA + 1));
        assertEquals(PlanoService.Decisao.NADA, PlanoService.decidir("cartao", true, ate, ate - DIA + 1, null, ate - DIA + 3_600_000), "já tentou hoje");
        assertEquals(PlanoService.Decisao.COBRAR_CARTAO, PlanoService.decidir("cartao", true, ate, ate - DIA, null, ate + DIA), "tenta de novo no dia seguinte");
        assertEquals(PlanoService.Decisao.ENCERRAR, PlanoService.decidir("cartao", true, ate, ate, null, ate + 3 * DIA), "3 dias sem pagar");
    }

    @Test void pixAvisaUmaVez3DiasAntes() {
        long ate = AGORA + 10 * DIA;
        assertEquals(PlanoService.Decisao.NADA, PlanoService.decidir("pix", false, ate, null, null, ate - 4 * DIA));
        assertEquals(PlanoService.Decisao.AVISAR_PIX, PlanoService.decidir("pix", false, ate, null, null, ate - 3 * DIA));
        assertEquals(PlanoService.Decisao.NADA, PlanoService.decidir("pix", false, ate, null, ate - 3 * DIA, ate - DIA), "já avisou");
        assertEquals(PlanoService.Decisao.ENCERRAR, PlanoService.decidir("pix", false, ate, null, ate - 3 * DIA, ate + 3 * DIA));
        assertEquals(PlanoService.Decisao.AVISAR_PIX, PlanoService.decidir("cartao", false, ate, null, null, ate - DIA), "cartão sem cartão salvo avisa como o Pix");
    }

    @Test void proRecebeOChamadoUmaOndaAntes() {
        /* 25 profissionais por distância (0 = mais perto). O 14º e o 24º são Pro. */
        List<Integer> porDistancia = java.util.stream.IntStream.range(0, 25).boxed().toList();
        List<Integer> ordem = ChamarAgoraService.ordemDaOnda(porDistancia, i -> i == 14 || i == 24);
        List<Integer> primeiraOnda = ordem.subList(0, ChamarAgoraService.POR_ONDA);
        assertTrue(primeiraOnda.contains(14), "o 15º mais perto, Pro, entra na 1ª onda");
        assertFalse(primeiraOnda.contains(24), "o 25º, mesmo Pro, só sobe uma onda");
        assertTrue(ordem.subList(10, 20).contains(24));
        assertEquals(0, ordem.get(0), "o mais perto continua primeiro");
        assertEquals(25, ordem.size());
        assertEquals(porDistancia, ChamarAgoraService.ordemDaOnda(porDistancia, i -> false), "sem Pro, a ordem é a da distância");
    }
}
