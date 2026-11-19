package com.taskgo.api;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService noImplicitUsers() {
        return username -> { throw new UsernameNotFoundException("Autenticação deve usar as contas do TaskGo."); };
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioRepository usuarios, org.springframework.beans.factory.ObjectProvider<SessaoContaRepository> sessoes,
            CookieCsrfTokenRepository csrfTokenRepository,
            CsrfCookieSameSiteFilter csrfCookieSameSiteFilter) throws Exception {
        return http
            .cors(cors -> {})
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                    /* Aviso do provedor Pix: servidor para servidor, protegido pelo token próprio. */
                    .ignoringRequestMatchers("/api/pagamentos/webhook/pix", "/api/pagamentos/webhook/asaas"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                /* Envio do tempo real (SSE) continua na mesma requisição já autenticada. */
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/api/auth/login", "/api/auth/registrar", "/api/privacidade/prazos", "/api/auth/confirmar-email", "/api/auth/2fa", "/api/auth/confirmar-email/reenviar", "/api/auth/csrf", "/api/auth/recuperacao", "/api/auth/recuperacao/**", "/api/auth/google", "/api/auth/google/config", "/api/profissionais", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/contatos").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pedidos/estimar").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pagamentos/webhook/pix", "/api/pagamentos/webhook/asaas").permitAll()
                .anyRequest().authenticated())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .addFilterAfter(filtroSessao(usuarios, sessoes.getIfAvailable()), org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .addFilterBefore(csrfCookieSameSiteFilter, CsrfFilter.class)
            .build();
    }

    @Bean CsrfCookieSameSiteFilter csrfCookieSameSiteFilter(
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite) {
        return new CsrfCookieSameSiteFilter(sameSite);
    }

    @Bean CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).path("/"));
        return repository;
    }

    @Bean CorsConfigurationSource corsConfigurationSource(
            @Value("${taskgo.cors.allowed-origins}") String configuredOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(configuredOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override public void handle(jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response,
                java.util.function.Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override public String resolveCsrfTokenValue(jakarta.servlet.http.HttpServletRequest request, CsrfToken csrfToken) {
            return StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
                    ? plain.resolveCsrfTokenValue(request, csrfToken)
                    : xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }
    private static SessionVersionFilter filtroSessao(UsuarioRepository usuarios, SessaoContaRepository sessoes) {
        SessionVersionFilter f = new SessionVersionFilter(usuarios);
        if (sessoes != null) f.setSessoes(sessoes);
        return f;
    }
}
