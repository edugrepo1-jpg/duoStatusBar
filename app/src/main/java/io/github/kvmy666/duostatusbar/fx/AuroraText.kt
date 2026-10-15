package io.github.kvmy666.duostatusbar.fx

import io.github.kvmy666.duostatusbar.i18n.UiText

/** Compact trilingual catalogue for the additive Aurora features. */
internal object AuroraText {
    fun t(pt:String,en:String,es:String)=when(UiText.language){"en"->en;"es"->es;else->pt}
    private val labels=mapOf(
        "unlockGlow" to arrayOf("Brilho ao desbloquear","Unlock glow","Brillo al desbloquear"),
        "unlockBuild" to arrayOf("Construção do anel","Ring drawing","Dibujo del anillo"),
        "privacyPair" to arrayOf("Privacidade na chamada de vídeo","Video call privacy","Privacidad en videollamadas"),
        "privacyOrigins" to arrayOf("Origem da câmera e microfone","Camera and microphone source","Origen de cámara y micrófono"),
        "headphoneConfirm" to arrayOf("Confirmação dos fones","Headphone confirmation","Confirmación de auriculares"),
        "shutter" to arrayOf("Obturador expressivo","Expressive shutter","Obturador expresivo"),
        "singleNotices" to arrayOf("Confirmações sem fila","Coalesced confirmations","Confirmaciones sin cola"),
        "batterySource" to arrayOf("Transição da fonte de bateria","Battery source transition","Transición de fuente de batería"),
        "chargeStyles" to arrayOf("Cabo e indução","Wired and wireless effects","Efectos de cable e inducción"),
        "touchResponse" to arrayOf("Resposta ao toque","Touch feedback","Respuesta al toque"),
        "interruptibleIsland" to arrayOf("Movimentos interrompíveis","Interruptible motion","Movimiento interrumpible"),
        "adaptiveContrast" to arrayOf("Contraste adaptável","Adaptive contrast","Contraste adaptable"),
        "safeGeometry" to arrayOf("Proteção de enquadramento","Safe icon bounds","Protección del encuadre"),
        "fingerExpansion" to arrayOf("Expansão pelo gesto","Expansion follows your finger","Expansión siguiendo el dedo"),
        "threeLevels" to arrayOf("Três níveis da ilha","Three island levels","Tres niveles de la isla"),
        "landscapeLayout" to arrayOf("Ilha na horizontal","Landscape island","Isla en horizontal"),
        "contextActions" to arrayOf("Controles por atividade","Activity controls","Controles por actividad"),
        "openSource" to arrayOf("Abrir aplicativo de origem","Open source app","Abrir aplicación de origen"),
        "timer" to arrayOf("Cronômetro e temporizador","Stopwatch and timer","Cronómetro y temporizador"),
        "voiceCall" to arrayOf("Tempo da chamada","Call duration","Duración de llamada"),
        "taskComplete" to arrayOf("Tarefa concluída","Task completed","Tarea completada"),
        "timeline" to arrayOf("Prévia com linha do tempo","Preview timeline","Vista previa con línea de tiempo")
    )
    private val descriptions=mapOf(
        "unlockGlow" to arrayOf("Uma iluminação verde breve confirma o desbloqueio. Requer Check e animações ativados.","A brief green glow confirms unlocking. Requires Check and animations.","Un brillo verde breve confirma el desbloqueo. Requiere Check y animaciones."),
        "unlockBuild" to arrayOf("Desenha o arco, revela a porcentagem e faz os pontos entrarem em sequência.","Draws the arc, reveals the percentage, then introduces the signal dots.","Dibuja el arco, muestra el porcentaje y añade los puntos en secuencia."),
        "privacyPair" to arrayOf("Durante uma chamada com câmera e microfone em uso, alterna apenas esses dois ícones. Ao terminar, o carrossel retorna.","During a call using camera and microphone, alternates only these two icons. The carousel returns afterwards.","Durante una llamada con cámara y micrófono en uso, alterna solo estos dos iconos. Después vuelve el carrusel."),
        "privacyOrigins" to arrayOf("Na ilha, identifica o app que usa o sensor quando o Android informa a origem. Não lê áudio nem imagens.","In the island, identifies the app using the sensor when Android provides its source. Reads no audio or images.","En la isla, identifica la app que usa el sensor cuando Android informa el origen. No lee audio ni imágenes."),
        "headphoneConfirm" to arrayOf("Ao conectar os fones, desenha o ícone e ilumina o anel uma vez.","On connection, draws the headphones and lights the ring once.","Al conectar, dibuja los auriculares e ilumina el anillo una vez."),
        "shutter" to arrayOf("Abre e fecha os traços do obturador após uma captura detectada. Requer Captura de tela ativada.","Opens and closes shutter blades after a detected screenshot. Requires Screenshot enabled.","Abre y cierra el obturador tras una captura detectada. Requiere Captura activada."),
        "singleNotices" to arrayOf("Agrupa confirmações próximas. Não guarda animações antigas para mostrar depois.","Coalesces nearby confirmations. Does not queue old animations for later.","Agrupa confirmaciones cercanas. No guarda animaciones antiguas para después."),
        "batterySource" to arrayOf("Identifica com uma transição a troca entre celular e fones, sem inventar porcentagens intermediárias.","Highlights the switch between phone and headphones without inventing intermediate percentages.","Destaca el cambio entre teléfono y auriculares sin inventar porcentajes intermedios."),
        "chargeStyles" to arrayOf("No cabo, a energia percorre o arco. Na indução, ondas convergem para o centro. Requer efeito de carga.","On cable, energy travels along the arc. Wireless waves converge inward. Requires charging effects.","Con cable, la energía recorre el arco. Con inducción, las ondas convergen al centro. Requiere efecto de carga."),
        "touchResponse" to arrayOf("O anel comprime suavemente enquanto você pressiona e retorna ao soltar.","The ring gently compresses while pressed and returns on release.","El anillo se comprime suavemente al pulsar y vuelve al soltar."),
        "interruptibleIsland" to arrayOf("Um gesto novo interrompe o movimento anterior, inclusive durante a abertura.","A new gesture interrupts the previous movement, including opening.","Un gesto nuevo interrumpe el movimiento anterior, incluso al abrir."),
        "adaptiveContrast" to arrayOf("Um contorno oposto à cor dos ícones melhora a leitura sem capturar seu papel de parede.","A contrasting outline improves legibility without capturing your wallpaper.","Un contorno de contraste mejora la lectura sin capturar tu fondo."),
        "safeGeometry" to arrayOf("Limita ícones e efeitos ao espaço livre quando o anel ou o traço ficam maiores.","Keeps icons and effects inside available space as the ring or stroke grows.","Limita iconos y efectos al espacio disponible cuando crece el anillo o el trazo."),
        "fingerExpansion" to arrayOf("Arrastar o cabeçalho faz a ilha acompanhar seu dedo. Soltar encaixa no nível mais próximo.","Dragging the header makes the island follow your finger. Release snaps to the nearest level.","Arrastrar la cabecera hace que la isla siga el dedo. Al soltar se ajusta al nivel más cercano."),
        "threeLevels" to arrayOf("Compacta, controles rápidos e detalhes. Arraste o cabeçalho para mudar de nível.","Compact, quick controls and details. Drag the header to change levels.","Compacta, controles rápidos y detalles. Arrastra la cabecera para cambiar de nivel."),
        "landscapeLayout" to arrayOf("Na horizontal, usa uma ilha mais larga e baixa com controles ao lado das informações.","In landscape, uses a wider, shorter island with controls beside information.","En horizontal, usa una isla más ancha y baja con controles junto a la información."),
        "contextActions" to arrayOf("Mostra os controles da atividade atual. Música oferece anterior, reproduzir/pausar e próxima; relógios têm até dois controles. Botões de mídia só aparecem com uma sessão.","Shows controls for the current activity. Music offers previous, play/pause and next; clocks have up to two controls. Media buttons appear only with a session.","Muestra los controles de la actividad actual. La música ofrece anterior, reproducir/pausar y siguiente; los relojes tienen hasta dos controles. Los botones multimedia solo aparecen con una sesión."),
        "openSource" to arrayOf("Toque no cartão de um estado para abrir seu app, se houver uma atividade disponível. Nunca abre outro app como substituto.","Tap a state card to open its app when an activity is available. Never substitutes another app.","Toca una tarjeta para abrir su app si hay una actividad disponible. Nunca abre otra app como sustituto."),
        "timer" to arrayOf("Inicie relógios visuais do DUO abaixo. Pause e ajuste pela ilha. Não substituem um alarme; reiniciar o SystemUI encerra a sessão.","Follows your phone�s Clock through notification access. Shows only counts provided by compatible apps.","Inicia relojes visuales DUO abajo. Pausa y ajusta en la isla. No sustituyen alarmas; reiniciar SystemUI termina la sesión."),
        "voiceCall" to arrayOf("Exibe o tempo desde que o Android informou modo de chamada. Pode incluir comunicação por apps; não lê contato nem número.","Shows time since Android reported call mode. Can include app calls; reads no contact or phone number.","Muestra el tiempo desde que Android informó modo de llamada. Puede incluir llamadas de apps; no lee contactos ni números."),
        "taskComplete" to arrayOf("Confirma downloads ou tarefas que publicam conclusão com progresso completo. Exige acesso às notificações; remover uma notificação não conta como conclusão.","Confirms downloads or tasks publishing completed progress. Requires notification access; dismissing a notification is not completion.","Confirma descargas o tareas con progreso completo publicado. Requiere acceso a notificaciones; descartarlas no significa finalizar."),
        "timeline" to arrayOf("Na prévia, avance e volte pelo desbloqueio e pela troca de ícones para inspecionar os efeitos.","In preview, scrub unlocking and icon transitions to inspect effects.","En la vista previa, avanza y retrocede por el desbloqueo y las transiciones para ver los efectos.")
    )
    fun title(key:String)=labels[key]?.let {t(it[0],it[1],it[2])} ?: key
    fun description(key:String)=descriptions[key]?.let {t(it[0],it[1],it[2])} ?: ""
}
