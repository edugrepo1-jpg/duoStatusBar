package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.i18n.UiText

@Composable internal fun LanguagePicker(firstLaunch:Boolean,onSelect:(String)->Unit) {
    Column(Modifier.fillMaxWidth().then(if(firstLaunch)Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(if(firstLaunch)24.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(firstLaunch) {
            Text("DUO",style=MaterialTheme.typography.displaySmall)
            Text("Escolha seu idioma\nChoose your language\nElige tu idioma",style=MaterialTheme.typography.headlineSmall)
            Text("Português · English · Español",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else SectionTitle(UiText.t("Idioma"))
        for((tag,name,detail) in listOf(Triple("pt-BR","Português","Brasil"),Triple("en","English","United States"),Triple("es","Español","España"))) {
            val selected=UiText.language==tag
            Surface(onClick={onSelect(tag)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),
                color=if(selected&&!firstLaunch)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border=BorderStroke(1.dp,if(selected&&!firstLaunch)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    LanguageFlag(tag)
                    Column(Modifier.weight(1f)) { Text(name,style=MaterialTheme.typography.titleMedium);Text(UiText.t(detail),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    StudioGlyph(if(selected&&!firstLaunch)StudioSymbol.CHECK else StudioSymbol.ARROW,MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Small flags drawn locally, consistent even when the system font lacks emoji flags. */
@Composable internal fun LanguageFlag(tag:String) {
    Canvas(Modifier.size(46.dp,32.dp).clip(RoundedCornerShape(7.dp))) {
        val w=size.width;val h=size.height
        when(tag) {
            "pt-BR"->{drawRect(Color(0xFF169B62));val p=Path().apply {moveTo(w*.5f,h*.08f);lineTo(w*.93f,h*.5f);lineTo(w*.5f,h*.92f);lineTo(w*.07f,h*.5f);close()};drawPath(p,Color(0xFFFFDF00));drawCircle(Color(0xFF002776),h*.25f,Offset(w*.5f,h*.5f));drawLine(Color.White,Offset(w*.32f,h*.43f),Offset(w*.68f,h*.55f),h*.05f)}
            "en"->{drawRect(Color.White);for(i in 0..6)drawRect(Color(0xFFB22234),Offset(0f,i*h*2/13),Size(w,h/13));drawRect(Color(0xFF3C3B6E),Offset.Zero,Size(w*.45f,h*7/13));for(y in 0..3)for(x in 0..4)drawCircle(Color.White,h*.018f,Offset(w*(.045f+x*.08f),h*(.07f+y*.12f)))}
            else->{drawRect(Color(0xFFAA151B));drawRect(Color(0xFFF1BF00),Offset(0f,h*.25f),Size(w,h*.5f));drawRect(Color(0xFFAA151B),Offset(w*.24f,h*.4f),Size(w*.1f,h*.2f))}
        }
    }
}
