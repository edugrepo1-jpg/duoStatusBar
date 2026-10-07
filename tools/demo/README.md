# Reproduzir a demonstração

O anel usa `DuoCanvasView`, `SlotCycle` e `EffectTimeline` de produção. A moldura e as telas do sistema são ilustrações Canvas; não são captura de dispositivo ou prova do encaixe na One UI. Os estados, a data e o horário são simulados.

Copie `StatusBarDemoTest.kt` temporariamente para `app/src/test/java/io/github/kvmy666/duostatusbar/fx/` e execute:

```sh
./gradlew :app:testDebugUnitTest --tests io.github.kvmy666.duostatusbar.fx.StatusBarDemoTest
```

A saída fica em `app/build/statusbar-demo`: 220 quadros PNG, separados por 100 ms, e cinco imagens estáticas. Remova a cópia temporária depois da exportação. Codifique os quadros em GIF com 100 ms por quadro e repetição contínua. A demonstração não altera o APK nem os 329 testes de validação da release.
