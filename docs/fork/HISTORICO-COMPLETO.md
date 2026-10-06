# DUO Recreate — histórico e contrato de preservação

Fork independente de Duo Status Bar, de kvmy666, sob GPL-3.0. Repositório: https://github.com/edugrepo1-jpg/duoStatusBar. Pacote Android: io.github.RECREATE.statusbar. O namespace Kotlin original fica como procedência; não é o identificador instalado. Base beta oficial 22-1.4.2-beta.2 / f3d3ac9f4ac6f48dc71e0cee57b75648cd4ede9f.

## Procedência do histórico

O Git upstream foi obtido com seu histórico intacto. Nove entregas possuem ZIP de fontes recuperável; cada uma ganhou um commit de reconstrução, com hash do ZIP. Esses commits foram criados agora: não são commits antigos reencontrados, e não inventam datas/autores de execução. A edição inicial fx1 tem APK mas não um ZIP completo de fontes separado; suas funções e correções estão presentes nos snapshots seguintes. Não há como demonstrar uma árvore fonte exata da primeira entrega a partir apenas de memória. Os textos colados, HTML de conversa e diagnósticos orientaram os requisitos, mas não foram publicados como dados pessoais.

## Tudo preservado e ampliado

| Área | Contrato atual e evidência |
|---|---|
| Desenho | Android Canvas, anel original, porcentagem centralizada no vão, quatro pontos de sinal, versões sem número/separadas, proporções e limites de altura/escala/posição. Sem runtime/asset Rive no APK. |
| Indicadores | Wi-Fi; rede móvel 2G/3G/4G/5G; avião; DND; Bluetooth; NFC; hotspot; fones; check; raio; câmera; microfone; alarme; VPN; GPS; silencioso/vibração; mídia; indução; lanterna; gravação; Wi-Fi offline. As 20 categorias aprovadas incluem variantes próprias. Câmera/microfone são separados. |
| Fones e offline | Silhueta própria de earbuds, bateria conhecida acima de 15% verde e ≤15% vermelha, sob ícone/acima dos pontos. Desconhecida fica sem número. Wi-Fi offline tem ! grande vermelho central, apagando a parte de trás. |
| Trocas e prioridade | Saída termina antes da entrada; nenhum Wi-Fi atrás do check. Carrossel inclui estados secundários ativos, 1–60 s universal ou individual. Música/microfone/gravação: evento observado mais recente fica até terminar; pausa remove mídia. Empate inicial sem cronologia real é determinístico. Check exclusivo 3 s, sem ser derrotado por outro evento. |
| Efeitos | Entrada/saída; brilho de carga inicial 3 s e movimento moderado dos arcos; carga rápida/indução; pulso crítico; traços desenhados; vidro; curvas de expansão; suspensão em tela apagada/bolso; ocultação opcional na câmera. |
| Nove ideias aprovadas | Anel musical/progresso/cores da capa; previsão de carga; prévia interativa; captura/obturador; editor de ritmo; volume; tempo da gravação; ilha ao segurar; desenho dos traços. |
| Bússola e escala | Seta acompanha norte magnético quando efetivamente exibida, sensor encerrado ao ocultar, limite 10 Hz, tentativa frustrada com espera 30 s. Ícones internos até 200%; traço 1–300%. |
| Ilha | Abre pílula 232×64 dp, música/estado/controle compacto; arrastar para baixo expande resumo e controles, para cima no cabeçalho recolhe. Janela compacta é menor de verdade. Conteúdo grande rola dentro dos limites da tela. |
| Interface | Quatro áreas, busca, prévia, sliders com confirmação ao soltar, explicação em cartão preto arredondado com ícone e controle próprio, fundo desfocado enquanto aberto, voltar não muda função. Tema sistema/claro/escuro com mudança imediata. |
| Idiomas | Escolha inicial com bandeiras; português, inglês e espanhol. Catálogos e placeholders verificados em conjunto. |
| Relatórios | Marca/modelo/Android/patch/kernel/ABI/ROM/tela/orientação/versão, status de anexação, estados e falhas reais, contadores de desenho/consultas/cache/sensores/ciclos, tempo visível/pausado, temperatura/tensão e variação observada da bateria. Sem exigir root concedido ao app. |
| Privacidade | Sem áudio/imagens/conteúdo de notificações, coordenadas, SSID, MAC, IMEI/serial/tokens. Exportação/share local sanitizados e limitados; nenhum envio automático ao autor original. O texto livre informado pelo usuário requer revisão antes de publicar. |
| Identidade | Pacote, authorities, ações/permission e marca próprios; updates só do fork. Download exige URL do fork, SHA-256, pacote/certificado instalado e código maior. Apoio/contato antigos removidos da UI. Crédito original/licença preservados. |
| Migração | Se app Canvas anterior ainda instalado e assinado com certificado esperado, importa a configuração das duas orientações uma vez. Não importa root/permissões/logs. Não sobrescreve ajustes novos. Desative módulo antigo no LSPosed antes de ativar DUO Recreate para evitar duas injeções. |

## Snapshots recuperados no GitHub

- 1.4.0-canvas-fx2: commit GitHub `a78531ae97a754f2c1d1d4b751349ec276e8e151`, ZIP SHA-256 `6f234f75203ae29c22394f6988040714dd3f0180f241b1ae0099bec0b7b89781`; 192 arquivos recuperados.
- 1.4.0-canvas-fx3: commit GitHub `638b1a96106648799cddce5d3f2c27036602758b`, ZIP SHA-256 `cc994817c46d2a88e9b1a6d4c0a34526d03479ef3715ebb336d00e0dbe4efd71`; 198 arquivos recuperados.
- 1.4.0-canvas-fx4: commit GitHub `bc0629a1405371c35d321a6f46726fb7199c13dd`, ZIP SHA-256 `2c8ed62c3c2ff528b9e29bccfc5a528891660b4dad3eff0ef925d2deb1df546d`; 201 arquivos recuperados.
- 1.4.0-canvas-fx5: commit GitHub `b20bd8acfa6cee5d07e039cc4209edf69a505de7`, ZIP SHA-256 `120acfd04708c18d06799e665b89a163db50f186bad7290e8c0ff74ab7ff0490`; 203 arquivos recuperados.
- 1.4.0-canvas-studio: commit GitHub `8bdbf47f6f99a7164b8207d30196a7b22aa33fed`, ZIP SHA-256 `d30381f485a601f190665292de06c0befeef29c25207b2d985f34a3f829a487f`; 209 arquivos recuperados.
- 1.4.0-canvas-studio-fx1: commit GitHub `7555683bfe0665291a9780dc2d43ca62bd1143ae`, ZIP SHA-256 `acfdd8a4b4d5d729a714858617b3909fbe90d95a19710dfea1ec1c32eef0bbac`; 211 arquivos recuperados.
- 1.4.0-canvas-experience: commit GitHub `e06c871206325fb9b51b22000354bcfa7133ad53`, ZIP SHA-256 `f48e96e1eaa5f1afc921991d9ba1ac41c541b55864f74c5112bbb6c09362a714`; 221 arquivos recuperados.
- 1.4.2-canvas-studio: commit GitHub `91f0f4cccb3acde16839c7b226b38ae79d652b21`, ZIP SHA-256 `a3e610e6021097cc31eb2a48d569550a52b67ec0200f08a8c2f9dc9668a3a08f`; 237 arquivos recuperados.
- 1.4.2-canvas-audit: commit GitHub `624396fed7090ab80d117d8693abc9b19cd0e17b`, ZIP SHA-256 `3c56ac6861513530f76556c41295ae9ca8b94c08ae9c39857e8114bbd430421c`; 251 arquivos recuperados.

## Evidência histórica de entregas

Os relatórios preservados abaixo são históricos, não prova de que o novo APK foi executado no telefone. Contagens/limitações da versão correspondente permanecem identificadas.

Os SHAs locais de reconstrução ficam em snapshots.json; o mapeamento das mesmas árvores para os commits do GitHub fica em remote-snapshots.json. Todos os nove tree SHAs remotos foram comparados exatamente aos locais antes da publicação.

- [DuoStatusBar-1.4.2-Audit-validacao](historico/DuoStatusBar-1.4.2-Audit-validacao.md)
- [DuoStatusBar-1.4.2-auditoria-completa](historico/DuoStatusBar-1.4.2-auditoria-completa.md)
- [DuoStatusBar-1.4.2-Studio-validacao](historico/DuoStatusBar-1.4.2-Studio-validacao.md)
- [DuoStatusBar-Experience-validacao](historico/DuoStatusBar-Experience-validacao.md)
- [DuoStatusBar-fx2-validacao](historico/DuoStatusBar-fx2-validacao.md)
- [DuoStatusBar-fx3-validacao](historico/DuoStatusBar-fx3-validacao.md)
- [DuoStatusBar-fx4-validacao](historico/DuoStatusBar-fx4-validacao.md)
- [DuoStatusBar-fx5-validacao](historico/DuoStatusBar-fx5-validacao.md)
- [DuoStatusBar-Studio-FX1-validacao](historico/DuoStatusBar-Studio-FX1-validacao.md)
- [DuoStatusBar-Studio-validacao](historico/DuoStatusBar-Studio-validacao.md)
