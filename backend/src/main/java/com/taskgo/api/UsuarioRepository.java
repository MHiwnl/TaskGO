package com.taskgo.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findByTipoIgnoreCaseAndServiceIgnoreCaseOrderByNomeAsc(String tipo, String service);
    List<Usuario> findByTipoIgnoreCaseOrderByNomeAsc(String tipo);
    /* Profissionais que atendem a categoria (principal ou uma das outras que marcaram). */
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where lower(u.tipo) = 'profissional' and (lower(u.service) = lower(:s) or u.id in (select x.usuarioId from ProfissionalServico x where x.servico = :s)) order by u.nome")
    List<Usuario> profissionaisDoServico(@org.springframework.data.repository.query.Param("s") String servico);

    /* ---- painel em escala ---- */
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where lower(u.nome) like :prefixo order by lower(u.nome)")
    List<Usuario> buscarPorNome(@org.springframework.data.repository.query.Param("prefixo") String prefixo, org.springframework.data.domain.Pageable pagina);
    List<Usuario> findByCpfIn(java.util.Collection<String> cpfs);
    List<Usuario> findByCpf(String cpf);
    List<Usuario> findByTelefoneIn(java.util.Collection<String> telefones);
    @org.springframework.data.jpa.repository.Query("select lower(u.tipo), count(u) from Usuario u where u.criadoEm >= :a and u.criadoEm < :b group by lower(u.tipo)")
    List<Object[]> novosEntre(@org.springframework.data.repository.query.Param("a") long a, @org.springframework.data.repository.query.Param("b") long b);
    @org.springframework.data.jpa.repository.Query("select lower(u.tipo), count(u) from Usuario u group by lower(u.tipo)")
    List<Object[]> contarPorTipo();
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.criadoEm >= :a and u.criadoEm < :b order by u.criadoEm asc, u.id asc")
    List<Usuario> paginaEntre(@org.springframework.data.repository.query.Param("a") long a, @org.springframework.data.repository.query.Param("b") long b, org.springframework.data.domain.Pageable pagina);
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where lower(u.tipo) in :tipos order by u.nome")
    List<Usuario> daEquipe(@org.springframework.data.repository.query.Param("tipos") java.util.Collection<String> tipos);
}
