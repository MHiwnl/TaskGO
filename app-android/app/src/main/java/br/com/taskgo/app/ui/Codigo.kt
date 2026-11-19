package br.com.taskgo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Quatro casas para o código que o cliente informa. Treme quando o código está errado. */
@Composable
fun CampoCodigo(valor: String, aoMudar: (String) -> Unit, erro: Boolean, tentativa: Int) {
    val foco = remember { FocusRequester() }
    val tremor = remember { Animatable(0f) }
    LaunchedEffect(Unit) { foco.requestFocus() }
    LaunchedEffect(tentativa) { if (erro) for (x in listOf(18f, -14f, 10f, -6f, 0f)) tremor.animateTo(x, tween(55)) }
    BasicTextField(
        value = valor, onValueChange = { aoMudar(it.filter(Char::isDigit).take(4)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true,
        modifier = Modifier.focusRequester(foco).graphicsLayer { translationX = tremor.value },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    val ativa = i == valor.length
                    Box(Modifier.size(64.dp, 76.dp).clip(Forma.botao).background(Cor.Cartao)
                        .border(2.dp, when { erro -> Cor.Vermelho; ativa -> Cor.Azul; else -> Cor.LinhaForte }, Forma.botao),
                        contentAlignment = Alignment.Center) {
                        Text(valor.getOrNull(i)?.toString() ?: "", style = Estilo.numero.copy(fontSize = 34.sp))
                    }
                }
            }
        }
    )
}

/* O código como o cliente vê: grande, para ler em voz alta. */
@Composable
fun CodigoGrande(codigo: String, fundo: androidx.compose.ui.graphics.Color = Cor.Azul) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        codigo.forEach { c ->
            Box(Modifier.size(58.dp, 70.dp).clip(Forma.botao).background(fundo), contentAlignment = Alignment.Center) {
                Text(c.toString(), style = Estilo.numero.copy(fontSize = 34.sp, color = Cor.Branco))
            }
        }
    }
}
