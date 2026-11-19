package com.taskgo.api;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/* Papéis da equipe e o que cada um pode ver e fazer no painel.
   Administrador: analisa os dados (hoje, busca, atenção, números, auditoria, exportar), administra a equipe e escreve avisos; não decide casos.
   Suporte: decide denúncias, recursos, verificações, problemas de pagamento (inclui obras) e emergências (tela própria).
   Moderador: denúncias, recursos, verificações, busca e auditoria. Financeiro: problemas de pagamento, atenção, números e exportações. */
public final class PapelEquipe {
    private PapelEquipe() {}

    public enum Area { HOJE, MODERACAO, PAGAMENTOS, BUSCAR, ATENCAO, NUMEROS, AUDITORIA, EXPORTAR, EQUIPE, AVISOS, FINANCEIRO }

    public static final Set<String> TIPOS_EQUIPE = Set.of("administrador", "moderador", "financeiro", "suporte");
    public static final Set<String> TODOS_OS_TIPOS = Set.of("cliente", "profissional", "administrador", "moderador", "financeiro", "suporte");
    /* Papéis que o administrador cria em Equipe (o administrador vem da configuração). */
    public static final Set<String> CONVIDAVEIS = Set.of("moderador", "financeiro", "suporte");

    static Set<Area> areas(String tipo) {
        return switch (tipo == null ? "" : tipo.toLowerCase(Locale.ROOT)) {
            case "administrador" -> EnumSet.of(Area.HOJE, Area.BUSCAR, Area.ATENCAO, Area.NUMEROS, Area.AUDITORIA, Area.EXPORTAR, Area.EQUIPE, Area.AVISOS, Area.FINANCEIRO);
            case "suporte" -> EnumSet.of(Area.MODERACAO, Area.PAGAMENTOS, Area.BUSCAR, Area.ATENCAO);
            case "moderador" -> EnumSet.of(Area.HOJE, Area.MODERACAO, Area.BUSCAR, Area.AUDITORIA);
            case "financeiro" -> EnumSet.of(Area.HOJE, Area.PAGAMENTOS, Area.BUSCAR, Area.ATENCAO, Area.NUMEROS, Area.EXPORTAR);
            default -> EnumSet.noneOf(Area.class);
        };
    }

    public static boolean equipe(String tipo) { return tipo != null && TIPOS_EQUIPE.contains(tipo.toLowerCase(Locale.ROOT)); }

    /* Quem chama precisa ter a área do seu papel (o administrador da configuração tem as áreas de administrador). */
    public static TaskgoPrincipal exigir(Authentication auth, ModeracaoService moderacao, Area area) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!areas(papelDe(p, moderacao)).contains(area)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel na equipe não dá acesso a esta área.");
        return p;
    }

    static String papelDe(TaskgoPrincipal p, ModeracaoService moderacao) {
        return moderacao != null && moderacao.administrador(p.email()) ? "administrador" : String.valueOf(p.tipo()).toLowerCase(Locale.ROOT);
    }
}
