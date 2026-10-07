package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.i18n.UiText

@Composable internal fun BatteryColorsSection(options:ExperienceOptions,enabled:Boolean,update:(ExperienceOptions)->Unit) {
    StudioCard {
        SectionTitle(UiText.t("Cores da bateria"))
        Text(UiText.t("Enquanto o fone Bluetooth aparece no centro, o anel e o número mostram a bateria dele. Ao sair, voltam à bateria do celular. Se o fone não informar a carga, a bateria do celular permanece."),style=MaterialTheme.typography.bodySmall)
        SettingSwitch(UiText.t("Cores por porcentagem"),UiText.t("Escolha uma cor para cada faixa. As cores valem para a bateria do celular e do fone Bluetooth."),options.customBatteryColors,enabled){update(options.copy(customBatteryColors=it))}
        if(options.customBatteryColors) {
            BatteryColorRow(UiText.t("0–20%"),options.batteryLow,enabled){update(options.copy(batteryLow=it))}
            BatteryColorRow(UiText.t("21–80%"),options.batteryMid,enabled){update(options.copy(batteryMid=it))}
            BatteryColorRow(UiText.t("81–100%"),options.batteryHigh,enabled){update(options.copy(batteryHigh=it))}
            TextButton(onClick={val defaults=ExperienceOptions();update(options.copy(batteryLow=defaults.batteryLow,batteryMid=defaults.batteryMid,batteryHigh=defaults.batteryHigh))},enabled=enabled){Text(UiText.t("Restaurar cores padrão"))}
        }
    }
}

@Composable private fun BatteryColorRow(range:String,color:Int,enabled:Boolean,onSelect:(Int)->Unit) {
    var open by remember {mutableStateOf(false)}
    var hex by remember(color){mutableStateOf("%06X".format(color and 0xFFFFFF))}
    val valid=hex.matches(Regex("[0-9a-fA-F]{6}"))
    OutlinedButton(onClick={hex="%06X".format(color and 0xFFFFFF);open=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("battery-color-$range")) {
        Box(Modifier.size(22.dp).background(Color(color),CircleShape))
        Spacer(Modifier.width(12.dp));Text(UiText.format("Escolher cor: {0}",range),Modifier.weight(1f));Text("#%06X".format(color and 0xFFFFFF))
    }
    if(open) {
        OverlayBackdrop()
        AlertDialog(onDismissRequest={open=false},title={Text(UiText.format("Escolher cor: {0}",range))},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf(0xFFFF453A,0xFFFF9F0A,0xFFFFCC00,0xFF30D158,0xFF0A84FF,0xFFBF5AF2).forEach {swatch->
                        TextButton(onClick={hex="%06X".format(swatch.toInt() and 0xFFFFFF)},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {contentDescription="#%06X".format(swatch.toInt() and 0xFFFFFF)},contentPadding=PaddingValues(4.dp)) {Box(Modifier.size(24.dp).background(Color(swatch),CircleShape))}
                    }
                }
                OutlinedTextField(value=hex,onValueChange={hex=it.removePrefix("#").take(6)},singleLine=true,label={Text(UiText.t("Cor hexadecimal (#RRGGBB)"))},isError=!valid,modifier=Modifier.testTag("battery-color-hex"))
                if(!valid)Text(UiText.t("Digite seis caracteres de 0 a 9 e A a F."),color=MaterialTheme.colorScheme.error)
            }},confirmButton={TextButton(onClick={onSelect(hex.toInt(16) or 0xFF000000.toInt());open=false},enabled=enabled&&valid){Text(UiText.t("Aplicar cor"))}},
            dismissButton={TextButton(onClick={open=false}){Text(UiText.t("Fechar"))}})
    }
}
