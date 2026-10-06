# Duo Status Bar — Canvas Studio
Versão 1.4.0-canvas-studio, código 23. Android 13 ou superior.
SHA-256: `08B29A2CE4879298275E4B2D89301D6DF1BE009BD751E6828F429AF0A0F066DE`

## Nova interface
- Identidade azul, grafite e verde menta, títulos grandes e cartões arredondados. Inspirada na organização do iOS, na leitura da One UI e nos atalhos de HyperOS, com desenho próprio.
- Quatro áreas na navegação inferior: Início, Visual, Efeitos e Ajustes. Voltar retorna à tela inicial ou limpa a busca. Teclado e barras do sistema têm espaço reservado.
- Início com prévia estática do anel, interruptor principal, estado real de ativação e atalhos para personalizar.
- Tamanho, espessura, escala, porcentagem e posição organizados em grupos. Sliders preservam o rascunho durante o arraste e aplicam ao terminar; botões +/− permitem ajustes de uma unidade.
- Interruptores com área de toque ampla e uma única ação por toque. Seletores compactos com indicação da opção escolhida.
- Busca em todas as áreas; seleção de página é preservada em recriações da tela.
- Efeitos agrupados por ícones, energia e contexto. Demonstração animada disponível sob demanda; demais prévias estáticas.
- Ajuda, exportação de relatório, contato e atualizações acessíveis em Ajustes. Informações detalhadas do módulo podem ser expandidas; eventos ao vivo continuam ocultos.
- Modo claro/escuro automático com paletas próprias. Sem bibliotecas, fontes ou imagens pesadas novas.
- Ícone vetorial original, adaptável às máscaras dos launchers, incluindo versão monocromática para ícones temáticos do Android.

## Funções preservadas
As fontes de hook, efeitos e configurações são byte a byte iguais às da FX5. A alteração foi na interface e nos recursos do app. Permanecem a correção de registro de Bluetooth, raio e movimento de carga, check exclusivo de 3 segundos com fade out e alternância de estados ativos. Configurações salvas não são redefinidas.

## Verificação
- 166 testes locais, zero falhas, erros ou ignorados. Incluem navegação nas quatro áreas, claro/escuro, interruptor principal, busca de NFC a partir do Início, ajuste fino de tamanho e desenho do ícone adaptável.
- Telas reais de Compose e Canvas renderizadas no runtime Android de testes do computador; oito telas e o ícone inspecionados visualmente. Esse ambiente não reproduz o compositor nem os serviços reais do Samsung.
- Compilação, assinatura e alinhamento de 16 KB verificados. Mesmo certificado das versões Canvas anteriores; APK sem Rive.
- As limitações locais de acesso a ZipFileSystem do Windows foram contornadas no host de testes usando os mesmos recursos nativos de fontes extraídos do artefato já instalado. Esse ajuste não entra no APK.

Não houve instalação no aparelho neste ambiente. As funções do módulo dependem da ativação em LSPosed e dos estados/permissões reais do SystemUI.

## Atualizar
Instale por cima da FX5 (ou FX4) e reinicie SystemUI ou o aparelho para carregar o módulo atualizado. As preferências existentes são preservadas.
Código correspondente GPL no ZIP, sem chaves ou senhas.
