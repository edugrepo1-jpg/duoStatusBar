# Auditoria de runtime e bateria — três lentes

Este relatório separa cadência de execução, comunicação de mídia e ciclo de vida/sensores. São perspectivas de revisão, não três agentes adicionais. As mudanças preservam música, cores da capa, gravação, captura de tela, bússola, animações e acesso local às funções.

## Lente 1: cadência e trabalho realmente executado

### NORMAL — callbacks iguais podiam adiar o heartbeat indefinidamente

Por quê: o relay removia e reagendava o heartbeat para 30 segundos depois de todo callback, inclusive quando nenhum novo snapshot era transmitido. O destinatário considera o relay vencido em 35 segundos. Uma sequência de callbacks de metadados idênticos a cada 10 segundos poderia adiar o heartbeat, enquanto a fonte real continuava tocando.

Evidência reproduzível: sequência envio em t=0, callbacks iguais em t=10/20/30 s, deadline antigo indo a t=40/50/60 s; recebedor expira em t=35 s. Não depende de dados pessoais ou do aparelho.

Correção: ExperienceRelayService ancora heartbeat em sentAt do último envio real; callbacks não alteram esse instante. Prazo restante reduz até zero e força publicação. Reprodução pausada, feature desativada, serviço desconectado e tela desligada continuam sem heartbeat constante.

Teste: RuntimeAuditTest verifica prazos de 30, 20, 1 e 0 segundos com callbacks intermediários. BatteryAuditTest mantém os cenários de pausa/offscreen/disconnect.

Limite: o Android pode atrasar handlers sob pressão. O buffer de 5 segundos de validade não constitui SLA de tempo real. Não foi medida economia em mAh.

### LEVE — consultas desnecessárias do estado/metadados nas notificações de posição

Por quê: antes, cada sessão era lida para achar a sessão tocando e a sessão escolhida era lida novamente para construir o snapshot; metadados com bitmap podiam ser reparcelados a cada atualização de reprodução. PlaybackState de alguns players é atualizado frequentemente.

Correção: RuntimeExperience e relay leem o estado uma vez por controller por rodada. Metadados e cor ficam em cache e mudam no callback de metadados, troca de token ou descoberta/heartbeat de segurança a cada 30 segundos. A posição continua vindo do PlaybackState, e o desenho extrapola pelo relógio local. Pausa continua autoritativa.

Teste: RuntimeAuditTest passa de Track A para Track B por callback e confirma que pausar mantém título/duração, mas remove playing e atualiza posição. ExperienceTest já cobre cálculo de progresso e cor.

Limite: registros e algumas leituras MediaSessionManager/MediaController ainda são APIs Binder síncronas, agora eventuais, sem loop de render. Não houve trace que reproduzisse demora individual do serviço de mídia; esta revisão não afirma que toda chamada Binder de uma ROM é livre de bloqueio. Mover tudo para executor sem contrato de seleção e ordem exigiria outra mudança maior e validação no aparelho.

## Lente 2: coerência do player e comunicação Binder

### NORMAL — callbacks atrasados reanexavam sessões depois de desativar mídia

Por quê: o listener de lista testava apenas running. Desativar as funções de mídia mantinha o runtime rodando para outros recursos; uma lista já enfileirada pelo Android depois de removeOnActiveSessionsChangedListener podia reanexar callbacks de players e reter controllers, mesmo com todas as funções de mídia desligadas.

Correção: flag mediaEnabled explícita, atualizada antes da limpeza. Listener atrasado e relay musical são ignorados quando a função não precisa de mídia. Desativar limpa dados diretos/relayed e estado conhecido. Reativar reinicia a descoberta imediatamente e permite uma nova tentativa de permissão, em vez de aguardar o resto da janela de 30 segundos.

Teste de regressão: RuntimeAuditTest inicia runtime sem mídia, entrega listener atrasado com controller real e verifica que nenhum controller fica registrado e playbackKnown continua falso. O código anterior falha nessa condição.

### NORMAL — controles da ilha podiam agir no player errado

Por quê: estado exibido no painel e seleção do controller na ação eram feitas separadamente. Com duas sessões, tocar pausa poderia encontrar outro player.

Correção coordenada: PlaybackSnapshot carrega sourcePackage apenas em memória/IPC local protegido; snapshot direto e relay preenchem a origem; entrada relay valida tamanho e caracteres. A ilha usa a origem para selecionar controller exato e falha silenciosamente se a sessão original desapareceu. Sem origem, conserva o comportamento compatível de priorizar quem está tocando.

Teste: RuntimeAuditTest rejeita origem malformada, excessiva e vazia; IslandCompactTest verifica geometria, eventos e acessibilidade, e a integração usa o sourcePackage na seleção do controller. Nenhuma origem, título ou capa é adicionada aos logs.

Migração: ACTION EXPERIENCE e REFRESH passaram a io.github.RECREATE.statusbar. Namespace Kotlin/imports e classe NotificationListenerService continuam iguais para compatibilidade do código; o applicationId/manifest é responsabilidade da integração.

## Lente 3: ciclo de vida, sensores e observadores

### NORMAL — recuperação do provedor de imagens deixava baseline negativo

Por quê: query inicial pode retornar null temporariamente. Se a primeira query bem-sucedida posterior for vazia, o código anterior preservava lastId=-1. Isso fazia o primeiro screenshot real falhar na condição lastId>=0, embora o observador já estivesse registrado.

Correção: toda primeira resposta bem-sucedida estabelece baseline, com ID mínimo zero, mesmo fora da query inicial. Não dispara fotos antigas como screenshot. Proteção existente por geração permanece contra resultados após stop/restart.

Teste de regressão: provider retorna null, depois cursor vazio, depois um screenshot novo. RuntimeAuditTest exige uma detecção na última etapa; código anterior falha. A consulta só recebe nomes/caminho/data, sem abrir/copiar/enviar a imagem.

### NORMAL — falha de sensor repetida em cada frame (correção coordenada por telemetria)

Evidência: HeadingObserver.start sai cedo apenas quando running=true. Sem sensor, running permanece false. Com LOCATION exibido e frames de 33 ms, getDefaultSensor/registerListener eram tentados novamente ~30 vezes/s; o flag failed silenciava apenas o log.

Correção solicitada ao proprietário de telemetria/HeadingObserver: cooldown de 30 segundos após tentativa frustrada, mantido mesmo ao alternar ícone; retry somente enquanto LOCATION visível. Sensor válido conserva o limitador de 10 Hz, preferência por vetor geomagnético e stop quando oculto.

Teste/estado: TelemetryAuditTest executa o burst e o limite de retry; o resultado final está em VALIDACAO.md. Nenhum consumo medido pode ser atribuído automaticamente a essa hipótese; o excesso de tentativas é demonstrado pelo fluxo do código.

### LEVE — fatores revisados sem novo defeito reproduzido

- Registrations de volume são idempotentes e removidos ao stop; valores duplicados não consultam novamente limite de volume.
- RuntimeIndicators aplica snapshot assíncrono por geração e não deixa resultado tardio reativar um ciclo encerrado; observadores de AppOps e lanterna mantêm estado por conjunto.
- Heading recebe amostras no máximo a 10 Hz e não desenha quando ícone está oculto; falha de registro é o item separado acima.
- ScreenshotObserver fecha cursor e thread, observa oito nomes com debounce e não acessa bytes das imagens.
- Não foi introduzido wakelock, captura de tela/áudio, root, rede remota nem timer de render na tela desligada.

## Classificação e limites gerais

Nenhum novo defeito CRÍTICO com evidência de crash, reinício do SystemUI, vazamento de segredo ou operação irreversível foi reproduzido nesta revisão. Os itens NORMAL afetam coerência, resposta e trabalho redundante. LEVE cobre custo marginal ou risco ainda não reproduzido. Isso não equivale a prova de ausência de outros bugs.

Contadores medem chamadas, amostras e frames realmente feitos. Intervalos de callback e tempo de execução são indicadores de trabalho, não percentuais de energia, culpa de outro aplicativo ou consumo do kernel. Comparar bateria requer mesma luminosidade, rádio, duração e carga; validar com trace/batterystats/perfil de energia no telefone.

Testes novos desta frente: cinco RuntimeAuditTest. A execução integrada e sua contagem estão em VALIDACAO.md; o relatório separa evidência automatizada de observação física.
