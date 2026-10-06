# Duo Status Bar — Canvas Studio 1.4.2

Versão 1.4.2-canvas-studio, código 26. Base oficial: tag 22-1.4.2-beta.2, commit f3d3ac9. Android 13 ou superior.
SHA-256 do APK: `611E701B4C641FE2E46CF1D028B3D3723D894726DB77A2FC3BD261C0A4284190`

## Instalação e uso
Instale este APK por cima da versão Canvas anterior. O certificado é o mesmo; as preferências existentes permanecem compatíveis. Mantenha o escopo SystemUI no LSPosed e reinicie o telefone para carregar o módulo atualizado.
Na primeira abertura, escolha Português, English ou Español. A escolha fica salva e pode ser alterada em Mais → Idioma; também chega aos textos do painel na barra.
Em Efeitos → Experiência expandida, ative Resumo expansível. Segure o anel para abrir o painel. Ativar todas as novidades habilita as funções opcionais sem apagar tamanho, idioma e tempos já escolhidos.

## Painel ao segurar, redesenhado
Superfície preta, contorno discreto, cantos arredondados e expansão/recolhimento animados a partir do anel. O painel usa a janela existente da barra; não requer permissão de sobreposição do aplicativo.
- Música: título local, cor da capa, progresso e botões anterior, reproduzir/pausar e próxima.
- Bateria do telefone, previsão de carga quando há evidência e percentual dos fones quando o acessório fornece esse dado.
- Todos os estados ativos em cartões, com rolagem. Layout compacto em orientação horizontal; não cresce além da área disponível da tela.
- Atalhos para rede, Bluetooth, volume e alternância da lanterna. As três primeiras opções abrem os controles nativos Android; música usa a sessão real e lanterna usa o serviço da câmera.
- Fechamento por botão, toque fora, voltar ou mudança de estado que exige recolher. A confirmação do desbloqueio continua com prioridade. A prévia não aciona os serviços reais do telefone.

## Preservação do nosso projeto
Renderer exclusivamente Android Canvas, UI Studio, ícone próprio do aplicativo e ajustes anteriores mantidos. Não há runtime nem assets Rive no APK.
Os 20 indicadores pedidos continuam: Wi-Fi, rede móvel 5G/4G, avião, Não Perturbe, Bluetooth, NFC, hotspot, fones, confirmação/check, raio de carga rápida, câmera e microfone separados, alarme, VPN, GPS/localização, silencioso/vibração, mídia, carga sem fio, lanterna, gravação e Wi-Fi sem internet. Os estados de silencioso/vibração e câmera/microfone têm ícones separados; indicadores adicionais oferecem previsão de carga, cronômetro, volume, captura e notificação.
Também permanecem: confirmação exclusiva de desbloqueio por três segundos com fade de saída e retorno ao Wi-Fi; saída antes da entrada de outro ícone; carrossel de todos os estados ativos; efeitos de carga, bateria crítica, vidro, molas, hotspot, bolso e contexto; ajustes de espessura/posição/altura/escala; prévia interativa e correções anteriores de cliques, anexação e receptores.
As nove ideias escolhidas continuam implementadas: anel musical, previsão de carga, prévia interativa, confirmação de captura, editor de ritmo, volume temporário, gravação com tempo, resumo expansível e desenho dos traços dos ícones. GPS com bússola e tamanho dos ícones até 200% permanecem.
O observador de desbloqueio é byte a byte igual ao da versão Experience entregue. Nenhuma declaração de função anterior desapareceu; os testes de comportamento anterior foram executados junto dos novos.

## Novos ajustes
- Permanência padrão de 1 a 60 segundos e duração individual por ícone. Saída e entrada continuam separadas. Tempos de fade muito longos respeitam o mínimo necessário para concluir a transição.
- Desativação do fade, com alternância direta, ou modo Somente Wi-Fi ou dados, que conserva apenas o indicador de rede e desliga os efeitos/carrossel.
- Fones redesenhados com silhuetas preenchidas e hastes curtas, em estilo iOS. Percentual abaixo, acima dos pontos: mais de 15% verde; 15% ou menos vermelho. Dado indisponível é omitido.
- Wi-Fi sem internet com exclamação vermelha grande no centro, cobrindo a área central do símbolo.
- Traduções em Português, Inglês e Espanhol: 152 recursos Android e 274 textos de UI por idioma, incluindo rótulos dinâmicos, prévia e painel. Nomes de músicas e telas do sistema Android pertencem ao conteúdo/aparelho.

## Logs e correções da beta
Revistos os 18 relatórios fornecidos, os 106 registros da conversa exportada e suas nove imagens incorporadas. O vídeo referenciado pela sessão Telegram não estava incorporado e não foi assistido. Não houve envio automático dos arquivos nem contato com o desenvolvedor.
Os registros antigos mostram versões e falhas diferentes: ausência de injeção no SystemUI, falta de evidência em exportações, receptores de desbloqueio anteriormente corrigidos, estados de NFC/alarme não confirmados e crescimento do anel na tela de bloqueio. Relatórios sem captura suficiente não permitem afirmar a causa exata em todas as ROMs.
A base beta traz melhorias de diagnóstico, tratamento de DND, captura e anexação. Nossa implementação completa foi incorporada à base, com estado de NFC/alarme falhando como inativo quando não pode ser confirmado e tamanho de bloqueio usando a referência capturada da barra.
Todo relatório agora começa com os mesmos dados técnicos sem privilégios: fabricante, marca, modelo, produto/dispositivo, Android, SDK, patch, ROM, kernel, ABI, SELinux, tela/densidade, orientação e versão do módulo. Há buffer local de 600 linhas do aplicativo e buffer do módulo enviado pelo canal existente. Uma falha de root/Shizuku não impede salvar o relatório.
Capturas opcionais com privilégio ficam restritas ao próprio módulo. Não são coletados IMEI, serial, Android ID, nomes de rede, endereços, coordenadas, contas, conteúdo de notificações, fotos ou inventário de outros módulos. Filtro adicional remove identificadores, URLs e caminhos pessoais de mensagens e legendas de envio. O kernel inclui apenas release, sem hostname.
Se o LSPosed não carregar o módulo, o app registra essa ausência e seus dados técnicos; não pode reconstruir um log do SystemUI que nunca foi produzido. Logs globais protegidos pelo Android continuam sujeitos aos privilégios do sistema.

## Validação realizada
- 237 testes locais; zero falhas, erros ou ignorados. Incluem regressões anteriores, todos os 26 estados no carrossel, limites/tempo por ícone, fades sequenciais, idiomas, persistência em duas orientações, logs sem root, filtros de privacidade, botões e acessibilidade do painel.
- Renderização Canvas nativa dos ícones, porcentagens, bússola, carga e painel; telas Studio claras/escuras, idiomas Inglês/Espanhol e seletor inicial. Imagens inspecionadas no computador.
- Compilação, verificação de bytecode, assinatura v3 e alinhamento de 16 KB. Certificado compatível com nossas versões anteriores; nenhum segredo de assinatura no ZIP de fontes.
- Não houve instalação deste APK em um telefone neste ambiente. As integrações de mídia, lanterna, sensores e janelas SystemUI precisam de validação no aparelho; os testes locais não garantem compatibilidade perfeita em todas as ROMs. Funções indisponíveis são tratadas sem derrubar a barra.

Código correspondente GPL incluído no ZIP. As imagens de validação são capturas locais do código, não provas de execução no telefone.
