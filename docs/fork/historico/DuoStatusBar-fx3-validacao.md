# Duo Status Bar 1.4.0-canvas-fx3

APK local corrigido da versão Canvas FX2, mantendo os ícones e efeitos integrados. Versão 20, Android 13 ou superior.
SHA-256: `1A3D7282309903B3B9FAFD41347BB4F9EF12DF86EA9BBF1C486433A138C5499A`

## Correções baseadas nos logs fornecidos

- O módulo atua no processo principal do SystemUI. A iluminação lateral da Samsung não substitui mais o diagnóstico por “renderer=none”.
- A posição horizontal acompanha o ajuste manual. O recorte da câmera não puxa mais o anel para o centro.
- O tamanho considera toda a área do desenho, incluindo a porcentagem. O deslocamento fica limitado à área medida da barra.
- Barras secundárias usam sua própria posição na janela, evitando o centro da tela inteira.
- A animação de expansão limpa o deslocamento ao terminar, ao ser interrompida ou ao reaplicar configurações.
- O conjunto de indicadores separado acompanha mudanças no tamanho do anel.
- Altura da porcentagem, tamanho, escala, grossura e espaçamento usam um valor local durante o arraste e salvam ao soltar. O valor salvo não puxa o controle para trás durante o gesto.
- O editor de posição usa arraste horizontal e o retorno atualizado, salvando ao soltar. Rolagem vertical continua disponível.
- Eventos ao vivo removidos da interface. A coleta de diagnóstico e a exportação manual continuam disponíveis.
- Pequenas prévias dos controles ficam estáticas; a demonstração completa de efeitos continua disponível.
- Interruptores respondem ao toque na linha. Mudanças de layout não recriam o detector de gestos da barra.
- Perfis de ROM são lidos dos recursos do módulo, corrigindo a procura por rom-profiles.json nos recursos do SystemUI.

Os dois relatórios são de SM-S947B / r8s / Android 16, densidade 2,8125. Mostram janela principal 1080×89 e desenho anterior 89×100, maior que a altura disponível. Registram também o processo secundário com.android.systemui:edgelighting sobrescrevendo o status. A evidência e os casos de regressão constam no código correspondente.

## Validação

- Compilação concluída; 152 testes, zero falhas, erros ou testes ignorados.
- Casos de regressão: limites da janela, origem de barras secundárias, tamanho salvo de 314%, estabilidade durante arraste, extremos dos controles, processo secundário e preservação do detector de toque.
- Assinatura e alinhamento de 16 KB verificados. APK sem runtime ou arquivos Rive.
- Testes anteriores de efeitos e geometria continuam passando.

Não houve instalação e teste desta versão no Samsung. Os logs permitiram corrigir problemas concretos, mas a validação local não garante ausência de todos os bugs no aparelho. Leitura de sensores, privacidade, Bluetooth e gravação mantém os limites documentados na FX2.

## Atualizar

Este APK usa a mesma chave local da FX2. Instale por cima da versão Canvas anterior para preservar as configurações; depois reinicie SystemUI ou o aparelho para carregar o novo módulo. Não é necessário ativar o módulo no processo de iluminação lateral.

Configurações existentes são preservadas. Tamanhos e deslocamentos muito grandes são limitados ao espaço físico da barra. Os ajustes são aplicados depois de soltar o controle quando “aplicação ao vivo” está habilitada.

Código sob GPL do projeto original. Os ícones são desenhos próprios, sem assets SF Symbols da Apple. Chaves e senhas não estão no ZIP.
