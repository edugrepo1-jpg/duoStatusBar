# Auditoria DUO Recreate — dez perspectivas

Método: revisões paralelas e integração em dez lentes. Não houve dez agentes simultâneos; três frentes especializadas colaboraram com a revisão de integração. Cada item separa causa, evidência, correção, teste e limite. “Crítico” afeta estabilidade/integridade da barra; “normal”, funcionamento/custo/interação; “leve”, clareza/apresentação. Nenhuma classificação promete ausência de outros defeitos.

## Lentes 1–3: diagnóstico, estado de funções e privacidade

| ID | Nível | Achado e causa | Correção | Verificação/limite |
|---|---|---|---|---|
| LOG-01 | Normal | Eventos anteriores não distinguiam habilitado de funcionando. | Ledger fixo com configuração/detecção/acesso/execução/falha e última evidência de 37 categorias. Inativo e não observado não são falhas. | TelemetryAuditTest; desenho bem-sucedido não certifica hardware/ROM. |
| LOG-02 | Normal | Relatórios não permitiam comparar trabalho e consumo observado. | Reaproveita broadcast de bateria com limite de 60 s, segmentos por conexão/reversão e taxa após ≥10 min descarregado. Contadores de desenho/tempo de callback/consultas/cache/sensor/visibilidade. | Sem timer/receiver/wakelock novo. Bateria do aparelho não é energia atribuída ao módulo; contadores são por processo/desde início. |
| LOG-03 | Normal | Buffers limitados por linhas ainda podiam guardar ~3,2 milhões de caracteres. | Orçamento total de 128 Ki caracteres em L e 96 Ki em Events, conservando eventos recentes. Dump antes de IPC continua ≤128 mil, telemetria no cabeçalho. | TelemetryBoundsTest stress de linhas longas e IntegrationAuditTest privacidade. |
| LOG-04 | Crítico | Pedido de diagnóstico tinha handler, mas não constava no filtro do receptor. Exportação podia usar estado antigo. | Registra ação própria DIAGNOSTICS_REQUEST; captura estado atual antes de exportar, mantendo canal sem root. | Inspeção do contrato filtro/handler e builds; resposta depende de módulo realmente injetado. |
| LOG-05 | Crítico | Reinício do SystemUI tinha receptor exportado sem permissão de remetente. Outro app podia enviar a ação. | Receiver exige permission SETTINGS de assinatura. Recuperação interna do provider usa callback local, pois SystemUI não possui essa permissão. | Manifesto/receiver alinhados; não foi executado ataque no telefone. |
| LOG-06 | Crítico | Bridge de retorno aceitava status/dump/fallback de qualquer remetente, inclusive no Android 33. Poderia sobrescrever diagnóstico real com falso. | Challenge aleatório de 256 bits em preferência privada do app, enviado ao SystemUI exclusivamente pelo canal SETTINGS protegido por assinatura. Receiver verifica em tempo constante; não aceita token ausente/falso. Últimos três relatórios aguardam handshake, limitados por caracteres, sem timer. | ReportAuthenticationTest rejeita mensagem falsa, aceita legítima e testa fila/limite/persistência. Testado no runtime Robolectric 35; Android 33 físico não foi testado. Token nunca entra no provider público, log, exportação ou migração. |

## Lentes 4–6: bateria, mídia e ciclo de vida

Detalhes reproduzíveis no relatório runtime anexo: heartbeat adiado por callbacks repetidos, reanexação depois de desligar mídia, descoberta bloqueada por cooldown antigo, screenshot cujo provider voltou sem baseline e leituras duplicadas de metadados. Correções preservam pausa autoritativa, cache e fonte interna para controlar o player certo. O nome do player é usado localmente e não entra no log técnico.

Falha de bússola podia repetir consulta/registro em cada frame; agora espera 30 s após falha. O contador mostra tentativas e amostras. Medir economia real em mAh exige teste no telefone; nenhuma porcentagem de economia foi inventada.

## Lente 7: ilha, gesto, ícones e acessibilidade

| ID | Nível | Achado | Correção e evidência |
|---|---|---|---|
| ISL-01 | Normal | Painel inicial ocupava quase a tela, embora fosse um resumo rápido. | Janela e superfície compactas 232×64 dp. Gesture down expande; header up recolhe; teste mede tamanho real e insets assimétricos/retrato/paisagem/tela minúscula. |
| ISL-02 | Normal | Arrastar/retornar ou multipointer podia acionar um botão. | Drag fica marcado; CANCEL/multipointer abortam; grid scroll separado de expansão; testes negativos de toque. |
| ISL-03 | Normal | Controles de mídia podiam escolher outra sessão com várias ativas. | Snapshot carrega fonte privada; consumidor filtra controllers por fonte, sem registrar conteúdo/nome. Ações continuam fora do thread de desenho. |
| ISL-04 | Leve | Estado genérico “Ativo” virava texto do ícone de rede; alvo de volume era um número simulado. | Correções da edição Audit preservadas: labels válidos 4G/5G ou barras, speaker nos atalhos, arte consistente; timer/dot alpha correto. |

## Lente 8: UI, explicações e tema

Cartão destacado preto com ícone do renderer real, estado e controle circular próprio. Função só muda após confirmação; cancelar preserva. Conteúdo rola se precisar; janela não ocupa página inteira. Fundo Compose aplica blur apenas enquanto diálogo existe; não há captura bitmap nem loop. Tema explícito sistema/claro/escuro, preferência e cores das barras de navegação/status atualizadas. PT/EN/ES mantidos. Testes de abertura/cancelamento/confirmação, navegação e captura nativa.

## Lente 9: pacote, migração e atualização

| ID | Nível | Problema | Correção/limite |
|---|---|---|---|
| FORK-01 | Normal | Usar pacote original provoca conflito de providers, canais e identidade. | Novo applicationId e contratos, provider/arquivos/Shizuku/actions/settings permission; timestamp de carregamento distinto. Namespace fonte preservado. Não ativar os dois módulos ao mesmo tempo. |
| FORK-02 | Normal | Feed/apoio/contato/relay seguiam para autor original. | Feed e issue tracker do fork real, crédito GPL, apoio/contato retirados, relay/token em branco e share chooser local. |
| FORK-03 | Crítico | Hash de transferência sozinho não confirma identidade/certificado do APK que será instalado. | Download só de asset do fork via HTTPS; valida tamanho ≤128 MiB, hash, pacote próprio, certificado igual ao instalado e versionCode maior antes de oferecer instalador. Testes rejeitam URL estrangeira/traversal/assinatura diversa/downgrade. |
| FORK-04 | Normal | Novo pacote não pode ler SharedPreferences privado do antigo. | Importação única do provider somente de nossa edição Canvas assinada; ambas orientações, efeitos e tempos próprios preservados. App antigo precisa estar instalado. Permissões/runtime grants não migram. |

## Lente 10: história, regressões e limites

Base upstream com histórico intacto; snapshots recuperados têm procedência explícita e hash. Código/relatórios anteriores preservados; fontes da assinatura não incluem chave. APK sem Rive; observador de desbloqueio conservado byte a byte; testes anteriores mais novos verificam contratos. Relatório final de validação traz contagens realmente executadas, hashes, assinatura e bytecode.

Foram lidos anteriormente 104 arquivos do ZIP (95 únicos). Crashes de outros processos/root ausente não são tratados como bugs atuais. A evidência anterior da edição Studio indicava anel anexado, sem ANR musical comprovado e sem medição de energia. Logs originais, fotos, conversas e dados do dispositivo não são publicados no fork. Diagnósticos atuais permitem a próxima verificação real do seu aparelho. Nenhum teste em aparelho físico foi feito nesta entrega; suporte de sensores/ROM, blur da GPU e economia física precisam dessa etapa.
