package io.github.kvmy666.duostatusbar.ui

import android.os.SystemClock
import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kvmy666.duostatusbar.fx.AuroraText
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.fx.NfcParticlePainter
import io.github.kvmy666.duostatusbar.fx.NfcPhoneSimulation
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable internal fun NfcParticleSettings(options:ExperienceOptions,enabled:Boolean,animationsEnabled:Boolean,search:SearchGate,onUpdate:(ExperienceOptions)->Unit) {
    val title=AuroraText.t("Partículas na aproximação NFC","NFC proximity particles","Partículas al acercar NFC")
    val detail=AuroraText.t(
        "Efeito experimental no topo da tela, quando a ROM informar um campo NFC próximo. Não confirma pagamento aprovado. Recebimento pelo InfinitePay não foi validado.",
        "Experimental effect at the top of the screen when the ROM reports a nearby NFC field. It does not confirm an approved payment. Receiving through InfinitePay has not been validated.",
        "Efecto experimental en la parte superior cuando la ROM informa un campo NFC cercano. No confirma un pago aprobado. La recepción con InfinitePay no está validada.")
    if(!search(title,detail,"NFC","InfinitePay"))return
    StudioCard {
        SectionTitle(title)
        SettingSwitch(title,detail,options.nfcParticles,enabled) {onUpdate(options.copy(nfcParticles=it))}
        Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        NfcParticlePreview(enabled && animationsEnabled && !options.networkOnly,enabled)
    }
}

/** Local-only demonstration: no broadcast, payment signal or overlay permission is requested. */
@Composable internal fun NfcParticlePreview(motionEnabled:Boolean=true,moduleEnabled:Boolean=true) {
    val context=LocalContext.current
    val painter=remember(context) {NfcPhoneSimulation(context)}
    var outcome by remember {mutableStateOf(NfcPhoneSimulation.Outcome.READ)}
    DisposableEffect(painter) {onDispose {painter.dispose()}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val host=LocalView.current
    var foreground by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var visible by remember {mutableStateOf(false)}
    var playing by remember {mutableStateOf(false)}
    var elapsed by remember {mutableLongStateOf(-1L)}
    var systemMotion by remember {mutableStateOf(ValueAnimator.areAnimatorsEnabled())}
    DisposableEffect(host) {
        val resolver=host.context.contentResolver
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean) {systemMotion=ValueAnimator.areAnimatorsEnabled()}
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {resolver.unregisterContentObserver(observer)}
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if(!foreground){playing=false;elapsed=-1L}
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(playing,visible,foreground,motionEnabled,systemMotion) {
        if(!playing||!visible||!foreground||!motionEnabled||!systemMotion){playing=false;elapsed=-1L;return@LaunchedEffect}
        val start=SystemClock.uptimeMillis()
        try {
            while(true) {
                val age=SystemClock.uptimeMillis()-start
                if(age>=NfcPhoneSimulation.DURATION_MS)break
                elapsed=age
                delay(NfcParticlePainter.FRAME_DELAY_MS)
            }
        } finally {elapsed=-1L;playing=false}
    }
    Text(AuroraText.t("Simulação na barra · 6 segundos","Status bar simulation · 6 seconds","Simulación en la barra · 6 segundos"),style=MaterialTheme.typography.labelMedium)
    Canvas(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(20.dp))
        .background(Color(0xFF111722)).onGloballyPositioned {coordinates->
            val b=coordinates.boundsInWindow()
            visible=coordinates.isAttached&&b.width>0f&&b.height>0f&&b.bottom>0f&&b.top<host.rootView.height&&b.right>0f&&b.left<host.rootView.width
        }) {
        painter.draw(drawContext.canvas.nativeCanvas,size.width,size.height,elapsed,outcome)
    }
    Text(AuroraText.t("Laranja, check e recusa são cenários simulados. O campo NFC não informa distância nem aprovação bancária.","Orange, checkmark and decline are simulated scenarios. An NFC field does not report distance or bank approval.","Naranja, marca y rechazo son escenarios simulados. El campo NFC no informa distancia ni aprobación bancaria."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        NfcPhoneSimulation.Outcome.entries.forEach {item->
            FilterChip(selected=outcome==item,onClick={playing=false;elapsed=-1;outcome=item},modifier=Modifier.weight(1f),label={Text(when(item){
                NfcPhoneSimulation.Outcome.READ->AuroraText.t("Leitura","Read","Lectura")
                NfcPhoneSimulation.Outcome.DECLINED->AuroraText.t("Recusa","Decline","Rechazo")
                NfcPhoneSimulation.Outcome.MOVE_CLOSER->AuroraText.t("Distante","Too far","Lejos")
            })})
        }
    }
    if(!moduleEnabled)Text(AuroraText.t("Ative o módulo para testar.","Enable the module to preview.","Activa el módulo para probar."),style=MaterialTheme.typography.bodySmall)
    else if(!motionEnabled)Text(AuroraText.t("Ative as animações e desative Somente rede para testar.","Enable animations and turn off Network only to preview.","Activa las animaciones y desactiva Solo red para probar."),style=MaterialTheme.typography.bodySmall)
    else if(!systemMotion)Text(AuroraText.t("As animações estão desativadas nas configurações do Android.","Animations are disabled in Android settings.","Las animaciones están desactivadas en los ajustes de Android."),style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={playing=!playing},enabled=foreground&&visible&&motionEnabled&&systemMotion,modifier=Modifier.heightIn(min=48.dp)) {
        Text(if(playing)AuroraText.t("Parar prévia","Stop preview","Detener vista previa") else AuroraText.t("Testar partículas NFC","Preview NFC particles","Probar partículas NFC"))
    }
}
