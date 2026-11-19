package com.taskgo.api;

import java.util.LinkedHashMap;
import java.util.Map;

/* Catálogo de serviços: a mesma lista de js/core/catalogo.js. Pedidos e profissionais só usam serviços daqui. */
public final class CatalogoServicos {
    private CatalogoServicos() {}
    public static final Map<String, String> NOMES = new LinkedHashMap<>();
    public static final Map<String, String> GRUPOS = new LinkedHashMap<>();
    public static final Map<String, String> UNIDADES = new LinkedHashMap<>();
    /* Serviços cobrados por dia ou visita: podem ser contratados de forma recorrente. */
    public static final java.util.Set<String> UNIDADES_RECORRENTES = java.util.Set.of("diária", "visita", "aula", "sessão", "passeio", "atendimento");
    static {
        add("eletrica", "Elétrica & Energia", "Reparos e manutenção", "visita");
        add("hidraulica", "Hidráulica", "Reparos e manutenção", "visita");
        add("marido", "Marido de Aluguel", "Reparos e manutenção", "hora");
        add("chaveiro", "Chaveiro", "Reparos e manutenção", "serviço");
        add("desentupidora", "Desentupidora", "Reparos e manutenção", "serviço");
        add("vidracaria", "Vidraçaria", "Reparos e manutenção", "serviço");
        add("serralheria", "Serralheria", "Reparos e manutenção", "serviço");
        add("portao", "Portão eletrônico & Interfone", "Reparos e manutenção", "serviço");
        add("gas", "Instalação de gás", "Reparos e manutenção", "serviço");
        add("impermeabilizacao", "Impermeabilização", "Reparos e manutenção", "serviço");
        add("calhas", "Calhas & Rufos", "Reparos e manutenção", "serviço");
        add("aquecedor", "Aquecedor & Boiler", "Reparos e manutenção", "serviço");
        add("reforma", "Pedreiro & Reformas", "Reforma e construção", "projeto");
        add("pintura", "Pintura", "Reforma e construção", "projeto");
        add("gesso", "Gesso & Drywall", "Reforma e construção", "projeto");
        add("pisos", "Pisos & Revestimentos", "Reforma e construção", "projeto");
        add("marcenaria", "Marcenaria", "Reforma e construção", "projeto");
        add("telhado", "Telhados", "Reforma e construção", "projeto");
        add("demolicao", "Demolição & Entulho", "Reforma e construção", "serviço");
        add("arquiteto", "Arquitetura & Projetos", "Reforma e construção", "projeto");
        add("engenheiro", "Engenharia & Laudos", "Reforma e construção", "serviço");
        add("solar", "Energia solar", "Reforma e construção", "serviço");
        add("montagem", "Montagem de Móveis", "Instalação e montagem", "serviço");
        add("ar", "Ar-condicionado", "Instalação e montagem", "serviço");
        add("cftv", "Câmeras & Segurança", "Instalação e montagem", "câmera");
        add("cortinas", "Cortinas & Persianas", "Instalação e montagem", "serviço");
        add("tv", "Instalação de TV & Som", "Instalação e montagem", "serviço");
        add("iluminacao", "Iluminação", "Instalação e montagem", "serviço");
        add("antena", "Antenas & Internet rural", "Instalação e montagem", "serviço");
        add("papel_parede", "Papel de parede & Adesivos", "Instalação e montagem", "serviço");
        add("limpeza", "Limpeza & Diarista", "Limpeza e conservação", "diária");
        add("pos_obra", "Limpeza pós-obra", "Limpeza e conservação", "serviço");
        add("estofados", "Limpeza de estofados", "Limpeza e conservação", "serviço");
        add("vidros", "Limpeza de vidros & Fachadas", "Limpeza e conservação", "serviço");
        add("caixa_dagua", "Limpeza de caixa d’água", "Limpeza e conservação", "serviço");
        add("passadoria", "Passadoria", "Limpeza e conservação", "serviço");
        add("dedetiza", "Dedetização", "Limpeza e conservação", "imóvel");
        add("lavanderia", "Lavanderia", "Limpeza e conservação", "serviço");
        add("jardim", "Jardinagem", "Jardim e área externa", "serviço");
        add("paisagismo", "Paisagismo", "Jardim e área externa", "projeto");
        add("poda", "Poda de árvores", "Jardim e área externa", "serviço");
        add("piscina", "Piscineiro", "Jardim e área externa", "visita");
        add("irrigacao", "Irrigação", "Jardim e área externa", "serviço");
        add("frete", "Frete & Mudança", "Mudança e transporte", "viagem");
        add("motoboy", "Motoboy & Entregas", "Mudança e transporte", "entrega");
        add("icamento", "Içamento", "Mudança e transporte", "serviço");
        add("guarda_moveis", "Guarda-móveis", "Mudança e transporte", "mês");
        add("ti", "Informática & Redes", "Tecnologia", "atendimento");
        add("celular", "Conserto de celular", "Tecnologia", "serviço");
        add("notebook", "Conserto de notebook & PC", "Tecnologia", "serviço");
        add("sites", "Sites & Sistemas", "Tecnologia", "projeto");
        add("automacao", "Casa inteligente", "Tecnologia", "serviço");
        add("eletro", "Eletrodomésticos", "Eletrodomésticos e eletrônicos", "serviço");
        add("eletronicos", "Eletrônicos", "Eletrodomésticos e eletrônicos", "serviço");
        add("refrigeracao", "Refrigeração comercial", "Eletrodomésticos e eletrônicos", "serviço");
        add("costura_maquina", "Máquinas de costura", "Eletrodomésticos e eletrônicos", "serviço");
        add("mecanico", "Mecânico", "Automotivo", "serviço");
        add("auto_eletrica", "Elétrica automotiva", "Automotivo", "serviço");
        add("funilaria", "Funilaria & Pintura automotiva", "Automotivo", "serviço");
        add("lavagem_auto", "Lavagem de carro", "Automotivo", "serviço");
        add("guincho", "Guincho & Reboque", "Automotivo", "viagem");
        add("borracharia", "Borracharia", "Automotivo", "serviço");
        add("insulfilm", "Insulfilm & Acessórios", "Automotivo", "serviço");
        add("cabelo", "Cabeleireiro", "Beleza e estética", "atendimento");
        add("barbeiro", "Barbeiro", "Beleza e estética", "atendimento");
        add("unhas", "Manicure & Pedicure", "Beleza e estética", "atendimento");
        add("maquiagem", "Maquiagem", "Beleza e estética", "atendimento");
        add("sobrancelha", "Sobrancelhas & Cílios", "Beleza e estética", "atendimento");
        add("depilacao", "Depilação", "Beleza e estética", "atendimento");
        add("estetica", "Estética", "Beleza e estética", "sessão");
        add("personal", "Personal trainer", "Saúde e bem-estar", "aula");
        add("massagem", "Massagem", "Saúde e bem-estar", "sessão");
        add("fisioterapia", "Fisioterapia domiciliar", "Saúde e bem-estar", "sessão");
        add("cuidador", "Cuidador de idosos", "Saúde e bem-estar", "diária");
        add("enfermagem", "Enfermagem domiciliar", "Saúde e bem-estar", "atendimento");
        add("nutricionista", "Nutricionista", "Saúde e bem-estar", "consulta");
        add("psicologo", "Psicologia", "Saúde e bem-estar", "sessão");
        add("reforco", "Reforço escolar", "Aulas", "aula");
        add("idiomas", "Idiomas", "Aulas", "aula");
        add("musica", "Aulas de música", "Aulas", "aula");
        add("aula_informatica", "Aulas de informática", "Aulas", "aula");
        add("danca", "Aulas de dança", "Aulas", "aula");
        add("direcao", "Instrutor de direção", "Aulas", "aula");
        add("fotografo", "Fotógrafo", "Eventos", "evento");
        add("filmagem", "Filmagem & Edição", "Eventos", "evento");
        add("buffet", "Buffet & Salgados", "Eventos", "evento");
        add("garcom", "Garçom & Bartender", "Eventos", "evento");
        add("confeitaria", "Bolos & Doces", "Eventos", "encomenda");
        add("decoracao", "Decoração de festas", "Eventos", "evento");
        add("dj", "DJ & Som", "Eventos", "evento");
        add("recreacao", "Recreação infantil", "Eventos", "evento");
        add("baba", "Babá", "Família, casa e pets", "diária");
        add("cozinheira", "Cozinheira", "Família, casa e pets", "diária");
        add("organizer", "Personal organizer", "Família, casa e pets", "serviço");
        add("costura", "Costureira", "Família, casa e pets", "peça");
        add("passeador", "Passeador de cães", "Família, casa e pets", "passeio");
        add("petsitter", "Pet sitter", "Família, casa e pets", "diária");
        add("banho_tosa", "Banho & Tosa", "Família, casa e pets", "atendimento");
        add("adestrador", "Adestrador", "Família, casa e pets", "aula");
        add("contador", "Contador & MEI", "Serviços profissionais", "serviço");
        add("advogado", "Advogado", "Serviços profissionais", "serviço");
        add("design", "Design gráfico", "Serviços profissionais", "projeto");
        add("marketing", "Marketing & Redes sociais", "Serviços profissionais", "mês");
        add("traducao", "Tradução & Revisão", "Serviços profissionais", "documento");
        add("despachante", "Despachante", "Serviços profissionais", "serviço");
    }
    private static void add(String id, String nome, String grupo, String unidade) { NOMES.put(id, nome); GRUPOS.put(id, grupo); UNIDADES.put(id, unidade); }
    public static boolean recorrente(String id) { return id != null && UNIDADES_RECORRENTES.contains(UNIDADES.get(id)); }
    public static boolean existe(String id) { return id != null && NOMES.containsKey(id.trim()); }
    public static String nome(String id) { return id == null ? "" : NOMES.getOrDefault(id, id); }
}
