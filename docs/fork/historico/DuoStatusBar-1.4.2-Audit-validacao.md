# Duo Status Bar — Canvas Audit

Versão 1.4.2-canvas-audit, código 27. Android 13 ou superior. Base beta oficial 22-1.4.2-beta.2, commit f3d3ac9; continuidade completa da edição Canvas Studio anterior.
SHA-256: `19B037E1AA788ECFD0BCC9F5AECEA64C64B235E74DE1F6036712C488FCF9189E`

## Como instalar
Instale sobre nossa edição Canvas anterior; o certificado é o mesmo. Preserve o escopo SystemUI no LSPosed e reinicie o telefone para carregar o código novo. Não é necessário desinstalar para atualizar entre nossas edições.

## O que mudou
- Interface inspirada no iOS: paleta azul e superfícies neutras, agrupamento, textos mais diretos e hierarquia clara. Toque numa função abre explicação; Ativar/Desativar confirma a escolha e Voltar conserva o estado.
- Ilha ao segurar: expansão/recolhimento suaves, música atualizada enquanto reproduz, alvos de 48 dp, transportes também no modo compacto, rolagem e ações acessíveis, ícones corrigidos e tipos 4G/5G reais.
- Evento mais recente de música, microfone ou gravação mantém o centro até parar; pausa remove música. Os demais continuam na ilha. Check exclusivo dura 3 s. Com Tempo de gravação ligado, ponto e cronômetro alternam dentro do evento, sem liberar o centro para Wi-Fi.
- Tempo único de 1–60 s para estados secundários, compartilhado nas duas orientações sem apagar tempos individuais nem outros ajustes; fades de entrada/saída separados. Tempos próprios só aparecem onde atuam; eventos persistentes e avisos breves têm explicação própria.
- Menos consultas repetidas e trabalho invisível: inventário em worker/cache, relay por callbacks, bússola apenas quando exibida, encerramento dos relógios da ilha em pausa/fechamento e intervalos de render conforme o efeito.
- Relatórios menores antes do envio entre processos, proteção de dados nas fronteiras, arquivos únicos, timeout de processos corrigido e filtro de privacidade sem complexidade quadrática em linhas longas. Dados técnicos do aparelho continuam disponíveis sem conceder root ao app.
- Downloads únicos, SHA obrigatório, cancelamento correto e explicação de que lançamentos oficiais têm assinatura/canal diferentes desta edição Canvas.

## Preservação
Renderer e ícones Android Canvas; zero assets/runtime Rive no APK. Todos os indicadores e as nove ideias aprovadas continuam, incluindo porcentagem dos fones, exclamação vermelha do Wi-Fi sem internet, GPS com bússola, escala interna até 200%, efeitos de carga/bateria/desbloqueio, música, previsão, prévia, captura, volume, gravação, ilha, traços, geometria e gestos.
Observador de desbloqueio byte a byte igual ao já funcional. A remoção de um antigo Runnable de polling substituído por callbacks é uma otimização de implementação, não remoção do recurso de mídia.
Português, inglês e espanhol: 152 recursos Android e 361 textos de catálogo por idioma; chaves e placeholders conferidos. Conteúdo de músicas e telas nativas do Android seguem o próprio aparelho/player.

## Validação
284 testes executados, zero falhas, erros ou ignorados: os 237 anteriores mais 47 novos. Incluem prioridades, pausa/retomada, cancelamento, lifecycle, gestos, acesso, idiomas, telas, pixels, cache, temporizadores, limites, exportação e linha grande do filtro de privacidade com guarda de processamento menor que 2 s.
Bytecode: classes=352 methods=3155 failures=0. Compilação concluída; assinatura v3 e alinhamento de 16 KB conferidos. APK código 27/versão audit, certificado compatível com edições Canvas anteriores. Capturas nativas da UI, janela de explicação e ilha inspecionadas no computador.

## O que ainda exige aparelho
Não houve instalação no telefone neste ambiente nem medição física de autonomia. Não prometo funcionamento perfeito de todas as APIs/ROMs. Carga ambiental continua animando enquanto visível (intervalo 33 ms); economia em mAh não foi medida. Mídia/lanterna/sensores e latência de serviços OEM precisam confirmação no aparelho. Relatórios atuais não contêm ANR formal de música; as causas de congelamento corrigidas foram reproduzidas no código e nos testes.
Há limites explícitos na auditoria completa: detecção de projeção, snapshots iniciais simultâneos, dependência de MediaSession/OEM, autenticação da bridge legada no Android 33 e privacidade de descrições livres digitadas pelo usuário.

O ZIP de fontes inclui GPL, código correspondente, testes, instruções de build e auditoria; não inclui chaves de assinatura nem logs pessoais fornecidos.
