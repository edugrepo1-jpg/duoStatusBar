# Compilar a edição Canvas Audit
Java 17/21, Android SDK 36, Build Tools 36.0.0, Gradle 8.13, AGP 8.13.2, Kotlin 2.1.0. Testes Robolectric SDK 35.
A entrada usada para a entrega é app/build-canvas.gradle. No settings.gradle.kts, depois de include(":app"), acrescente:
project(":app").buildFileName = "build-canvas.gradle"
Execute ./gradlew :app:testDebugUnitTest :app:assembleDebug (gradlew.bat no Windows).
Debug gera APK unsigned; assine com sua própria chave. A chave privada da entrega não é distribuída. O app/build.gradle.kts equivalente permanece como referência, mas a entrada acima é a testada.
O build neste host usou ECJ e recursos nativos do Robolectric extraídos por uma limitação local de filesystem/JDK. Em ambiente normal, Gradle/Android Studio executam os compiladores padrão e o Robolectric distribui suas dependências. Não é preciso instalar os workarounds deste computador no telefone.
Os fontes desta entrega não precisam de Rive. Arquivos históricos do upstream não integram o renderer do APK. Base upstream GPL: tag 22-1.4.2-beta.2, commit f3d3ac9.
