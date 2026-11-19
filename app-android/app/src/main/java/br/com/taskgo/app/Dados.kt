package br.com.taskgo.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PestControl
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.roundToInt

/* =========================================================
   DADOS E REGRAS DO PROTÓTIPO
   Nada vem de servidor: o "outro lado" (cliente ou profissional) é simulado pelo próprio app.
   ========================================================= */

data class Categoria(val id: String, val nome: String, val icone: ImageVector, val min: Int, val max: Int, val unidade: String, val popular: Boolean)

val CATEGORIAS = listOf(
    Categoria("eletrica", "Elétrica", Icons.Outlined.ElectricBolt, 90, 420, "visita", true),
    Categoria("ar", "Ar-condicionado", Icons.Outlined.AcUnit, 150, 780, "serviço", true),
    Categoria("hidraulica", "Hidráulica", Icons.Outlined.WaterDrop, 110, 520, "visita", true),
    Categoria("limpeza", "Limpeza", Icons.Outlined.CleaningServices, 130, 360, "diária", true),
    Categoria("montagem", "Montagem de móveis", Icons.Outlined.Weekend, 80, 450, "serviço", true),
    Categoria("marido", "Marido de aluguel", Icons.Outlined.Handyman, 70, 300, "hora", true),
    Categoria("pintura", "Pintura", Icons.Outlined.FormatPaint, 200, 2400, "projeto", false),
    Categoria("reforma", "Pedreiro e reformas", Icons.Outlined.Construction, 180, 3800, "projeto", false),
    Categoria("jardim", "Jardinagem", Icons.Outlined.Grass, 90, 480, "serviço", false),
    Categoria("eletro", "Eletrodomésticos", Icons.Outlined.Kitchen, 120, 690, "serviço", false),
    Categoria("frete", "Frete e mudança", Icons.Outlined.LocalShipping, 120, 1600, "viagem", false),
    Categoria("dedetiza", "Dedetização", Icons.Outlined.PestControl, 160, 820, "imóvel", false),
    Categoria("ti", "Informática e redes", Icons.Outlined.Computer, 90, 640, "atendimento", false),
    Categoria("cftv", "Câmeras e segurança", Icons.Outlined.Videocam, 100, 300, "câmera", false)
)
fun categoria(id: String) = CATEGORIAS.first { it.id == id }

/* ---------- Valores ---------- */
/* Taxa da TaskGO sobre o valor do serviço. O profissional vê a taxa; o cliente vê o valor cheio. */
const val TAXA = 0.12
fun taxaDe(valor: Int) = (valor * TAXA).roundToInt()
fun liquido(valor: Int) = valor - taxaDe(valor)
fun reais(valor: Int) = "R$ " + "%,d".format(valor).replace(',', '.')
fun arredonda5(v: Double) = ((v / 5).roundToInt() * 5).coerceAtLeast(5)

/* Cancelamento, conforme a proposta de termos: sem taxa antes do aceite; 10% a caminho; 20% depois da chegada. */
fun percentualCancelamento(fase: Fase) = when (fase) {
    Fase.BUSCANDO -> 0
    Fase.A_CAMINHO -> 10
    Fase.NO_LOCAL, Fase.TRABALHANDO -> 20
    else -> 0
}

data class Pessoa(val nome: String, val nota: Double, val avaliacoes: Int) {
    val primeiroNome get() = nome.substringBefore(" ")
}

enum class Lado { CLIENTE, PROFISSIONAL }
enum class Perfil { CLIENTE, PROFISSIONAL }
fun Perfil.lado() = if (this == Perfil.CLIENTE) Lado.CLIENTE else Lado.PROFISSIONAL

enum class StatusProposta { PENDENTE, ACEITA, RECUSADA, SUBSTITUIDA, CANCELADA }

/* Uma proposta de valor dentro da negociação de um pedido com um profissional. */
class Proposta(val de: Lado, val profissional: Pessoa, val valor: Int) {
    val id = "pr" + (contador++)
    var status by mutableStateOf(StatusProposta.PENDENTE)
    companion object { private var contador = 0 }
}

enum class Fase { BUSCANDO, A_CAMINHO, NO_LOCAL, TRABALHANDO, CONCLUIDO, CANCELADO }

data class Mensagem(val minha: Boolean, val texto: String)

class Pedido(
    val id: String,
    val categoria: String,
    val descricao: String,
    val bairro: String,
    val endereco: String,
    val cliente: Pessoa,
    val urgente: Boolean,
    val oferta: Int,
    val distanciaKm: Double,
    val criado: Long = System.currentTimeMillis()
) {
    var profissional by mutableStateOf<Pessoa?>(null)
    var valor by mutableStateOf<Int?>(null)
    var fase by mutableStateOf(Fase.BUSCANDO)
    val propostas = mutableStateListOf<Proposta>()
    val mensagens = mutableStateListOf<Mensagem>()
    var inicioRota by mutableLongStateOf(0L)
    val duracaoRota = 25_000L
    val codigo = "2217"
    var inicioTrabalho by mutableLongStateOf(0L)
    var notaDoCliente by mutableStateOf<Int?>(null)
    var notaDoProfissional by mutableStateOf<Int?>(null)
    var canceladoPor by mutableStateOf<Lado?>(null)
    var taxaCancelamento by mutableIntStateOf(0)

    fun progresso(agora: Long) = if (inicioRota == 0L) 0f else ((agora - inicioRota).toFloat() / duracaoRota).coerceIn(0f, 1f)
    fun distanciaRestante(agora: Long) = distanciaKm * (1 - progresso(agora))
    /* Última proposta de um profissional: é ela que manda na negociação com ele. */
    fun ultimaCom(p: Pessoa) = propostas.lastOrNull { it.profissional == p }
}

fun km(d: Double) = if (d < 1) "${(d * 1000).roundToInt()} m" else "%.1f km".format(d).replace('.', ',')
fun tempoDesde(inicio: Long, agora: Long): String {
    val s = ((agora - inicio) / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/* ---------- Moderação da conversa ---------- */
/* Números, valores e contatos não podem ir pelo chat: protege os dois lados e evita o serviço por fora. */
private val PALAVRAS_NUMERO = setOf("zero", "um", "uma", "dois", "duas", "tres", "três", "quatro", "cinco", "seis", "sete", "oito", "nove",
    "dez", "onze", "doze", "treze", "vinte", "trinta", "quarenta", "cinquenta", "cem", "cento", "duzentos", "trezentos", "mil")
private val PALAVRAS_CONTATO = listOf("whatsapp", "whats", "zap", "wpp", "telefone", "celular", "cpf", "pix", "instagram", "insta", "e-mail", "email", "@", ".com", "por fora", "fora do app")

fun motivoBloqueio(texto: String): String? {
    val t = texto.lowercase()
    if (Regex("\\d").containsMatchIn(t)) return "números"
    if ("r$" in t || Regex("\\breais\\b").containsMatchIn(t)) return "valores"
    val palavras = Regex("[a-zà-ú]+").findAll(t).map { it.value }.toList()
    if (palavras.count { it in PALAVRAS_NUMERO } >= 2) return "números escritos por extenso"
    PALAVRAS_CONTATO.firstOrNull { it in t }?.let { return "contato fora do app" }
    return null
}

enum class StatusRecurso { NENHUM, EM_ANALISE, APROVADO }

class Moderacao {
    var avisos by mutableIntStateOf(0)
    var punidoAte by mutableLongStateOf(0L)
    var recurso by mutableStateOf(StatusRecurso.NENHUM)
    var textoRecurso by mutableStateOf("")
    fun suspenso(agora: Long = System.currentTimeMillis()) = avisos < 3 && punidoAte > agora
    val banido get() = avisos >= 3
    fun registrarInfracao() {
        avisos += 1
        if (avisos == 2) punidoAte = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
    }
    fun aprovarRecurso() { avisos = (avisos - 1).coerceAtLeast(0); punidoAte = 0L; recurso = StatusRecurso.APROVADO }
}

/* ---------- Pessoas simuladas ---------- */
val PROFISSIONAIS_SIMULADOS = listOf(Pessoa("Marcos Vinícius", 4.9, 128), Pessoa("Diego Ferreira", 4.7, 63), Pessoa("Rafael Lima", 4.8, 91))
private val CLIENTES_SIMULADOS = listOf(Pessoa("Marina Souza", 4.9, 32), Pessoa("Rafael Torres", 4.6, 11), Pessoa("Sônia Melo", 5.0, 7),
    Pessoa("Paulo Henrique", 4.3, 5), Pessoa("Júlia Ramos", 4.8, 19))
private val SERVICOS_ELETRICA = listOf(
    Triple("Troca de chuveiro. O disjuntor desarma quando liga no quente.", "Boa Viagem", "Rua dos Navegantes, 1400, Boa Viagem, Recife"),
    Triple("Tomada da cozinha escureceu e parou de funcionar.", "Pina", "Av. Herculano Bandeira, 500, Pina, Recife"),
    Triple("Instalar ventilador de teto no quarto. O ventilador já está comprado.", "Espinheiro", "Rua do Espinheiro, 300, Espinheiro, Recife"),
    Triple("Metade da casa ficou sem luz depois da chuva.", "Casa Forte", "Praça de Casa Forte, 20, Casa Forte, Recife"),
    Triple("Trocar o quadro de disjuntores antigo.", "Madalena", "Rua Benfica, 77, Madalena, Recife")
)
private var sequencia = 0
fun novaOportunidade(urgente: Boolean): Pedido {
    val i = sequencia++
    val (desc, bairro, end) = SERVICOS_ELETRICA[i % SERVICOS_ELETRICA.size]
    val oferta = listOf(150, 110, 190, 240, 380)[i % 5]
    return Pedido("op$i", "eletrica", desc, bairro, end, CLIENTES_SIMULADOS[i % CLIENTES_SIMULADOS.size], urgente, oferta,
        listOf(2.1, 3.4, 4.8, 1.6, 5.2)[i % 5])
}

val RESPOSTAS_PROFISSIONAL = listOf("Estou a caminho.", "Cheguei na portaria.", "Pode me mandar uma foto do problema?")
val RESPOSTAS_CLIENTE = listOf("Estou em casa.", "Pode subir direto.", "Obrigada!")
fun respostaDoOutroLado(texto: String) = when (texto) {
    "Estou a caminho." -> "Perfeito, te espero."
    "Cheguei na portaria." -> "Já vou liberar sua entrada."
    "Pode me mandar uma foto do problema?" -> "Mando sim, um minuto."
    "Estou em casa." -> "Ótimo, já estou chegando."
    else -> "Certo!"
}

/* ---------- Estado do app ---------- */
class EstadoApp(perfilInicial: Perfil? = null) {
    var perfil by mutableStateOf(perfilInicial)
    val cliente = Pessoa("Ana Beatriz", 4.8, 12)
    val profissional = Pessoa("Carlos Andrade", 4.9, 87)
    val enderecos = listOf("Casa" to "Rua das Graças, 320, Graças, Recife", "Trabalho" to "Av. Conde da Boa Vista, 1200, Boa Vista, Recife")

    val pedidosCliente = mutableStateListOf<Pedido>()
    val oportunidades = mutableStateListOf<Pedido>()
    var urgente by mutableStateOf<Pedido?>(null)
    var atendimento by mutableStateOf<Pedido?>(null)
    val concluidosProfissional = mutableStateListOf<Pedido>()
    var online by mutableStateOf(true)
    val moderacao = mapOf(Lado.CLIENTE to Moderacao(), Lado.PROFISSIONAL to Moderacao())
    var dialogoModeracao by mutableStateOf<Pair<Lado, String>?>(null)
    var voltouDoWaze by mutableStateOf(false)
    var aviso by mutableStateOf<String?>(null)
    var recursoAberto by mutableStateOf<Lado?>(null)

    init {
        /* Histórico inicial: dois atendimentos já encerrados, com a conversa guardada. */
        val antigo = Pedido("h1", "hidraulica", "Troca do registro do chuveiro.", "Graças", enderecos[0].second, cliente, false, 180, 2.0,
            System.currentTimeMillis() - 9 * 86_400_000L).apply {
            profissional = PROFISSIONAIS_SIMULADOS[1]; valor = 180; fase = Fase.CONCLUIDO; notaDoCliente = 5
            mensagens.addAll(listOf(Mensagem(false, "Oi! Já estou a caminho."), Mensagem(true, "Estou em casa."), Mensagem(false, "Cheguei na portaria.")))
        }
        pedidosCliente.add(antigo)
        repeat(3) { oportunidades.add(novaOportunidade(false)) }
    }

    fun moderacaoDe(perfil: Perfil) = moderacao.getValue(perfil.lado())

    val pedidoAtivoCliente get() = pedidosCliente.firstOrNull {
        it.fase in setOf(Fase.BUSCANDO, Fase.A_CAMINHO, Fase.NO_LOCAL, Fase.TRABALHANDO) || (it.fase == Fase.CONCLUIDO && it.notaDoCliente == null)
    }

    fun criarPedido(cat: Categoria, descricao: String, endereco: String, urgente: Boolean, oferta: Int): Pedido {
        val p = Pedido("c" + System.currentTimeMillis(), cat.id, descricao.ifBlank { "Sem descrição." }, endereco.split(", ").getOrElse(2) { "" },
            endereco, cliente, urgente, oferta, 3.2)
        pedidosCliente.add(0, p)
        return p
    }

    /* O profissional (Carlos) fecha negócio com um cliente simulado. */
    fun conectarProfissional(p: Pedido, valor: Int) {
        oportunidades.remove(p); if (urgente == p) urgente = null
        p.profissional = profissional; p.valor = valor; p.fase = Fase.A_CAMINHO; p.inicioRota = System.currentTimeMillis()
        p.mensagens.add(Mensagem(false, "Oi, ${profissional.primeiroNome}! Estou em casa te esperando."))
        atendimento = p
    }

    /* A cliente (Ana) aceita a proposta de um profissional simulado. */
    fun conectarCliente(p: Pedido, proposta: Proposta) {
        proposta.status = StatusProposta.ACEITA
        p.propostas.filter { it !== proposta && it.status == StatusProposta.PENDENTE }.forEach { it.status = StatusProposta.RECUSADA }
        p.profissional = proposta.profissional; p.valor = proposta.valor; p.fase = Fase.A_CAMINHO; p.inicioRota = System.currentTimeMillis()
        p.mensagens.add(Mensagem(false, "Oi, ${cliente.primeiroNome}! Aceitei e já estou indo."))
    }

    fun taxaCancelamento(p: Pedido) = ((p.valor ?: p.oferta) * percentualCancelamento(p.fase) / 100.0).roundToInt()

    fun cancelar(p: Pedido, por: Lado) {
        p.taxaCancelamento = taxaCancelamento(p); p.canceladoPor = por; p.fase = Fase.CANCELADO
        if (atendimento == p) { concluidosProfissional.add(0, p); atendimento = null }
    }

    fun ficarDisponivel() {
        atendimento?.let { if (it !in concluidosProfissional) concluidosProfissional.add(0, it) }
        atendimento = null; online = true
    }
}

/* Um só estado para o processo: a Activity também mexe nele (volta do Waze). */
object Global { var estado by mutableStateOf(EstadoApp()) }
