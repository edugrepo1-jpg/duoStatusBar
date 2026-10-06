# Duo Status Bar 1.4.0-canvas-fx4

APK corrigido a partir da FX3, preservando seus ajustes de interface e posição. Versão 21, Android 13 ou superior.
SHA-256: `07366034D331BDCFAB575A1C6CCF28D78C32EE055F01971F2088D3D3CA469719`

## Correções desta versão

- Todos os indicadores detectados ativos participam da alternância. Mudanças na lista preservam o tempo do ícone atual; a atualização não reinicia a sequência continuamente.
- Bluetooth e GPS entram junto de avião, lanterna, carga e demais indicadores. Localização considera também o botão do sistema ligado, sem solicitar coordenadas. O receptor de Bluetooth recebe eventos de seu processo privilegiado, separado dos eventos privados do SystemUI. Isso segue a [documentação de broadcasts do Android](https://developer.android.com/develop/background-work/background-tasks/broadcasts).
- O raio de carga é um integrante da lista; não impede outros ícones. Há brilho inicial por 3 segundos ao conectar qualquer carregador, mesmo sem leitura de potência, e feixe contínuo mais visível sobre os arcos existentes durante a carga.
- Check por 3 segundos incluindo fade out de 280 ms. É exclusivo: áudio, carga, câmera e bolso não o encobrem. Depois entra o próximo indicador com fade in, sem sobreposição. O tempo da alternância é preservado durante o check.
- O evento do sistema que confirma desbloqueio aciona o check após biometria ou PIN. Removidas a restrição de 3,5 segundos após acender a tela e a dependência do gancho biométrico opcional da ROM.
- Sensor de bolso precisa ficar estável por 500 ms antes de pausar, corrigindo oscilações rápidas observadas no log.
- Diagnóstico registra a lista de ícones ativos e as trocas, para distinguir estado não detectado de problema de animação. Eventos ao vivo continuam ocultos na interface; exportação manual permanece disponível.

## Validação realizada

- Compilação concluída; 159 testes, zero falhas, erros ou testes ignorados.
- Testes com todos os 21 estados em múltiplas voltas, lista variando, carga sem potência informada, desbloqueio com outros estados ativos, GPS ligado e ruído de proximidade.
- Assinatura e alinhamento de 16 KB verificados; APK sem arquivos ou runtime Rive.
- Renderização de 48 estados e animação de 307 quadros do desenho real, inspecionadas no computador. Fonte e gradiente do computador são aproximações; não representam validação do compositor do Samsung.

O relatório recebido mostra Canvas anexado na FX3 e registra brilho não disparado por potência não confirmada, além de pausas rápidas do sensor. A ausência de GPS/Bluetooth não era explicada pelo relatório anterior; agora os estados detectados e as trocas ficam registrados.

Não houve instalação desta versão no Samsung. Testes locais e correções baseadas no log não garantem ausência de todos os problemas em aparelho; indicadores continuam sujeitos às permissões e leituras reais do SystemUI.

## Atualizar

Instale por cima da FX3 e reinicie SystemUI ou o aparelho para carregar o módulo novo. Mesma chave local, preservando as configurações. Os efeitos obedecem aos interruptores de animação e aos recursos ativados no app.

Código GPL correspondente no ZIP; chaves e senhas não são distribuídas. Ícones próprios, sem assets SF Symbols da Apple.
