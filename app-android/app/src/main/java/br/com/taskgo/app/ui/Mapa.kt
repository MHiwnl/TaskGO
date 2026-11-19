package br.com.taskgo.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.hypot

/* Relógio que atualiza a tela: a posição no mapa e os cronômetros dependem dele. */
@Composable
fun agoraAoVivo(intervalo: Long = 200): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) { value = System.currentTimeMillis(); delay(intervalo) }
}

/* Trajeto pelas ruas, em coordenadas relativas ao mapa (0 a 1). Começa no profissional e termina na casa do cliente. */
private val ROTA = listOf(Offset(.14f, .88f), Offset(.14f, .62f), Offset(.46f, .62f), Offset(.46f, .34f), Offset(.80f, .34f), Offset(.80f, .18f))

private fun pontoNaRota(t: Float, w: Float, h: Float): Offset {
    val pts = ROTA.map { Offset(it.x * w, it.y * h) }
    val trechos = pts.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
    var resto = trechos.sum() * t
    pts.zipWithNext().forEachIndexed { i, (a, b) ->
        if (resto <= trechos[i]) { val f = if (trechos[i] == 0f) 0f else resto / trechos[i]; return Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f) }
        resto -= trechos[i]
    }
    return pts.last()
}

/* Mapa desenhado pelo próprio app (protótipo, sem internet): ruas, trajeto e o ponto andando até o destino. */
@Composable
fun MapaRota(progresso: Float, modifier: Modifier = Modifier) {
    val pulso by rememberInfiniteTransition(label = "gps").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(if (TemaApp.escuro) Color(0xFF0E0E11) else Color(0xFFECECEF))
        /* Quarteirões e um parque. */
        drawRect(if (TemaApp.escuro) Color(0xFF12201A) else Color(0xFFDDEBDF), topLeft = Offset(w * .56f, h * .44f), size = Size(w * .3f, h * .14f))
        val rua = if (TemaApp.escuro) Color(0xFF26262C) else Color(0xFFFFFFFF); val ruaFina = if (TemaApp.escuro) Color(0xFF1A1A1F) else Color(0xFFF6F6F8)
        listOf(.14f, .30f, .46f, .64f, .80f).forEach { x -> drawLine(if (x == .46f || x == .14f) rua else ruaFina, Offset(w * x, 0f), Offset(w * x, h), if (x == .46f) 22f else 12f) }
        listOf(.18f, .34f, .48f, .62f, .76f, .90f).forEach { y -> drawLine(if (y == .62f || y == .34f) rua else ruaFina, Offset(0f, h * y), Offset(w, h * y), if (y == .62f) 22f else 12f) }
        /* Trajeto completo e o que já foi percorrido. */
        val caminho = Path().apply { ROTA.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * w, p.y * h) else lineTo(p.x * w, p.y * h) } }
        drawPath(caminho, Cor.Azul.copy(alpha = .3f), style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val feito = Path(); var t = 0f; feito.moveTo(ROTA[0].x * w, ROTA[0].y * h)
        while (t < progresso) { t = (t + .01f).coerceAtMost(progresso); val p = pontoNaRota(t, w, h); feito.lineTo(p.x, p.y) }
        drawPath(feito, Cor.Azul, style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        /* Casa do cliente. */
        val destino = Offset(ROTA.last().x * w, ROTA.last().y * h)
        drawCircle(Cor.Branco, 26f, destino); drawCircle(Cor.VermelhoForte, 17f, destino)
        /* Posição do profissional, com o anel do GPS. */
        val atual = pontoNaRota(progresso, w, h)
        drawCircle(Cor.Azul.copy(alpha = .35f * (1 - pulso)), 26f + 40f * pulso, atual)
        drawCircle(Cor.Branco, 20f, atual); drawCircle(Cor.Azul, 14f, atual)
    }
}
