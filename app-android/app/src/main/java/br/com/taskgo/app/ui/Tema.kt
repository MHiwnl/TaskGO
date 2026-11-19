package br.com.taskgo.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.taskgo.app.R

/* ---------- Tema: escuro (padrão, como o site), claro (para a rua) ou o do sistema ---------- */
enum class ModoTema(val rotulo: String) { ESCURO("Escuro"), CLARO("Claro"), SISTEMA("Do sistema") }

object TemaApp {
    var modo by mutableStateOf(ModoTema.ESCURO)
    var sistemaEscuro by mutableStateOf(true)
    /* A Activity liga isto ao armazenamento do aparelho, para a escolha valer na próxima abertura. */
    var salvar: (ModoTema) -> Unit = {}
    val escuro get() = when (modo) { ModoTema.ESCURO -> true; ModoTema.CLARO -> false; ModoTema.SISTEMA -> sistemaEscuro }
    fun escolher(m: ModoTema) { modo = m; salvar(m) }
}

private fun t(escuro: Long, claro: Long) = Color(if (TemaApp.escuro) escuro else claro)

/* Os mesmos tokens do site (css/base.css): preto puro, um azul só e cores de estado.
   Lidos a cada desenho, então a troca de tema vale na hora para o app inteiro. */
object Cor {
    val Fundo get() = t(0xFF000000, 0xFFFFFFFF)
    val Cartao get() = Fundo
    val Cartao2 get() = t(0xFF16161A, 0xFFF3F3F5)
    val Superficie3 get() = t(0xFF212126, 0xFFE7E7EB)
    val Linha get() = t(0x2BFFFFFF, 0x24000000)
    val Rejunte get() = Linha
    val LinhaForte get() = t(0x5CFFFFFF, 0x6B000000)
    val Texto get() = t(0xFFFFFFFF, 0xFF000000)
    val Texto2 get() = t(0xFFD3D5DC, 0xFF33353D)
    val Apagado get() = t(0xFFA3A6B1, 0xFF5A5D68)
    val AzulLeitura get() = t(0xFF8AA3FF, 0xFF1A3FD6)
    val AzulFundo get() = t(0xFF0A1440, 0xFFE8EDFF)
    val Verde get() = t(0xFF4BE0A0, 0xFF0A7A4B)
    val VerdeForte get() = t(0xFF35D08F, 0xFF10A56A)
    val VerdeFundo get() = t(0xFF06261A, 0xFFE2F5EC)
    val Ambar get() = t(0xFFFFB020, 0xFFD98A00)
    val AmbarTexto get() = t(0xFFFFC04D, 0xFF8A5A00)
    val AmbarFundo get() = t(0xFF2A1F06, 0xFFFFF3DC)
    val Vermelho get() = t(0xFFFF7D82, 0xFFC4262E)
    val VermelhoForte get() = t(0xFFFF6369, 0xFFD9333B)

    /* Fixas nos dois temas: o azul da marca e o texto sobre ele. */
    val Azul = Color(0xFF2B59FF)
    val Branco = Color(0xFFFFFFFF)
    val Preto = Color(0xFF000000)
}

/* Cantos retos em tudo o que contém algo, como no site. O círculo fica só para marcadores e avatar. */
object Forma {
    val cartao = RoundedCornerShape(0.dp)
    val botao = RoundedCornerShape(0.dp)
    val pilula = RoundedCornerShape(0.dp)
}

@OptIn(ExperimentalTextApi::class)
private fun peso(id: Int, w: Int, estilo: FontStyle = FontStyle.Normal) =
    Font(id, weight = FontWeight(w), style = estilo, variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/* As letras do site: Spline Sans Mono (com itálico verdadeiro) nos títulos, rótulos e números; Spline Sans no texto. */
val SplineSans = FontFamily(
    peso(R.font.spline_sans, 400), peso(R.font.spline_sans, 500),
    peso(R.font.spline_sans, 600), peso(R.font.spline_sans, 700)
)
val SplineMono = FontFamily(
    peso(R.font.spline_sans_mono, 300), peso(R.font.spline_sans_mono, 400), peso(R.font.spline_sans_mono, 500),
    peso(R.font.spline_sans_mono, 600), peso(R.font.spline_sans_mono, 700),
    peso(R.font.spline_sans_mono_italic, 300, FontStyle.Italic), peso(R.font.spline_sans_mono_italic, 400, FontStyle.Italic)
)

object Estilo {
    /* Título em duas vozes, como no site: primeira linha em itálico leve, segunda em negrito. */
    val vozLeve get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.6).sp, color = Cor.Texto)
    val vozForte get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.6).sp, color = Cor.Texto)
    val titulo get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp, color = Cor.Texto)
    val secao get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, color = Cor.Texto)
    val corpo get() = TextStyle(fontFamily = SplineSans, fontSize = 15.sp, lineHeight = 22.sp, color = Cor.Texto2)
    val item get() = TextStyle(fontFamily = SplineSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = Cor.Texto)
    val nota get() = TextStyle(fontFamily = SplineSans, fontSize = 13.sp, lineHeight = 18.sp, color = Cor.Apagado)
    val rotulo get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = Cor.Texto2)
    val registro get() = TextStyle(fontFamily = SplineMono, fontSize = 14.sp, lineHeight = 19.sp, color = Cor.Texto)
    val numero get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.8).sp, color = Cor.Texto)
    val botao get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Cor.Texto)
    val aba get() = TextStyle(fontFamily = SplineMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Cor.Texto2)
}

@Composable
fun TemaTaskGO(conteudo: @Composable () -> Unit) {
    val sistema = isSystemInDarkTheme()
    SideEffect { TemaApp.sistemaEscuro = sistema }
    val esquema = if (TemaApp.escuro)
        darkColorScheme(primary = Cor.Azul, onPrimary = Cor.Branco, background = Cor.Fundo, onBackground = Cor.Texto, surface = Cor.Cartao, onSurface = Cor.Texto, outline = Cor.LinhaForte, surfaceContainerHigh = Cor.Cartao)
    else lightColorScheme(primary = Cor.Azul, onPrimary = Cor.Branco, background = Cor.Fundo, onBackground = Cor.Texto, surface = Cor.Cartao, onSurface = Cor.Texto, outline = Cor.LinhaForte, surfaceContainerHigh = Cor.Cartao)
    MaterialTheme(colorScheme = esquema, typography = Typography(bodyLarge = Estilo.corpo, bodyMedium = Estilo.corpo), content = conteudo)
}
