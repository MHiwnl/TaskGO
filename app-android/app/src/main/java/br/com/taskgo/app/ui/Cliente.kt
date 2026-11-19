package br.com.taskgo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.taskgo.app.CATEGORIAS
import br.com.taskgo.app.Categoria
import br.com.taskgo.app.Fase
import br.com.taskgo.app.Global
import br.com.taskgo.app.Lado
import br.com.taskgo.app.PROFISSIONAIS_SIMULADOS
import br.com.taskgo.app.Pedido
import br.com.taskgo.app.Perfil
import br.com.taskgo.app.Proposta
import br.com.taskgo.app.StatusProposta
import br.com.taskgo.app.arredonda5
import br.com.taskgo.app.categoria
import br.com.taskgo.app.km
import br.com.taskgo.app.reais
import br.com.taskgo.app.tempoDesde
import kotlinx.coroutines.delay

private sealed interface TelaCliente {
    data class Categorias(val urgente: Boolean) : TelaCliente
    data class NovoPedido(val cat: Categoria, val urgente: Boolean) : TelaCliente
    data class Chat(val p: Pedido, val leitura: Boolean) : TelaCliente
}

@Composable
fun AppCliente(aoTrocarPerfil: () -> Unit, aoSair: () -> Unit) {
    val estado = Global.estado
    val mod = estado.moderacao.getValue(Lado.CLIENTE)
    var aba by remember { mutableIntStateOf(0) }
    var tela by remember { mutableStateOf<TelaCliente?>(null) }
    val ativo = estado.pedidoAtivoCliente

    if (mod.banido) { TelaBanida(Lado.CLIENTE, { estado.recursoAberto = Lado.CLIENTE }, aoTrocarPerfil); return }
    SimulacaoProfissional(ativo)
    LaunchedEffect(ativo?.id) { if (ativo != null && tela !is TelaCliente.Chat) tela = null }
    BackHandler(tela != null) { tela = when (val t = tela) { is TelaCliente.NovoPedido -> TelaCliente.Categorias(t.urgente); else -> null } }
    BackHandler(tela == null && ativo == null && aba != 0) { aba = 0 }

    when (val t = tela) {
        is TelaCliente.Chat -> TelaConversa(t.p, t.p.profissional ?: estado.cliente, Perfil.CLIENTE, t.leitura) { tela = null }
        is TelaCliente.Categorias -> TelaCategorias(t.urgente, { tela = null }) { tela = TelaCliente.NovoPedido(it, t.urgente) }
        is TelaCliente.NovoPedido -> TelaNovoPedido(t.cat, t.urgente, { tela = TelaCliente.Categorias(t.urgente) }) { tela = null }
        null -> if (ativo != null) Acompanhamento(ativo) { tela = TelaCliente.Chat(ativo, false) } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (aba) {
                    0 -> InicioCliente({ tela = TelaCliente.Categorias(it) }) { c, u -> tela = TelaCliente.NovoPedido(c, u) }
                    1 -> PedidosCliente { tela = TelaCliente.Chat(it, true) }
                    2 -> HistoricoConversas(estado.pedidosCliente.filter { it != ativo && it.mensagens.isNotEmpty() }, { it.profissional ?: estado.cliente }) { tela = TelaCliente.Chat(it, true) }
                    else -> PerfilConta(Perfil.CLIENTE, aoTrocarPerfil, aoSair)
                }
            }
            BarraAbas(listOf(Aba("Início", Icons.Outlined.Home), Aba("Pedidos", Icons.AutoMirrored.Outlined.ListAlt), Aba("Conversas", Icons.Outlined.Forum), Aba("Perfil", Icons.Outlined.Person)), aba) { aba = it }
        }
    }
}

/* Na demonstração, o lado do profissional é simulado: propostas chegam ao vivo e o atendimento avança sozinho. */
@Composable
private fun SimulacaoProfissional(p: Pedido?) {
    val estado = Global.estado
    LaunchedEffect(p?.id, p?.fase) {
        p ?: return@LaunchedEffect
        when (p.fase) {
            Fase.BUSCANDO -> listOf(2500L to 1.0, 4000L to 1.15, 4500L to .95).forEachIndexed { i, (espera, fator) ->
                delay(espera)
                if (p.fase != Fase.BUSCANDO) return@LaunchedEffect
                if (p.propostas.none { it.profissional == PROFISSIONAIS_SIMULADOS[i] })
                    p.propostas.add(Proposta(Lado.PROFISSIONAL, PROFISSIONAIS_SIMULADOS[i], arredonda5(p.oferta * fator)))
            }
            Fase.A_CAMINHO -> { delay((p.inicioRota + p.duracaoRota - System.currentTimeMillis()).coerceAtLeast(0) + 1500); if (p.fase == Fase.A_CAMINHO) p.fase = Fase.NO_LOCAL }
            Fase.NO_LOCAL -> { delay(9000); if (p.fase == Fase.NO_LOCAL) { p.inicioTrabalho = System.currentTimeMillis(); p.fase = Fase.TRABALHANDO } }
            Fase.TRABALHANDO -> { delay(14000); if (p.fase == Fase.TRABALHANDO) p.fase = Fase.CONCLUIDO }
            else -> Unit
        }
    }
    /* Resposta do profissional a uma contraproposta da cliente. */
    val pendente = p?.propostas?.lastOrNull { it.de == Lado.CLIENTE && it.status == StatusProposta.PENDENTE }
    LaunchedEffect(pendente?.id) {
        val c = pendente ?: return@LaunchedEffect
        delay(2500)
        if (c.status != StatusProposta.PENDENTE || p.fase != Fase.BUSCANDO) return@LaunchedEffect
        val anterior = p.propostas.lastOrNull { it.profissional == c.profissional && it.de == Lado.PROFISSIONAL }?.valor ?: c.valor
        if (c.valor >= anterior * .9) estado.conectarCliente(p, c)
        else { c.status = StatusProposta.SUBSTITUIDA; p.propostas.add(Proposta(Lado.PROFISSIONAL, c.profissional, arredonda5((c.valor + anterior) / 2.0))) }
    }
}

/* ---------- Início ---------- */
@Composable
private fun InicioCliente(aoProcurar: (Boolean) -> Unit, aoEscolher: (Categoria, Boolean) -> Unit) {
    val estado = Global.estado
    val eu = estado.cliente
    val suspenso = estado.moderacao.getValue(Lado.CLIENTE).suspenso()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FaixaAzul {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(eu.nome, 52.dp, Cor.Branco); Spacer(Modifier.width(12.dp))
                    Column { Text("Olá, ${eu.primeiroNome}", style = Estilo.titulo.copy(color = Cor.Branco, fontSize = 24.sp)); Nota(eu.nota, eu.avaliacoes, 15, Cor.Branco) }
                }
                Text("Do que você precisa hoje?", style = Estilo.vozForte.copy(fontSize = 28.sp, color = Cor.Branco), modifier = Modifier.padding(top = 20.dp, bottom = 14.dp))
                Row(Modifier.fillMaxWidth().clip(Forma.botao).background(Cor.Cartao).toque { aoProcurar(false) }.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = Cor.Azul); Spacer(Modifier.width(10.dp))
                    Text("Procurar um serviço", style = Estilo.corpo.copy(color = Cor.Texto2))
                }
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        if (suspenso) { AvisoSuspensao(Lado.CLIENTE) { estado.recursoAberto = Lado.CLIENTE }; Spacer(Modifier.height(12.dp)) }
        Cartao(cor = Cor.AmbarFundo, borda = Cor.Ambar, onClick = { aoProcurar(true) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeCirculo(Icons.Filled.Bolt, Cor.Texto, Cor.Ambar, 48.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Precisa agora?", style = Estilo.item.copy(fontSize = 17.sp, color = Cor.AmbarTexto))
                    Text("Peça com urgência: o pedido chega na hora aos profissionais online.", style = Estilo.nota.copy(color = Cor.Texto2))
                }
            }
        }
        TituloSecao("Mais pedidos")
        GradeCategorias(CATEGORIAS.filter { it.popular }) { aoEscolher(it, false) }
        Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp)) { LinkPequeno("Ver todas as ${CATEGORIAS.size} categorias", Cor.AzulLeitura) { aoProcurar(false) } }
        Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GradeCategorias(lista: List<Categoria>, aoEscolher: (Categoria) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lista.chunked(2).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                linha.forEach { c ->
                    Cartao(Modifier.weight(1f), onClick = { aoEscolher(c) }) {
                        IconeCirculo(c.icone)
                        Text(c.nome, style = Estilo.item, modifier = Modifier.padding(top = 12.dp))
                        Text("a partir de ${reais(c.min)}", style = Estilo.nota)
                    }
                }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TelaCategorias(urgente: Boolean, aoVoltar: () -> Unit, aoEscolher: (Categoria) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Topo(if (urgente) "Pedido urgente" else "Categorias", aoVoltar) { if (urgente) Pilula("URGENTE", Cor.AmbarTexto, Cor.AmbarFundo, Icons.Filled.Bolt) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            GradeCategorias(CATEGORIAS, aoEscolher)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/* ---------- Novo pedido ---------- */
@Composable
private fun TelaNovoPedido(cat: Categoria, urgenteInicial: Boolean, aoVoltar: () -> Unit, aoEnviar: () -> Unit) {
    val estado = Global.estado
    val suspenso = estado.moderacao.getValue(Lado.CLIENTE).suspenso()
    var descricao by remember { mutableStateOf("") }
    var endereco by remember { mutableIntStateOf(0) }
    var quando by remember { mutableIntStateOf(if (urgenteInicial) 0 else 1) }
    val referencia = arredonda5(cat.min * 1.6)
    var oferta by remember { mutableIntStateOf(referencia) }
    Column(Modifier.fillMaxSize()) {
        Topo(cat.nome, aoVoltar)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Cartao {
                Text("O que está acontecendo?", style = Estilo.item)
                BasicTextField(descricao, { descricao = it.take(300) }, textStyle = Estilo.corpo.copy(color = Cor.Texto), cursorBrush = SolidColor(Cor.Azul),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(Forma.botao).background(Cor.Cartao2).heightIn(min = 90.dp).padding(14.dp),
                    decorationBox = { c -> Box { if (descricao.isEmpty()) Text("Ex.: o chuveiro desarma o disjuntor quando liga no quente.", style = Estilo.corpo.copy(color = Cor.Apagado)); c() } })
            }
            Cartao {
                Text("Onde?", style = Estilo.item)
                estado.enderecos.forEachIndexed { i, (nome, end) -> Opcao(Icons.Outlined.LocationOn, nome, end, i == endereco) { endereco = i } }
            }
            Cartao {
                Text("Quando?", style = Estilo.item, modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(Icons.Filled.Bolt, "Urgente", Cor.Ambar), Triple(Icons.Outlined.Today, "Hoje", Cor.AzulLeitura), Triple(Icons.Outlined.CalendarMonth, "Agendar", Cor.AzulLeitura))
                        .forEachIndexed { i, (ic, r, cor) ->
                            val sel = i == quando
                            Column(Modifier.weight(1f).clip(Forma.botao).background(if (sel) (if (i == 0) Cor.AmbarFundo else Cor.AzulFundo) else Cor.Cartao2)
                                .border(1.5.dp, if (sel) cor else Color.Transparent, Forma.botao).toque { quando = i }.padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(ic, null, tint = if (sel) cor else Cor.Texto2)
                                Text(r, style = Estilo.rotulo.copy(color = if (sel) Cor.Texto else Cor.Texto2), modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                }
                if (quando == 0) Text("O pedido chega na hora aos profissionais online, com destaque.", style = Estilo.nota.copy(color = Cor.AmbarTexto), modifier = Modifier.padding(top = 10.dp))
            }
            Cartao {
                Text("Quanto você oferece?", style = Estilo.item)
                Text("Valor total do serviço. Os profissionais podem aceitar ou mandar outra proposta.", style = Estilo.nota, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                SeletorValor(oferta, { oferta = it }, referencia, false)
            }
            if (suspenso) AvisoSuspensao(Lado.CLIENTE) { estado.recursoAberto = Lado.CLIENTE }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.padding(18.dp)) {
            Botao(if (quando == 0) "Pedir com urgência · ${reais(oferta)}" else "Enviar pedido · ${reais(oferta)}", {
                estado.criarPedido(cat, descricao, estado.enderecos[endereco].second, quando == 0, oferta); aoEnviar()
            }, Modifier.fillMaxWidth(), if (quando == 0) TipoBotao.CLARO else TipoBotao.PRIMARIO, ativo = !suspenso, alto = true,
                icone = if (quando == 0) Icons.Filled.Bolt else null)
        }
    }
}

@Composable
private fun Opcao(icone: ImageVector, titulo: String, apoio: String, marcada: Boolean, aoTocar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(Forma.botao).background(if (marcada) Cor.AzulFundo else Cor.Cartao2)
        .border(1.5.dp, if (marcada) Cor.Azul else Color.Transparent, Forma.botao).toque { aoTocar() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, null, tint = if (marcada) Cor.AzulLeitura else Cor.Texto2); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(titulo, style = Estilo.item); Text(apoio, style = Estilo.nota.copy(color = Cor.Texto2)) }
    }
}

/* ---------- Acompanhamento do pedido ativo ---------- */
@Composable
private fun Acompanhamento(p: Pedido, aoConversar: () -> Unit) {
    when (p.fase) {
        Fase.BUSCANDO -> TelaPropostas(p)
        Fase.A_CAMINHO -> TelaACaminho(p, aoConversar)
        Fase.NO_LOCAL -> TelaChegouCliente(p, aoConversar)
        Fase.TRABALHANDO -> TelaEmAndamento(p, aoConversar)
        Fase.CONCLUIDO -> TelaAvaliar(p)
        else -> Unit
    }
}

@Composable
private fun Radar(icone: ImageVector) {
    val t by rememberInfiniteTransition(label = "radar").animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Restart), label = "r")
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            listOf(0f, .33f, .66f).forEach { d ->
                val f = (t + d) % 1f
                drawCircle(Cor.Azul.copy(alpha = (1 - f) * .7f), radius = size.minDimension / 2 * f, style = Stroke(4f))
            }
        }
        IconeCirculo(icone, Cor.Branco, Cor.Azul, 64.dp)
    }
}

@Composable
private fun TelaPropostas(p: Pedido) {
    val estado = Global.estado
    val cat = categoria(p.categoria)
    var contra by remember { mutableStateOf<Proposta?>(null) }
    var cancelar by remember { mutableStateOf(false) }
    val profissionais = p.propostas.map { it.profissional }.distinct()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Radar(cat.icone)
        Text(if (profissionais.isEmpty()) "Procurando profissionais" else "Propostas chegando", style = Estilo.vozForte.copy(fontSize = 26.sp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.urgente) { Pilula("URGENTE", Cor.AmbarTexto, Cor.AmbarFundo, Icons.Filled.Bolt); Spacer(Modifier.width(8.dp)) }
            Text("${cat.nome} · sua oferta ${reais(p.oferta)}", style = Estilo.corpo)
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            profissionais.forEachIndexed { i, pro ->
                val ult = p.ultimaCom(pro)!!
                var visivel by remember(pro) { mutableStateOf(false) }
                LaunchedEffect(pro) { visivel = true }
                AnimatedVisibility(visivel, enter = fadeIn() + slideInVertically { it / 2 }) {
                    Cartao(aba = when { ult.status == StatusProposta.RECUSADA -> "RECUSADA"; ult.de == Lado.CLIENTE && ult.status == StatusProposta.PENDENTE -> "SUA CONTRAPROPOSTA"; ult.de == Lado.PROFISSIONAL && ult.status == StatusProposta.PENDENTE -> "PROPOSTA NOVA"; else -> null },
                        corAba = when { ult.status == StatusProposta.RECUSADA -> Cor.Apagado; ult.de == Lado.CLIENTE -> Cor.Ambar; else -> Cor.Azul }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(pro.nome, 48.dp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(pro.nome, style = Estilo.item); Nota(pro.nota, pro.avaliacoes, 13); Text("a ${km(listOf(1.8, 2.6, 3.9)[i % 3])}", style = Estilo.nota) }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (ult.de == Lado.PROFISSIONAL) "propõe" else "você propôs", style = Estilo.nota)
                                ValorAnimado(ult.valor, Estilo.numero.copy(fontSize = 22.sp))
                            }
                        }
                        when {
                            ult.status == StatusProposta.RECUSADA -> Text("Proposta recusada.", style = Estilo.nota, modifier = Modifier.padding(top = 10.dp))
                            ult.de == Lado.CLIENTE && ult.status == StatusProposta.PENDENTE -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                PontoPulsante(Cor.AzulLeitura); Spacer(Modifier.width(8.dp)); Text("Aguardando ${pro.primeiroNome}", style = Estilo.corpo, modifier = Modifier.weight(1f))
                                LinkPequeno("Cancelar proposta", Cor.Vermelho) { ult.status = StatusProposta.CANCELADA }
                            }
                            ult.status == StatusProposta.CANCELADA -> Row(Modifier.padding(top = 8.dp)) { LinkPequeno("Fazer outra proposta", Cor.AzulLeitura) { contra = ult } }
                            else -> Column(Modifier.padding(top = 14.dp)) {
                                Botao("Aceitar ${reais(ult.valor)}", { estado.conectarCliente(p, ult) }, Modifier.fillMaxWidth(), TipoBotao.SUCESSO)
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Botao("Contraproposta", { contra = ult }, Modifier.weight(1f), TipoBotao.CONTORNO)
                                    Botao("Recusar", { ult.status = StatusProposta.RECUSADA }, Modifier.weight(1f), TipoBotao.CONTORNO)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (profissionais.isEmpty()) Text("Avisando os profissionais de ${cat.nome.lowercase()} perto de você…", style = Estilo.nota)
        Spacer(Modifier.height(20.dp))
        LinkPequeno("Cancelar pedido", Cor.Vermelho) { cancelar = true }
        Spacer(Modifier.height(20.dp))
    }
    contra?.let { base ->
        DialogoProposta(base.valor, false, { contra = null }) { v ->
            if (base.status == StatusProposta.PENDENTE) base.status = StatusProposta.SUBSTITUIDA
            p.propostas.add(Proposta(Lado.CLIENTE, base.profissional, v))
        }
    }
    if (cancelar) Confirmar("Cancelar pedido?", "Antes do aceite, cancelar não tem taxa.", "Cancelar pedido", { estado.cancelar(p, Lado.CLIENTE); estado.aviso = "Pedido cancelado." }, { cancelar = false }, perigo = true)
}

@Composable
private fun CabecalhoProfissional(p: Pedido, status: String, aoConversar: () -> Unit) {
    val pro = p.profissional ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(pro.nome, 52.dp); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(pro.nome, style = Estilo.item.copy(fontSize = 17.sp)); Nota(pro.nota, pro.avaliacoes, 13); Text(status, style = Estilo.nota.copy(color = Cor.AzulLeitura)) }
        Box(Modifier.size(52.dp).clip(CircleShape).background(Cor.AzulFundo).toque(onClick = aoConversar), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Chat, "Conversar", tint = Cor.AzulLeitura, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TelaACaminho(p: Pedido, aoConversar: () -> Unit) {
    val agora by agoraAoVivo()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapaRota(p.progresso(agora), Modifier.fillMaxSize())
            BotaoEmergencia(Modifier.align(Alignment.TopStart).padding(14.dp))
            Column(Modifier.align(Alignment.TopEnd).padding(14.dp).clip(Forma.botao).background(Cor.Cartao).padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                Text(km(p.distanciaRestante(agora)), style = Estilo.numero.copy(fontSize = 20.sp)); Text("até você", style = Estilo.nota)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Cor.Cartao).padding(20.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) { EtapasPontos(listOf("aceite", "caminho", "chegada", "fim"), 1, 54.dp) }
            CabecalhoProfissional(p, "a caminho do seu endereço", aoConversar)
            Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(Forma.cartao).background(Cor.Azul).padding(16.dp)) {
                Text("Seu código de chegada", style = Estilo.nota.copy(color = Cor.Branco))
                Row(Modifier.padding(vertical = 10.dp)) { CodigoGrande(p.codigo, Cor.Azul) }
                Text("Informe só quando o profissional chegar. Sem ele, o serviço não começa.", style = Estilo.nota.copy(color = Cor.Branco))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Valor combinado", style = Estilo.corpo, modifier = Modifier.weight(1f)); Text(reais(p.valor ?: 0), style = Estilo.numero.copy(fontSize = 20.sp))
            }
            Box(Modifier.align(Alignment.CenterHorizontally)) { CancelarAtendimento(p, Lado.CLIENTE) }
        }
    }
}

@Composable
private fun TelaChegouCliente(p: Pedido, aoConversar: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        PontoPulsante(Cor.Verde, 16.dp)
        EtapasPontos(listOf("aceite", "caminho", "chegada", "fim"), 3, 58.dp)
        Text("${p.profissional?.primeiroNome} chegou!", style = Estilo.vozForte.copy(fontSize = 30.sp), modifier = Modifier.padding(top = 18.dp))
        Text("Informe este código para ele começar o serviço:", style = Estilo.corpo, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
        CodigoGrande(p.codigo)
        Text("O código confirma que é a pessoa certa, no endereço certo.", style = Estilo.nota, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(28.dp))
        Cartao { CabecalhoProfissional(p, "no seu endereço", aoConversar) }
        Spacer(Modifier.height(12.dp))
        BotaoEmergencia(Modifier.fillMaxWidth())
        CancelarAtendimento(p, Lado.CLIENTE)
    }
}

@Composable
private fun TelaEmAndamento(p: Pedido, aoConversar: () -> Unit) {
    val agora by agoraAoVivo(1000)
    val giro by rememberInfiniteTransition(label = "giro").animateFloat(0f, 360f, infiniteRepeatable(tween(1800)), label = "g")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawArc(Cor.Azul, giro, 270f, false, style = Stroke(10f)) }
            Icon(Icons.Outlined.HourglassTop, null, tint = Cor.AzulLeitura, modifier = Modifier.size(42.dp))
        }
        Text("Serviço em andamento", style = Estilo.vozForte.copy(fontSize = 28.sp), modifier = Modifier.padding(top = 18.dp))
        Text("Código confirmado. ${p.profissional?.primeiroNome} está trabalhando.", style = Estilo.corpo, modifier = Modifier.padding(top = 6.dp))
        Text(tempoDesde(p.inicioTrabalho, agora), style = Estilo.numero.copy(fontSize = 40.sp), modifier = Modifier.padding(top = 18.dp))
        Spacer(Modifier.height(22.dp))
        Cartao { CabecalhoProfissional(p, "trabalhando no local", aoConversar) }
        Spacer(Modifier.height(12.dp))
        Cartao { Row { Text("Valor combinado", style = Estilo.corpo, modifier = Modifier.weight(1f)); Text(reais(p.valor ?: 0), style = Estilo.numero.copy(fontSize = 20.sp)) } }
        Spacer(Modifier.height(12.dp))
        BotaoEmergencia(Modifier.fillMaxWidth())
        CancelarAtendimento(p, Lado.CLIENTE)
    }
}

@Composable
private fun TelaAvaliar(p: Pedido) {
    val estado = Global.estado
    var nota by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        SeloFeito(76.dp)
        Text("Serviço concluído", style = Estilo.vozForte.copy(fontSize = 28.sp), modifier = Modifier.padding(top = 14.dp))
        Text("Valor do serviço", style = Estilo.nota, modifier = Modifier.padding(top = 16.dp))
        Text(reais(p.valor ?: 0), style = Estilo.numero.copy(fontSize = 36.sp))
        Spacer(Modifier.height(28.dp))
        Cartao {
            Text("Como foi o atendimento de ${p.profissional?.primeiroNome}?", style = Estilo.item.copy(fontSize = 17.sp))
            Text("Sua nota aparece para outros clientes antes de aceitar uma proposta.", style = Estilo.nota, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EscolherEstrelas(nota) { nota = it } }
        }
        Spacer(Modifier.height(20.dp))
        Botao("Enviar avaliação", { p.notaDoCliente = nota; estado.aviso = "Obrigado! Sua avaliação foi enviada." }, Modifier.fillMaxWidth(), ativo = nota > 0, alto = true)
    }
}

/* ---------- Pedidos (histórico) ---------- */
@Composable
private fun PedidosCliente(aoAbrirConversa: (Pedido) -> Unit) {
    val estado = Global.estado
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("Pedidos", style = Estilo.titulo.copy(fontSize = 26.sp), modifier = Modifier.padding(top = 22.dp, bottom = 14.dp))
        if (estado.pedidosCliente.isEmpty()) Text("Seus pedidos aparecem aqui.", style = Estilo.corpo)
        estado.pedidosCliente.forEach { p ->
            val cat = categoria(p.categoria)
            Cartao(Modifier.padding(bottom = 10.dp), onClick = if (p.mensagens.isNotEmpty()) ({ aoAbrirConversa(p) }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconeCirculo(cat.icone); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(cat.nome, style = Estilo.item); Text(p.profissional?.nome ?: "Sem profissional", style = Estilo.nota) }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(reais(p.valor ?: p.oferta), style = Estilo.registro)
                        Pilula(if (p.fase == Fase.CANCELADO) "Cancelado" else "Concluído", if (p.fase == Fase.CANCELADO) Cor.Vermelho else Cor.Verde, Cor.Cartao2)
                    }
                }
                if (p.taxaCancelamento > 0) Text("Taxa de cancelamento: ${reais(p.taxaCancelamento)}", style = Estilo.nota.copy(color = Cor.Vermelho), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
