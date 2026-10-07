# Validação — DUO Recreate 1.4.4

Código **30** · pacote **io.github.RECREATE.statusbar** · Android 13+ / arm64.

APK SHA-256: `D04D6B7BFF8B0EF5168EC69A77B00A94158E54DAB44AD12E6D92005E863AA284`

Certificado SHA-256: `e6aaa9c405ab2114e6aa59b1c6aec5e94bf942f98d09788180803d7e5acf2be8` — mesma chave do fork anterior.

## Verificações concluídas

- **348 testes**, zero falhas, erros ou ignorados; compilação concluída. [Resultados por suíte](evidence/validation-1.4.4.json).
- Canvas nativo e interfaces Compose em Android API 35 simulado. Testes de anexação horizontal reproduzem a árvore e as dimensões observadas no relatório recebido, sem publicar o relatório bruto.
- Bytecode Kotlin: `classes=412 methods=3635 failures=0`. Assinatura v2/v3 e alinhamento de 16 KB verificados; pacote, versão e entrada Xposed conferidos. Não há assets nem runtime Rive no APK.
- **415 textos por idioma**, português, inglês e espanhol, com as mesmas chaves e parâmetros.
- Observador de desbloqueio byte-idêntico à versão funcional anterior. As nove ideias anteriores, sensores, prévia, controles e funções continuam presentes.

## Resultado funcional desta revisão

O design anterior permanece. Visual reúne aparência, dimensões, posições, painéis, orientação e cores de bateria; Efeitos reúne ritmos, animações, música, gravação, eventos, ilha e prévia; Ajustes reúne permissões, contexto, Shizuku, gestos, tema, idioma, logs e atualizações.

Perfis vinculados por padrão mantêm configurações e efeitos ao girar. Separar os perfis é opcional, preservando o perfil horizontal antigo. Novas janelas recebem estado e gestos; cabeçalhos de notificações e ajustes rápidos usam a base de bateria e o limite do cabeçalho real, com tamanhos próprios.

Wi-Fi conectado e sem internet compartilham a mesma geometria. O aviso adiciona a barra diagonal vermelha e pulsa duas vezes por segundo; animações desligadas deixam-no imóvel. Fones usam o arco e as almofadas da referência recente. Enquanto o fone Bluetooth é exibido e informa bateria válida, anel e número superior mostram essa bateria. Ao sair, retomam a do celular. O número abaixo do fone foi removido. As faixas 0–20%, 21–80% e 81–100% aceitam cores personalizadas em Visual, sem sobreposição nos limites.

Testes também verificam fonte desconhecida, bateria zero, fades, carga simultânea do celular, prioridade do check, persistência da paleta, validação hexadecimal e remoção do blur ao fechar o seletor. Trocar a fonte não muda os dados do dispositivo, a previsão de carga ou os logs, e não acrescenta consulta por quadro.

A ilha compacta usa o mesmo evento dominante do anel. Com animações desligadas, atualizações musicais e de tempo ficam na cadência de segundos; escondida ou encerrada, a ilha para de desenhar.

## Limites e instalação

**Não houve teste físico no Samsung nem medição de autonomia do módulo.** APIs de privacidade, mídia, fones e integração de cabeçalhos dependem da ROM e do acessório. O anel simulado nas imagens/GIF usa o renderizador de produção; as telas do telefone ao redor são ilustrações.

Instale por cima do DUO Recreate, mantendo apenas este módulo no escopo System UI do LSPosed. Reinicie System UI ou o aparelho para carregar o código atualizado. Valide retrato/paisagem, os dois painéis, gestos, troca de bateria do fone e retorno ao celular. [Auditoria e correções](AUDITORIA-1.4.4.md).

Nenhum relatório bruto, conversa, SSID, notificação, imagem pessoal ou material de assinatura é publicado. Os resultados anteriores permanecem no histórico.
