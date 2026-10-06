# Duo Status Bar — Canvas Studio FX1
Versão 1.4.0-canvas-studio-fx1, código 24. Android 13 ou superior.
SHA-256: `9B6A2E66F889D2CAC10B753B1FAF3AA26F248977C823E051E617856DB6A4ACA0`

## Evidência e correção
Os relatórios duo-log-20261006-021711 e duo-log-20261006-021746 mostram a versão Studio, código 23, anexada ao SystemUI e alternando os ícones durante a carga. Há quatro ganchos biométricos instalados, mas nenhum disparo do check registrado. Os relatórios antigos não registravam os avisos de desbloqueio separadamente, portanto não demonstram se USER_PRESENT foi enviado ou recebido.

A revisão confirmou que os callbacks biométricos apenas guardavam um horário sem iniciar nenhum efeito. Um novo teste reproduziu a falta do check ao sair do bloqueio sem USER_PRESENT: falhou na versão anterior e passou após a correção.

- Observação direta da saída da tela de bloqueio por KeyguardManager.isKeyguardLocked, além do aviso USER_PRESENT e dos ganchos biométricos já existentes. Sem novos nomes de métodos Samsung, listeners privilegiados ou permissões.
- Biometria aguarda a saída real do bloqueio; autenticar sem dispensar a tela não mostra check antes da hora. PIN também funciona sem callback biométrico.
- Check exclusivo por 3 segundos, fade out no final e retorno do ícone normal com fade in. Carregamento, câmera, fones, bolso e outros ícones não ocupam o centro durante o check.
- Fontes duplicadas e avisos atrasados não reiniciam o efeito. Inicializar em um aparelho já desbloqueado não dispara o check.
- Uma leitura após apagar a tela captura o bloqueio tardio; depois o observador dorme até a tela acordar. Leituras a cada 250 ms enquanto o bloqueio aparece e a cada 2 segundos com aparelho desbloqueado; após autenticação a confirmação temporária usa 100 ms por até 5 segundos. Parar o módulo remove os callbacks.
- Novos registros locais mostram início do observador, mudanças do bloqueio, autenticação, USER_PRESENT e origem do disparo. Efeitos desativados registram o motivo de não exibir o check. Eventos ao vivo permanecem ocultos na interface.

A semântica de tela de bloqueio exibida, incluindo bloqueio por deslize e telas sobrepostas, é descrita na [documentação Android](https://developer.android.com/reference/android/app/KeyguardManager#isKeyguardLocked()).

## Preservação e validação
- Interface Studio, novo ícone do app, renderer Canvas, anexação da barra e preferências preservados; comparação das fontes correspondentes com o ZIP Studio anterior.
- 176 testes locais passaram, zero falhas, erros ou ignorados. Dez testes novos cobrem regressão sem USER_PRESENT, PIN, autenticação antes de dispensar o bloqueio, despertar rápido, avisos duplicados, dois desbloqueios sucessivos, estado indisponível, tela apagada e encerramento do observador. Os testes existentes incluem prioridade exclusiva e duração/fade enquanto há carga, câmera, áudio e bolso.
- Compilação, assinatura e alinhamento de 16 KB verificados. Mesmo certificado das versões Canvas anteriores; APK sem Rive. Verificação de bytecode e roteamento de logs concluída.
- Os testes incluem o runtime Android de testes no computador; não reproduzem todos os serviços e o compositor reais do Samsung. Não houve instalação no telefone neste ambiente. O comportamento da correção no aparelho ainda não foi confirmado.

## Atualizar
Instale por cima da versão Canvas Studio e reinicie o aparelho ou SystemUI para carregar o módulo novo. Mantenha o módulo ativado no escopo SystemUI do LSPosed, Animações e Check de desbloqueio ligados. Configurações existentes são preservadas.

Código correspondente GPL no ZIP, sem chaves ou senhas.
