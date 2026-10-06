# Compilar a versão Canvas FX2

Este é o código correspondente ao APK 1.4.0-canvas-fx2, baseado no código GPL da versão oficial 1.4.0.
Use Android SDK 36 / Build Tools 36.0.0 e Java 17 ou 21. Configure o SDK no ambiente/local.properties.
Execute ./gradlew :app:testDebugUnitTest :app:assembleDebug. Os ícones são desenhos próprios, sem assets da Apple.
O diretório rive contém somente referência histórica do projeto original e não é usado pelo app Canvas.
FEATURE_MASK padrão: 16383; preferências novas: 16351 (modo de ocultar na câmera desligado para permitir indicador de privacidade).
O APK entregue foi compilado neste Windows pelo build-canvas.gradle, equivalente ao build.gradle.kts, usando um host Groovy auxiliar para contornar uma falha de javac/Path.toRealPath no sandbox; isso não muda o código Kotlin do módulo. O passo de assinatura usa uma chave local privada que não é distribuída. Para produzir outro APK assinado, use sua própria chave.
