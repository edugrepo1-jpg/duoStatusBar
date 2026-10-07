# Auditoria 1.4.4 — orientação, painéis, ícones e navegação

Data: 7 de outubro de 2026. Esta revisão complementa as auditorias anteriores, preservadas no repositório. A interface conserva o design anterior do DUO Recreate. Moto Nuvem não foi aplicado ao resultado final.

## Evidência e alcance

O relatório fornecido mostrou o Canvas anexado na horizontal, sem falha do renderizador, mas com um perfil horizontal antigo: tamanho 100%, painéis 100% e recursos expandidos desligados. O perfil vertical tinha tamanho 133%, painéis 200% e os recursos ligados. Isso explica o anel visível sem os mesmos efeitos e gestos após girar.

O cabeçalho horizontal observado continha uma faixa interna de 30×34 px dentro de um cabeçalho de 160 px. O tamanho do anel estava limitado pela faixa interna. Dois caminhos de descoberta podiam anexar elementos às faixas interna e externa do mesmo cabeçalho. Os nomes usados na correção foram observados no relatório; não foram adivinhados.

Não publicamos o relatório bruto nem identificadores pessoais. Os testes usam árvores de Views e estados simulados. Eles não substituem a verificação física na ROM Samsung do usuário. Consumo de bateria no aparelho ainda precisa de medição; não há promessa de redução percentual.

## Achados e correções

| Nível | Problema | Correção e validação |
|---|---|---|
| Crítico | Recursos e gestos desligados por um perfil horizontal antigo | Ajustes vinculados entre orientações por padrão. O usuário pode separar os perfis em Visual. O perfil antigo permanece guardado. Testes de edição, leitura, revisão e canal de configurações. |
| Crítico | Janela substituída ao girar perdia o caminho de toque do módulo | Registro fraco das janelas e do método concreto de toque. A janela nova recebe o monitor e os gestos; chamadas a métodos superiores não duplicam o toque. Testes de substituição e herança. |
| Normal | Notificações e ajustes rápidos limitados pela faixa de ícones de 34 px | Seleção do cabeçalho real, base de bateria estável e tamanhos independentes. Testes com a estrutura e dimensões observadas, incluindo 50% e 200%. |
| Normal | Elementos duplicados no mesmo cabeçalho | Descoberta convergente para o mesmo contêiner externo e reutilização do elemento já anexado. Teste de descoberta genérica e específica. |
| Normal | Canvas recém-anexado não recebia o estado atual até outro evento | Configuração, estado visual e efeito mais recente aplicados no momento da anexação e ao reativar um painel. Teste de preservação dos efeitos. |
| Normal | Geometria não reaplicada ao girar quando as configurações eram iguais | Atualização forçada da geometria, mesmo com perfis vinculados idênticos; a ilha antiga é fechada antes de reposicionar. Teste de rotação e substituição da janela. |
| Normal | Ilha compacta priorizava música sobre uma gravação mais recente | O estado da ilha recebe o evento dominante do mesmo controlador do anel. Teste de gravação, música e término do evento. |
| Normal | Controles em categorias inadequadas e card muito longo | Escala dos ícones em Visual; ritmos, desenho e eventos em Efeitos; permissões em Ajustes. Recursos separados em cards menores e controles repetidos removidos. Testes de localização e navegação, inclusive horizontal. |
| Normal | Explicação apontava para uma seção removida | Instrução atualizada para Ritmo e transições na aba Efeitos, em português, inglês e espanhol. Verificação dos catálogos e parâmetros traduzidos. |
| Leve | Fones diferentes da referência e Wi-Fi offline com aviso antigo | Fones sobre a orelha, arco simétrico, almofadas e degradês; Wi-Fi com a mesma geometria do conectado, adicionando apenas a faixa vermelha diagonal. Arte original em Canvas, sem marca d’água ou dependência de fonte. Testes nativos de forma e cor. |
| Leve | Wi-Fi offline sem pulso rápido | Duas pulsações por segundo, com opacidade mínima de 45%. Usa a cadência existente apenas no ícone visível, sem temporizador novo. Respeita desativação das animações e modo somente rede. Testes de período, legibilidade, bloqueio e desenho estático. |
| Crítico, detectado antes da entrega | Catálogo dos ícones inicializado antes das almofadas dos fones | Ordem de inicialização corrigida. A suíte identificou o acesso nulo; a versão com esse erro não foi publicada como APK. Revalidação da construção de todos os ícones, interface e ilha. |

## Organização final

- **Início:** prévia e ativação principal, com acessos às áreas.
- **Visual:** orientação vinculada ou separada; geometria do anel; notificações e ajustes rápidos; porcentagem; posição; cores e apresentação; escala dos ícones; alinhamento e vidro.
- **Efeitos:** controle geral; entrada e saída do anel; carregamento; ritmo universal ou individual de 1 a 60 segundos; fades sequenciais; desenho dos ícones; música e gravação; eventos; Dynamic Island; indicadores; bateria fraca; prévia interativa.
- **Ajustes:** conexões e permissões; pausa na câmera e bolso; remoção dos ícones originais; gestos; tema; idioma; atualizações do fork; diagnóstico.

Explicações permanecem em um card modal com controle de ativar/desativar. O fundo desfoca enquanto há um modal e volta ao normal ao fechar. O botão de ativação em lote permanece disponível. Tempos individuais, escolhas de idioma, funcionalidades anteriores e assinatura do APK são preservados.

## Energia e detalhes visuais

Não foi criado serviço adicional, wakelock, sensor ou consulta periódica para o pulso offline. O controlador já suspende quadros com tela apagada, no bolso, em contexto bloqueado ou com animações desligadas. Um pulso visível necessariamente produz mais quadros que um ícone estático; desativar animações deixa o aviso imóvel. A ilha usa seu ciclo de desenho somente quando um elemento móvel está visível.

Quando o fone Bluetooth está visível, o anel e o número superior passam à bateria dele. Quando sai, voltam ao celular. A porcentagem abaixo do ícone foi removida. As faixas 0–20%, 21–80% e 81–100% podem receber qualquer cor opaca em Visual; por padrão, o fone usa vermelho, amarelo e verde. A bateria desconhecida conserva a do celular, e os cálculos de carga/logs continuam referentes ao dispositivo. A leitura depende de o acessório informar bateria ao Android. A ampliação até 200% continua limitada ao espaço livre do anel para evitar recortes.

## Verificação no aparelho após atualizar

1. Ativar o novo APK no mesmo escopo System UI do LSPosed e reiniciar System UI ou o dispositivo para carregar o código novo.
2. Em Visual, manter **Manter ajustes ao girar** ligado; conferir efeitos, ícones e toque prolongado nas duas orientações.
3. Ajustar separadamente os tamanhos de notificações e ajustes rápidos. Conferir ambos abertos em retrato e paisagem.
4. Desconectar a saída para internet mantendo o Wi-Fi conectado: conferir símbolo riscado e pulso; desligar animações e confirmar estado imóvel.
5. Reproduzir música e iniciar gravação depois: o evento mais recente deve ocupar o anel e a ilha compacta. Ao terminar, o evento anterior ainda ativo volta.

Esta lista serve para validar integração no dispositivo. Testes automatizados e provas gráficas ficam registrados na validação desta versão; não afirmamos que todos os comportamentos específicos de todas as ROMs foram testados.

## Nova troca de fonte da bateria

A seleção depende do ícone realmente visível (incluindo sua entrada e saída), do aviso temporário de fones e de uma leitura válida de 0 a 100. O check suspende a troca. Fones com fio não recebem a bateria de outro dispositivo Bluetooth: a consulta filtra saídas Bluetooth antes de consultar acessórios conectados. O raio, brilho e alerta crítico do celular não são desenhados sobre a bateria do fone. A opção de esconder o número continua respeitada. Não foi acrescentada consulta por quadro, sensor ou temporizador.

Testes verificam limites 20/21 e 80/81, bateria zero válida/valor desconhecido, fades, retomada do celular, carga do celular simultânea, prioridade do check, persistência das três cores e fechamento do seletor com remoção do blur.
