package br.com.taskgo.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import br.com.taskgo.app.ui.AppCliente
import br.com.taskgo.app.ui.AppProfissional
import br.com.taskgo.app.ui.Cor
import br.com.taskgo.app.ui.DialogoModeracao
import br.com.taskgo.app.ui.Estilo
import br.com.taskgo.app.ui.Forma
import br.com.taskgo.app.ui.TelaEntrada
import br.com.taskgo.app.ui.TelaRecurso
import br.com.taskgo.app.ui.TemaTaskGO
import br.com.taskgo.app.ui.TemaApp
import br.com.taskgo.app.ui.ModoTema
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/* App nativo da TaskGO: protótipo para visualizar o produto, separado do site e sem servidor. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.BLACK), navigationBarStyle = SystemBarStyle.dark(Color.BLACK))
        super.onCreate(savedInstanceState)
        /* Tema escolhido no perfil: lido e guardado no aparelho. */
        val prefs = getSharedPreferences("taskgo", MODE_PRIVATE)
        TemaApp.modo = runCatching { ModoTema.valueOf(prefs.getString("tema", ModoTema.ESCURO.name)!!) }.getOrDefault(ModoTema.ESCURO)
        TemaApp.salvar = { prefs.edit().putString("tema", it.name).apply() }
        tratar(intent)
        setContent { TemaTaskGO { App() } }
    }

    /* Volta do Waze pelo botão flutuante ou pela notificação. */
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); tratar(intent) }
    private fun tratar(intent: Intent?) {
        if (intent?.getBooleanExtra(Navegacao.EXTRA_CHEGUEI, false) == true) {
            intent.removeExtra(Navegacao.EXTRA_CHEGUEI)
            Global.estado.voltouDoWaze = true
        }
    }

    override fun onResume() { super.onResume(); Navegacao.esconder(this) }
}

@Composable
fun App() {
    val estado = Global.estado
    /* Barras do sistema no tom do tema: ícones claros no escuro, escuros no claro. */
    val activity = LocalContext.current as ComponentActivity
    val escuro = TemaApp.escuro
    SideEffect {
        val estilo = if (escuro) SystemBarStyle.dark(Color.BLACK) else SystemBarStyle.light(Color.WHITE, Color.BLACK)
        activity.enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
    }
    BackHandler(estado.recursoAberto != null) { estado.recursoAberto = null }
    BackHandler(estado.perfil != null && estado.recursoAberto == null) { estado.perfil = null }
    Box(Modifier.fillMaxSize().background(Cor.Fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Crossfade(estado.perfil, label = "perfil") { perfil ->
            when (perfil) {
                null -> TelaEntrada { estado.perfil = it }
                Perfil.CLIENTE -> AppCliente({ estado.perfil = Perfil.PROFISSIONAL }) { estado.perfil = null }
                Perfil.PROFISSIONAL -> AppProfissional({ estado.perfil = Perfil.CLIENTE }) { estado.perfil = null }
            }
        }
        estado.recursoAberto?.let { lado -> Box(Modifier.fillMaxSize().background(Cor.Fundo)) { TelaRecurso(lado) { estado.recursoAberto = null } } }
        DialogoModeracao { estado.recursoAberto = it }

        /* Aviso curto no topo, que some sozinho. */
        LaunchedEffect(estado.aviso) { if (estado.aviso != null) { delay(3500); estado.aviso = null } }
        AnimatedVisibility(estado.aviso != null, Modifier.align(Alignment.TopCenter).padding(12.dp),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Text(estado.aviso ?: "", style = Estilo.corpo.copy(color = Cor.Texto),
                modifier = Modifier.fillMaxWidth().clip(Forma.botao).background(Cor.Cartao2).padding(16.dp))
        }
    }
}
