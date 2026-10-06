# UI e painéis — 1.4.3 / código 29

## Pedido e resultado

Controle pill com trilho cinza/azul e polegar preto, estado e acessibilidade de switch; permanece aberto ao alterar. Opções explicativas têm Configurar e seta; destinos têm Abrir. Desfoque por leases: fechar um modal não remove o efeito de outro ainda aberto; disposição remove seu próprio lease. Cabeçalho/controle/Fechar fixos, explicação rolável para texto grande.

Visual inclui notificações e ajustes rápidos com habilitação e tamanho 50–200% independentes, também salvos por orientação. O tamanho da barra normal não é reutilizado como preferência do painel. Android limita a arte à altura do cabeçalho.

## Correção dos cabeçalhos

O mecanismo herdado dependia de ShadeHeaderController/ViewStub e shade_header_system_icons. ROMs com cabeçalhos separados podem não usar esse caminho. Agora há descoberta limitada de BatteryMeterView/CombinedStatusView dentro de cabeçalhos conhecidos, hooks de inflação OEM e expansão compartilhada. A âncora é o pai imediato do indicador no cabeçalho, nunca o centro da janela de notificações ou um tile.

Cada slot tem seu restaurador de ícones: desligar um painel devolve apenas os indicadores dele. Slots duplicados no mesmo contêiner são evitados; reinflação limpa instâncias anteriores. A descoberta em layout é limitada a 512 vistas e intervalo de 750 ms, sem polling ou wakelock novo. Em cabeçalhos compartilhados, setQsExpanded/setExpanded seleciona a preferência correspondente. Sem cabeçalho reconhecido, nenhum ícone é removido.

## Evidência e limites

Testes cobrem localização de dois cabeçalhos OEM simulados, exclusão de lockscreen/tiles, cabeçalho compartilhado, prioridades/habilitação, preferências nas duas orientações, colunas antigas, limites e restauração independente. Galeria usa desenho nativo Android/Robolectric 35, não mockup ilustrado. Leases e switch são exercitados por interação.

Não há execução no Samsung das imagens do usuário nesta entrega. Isso exige confirmar os nomes/classes realmente usados pela sua versão de One UI, layout final e GPU blur no aparelho. Imagens e mensagens pessoais do usuário não são publicadas. O log de árvore do SystemUI permite identificar um cabeçalho ainda sem suporte sem exigir root para exportação.

## Preservação

Não altera observador de desbloqueio, renderizador Canvas, duração de 3 segundos, prioridade do evento mais recente, efeitos de carga, ritmo universal/individual, sensores, ilha, telemetria ou identidade/assinatura do fork. A suíte de regressão completa é executada novamente; os resultados finais estão em VALIDACAO.md.

## Remoção dos indicadores nos painéis

A lista opcional do Shizuku permanece `wifi,mobile,battery`, preservando as entradas de outros aplicativos. Como a One UI pode ignorar `icon_blacklist` nos cabeçalhos, cada slot DUO também tem seu próprio responsável pela remoção/restauração dessas views (inclusive CombinedStatusView). A remoção só ocorre depois de o Canvas estar pronto. Os indicadores voltam ao desligar aquele painel ou desmontar o host; não se esconde o painel inteiro. Nenhuma nova chamada shell periódica foi adicionada. A integração automatizada verifica remoção nos dois painéis e restauração independente. O efeito da blacklist global do Android após desativar um painel depende da ROM; para exibir os indicadores originais nesse caso, desative também a remoção adicional via Shizuku.
