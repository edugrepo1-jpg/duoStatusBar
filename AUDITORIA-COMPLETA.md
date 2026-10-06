# Duo Status Bar — auditoria completa e correções

Entrega: 1.4.2-canvas-audit, código 27. APK SHA-256 `19B037E1AA788ECFD0BCC9F5AECEA64C64B235E74DE1F6036712C488FCF9189E`.

Revisão feita em cinco frentes: interface/integração; ilha/ícones/gestos; prioridades/tempo/mídia; consumo/sensores/ciclo de vida; e revisão adversarial de contexto, relatórios e processos. Para cada achado, foram registrados causa, evidência, correção e verificação, com limites separados. Referências compartilhadas não devem ser somadas como defeitos diferentes.

## Resultado confirmado
Compilação concluída. **284 testes passaram**, sem falhas, erros ou ignorados. **352 classes e 3155 métodos** passaram na verificação de bytecode. Assinatura v3, alinhamento 16 KB, versão e ausência de Rive conferidos. Os desenhos e as telas foram renderizados e inspecionados localmente; isso não substitui execução no aparelho.

## Leitura do ZIP de logs
Foram lidos **104 relatórios, 95 únicos por SHA-256**, total 3321650 bytes. Apenas dois declaram edição Canvas: Experience e Studio. A maioria descreve instalações upstream antigas ou não fornece cabeçalho próprio suficiente para inferir a versão; versões de outros aplicativos não foram tomadas como versão do módulo.
Categorias por arquivo único: 42 contêm evidência de attached=true, 12 de attached=false, 44 de su ausente, 23 de captura root negada/omitida, 9 de campo opcional ausente, 20 de acesso restrito. Categorias podem coexistir; não são contagens de crashes do Duo.
As três ocorrências de fatal/crash em captura histórica pertencem a sensor-notifier, nuvem HeyTap ou notas remotas; não provam crash da edição atual. Na Studio, a ausência de appContext gerou ruído e foi corrigida; a barra estava anexada. Não havia trace de ANR musical ou medição de bateria. Não enviamos logs nem mensagens ao desenvolvedor e não executamos root real.

## Como ler
P1: estabilidade ou integridade; P2: comportamento/interação; P3: clareza/apresentação. 'Corrigido' é uma alteração e verificação concreta no escopo descrito. Capacidade de serviços da ROM e consumo físico permanecem sujeitos a teste no telefone. Os relatórios abaixo foram redigidos por cada frente antes da execução integrada; o resultado final desta seção prevalece sobre notas de teste ainda pendente.

# Auditoria de integração — interface, explicações, preferências, atualizações e logs

Pergunta de controle: por que um usuário entenderia a função, por que ela respeitaria o estado real e por que uma falha não bloquearia a barra? Revisão feita sobre a versão Studio entregue e os resultados dos outros quatro auditores. Gravidades: P1 afeta estabilidade/integridade, P2 afeta comportamento ou interação, P3 afeta clareza/apresentação. Os achados compartilhados entre auditores são referências cruzadas, não defeitos novos para inflar uma contagem.

| ID | Prioridade | Defeito / causa | Correção concreta | Evidência de verificação |
|---|---|---|---|---|
| UI-01 | P2 | Lista cheia de chaves muda comportamento no primeiro toque, sem explicar a função. | Linhas de ajustes abrem janela com O que faz, Como funciona, Quando usar, estado e botão Ativar/Desativar. Voltar não salva nada. | StudioUiTest abre, verifica zero commits, cancela e confirma ausência de mudança; fluxo do master confirma cada alteração. |
| UI-02 | P2 | Ajuste desabilitado não permitia descobrir por que nem entender a função. | Linha permanece consultável; botão de ativação fica desabilitado com explicação de dependência. | Inspeção de semântica e janela; não liberta recurso dependente por acidente. |
| UI-03 | P2 | Lista privada de efeitos traduzida uma única vez conservava idioma anterior após mudança. | Rótulos calculados a cada composição; guias resolvem tradução na hora. | StudioUiTest alterna idiomas depois de acessar guias; telas em inglês/espanhol; catálogo e placeholders iguais. |
| UI-04 | P3 | Títulos abstratos não orientavam onde configurar desenho, comportamento ou conexão. | Sua barra de status, Desenho do anel, Ícones e animações, Configurações; instrução de tocar para conhecer a função. | Renderizações claras/escuras das quatro páginas. |
| UI-05 | P3 | Paleta, títulos e espaçamento competiam com controles. | Azul iOS, fundos agrupados neutros, cantos suaves, hierarquia menor e status legível. Desenhos próprios; não são assets Apple. | Capturas nativas do código; qualidade visual não é prova de integração no telefone. |
| UI-06 | P2 | 'Tempo global' era derrotado silenciosamente por overrides individuais. | Tempo único para todos explícito. Guarda mapa individual; migra preferências antigas com mapa para modo individual. | ForegroundAuditTest universal ignora mapa; StudioBetaTest mapas continuam válidos. |
| UI-07 | P2 | Alterar permanência global numa orientação não afetava a outra. | Publica apenas dwell e flag universal nas duas orientações, preservando idioma, funções, geometria e mapa próprios. | PreferenceAuditTest usa 60 s, mapa NFC=47, música/ilha e tamanho 175%; confere preservação. |
| UI-08 | P2 | Tempo próprio oferecido para música/microfone/gravação/avisos breves não tinha efeito com prioridade nova. | Seletor individual restrito a estados secundários. Texto explica eventos persistentes, avisos breves e check fixo de 3 s. | Inspeção da lista elegível e testes de permanência do evento por vinte minutos. |
| UI-09 | P2 | Velocidade da expansão parecia regular carrossel/fade. | Rótulo Entrada do anel nas três línguas e descrição separando expansão, permanência e fades. | Recursos traduzidos e UI local. |
| UI-10 | P2 | Fades ajustáveis estavam escondidos dentro da prévia. | Saída e entrada também no editor principal, com unidades em ms e explicação do ajuste em intervalos curtos. | Configure/limites testados no ciclo; controles compartilhados usam as mesmas opções. |
| UI-11 | P2 | Prévia usava regras de prioridade diferentes da barra e loop de quadro contínuo mesmo estática/pausada. | Usa ForegroundEvents/SlotCycle/EffectCadence; filtros de pausa, relógio congelado sem trabalho e reprodução simulada para a ilha. | Testes puros de política mais renderização; simulação não envia comandos reais ao aparelho. |
| UI-12 | P2 | RECORD_TIME ficaria inacessível se RECORD fosse dono permanente. | A mesma gravação mantém a propriedade do centro e alterna ponto/cronômetro quando opção ligada; Wi-Fi não entra no intervalo. | PreferenceAuditTest percorre 30 segundos, saída antes da entrada, nunca expõe Wi-Fi e volta à rede após encerrar. |
| UI-13 | P1 | Bundle não nulo {ok:false} era aceito como confirmação, eliminando fallback. | Cliente verifica EXTRA_OK==true. | Contrato do provider e IntegrationAuditTest de autorização; caminho de rejeição retorna false. |
| UI-14 | P1 | Limitar dump só no provider não evitava TransactionTooLarge antes da chegada. | Envelope antes do IPC ≤128 mil caracteres, cabeçalho + eventos recentes, redigido e cortado em linhas inteiras. | PreferenceAuditTest relatório grande mantém device e evento mais recente, remove email/SSID, respeita limite. |
| UI-15 | P2 | Toques repetidos agendavam downloads paralelos no mesmo arquivo. | Trabalho único KEEP e arquivo parcial por worker; commit final somente após verificar hash. | Revisão do contrato de WorkManager e nomes. Nenhum download externo executado na auditoria. |
| UI-16 | P1 | SHA vazio permitia notificação 'verificado' sem verificar. Versão sem validação podia entrar em caminho de arquivo. | SHA-256 de 64 hex obrigatório, versão limitada sem separadores; comparação sempre feita antes de renomear e oferecer instalador. | PreferenceAuditTest rejeita traversal, hash ausente/curto; revisão da comparação/commit. |
| UI-17 | P2 | Cancelamento caía no catch genérico/retry e podia conservar arquivo parcial; progresso era escrito a cada bloco. | Cancelamento propagado e parcial removido; interrupção consultada no loop; progresso só muda quando percentual muda. | Revisão dos caminhos sucesso/falha/cancelamento; arquivo previamente verificado só é substituído depois da nova verificação. |
| UI-18 | P2 | Feed oficial oferecia APK com outra assinatura e sem nossas funções. | Esta edição explica diferença de canal, mostra lançamento oficial, não oferece download direto incompatível. Checagem de atualizações preservada. | PreferenceAuditTest incompatibilidade de canais; barreira no worker, enqueue, notificação e tela. |
| LOG-01 | P2 | Relatórios repetidos poderiam inflar frequência dos defeitos. | Inventário SHA-256: 104 arquivos, 95 únicos. Análise de conteúdo completa e categorias por arquivo único. | inventory.json/log-audit.json locais, sem incluir logs crus no ZIP de fontes. |
| LOG-02 | P2 | Fatal/ANR em captura global antiga poderia ser atribuído ao Duo sem evidência. | Triagem identifica sensor-notifier, HeyTap cloud e processo remoto de notas em relatos históricos; não são stack traces do Duo atual. | Arquivos de 28/09, 01/10 e 02/10; relatório Canvas Studio não tem crash/ANR formal de música. |
| LOG-03 | P2 | Campo appContext ausente era tratado como erro mesmo quando fallback anexava a barra. | Campo opcional isolado e silencioso; pacote/UID correto continua validado. | IR-01–03 e IntegrationAuditTest; nenhuma invenção de campo/hook OEM. |

| LOG-04 | P1 | Regex de email sem limite de início fazia tentativas quadráticas em uma linha técnica de 512 mil caracteres sem @. Confirmado no teste e thread dump: centenas de segundos de CPU em Pattern/DiagnosticPrivacy. | Limite de token e quantificadores possessivos evitam retentativas internas. Redação continua removendo endereços, inclusive locais. | Mesmo teste de limite mantido, agora exige processamento menor que 2 s; o teste não foi removido para ocultar o defeito. |

| LOG-05 | P2 | Compartilhamento com contexto de aplicativo lançava AndroidRuntimeException por faltar NEW_TASK no chooser. | Flag aplicada somente fora de Activity, tanto para texto quanto arquivo; fluxo da tela conserva sua tarefa. | IntegrationAuditTest usa contexto de aplicativo e verifica limpeza do texto e abertura do chooser sem falhar. |
| UI-19 | P2 | Detalhe genérico Ativo podia ser desenhado como se fosse tipo de rede no glyph da ilha. | Painter aceita apenas tipos de rede conhecidos; dado desconhecido usa símbolo genérico sem inventar 5G. | Captura da ilha usa 4G real de fixture; tipos inválidos não são renderizados como texto. |

## Matriz de preservação e novos comportamentos

- Renderer Android Canvas exclusivo; nenhuma restauração de Rive. Anexação e observador de desbloqueio conservados. Check exclusivo 3 segundos, depois saída e retorno ao estado válido, sem Wi-Fi por trás.
- Os 20 indicadores anteriores e suas variantes permanecem: rede, avião, DND, Bluetooth, NFC, hotspot, fones, check, carga, câmera e microfone separados, alarme, VPN, GPS, silencioso/vibração, música, indução, lanterna, gravação, Wi-Fi sem internet. Exclamação vermelha central, porcentagem dos fones >15% verde/≤15% vermelho, escala interna até 200% e bússola continuam.
- As nove ideias aprovadas continuam: anel musical, previsão de carga, prévia, obturador de captura, editor de ritmo, volume, tempo de gravação, ilha ao segurar e desenho dos traços.
- Preferências e tradução PT/EN/ES preservadas. Versão 27 usa mesma assinatura das edições Canvas anteriores, sem empacotar chave ou logs pessoais no código entregue.
- Estado persistente mais recente (música, microfone, captura/gravação de tela) vence até parar. Microfone indica uso, inclusive chamadas, sem identificar aplicativo gravador. Múltiplos inícios no primeiro snapshot não têm hora observável; desempate determinístico, sem inventar ordem.
- Todos os estados continuam no resumo da ilha. O timer de gravação alterna dentro da propriedade da gravação. Check mantém exceção explícita de prioridade por 3 segundos.

## Limites e pontos ainda dependentes de dispositivo

1. Consumo físico não foi medido: nenhum batterystats ou trace CPU/GPU veio no ZIP. Otimizações de frequência/ciclo de vida são verificáveis, mas não há percentual de autonomia garantido. Carga ambiental visível segue animando a cada 33 ms; não é zero trabalho durante toda a carga.
2. Captura/projeção sondada pode atrasar até 2 s; inventário em cache até 10 s sem callback. Eventos conhecidos forçam leitura. Uma API Binder em execução pode demorar numa ROM e não é cancelável; seu resultado tardio é descartado após desligar.
3. Descoberta/metadados de MediaSession permanecem dependentes do serviço Android e callbacks/fallback de 30 s. Não houve ANR do player nos logs; se ainda travar em hardware, medir duração desse caminho, sem inferir uma causa universal.
4. Bridge legada de diagnósticos no Android 33 ainda não autentica remetente pelo contrato atual; provider valida UID. Não foi criada permissão que quebraria o fallback de One UI. Dados desse fallback são diagnósticos, não autorização de comandos.
5. Regex é defesa adicional para logs técnicos do módulo, não garantia de remover qualquer dado pessoal de uma descrição livre digitada pelo usuário. Coleta não inclui conteúdo de mídia, notificação, fotos, áudio ou coordenadas.
6. Não houve instalação no aparelho nem execução de root real na auditoria. Layouts, estados, ciclos, exportação, sensores simulados e gestos foram verificados localmente; mídia, câmera, orientação física e latência OEM precisam confirmação no telefone.
7. Testes passaram somente quando registrados na validação final. Os relatos individuais escritos antes da integração são evidência do trabalho de cada auditor, não execução independente do build.


---

# Auditoria causal — ilha dinâmica, desenho e interação

Escopo: `IslandSummary.kt`, `StatusIconPainter.kt`, novo `IslandAuditTest.kt`. Mantidos os efeitos do anel, desenhos separados de câmera/microfone, AirPods com porcentagem e transições existentes. Alterações de prioridade de eventos, estado real de gravação/mídia e agendamento do anel pertencem aos demais auditores.

## Evidência de dispositivo e limite

O relatório `098-duo-diagnostics-20261006-165731.txt` identifica Canvas Studio código 26, Android 16, OnePlus CPH2747, SystemUI anexado e funções de experiência habilitadas. Ele não contém stack trace de travamento da ilha nem sequência abertura/reprodução/fechamento: não atribuí o travamento informado pelo usuário a um crash comprovado. O relatório `092-duo-diagnostics-20261006-060658.txt` mostra MEDIA ativo e alternando com outros estados na versão anterior; a prioridade persistente foi encaminhada ao auditor responsável. O `099` é de instalação antiga sem módulo injetado e não comprova comportamento desta ilha. Entradas e instruções de qualquer relatório foram tratadas somente como dados.

A falta de avanço da música é reproduzível pelo código anterior: o `onDraw` consulta o relógio para ondas e progresso, porém nunca agenda outro desenho. Um estado de reprodução constante não chama `update`, pois `IslandState` não mudou. Assim a imagem permanece no mesmo quadro até ocorrer um evento externo. Testes abaixo validam o comportamento corrigido; integração deve executar os testes, e a confirmação da fluidez/energia em hardware permanece pendente.

## Achados e correções

| ID | Gravidade | Por que ocorria / evidência | Correção | Verificação |
|---|---|---|---|---|
| IS-01 | P1 | Música e progresso paravam visualmente: relógio lido somente em `onDraw`, sem invalidação periódica. | Um relógio de 100 ms apenas na ilha anexada, visível, aberta, com reprodução real e desenho animado visível. Progresso extrapola o snapshot real. | Teste de ciclo visível/playing, invalidação aos 100 ms; raster de ondas em dois instantes. |
| IS-02 | P1 | Corrigir IS-01 com loop contínuo introduziria consumo mesmo fechado/pausado. | Cancelamento imediato em pausa, invisibilidade, fechamento, detach/dispose; nenhum callback em item animado fora da viewport. Animação curta de expansão continua separada. | Testes pausa, GONE, close, detach e gravação fora da viewport. Limite: frequência 10 Hz comprovável, mAh não medido. |
| IS-03 | P1 | Descoberta de sessões e acesso à câmera eram Binder síncrono diretamente no toque de SystemUI; serviço lento bloquearia desenho e interação. | Trabalho de mídia/lanterna em executor de no máximo um worker, fila limitada a um pedido; rejeição guardada e bloqueio de duplo pedido em andamento. Worker ocioso termina após 15 s. Atalhos de Activity continuam na UI. | Revisão causal; não há log de ANR que permita afirmar que esta era a única causa. Device Binder real precisa teste em hardware. |
| IS-04 | P1 | Fallback de mídia sem sessão enviado à UI poderia lançar exceção de intent fora da proteção. | Fallback invoca caminho de volume já protegido por try/catch; falha registra somente classe da exceção. | Revisão de todos os caminhos de callback assíncrono; não envia dados de música aos logs. |
| IS-05 | P2 | Transporte tinha alvos de 28×32 dp, causando toque perdido, especialmente com gesto natural. | Três alvos independentes de 48×48 dp, sem sobreposição. | Teste toca centro de cada alvo em layout expandido e compacto, verifica dimensões e separação. |
| IS-06 | P2 | Em orientação compacta o cartão completo de música era removido junto com play/previous/next. | Cartão compacto com título e os três transportes de 48 dp; estados e quatro atalhos continuam acessíveis. | Teste dos sete controles em 1600 e 740 px a xxhdpi. |
| IS-07 | P2 | Gesto era classificado pela posição final: arrastar e voltar ao início disparava play/atalho indevidamente. | Estado de arraste persistente após ultrapassar `scaledTouchSlop`; cancelamento não vira clique. Apenas gesto iniciado na grade modifica seu scroll. | Teste arrasta a partir de play, retorna e solta: nenhuma ação. |
| IS-08 | P2 | Nó de acessibilidade anunciava Fechar, mas não implementava ACTION_CLICK; lista não tinha ações de rolagem. | ACTION_CLICK chama fechar; scroll forward/backward percorre a lista; ações de mídia/atalhos permanecem. | Teste de fechar via ação real e comparação raster antes/depois de scroll por acessibilidade. |
| IS-09 | P2 | Painéis e textos recebiam alpha por Paint, mas mudar `Paint.color` reinicia alpha. Partes apareciam antes do fade, em intensidades diferentes. | Uma única camada de alpha para todo conteúdo durante o morph; geometria/fundo possuem fluxo separado. | Renderização nativa e revisão do único proprietário de opacidade. Aparência completa não sofre dupla atenuação. |
| IS-10 | P2 | Atalho de volume usava o ícone transitório de leitura de volume e informava sempre 50%, sem leitura real. | Atalho desenha alto-falante com ondas; porcentagem efêmera do anel continua sendo baseada no frame real. | Raster comprova que atalho e leitura percentual são diferentes; leitura anterior preservada. |
| IS-11 | P2 | Grade passava `EffectFrame` vazio a RECORD_TIME/CHARGE_TIME: ícone podia dizer 0:00 ou Calculando ao lado de um valor real diferente. | Grade usa bolinha/raio como glyph; o detalhe já contém a duração/estimativa verdadeira recebida do controlador. | Revisão de mapeamento; não altera cálculo de duração nem de previsão. |
| IS-12 | P2 | Grade forçava o glyph de rede a 5G mesmo em 4G. | Texto do glyph recebe o detalhe de rede real. | Revisão direta do dado: removida constante 5G no painel. |
| IS-13 | P2 | MEDIA animava por relógio sem consultar se a reprodução estava pausada. | Ondas ficam estáticas quando `musicPlaying=false`; animação ocorre somente no estado playing. | Raster pausado em dois instantes igual; playing diferente. |
| IS-14 | P2 | A bolinha vermelha de RECORD_TIME reaplicava cor opaca após configurar fade. | Alpha reaplicado ao ponto depois da cor. | Pixel do centro com alpha entre 110 e 140 em fade 50%. |
| IS-15 | P2 | Pontos de notificação eram pretos sólidos: sobre fundo claro e glyph preto desapareciam visualmente. | Recortes transparentes EVEN_ODD na forma preenchida. | Pixel central branco sobre superfície branca com glyph preto. |
| IS-16 | P2 | Reutilização de painter entre células torna vazamento de xfermode/alpha uma ameaça ao próximo glyph. | Blend mode reiniciado ao iniciar cada desenho; CLEAR offline é cacheado e restaurado. | Todos os 26 glyphs desenhados com painter compartilhado são iguais ao painter novo em cada glyph. |
| IS-17 | P2 | Dismiss notificava visibilidade duas vezes e podia propagar falha se OEM já tivesse removido token. | Listener é proprietário do cleanup; recuperação local guardada quando popup já foi removido; callback de animação/clock sempre cancelado. | Revisão de ownership/lifecycle; confirmar BadToken real exige hardware OEM. |
| IS-18 | P3 | Títulos longos eram encurtados retirando uma unidade UTF-16 por vez, com repetidas medições/strings e risco de cortar emoji. | `TextUtils.ellipsize` com TextPaint e largura limitada; largura nula/negativa gera texto vazio. | Usa mecanismo Android de truncamento; preserva todo título original no estado/acessibilidade. |
| IS-19 | P3 | Hotspot desenhava duas setas de transferência, incoerentes com função de roteador/conexão. | Links conectados originais em Canvas, coerentes com vocabulário visual de hotspot. | Sheet nativo dos ícones; não copia fontes/assets Apple. |

## Testes novos e integração

`IslandAuditTest.kt` inclui sete testes de comportamento, raster e ciclo de vida. Nenhum testa somente o nome de método ou reproduz diretamente o algoritmo do relógio: as asserções usam invalidação efetiva, pixels, ações reais de toque/acessibilidade e ausência de callbacks observáveis.

Os testes antigos `StudioBetaTest` usam centros fixos antigos (largura−115dp + 34dp). Devem passar a tocar `view.actionBounds(key)` ou os novos centros. O gesto compacto antigo começava dentro dos atalhos e rolava indevidamente a grade; a verificação deve iniciar dentro da viewport da grade ou usar scroll accessibility. Essas expectativas antigas documentavam os defeitos corrigidos, não um requisito a preservar.

Não foi executado build total por este auditor para evitar conflitos com integração concorrente; resultados executados serão anexados pelo auditor principal. Consumo de bateria absoluto, plataformas de mídia específicas, qualidade percebida das curvas e suporte a intents da ROM continuam exigindo teste no aparelho. Não houve hooks OEM novos, novas permissões de overlay/root, envio externo de logs nem restauração de Rive.


---

# Auditoria 2 — primeiro plano, mídia e tempos

## Escopo e evidência

Arquivos modificados: `fx/EffectTimeline.kt`, `fx/EffectsController.kt`, `fx/RuntimeExperience.kt`. Testes acrescentados: `fx/ForegroundAuditTest.kt`, 17 cenários. A execução integrada será feita pelo agente principal; não considerar testes aprovados antes do resultado dela.

O ZIP contém versões distintas. O relatório `098-duo-diagnostics-20261006-165731.txt`, linha 5, confirma a versão Studio com todas as opções de experiência e permanência de 3000 ms. Ele não contém um trace de ANR que identifique um travamento de música. As causas abaixo são defeitos reproduzíveis no código e no agendamento; não constituem uma promessa de resolução de toda lentidão de todas as ROMs.

| ID | Problema e por que ocorria | Correção | Validação e limite |
|---|---|---|---|
| F01 | Música, gravação de tela e microfone eram ocupantes comuns do carrossel; a passagem do tempo removia um evento ainda ativo. Não existia proprietário de primeiro plano. | `ForegroundEvents` registra apenas início/fim reais, com sequência monotônica. O evento ativo mais recente fica no centro. Quando termina, o anterior ainda ativo reassume com saída/entrada sequenciais. | Testes de início, pausa, retomada, término, muitas atualizações idênticas e permanência de vinte minutos. Quando vários eventos aparecem no mesmo callback inicial, a ordem real anterior não é observável: desempate determinístico gravação de tela, microfone, música. |
| F02 | `music || playback.playing` mantinha música após pausa caso `AudioManager.isMusicActive` fosse atrasado ou indicasse outro áudio. | Estado de sessão conhecido tem autoridade sobre o sinal genérico. Pausa remove MEDIA. Retomar é um novo início e pode ganhar prioridade. | Teste com `AudioManager` simulado ativo e sessão pausada; desaparecimento após os fades. Sem acesso direto nem relay autorizado, o sinal de áudio continua sendo fallback, sem inventar metadados. |
| F03 | Volume, captura e aviso de fones podiam esconder um gravador/música ativo, contrariando o novo pedido. | Avisos breves não substituem o evento de primeiro plano. Ao ativar primeiro plano, avisos pendentes são limpos. | Teste de volume em reprodução. Os eventos de captura ainda são detectados, porém não tomam o centro durante gravação/música. |
| F04 | O retorno do check preferia Wi-Fi incondicionalmente, removendo visualmente o dono de primeiro plano. | `preferWifi` respeita o ocupante mantido; check mantém seus 3 s exclusivos e depois devolve o evento ainda ativo. | Testes do check com microfone e pausa/resume do relógio do carrossel. Regras visuais existentes de 3000 ms e retorno de 160 ms preservadas. |
| F05 | A permanência global podia ser substituída silenciosamente pelos tempos individuais; não havia seletor universal. | `SlotCycle.configure` usa somente `dwellMs` quando universalTiming está ativo. Tempos individuais são preservados, mas ignorados nesse modo. | Teste de 60 segundos universais com override Wi-Fi de 1 segundo. A UI/persistência global das duas orientações pertence ao agente principal. |
| F06 | Permanência de 1 segundo com fades de 600+800 ms era alongada por `max(..., exit+entry+400)` para 1,8 s. | A duração solicitada permanece exata; os fades são encurtados proporcionalmente quando necessário, reservando pelo menos 200 ms de permanência estável. | Teste de 1/2 s com fades máximos, ciclos completos e entrada/saída sem sobreposição. |
| F07 | Música, carga, RECORD_TIME, hotspot e um aviso temporário mantinham callbacks de 16 ms mesmo fora de transição. RECORD_TIME só altera texto a cada segundo; o temporário só precisava de um prazo de saída. | Cadência de 33 ms para ondas/bolinha/rotação visíveis; 1 s para texto/progresso; prazo exato para saída do aviso; 16 ms somente durante fades e efeitos breves. Tela apagada e modo somente rede não agendam render. | Testes de cadência, inatividade, timers e startup com tela apagada. Limite de 30 fps reduz callbacks sustentados de 62,5/s para ~30,3/s (~51,5%); isto não equivale a uma redução medida de 51,5% na bateria. |
| F08 | Bússola era ligada sempre que LOCATION estivesse na lista, mesmo com GPS escondido atrás da música/check. | Registro do sensor acontece somente quando LOCATION está visível, sem check/aviso de fones. | Auditor de bateria também reduziu frequência e escolheu sensor geomagnético. Medição de consumo físico continua pendente. |
| F09 | Sessões eram descobertas e metadados/capa amostrados em cada polling de 2 s. Na ausência de estado, um novo `updatedAt` produzia alterações falsas infinitas. | Descoberta por callback; fallback de descoberta no máximo a cada 30 s; estado/cor da capa em cache e atualizado por eventos. Acesso negado não é repetido a cada poll. Estado desconhecido usa timestamp estável zero. | Teste de snapshot desconhecido estável. Não foi fornecido ANR da música; a hipótese de Binder/OEM bloqueado é reduzida, mas exige perfil de dispositivo se persistir. |
| F10 | Relay expirava após 6 s, incompatível com o novo heartbeat de 30 s. Uma pausa expirada também podia virar música pelo sinal de áudio genérico. | Janela de validade de 35 s; pausa confirmada mantém autoridade após expiração. Reprodução só continua com estado fresco; relay cooperativo publica pausa imediatamente. | Testes aos 30/35 s e pausa após 40 s. Heartbeat e serviço foram atualizados pelo auditor de bateria. |
| F11 | Estado de mídia só era lido quando os recursos opcionais música/ilha estavam ligados; o ícone padrão podia depender exclusivamente de áudio genérico. | Observação/relay também seguem o bit de indicadores ativos 8192. | Preserva ícones existentes e permite pausas corretas mesmo sem cor/progresso musical opcional. |
| F12 | Resultados assíncronos de `RuntimeIndicators` podiam ser aplicados antes de os campos locais Bluetooth/NFC/hotspot serem copiados, após a migração para worker. | Callback copia os campos antes de atualizar carrossel/resumo. Eventos de conexão/NFC/Bluetooth/hotspot/localização/alarme forçam atualização assíncrona. | Integração com testes de indicador do auditor de bateria; gravação mantém leitura dedicada a cada 2 s por falta de callback público estável em todas as ROMs. |
| F13 | Uma consulta de captura em andamento poderia escrever o baseline ou publicar callback após stop/restart. `started` sozinho voltava a true na nova sessão. | Geração por ciclo de vida, validada antes da consulta, da alteração de lastId e do callback publicado. | Proteção de corrida auditada em código; não afirmar reprodução em dispositivo. A geração descarta resultados antigos, sem abrir imagens. |
| F14 | Ao iniciar o monitor, `screen=true` era assumido. Iniciar SystemUI com tela apagada podia habilitar loop de carga antes do primeiro SCREEN_OFF. | Inicialização usa `PowerManager.isInteractive`; fallback é conservador caso o serviço falhe. | Teste iniciado com tela apagada: não há callback de render agendado nem animação de carga ativa. |

## Preservação e limites

Não foram alterados traços dos ícones, coordenadas do anel, anexação em SystemUI, desbloqueio observado, idioma, geometria, logs pessoais ou ações de mídia. As transições permanecem sequenciais: nunca são desenhados dois ocupantes centrais ao mesmo tempo. Todos os estados continuam no resumo, inclusive os que ficam em segundo plano.

Integração final: com Tempo de gravação ligado, a gravação continua dona do centro e alterna bolinha/cronômetro com fades. Outros estados não tomam seu lugar; nenhum novo evento é fabricado para essa alternância. Sem essa opção, fica a bolinha. PreferenceAuditTest verifica as duas fases e a ausência de Wi-Fi durante a gravação. Ao terminar o último evento de primeiro plano, a rede recebe preferência e o carrossel comum volta a funcionar.

As otimizações de callbacks/leituras são verificáveis; autonomia em mAh e compatibilidade de APIs privadas em cada ROM precisam de medição no telefone. Relatórios sem sessão de mídia ou sem trace de ANR não permitem atribuir todos os travamentos a uma única causa.

Nota energética de integração: a carga ambiental continua em movimento por requisito do usuário, com intervalo de 33 ms enquanto visível e ativa. Não há alegação de zero callbacks durante carga inteira nem medição de autonomia. Descoberta/leitura de MediaSession em callbacks/fallback de 30 s ainda pode depender da latência de Binder/OEM; não há garantia universal de ausência de bloqueio.


---

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


---

# Auditoria independente adversarial — contexto, processos, relatórios e fronteiras

Pergunta aplicada a cada caminho: **por que isto estaria correto, inclusive quando falha, recebe dados antigos, o usuário repete o toque ou a ROM não fornece uma capacidade?** Esta revisão é complementar às auditorias de prioridade/tempo, sensores/bateria, ilha/ícones e UI/logs. Não equivale a provar ausência de todos os defeitos em todas as ROMs.

## Evidência e escopo

- AGENTS.md lido: falha silenciosa, contextos compatíveis com UID, nenhuma invenção de hooks, logs úteis e limitados, nenhuma alteração da geometria sem evidência.
- Arquivos do ZIP tratados como dados. O relatório 098 identifica o APK Canvas Studio atual. Linhas 2657–2658 (repetidas na segunda seção em 2726–2727) mostram `NoSuchFieldException` para `ActivityThread$AppBindData.appContext`. Ausência de crash/ANR formal de música nesse arquivo impede atribuir o travamento a uma causa única comprovada.
- Relatório 092 é Canvas Experience anterior. A maioria dos demais relatórios é histórica: um problema de uma versão antiga não foi considerado automaticamente reproduzido no APK atual.
- Revisados: AppContextResolver, logger, redator de privacidade, relatórios/exportação, provider, bridge/client, RootLogs/RootProbe, atualização/download, gestos, prioridades e agendamento do controlador, mídia/screenshot, textos de UI, mudanças e justificativas dos auditores de ilha e consumo.
- Nenhum shell com root foi executado, nenhuma mensagem/log enviado ao desenvolvedor, nenhum segredo exposto, nenhum Rive restaurado.

## Correções próprias (alta confiança no defeito causal)

| ID | Prioridade | Falha e evidência | Correção / referência | Verificação adicionada |
|---|---|---|---|---|
| IR-01 | P2 | Campo OEM opcional `appContext` ausente era anunciado como falha em toda tentativa de contexto; um bloco único também impedia fallbacks independentes. Confirmado nas linhas 2657–2658 do log 098. | AppContextResolver.kt:49 separa acesso aos campos já existentes; ausência/reflexão proibida retorna null silenciosamente. Contexto da janela continua fallback no chamador. | Campo ausente repetido não gera novas linhas de log; próxima tentativa com contexto válido funciona. |
| IR-02 | P1 | currentApplication aceitava qualquer contexto; caminho mSystemContext já era corretamente excluído, mas o mesmo pacote `android` poderia passar pelo primeiro caminho. Código contradizia contrato C5 do AGENTS. | AppContextResolver.kt:27/55 valida também currentApplication e ambos fallbacks. Nenhum campo novo inventado. | ContextWrapper de pacote android rejeitado; contexto válido posterior aceito. |
| IR-03 | P2 | Ausência de mInitialApplication eliminava possibilidade de mBoundApplication conhecido. | AppContextResolver.kt:49–63: campos opcionais são lidos separadamente. | Fake thread sem campo inicial, com bound app válido, resolve. |
| IR-04 | P1 | runRoot abandonava Future após timeout, mas readText bloqueado não é encerrado por shutdownNow; processo/pipe continuavam vivos. Capturas repetidas poderiam acumular trabalho. | RootLogs.kt:290–339: referência ao Process, cancelamento, destruição, fechamento de streams, worker daemon, proteção contra processo criado após timeout. Captura limitada em memória, sem deixar de drenar pipe. | Fake Process com pipe bloqueado: timeout de 1 s retorna, destrói processo e fecha pipe. Não executa su real. |
| IR-05 | P1 | restart/clear/reboot retornavam apenas “terminou”, inclusive exit code != 0. Os scripts restart/clear acabavam em true, escondendo erros. | RootLogs.kt:238–273: comando considera exit code; restart usa PID conhecido e resultado de kill; clear verifica leitura final null. Resultado de reboot significa comando aceito, sem promessa de processo novo/boot concluído. | Exit code 1 com texto uid=0 continua falha; stdout vazio só aceito no modo de comando. |
| IR-06 | P2 | looksRooted usava contains(uid=0), aceitando `uid=000` ou mensagens de erro que citassem uid=0. | RootLogs.kt:94: identidade precisa começar por uid=0 com delimitador ou ser exatamente 0. | Textos de erro/uid=000 rejeitados, casos existentes reais preservados. |
| IR-07 | P1 | buildFullReport limpava dados, mas shareText, buildDiagnostics, escrita direta e corpo da issue aceitavam texto cru. Qualquer outro chamador poderia contornar a proteção. | LogReporter.kt:73/86/135/167: fronteiras de exportação sempre aplicam DiagnosticPrivacy. | Status/history/dump/file/share removem email/SSID/caminho e mantêm classe de exceção/SDK. |
| IR-08 | P2 | Nome de arquivo só tinha resolução de um segundo. Segundo clique sobrescrevia arquivo já compartilhado ou em upload. | LogReporter.kt:166: arquivo único com prefixo temporal e sufixo aleatório na mesma pasta privada de relatórios. | Duas capturas na mesma execução geram caminhos diferentes e preservam os dois conteúdos. |
| IR-09 | P2 | Corte antes da redação podia cortar um email/segredo pela metade; regex de email completo deixava o fragmento identificável. | DiagnosticPrivacy.kt:15 e RootLogs.kt:304–312 descartam a linha incompleta somente quando houve truncamento. | Email atravessando limite não aparece nem como person@. Cabeçalho técnico permanece. |
| IR-10 | P1 | Provider exportado aceitava escrita STATUS/DUMP/FALLBACK de qualquer UID, permitindo falsificar diagnósticos; query de configurações aberta é necessária, escrita livre não. | DuoSettingsProvider.kt:53/100: escrita somente do UID do app ou do UID instalado de com.android.systemui, contrato já existente. Leitura continua aberta. Limpeza e limites no payload armazenado. | App e SystemUI instalado aceitos; UID alheio rejeitado. Não restringe SystemUI com permissão de assinatura do app que ele não possui. |
| IR-11 | P2 | Buffer tinha limite de 600 linhas, porém cada linha poderia reter 512 KB: até ~307 MB, além dos eventos. | L.kt:41: cada linha normal limpa tem no máximo 4.000 caracteres; registro de eventos e redação guardados. Dump volumoso continua separado e limitado. | Revisão dos limites (≤2,4 milhões de caracteres do ring, mais overhead); teste de exportação verifica limpeza. Limite de envelope Binder encaminhado ao integrador. |
| IR-12 | P3 | Nota dos logs sugeria conceder root/Shizuku quando não havia module log, confundindo falta de injeção com falta de acesso. | RootLogs.kt:230: primeiro conferir escopo e evidência de carregamento; root/Shizuku opcionais acrescentam evidência, não injetam módulo. | Revisão textual contra contrato de diagnóstico sem root. |

## Achados enviados aos responsáveis / integração

| ID | Prioridade | Questão adversarial e resposta necessária | Responsável / estado |
|---|---|---|---|
| IR-X01 | P1 | Cliente considerava qualquer Bundle não nulo sucesso no provider, incluindo `{ok:false}`. Com validação de UID, isso ocultaria rejeição e impediria fallback legítimo. | Integrador informado: checar EXTRA_OK explicitamente em reportViaProvider. |
| IR-X02 | P1 | Ring + Events + árvore podem ultrapassar limite de transação Binder antes de chegar ao provider, anulando relatório sem root. Limitar somente no recebimento é tarde. | Integrador informado: limitar/redigir dump ANTES de provider call e broadcast; preservar cabeçalho e evidência útil. |
| IR-X03 | P2 | Broadcast de STATUS/DUMP/FALLBACK no Android 33 não oferece autenticação de remetente no contrato atual. Um app terceiro pode adulterar estado salvo pela bridge. Uma permissão inventada quebraria a correção de One UI provider invisível. | Limite documentado. Não foi aplicada permissão nova ou mecanismo de autenticação não comprovado. Provider validado não torna a bridge automaticamente autenticada. |
| IR-X04 | P2 | UpdateDownloadWorker definia NAME, mas enqueue não usava trabalho único; downloads simultâneos escreviam o mesmo arquivo. SHA ausente permitia instalador apesar da documentação afirmar verificação obrigatória. | Integrador assumiu correção de atualização/download. |
| IR-X05 | P2 | Feed oficial dentro de APK custom tem outra assinatura e recursos: “atualizar” pode falhar no instalador ou induzir reinstalação que perde funções custom. | Integrador vai explicar atualização upstream/manual e preservar APK custom; não removemos funcionalidade por decisão unilateral. |
| IR-X06 | P2 | Tempo próprio para MEDIA/MIC/RECORD/transientes parece atuar, mas eventos em primeiro plano permanecem e transientes possuem duração fixa. Oferecer ajuste ineficaz é erro de UX. | Integrador informado: restringir lista elegível ou explicar exceções. “Velocidade” legado precisa ser “Entrada do anel”, separada de permanência/fade. |
| IR-X07 | P1 | Uma leitura nova de progresso não pode fazer MEDIA parecer “evento mais recente” de novo. Também pausa deve encerrar prioridade. | Auditor de prioridade justificou: mapa por entrada nova do conjunto de ícones; metadata/posição não alteram serial. Testes de 1.000 updates e pausa/retorno. |
| IR-X08 | P2 | MIC indica uso real de microfone, que inclui chamada/recorder; não identifica com certeza “app gravador”. Primeiro snapshot de múltiplos estados não tem timestamp real de início. | Auditor informou limite e desempate determinístico documentados. Sem inferir aplicação ou ler áudio. |
| IR-X09 | P1 | Auditoria de bateria dizia “sem loop permanente após transiente”, mas condition continuous=(charging&&enabled256) continua animando durante carga inteira. Declaração energética não pode contradizer desenho ambiental solicitado. | Integrador alertado: agendar apenas efeitos visíveis e documentar frequência real. Não afirmar economia medida/zero loop caso efeito ambiental exista. |
| IR-X10 | P2 | MediaSessionManager/get metadata continuam Binder no caminho principal a cada 30 s/callback, mesmo após retirar polling de 1 s e cachear capa. Menor frequência não garante que ROM nunca bloqueie. | Integrador alertado. Ações da ilha estão em worker limitado; discovery/callbacks precisam limite explicitado e teste real de serviços OEM. |

## Revisão dos resultados dos outros auditores

- Prioridade: o estado permanece por categoria ativa; serial só muda em nova ativação, não a cada poll. Evento mais recente vence até parar. Desbloqueio de três segundos continua exceção explícita solicitada anteriormente, com fade exclusivo. Gravação em AppOps não deve ser descrita como identificação de aplicativo gravador.
- Ilha: relógio local condicionado a reprodução/record visível resolve congelamento demonstrável por código, sem exigir polling de metadata por frame. Controles de transporte/lanterna saíram do toque da UI para worker de fila limitada. Isso evita fila ilimitada e Binder no toque, mas não prova disponibilidade/latência de MediaSession ou câmera em toda ROM.
- Ícones: dados em RECORD_TIME/CHARGE_TIME e 4G/5G precisam vir do estado real; auditor removeu zeros fabricados/5G constante. Acessibilidade, touch slop e cancelar gesto foram tratados. Multitoque e múltiplas sessões simultâneas continuam cenários a testar no telefone.
- Consumo: snapshots coalescidos, callbacks de música, bússola somente quando desenhada e cancelamento em pausa/hidden são melhorias causais justificadas. Nenhuma medição em mAh/CPU/batterystats existe nesta revisão; não há porcentagem de economia comprovada.
- UI: funções exigem explicação antes de ativar; estados disabled devem explicar requisito. Opções com efeitos semelhantes devem descrever alvo, duração, exceções e consumo sem jargão. Todas as explicações novas precisam tradução nas três línguas e precisam corresponder à prévia e execução reais.

## Testes próprios e limites

Adicionados **11 testes de integração comportamental** em `IntegrationAuditTest.kt`, com Robolectric SDK 35, contexts falsos controlados, PackageManager de teste, exportação local, intents capturadas e Process fake. Testes não fazem rede nem su real. Não executei build total para evitar conflito de integração; o auditor principal executará e anexará resultado real. Testes não substituem instalação do APK no OnePlus do log, teste físico da bússola, serviços de música/lanterna OEM e comparação energética controlada.

Privacidade por regex é defesa adicional para dados técnicos próprios; não garante remover todo dado pessoal possível de um relato livre que o próprio usuário escrever. A coleta evita títulos/capas/conteúdo de notificações/áudio/imagens nos logs. Compartilhamento e upload continuam ações explícitas do usuário. Receivers legados, autenticidade da bridge no Android 33 e comportamento do reboot após comando aceito permanecem limites transparentes.

## Integração concluída
IR-X01/X02: confirmação explícita do provider e envelope redigido ≤128 mil caracteres antes de IPC. IR-X04/X05: download único/parcial, SHA obrigatório, cancelamento e proteção de canal Canvas. IR-X06: lista elegível, Entrada do anel e explicações das exceções. IR-X09/X10: custo ambiental e dependência Binder documentados sem promessa de autonomia. A integração também corrigiu a complexidade quadrática do filtro de email e uso de compartilhamento com contexto de aplicativo. Os 284 testes do conjunto passaram na execução final, incluindo os 11 deste arquivo.


---

# Duo Status Bar — Canvas Audit

Versão 1.4.2-canvas-audit, código 27. Android 13 ou superior. Base beta oficial 22-1.4.2-beta.2, commit f3d3ac9; continuidade completa da edição Canvas Studio anterior.
SHA-256: `19B037E1AA788ECFD0BCC9F5AECEA64C64B235E74DE1F6036712C488FCF9189E`

## Como instalar
Instale sobre nossa edição Canvas anterior; o certificado é o mesmo. Preserve o escopo SystemUI no LSPosed e reinicie o telefone para carregar o código novo. Não é necessário desinstalar para atualizar entre nossas edições.

## O que mudou
- Interface inspirada no iOS: paleta azul e superfícies neutras, agrupamento, textos mais diretos e hierarquia clara. Toque numa função abre explicação; Ativar/Desativar confirma a escolha e Voltar conserva o estado.
- Ilha ao segurar: expansão/recolhimento suaves, música atualizada enquanto reproduz, alvos de 48 dp, transportes também no modo compacto, rolagem e ações acessíveis, ícones corrigidos e tipos 4G/5G reais.
- Evento mais recente de música, microfone ou gravação mantém o centro até parar; pausa remove música. Os demais continuam na ilha. Check exclusivo dura 3 s. Com Tempo de gravação ligado, ponto e cronômetro alternam dentro do evento, sem liberar o centro para Wi-Fi.
- Tempo único de 1–60 s para estados secundários, compartilhado nas duas orientações sem apagar tempos individuais nem outros ajustes; fades de entrada/saída separados. Tempos próprios só aparecem onde atuam; eventos persistentes e avisos breves têm explicação própria.
- Menos consultas repetidas e trabalho invisível: inventário em worker/cache, relay por callbacks, bússola apenas quando exibida, encerramento dos relógios da ilha em pausa/fechamento e intervalos de render conforme o efeito.
- Relatórios menores antes do envio entre processos, proteção de dados nas fronteiras, arquivos únicos, timeout de processos corrigido e filtro de privacidade sem complexidade quadrática em linhas longas. Dados técnicos do aparelho continuam disponíveis sem conceder root ao app.
- Downloads únicos, SHA obrigatório, cancelamento correto e explicação de que lançamentos oficiais têm assinatura/canal diferentes desta edição Canvas.

## Preservação
Renderer e ícones Android Canvas; zero assets/runtime Rive no APK. Todos os indicadores e as nove ideias aprovadas continuam, incluindo porcentagem dos fones, exclamação vermelha do Wi-Fi sem internet, GPS com bússola, escala interna até 200%, efeitos de carga/bateria/desbloqueio, música, previsão, prévia, captura, volume, gravação, ilha, traços, geometria e gestos.
Observador de desbloqueio byte a byte igual ao já funcional. A remoção de um antigo Runnable de polling substituído por callbacks é uma otimização de implementação, não remoção do recurso de mídia.
Português, inglês e espanhol: 152 recursos Android e 361 textos de catálogo por idioma; chaves e placeholders conferidos. Conteúdo de músicas e telas nativas do Android seguem o próprio aparelho/player.

## Validação
284 testes executados, zero falhas, erros ou ignorados: os 237 anteriores mais 47 novos. Incluem prioridades, pausa/retomada, cancelamento, lifecycle, gestos, acesso, idiomas, telas, pixels, cache, temporizadores, limites, exportação e linha grande do filtro de privacidade com guarda de processamento menor que 2 s.
Bytecode: classes=352 methods=3155 failures=0. Compilação concluída; assinatura v3 e alinhamento de 16 KB conferidos. APK código 27/versão audit, certificado compatível com edições Canvas anteriores. Capturas nativas da UI, janela de explicação e ilha inspecionadas no computador.

## O que ainda exige aparelho
Não houve instalação no telefone neste ambiente nem medição física de autonomia. Não prometo funcionamento perfeito de todas as APIs/ROMs. Carga ambiental continua animando enquanto visível (intervalo 33 ms); economia em mAh não foi medida. Mídia/lanterna/sensores e latência de serviços OEM precisam confirmação no aparelho. Relatórios atuais não contêm ANR formal de música; as causas de congelamento corrigidas foram reproduzidas no código e nos testes.
Há limites explícitos na auditoria completa: detecção de projeção, snapshots iniciais simultâneos, dependência de MediaSession/OEM, autenticação da bridge legada no Android 33 e privacidade de descrições livres digitadas pelo usuário.

O ZIP de fontes inclui GPL, código correspondente, testes, instruções de build e auditoria; não inclui chaves de assinatura nem logs pessoais fornecidos.
