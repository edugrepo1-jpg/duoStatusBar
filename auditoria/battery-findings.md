# Auditoria 3 — consumo, sensores, processos e ciclo de vida

Escopo: RuntimeIndicators, HeadingObserver, ExperienceRelayService, VolumeObserver; revisão de ScreenshotObserver, EffectsController, RuntimeExperience e GlassBackdrop sem editar arquivos de outros responsáveis. Base de evidência: fonte da versão entregue 1.4.2 Canvas Studio, relatório 098-duo-diagnostics-20261006-165731.txt e relatório 092 Canvas Experience. Os outros documentos do ZIP têm versões anteriores e não demonstram regressões do APK atual. Não há captura de batterystats, trace de CPU/GPU ou medição de mAh; não é possível atribuir percentuais de economia medidos.

## A3-01 — reprodução redundante por polling permanente (corrigido)

Por quê: ExperienceRelayService consultava MediaSessionManager, metadados, amostragem da capa e enviava broadcast a cada 1.000 ms, enquanto a música estava pausada e com a tela desligada. PlaybackSnapshot já extrapola progresso a partir do relógio; cada frame não precisa de nova leitura remota.

Evidência: tick original postDelayed(1000) sem condição screen/playing. O loop ficava ligado se a preferência music estivesse marcada, independente de playback.

Correção: listener de sessões + callback de reprodução/metadados. Pausa/parada transmitem imediatamente. Heartbeat de segurança de 30 segundos só durante reprodução com tela ligada, ou em ROM sem callback de sessões. Limpeza de callbacks, listener, receiver e heartbeat no desligamento do serviço. Captura de tela continua opcional e preservada. Monitoramento cobre também ilha/ícone de mídia padrão, sem depender exclusivamente do anel musical opcional.

Teste: BatteryAuditTest verifica política com pausa, screen-off, disable, disconnect e fallback; verifica reconnect/disconnect sem multiplicar receivers. RuntimeExperience terá TTL 35 segundos (integração com auditor 2).

Risco/limite: ROM sem permissão de sessão continua falhando silenciosamente, sem inventar estado; validar acesso de notificações e retorno após reinício no telefone. Testes locais não medem mAh.

## A3-02 — inventário Binder dentro do caminho de desenho (corrigido)

Por quê: RuntimeIndicators.refresh percorria localização, Bluetooth, NFC, hotspot, alarme, DND, volume, todas as redes, dispositivos pareados, bateria dos fones e projeção na thread principal a cada dois segundos. Uma API lenta impede temporariamente desenho, toque e progressos.

Evidência: indicatorsTick original chama refresh antes de updateCycle/draw; essas leituras são síncronas. Em 098, o ícone de localização está ativo, mas não há ANR formal registrado; o custo e a possibilidade de bloqueio são demonstrados pelo código, não uma duração de travamento medida no log.

Correção: uma única thread DuoIndicators executa inventário, com solicitações coalescidas e cache de 10 segundos. Eventos de conexão/estado podem forçar atualização. Snapshot aplicado na thread da UI apenas quando mudou; geração de ciclo de vida rejeita respostas tardias após stop/restart. Captura de tela usa uma sondagem separada, pequena, a cada 2 segundos de tela visível, sem repetir o restante do inventário. Sensores, AppOps e lanterna continuam por callbacks e nunca consultam coordenadas ou abrem câmera/microfone.

Teste: BatteryAuditTest verifica coalescing, bypass por evento, intervalo e reset. RuntimeIndicatorsTest existente conserva semântica de alternância de localização em uso isolado. Integração controller passa force=true nos eventos e copia Bluetooth/NFC/hotspot no callback de diff.

Risco/limite: projeção pode ter atraso de até dois segundos porque a API acessível já usada pelo módulo é uma sondagem; início de gravador de áudio depende de AppOps (permissão do SystemUI). Estado de alarme/VPN, na ausência de broadcast OEM, pode aguardar cache de 10 segundos. Uma chamada Binder já em execução não pode ser cancelada, mas seu resultado depois de stop é descartado.

## A3-03 — bússola gastando CPU quando o GPS não está desenhado (corrigido em conjunto)

Por quê: GPS habilitado colocava LOCATION na lista e ligava o sensor permanentemente, mesmo quando ALARM, MEDIA, Wi-Fi ou check ocupavam o centro. SENSOR_DELAY_UI pedia ~16,7 leituras/segundo e os callbacks consultavam rotação da tela a cada leitura.

Evidência: relatório 098 mostra lista ALARM, LOCATION, VIBRATE com ícone exibido ALARM. updateCycle original registrava heading sempre que LOCATION estivesse em items. O gasto é confirmado pelo caminho de registro, não por medição energética do relatório.

Correção própria: preferência por TYPE_GEOMAGNETIC_ROTATION_VECTOR (dispensa giroscópio), com fallback ao vetor de rotação disponível; período solicitado 100 ms e limitador explícito de 10 leituras/s; atualização apenas com movimento >0,5 grau; normalização de azimute e preservação do caminho mais curto no norte. Correção controller pelo auditor 2: registro somente se LOCATION realmente ocupa o centro, sem check, tela apagada ou bolso.

Teste: BatteryAuditTest verifica limite em burst de 1.000 eventos simulados e virada 359→1. ExperienceTest existente mantém os quatro casos de menor arco.

Risco/limite: qualidade de norte magnético depende do sensor e calibração OEM; validar giro físico. A suavização preserva movimento, mas com frequência inferior à antiga.

## A3-04 — sinalizadores de privacidade antigos após stop (corrigido)

Por quê: stop removia operações e torches dos conjuntos, mas não zerava camera, microphone, locationInUse e torch. Reanexação poderia manter ícone ativo até surgir novo callback.

Correção: zerar os sinais transitórios e impedir callbacks de lanterna indisponível quando parado. Atualização AppOps/lanterna só notifica ao alterar o estado visual agregado; estados de dois aplicativos iguais não provocam frames redundantes. Snapshot assíncrono tardio é rejeitado por geração.

Risco/limite: read inicial de AppOps é uma API de sistema já existente, dependente da autorização da ROM; falha preserva barra original e é logada uma vez.

## A3-05 — consultas duplicadas de limite de volume (corrigido)

Por quê: receiver de volume e ContentObserver podem informar o mesmo evento. O código consultava getStreamMaxVolume mesmo quando o volume atual não mudou; o observer pode varrer cinco streams.

Correção: consultar limite apenas para valor efetivamente alterado; callbacks não geram notificações na inicialização nem repetem valores. Registro/desregistro continuam pareados e idempotentes.

Teste: BatteryAuditTest confirma que broadcasts depois de stop não disparam ações. ExperienceTest existente verifica alteração real sem duplicação.

## A3-06 — 60 fps contínuos de carga, mídia e gravação (delegado e corrigido pelo auditor 2)

Por quê: controller original usava 16 ms sempre que charging, selected MEDIA/RECORD/RECORD_TIME ou hotspot estivessem ativos, inclusive depois do brilho inicial de carga. Texto de tempo precisa de 1 Hz, não 60 Hz; shapes visíveis podem usar 30 fps e progresso um relógio local.

Correção coordenada: 33 ms apenas para shapes em movimento e transições visíveis; timers/progresso 1 segundo; sem callbacks de 16 ms para transientes expirados e estados estáticos; o arco ambiental continua animado a cada 33 ms durante carga visível; screen-off/bolso cancela trabalho. Animações e eventos continuam presentes.

## A3-07 — capa recolorida/reprodução reread a cada refresco (delegado ao auditor 2)

Por quê: RuntimeExperience.snapshot era reconstruído com releitura de 64 pixels a cada polling e state ausente criava sampledAt novo, gerando diff falso. Isso causava frames que não correspondem a mudança real.

Correção coordenada: cache de cor por identidade/generation da imagem, snapshot de estado estável e callbacks reais; relay utiliza snapshot compartilhado. Nenhum título ou imagem é incluído nos logs.

## A3-08 — painel musical sem relógio local (delegado ao auditor 1)

Por quê: IslandSummary atualiza quando state chega; entre callbacks, progress e shapes ficavam congelados. Atualizar a fonte a 60 fps gastaria bateria e não resolveria causa.

Correção coordenada: painel agenda invalidação de 100 ms somente quando playback real ou gravação está ativa, anexado/visível e expansão terminada; pausa/dispose/tela oculta retiram callback. Progresso extrapolado pelo relógio do PlaybackSnapshot.

## Itens inspecionados sem evidência suficiente para mudança

- ScreenshotObserver: consulta de nomes/caminhos em thread própria; debounce 350 ms, oito registros, nomes nunca logados, observer removido e thread quitSafely. Sinalizei ao proprietário RuntimeExperience que callback de query após stop/restart precisa geração para evitar disparo antigo; não editei arquivo compartilhado.
- GlassBackdrop: não captura tela/bitmaps nem cria loop próprio. BackgroundBlurDrawable criado uma vez, removido por release. O sistema pode desativar blur; o fallback translúcido mantém legibilidade. Custo GPU de blur depende do aparelho e não foi medido.
- CanvasMotion: matemática pura, sem scheduler, registro ou alocações de Bitmap. Não constitui loop de energia independente.
- Processos/root: minhas correções não iniciam shell, rede remota, wakelock ou serviço de primeiro plano. O relay usa apenas o serviço de acesso a notificações escolhido pelo usuário.

## Validação pendente ao integrar

Executar BatteryAuditTest + testes existentes ligados às funções + regressões de foreground/timing/island. Em telefone, comparar antes/depois com mesma luminosidade e carga de trabalho: Wi-Fi ocioso, música reproduzindo/pausada, gravação, carga, tela desligada, GPS oculto/visível. Não declarar economia de bateria medida sem batterystats/perfil de energia e janela comparável.
