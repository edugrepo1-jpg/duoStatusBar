# Duo Status Bar 1.4.0-canvas-fx5

Versão 22, Android 13 ou superior. Correção da inicialização da FX4; demais recursos Canvas preservados.
SHA-256: `6F45F4157FDC21B611578D2627563E511039CEBDECF9C52765AB516B36451CCF`

## Causa confirmada nos três logs

Os logs 014014, 014101 e 014134 mostram `monitor start failed: IllegalStateException: Receiver ... registered with differing handler`. A FX4 registrava o mesmo receptor com o agendador padrão do Android e, depois, com outro agendador para Bluetooth. A segunda chamada interrompia a inicialização antes de iniciar o controlador dos efeitos. O log 014134 confirma carregamento detectado, mas o controlador permanecia parado; isso impedia o raio, o brilho de carga e o check de desbloqueio.

## Correção

- Bluetooth usa uma instância própria do receptor, com a permissão e o agendador correspondentes. Eventos de bateria, tela e desbloqueio mantêm o receptor principal.
- Falha no registro opcional de Bluetooth é isolada: não interrompe carga, desbloqueio ou o restante dos efeitos. As leituras periódicas de Bluetooth continuam disponíveis quando permitidas.
- O monitor só marca o receptor principal como registrado após sucesso. Falha na inicialização limpa receptores, efeitos e tarefas pendentes, permitindo nova tentativa.
- Encerramento remove os dois receptores e evita eventos atrasados depois de parar.
- Mantidos: raio na alternância junto dos demais estados ativos, brilho inicial de carga por 3 segundos e feixe contínuo nos arcos; check exclusivo por 3 segundos com fade out, seguido pela entrada do próximo ícone.

## Validação

- 163 testes locais; zero falhas, erros ou testes ignorados. Novos testes exercitam `DuoStateMonitor.start()` com o mesmo contrato de registro observado no Samsung, bateria já carregando, eventos de tela/desbloqueio, Bluetooth negado, falha inicial seguida de nova tentativa e encerramento/reinício.
- Os quatro testes novos falharam com o código da FX4 antes da correção, reproduzindo a regressão.
- Compilação, assinatura e alinhamento de 16 KB verificados. APK sem Rive.
- O desenho e os tempos dos efeitos não foram alterados nesta versão; permanecem cobertos pelos testes existentes de Canvas, geometria e alternância.

Não houve instalação desta versão no Samsung neste ambiente. A causa foi confirmada nos logs reais e a correção foi testada localmente; o resultado final no aparelho ainda precisa ser confirmado após atualização.

## Instalar

Atualize por cima da FX4 e reinicie SystemUI ou o aparelho para substituir o módulo já carregado. Mesma assinatura das versões Canvas anteriores, preservando configurações. Os efeitos obedecem aos recursos e animações ativados no app.

Código GPL correspondente no ZIP; chaves e senhas não são distribuídas. Ícones próprios, sem assets SF Symbols da Apple.
