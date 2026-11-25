package com.taskgo.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UsuarioRepository repository;
    private final PedidoRepository pedidoRepository;
    private final PedidoContratadoRepository pedidoContratadoRepository;
    private final MensagemRepository mensagemRepository;
    private final NotificacaoRepository notificacaoRepository;
    private PushInscricaoRepository pushInscricoes;
    private VerificacaoRepository verificacoesRepo;
    private BloqueioAgendaRepository bloqueiosRepo;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setVerificacoesRepo(VerificacaoRepository r) { this.verificacoesRepo = r; }
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setBloqueiosRepo(BloqueioAgendaRepository r) { this.bloqueiosRepo = r; }
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setPushInscricoes(PushInscricaoRepository r) { this.pushInscricoes = r; }
    private CartaoRepository cartoesRepo;
    private CobrancaRepository cobrancasRepo;
    private CarteiraRepository carteirasRepo;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setCobrancasRepo(CobrancaRepository r) { this.cobrancasRepo = r; }
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setCarteirasRepo(CarteiraRepository r) { this.carteirasRepo = r; }
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setCartoesRepo(CartaoRepository r) { this.cartoesRepo = r; }
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    /* Em produção o CPF precisa ter dígito verificador válido e ser único (um CPF por conta).
       Desligado nos testes (CPFs de fachada) pela propriedade. */
    @org.springframework.beans.factory.annotation.Value("${taskgo.cadastro.validar-cpf:true}")
    private boolean validarCpf;

    public AuthController(UsuarioRepository repository, PedidoRepository pedidoRepository,
            PedidoContratadoRepository pedidoContratadoRepository, MensagemRepository mensagemRepository, NotificacaoRepository notificacaoRepository, PasswordEncoder passwordEncoder, LoginAttemptLimiter loginAttemptLimiter) {
        this.repository = repository;
        this.pedidoRepository = pedidoRepository;
        this.pedidoContratadoRepository = pedidoContratadoRepository;
        this.mensagemRepository = mensagemRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    /* O administrador é um tipo próprio de conta: nem cliente nem profissional, sem pedidos.
       Quem se cadastra ou entra com um e-mail de TASKGO_ADMIN_EMAILS vira administrador. */
    private ModeracaoService moderacao;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setModeracao(ModeracaoService moderacao) { this.moderacao = moderacao; }
    private boolean administrador(String email) { return moderacao != null && moderacao.administrador(email); }
    private ConfirmacaoEmailService confirmacao;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setConfirmacao(ConfirmacaoEmailService confirmacao) { this.confirmacao = confirmacao; }
    private PrivacidadeController privacidade;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPrivacidade(PrivacidadeController privacidade) { this.privacidade = privacidade; }
    private SegurancaContaController seguranca;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSeguranca(SegurancaContaController seguranca) { this.seguranca = seguranca; }
    private static final String PENDENTE_2FA = "taskgo2faPendente";
    private static final long PRAZO_2FA_MS = 5 * 60_000L;

    /* Segunda etapa: depois da senha certa, a sessão só abre com o código do aplicativo autenticador. */
    public record SegundaEtapa(String codigo) {}
    @PostMapping("/2fa")
    public UsuarioResponse segundaEtapa(@RequestBody SegundaEtapa body, HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object pend = s == null ? null : s.getAttribute(PENDENTE_2FA);
        if (!(pend instanceof long[] p) || System.currentTimeMillis() > p[1])
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "O tempo para digitar o código acabou. Entre de novo com e-mail e senha.");
        if (p[2] >= 5) { s.invalidate(); throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Entre de novo com e-mail e senha."); }
        Usuario u = repository.findById(p[0]).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (seguranca == null || !seguranca.conferirSegundaEtapa(u, body == null ? null : body.codigo())) {
            p[2]++; s.setAttribute(PENDENTE_2FA, p);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorreto. Use o código que aparece agora no aplicativo, ou um código de recuperação.");
        }
        estabelecerSessao(u, request);
        return resposta(u);
    }

    private SuporteController suporte;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSuporte(SuporteController suporte) { this.suporte = suporte; }

    private PoliticaSenha politicaSenha;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPoliticaSenha(PoliticaSenha politicaSenha) { this.politicaSenha = politicaSenha; }
    private void exigirSenhaForte(String senha, String email, String nome) {
        if (politicaSenha != null) politicaSenha.exigir(senha, email, nome); else PoliticaSenha.exigirLocal(senha, email, nome);
    }
    /* Papéis fixos pelo e-mail: administrador (TASKGO_ADMIN_EMAILS) ou suporte (TASKGO_SUPORTE_EMAILS). */
    private String papelFixo(String email) { return administrador(email) ? "administrador" : suporte != null && suporte.suporte(email) ? "suporte" : null; }
    private static void virarPapel(Usuario u, String papel) { u.setTipo(papel); u.setProId(null); u.setService(null); }
    private static void virarAdministrador(Usuario u) { virarPapel(u, "administrador"); }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) { token.getToken(); return ResponseEntity.noContent().build(); }

    @PostMapping("/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@RequestBody Usuario usuario, HttpServletRequest request) {
        if (usuario == null || vazio(usuario.getEmail()) || vazio(usuario.getSenha())
                || vazio(usuario.getNome()) || vazio(usuario.getTipo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe nome, e-mail, tipo de conta e senha.");
        /* O id nasce no banco: com id no corpo, o save viraria merge sobre a conta existente. */
        usuario.setId(null);
        exigirSenhaForte(usuario.getSenha(), usuario.getEmail(), usuario.getNome());
        String fixo = papelFixo(usuario.getEmail());
        String tipo = fixo != null ? fixo : usuario.getTipo().trim().toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("cliente", "profissional", "administrador", "suporte").contains(tipo) || (fixo == null && !java.util.Set.of("cliente", "profissional").contains(tipo)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de conta inválido.");
        String email = usuario.getEmail().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        usuario.setEmail(email);
        usuario.setTipo(tipo);
        usuario.setCriadoEm(System.currentTimeMillis());
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        if (tipo.equals("profissional") && !CatalogoServicos.existe(usuario.getService())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha a sua categoria no catálogo de serviços.");
        if (tipo.equals("profissional")) usuario.setProId("pro_" + java.util.UUID.randomUUID());
        else { usuario.setProId(null); usuario.setService(null); }
        if (exigeCpf(tipo)) usuario.setCpf(cpfParaSalvar(usuario.getCpf(), null));
        if (fixo != null) virarPapel(usuario, fixo);
        /* Só maiores de 18 (Código Civil, art. 5º). A declaração chega no extra; a data real é conferida na verificação. */
        if (fixo == null && !Boolean.TRUE.equals(usuario.getExtra().get(MAIOR_DE_IDADE)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirme que você tem 18 anos ou mais.");
        /* O cliente não define nenhum campo "extra" no cadastro (verificado, cnpj, chavePix, saldo... são do servidor
           ou têm rota própria). Como o Usuario usa @JsonAnySetter, qualquer chave do corpo cairia no extra: descarta. */
        usuario.getExtra().clear();
        Usuario salvo = repository.save(usuario);
        /* Sem e-mail confirmado não há sessão: a pessoa digita o código que chegou e só então entra. */
        if (confirmacao != null && confirmacao.iniciar(salvo)) return resposta(repository.save(salvo));
        if (confirmacao != null) salvo = repository.save(salvo);
        estabelecerSessao(salvo, request);
        return resposta(salvo);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Usuario dados, HttpServletRequest request) {
        if (dados == null || vazio(dados.getEmail()) || vazio(dados.getSenha()))
            return ResponseEntity.badRequest().body(Map.of("message", "Informe o e-mail e a senha."));
        String address = request.getRemoteAddr();
        if (!loginAttemptLimiter.allowed(address)) return ResponseEntity.status(429).body(Map.of("message", "Muitas tentativas. Aguarde alguns minutos e tente novamente."));
        /* Mesma conta: 3 senhas erradas em 10 minutos bloqueiam até a janela acabar (vale também para e-mail que não existe). */
        long espera = loginAttemptLimiter.contaBloqueadaPor(dados.getEmail());
        if (espera > 0) {
            long min = Math.max(1, (espera + 59_999) / 60_000);
            return ResponseEntity.status(429).header("Retry-After", String.valueOf((espera + 999) / 1000))
                    .body(Map.of("codigo", "CONTA_BLOQUEADA", "message", "Esta conta recebeu 3 senhas erradas. Tente de novo em " + min + (min == 1 ? " minuto" : " minutos") + ", ou use Esqueci a senha."));
        }
        Usuario usuario = repository.findByEmailIgnoreCase(dados.getEmail().trim()).orElse(null);
        if (usuario == null || usuario.getSenha() == null || !passwordEncoder.matches(dados.getSenha(), usuario.getSenha())
                || usuario.getTipo() == null || !PapelEquipe.TODOS_OS_TIPOS.contains(usuario.getTipo().toLowerCase(Locale.ROOT)))
        {
            loginAttemptLimiter.failed(address);
            loginAttemptLimiter.contaErrou(dados.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "E-mail ou senha inválidos."));
        }
        loginAttemptLimiter.succeeded(address);
        loginAttemptLimiter.contaAcertou(dados.getEmail());
        if (!ConfirmacaoEmailService.confirmado(usuario)) {
            try { if (confirmacao != null) confirmacao.reenviar(usuario.getEmail()); } catch (ResponseStatusException ignorada) { /* código recente ainda vale */ }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("codigo", "EMAIL_NAO_CONFIRMADO", "email", usuario.getEmail(),
                    "message", "Confirme seu e-mail para entrar. Mandamos um código de 6 números para " + usuario.getEmail() + "."));
        }
        /* Conta antiga com e-mail de administrador passa a ser administrador. */
        String fixoLogin = papelFixo(usuario.getEmail());
        if (fixoLogin != null && !fixoLogin.equalsIgnoreCase(usuario.getTipo())) { virarPapel(usuario, fixoLogin); repository.save(usuario); }
        boolean removerServicoCliente = "cliente".equalsIgnoreCase(usuario.getTipo()) && usuario.getService() != null;
        if (removerServicoCliente) usuario.setService(null);
        Map<String, Object> safe = sanitizarDadosFinanceiros(usuario.getExtra());
        if (removerServicoCliente || !safe.equals(usuario.getExtra())) { usuario.getExtra().clear(); usuario.getExtra().putAll(safe); repository.save(usuario); }

        if (SegurancaContaController.doisFatoresAtivo(usuario)) {
            HttpSession velha = request.getSession(false); if (velha != null) velha.invalidate();
            request.getSession(true).setAttribute(PENDENTE_2FA, new long[]{usuario.getId(), System.currentTimeMillis() + PRAZO_2FA_MS, 0});
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("codigo", "DOIS_FATORES", "message", "Digite o código do seu aplicativo autenticador."));
        }
        estabelecerSessao(usuario, request);
        return ResponseEntity.ok(resposta(usuario));
    }

    /* ---------- Entrar com Google ---------- */
    private GoogleLogin google;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setGoogle(GoogleLogin google) { this.google = google; }

    @GetMapping("/google/config")
    public Map<String, Object> googleConfig() { return Map.of("clientId", google == null || !google.ativo() ? "" : google.clientId()); }

    /* modo: "entrar" (só conta que já existe) ou "criar" (só conta nova); vazio aceita os dois. */
    public record GoogleRequest(String credencial, String tipo, String telefone, String service, String modo, Boolean maiorDeIdade) {}
    static final String MAIOR_DE_IDADE = "maiorDeIdade";

    /* Token do botão do Google. Conta existente com o mesmo e-mail: entra (e liga a conta Google).
       Sem conta: responde CADASTRO_GOOGLE e o site pede o tipo de conta (e a categoria, se profissional). */
    @PostMapping("/google")
    public ResponseEntity<?> entrarComGoogle(@RequestBody GoogleRequest body, HttpServletRequest request) {
        if (google == null || !google.ativo()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Entrar com Google não está configurado."));
        String address = request.getRemoteAddr();
        if (!loginAttemptLimiter.allowed(address)) return ResponseEntity.status(429).body(Map.of("message", "Muitas tentativas. Aguarde alguns minutos e tente novamente."));
        GoogleLogin.Conta conta;
        try { conta = google.verificar(body == null ? null : body.credencial(), System.currentTimeMillis() / 1000); }
        catch (IllegalArgumentException e) { loginAttemptLimiter.failed(address); return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage())); }
        Usuario usuario = repository.findByEmailIgnoreCase(conta.email()).orElse(null);
        String modo = body.modo() == null ? "" : body.modo().trim();
        if ("entrar".equals(modo) && usuario == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("codigo", "SEM_CONTA", "email", conta.email(),
                    "message", "Não existe conta TaskGO com " + conta.email() + ". Use Criar conta com Google."));
        if ("criar".equals(modo) && usuario != null)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("codigo", "CONTA_EXISTE", "email", conta.email(),
                    "message", "Já existe conta TaskGO com " + conta.email() + ". Use Entrar com Google."));
        if (usuario == null) {
            String tipoPedido = body.tipo() == null ? "" : body.tipo().trim().toLowerCase(Locale.ROOT);
            String fixo = papelFixo(conta.email());
            if (fixo == null && tipoPedido.isBlank())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("codigo", "CADASTRO_GOOGLE", "nome", conta.nome(), "email", conta.email(),
                        "message", "Escolha o tipo de conta para terminar o cadastro."));
            if (fixo == null && !java.util.Set.of("cliente", "profissional").contains(tipoPedido))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de conta inválido.");
            if (fixo == null && tipoPedido.equals("profissional") && vazio(body.service()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha a sua categoria de serviço.");
            if (fixo == null && !Boolean.TRUE.equals(body.maiorDeIdade()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirme que você tem 18 anos ou mais.");
            usuario = new Usuario();
            usuario.setEmail(conta.email());
            usuario.setNome(conta.nome().isBlank() ? conta.email().substring(0, conta.email().indexOf('@')) : conta.nome());
            usuario.setTipo(fixo != null ? fixo : tipoPedido);
            if (!vazio(body.telefone())) usuario.setTelefone(body.telefone().trim());
            usuario.setCriadoEm(System.currentTimeMillis());
            /* Sem senha conhecida: quem quiser uma usa "Esqueci a senha". 43 caracteres: o BCrypt recusa mais de 72 bytes. */
            byte[] aleatorio = new byte[32]; new java.security.SecureRandom().nextBytes(aleatorio);
            usuario.setSenha(passwordEncoder.encode(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio)));
            if ("profissional".equals(usuario.getTipo())) {
                if (!CatalogoServicos.existe(body.service())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha a sua categoria no catálogo de serviços.");
                usuario.setProId("pro_" + java.util.UUID.randomUUID()); usuario.setService(body.service().trim());
            }
            if (fixo != null) virarPapel(usuario, fixo);
            usuario.getExtra().put(ConfirmacaoEmailService.CONFIRMADO, true);
            usuario.getExtra().put("googleSub", conta.sub());
            usuario = repository.save(usuario);
            if (moderacao != null) moderacao.registrar(usuario.getId(), "email", "Criou a conta com o Google", usuario.getId(), null);
        } else {
            Object ligado = usuario.getExtra().get("googleSub");
            if (ligado != null && !ligado.equals(conta.sub())) {
                loginAttemptLimiter.failed(address);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Esta conta da TaskGO está ligada a outra conta Google."));
            }
            usuario.getExtra().put("googleSub", conta.sub());
            usuario.getExtra().put(ConfirmacaoEmailService.CONFIRMADO, true);
            String fixoLogin = papelFixo(usuario.getEmail());
            if (fixoLogin != null && !fixoLogin.equalsIgnoreCase(usuario.getTipo())) virarPapel(usuario, fixoLogin);
            usuario = repository.save(usuario);
        }
        if (usuario.getTipo() == null || !PapelEquipe.TODOS_OS_TIPOS.contains(usuario.getTipo().toLowerCase(Locale.ROOT)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Conta inválida."));
        loginAttemptLimiter.succeeded(address);
        if (SegurancaContaController.doisFatoresAtivo(usuario)) {
            HttpSession velha = request.getSession(false); if (velha != null) velha.invalidate();
            request.getSession(true).setAttribute(PENDENTE_2FA, new long[]{usuario.getId(), System.currentTimeMillis() + PRAZO_2FA_MS, 0});
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("codigo", "DOIS_FATORES", "message", "Digite o código do seu aplicativo autenticador."));
        }
        estabelecerSessao(usuario, request);
        return ResponseEntity.ok(resposta(usuario));
    }

    public record ConfirmarEmailRequest(String email, String codigo) {}
    @PostMapping("/confirmar-email")
    public UsuarioResponse confirmarEmail(@RequestBody ConfirmarEmailRequest body, HttpServletRequest request) {
        if (confirmacao == null || body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmação indisponível.");
        Usuario u = confirmacao.confirmar(body.email(), body.codigo());
        String fixoConf = papelFixo(u.getEmail());
        if (fixoConf != null && !fixoConf.equalsIgnoreCase(u.getTipo())) { virarPapel(u, fixoConf); u = repository.save(u); }
        if (moderacao != null) moderacao.registrar(u.getId(), "email", "Confirmou o e-mail", u.getId(), null);
        estabelecerSessao(u, request);
        return resposta(u);
    }
    @PostMapping("/confirmar-email/reenviar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarCodigo(@RequestBody Map<String, String> body) {
        if (confirmacao != null && body != null) confirmacao.reenviar(body.get("email"));
    }

    @GetMapping("/sessao")
    public UsuarioResponse sessao(Authentication auth) { return resposta(principal(auth)); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        if (seguranca != null) seguranca.encerrarSessaoAtual(request);
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    @PutMapping("/perfil")
    public UsuarioResponse atualizarPerfil(@RequestBody Usuario dados, Authentication auth) {
        Usuario atual = usuarioAutenticado(auth);
        if (dados == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados de perfil ausentes.");
        atual.setNome(dados.getNome());
        atual.setCpf(exigeCpf(atual.getTipo()) ? cpfParaSalvar(dados.getCpf(), atual.getId()) : dados.getCpf());
        atual.setTelefone(dados.getTelefone());
        atual.setCidade(dados.getCidade()); atual.setEndereco(dados.getEndereco()); atual.setBio(dados.getBio());
        if ("cliente".equalsIgnoreCase(atual.getTipo())) atual.setService(null);
        if (dados.getBirth() != null) atual.setBirth(dados.getBirth());
        if ("profissional".equalsIgnoreCase(atual.getTipo()) && dados.getService() != null && !dados.getService().isBlank()) {
            if (!CatalogoServicos.existe(dados.getService())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha a sua categoria no catálogo de serviços.");
            atual.setService(dados.getService().trim());
        }
        /* Senha não muda por aqui: só em PUT /api/auth/senha, que pede a senha atual (sessão roubada não troca a senha). */
        /* Perfil só altera os campos estruturados acima. O "extra" do corpo é ignorado para o cliente não se
           auto-verificar nem forjar CNPJ via @JsonAnySetter; foto, chave Pix e preferências têm rotas próprias. */
        return resposta(repository.save(atual));
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void atualizarSenha(@RequestBody AlterarSenhaRequest request, Authentication auth) {
        Usuario atual = usuarioAutenticado(auth);
        if (request == null || vazio(request.senhaAtual()) || vazio(request.novaSenha()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha atual e a nova senha.");
        if (!passwordEncoder.matches(request.senhaAtual(), atual.getSenha()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha atual incorreta.");
        exigirSenhaForte(request.novaSenha(), atual.getEmail(), atual.getNome());
        atual.setSenha(passwordEncoder.encode(request.novaSenha()));
        atual.setCredentialVersion(atual.getCredentialVersion()+1);
        repository.save(atual);
    }

    @GetMapping("/dados")
    public Map<String, Object> obterDados(Authentication auth) {
        Usuario usuario = usuarioAutenticado(auth);
        Object dados = usuario.getExtra().get("taskgoData");
        if (dados instanceof Map<?, ?> map) {
            Map<String, Object> resultado = new HashMap<>();
            map.forEach((key, value) -> resultado.put(String.valueOf(key), value));
            return sanitizarDadosFinanceiros(resultado);
        }
        return Map.of();
    }

    @PutMapping("/dados")
    public Map<String, Object> salvarDados(@RequestBody DadosContaRequest request, Authentication auth) {
        if (request == null || request.dados() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados a salvar.");
        Usuario usuario = usuarioAutenticado(auth);
        usuario.getExtra().put("taskgoData", sanitizarDadosFinanceiros(request.dados()));
        repository.save(usuario);
        return sanitizarDadosFinanceiros(request.dados());
    }

    @DeleteMapping("/conta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void excluirConta(@RequestBody ExcluirContaRequest request, Authentication auth, HttpServletRequest httpRequest) {
        Usuario usuario = usuarioAutenticado(auth);
        if ("administrador".equalsIgnoreCase(usuario.getTipo()) || "suporte".equalsIgnoreCase(usuario.getTipo()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contas da equipe (administrador e suporte) não podem ser excluídas pelo site. Tire o e-mail da configuração antes.");
        if (request == null || vazio(request.senha())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha da conta.");
        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não foi possível confirmar a senha da conta.");
        /* Dinheiro em aberto não pode ficar sem dono: pagamento em andamento ou saldo na carteira primeiro se resolvem. */
        if (cobrancasRepo != null && cobrancasRepo.temEmAberto(usuario.getId(), Cobranca.PENDENTE, System.currentTimeMillis()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há um pagamento em andamento nesta conta (Pix no prazo ou cartão em conferência). Tente de novo depois que ele vencer ou for confirmado.");
        Carteira carteira = carteirasRepo == null ? null : carteirasRepo.findByUsuarioId(usuario.getId()).orElse(null);
        if (carteira != null && (carteira.getSaldoDisponivel().signum() > 0 || carteira.getSaldoPendente().signum() > 0 || carteira.getSaldoBloqueado().signum() > 0))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há saldo na sua carteira. Resgate o valor antes de excluir a conta.");
        notificacaoRepository.deleteByUsuarioId(usuario.getId());
        if (pushInscricoes != null) pushInscricoes.deleteByUsuarioId(usuario.getId());
        if (verificacoesRepo != null) verificacoesRepo.deleteByUsuarioId(usuario.getId());
        if (bloqueiosRepo != null) bloqueiosRepo.deleteByUsuarioId(usuario.getId());
        if (privacidade != null) privacidade.apagarRestos(usuario.getId());
        pedidoRepository.deleteByEmailClienteIgnoreCase(usuario.getEmail());
        /* Pedido com a outra pessoa fica para ela (histórico, recibo, conversa): só os dados desta conta saem dele.
           Pedido sem outra pessoa (ninguém aceitou) some junto com a conta. */
        String anonimo = "excluida-" + usuario.getId() + "@conta.invalid";
        for (Pedido x : pedidoContratadoRepository.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(usuario.getEmail(), usuario.getEmail())) {
            boolean souCliente = usuario.getEmail().equalsIgnoreCase(String.valueOf(x.getClientEmail()));
            String outro = souCliente ? x.getProEmail() : x.getClientEmail();
            if (outro == null || outro.isBlank()) { mensagemRepository.deleteByPedidoIdIn(List.of(x.getId())); pedidoContratadoRepository.delete(x); continue; }
            if (souCliente) { x.setClientEmail(anonimo); x.setClientId(null); x.setAddress(null); x.getExtra().remove("clientName"); x.getExtra().remove("lat"); x.getExtra().remove("lng"); }
            else { x.setProEmail(anonimo); x.setProfessionalName("Conta excluída"); }
            pedidoContratadoRepository.save(x);
        }
        if (cartoesRepo != null) cartoesRepo.deleteAll(cartoesRepo.findByUsuarioIdOrderByCriadoEmAsc(usuario.getId()));
        if (carteira != null) carteirasRepo.delete(carteira);
        repository.delete(usuario);
        HttpSession session = httpRequest.getSession(false); if (session != null) session.invalidate();
    }

    private Usuario usuarioAutenticado(Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        return repository.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
    }
    private void estabelecerSessao(Usuario usuario, HttpServletRequest request) {
        HttpSession old = request.getSession(false);
        if (old != null) { if (seguranca != null) seguranca.encerrarSessaoAtual(request); old.invalidate(); }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        TaskgoPrincipal principal = principal(usuario);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        if (seguranca != null) seguranca.registrarSessao(usuario, request);
    }
    private TaskgoPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
    private TaskgoPrincipal principal(Usuario u) { return new TaskgoPrincipal(u.getId(), u.getEmail(), u.getNome(), u.getTipo(), u.getProId(), u.getCredentialVersion()); }
    private UsuarioResponse resposta(Usuario u) { return new UsuarioResponse(u.getId(), u.getNome(), u.getCpf(), u.getTelefone(), u.getEmail(), u.getTipo(), u.getCidade(), u.getEndereco(), u.getBio(), u.getProId(), u.getService(), u.getBirth(), ConfirmacaoEmailService.confirmado(u), VerificacaoController.verificado(u)); }
    private UsuarioResponse resposta(TaskgoPrincipal p) {
        Usuario u = repository.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
        return resposta(u);
    }
    private boolean vazio(String v) { return v == null || v.isBlank(); }

    /* Só os dígitos do CPF. */
    static String cpfDigitos(String cpf) { return cpf == null ? "" : cpf.replaceAll("\\D", ""); }
    /* Valida os dois dígitos verificadores do CPF; recusa número repetido (ex.: 111.111.111-11). */
    static boolean cpfValido(String cpf) {
        String d = cpfDigitos(cpf);
        if (d.length() != 11 || d.chars().distinct().count() == 1) return false;
        for (int t = 9; t < 11; t++) {
            int soma = 0;
            for (int i = 0; i < t; i++) soma += (d.charAt(i) - '0') * (t + 1 - i);
            int dig = (soma * 10) % 11 % 10;
            if (dig != d.charAt(t) - '0') return false;
        }
        return true;
    }
    /* Em produção exige CPF válido e único e devolve o CPF só com dígitos; com a flag desligada, passa como veio. */
    private String cpfParaSalvar(String cpf, Long idProprio) {
        if (!validarCpf) return cpf;
        if (vazio(cpf)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o seu CPF.");
        String d = cpfDigitos(cpf);
        if (!cpfValido(d)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF inválido. Confira os números.");
        boolean duplicado = repository.findByCpf(d).stream().anyMatch(u -> idProprio == null || !u.getId().equals(idProprio));
        if (duplicado) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este CPF já está cadastrado em outra conta.");
        return d;
    }
    private boolean exigeCpf(String tipo) { return "cliente".equalsIgnoreCase(tipo) || "profissional".equalsIgnoreCase(tipo); }

    /* Mantida só para referência histórica; o cadastro e o perfil não aceitam mais "extra" do cliente. */
    @SuppressWarnings("unused")
    private Map<String, Object> sanitizarEntrada(Map<String, ?> dados) {
        Map<String, Object> r = sanitizarDadosFinanceiros(dados);
        r.keySet().removeIf(k -> { String n = k.toLowerCase(Locale.ROOT); return n.startsWith("moderacao") || n.startsWith("emailcodigo") || n.equals("emailconfirmado") || n.startsWith("seguranca") || n.startsWith("identidade"); });
        return r;
    }
    private Map<String, Object> sanitizarDadosFinanceiros(Map<String, ?> dados) {
        Map<String, Object> resultado = new LinkedHashMap<>(); if (dados == null) return resultado;
        for (Map.Entry<String, ?> entrada : dados.entrySet()) {
            String chave = entrada.getKey();
            String normalizada = chave.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (java.util.Set.of("balance", "pending", "availablebalance", "pendingbalance", "transactions", "transaction", "ledger", "wallet", "wallets", "withdrawals", "payouts", "plan", "subscription", "subscriptions", "verified", "faceverified", "admin", "isadmin", "role", "roles", "permissions", "notifications", "chats", "messages", "pass", "password", "senha", "passwordhash", "token", "accesstoken", "refreshtoken").contains(normalizada)) continue;
            Object valor = entrada.getValue();
            if (valor instanceof Map<?, ?> mapa) {
                Map<String, Object> nested = new LinkedHashMap<>(); mapa.forEach((k, v) -> nested.put(String.valueOf(k), v));
                resultado.put(chave, sanitizarDadosFinanceiros(nested));
            } else if (valor instanceof List<?> lista) {
                List<Object> safe = new ArrayList<>();
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> mapa) { Map<String, Object> nested = new LinkedHashMap<>(); mapa.forEach((k, v) -> nested.put(String.valueOf(k), v)); safe.add(sanitizarDadosFinanceiros(nested)); }
                    else safe.add(item);
                }
                resultado.put(chave, safe);
            } else resultado.put(chave, valor);
        }
        return resultado;
    }

    public record UsuarioResponse(Long id, String nome, String cpf, String telefone, String email, String tipo,
            String cidade, String endereco, String bio, String proId, String service, String birth, boolean emailConfirmado, boolean verificado) {}
    public record ExcluirContaRequest(String senha) {}
    public record AlterarSenhaRequest(String senhaAtual, String novaSenha) {}
    public record DadosContaRequest(Map<String, Object> dados) {}
}
