# Instalar DUO Recreate

[Português](INSTALL.pt-BR.md) · [English](INSTALL.en.md) · [Español](INSTALL.es.md) · [Inicio](README.es.md)

## 1. Comprueba los requisitos

- **Android 13 o superior**, dispositivo **arm64**, según esta distribución del APK.
- **LSPosed activo o un framework compatible con la API Xposed tradicional**, con soporte para tu versión de Android y ROM.
- Solo **un módulo Duo** habilitado en System UI. Desactiva el original u otro fork antes.

**El APK por sí solo no modifica la barra. Shizuku no sustituye LSPosed.** La integración con System UI necesita el framework. Sin él, puedes abrir los ajustes y la vista previa, pero no aparecerá el anillo inyectado.

## 2. Prepara el framework

Si LSPosed ya funciona, pasa al paso 3. Si no, sigue las instrucciones oficiales del framework y del gestor de root compatible con tu dispositivo. Normalmente se utiliza Magisk o KernelSU con la implementación Zygisk adecuada, se instala el ZIP desde el gestor de root y se reinicia. Comprueba que el gestor del framework indique **activo** antes de habilitar DUO.

El proyecto [LSPosed original](https://github.com/LSPosed/LSPosed) documenta soporte para Android 8.1–14. Para versiones posteriores, elige una implementación mantenida que indique soporte para tu Android, como [Vector](https://github.com/JingMatrix/Vector), compatible con la API tradicional. El soporte del framework no certifica la integración del DUO en tu ROM. Que el APK acepte Android 13+ no garantiza que un framework antiguo admita versiones posteriores.

El desbloqueo del bootloader y la instalación de root dependen del dispositivo; esta guía supone ese entorno preparado. DUO no realiza esos procedimientos. **Dar permiso de root al propio DUO es opcional:** Canvas y los diagnósticos básicos no lo necesitan. Es distinto del entorno requerido para instalar el framework.

## 3. Instala y activa

1. Abre [la última release del fork](https://github.com/edugrepo1-jpg/duoStatusBar/releases/latest). En **Assets**, descarga **DUO-Recreate-…apk**. Los archivos del código fuente no son instaladores.
2. Instala el APK encima del DUO Recreate existente con la misma firma para conservar los ajustes.
3. Abre la app y elige portugués, inglés o español.
4. En LSPosed/el gestor, abre **Módulos → DUO Recreate**, actívalo y selecciona **System UI / Interfaz del sistema (`com.android.systemui`)** como ámbito. No selecciones todas las apps ni el proceso `android`.
5. Reinicia el teléfono para que System UI cargue el módulo. Reabrir la app de ajustes no basta.
6. Abre **Inicio → Personalizar la barra → Configurar**, activa el interruptor y comprueba el estado del módulo en Inicio.

## 4. Personaliza

| Pestaña | Controles |
|---|---|
| **Visual** | Tamaño, posición, trazo, porcentaje, escala de iconos, colores de batería y encabezados. |
| **Efectos** | Estados, permanencia universal/individual, fades, carga, desbloqueo y vista previa. |
| **Ajustes** | Idioma, tema, accesos opcionales, Shizuku, actualizaciones y diagnósticos. |

En **Visual → Paneles del sistema**, activa notificaciones y ajustes rápidos por separado y ajusta sus tamaños. El espacio disponible limita el tamaño real. Los ajustes de orientación pueden ser compartidos o independientes; compruébalo antes de modificar la horizontal.

La vista previa utiliza estados simulados. Pulsa **Aplicar en la barra** para transferir los cambios. Música, capturas, ubicación y privacidad dependen de señales de Android y accesos opcionales. Concede únicamente los necesarios.

**Shizuku es opcional:** permite ocultar adicionalmente los iconos originales y otras acciones autorizadas. El módulo realiza la ocultación principal. La blacklist de Android es global: si desactivas DUO en una orientación o panel, desactiva también la ocultación adicional de Shizuku para no perder indicadores allí.

## 5. Actualiza o restaura

Instala el APK nuevo encima del fork y reinicia System UI o el teléfono. Paquete: `io.github.RECREATE.statusbar`. Otra clave de firma no puede actualizar esta distribución. La app original utiliza otro paquete/firma.

Antes de desinstalar, restaura los iconos originales mediante Shizuku si utilizaste esa opción. Desactiva DUO Recreate en el framework y reinicia. Ante una falla grave de System UI, sigue la recuperación/modo seguro documentado por tu gestor de root/framework; no existe una combinación universal de botones.

## Si no funciona

| Síntoma | Comprueba |
|---|---|
| App abre, anillo ausente | Framework activo, ámbito System UI, interruptor DUO y reinicio tras activarlo. |
| Dos anillos o iconos superpuestos | Otro módulo Duo habilitado; comprueba también la ocultación adicional. |
| Anillo ausente en un panel | Panel habilitado y ancla reconocida en la ROM. Un encabezado desconocido conserva los originales. |
| Horizontal diferente | Ajustes independientes por orientación y dimensiones del encabezado. |
| Música/batería del auricular ausente | Acceso opcional, reproducción activa y datos proporcionados por Android/accesorio. Una batería inválida mantiene la del teléfono. |

Abre la pantalla afectada y exporta **Ajustes → Diagnósticos**. No exige permiso de root en la app; distingue configuración, detección, ejecución y acceso no disponible. La vista previa no certifica la integración física en la ROM.

Las insignias del README consultan descargas de assets y la última release de **este fork**. No son instalaciones ni usuarios únicos. Android 13+ representa el mínimo del APK (`minSdk 33`), sin certificar todas las ROMs.
