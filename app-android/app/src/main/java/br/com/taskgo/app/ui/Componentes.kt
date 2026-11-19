package br.com.taskgo.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.taskgo.app.reais
import br.com.taskgo.app.taxaDe
import br.com.taskgo.app.liquido
import br.com.taskgo.app.TAXA

/* Tudo o que se toca afunda um pouco: resposta imediata ao dedo. */
@Composable
fun Modifier.toque(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val interacao = remember { MutableInteractionSource() }
    val apertado by interacao.collectIsPressedAsState()
    val escala by animateFloatAsState(if (apertado) .96f else 1f, spring(stiffness = 900f), label = "toque")
    return this.graphicsLayer { scaleX = escala; scaleY = escala }
        .clickable(interactionSource = interacao, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/* Módulo como no site: seção aberta com linha forte no topo, sem caixa nem sombra.
   Com destaque (borda), vira contorno reto completo; com cor de estado, ganha fundo. A aba nomeia o estado. */
@Composable
fun Cartao(modifier: Modifier = Modifier, cor: Color? = null, borda: Color? = null, onClick: (() -> Unit)? = null,
           aba: String? = null, corAba: Color = Cor.Azul, conteudo: @Composable ColumnScope.() -> Unit) {
    val fechado = borda != null || cor != null || aba != null
    Column(modifier.fillMaxWidth()) {
        if (aba != null) Text(aba, style = Estilo.aba.copy(color = if (corAba == Cor.Ambar) Cor.Preto else Cor.Branco, fontWeight = FontWeight.Bold, fontSize = 11.sp),
            modifier = Modifier.background(corAba).padding(horizontal = 10.dp, vertical = 5.dp))
        Column(
            Modifier.fillMaxWidth()
                .let { if (cor != null) it.background(cor) else it }
                .let { if (fechado) it.border(1.dp, borda ?: if (aba != null) corAba else Cor.LinhaForte) else it.linhaTopo() }
                .let { if (onClick != null) it.toque(onClick = onClick) else it }
                .padding(horizontal = if (fechado) 16.dp else 0.dp, vertical = 18.dp),
            content = conteudo
        )
    }
}

/* Linha forte no topo: abre cada lista e cada módulo, como no site. */
fun Modifier.linhaTopo(): Modifier = drawBehind { drawLine(Cor.LinhaForte, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
fun Modifier.linhaBase(): Modifier = drawBehind { drawLine(Cor.Linha, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }

enum class TipoBotao { PRIMARIO, SUCESSO, CONTORNO, PERIGO, CLARO }

@Composable
fun Botao(texto: String, aoTocar: () -> Unit, modifier: Modifier = Modifier, tipo: TipoBotao = TipoBotao.PRIMARIO,
          ativo: Boolean = true, icone: ImageVector? = null, alto: Boolean = false) {
    val interacao = remember { MutableInteractionSource() }
    val apertado by interacao.collectIsPressedAsState()
    val (fundo, corTexto, borda) = when (tipo) {
        TipoBotao.PRIMARIO, TipoBotao.SUCESSO -> Triple(Cor.Azul, Cor.Branco, Cor.Azul)
        TipoBotao.CONTORNO -> Triple(Color.Transparent, Cor.Texto, Cor.LinhaForte)
        TipoBotao.PERIGO -> Triple(Cor.VermelhoForte, Cor.Branco, Cor.VermelhoForte)
        TipoBotao.CLARO -> Triple(Cor.Branco, Cor.Preto, Cor.Branco)
    }
    val f by animateColorAsState(when { !ativo -> Color.Transparent; apertado -> Cor.Texto; else -> fundo }, tween(140), label = "fundo")
    val c by animateColorAsState(when { !ativo -> Cor.Apagado; apertado -> Cor.Fundo; else -> corTexto }, tween(140), label = "texto")
    val escala by animateFloatAsState(if (apertado) .98f else 1f, tween(100), label = "afunda")
    Row(
        modifier.heightIn(min = if (alto) 58.dp else 50.dp).graphicsLayer { scaleX = escala; scaleY = escala }
            .background(f).border(1.dp, if (ativo) (if (apertado) Cor.Texto else borda) else Cor.Linha)
            .clickable(interactionSource = interacao, indication = null, enabled = ativo, role = Role.Button, onClick = aoTocar)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icone != null) { Icon(icone, null, tint = c, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, style = Estilo.botao.copy(color = c, fontSize = if (alto) 15.sp else 14.sp, fontWeight = FontWeight.Medium))
    }
}

/* Cancelar é pequeno e discreto de propósito: ninguém cancela um atendimento sem querer. Sempre pede confirmação. */
@Composable
fun LinkPequeno(texto: String, cor: Color = Cor.Apagado, aoTocar: () -> Unit) {
    Text(texto, style = Estilo.rotulo.copy(color = cor), modifier = Modifier.clip(Forma.pilula).clickable(role = Role.Button, onClick = aoTocar)
        .padding(horizontal = 12.dp, vertical = 10.dp))
}

/* Emergência: grande, vermelho, sempre à vista durante o atendimento. Abre o discador, nunca liga sozinho. */
@Composable
fun BotaoEmergencia(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var escolher by remember { mutableStateOf(false) }
    Botao("Emergência", { escolher = true }, modifier, TipoBotao.PERIGO, icone = Icons.Filled.Call, alto = true)
    if (escolher) AlertDialog(
        onDismissRequest = { escolher = false }, containerColor = Cor.Cartao,
        title = { Text("Emergência", style = Estilo.titulo) },
        text = { Text("Escolha para quem ligar. O telefone abre com o número pronto.", style = Estilo.corpo) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                listOf("190" to "Polícia · 190", "192" to "SAMU · 192").forEach { (n, r) ->
                    Botao(r, { escolher = false; ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }, Modifier.fillMaxWidth().padding(top = 8.dp), TipoBotao.PERIGO, icone = Icons.Filled.Call)
                }
            }
        },
        dismissButton = { TextButton({ escolher = false }) { Text("Fechar", style = Estilo.botao) } }
    )
}

@Composable
fun Confirmar(titulo: String, texto: String, confirmar: String, aoConfirmar: () -> Unit, aoFechar: () -> Unit, perigo: Boolean = false) {
    AlertDialog(
        onDismissRequest = aoFechar, containerColor = Cor.Cartao,
        title = { Text(titulo, style = Estilo.titulo) },
        text = { Text(texto, style = Estilo.corpo) },
        confirmButton = { Botao(confirmar, { aoConfirmar(); aoFechar() }, tipo = if (perigo) TipoBotao.PERIGO else TipoBotao.PRIMARIO) },
        dismissButton = { TextButton(aoFechar) { Text("Voltar", style = Estilo.botao) } }
    )
}

/* Nota com estrela: aparece antes de aceitar, para os dois lados. */
@Composable
fun Nota(nota: Double, avaliacoes: Int? = null, tamanho: Int = 15, cor: Color = Cor.Texto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = Cor.Ambar, modifier = Modifier.size((tamanho + 2).dp))
        Spacer(Modifier.width(4.dp))
        Text("%.1f".format(nota).replace('.', ','), style = Estilo.item.copy(fontSize = tamanho.sp, fontFamily = SplineMono, color = cor))
        if (avaliacoes != null) Text("  ($avaliacoes)", style = Estilo.nota.copy(fontSize = (tamanho - 2).sp, color = if (cor == Cor.Texto) Cor.Apagado else cor.copy(alpha = .8f)))
    }
}

@Composable
fun EscolherEstrelas(nota: Int, aoEscolher: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { n ->
            val escala by animateFloatAsState(if (n <= nota) 1.15f else 1f, spring(dampingRatio = .4f), label = "estrela")
            Icon(Icons.Filled.Star, "$n de 5 estrelas", tint = if (n <= nota) Cor.Ambar else Cor.Cartao2,
                modifier = Modifier.size(44.dp).scale(escala).clip(CircleShape).clickable(role = Role.Button) { aoEscolher(n) })
        }
    }
}

@Composable
fun Avatar(nome: String, tamanho: Dp = 44.dp, fundo: Color = Cor.Azul) {
    val iniciais = nome.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(Modifier.size(tamanho).clip(CircleShape).background(fundo), contentAlignment = Alignment.Center) {
        Text(iniciais, style = Estilo.item.copy(color = when (fundo) { Cor.Azul -> Cor.Branco; Cor.Branco -> Cor.Azul; else -> Cor.Texto }, fontSize = (tamanho.value * .36f).sp, fontFamily = SplineMono))
    }
}

@Composable
fun IconeCirculo(icone: ImageVector, cor: Color = Cor.AzulLeitura, fundo: Color = Cor.AzulFundo, tamanho: Dp = 44.dp) {
    Box(Modifier.size(tamanho).clip(CircleShape).background(fundo), contentAlignment = Alignment.Center) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(tamanho * .5f))
    }
}

@Composable
fun Pilula(texto: String, cor: Color, fundo: Color, icone: ImageVector? = null) {
    Row(Modifier.background(fundo).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icone != null) { Icon(icone, null, tint = cor, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(texto, style = Estilo.aba.copy(color = cor, fontWeight = FontWeight.Bold, fontSize = 12.sp))
    }
}

@Composable
fun PontoPulsante(cor: Color, tamanho: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "pulso")
    val a by t.animateFloat(1f, .25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(tamanho).graphicsLayer { alpha = a }.clip(CircleShape).background(cor))
}

/* Valor que vira como placa de embarque quando muda: o novo sobe, o antigo sai por cima. */
@Composable
fun ValorAnimado(valor: Int, estilo: androidx.compose.ui.text.TextStyle = Estilo.numero) {
    AnimatedContent(valor, transitionSpec = { (slideInVertically(tween(260)) { it } + fadeIn(tween(260))) togetherWith (slideOutVertically(tween(260)) { -it } + fadeOut(tween(200))) }, label = "valor") { v ->
        Text(reais(v), style = estilo)
    }
}

/* O profissional vê de onde vem cada real: valor do cliente, taxa da TaskGO e o que ele recebe. */
@Composable
fun QuadroValor(valorCheio: Int, destaque: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        LinhaValor("Cliente paga", reais(valorCheio))
        LinhaValor("Taxa TaskGO (${(TAXA * 100).toInt()}%)", "− " + reais(taxaDe(valorCheio)), Cor.Vermelho)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Cor.Cartao2))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Você recebe", style = Estilo.item, modifier = Modifier.weight(1f))
            ValorAnimado(liquido(valorCheio), Estilo.numero.copy(color = Cor.Verde, fontSize = if (destaque) 26.sp else 20.sp))
        }
    }
}

@Composable
fun LinhaValor(rotulo: String, valor: String, cor: Color = Cor.Texto2) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(rotulo, style = Estilo.corpo, modifier = Modifier.weight(1f))
        Text(valor, style = Estilo.registro.copy(color = cor))
    }
}

/* Escolher um valor: botões de menos e mais e atalhos em volta da referência. */
@Composable
fun SeletorValor(valor: Int, aoMudar: (Int) -> Unit, referencia: Int, mostrarLiquido: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotaoRedondo(Icons.Outlined.Remove) { aoMudar((valor - 10).coerceAtLeast(10)) }
            Box(Modifier.width(190.dp), contentAlignment = Alignment.Center) { ValorAnimado(valor, Estilo.numero.copy(fontSize = 38.sp)) }
            BotaoRedondo(Icons.Outlined.Add) { aoMudar(valor + 10) }
        }
        if (mostrarLiquido) Text("Você recebe ${reais(liquido(valor))} · taxa TaskGO ${reais(taxaDe(valor))}", style = Estilo.nota.copy(color = Cor.Verde), modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-15, 0, 15).forEach { p ->
                val v = br.com.taskgo.app.arredonda5(referencia * (1 + p / 100.0))
                val sel = v == valor
                Text(if (p == 0) "Referência" else "${if (p > 0) "+" else ""}$p%",
                    style = Estilo.rotulo.copy(color = if (sel) Cor.Texto else Cor.Texto2, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clip(Forma.pilula).background(if (sel) Cor.Azul else Cor.Cartao2).toque { aoMudar(v) }
                        .padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
private fun BotaoRedondo(icone: ImageVector, aoTocar: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Cor.Cartao2).toque(onClick = aoTocar), contentAlignment = Alignment.Center) {
        Icon(icone, null, tint = Cor.Texto, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun Topo(titulo: String, aoVoltar: (() -> Unit)? = null, direita: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (aoVoltar != null) Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = aoVoltar), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = Cor.Texto)
        } else Spacer(Modifier.width(12.dp))
        Text(titulo, style = Estilo.secao.copy(fontSize = 18.sp), modifier = Modifier.weight(1f).padding(start = 4.dp))
        direita?.invoke()
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun TituloSecao(texto: String, modifier: Modifier = Modifier, direita: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = Estilo.secao.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
        direita?.invoke()
    }
}

data class Aba(val rotulo: String, val icone: ImageVector, val contador: Int = 0)

/* Barra de abas embaixo; some com o teclado aberto. A aba ativa ganha uma pílula azul atrás do ícone. */
@Composable
fun BarraAbas(abas: List<Aba>, selecionada: Int, aoEscolher: (Int) -> Unit) {
    if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) return
    Row(Modifier.fillMaxWidth().background(Cor.Fundo).linhaTopo().padding(horizontal = 6.dp, vertical = 6.dp)) {
        abas.forEachIndexed { i, aba -> ItemAba(aba, i == selecionada) { aoEscolher(i) } }
    }
}

@Composable
private fun RowScope.ItemAba(aba: Aba, ativa: Boolean, aoTocar: () -> Unit) {
    val fundo by animateColorAsState(if (ativa) Cor.Azul else Color.Transparent, tween(160), label = "aba")
    val cor by animateColorAsState(if (ativa) Cor.Branco else Cor.Texto2, tween(160), label = "corAba")
    Column(Modifier.weight(1f).background(fundo).toque(role = Role.Tab, onClick = aoTocar).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Icon(aba.icone, null, tint = cor, modifier = Modifier.size(22.dp))
            if (aba.contador > 0) Text(aba.contador.toString(), style = Estilo.aba.copy(color = Cor.Branco, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.TopEnd).padding(start = 18.dp).background(if (ativa) Cor.Preto else Cor.Azul).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(aba.rotulo, style = Estilo.aba.copy(color = cor, fontSize = 11.sp))
    }
}
