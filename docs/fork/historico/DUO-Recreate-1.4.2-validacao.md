# DUO Recreate — validação final

Versão `1.4.2-canvas-recreate`, código **28**, pacote **io.github.RECREATE.statusbar**. Android 13+ / arm64. Base beta upstream 22-1.4.2-beta.2, commit f3d3ac9f4ac6f48dc71e0cee57b75648cd4ede9f. Repositório: https://github.com/edugrepo1-jpg/duoStatusBar.

APK SHA-256: `77F5C58B9DD6FB8C671C68088492D6DB925FF901C06EC3A580D53CC2CB169911`
Certificado SHA-256: `e6aaa9c405ab2114e6aa59b1c6aec5e94bf942f98d09788180803d7e5acf2be8`

## Resultado executado

**317 testes passaram**, zero falhas, erros ou ignorados. São os 284 testes da entrega Audit anterior mais 33: telemetria/limites (15), runtime/bateria (5), identidade/migração/tema/update (4), ilha compacta (7), autenticação do relatório (2). Compilação concluída. Bytecode: `classes=379 methods=3403 failures=0`. Assinatura v3 e alinhamento de 16 KB conferidos. Manifesto, pacote, componentes Xposed, versão e ausência de assets/runtime Rive conferidos.

Capturas nativas de temas, navegação, cartão da função, ilha compacta e expandida foram renderizadas e inspecionadas no computador. Testes simulam Android 35; aparelhos Android 33 ou ROMs OEM não foram executados nesta entrega. O primeiro ensaio específico SDK 33 dependia de um runtime não disponível offline; a validação reproduzível do canal usa o SDK 35 instalado, e a implementação não depende de APIs de identificação de remetente introduzidas no 34.

Catálogos: **373 textos por idioma**, 152 recursos Android por idioma, português/inglês/espanhol. Chaves e placeholders conferidos. Conteúdo do player e telas nativas seguem seu próprio idioma. Observador de desbloqueio preservado byte a byte da entrega funcional; todas as classes das nove ideias continuam. O Runnable de polling musical substituído por callbacks não representa remoção da função.

## Principais correções e mudanças

- Ilha abre como pílula 232×64 dp; a própria janela encolhe. Arrastar para baixo expande, para cima no cabeçalho recolhe; CANCEL e múltiplos dedos não acionam botões. Controles do player seguem a origem da música exibida.
- Cartão preto pequeno, ícone próprio e controle de ativar/desativar, explicação e informação útil; fundo desfocado durante sua abertura. Tema sistema/claro/escuro salva e muda imediatamente.
- Bateria e trabalho observados, estados de funções habilitadas/detectadas/executadas/falhas/desconhecidas, limites dos buffers e retry de sensor 30 s após falha, sem timer ou wakelock novo para telemetria.
- Heartbeat musical ancorado no último envio real, cache de metadados, bloqueio de callbacks tardios após desligar mídia, reativação imediata e recuperação do baseline de capturas.
- Pedido de diagnóstico incluído no filtro; settings/reinício/experiência protegidos por assinatura. Retorno alternativo de logs autenticado por challenge privado, com fila limitada e sem expor token.
- Novo pacote, autoridades, ações e marca. Atualizações só do fork, downloads limitados e validação de hash, pacote, certificado instalado e código maior. Contato/apoio/envio automático ao dev antigo removidos; GPL e crédito preservados.
- Importação única das preferências do Canvas anterior assinado, nas duas orientações, sem apagar ajustes novos nem importar root/logs/permissões.

## Instalar

1. Mantenha nosso app Canvas anterior instalado até abrir o novo app pela primeira vez, para permitir importação dos ajustes. Somente as nossas edições assinadas qualificam.
2. Instale DUO Recreate. Escolha seu idioma. Como o pacote mudou, ele aparece como outro app.
3. No LSPosed, **desative o módulo antigo**, ative **DUO Recreate** com escopo **System UI** e reinicie o telefone. Não deixe as duas injeções habilitadas juntas.
4. Abra a prévia, confira preferências e habilite a personalização. Autorize acesso a notificações/imagens apenas quando precisar de mídia/captura; grants do app antigo não migram.

Root não é necessário para exportar os diagnósticos do app/módulo. O ambiente LSPosed continua necessário para injetar a barra.

## Limites

Não houve instalação neste telefone, trace OEM nem medição física de autonomia. Não há promessa de funcionamento perfeito. APIs de mídia ainda podem envolver Binder síncrono ocasional; detecção de projeção/indicadores depende do Android/ROM. GPU blur e sensores exigem confirmação no aparelho. Telemetria descreve energia observada do dispositivo inteiro; não calcula mAh ou economia atribuída ao módulo. Snapshot inicial sem cronologia usa desempate determinístico para eventos simultâneos.

O histórico Git preserva upstream e nove reconstruções identificadas. Relatórios originais (104 arquivos, 95 únicos), conversas, fotos e chaves privadas não são publicados. Fonte GPL, testes, auditoria e procedência estão incluídos no ZIP; certificado público serve para conferência, chave privada não é distribuída. Relatórios históricos têm conclusões referentes à edição da época; as correções atuais e esta execução final prevalecem.
