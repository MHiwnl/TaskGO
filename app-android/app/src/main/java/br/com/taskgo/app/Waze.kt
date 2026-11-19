package br.com.taskgo.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/* =========================================================
   NAVEGAÇÃO PELO WAZE
   Ao abrir o Waze, o app deixa um botão por cima dele ("Cheguei ao local"), quando o celular permite
   aparecer sobre outros apps, e uma notificação com o mesmo botão. Tocar volta ao TaskGO.
   ========================================================= */
object Navegacao {
    const val EXTRA_CHEGUEI = "cheguei"
    private const val CANAL = "atendimento"
    private const val ID_NOTIFICACAO = 7
    private var botao: View? = null

    fun podeSobrepor(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun pedirSobreposicao(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun voltarAoApp(ctx: Context) = Intent(ctx, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        .putExtra(EXTRA_CHEGUEI, true)

    fun abrirWaze(ctx: Context, endereco: String) {
        mostrarBotao(ctx.applicationContext)
        mostrarNotificacao(ctx.applicationContext)
        val uri = Uri.parse("https://waze.com/ul?q=" + Uri.encode(endereco) + "&navigate=yes")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun mostrarBotao(ctx: Context) {
        if (botao != null || !podeSobrepor(ctx)) return
        val wm = ctx.getSystemService(WindowManager::class.java)
        val v = TextView(ctx).apply {
            text = "Cheguei ao local · voltar ao app"
            setTextColor(android.graphics.Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setPadding(56, 36, 56, 36)
            background = GradientDrawable().apply { cornerRadius = 60f; setColor(0xFF2B59FF.toInt()) }
            elevation = 12f
            setOnClickListener { esconder(ctx); ctx.startActivity(voltarAoApp(ctx)) }
        }
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 160 }
        wm.addView(v, p); botao = v
    }

    private fun mostrarNotificacao(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Atendimento em andamento", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, 1, voltarAoApp(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("A caminho do cliente")
            .setContentText("Toque em \"Cheguei ao local\" quando estiver no endereço.")
            .setOngoing(true).setContentIntent(pi)
            .addAction(0, "Cheguei ao local", pi)
            .build()
        try { nm.notify(ID_NOTIFICACAO, n) } catch (_: SecurityException) { }
    }

    fun esconder(ctx: Context) {
        botao?.let { runCatching { ctx.applicationContext.getSystemService(WindowManager::class.java).removeView(it) } }
        botao = null
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_NOTIFICACAO)
    }
}
