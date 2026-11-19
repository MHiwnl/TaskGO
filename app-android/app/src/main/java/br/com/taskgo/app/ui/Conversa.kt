package br.com.taskgo.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import br.com.taskgo.app.Global
import br.com.taskgo.app.Lado
import br.com.taskgo.app.Mensagem
import br.com.taskgo.app.Pedido
import br.com.taskgo.app.Perfil
import br.com.taskgo.app.Pessoa
import br.com.taskgo.app.RESPOSTAS_CLIENTE
import br.com.taskgo.app.RESPOSTAS_PROFISSIONAL
import br.com.taskgo.app.StatusRecurso
import br.com.taskgo.app.lado
import br.com.taskgo.app.motivoBloqueio
import br.com.taskgo.app.respostaDoOutroLado
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/* Conversa do atendimento. Só existe enquanto cliente e profissional estão conectados;
   depois fica só para leitura, no histórico. Números, valores e contatos são bloqueados. */
@Composable
fun TelaConversa(pedido: Pedido, outro: Pessoa, perfil: Perfil, somenteLeitura: Boolean, aoVoltar: () -> Unit) {
    val estado = Global.estado
    val mod = estado.moderacaoDe(perfil)
    var texto by remember { mutableStateOf("") }
    var escrevendo by remember { mutableStateOf(false) }
    var pendente by remember { mutableStateOf<String?>(null) }
    val lista = rememberLazyListState()

    fun enviar(t: String) {
        if (t.isBlank() || escrevendo) return
        val motivo = motivoBloqueio(t)
        if (motivo != null) { mod.registrarInfracao(); estado.dialogoModeracao = perfil.lado() to motivo; return }
        pedido.mensagens.add(Mensagem(true, t.trim())); texto = ""; pendente = t
    }
    LaunchedEffect(pendente) {
        val enviado = pendente ?: return@LaunchedEffect
        escrevendo = true; delay(1200)
        pedido.mensagens.add(Mensagem(false, respostaDoOutroLado(enviado))); escrevendo = false; pendente = null
    }
    LaunchedEffect(pedido.mensagens.size, escrevendo) { if (pedido.mensagens.isNotEmpty()) lista.animateScrollToItem(pedido.mensagens.size - 1 + if (escrevendo) 1 else 0) }

    Column(Modifier.fillMaxSize()) {
        Topo(outro.nome, aoVoltar) { Nota(outro.nota, outro.avaliacoes, 13) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(Forma.botao).background(if (somenteLeitura) Cor.Cartao else Cor.AzulFundo).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, tint = Cor.AzulLeitura, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (somenteLeitura) "Conversa encerrada. Fica guardada aqui só para consulta."
                 else "Conversa protegida: números, valores e contatos não são permitidos. Avisos: ${mod.avisos} de 3.",
                style = Estilo.nota.copy(color = Cor.Texto2))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), state = lista, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.size(8.dp)) }
            items(pedido.mensagens) { Balao(it) }
            if (escrevendo) item { Text("${outro.primeiroNome} está escrevendo…", style = Estilo.nota) }
            item { Spacer(Modifier.size(8.dp)) }
        }
        if (!somenteLeitura) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (perfil == Perfil.PROFISSIONAL) RESPOSTAS_PROFISSIONAL else RESPOSTAS_CLIENTE).forEach { r ->
                    Text(r, style = Estilo.rotulo.copy(color = Cor.Texto), modifier = Modifier.clip(Forma.pilula).background(Cor.Cartao2).toque { enviar(r) }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(texto, { texto = it.take(300) }, textStyle = Estilo.corpo.copy(color = Cor.Texto), cursorBrush = SolidColor(Cor.Azul),
                    modifier = Modifier.weight(1f).clip(Forma.pilula).background(Cor.Cartao2).heightIn(min = 50.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                    decorationBox = { campo -> Box { if (texto.isEmpty()) Text("Escreva uma mensagem", style = Estilo.corpo.copy(color = Cor.Apagado)); campo() } })
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(50.dp).clip(CircleShape).background(if (texto.isBlank()) Cor.Cartao2 else Cor.Azul).toque { enviar(texto) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (texto.isBlank()) Cor.Apagado else Cor.Branco, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun Balao(m: Mensagem) {
    var visivel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visivel = true }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.minha) Arrangement.End else Arrangement.Start) {
        AnimatedVisibility(visivel, enter = fadeIn() + slideInVertically { it / 2 }) {
            Text(m.texto, style = Estilo.corpo.copy(color = if (m.minha) Cor.Branco else Cor.Texto),
                modifier = Modifier.widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, if (m.minha) 4.dp else 18.dp, if (m.minha) 18.dp else 4.dp))
                    .background(if (m.minha) Cor.Azul else Cor.Cartao).padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

/* ---------- Avisos, suspensão e banimento ---------- */
private fun hora(ms: Long) = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")).format(Date(ms))

/* Diálogo que aparece logo depois de uma mensagem bloqueada. */
@Composable
fun DialogoModeracao(aoRecorrer: (Lado) -> Unit) {
    val estado = Global.estado
    val (lado, motivo) = estado.dialogoModeracao ?: return
    val mod = estado.moderacao.getValue(lado)
    val (titulo, texto) = when (mod.avisos) {
        1 -> "Aviso 1 de 3" to "Sua mensagem não foi enviada: ela tinha $motivo. Para a segurança dos dois lados, combinar contato, valores ou serviço por fora não é permitido."
        2 -> "Aviso 2 de 3: conta suspensa" to "Sua mensagem tinha $motivo e não foi enviada. Por 24 horas você não recebe pedidos nem propostas (até ${hora(mod.punidoAte)}). Mais um aviso e a conta é banida."
        else -> "Conta banida" to "Sua mensagem tinha $motivo. Com 3 avisos, a conta foi banida do TaskGO. Se achar que foi um engano, você pode recorrer."
    }
    AlertDialog(
        onDismissRequest = { estado.dialogoModeracao = null }, containerColor = Cor.Cartao,
        icon = { Icon(if (mod.avisos >= 3) Icons.Outlined.Block else Icons.Outlined.WarningAmber, null, tint = if (mod.avisos >= 2) Cor.Vermelho else Cor.Ambar, modifier = Modifier.size(36.dp)) },
        title = { Text(titulo, style = Estilo.titulo) },
        text = { Text(texto, style = Estilo.corpo) },
        confirmButton = { Botao("Entendi", { estado.dialogoModeracao = null }) },
        dismissButton = { TextButton({ estado.dialogoModeracao = null; aoRecorrer(lado) }) { Text("Recorrer", style = Estilo.botao.copy(color = Cor.AzulLeitura)) } }
    )
}

@Composable
fun AvisoSuspensao(lado: Lado, aoRecorrer: () -> Unit) {
    val mod = Global.estado.moderacao.getValue(lado)
    Cartao(cor = Cor.AmbarFundo, borda = Cor.Ambar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Cor.Ambar, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
            Text("Conta suspensa até ${hora(mod.punidoAte)}", style = Estilo.item.copy(color = Cor.AmbarTexto))
        }
        Text(if (lado == Lado.PROFISSIONAL) "Você não recebe pedidos nem propostas durante a suspensão (2º aviso por contato fora do app)."
             else "Você não pode fazer pedidos durante a suspensão (2º aviso por contato fora do app).", style = Estilo.nota.copy(color = Cor.Texto2), modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 4.dp)) {
            LinkPequeno("Recorrer", Cor.AzulLeitura, aoRecorrer)
            LinkPequeno("Encerrar suspensão (demonstração)") { mod.punidoAte = 0L }
        }
    }
}

@Composable
fun TelaBanida(lado: Lado, aoRecorrer: () -> Unit, aoTrocarPerfil: () -> Unit) {
    val mod = Global.estado.moderacao.getValue(lado)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        IconeCirculo(Icons.Outlined.Block, Cor.Vermelho, Cor.Cartao2, 72.dp)
        Text("Conta banida", style = Estilo.vozForte, modifier = Modifier.padding(top = 20.dp))
        Text("A conta recebeu 3 avisos por tentar combinar contato, valores ou serviço fora do app. Enquanto isso, ela não acessa pedidos, propostas nem conversas.",
            style = Estilo.corpo, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.size(24.dp))
        when (mod.recurso) {
            StatusRecurso.EM_ANALISE -> Cartao(cor = Cor.AzulFundo) {
                Text("Recurso em análise", style = Estilo.item); Text("A equipe responde em até 2 dias úteis.", style = Estilo.nota.copy(color = Cor.Texto2))
                LinkPequeno("Simular aprovação do recurso (demonstração)", Cor.AzulLeitura) { mod.aprovarRecurso() }
            }
            else -> Botao("Recorrer da decisão", aoRecorrer, Modifier.fillMaxWidth(), icone = Icons.Outlined.Gavel, alto = true)
        }
        Spacer(Modifier.size(12.dp))
        LinkPequeno("Entrar com o outro perfil (demonstração)", aoTocar = aoTrocarPerfil)
    }
}

@Composable
fun TelaRecurso(lado: Lado, aoVoltar: () -> Unit) {
    val mod = Global.estado.moderacao.getValue(lado)
    var texto by remember { mutableStateOf(mod.textoRecurso) }
    Column(Modifier.fillMaxSize()) {
        Topo("Recorrer", aoVoltar)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Conte o que aconteceu", style = Estilo.titulo, modifier = Modifier.padding(top = 8.dp))
            Text("A equipe revê a conversa e a sua explicação. Avisos: ${mod.avisos} de 3.", style = Estilo.nota.copy(color = Cor.Texto2), modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            BasicTextField(texto, { texto = it.take(600) }, textStyle = Estilo.corpo.copy(color = Cor.Texto), cursorBrush = SolidColor(Cor.Azul),
                modifier = Modifier.fillMaxWidth().clip(Forma.botao).background(Cor.Cartao2).heightIn(min = 160.dp).padding(16.dp),
                decorationBox = { c -> Box { if (texto.isEmpty()) Text("Ex.: eu passei o número do apartamento, não um telefone.", style = Estilo.corpo.copy(color = Cor.Apagado)); c() } })
            if (mod.recurso == StatusRecurso.EM_ANALISE) Text("Recurso enviado. A equipe responde em até 2 dias úteis.", style = Estilo.nota.copy(color = Cor.Verde), modifier = Modifier.padding(top = 12.dp))
        }
        Box(Modifier.padding(20.dp)) {
            Botao("Enviar recurso", { mod.textoRecurso = texto; mod.recurso = StatusRecurso.EM_ANALISE; aoVoltar() }, Modifier.fillMaxWidth(), ativo = texto.length >= 10, alto = true)
        }
    }
}
