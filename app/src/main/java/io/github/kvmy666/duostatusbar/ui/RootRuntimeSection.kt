package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.fx.AuroraText
import io.github.kvmy666.duostatusbar.RootLogs
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kvmy666.duostatusbar.settings.RuntimeBackend
import io.github.kvmy666.duostatusbar.standalone.RootRuntimeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun rootStageText(stage: RootRuntimeManager.Stage): String = when (stage) {
    RootRuntimeManager.Stage.AUTHORIZATION -> AuroraText.t("Verificando acesso root (até 12 s). No KernelSU, confira a lista Superusuário; pode não aparecer uma janela.", "Checking root access (up to 12 s). In KernelSU, check the Superuser list; a popup may not appear.", "Verificando acceso root (hasta 12 s). En KernelSU, revisa Superusuario; puede no aparecer una ventana.")
    RootRuntimeManager.Stage.STARTING -> AuroraText.t("Root UID 0 confirmado. Iniciando a barra…", "Root UID 0 confirmed. Starting the bar…", "Root UID 0 confirmado. Iniciando la barra…")
    RootRuntimeManager.Stage.FIRST_FRAME -> AuroraText.t("Root autorizado. Aguardando o primeiro desenho da barra (até 12 s)…", "Root authorized. Waiting for the bar's first frame (up to 12 s)…", "Root autorizado. Esperando el primer dibujo de la barra (hasta 12 s)…")
    else -> AuroraText.t("Concluindo…", "Finishing…", "Finalizando…")
}

private fun rootFailureText(): String = when {
    RootRuntimeManager.stage != RootRuntimeManager.Stage.ACCESS_FAILED -> AuroraText.t("Root UID 0 foi confirmado, mas a barra não iniciou. Isso é uma falha de execução, não de autorização. Exporte os diagnósticos em Ajuda.", "Root UID 0 was confirmed, but the bar did not start. This is a runtime failure, not an authorization failure. Export diagnostics in Help.", "Root UID 0 fue confirmado, pero la barra no inició. Es un fallo de ejecución, no de autorización. Exporta los diagnósticos en Ayuda.")
    RootLogs.lastAccess == RootLogs.Access.NON_ROOT_PROFILE -> AuroraText.t("O gerenciador respondeu com um perfil sem UID 0. Revise o perfil de DUO Recreate no gerenciador root; este modo requer UID 0.", "The manager returned a profile without UID 0. Review DUO Recreate's profile in your root manager; this mode requires UID 0.", "El gestor devolvió un perfil sin UID 0. Revisa el perfil de DUO Recreate en tu gestor root; este modo requiere UID 0.")
    RootLogs.lastAccess == RootLogs.Access.TIMEOUT -> AuroraText.t("A verificação terminou sem resposta. No KernelSU / KSU Next, autorize DUO Recreate em Superusuário e tente novamente. Nenhuma notificação do Android é necessária.", "The check ended without a response. In KernelSU / KSU Next, allow DUO Recreate in Superuser and retry. No Android notification is required.", "La verificación terminó sin respuesta. En KernelSU / KSU Next, autoriza DUO Recreate en Superusuario y reintenta. No se necesita una notificación de Android.")
    else -> AuroraText.t("Acesso root não confirmado. Confira DUO Recreate em Superusuário no gerenciador. Isso não prova que o telefone esteja sem root.", "Root access was not confirmed. Check DUO Recreate in your manager's Superuser list. This does not prove the phone is unrooted.", "Acceso root no confirmado. Revisa DUO Recreate en Superusuario del gestor. Esto no demuestra que el teléfono no tenga root.")
}

@Composable internal fun RootRuntimeSection(matches: SearchGate) {
    if (!matches(UiText.t("Root sem LSPosed"), "root", "LSPosed")) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(RuntimeBackend.ready(context)) }
    var mode by remember { mutableStateOf(RuntimeBackend.mode(context)) }
    var message by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(RootRuntimeManager.stage) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, busy) {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) {
            ready = RuntimeBackend.ready(context); mode = RuntimeBackend.mode(context)
            phase = RootRuntimeManager.stage
            delay(if (busy) 250 else 2000)
        }
      }
    }
    StudioCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(UiText.t("Root sem LSPosed"))
            Text(UiText.t("Modo experimental: executa o Canvas em um processo root próprio, sem ativar módulo no LSPosed."))
            Text(UiText.t("Ative primeiro Personalizar a barra. Toque em Iniciar e autorize no gerenciador root. Após reiniciar o telefone, inicie novamente aqui."), style = MaterialTheme.typography.bodySmall)
            Text(UiText.t("A ocultação dos ícones depende da ROM. Neste modo, notificações e ajustes rápidos compartilham o tamanho da tela normal; tamanhos independentes e alguns acessos internos do sistema precisam do módulo."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(UiText.t(if (ready) "Canvas root em execução; ocultação visual não verificada." else if (mode == RuntimeBackend.ROOT) "Aguardando confirmação do processo root." else "Processo root parado. O caminho LSPosed continua opcional."))
            Text(AuroraText.t("KernelSU / KSU Next / APatch: autorize DUO Recreate (io.github.RECREATE.statusbar) na lista Superusuário do gerenciador. O perfil precisa fornecer UID 0. Depois volte e toque em Iniciar com root. Ausência de uma janela de autorização não significa ausência de root.", "KernelSU / KSU Next / APatch: allow DUO Recreate (io.github.RECREATE.statusbar) in the manager's Superuser list. The profile must provide UID 0. Return and tap Start with root. No permission popup does not mean root is absent.", "KernelSU / KSU Next / APatch: autoriza DUO Recreate (io.github.RECREATE.statusbar) en Superusuario del gestor. El perfil debe proporcionar UID 0. Vuelve y pulsa Iniciar con root. La ausencia de una ventana de autorización no significa que no haya root."), style=MaterialTheme.typography.bodySmall)
            if (busy) Text(rootStageText(phase), color = MaterialTheme.colorScheme.primary)
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; message = ""
                scope.launch {
                    try {
                        message = if (RootRuntimeManager.start(context)) UiText.t("Processo iniciado e primeiro desenho confirmado.") else rootFailureText()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { message = "Não foi possível iniciar. Confira o root e envie os diagnósticos. Os ícones originais são preservados." }
                    finally { busy = false; ready = RuntimeBackend.ready(context); mode = RuntimeBackend.mode(context) }
                }
            }) { Text(UiText.t(if (busy) "Aguarde…" else if (ready) "Verificar processo root" else "Iniciar com root")) }
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; message = ""
                scope.launch {
                    try {
                        message = UiText.t(if (RootRuntimeManager.stop(context)) "Processo parado e restauração solicitada." else "Restauração não confirmada. Confira o acesso root antes de continuar.")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { message = "Restauração não confirmada. Confira o acesso root antes de continuar." } finally { busy = false; ready = RuntimeBackend.ready(context); mode = RuntimeBackend.mode(context) }
                }
            }) { Text(UiText.t("Parar root e restaurar ícones")) }
        }
    }
}

