# Reproduzir a demonstração 1.4.4

O anel é o Canvas de produção. A moldura e o sistema ao redor são ilustrações; não são uma gravação Samsung. Ícones das telas ilustrativas são vetores originais para Fotos, Música, Câmera, Ajustes, Mapas, Notas, Agenda e Duo, sem emoji ou símbolos de fonte reaproveitados.

Copie `StatusBarDemoTest.kt` para o diretório de testes do pacote `fx` e execute `:app:testDebugUnitTest --tests io.github.kvmy666.duostatusbar.fx.StatusBarDemoTest`. A exportação produz 500 quadros separados por 100 ms em `app/build/statusbar-demo` e cinco imagens estáticas. O GIF dura 50 segundos. Remova a cópia temporária depois de exportar.

A sequência mostra todos os 26 ícones do carrossel com saída e entrada sequenciais, check, carga, mídia, volume, gravação, obturador, ilha compacta e painéis ilustrativos. Não demonstra anexação na ROM nem comprova consumo de bateria no aparelho.
