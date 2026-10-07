# Instalar DUO Recreate

[Português](INSTALL.pt-BR.md) · [English](INSTALL.en.md) · [Español](INSTALL.es.md) · [Início](../README.md)

## 1. Confira os requisitos

- **Android 13 ou superior** e aparelho **arm64**, conforme esta distribuição do APK.
- **LSPosed ou framework compatível com a API Xposed tradicional**, instalado, ativo e compatível com a sua versão de Android e ROM.
- Apenas **um módulo Duo** habilitado no System UI. Desative o Duo original ou outro fork antes de habilitar Recreate.

**O APK sozinho não modifica a barra. Shizuku também não substitui LSPosed.** A injeção no System UI depende do framework. Um telefone sem esse ambiente pode abrir o aplicativo e sua prévia, mas não terá a barra injetada.

## 2. Prepare o framework

Se LSPosed já está funcionando, vá à etapa 3. Caso contrário, siga a documentação oficial do framework e do gerenciador de root compatível com seu aparelho. A instalação normalmente usa Magisk ou KernelSU com o mecanismo Zygisk adequado, instala o ZIP do framework pelo gerenciador de root e exige reiniciar o telefone. Abra o gerenciador do framework e confirme que ele está **ativo** antes de instalar o módulo.

O projeto [LSPosed original](https://github.com/LSPosed/LSPosed) documenta suporte a Android 8.1–14. Para versões posteriores, escolha uma implementação mantida que documente suporte ao seu Android, como [Vector](https://github.com/JingMatrix/Vector), compatível com a API tradicional. Isso descreve o suporte do **framework**, sem certificar o encaixe do DUO na sua ROM. Não use uma versão antiga do framework apenas porque o APK aceita Android 13+.

Este guia começa com o aparelho preparado: desbloqueio do bootloader e instalação de root dependem do fabricante e modelo. O DUO não realiza esses procedimentos. **A autorização de root dentro do DUO é opcional**; ela não é necessária para o Canvas nem para exportar os diagnósticos básicos. Isso é diferente do ambiente necessário para instalar o framework.

## 3. Instale e habilite

1. Abra [Releases do nosso fork](https://github.com/edugrepo1-jpg/duoStatusBar/releases/latest) e, em **Assets**, baixe o arquivo **DUO-Recreate-…apk**. Os arquivos automáticos de código-fonte não são instaladores.
2. Instale o APK. Se já usa DUO Recreate com a mesma assinatura, instale por cima para preservar os ajustes.
3. Abra o aplicativo e escolha português, inglês ou espanhol.
4. No gerenciador LSPosed/framework, abra **Módulos → DUO Recreate**, habilite o módulo e selecione **System UI / Interface do sistema (`com.android.systemui`)** como escopo. Não é necessário selecionar todos os aplicativos nem o processo `android`.
5. Reinicie o telefone para o System UI carregar o módulo. Não basta fechar e reabrir o aplicativo de ajustes.
6. No DUO, abra **Início → Personalizar a barra → Configurar** e ligue o interruptor. Confira o estado de ativação do módulo na página inicial.

## 4. Personalize sem se perder

| Aba | O que ajustar |
|---|---|
| **Visual** | Tamanho, posição, traço, número, escala dos ícones, cores da bateria e cabeçalhos dos painéis. |
| **Efeitos** | Estados exibidos, permanência universal/individual, fades, carga, desbloqueio e prévia interativa. |
| **Ajustes** | Idioma, tema, acessos opcionais, Shizuku, atualizações e diagnósticos. |

Nas notificações e nos ajustes rápidos, use **Visual → Painéis do sistema** para habilitar cada painel e ajustar seu tamanho. O espaço do cabeçalho limita o tamanho final. Configurações de orientação podem ser compartilhadas ou independentes; confira a opção antes de ajustar a horizontal.

A prévia usa estados simulados. Use **Aplicar na barra** para transferir seus ajustes. Música, captura, localização e privacidade dependem dos acessos e sinais que o Android disponibiliza. Conceda somente os acessos das funções que deseja usar.

**Shizuku é opcional:** serve para a remoção adicional dos indicadores originais e outras ações autorizadas. O ocultamento principal acontece pelo módulo. A blacklist do Android é global: se desligar o DUO em uma orientação ou painel, desligue também a remoção adicional do Shizuku para evitar ficar sem indicadores naquela tela.

## 5. Atualize ou volte ao sistema original

Para atualizar, instale o APK mais recente por cima do fork e reinicie System UI ou o aparelho. O pacote é `io.github.RECREATE.statusbar`; uma compilação assinada com outra chave não atualiza a distribuição instalada. O app original possui outro pacote/assinatura.

Para remover, primeiro restaure os ícones originais na opção Shizuku, caso a tenha usado. Depois desabilite DUO Recreate no framework e reinicie. Em caso de falha grave do System UI, use o procedimento de recuperação/modo seguro documentado pelo seu gerenciador de root/framework; não há uma combinação de botões universal para todos os aparelhos.

## Se não funcionar

| Sintoma | Verifique |
|---|---|
| App abre, barra não aparece | Framework ativo, escopo System UI, interruptor do DUO ligado e reinício após habilitar. |
| Duas barras ou ícones sobrepostos | Outro módulo Duo ainda habilitado; confira também a remoção adicional opcional. |
| Não aparece no painel | Habilitação do painel e âncora reconhecida na ROM. Um cabeçalho desconhecido mantém os originais. |
| Horizontal difere da vertical | Configurações independentes de orientação e dimensões do cabeçalho. |
| Música ou bateria do fone não aparecem | Acesso opcional, reprodução realmente ativa e informação fornecida pelo Android/acessório. Sem bateria válida do fone, o anel mantém a do telefone. |

Abra a tela onde ocorreu a falha e exporte o relatório em **Ajustes → Diagnósticos**. Ele funciona sem autorização de root no app e distingue configuração, detecção, execução e indisponibilidade. Uma prévia correta não comprova a integração na sua ROM; não foi realizada certificação física de todos os aparelhos.

Os selos do README consultam os downloads dos assets e a última release do **nosso fork**. Downloads não representam instalações nem usuários únicos. O selo Android mostra o mínimo do APK (`minSdk 33`), sem prometer suporte universal às ROMs.
