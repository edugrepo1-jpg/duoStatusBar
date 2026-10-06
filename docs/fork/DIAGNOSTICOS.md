# Diagnósticos sem root — contrato e interpretação

O app exporta informações próprias e o relatório produzido pelo módulo injetado no SystemUI. Não precisa executar `su` para obter configuração, estado da barra, APIs disponíveis, dispositivo e contadores. Isso não concede ao app acesso irrestrito ao logcat de todos os processos nem substitui as condições de instalação do LSPosed. Captura privilegiada continua opcional e identificada separadamente.

## O que cada estado significa

| Campo | Significado e limite |
|---|---|
| configured | Preferência habilitada/desabilitada. Não prova que o estado existe no aparelho. |
| detected | Última observação do sistema, quando disponível. Inativo não é defeito. |
| probeOkay | API/leitura disponível ou falhou. Ausência de observação permanece desconhecida. |
| execution_observed | Houve execução/desenho observado desta função. Não certifica comportamento de todo hardware/ROM. |
| disabled / inactive | Configuração desligada ou estado não ativo. Não entram como falha. |
| failed_reader | Uma leitura falhou; classe/momento e idade da evidência permitem investigação. |
| unverified | Não há evidência suficiente; não é inventado “funcionando”. |

Ledger de 37 categorias, tamanho fixo, registra transições, execuções e falhas. Contadores mostram desenhos, tempo dentro do callback, ticks, consultas, acertos de cache, amostras/tentativas de sensor, início/fim de controllers e exportações. Contagem atômica de frames evita escrever uma linha de log por frame. Duração visível/pausada/parada é por processo desde seu início. Tempo de callback não é tempo de CPU nem energia em mAh.

## Bateria

Reaproveita o broadcast de bateria que já existia. Amostra no máximo a cada 60 segundos, separa períodos de conexão/descarga e reinicia a referência ao mudar a direção da porcentagem. Relata nível, temperatura e tensão disponíveis. Taxa somente após pelo menos dez minutos em um segmento descarregado comparável. Sem dados suficientes, fica desconhecida.

O resultado é **do dispositivo inteiro**. Luminosidade, rádio, apps, temperatura e desgaste alteram o nível; não se atribui uma variação ao DUO automaticamente. Contadores de trabalho ajudam comparar duas sessões, mas não calculam energia do módulo. A economia física precisa de comparação controlada no telefone.

## Dispositivo, privacidade e limites

Inclui Android/API/patch, marca, fabricante, modelo, build/ROM, kernel, ABI, SELinux quando disponível, tela/densidade/orientação, versão do módulo e anexação. Não inclui IMEI, serial, MAC, SSID, coordenadas, conteúdo de músicas/notificações, imagens ou áudio. Fonte do player serve somente para direcionar controles locais e não é exportada. Texto livre digitado pelo usuário requer revisão antes de publicar.

Logs recentes limitados a 128 Ki caracteres; eventos a 96 Ki. Dump preparado antes do IPC até 128 mil caracteres. Relatórios têm limite explícito de tamanho e filtro de privacidade nas fronteiras. Arquivos de exportação são únicos e compartilham pelo seletor Android; nenhuma mensagem é enviada automaticamente ao autor original.

Provider valida UID do app/SystemUI. A alternativa por broadcast exige challenge privado recebido pelo SystemUI no canal protegido por assinatura; mensagens falsas são ignoradas. Se a autenticação ainda não chegou, só o último relatório de cada tipo espera numa fila de três itens, sem timer extra. Challenge nunca é publicado no log, no provider de configuração ou no Git. As ações de atualização de configuração, reinício e experiência mantêm proteção de assinatura.

## Como comparar uma sessão

Exporte um relatório depois de abrir o app e carregar o módulo novo. Use uma segunda exportação após uso com mesma luminosidade, conexão, carga e duração. Compare estado ativo, intervalos de amostra, consultas/cache/sensores e tempo visível. Diferencie observações do processo do app das observações do SystemUI. Relatório antigo/canal indisponível fica identificado; estado desconhecido não deve ser tratado como sucesso ou falha.

Testes novos incluem transições/estados, taxa mínima e segmentação, limites de buffers, idade de evidência, fila e autenticação de retorno. A contagem final realmente executada está em VALIDACAO.md.
