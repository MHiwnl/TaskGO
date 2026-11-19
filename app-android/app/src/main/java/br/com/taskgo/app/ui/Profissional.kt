package br.com.taskgo.app.ui

import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.VectorConverter
import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.taskgo.app.Fase
import br.com.taskgo.app.Global
import br.com.taskgo.app.Lado
import br.com.taskgo.app.Navegacao
import br.com.taskgo.app.Pedido
import br.com.taskgo.app.Perfil
import br.com.taskgo.app.Proposta
import br.com.taskgo.app.StatusProposta
import br.com.taskgo.app.arredonda5
import br.com.taskgo.app.km
import br.com.taskgo.app.liquido
import br.com.taskgo.app.novaOportunidade
import br.com.taskgo.app.percentualCancelamento
import br.com.taskgo.app.reais
import br.com.taskgo.app.taxaDe
import br.com.taskgo.app.tempoDesde
import kotlinx.coroutines.delay

private sealed interface TelaPro {
    data class Oportunidade(val p: Pedido) : TelaPro
    data class Chat(val p: Pedido, val leitura: Boolean) : TelaPro
}

@Composable
fun AppProfissional(aoTrocarPerfil: () -> Unit, aoSair: () -> Unit) {
    val estado = Global.estado
    val mod = estado.moderacao.getValue(Lado.PROFISSIONAL)
    var aba by remember { mutableIntStateOf(0) }
    var tela by remember { mutableStateOf<TelaPro?>(null) }
    val agora by agoraAoVivo(1000)
    val suspenso = mod.suspenso(agora)
    val at = estado.atendimento

    if (mod.banido) { TelaBanida(Lado.PROFISSIONAL, { estado.recursoAberto = Lado.PROFISSIONAL }, aoTrocarPerfil); return }

    LaunchedEffect(suspenso) { if (suspenso) estado.online = false }
    LaunchedEffect(at) { if (at != null && tela !is TelaPro.Chat) tela = null }
    /* Pedidos chegando em tempo real enquanto o profissional está online e livre. O primeiro é urgente. */
    LaunchedEffect(estado.online, at == null, suspenso) {
        if (!estado.online || at != null || suspenso) return@LaunchedEffect
        var n = 0
        while (true) {
            delay(if (n == 0) 6000 else 22000)
            val urgente = n % 2 == 0
            val p = novaOportunidade(urgente)
            estado.oportunidades.add(0, p)
            if (urgente && estado.urgente == null) estado.urgente = p
            n++
        }
    }
    BackHandler(tela != null) { tela = null }
    BackHandler(tela == null && at == null && aba != 0) { aba = 0 }

    Box(Modifier.fillMaxSize()) {
        when (val t = tela) {
            is TelaPro.Chat -> TelaConversa(t.p, t.p.cliente, Perfil.PROFISSIONAL, t.leitura) { tela = null }
            is TelaPro.Oportunidade -> if (at == null) DetalheOportunidade(t.p) { tela = null } else tela = null
            null -> if (at != null) Atendimento(at) { tela = TelaPro.Chat(at, false) } else Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (aba) {
                        0 -> InicioProfissional(suspenso) { tela = TelaPro.Oportunidade(it) }
                        1 -> Ganhos()
                        2 -> HistoricoConversas(estado.concluidosProfissional.filter { it.mensagens.isNotEmpty() }, { it.cliente }) { tela = TelaPro.Chat(it, true) }
                        else -> PerfilConta(Perfil.PROFISSIONAL, aoTrocarPerfil, aoSair)
                    }
                }
                BarraAbas(listOf(
                    Aba("Pedidos", Icons.Outlined.Home, if (estado.online) estado.oportunidades.size else 0),
                    Aba("Ganhos", Icons.Outlined.Payments),
                    Aba("Conversas", Icons.Outlined.Forum),
                    Aba("Perfil", Icons.Outlined.Person)
                ), aba) { aba = it }
            }
        }
        val urg = estado.urgente
        AnimatedVisibility(urg != null && at == null && tela == null && aba == 0 && estado.online, Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            if (urg != null) PopupUrgente(urg, { estado.conectarProfissional(urg, urg.oferta) }, { estado.urgente = null; tela = TelaPro.Oportunidade(urg) }) {
                estado.urgente = null; estado.oportunidades.remove(urg)
            }
        }
    }
}

/* ---------- Início: online/offline e oportunidades ---------- */
@Composable
private fun InicioProfissional(suspenso: Boolean, aoAbrir: (Pedido) -> Unit) {
    val estado = Global.estado
    val eu = estado.profissional
    val agora by agoraAoVivo(30_000)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FaixaAzul {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(eu.nome, 52.dp, Cor.Branco)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Olá, ${eu.primeiroNome}", style = Estilo.titulo.copy(color = Cor.Branco, fontSize = 24.sp))
                        Nota(eu.nota, eu.avaliacoes, 15, Cor.Branco)
                    }
                }
                Spacer(Modifier.height(18.dp))
                CartaoOnline(estado.online, !suspenso) { estado.online = !estado.online; if (!estado.online) estado.urgente = null }
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        if (suspenso) { AvisoSuspensao(Lado.PROFISSIONAL) { estado.recursoAberto = Lado.PROFISSIONAL }; Spacer(Modifier.height(12.dp)) }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val feitos = estado.concluidosProfissional.filter { it.fase == Fase.CONCLUIDO }
            Cartao(Modifier.weight(1f)) {
                Text("Ganhos", style = Estilo.nota)
                ValorAnimado(feitos.sumOf { liquido(it.valor ?: 0) }, Estilo.numero.copy(fontSize = 22.sp, color = Cor.Verde))
            }
            Cartao(Modifier.weight(1f)) {
                Text("Atendimentos", style = Estilo.nota)
                Text(feitos.size.toString(), style = Estilo.numero.copy(fontSize = 22.sp))
            }
        }

        TituloSecao("Oportunidades") { if (estado.online) Pilula("${estado.oportunidades.size} perto de você", Cor.AzulLeitura, Cor.AzulFundo) }
        when {
            !estado.online -> Text("Você está offline. Fique online para receber pedidos e propostas.", style = Estilo.corpo, modifier = Modifier.padding(vertical = 12.dp))
            estado.oportunidades.isEmpty() -> Text("Nenhum pedido agora. Os novos aparecem aqui na hora.", style = Estilo.corpo, modifier = Modifier.padding(vertical = 12.dp))
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                estado.oportunidades.forEach { p -> CartaoOportunidade(p, agora) { aoAbrir(p) } }
            }
        }
        Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CartaoOnline(online: Boolean, pode: Boolean, aoAlternar: () -> Unit) {
    /* Dentro da área azul, como no site: texto e linha brancos; o botão se inverte para branco. O estado também vai por extenso. */
    Column(Modifier.fillMaxWidth().drawBehind { drawLine(Cor.Branco.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (online) PontoPulsante(Cor.Branco, 12.dp) else Box(Modifier.size(12.dp).clip(CircleShape).border(1.5.dp, Cor.Branco, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (online) "Você está online" else "Você está offline", style = Estilo.item.copy(fontSize = 17.sp, color = Cor.Branco))
                Text(if (online) "Recebendo pedidos de Elétrica perto de você" else "Nenhum pedido ou proposta chega até você", style = Estilo.nota.copy(color = Cor.Branco))
            }
        }
        Spacer(Modifier.height(14.dp))
        Botao(if (online) "Ficar offline" else "Ficar online", aoAlternar, Modifier.fillMaxWidth(),
            TipoBotao.CLARO, ativo = pode, icone = Icons.Outlined.PowerSettingsNew)
    }
}

private fun haQuanto(p: Pedido, agora: Long): String {
    val min = ((agora - p.criado) / 60_000).toInt()
    return if (min < 1) "agora" else "há $min min"
}

@Composable
private fun CartaoOportunidade(p: Pedido, agora: Long, aoTocar: () -> Unit) {
    val ult = p.ultimaCom(Global.estado.profissional)
    val aba = when {
        ult?.de == Lado.CLIENTE && ult.status == StatusProposta.PENDENTE -> "CONTRAPROPOSTA" to Cor.Ambar
        ult?.de == Lado.PROFISSIONAL && ult.status == StatusProposta.PENDENTE -> "PROPOSTA ENVIADA" to Cor.Azul
        p.urgente -> "URGENTE" to Cor.Ambar
        else -> null
    }
    Cartao(onClick = aoTocar, aba = aba?.first, corAba = aba?.second ?: Cor.Azul) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.cliente.nome, 44.dp, Cor.Cartao2)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(p.cliente.nome, style = Estilo.item); Nota(p.cliente.nota, p.cliente.avaliacoes, 14) }
            Column(horizontalAlignment = Alignment.End) { Text(km(p.distanciaKm), style = Estilo.numero.copy(fontSize = 16.sp)); Text(p.bairro, style = Estilo.nota) }
        }
        Text(p.descricao, style = Estilo.corpo.copy(color = Cor.Texto), modifier = Modifier.padding(top = 12.dp))
        Text(haQuanto(p, agora), style = Estilo.nota, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Text("Cliente oferece", style = Estilo.nota); Text(reais(p.oferta), style = Estilo.numero.copy(fontSize = 20.sp)) }
            Column(horizontalAlignment = Alignment.End) { Text("Você recebe", style = Estilo.nota); Text(reais(liquido(p.oferta)), style = Estilo.numero.copy(fontSize = 20.sp, color = Cor.Verde)) }
        }
    }
}

/* Pedido urgente chegando na hora: 30 segundos para decidir. */
@Composable
private fun PopupUrgente(p: Pedido, aoAceitar: () -> Unit, aoPropor: () -> Unit, aoRecusar: () -> Unit) {
    val tempo = remember(p.id) { Animatable(1f) }
    LaunchedEffect(p.id) { tempo.animateTo(0f, tween(30_000, easing = LinearEasing)); Global.estado.urgente = null }

    /* Gesto: puxar para baixo aceita; arrastar para o lado ou para cima recusa. Soltar antes do ponto devolve o cartão. */
    val desloc = remember(p.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val escopo = rememberCoroutineScope()
    val vibrar = LocalHapticFeedback.current
    val limite = with(LocalDensity.current) { 110.dp.toPx() }
    fun intencao(v: Offset) = when {
        v.y > limite && v.y > abs(v.x) -> 1
        v.y < -limite || abs(v.x) > limite -> -1
        else -> 0
    }
    val agora = intencao(desloc.value)
    LaunchedEffect(agora) { if (agora != 0) vibrar.performHapticFeedback(HapticFeedbackType.LongPress) }
    val borda by animateColorAsState(when (agora) { 1 -> Cor.VerdeForte; -1 -> Cor.VermelhoForte; else -> Cor.Ambar }, tween(120), label = "borda")

    Column(Modifier.padding(12.dp).fillMaxWidth()
        .offset { IntOffset(desloc.value.x.roundToInt(), desloc.value.y.roundToInt()) }
        .graphicsLayer { rotationZ = desloc.value.x / 45f; alpha = (1f - abs(desloc.value.x) / (limite * 4)).coerceIn(.3f, 1f) }
        .pointerInput(p.id) {
            detectDragGestures(
                onDragEnd = {
                    escopo.launch {
                        val v = desloc.value
                        when (intencao(v)) {
                            1 -> { desloc.animateTo(Offset(0f, v.y + 600f), tween(160)); aoAceitar() }
                            -1 -> { desloc.animateTo(Offset(v.x * 4, if (v.y < 0) v.y * 4 else v.y), tween(180)); aoRecusar() }
                            else -> desloc.animateTo(Offset.Zero, spring(dampingRatio = .6f))
                        }
                    }
                },
                onDragCancel = { escopo.launch { desloc.animateTo(Offset.Zero, spring(dampingRatio = .6f)) } },
                onDrag = { mudanca, d -> mudanca.consume(); escopo.launch { desloc.snapTo(desloc.value + d) } }
            )
        }
        .background(Cor.Cartao).border(2.dp, borda).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pilula("PEDIDO URGENTE", Cor.Preto, Cor.Ambar, Icons.Filled.Bolt)
            Spacer(Modifier.weight(1f))
            Text("${(tempo.value * 30).toInt()}s", style = Estilo.registro.copy(color = Cor.AmbarTexto))
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp).background(Cor.Cartao2)) {
            Box(Modifier.fillMaxWidth(tempo.value).height(4.dp).background(Cor.Ambar))
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.cliente.nome, 40.dp, Cor.Cartao2); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(p.cliente.nome, style = Estilo.item); Nota(p.cliente.nota, p.cliente.avaliacoes, 13) }
            Column(horizontalAlignment = Alignment.End) { Text(reais(p.oferta), style = Estilo.numero.copy(fontSize = 20.sp)); Text("você recebe ${reais(liquido(p.oferta))}", style = Estilo.nota.copy(color = Cor.Verde)) }
        }
        Text(p.descricao, style = Estilo.corpo.copy(color = Cor.Texto), modifier = Modifier.padding(top = 10.dp))
        Text("${p.bairro} · ${km(p.distanciaKm)}", style = Estilo.nota)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Botao("Propor", aoPropor, Modifier.weight(1f), TipoBotao.CONTORNO)
            Botao("Aceitar", aoAceitar, Modifier.weight(1.4f), TipoBotao.SUCESSO)
        }
        /* A dica do gesto vira a confirmação do que vai acontecer ao soltar. */
        Text(when (agora) { 1 -> "Solte para aceitar"; -1 -> "Solte para recusar"; else -> "Puxe para baixo para aceitar · para o lado ou para cima para recusar" },
            style = Estilo.rotulo.copy(color = when (agora) { 1 -> Cor.Verde; -1 -> Cor.Vermelho; else -> Cor.Apagado }, fontWeight = if (agora != 0) FontWeight.Bold else FontWeight.Medium),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) { LinkPequeno("Recusar", aoTocar = aoRecusar) }
    }
}

/* ---------- Detalhe e negociação ---------- */
@Composable
private fun DetalheOportunidade(p: Pedido, aoVoltar: () -> Unit) {
    val estado = Global.estado
    val eu = estado.profissional
    val ult = p.ultimaCom(eu)
    var propor by remember { mutableStateOf(false) }

    /* O cliente simulado responde em 2,5 s: aceita, faz contraproposta ou recusa. */
    LaunchedEffect(ult?.id, ult?.status) {
        val u = ult ?: return@LaunchedEffect
        if (u.de != Lado.PROFISSIONAL || u.status != StatusProposta.PENDENTE) return@LaunchedEffect
        delay(2500)
        if (u.status != StatusProposta.PENDENTE) return@LaunchedEffect
        val contras = p.propostas.count { it.de == Lado.CLIENTE }
        when {
            u.valor <= p.oferta * 1.10 -> { u.status = StatusProposta.ACEITA; estado.conectarProfissional(p, u.valor) }
            u.valor <= p.oferta * 1.5 && contras < 2 -> { u.status = StatusProposta.SUBSTITUIDA; p.propostas.add(Proposta(Lado.CLIENTE, eu, arredonda5((p.oferta + u.valor) / 2.0))) }
            else -> u.status = StatusProposta.RECUSADA
        }
    }

    Column(Modifier.fillMaxSize()) {
        Topo("Oportunidade", aoVoltar) { if (p.urgente) Pilula("URGENTE", Cor.AmbarTexto, Cor.AmbarFundo, Icons.Filled.Bolt) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Cartao {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.cliente.nome, 56.dp, Cor.Cartao2); Spacer(Modifier.width(14.dp))
                    Column { Text(p.cliente.nome, style = Estilo.titulo.copy(fontSize = 19.sp)); Nota(p.cliente.nota, p.cliente.avaliacoes, 15)
                        Text("Nota dada por outros profissionais", style = Estilo.nota) }
                }
            }
            Cartao {
                Text(p.descricao, style = Estilo.item.copy(fontSize = 17.sp, lineHeight = 23.sp))
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = Cor.AzulLeitura, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("${p.bairro} · ${km(p.distanciaKm)} de você", style = Estilo.corpo)
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = Cor.Apagado, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Endereço completo e conversa só depois do aceite", style = Estilo.nota)
                }
            }
            Cartao { Text("Oferta do cliente", style = Estilo.nota.copy(color = Cor.Texto2)); Spacer(Modifier.height(8.dp)); QuadroValor(p.oferta) }
            Negociacao(p, ult)
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val pendenteMinha = ult?.de == Lado.PROFISSIONAL && ult.status == StatusProposta.PENDENTE
            val contra = ult?.takeIf { it.de == Lado.CLIENTE && it.status == StatusProposta.PENDENTE }
            when {
                pendenteMinha -> LinkPequeno("Cancelar minha proposta", Cor.Vermelho) { ult!!.status = StatusProposta.CANCELADA }
                contra != null -> {
                    Botao("Aceitar ${reais(contra.valor)}", { contra.status = StatusProposta.ACEITA; estado.conectarProfissional(p, contra.valor) }, Modifier.fillMaxWidth(), TipoBotao.SUCESSO, alto = true)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Botao("Outra proposta", { propor = true }, Modifier.weight(1f), TipoBotao.CONTORNO)
                        Botao("Recusar", { contra.status = StatusProposta.RECUSADA }, Modifier.weight(1f), TipoBotao.CONTORNO)
                    }
                }
                else -> {
                    Botao("Aceitar por ${reais(p.oferta)}", { estado.conectarProfissional(p, p.oferta) }, Modifier.fillMaxWidth(), TipoBotao.SUCESSO, alto = true)
                    Botao("Propor outro valor", { propor = true }, Modifier.fillMaxWidth().padding(top = 10.dp), TipoBotao.CONTORNO)
                }
            }
            LinkPequeno("Recusar pedido") { estado.oportunidades.remove(p); if (estado.urgente == p) estado.urgente = null; aoVoltar() }
        }
    }
    if (propor) DialogoProposta(p.oferta, true, { propor = false }) { v ->
        ult?.takeIf { it.status == StatusProposta.PENDENTE }?.status = StatusProposta.SUBSTITUIDA
        p.propostas.add(Proposta(Lado.PROFISSIONAL, eu, v))
    }
}

@Composable
private fun Negociacao(p: Pedido, ult: Proposta?) {
    if (ult == null) return
    val nome = p.cliente.primeiroNome
    when {
        ult.de == Lado.PROFISSIONAL && ult.status == StatusProposta.PENDENTE -> Cartao(cor = Cor.AzulFundo, borda = Cor.Azul) {
            Row(verticalAlignment = Alignment.CenterVertically) { PontoPulsante(Cor.AzulLeitura); Spacer(Modifier.width(10.dp)); Text("Proposta enviada · aguardando $nome", style = Estilo.item) }
            Text("Sua proposta: ${reais(ult.valor)} (você recebe ${reais(liquido(ult.valor))})", style = Estilo.corpo, modifier = Modifier.padding(top = 6.dp))
        }
        ult.de == Lado.CLIENTE && ult.status == StatusProposta.PENDENTE -> Cartao(cor = Cor.AmbarFundo, borda = Cor.Ambar) {
            Text("$nome fez uma contraproposta", style = Estilo.item.copy(color = Cor.AmbarTexto))
            Spacer(Modifier.height(8.dp)); QuadroValor(ult.valor)
        }
        ult.status == StatusProposta.RECUSADA -> Cartao { Text(if (ult.de == Lado.PROFISSIONAL) "$nome recusou sua proposta de ${reais(ult.valor)}." else "Você recusou a proposta de $nome.", style = Estilo.corpo) }
        ult.status == StatusProposta.CANCELADA -> Cartao { Text("Você cancelou sua proposta.", style = Estilo.corpo) }
    }
}

@Composable
fun DialogoProposta(referencia: Int, profissional: Boolean, aoFechar: () -> Unit, aoEnviar: (Int) -> Unit) {
    var valor by remember { mutableIntStateOf(referencia) }
    AlertDialog(
        onDismissRequest = aoFechar, containerColor = Cor.Cartao,
        title = { Text(if (profissional) "Sua proposta" else "Sua contraproposta", style = Estilo.titulo) },
        text = { Column { Text(if (profissional) "O cliente recebe na hora e pode aceitar, recusar ou propor outro valor." else "O profissional recebe na hora e pode aceitar, recusar ou propor outro valor.", style = Estilo.nota.copy(color = Cor.Texto2))
            Spacer(Modifier.height(16.dp)); SeletorValor(valor, { valor = it }, referencia, profissional) } },
        confirmButton = { Botao("Enviar ${reais(valor)}", { aoEnviar(valor); aoFechar() }) },
        dismissButton = { TextButton(aoFechar) { Text("Voltar", style = Estilo.botao) } }
    )
}

/* ---------- Atendimento: rota, chegada, trabalho, fim ---------- */
@Composable
private fun Atendimento(p: Pedido, aoConversar: () -> Unit) {
    when (p.fase) {
        Fase.A_CAMINHO -> TelaRota(p, aoConversar)
        Fase.NO_LOCAL -> TelaChegadaPro(p, aoConversar)
        Fase.TRABALHANDO -> TelaTrabalhoPro(p, aoConversar)
        Fase.CONCLUIDO -> TelaFimPro(p)
        else -> Unit
    }
}

@Composable
fun CancelarAtendimento(p: Pedido, lado: Lado) {
    val estado = Global.estado
    var confirmar by remember { mutableStateOf(false) }
    LinkPequeno("Cancelar atendimento", Cor.Vermelho) { confirmar = true }
    if (confirmar) {
        val taxa = estado.taxaCancelamento(p)
        Confirmar("Cancelar atendimento?",
            if (taxa == 0) "Sem taxa neste momento." else "Taxa de cancelamento: ${reais(taxa)} (${percentualCancelamento(p.fase)}% do valor, conforme os termos). ${if (lado == Lado.PROFISSIONAL) "O cliente" else "O profissional"} é avisado na hora.",
            "Cancelar e pagar ${reais(taxa)}".takeIf { taxa > 0 } ?: "Cancelar", {
                estado.cancelar(p, lado)
                estado.aviso = if (taxa > 0) "Atendimento cancelado. Taxa de ${reais(taxa)}." else "Pedido cancelado."
            }, { confirmar = false }, perigo = true)
    }
}

@Composable
private fun CabecalhoCliente(p: Pedido, aoConversar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.cliente.nome, 52.dp, Cor.Cartao2); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(p.cliente.nome, style = Estilo.item.copy(fontSize = 17.sp)); Nota(p.cliente.nota, p.cliente.avaliacoes, 13) }
        Box(Modifier.size(52.dp).clip(CircleShape).background(Cor.AzulFundo).toque(onClick = aoConversar), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Chat, "Conversar", tint = Cor.AzulLeitura, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TelaRota(p: Pedido, aoConversar: () -> Unit) {
    val estado = Global.estado
    val ctx = LocalContext.current
    val agora by agoraAoVivo()
    val prog = p.progresso(agora)
    val resta = p.distanciaRestante(agora)
    val perto = resta <= 0.1
    var sobrepor by remember { mutableStateOf(false) }

    LaunchedEffect(estado.voltouDoWaze) {
        if (!estado.voltouDoWaze) return@LaunchedEffect
        estado.voltouDoWaze = false
        if (p.distanciaRestante(System.currentTimeMillis()) <= 0.1) p.fase = Fase.NO_LOCAL
        else estado.aviso = "Você ainda está a ${km(p.distanciaRestante(System.currentTimeMillis()))} do endereço. A chegada só é confirmada perto do local."
    }
    fun abrirWaze() {
        if (Build.VERSION.SDK_INT >= 33) (ctx as? Activity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Navegacao.abrirWaze(ctx, p.endereco)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapaRota(prog, Modifier.fillMaxSize())
            BotaoEmergencia(Modifier.align(Alignment.TopStart).padding(14.dp))
            Column(Modifier.align(Alignment.TopEnd).padding(14.dp).clip(Forma.botao).background(Cor.Cartao).padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                Text(if (perto) "Chegou" else km(resta), style = Estilo.numero.copy(fontSize = 20.sp, color = if (perto) Cor.Verde else Cor.Texto))
                Text(if (perto) "no endereço" else "até o cliente", style = Estilo.nota)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Cor.Cartao).padding(20.dp)) {
            CabecalhoCliente(p, aoConversar)
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = Cor.Vermelho, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(p.endereco, style = Estilo.corpo.copy(color = Cor.Texto), modifier = Modifier.weight(1f))
                Text("recebe ${reais(liquido(p.valor ?: 0))}", style = Estilo.nota.copy(color = Cor.Verde))
            }
            Botao("Abrir no Waze", { if (Navegacao.podeSobrepor(ctx)) abrirWaze() else sobrepor = true }, Modifier.fillMaxWidth().padding(top = 14.dp), TipoBotao.CONTORNO, icone = Icons.Outlined.Navigation)
            Botao(if (perto) "Cheguei ao local" else "Faltam ${km(resta)} para liberar a chegada", { p.fase = Fase.NO_LOCAL },
                Modifier.fillMaxWidth().padding(top = 10.dp), TipoBotao.SUCESSO, ativo = perto, alto = true)
            Text("A chegada só é liberada quando o GPS estiver a até 100 m do endereço indicado pelo cliente.", style = Estilo.nota, modifier = Modifier.padding(top = 8.dp))
            Box(Modifier.align(Alignment.CenterHorizontally)) { CancelarAtendimento(p, Lado.PROFISSIONAL) }
        }
    }
    if (sobrepor) AlertDialog(
        onDismissRequest = { sobrepor = false }, containerColor = Cor.Cartao,
        title = { Text("Botão por cima do Waze", style = Estilo.titulo) },
        text = { Text("Para mostrar o botão \"Cheguei ao local\" enquanto você usa o Waze, permita que o TaskGO apareça sobre outros apps. Sem isso, o botão fica só na notificação.", style = Estilo.corpo) },
        confirmButton = { Botao("Permitir", { sobrepor = false; Navegacao.pedirSobreposicao(ctx) }) },
        dismissButton = { TextButton({ sobrepor = false; abrirWaze() }) { Text("Abrir sem o botão", style = Estilo.botao) } }
    )
}

@Composable
private fun TelaChegadaPro(p: Pedido, aoConversar: () -> Unit) {
    var codigo by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }
    var tentativa by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        IconeCirculo(Icons.Outlined.Key, tamanho = 64.dp)
        Text("Confirme sua chegada", style = Estilo.vozForte.copy(fontSize = 28.sp), modifier = Modifier.padding(top = 18.dp))
        Text("Peça para ${p.cliente.primeiroNome} o código de 4 dígitos que aparece no app dela.", style = Estilo.corpo, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CampoCodigo(codigo, {
                codigo = it; erro = false
                if (it.length == 4) {
                    if (it == p.codigo) { p.inicioTrabalho = System.currentTimeMillis(); p.fase = Fase.TRABALHANDO }
                    else { erro = true; tentativa++ }
                }
            }, erro, tentativa)
        }
        Text(if (erro) "Código incorreto. Peça para ${p.cliente.primeiroNome} conferir no app dela." else "Na demonstração, o código é ${p.codigo}.",
            style = Estilo.nota.copy(color = if (erro) Cor.Vermelho else Cor.Apagado), modifier = Modifier.padding(top = 14.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(32.dp))
        Cartao { CabecalhoCliente(p, aoConversar) }
        Spacer(Modifier.height(12.dp))
        BotaoEmergencia(Modifier.fillMaxWidth())
        Box(Modifier.align(Alignment.CenterHorizontally)) { CancelarAtendimento(p, Lado.PROFISSIONAL) }
    }
}

@Composable
private fun TelaTrabalhoPro(p: Pedido, aoConversar: () -> Unit) {
    val agora by agoraAoVivo(1000)
    var concluir by remember { mutableStateOf(false) }
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrada.animateTo(1f, spring(dampingRatio = .45f)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        SeloFeito(104.dp)
        Text("Pode começar o trabalho", style = Estilo.vozForte.copy(fontSize = 26.sp), modifier = Modifier.padding(top = 18.dp))
        Text("Código confirmado. ${p.cliente.primeiroNome} já sabe que você está no local.", style = Estilo.corpo, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(22.dp))
        Cartao {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, null, tint = Cor.AzulLeitura); Spacer(Modifier.width(10.dp))
                Text("Trabalhando há", style = Estilo.corpo, modifier = Modifier.weight(1f))
                Text(tempoDesde(p.inicioTrabalho, agora), style = Estilo.numero.copy(fontSize = 24.sp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Cartao { CabecalhoCliente(p, aoConversar) }
        Spacer(Modifier.height(12.dp))
        Cartao { QuadroValor(p.valor ?: 0) }
        Spacer(Modifier.height(16.dp))
        Botao("Concluir serviço", { concluir = true }, Modifier.fillMaxWidth(), TipoBotao.SUCESSO, alto = true)
        Spacer(Modifier.height(10.dp))
        BotaoEmergencia(Modifier.fillMaxWidth())
        CancelarAtendimento(p, Lado.PROFISSIONAL)
    }
    if (concluir) Confirmar("Concluir o serviço?", "${p.cliente.primeiroNome} recebe o aviso para avaliar o atendimento.", "Concluir", { p.fase = Fase.CONCLUIDO }, { concluir = false })
}

@Composable
private fun TelaFimPro(p: Pedido) {
    val estado = Global.estado
    var nota by remember { mutableIntStateOf(p.notaDoProfissional ?: 0) }
    LaunchedEffect(p.id) { delay(1800); if (p.notaDoCliente == null) p.notaDoCliente = 5 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { SeloFeito(76.dp) }
        Text("Serviço concluído!", style = Estilo.vozForte.copy(fontSize = 28.sp), modifier = Modifier.padding(top = 18.dp))
        Text("Você recebeu", style = Estilo.corpo, modifier = Modifier.padding(top = 18.dp))
        ValorAnimado(liquido(p.valor ?: 0), Estilo.numero.copy(fontSize = 44.sp, color = Cor.Verde))
        Text("Valor do serviço ${reais(p.valor ?: 0)} · taxa TaskGO ${reais(taxaDe(p.valor ?: 0))}", style = Estilo.nota)
        AnimatedVisibility(p.notaDoCliente != null, enter = fadeIn() + slideInVertically { it }) {
            Cartao(Modifier.padding(top = 18.dp), cor = Cor.AmbarFundo) {
                Text("${p.cliente.primeiroNome} te avaliou", style = Estilo.nota.copy(color = Cor.Texto2))
                Row(Modifier.padding(top = 4.dp)) { repeat(p.notaDoCliente ?: 0) { Icon(Icons.Filled.Star, null, tint = Cor.Ambar, modifier = Modifier.size(26.dp)) } }
            }
        }
        TituloSecao("Como foi atender ${p.cliente.primeiroNome}?")
        EscolherEstrelas(nota) { nota = it; p.notaDoProfissional = it }
        Text("Sua nota ajuda outros profissionais a decidir antes de aceitar.", style = Estilo.nota, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(28.dp))
        Botao("Ficar disponível de novo", { estado.ficarDisponivel() }, Modifier.fillMaxWidth(), TipoBotao.PRIMARIO, icone = Icons.Outlined.PowerSettingsNew, alto = true)
    }
}

/* ---------- Ganhos ---------- */
@Composable
private fun Ganhos() {
    val feitos = Global.estado.concluidosProfissional.filter { it.fase == Fase.CONCLUIDO }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("Ganhos", style = Estilo.titulo.copy(fontSize = 26.sp), modifier = Modifier.padding(top = 22.dp))
        Cartao(Modifier.padding(top = 16.dp), cor = Cor.VerdeFundo, borda = Cor.VerdeForte) {
            Text("Total recebido", style = Estilo.nota.copy(color = Cor.Texto2))
            ValorAnimado(feitos.sumOf { liquido(it.valor ?: 0) }, Estilo.numero.copy(fontSize = 34.sp, color = Cor.Verde))
            Text("Taxa TaskGO: ${reais(feitos.sumOf { taxaDe(it.valor ?: 0) })} · ${feitos.size} atendimento(s)", style = Estilo.nota)
        }
        TituloSecao("Atendimentos")
        if (feitos.isEmpty()) Text("Seus atendimentos concluídos aparecem aqui, com o valor e a taxa de cada um.", style = Estilo.corpo)
        feitos.forEach { p ->
            Cartao(Modifier.padding(bottom = 12.dp)) {
                Text(p.cliente.nome, style = Estilo.item); Text(p.descricao, style = Estilo.nota, modifier = Modifier.padding(bottom = 8.dp))
                QuadroValor(p.valor ?: 0, destaque = false)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------- Histórico de conversas (compartilhado) ---------- */
@Composable
fun HistoricoConversas(pedidos: List<Pedido>, outro: (Pedido) -> br.com.taskgo.app.Pessoa, aoAbrir: (Pedido) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("Conversas", style = Estilo.titulo.copy(fontSize = 26.sp), modifier = Modifier.padding(top = 22.dp))
        Text("Histórico dos atendimentos encerrados. A conversa de um atendimento em andamento abre dentro dele.", style = Estilo.nota, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        if (pedidos.isEmpty()) Text("Nenhuma conversa encerrada ainda.", style = Estilo.corpo)
        pedidos.forEach { p ->
            val o = outro(p)
            Cartao(Modifier.padding(bottom = 10.dp), onClick = { aoAbrir(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(o.nome, 44.dp, Cor.Cartao2); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.nome, style = Estilo.item)
                        Text(p.mensagens.lastOrNull()?.texto ?: "", style = Estilo.nota, maxLines = 1)
                    }
                    Pilula(if (p.fase == Fase.CANCELADO) "Cancelado" else "Concluído", if (p.fase == Fase.CANCELADO) Cor.Vermelho else Cor.Verde, Cor.Cartao2)
                }
            }
        }
    }
}

/* ---------- Perfil (compartilhado) ---------- */
@Composable
fun PerfilConta(perfil: Perfil, aoTrocarPerfil: () -> Unit, aoSair: () -> Unit) {
    val estado = Global.estado
    val eu = if (perfil == Perfil.PROFISSIONAL) estado.profissional else estado.cliente
    val mod = estado.moderacaoDe(perfil)
    var termos by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(eu.nome, 64.dp); Spacer(Modifier.width(14.dp))
            Column { Text(eu.nome, style = Estilo.titulo); Nota(eu.nota, eu.avaliacoes, 15)
                Text(if (perfil == Perfil.PROFISSIONAL) "Profissional · Elétrica" else "Cliente", style = Estilo.nota) }
        }
        Cartao(cor = if (mod.avisos > 0) Cor.AmbarFundo else Cor.Cartao) {
            Text("Avisos na conversa: ${mod.avisos} de 3", style = Estilo.item.copy(color = if (mod.avisos > 0) Cor.AmbarTexto else Cor.Texto))
            Text("Números, valores e contatos não podem ir pelo chat. 1º aviso; 2º suspensão de 24 h; 3º banimento. Toda decisão pode ser contestada.", style = Estilo.nota.copy(color = Cor.Texto2), modifier = Modifier.padding(top = 4.dp))
            if (mod.avisos > 0) LinkPequeno("Recorrer", Cor.AzulLeitura) { estado.recursoAberto = perfil.let { if (it == Perfil.CLIENTE) Lado.CLIENTE else Lado.PROFISSIONAL } }
        }
        /* Tema: o mesmo escuro e claro do site. A escolha fica guardada no aparelho. */
        Cartao {
            Text("Tema", style = Estilo.item)
            Text("O escuro é o padrão; o claro ajuda a ler na rua, sob o sol.", style = Estilo.nota, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                ModoTema.entries.forEach { m ->
                    val sel = TemaApp.modo == m
                    Text(m.rotulo, style = Estilo.rotulo.copy(color = if (sel) Cor.Branco else Cor.Texto, fontWeight = FontWeight.SemiBold),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).background(if (sel) Cor.Azul else Cor.Fundo)
                            .border(1.dp, if (sel) Cor.Azul else Cor.LinhaForte).toque(role = androidx.compose.ui.semantics.Role.RadioButton) { TemaApp.escolher(m) }
                            .padding(vertical = 13.dp))
                }
            }
        }
        Cartao(onClick = { termos = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Gavel, null, tint = Cor.AzulLeitura); Spacer(Modifier.width(10.dp)); Text("Taxas e cancelamento", style = Estilo.item) }
        }
        Cartao(onClick = aoTrocarPerfil) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.SwapHoriz, null, tint = Cor.AzulLeitura); Spacer(Modifier.width(10.dp))
                Text(if (perfil == Perfil.PROFISSIONAL) "Entrar como cliente" else "Entrar como profissional", style = Estilo.item) }
        }
        Cartao(onClick = { Global.estado = br.com.taskgo.app.EstadoApp(perfil) }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Refresh, null, tint = Cor.AzulLeitura); Spacer(Modifier.width(10.dp)); Text("Recomeçar demonstração", style = Estilo.item) }
        }
        Botao("Sair", aoSair, Modifier.fillMaxWidth(), TipoBotao.CONTORNO, icone = Icons.AutoMirrored.Outlined.Logout)
        Text("Protótipo: pessoas, pedidos e valores são fictícios. Nenhum pagamento é feito.", style = Estilo.nota, modifier = Modifier.padding(bottom = 24.dp))
    }
    if (termos) AlertDialog(
        onDismissRequest = { termos = false }, containerColor = Cor.Cartao,
        title = { Text("Taxas e cancelamento", style = Estilo.titulo) },
        text = { Text("Taxa TaskGO: 12% do valor do serviço, descontada do profissional. O cliente paga o valor cheio.\n\nCancelamento:\n· Antes do aceite: sem taxa.\n· Depois do aceite, a caminho: 10% do valor.\n· Depois da chegada: 20% do valor.\n\nQuem cancela paga a taxa. Proposta dos termos, ainda em definição.", style = Estilo.corpo) },
        confirmButton = { Botao("Entendi", { termos = false }) }
    )
}
