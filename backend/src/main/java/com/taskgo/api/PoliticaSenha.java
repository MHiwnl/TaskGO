package com.taskgo.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Regras de senha da TaskGO:
 * - no mínimo 12 caracteres (no máximo 128);
 * - pelo menos 3 de 4 tipos: letra minúscula, letra maiúscula, número, símbolo;
 * - não pode ser senha comum, sequência óbvia, nem conter o nome ou o e-mail da pessoa;
 * - não pode estar em vazamentos conhecidos (Have I Been Pwned, consulta por k-anonimato:
 *   só os 5 primeiros caracteres do hash SHA-1 saem do servidor; a senha nunca sai).
 */
@Component
public class PoliticaSenha {
    private static final Logger log = LoggerFactory.getLogger(PoliticaSenha.class);
    public static final int MINIMO = 12, MAXIMO = 128;

    /* Senhas e palavras que aparecem no topo das listas de vazamento (globais e do Brasil). */
    private static final Set<String> COMUNS = Set.of(
        "123456","1234567","12345678","123456789","1234567890","12345678910","123123","123123123","111111","000000","654321",
        "senha","senha123","senha1234","senha12345","minhasenha","mudar123","trocar123","acesso","acesso123","admin","admin123",
        "password","password1","password123","passw0rd","qwerty","qwerty123","qwertyuiop","asdfgh","asdfghjkl","zxcvbnm",
        "abc123","abcdef","abcd1234","iloveyou","teamo","teamo123","eusouf","brasil","brasil123","flamengo","flamengo123",
        "corinthians","palmeiras","saopaulo","santos","vasco","gremio","internacional","cruzeiro","sport","nautico","santacruz",
        "recife","pernambuco","jesus","jesus123","deus","deusefiel","amor","amor123","familia","felicidade","futebol",
        "taskgo","taskgo123","welcome","welcome1","letmein","monkey","dragon","master","sunshine","princess","football",
        "baseball","superman","batman","trustno1","whatever","starwars","pokemon","naruto","minecraft","google","facebook",
        "instagram","whatsapp","gabriel","lucas","mateus","pedro","maria","ana","juliana","fernanda","camila","rafael"
    );
    private static final String[] SEQUENCIAS = {"0123456789","9876543210","abcdefghijklmnopqrstuvwxyz","qwertyuiop","asdfghjkl","zxcvbnm"};

    private final boolean verificarVazamentos;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public PoliticaSenha(@Value("${taskgo.senha.verificar-vazamentos:true}") boolean verificarVazamentos) {
        this.verificarVazamentos = verificarVazamentos;
    }

    /** Regras que não dependem de rede. Devolve a lista do que falta (vazia = senha aceita). */
    public static List<String> problemas(String senha, String email, String nome) {
        List<String> faltas = new ArrayList<>();
        if (senha == null || senha.length() < MINIMO) { faltas.add("ter pelo menos " + MINIMO + " caracteres"); }
        if (senha != null && senha.length() > MAXIMO) faltas.add("ter no máximo " + MAXIMO + " caracteres");
        if (senha == null) return faltas;
        int tipos = 0;
        if (senha.chars().anyMatch(Character::isLowerCase)) tipos++;
        if (senha.chars().anyMatch(Character::isUpperCase)) tipos++;
        if (senha.chars().anyMatch(Character::isDigit)) tipos++;
        if (senha.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) tipos++;
        if (tipos < 3) faltas.add("misturar pelo menos 3 destes: letra minúscula, letra maiúscula, número e símbolo");
        String base = semAcento(senha), leet = leet(base);
        boolean comum = false;
        for (String forma : List.of(base, leet)) {
            String letras = forma.replaceAll("[^a-z]+$", "").replaceAll("^[^a-z]+", "");
            if (COMUNS.contains(forma) || COMUNS.contains(letras)) comum = true;
        }
        if (comum || base.chars().distinct().count() <= 3 || sequencia(base))
            faltas.add("não ser uma senha comum ou fácil de adivinhar");
        if (email != null && !email.isBlank()) {
            String usuario = semAcento(email.split("@")[0]).replaceAll("[^a-z]", "");
            if (usuario.length() >= 4 && (base.contains(usuario) || leet.contains(usuario))) faltas.add("não conter o seu e-mail");
        }
        if (nome != null) for (String parte : semAcento(nome).split(" +"))
            if (parte.length() >= 4 && (base.contains(parte) || leet.contains(parte))) { faltas.add("não conter o seu nome"); break; }
        return faltas;
    }

    /** Valida tudo e responde 400 com uma frase clara quando a senha não serve. */
    public void exigir(String senha, String email, String nome) {
        List<String> faltas = problemas(senha, email, nome);
        if (!faltas.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha precisa " + String.join("; ", faltas) + ".");
        if (vazada(senha))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Essa senha já apareceu em vazamentos de dados na internet. Escolha outra.");
    }

    /** Validação só local, para quem não tem o componente (testes que montam o controller à mão). */
    public static void exigirLocal(String senha, String email, String nome) {
        List<String> faltas = problemas(senha, email, nome);
        if (!faltas.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha precisa " + String.join("; ", faltas) + ".");
    }

    boolean vazada(String senha) {
        if (!verificarVazamentos) return false;
        try {
            String sha1 = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(senha.getBytes(StandardCharsets.UTF_8)));
            String prefixo = sha1.substring(0, 5), resto = sha1.substring(5);
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.pwnedpasswords.com/range/" + prefixo))
                .timeout(Duration.ofSeconds(3)).header("Add-Padding", "true").header("User-Agent", "TaskGO-senha").GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return false;
            for (String linha : resp.body().split("\r?\n")) {
                int i = linha.indexOf(':');
                if (i > 0 && linha.substring(0, i).equalsIgnoreCase(resto)) {
                    long vezes = Long.parseLong(linha.substring(i + 1).trim());
                    if (vezes > 0) return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return false;
        } catch (Exception e) {
            /* Sem internet ou serviço fora do ar: as regras locais continuam valendo. */
            log.debug("Consulta de senhas vazadas indisponível: {}", e.getMessage());
            return false;
        }
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static String leet(String s) {
        return s.replace('@', 'a').replace('4', 'a').replace('3', 'e').replace('1', 'i').replace('0', 'o').replace('$', 's').replace('5', 's');
    }

    private static boolean sequencia(String base) {
        String so = base.replaceAll("[^a-z0-9]", "");
        if (so.length() < 6) return false;
        for (String seq : SEQUENCIAS) if (seq.contains(so) || seq.contains(new StringBuilder(so).reverse())) return true;
        return false;
    }
}
