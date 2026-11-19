package br.com.taskgo.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/* =========================================================
   O SITE NO APP
   Área azul com grão, etapas em pontos ligados e o bloco azul de "feito".
   ========================================================= */

/* Grão: ruído fino, igual ao das áreas azuis do site, desenhado uma vez e repetido. */
private val GRAO: ImageBitmap by lazy {
    val lado = 160
    val px = IntArray(lado * lado) { val v = Random.nextInt(256); (Random.nextInt(18, 60) shl 24) or (v shl 16) or (v shl 8) or v }
    android.graphics.Bitmap.createBitmap(px, lado, lado, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

fun Modifier.grao(): Modifier = drawWithCache {
    val pincel = ShaderBrush(ImageShader(GRAO, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent { drawRect(pincel, blendMode = BlendMode.Overlay); drawContent() }
}

/* Área azul inteira com grão: texto, linhas e foco passam a branco. */
@Composable
fun FaixaAzul(modifier: Modifier = Modifier, conteudo: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth().background(Cor.Azul).grao(), content = conteudo)
}

/* Etapas do atendimento: pontos ligados por uma linha. Concluída na cor do texto, atual em azul. */
@Composable
fun EtapasPontos(rotulos: List<String>, alcancadas: Int, @Suppress("UNUSED_PARAMETER") tamanho: Dp = 0.dp) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            rotulos.forEachIndexed { i, _ ->
                val atual = i == alcancadas - 1
                val cor by animateColorAsState(when { atual -> Cor.Azul; i < alcancadas -> Cor.Texto; else -> Color.Transparent }, tween(300), label = "etapa")
                Box(Modifier.size(if (atual) 16.dp else 12.dp).clip(CircleShape).background(cor).border(1.5.dp, if (i < alcancadas) cor else Cor.Apagado, CircleShape))
                if (i < rotulos.lastIndex) Box(Modifier.weight(1f).height(1.dp).background(if (i < alcancadas - 1) Cor.Texto else Cor.Linha))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            rotulos.forEachIndexed { i, r ->
                Text(r, style = Estilo.aba.copy(color = if (i < alcancadas) Cor.Texto else Cor.Apagado, fontSize = 11.sp))
            }
        }
    }
}

/* Bloco azul com grão e o visto: o momento em que algo foi confirmado. Assenta com uma mola curta. */
@Composable
fun SeloFeito(tamanho: Dp = 96.dp) {
    val escala = remember { Animatable(.6f) }
    LaunchedEffect(Unit) { escala.animateTo(1f, spring(dampingRatio = .45f, stiffness = 420f)) }
    Box(Modifier.size(tamanho).graphicsLayer { scaleX = escala.value; scaleY = escala.value }.background(Cor.Azul).grao(), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Check, null, tint = Cor.Branco, modifier = Modifier.size(tamanho * .55f))
    }
}
