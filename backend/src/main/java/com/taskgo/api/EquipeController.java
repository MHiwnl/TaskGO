package com.taskgo.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Equipe de suporte criada pelo administrador. A conta nasce sem senha conhecida: a pessoa recebe um convite
   por e-mail e cria a própria senha (o que também confirma o e-mail). */
@RestController
public class EquipeController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UsuarioRepository usuarios;
    private final ModeracaoService moderacao;
    private final RecuperacaoSenhaService recuperacao;
    private final ChamadoRepository chamados;
    private final PasswordEncoder encoder;

    public EquipeController(UsuarioRepository usuarios, ModeracaoService moderacao, RecuperacaoSenhaService recuperacao, ChamadoRepository chamados, PasswordEncoder encoder) {
        this.usuarios = usuarios; this.moderacao = moderacao; this.recuperacao = recuperacao; this.chamados = chamados; this.encoder = encoder;
    }

    @GetMapping("/api/admin/equipe")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Authentication auth) {
        admin(auth);
        List<Chamado> todos = chamados.findAll();
        return usuarios.daEquipe(PapelEquipe.CONVIDAVEIS).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId()); m.put("nome", u.getNome()); m.put("papel", u.getTipo()); m.put("email", u.getEmail()); m.put("criadoEm", u.getCriadoEm());
            m.put("convitePendente", Boolean.TRUE.equals(u.getExtra().get("convitePendente")));
            m.put("chamadosAtendidos", todos.stream().filter(c -> u.getId().equals(c.getAssumidoPor())).count());
            m.put("emAtendimento", todos.stream().filter(c -> u.getId().equals(c.getAssumidoPor()) && Chamado.EM_ATENDIMENTO.equals(c.getStatus())).count());
            return m;
        }).toList();
    }

    public record NovoSuporte(String nome, String email, String papel) {}

    @PostMapping("/api/admin/equipe")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> criar(@RequestBody NovoSuporte body, Authentication auth) {
        TaskgoPrincipal adm = admin(auth);
        String nome = body == null || body.nome() == null ? "" : body.nome().trim();
        String email = body == null || body.email() == null ? "" : body.email().trim().toLowerCase(Locale.ROOT);
        if (nome.length() < 2 || nome.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome da pessoa.");
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.");
        String papel = body.papel() == null || body.papel().isBlank() ? "suporte" : body.papel().trim().toLowerCase(Locale.ROOT);
        if (!PapelEquipe.CONVIDAVEIS.contains(papel)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha suporte, moderador ou financeiro.");
        if (usuarios.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail. Use outro e-mail para a equipe.");
        Usuario u = new Usuario();
        u.setNome(nome); u.setEmail(email); u.setTipo(papel); u.setCriadoEm(System.currentTimeMillis());
        /* Senha aleatória que ninguém conhece: só o convite permite entrar. */
        byte[] aleatorio = new byte[32]; new java.security.SecureRandom().nextBytes(aleatorio);
        u.setSenha(encoder.encode(java.util.Base64.getEncoder().encodeToString(aleatorio)));
        u.getExtra().put(ConfirmacaoEmailService.CONFIRMADO, false);
        u.getExtra().put("convitePendente", true);
        u = usuarios.save(u);
        recuperacao.convidar(u, adm.nome());
        moderacao.registrar(u.getId(), "equipe", "Conta de " + papel + " criada pelo administrador", adm.id(), null);
        return Map.of("id", u.getId(), "nome", u.getNome(), "email", u.getEmail(), "papel", papel, "convitePendente", true);
    }

    @PostMapping("/api/admin/equipe/{id}/reenviar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public void reenviar(@PathVariable Long id, Authentication auth) {
        TaskgoPrincipal adm = admin(auth);
        Usuario u = suporte(id);
        if (!Boolean.TRUE.equals(u.getExtra().get("convitePendente"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta pessoa já criou a senha.");
        recuperacao.convidar(u, adm.nome());
    }

    /* Tira a pessoa da equipe: a conta de suporte é apagada (ela não tem pedidos). Os chamados que ela atendeu ficam. */
    @DeleteMapping("/api/admin/equipe/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remover(@PathVariable Long id, Authentication auth) {
        TaskgoPrincipal adm = admin(auth);
        Usuario u = suporte(id);
        moderacao.registrar(adm.id(), "equipe", "Removeu " + u.getNome() + " (" + u.getEmail() + ", " + u.getTipo() + ") da equipe", adm.id(), null);
        usuarios.delete(u);
    }

    private Usuario suporte(Long id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
        if (!PapelEquipe.CONVIDAVEIS.contains(String.valueOf(u.getTipo()).toLowerCase(Locale.ROOT))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta conta não é da equipe.");
        return u;
    }
    private TaskgoPrincipal admin(Authentication auth) {
        return PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.EQUIPE);
    }
}
