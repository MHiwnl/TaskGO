package com.taskgo.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/* As categorias do profissional: a principal (usuarios.service) e as outras que ele sabe fazer (até 10 no total).
   Ele recebe oportunidades, avisos e Chamar agora de todas. */
@Service
public class ServicosProfissional {
    static final int MAXIMO = 10;
    private final ProfissionalServicoRepository repo;
    private final UsuarioRepository usuarios;
    public ServicosProfissional(ProfissionalServicoRepository repo, UsuarioRepository usuarios) { this.repo = repo; this.usuarios = usuarios; }
    /* Plano Pro: até 20 categorias. */
    private PlanoService planos;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPlanos(@org.springframework.context.annotation.Lazy PlanoService p) { this.planos = p; }
    public int maximo(Usuario u) { return planos == null ? MAXIMO : planos.maxCategorias(u); }

    /** Todas as categorias, a principal primeiro. */
    @Transactional(readOnly = true)
    public List<String> de(Usuario u) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        if (u.getService() != null && !u.getService().isBlank()) s.add(u.getService());
        repo.findByUsuarioId(u.getId()).forEach(x -> s.add(x.getServico()));
        return new ArrayList<>(s);
    }

    public boolean faz(Usuario u, String servico) {
        if (u == null || servico == null) return false;
        if (servico.equalsIgnoreCase(String.valueOf(u.getService()))) return true;
        return repo.existsByUsuarioIdAndServico(u.getId(), servico.toLowerCase(Locale.ROOT));
    }

    /** Troca as categorias; a primeira vira a principal. */
    @Transactional
    public List<String> salvar(Usuario u, List<String> lista) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (String x : lista == null ? List.<String>of() : lista) if (x != null && !x.isBlank()) s.add(x.trim());
        if (s.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha pelo menos uma categoria.");
        int maximo = maximo(u);
        if (s.size() > maximo) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha no máximo " + maximo + " categorias." + (maximo < PlanoService.CATEGORIAS_PRO ? " Com o Plano Pro, até " + PlanoService.CATEGORIAS_PRO + "." : ""));
        for (String x : s) if (!CatalogoServicos.existe(x)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria fora do catálogo: " + x + ".");
        repo.apagarDoUsuario(u.getId());
        for (String x : s) repo.save(new ProfissionalServico(u.getId(), x));
        Usuario atual = usuarios.findById(u.getId()).orElseThrow();
        atual.setService(s.iterator().next());
        usuarios.save(atual);
        return new ArrayList<>(s);
    }
}
