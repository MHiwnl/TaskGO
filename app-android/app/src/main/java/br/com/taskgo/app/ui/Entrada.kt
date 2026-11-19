package br.com.taskgo.app.ui

import br.com.taskgo.app.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.taskgo.app.Perfil
import kotlinx.coroutines.delay

/* Entrada: o topo da Início do site em forma de app. Área azul com grão, título em duas vozes
   e os dois caminhos com a seta em círculo. Sem logo nem nome: quem abriu o app já sabe qual é. */
@Composable
fun TelaEntrada(aoEscolher: (Perfil) -> Unit) {
    val leve = remember { Animatable(0f) }
    val forte = remember { Animatable(0f) }
    LaunchedEffect(Unit) { leve.animateTo(1f, tween(900)) }
    LaunchedEffect(Unit) { delay(140); forte.animateTo(1f, tween(900)) }
    Column(Modifier.fillMaxSize()) {
        FaixaAzul(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 28.dp)) {
                /* Logo e nome no centro da área azul, na versão branca que o site usa sobre o azul. */
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer { alpha = leve.value; scaleX = .9f + .1f * leve.value; scaleY = .9f + .1f * leve.value }) {
                        Image(painterResource(R.drawable.simbolo), contentDescription = null, modifier = Modifier.size(112.dp),
                            colorFilter = ColorFilter.tint(Cor.Branco))
                        Text(buildAnnotatedString {
                            append("Task")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("GO") }
                        }, style = Estilo.vozForte.copy(color = Cor.Branco, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal),
                            modifier = Modifier.padding(top = 14.dp).semantics { contentDescription = "TaskGO" })
                    }
                }
                /* Cada linha sobe, como o título do topo do site. */
                Text("você escolhe o pedido.", style = Estilo.vozLeve.copy(color = Cor.Branco, fontSize = 32.sp, lineHeight = 38.sp),
                    modifier = Modifier.graphicsLayer { alpha = leve.value; translationY = (1 - leve.value) * 40f })
                Text("o endereço só aparece depois do aceite.", style = Estilo.vozForte.copy(color = Cor.Branco, fontSize = 32.sp, lineHeight = 38.sp),
                    modifier = Modifier.graphicsLayer { alpha = forte.value; translationY = (1 - forte.value) * 40f })
                Text("Pedido, aceite, conversa e acompanhamento no mesmo lugar.", style = Estilo.corpo.copy(color = Cor.Branco),
                    modifier = Modifier.padding(top = 14.dp).graphicsLayer { alpha = forte.value })
            }
        }
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Seta("Sou profissional", forte = true) { aoEscolher(Perfil.PROFISSIONAL) }
            Spacer(Modifier.height(14.dp))
            Seta("Preciso de um serviço", forte = false) { aoEscolher(Perfil.CLIENTE) }
            Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).border(1.5.dp, Cor.Texto2, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Protótipo · pessoas, pedidos e valores fictícios", style = Estilo.rotulo)
            }
        }
    }
}

/* A seta em círculo do site: texto em mono e um círculo de 56 dp. A forte vem preenchida de azul. */
@Composable
fun Seta(texto: String, forte: Boolean, aoTocar: () -> Unit) {
    Row(Modifier.fillMaxWidth().toque(onClick = aoTocar).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = Estilo.botao.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (forte) Cor.Azul else Cor.Fundo)
            .border(1.dp, if (forte) Cor.Azul else Cor.LinhaForte, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = if (forte) Cor.Branco else Cor.Texto, modifier = Modifier.size(22.dp))
        }
    }
}
