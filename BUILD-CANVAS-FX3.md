# Compilar Canvas FX3

Código correspondente ao APK 1.4.0-canvas-fx3, baseado na versão oficial GPL 1.4.0.
SDK 36 / Build Tools 36.0.0, Java 17 ou 21. Configure o SDK em local.properties ou no ambiente.
Execute ./gradlew :app:testDebugUnitTest :app:assembleDebug.
No Windows deste workspace foi usado o build-canvas.gradle equivalente com host Groovy e ECJ para contornar restrições de javac do ambiente. As fontes Kotlin são as mesmas.
O diretório rive é referência histórica; o app é Canvas. Assine com sua própria chave; a chave local de entrega não é distribuída.
