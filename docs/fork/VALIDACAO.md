# DUO Recreate — validação 1.4.3

Versão **1.4.3-canvas-recreate**, código **29**, pacote **io.github.RECREATE.statusbar**. Android 13+ / arm64. Base upstream beta 22 / f3d3ac9f4ac6f48dc71e0cee57b75648cd4ede9f.

APK SHA-256: `D2B01B4EAFA4293910335FF5D1B36C67EC440B424E665A47C619A1D10D4A41D7`
Certificado SHA-256: `e6aaa9c405ab2114e6aa59b1c6aec5e94bf942f98d09788180803d7e5acf2be8`

## Verificações executadas

- Compilação concluída e **329 testes passaram**, zero falhas, erros ou ignorados. Inclui 317 da entrega anterior, seis testes de descoberta/contrato dos painéis, uma integração de anexação/restauração do host e cinco de interação/galeria.
- Bytecode: `classes=387 methods=3479 failures=0`. Assinatura v3, alinhamento de 16 KB, pacote/versão/manifesto Xposed e ausência de runtime/assets Rive conferidos.
- **388 textos por idioma** (PT/EN/ES), mesmas chaves e placeholders; 152 recursos Android por idioma. Canal de preferências segue compatível com colunas antigas, limites e duas orientações.
- **97 capturas nativas** das interfaces em Android simulado/Robolectric SDK 35. Telas roláveis, temas, idiomas, switch, cartões, prévia e ilha. Há PNGs individuais e ZIP; hashes/procedência em docs/images/manifest.json.
- Observador de desbloqueio byte-idêntico à entrega funcional; as nove ideias e todas as classes correspondentes preservadas. Fallback de configurações conserva o último estado válido em indisponibilidade transitória. O teste de processo novo agora isola o cache preenchido por outros testes.

## Mudanças desta entrega

Switch com trilho cinza/azul e polegar preto; cartão permanece aberto e informa o estado atual. Fechar fica acessível com explicação rolável. Opções têm ações explícitas Configurar/Abrir, sliders com alvos de 48 dp. Popups declaram sua própria abertura; blur da tela de fundo desaparece ao fechar o último popup.

Em **Visual → Painéis do sistema**, notificações e ajustes rápidos têm habilitação e tamanho independentes (50–200%). O limite físico do cabeçalho protege contra recortes; nenhuma centralização usa a altura da tela inteira. Cabeçalhos compartilhados escolhem o ajuste conforme a expansão. Remontagem remove slots antigos, sem anexação duplicada.

Os indicadores substituídos (bateria/Wi-Fi/rede móvel, inclusive CombinedStatusView) só são removidos após o Canvas ficar pronto, por um responsável separado em cada painel. Falha/desativação/desmontagem restaura apenas os originais daquele slot. Shizuku mantém a blacklist opcional wifi,mobile,battery, preservando outras entradas. A remoção local contorna cabeçalhos que ignorem a blacklist. Se a ROM aplicar a blacklist global mesmo ao painel desativado, desative também a opção adicional Shizuku para recuperar os originais. Não há novo polling ou wakelock.

README em três idiomas dividido em páginas curtas, galeria organizada e instruções de instalação/migração. Os detalhes e o histórico integral continuam em docs/fork. Versão maior e mesma assinatura permitem atualização do fork.

## Instalação e confirmação no aparelho

Atualize o APK no app DUO Recreate existente, mantenha apenas este módulo habilitado no LSPosed (escopo System UI) e reinicie a interface do sistema ou o aparelho. Ative os painéis em Visual e ajuste seus tamanhos. Para migrar do pacote Canvas antigo, conserve-o até a primeira abertura para importar preferências, depois desative sua injeção.

**Não houve teste físico no Samsung**, trace GPU ou medição de autonomia. A descoberta OEM tem testes sintéticos, mas nomes/classes específicos da sua One UI precisam de confirmação. Um cabeçalho não reconhecido mantém os originais e precisa de diagnóstico com o painel aberto. Root não é exigido para exportar os logs do módulo/app; o ambiente LSPosed segue necessário. Telemetria observa energia do dispositivo inteiro, não atribui mAh ao módulo. Recursos de sensores/privacidade/mídia dependem das APIs e da ROM.

Não são publicados logs brutos, conversas, fotos do dispositivo, SSID, conteúdo de notificações ou chaves privadas. Histórico v28 preservado em historico/DUO-Recreate-1.4.2-validacao.md; esta execução prevalece para a edição atual.
