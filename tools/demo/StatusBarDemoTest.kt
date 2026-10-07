package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

/** Documentation export: native production renderer in an explicitly illustrated phone shell. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusBarDemoTest {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun color(hex:String)=Color.parseColor(hex)
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float=25f,fg:String="#FFFFFF",bold:Boolean=false) {
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color(fg);paint.textSize=size
        paint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s,x,y,paint)
    }
    private fun rect(c:Canvas,x:Float,y:Float,r:Float,b:Float,fg:String,round:Float=24f) {
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color(fg);c.drawRoundRect(x,y,r,b,round,round,paint)
    }
    private fun ring(c:Canvas,view:DuoCanvasView,x:Float,y:Float,side:Int) {
        val h=RingGeometry.elementHeightPx(side)
        view.measure(View.MeasureSpec.makeMeasureSpec(side,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        view.layout(0,0,side,h);val save=c.save();c.translate(x,y);view.draw(c);c.restoreToCount(save)
    }
    @Test fun `export native status bar demonstration frames`() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0x3FFF,experienceJson=ExperienceOptions.ALL.encode()),ctx)
        val small=DuoCanvasView(ctx).apply {animationsEnabled=false;start()}
        val zoom=DuoCanvasView(ctx).apply {animationsEnabled=false;start()}
        val cycle=SlotCycle();cycle.configure(ExperienceOptions.ALL.copy(dwellMs=1400,exitMs=200,entryMs=240),0)
        cycle.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH,SlotIcon.NFC,SlotIcon.AIRPODS,SlotIcon.WIFI_OFFLINE),0)
        val output=File("build/statusbar-demo").apply {mkdirs()}
        fun scene(t:Long,mode:String="home"):Bitmap {
            val charging=t in 12000L until 16000L
            val music=t in 16000L until 20000L
            val check=(t-1000).takeIf {it in 0 until EffectTimeline.UNLOCK_MS}?:-1L
            val slot=when {t<5000->SlotFrame(SlotIcon.WIFI);t<12000->cycle.frame(t-5000);charging->SlotFrame(SlotIcon.BOLT);music->SlotFrame(SlotIcon.MEDIA);else->SlotFrame(SlotIcon.RECORD)}
            val effect=EffectFrame(checkMs=check,chargeMs=if(charging)(t-12000).takeIf {it<3000}?:-1 else -1,
                slot=slot,managedSlots=true,charging=charging,motionMs=t,headphoneBattery=65,
                musicPlaying=music,musicProgress=if(music).18f+(t-16000)/8000f else -1f,
                albumColor=if(music)color("#8CC6FF")else 0,drawIcons=true,iconPercent=100,recordElapsedMs=t-20000)
            val visual=DuoMapping.visual(72,charging,false,true,3,4,false)
            for(v in listOf(small,zoom)){v.render(visual);v.effects=effect}
            val bitmap=Bitmap.createBitmap(960,1280,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap)
            c.drawColor(color("#0B101B"))
            rect(c,32f,26f,604f,1242f,"#394154",62f);rect(c,38f,32f,598f,1236f,"#030407",60f)
            val save=c.save();c.clipPath(Path().apply {addRoundRect(RectF(51f,46f,585f,1221f),48f,48f,Path.Direction.CW)})
            paint.shader=LinearGradient(51f,46f,585f,1221f,intArrayOf(color("#253250"),color("#585584"),color("#1C777C")),null,Shader.TileMode.CLAMP)
            c.drawRect(51f,46f,585f,1221f,paint);paint.shader=null
            text(c,"17:52",84f,117f,29f,bold=true);paint.color=color("#06070D");c.drawCircle(316f,92f,12f,paint)
            ring(c,small,510f,76f,62)
            if(mode=="home") {
                text(c,"Terça-feira, 6 de outubro",91f,250f,24f,fg="#DDE5F8")
                text(c,"Boa tarde",91f,307f,49f,bold=true)
                rect(c,83f,357f,553f,518f,"#40FFFFFF",28f)
                text(c,"Seu dia, em equilíbrio",111f,410f,27f,bold=true)
                text(c,"Anel no lugar dos indicadores",111f,454f,22f,fg="#D4E8F4")
                text(c,"Exemplo de tela inicial",111f,491f,20f,fg="#D4E8F4")
                val names=listOf("Fotos","Música","Câmera","Ajustes","Mapas","Notas","Agenda","Duo")
                for(i in names.indices){val x=102f+(i%4)*119;val y=590f+(i/4)*164
                    rect(c,x,y,x+80,y+80,listOf("#657DD6","#D6699A","#4B5E76","#758694")[i%4],23f)
                    text(c,listOf("✦","♫","◎","⚙")[i%4],x+24,y+55,37f,bold=true)
                    text(c,names[i],x+5,y+115,21f)
                }
                rect(c,84f,1056f,552f,1151f,"#405C86A6",33f)
                text(c,"Telefone     Mensagens     Navegador",108f,1115f,21f)
            } else {
                text(c,if(mode=="notifications")"Notificações"else"Ajustes rápidos",87f,247f,37f,bold=true)
                text(c,"TERÇA-FEIRA, 6 DE OUTUBRO",88f,293f,20f,fg="#DDE5F8")
                if(mode=="notifications") {
                    rect(c,83f,344f,553f,503f,"#CA233047")
                    text(c,"Notificação de exemplo",110f,396f,27f,bold=true)
                    text(c,"Conteúdo ilustrativo, sem dados pessoais.",110f,443f,20f)
                } else {
                    for(i in 0..5){val x=84f+(i%2)*242;val y=347f+(i/2)*131
                        rect(c,x,y,x+226,y+112,if(i==0)"#5A83C1"else"#B22D3C54")
                        text(c,listOf("Wi-Fi","Bluetooth","Lanterna","Avião","Localização","Não perturbe")[i],x+19,y+62,24f,bold=true)
                    }
                    rect(c,83f,780f,553f,865f,"#CA233047");text(c,"Brilho  ━━━━━━━━━",110f,832f,24f)
                }
            }
            rect(c,235f,1190f,401f,1196f,"#D5DCEA",3f);c.restoreToCount(save)
            text(c,"DUO Recreate",641f,87f,34f,bold=true)
            text(c,"Canvas nativo",641f,128f,24f,fg="#8ED8F2")
            val title=when {mode=="notifications"->"Notificações";mode=="quick"->"Ajustes rápidos";check>=0->"Desbloqueio";charging->"Carregamento";music->"Anel musical";t>=20000->"Gravação";else->"Carrossel"}
            text(c,title,641f,205f,30f,bold=true)
            ring(c,zoom,662f,257f,238)
            text(c,"Detalhe ampliado",649f,633f,23f,fg="#A7B8D0")
            val lines=when {
                mode!="home"->listOf("Tamanho próprio", "para cada painel.", "Cabeçalho ilustrativo;", "integração OEM exige", "teste no aparelho.")
                check>=0->listOf("Check exclusivo", "durante 3 segundos.","Sai com fade out;", "Wi-Fi volta com fade in.")
                charging->listOf("Raio + arcos verdes", "com brilho em movimento.", "Efeito inicial de carga.")
                music->listOf("Ondas de mídia", "e progresso no anel.", "Cor de capa simulada.")
                t>=20000->listOf("Ponto vermelho", "com pulso de gravação.")
                else->listOf("Wi-Fi • Bluetooth • NFC", "Fones • Wi-Fi offline", "Um ícone por vez:", "saída antes da entrada.")
            }
            for((i,line)in lines.withIndex())text(c,line,641f,727f+i*42,22f,fg="#D3DDEA")
            rect(c,631f,1030f,935f,1195f,"#172233")
            text(c,"DEMONSTRAÇÃO",650f,1075f,24f,fg="#8ED8F2",bold=true)
            text(c,"Android 15 · API 35",650f,1118f,22f)
            text(c,"Não é captura do Samsung.",650f,1158f,20f,fg="#B9C8DC")
            return bitmap
        }
        try {
            for(i in 0..219){val b=scene(i*100L);File(output,"frame-${i.toString().padStart(3,'0')}.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
            for((name,t,mode)in listOf(Triple("dispositivo",5200L,"home"),Triple("desbloqueio",1900L,"home"),Triple("carga",13400L,"home"),Triple("notificacoes",5200L,"notifications"),Triple("ajustes-rapidos",5200L,"quick"))){val b=scene(t,mode);File(output,"$name.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
            assertEquals(220,output.listFiles()!!.count {it.name.startsWith("frame-")})
        } finally {small.teardown();zoom.teardown();Fx.sync(ModuleSettings.DEFAULT,ctx)}
    }
}
