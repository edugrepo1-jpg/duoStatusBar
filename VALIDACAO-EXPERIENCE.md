# Duo Status Bar — Canvas Experience
Versão 1.4.0-canvas-experience, código 25. Android 13 ou superior.
SHA-256: `7BC087FDBCF2BB0A1819D46909C388F56609D0ECB28550E656AB4E7800D2F5AF`

## Funções incluídas
1. Anel musical: ondas reais de reprodução, arco interno do progresso e cor extraída localmente da capa. Progresso aparece quando o player fornece duração e posição; capa ausente mantém a cor normal.
2. Previsão de carga: alterna com o raio. Usa a previsão Android disponível ou o ritmo observado, após pelo menos 2 pontos percentuais em um minuto. Enquanto faltam dados mostra Calculando; a estimativa pode variar com temperatura, carga rápida e uso.
3. Prévia interativa Canvas: simula desbloqueio, carga, notificações, volume, captura, gravação e múltiplos estados. Permite pausar e abrir o resumo. Ajustes da prévia ficam locais até Aplicar na barra.
4. Captura de tela: obturador animado para capturas salvas detectadas por metadados no armazenamento compartilhado, com retorno por fade. Não lê conteúdo de fotos; não detecta capturas privadas não salvas nem nomes sem identificação de captura.
5. Editor de ritmo: permanência, saída e entrada independentes; presets Discreto, Fluido e Rápido. A saída termina antes da entrada seguinte; desenhos de traços seguem essa sequência.
6. Volume no anel: aviso temporário com percentual e arco ao mudar o nível real, seguido do retorno ao carrossel.
7. Gravação de tela: bolinha vermelha pulsante alterna com cronômetro contado desde a detecção. O estado de projeção depende do acesso disponível na ROM.
8. Resumo ao segurar o anel: painel Canvas em pílula se expande e recolhe, mostra bateria e todos os estados ativos, com rolagem quando necessário. Janela anexada à barra existente; não muda a posição da barra. Apagar a tela, entrar no bolso ou desbloquear fecha o resumo; a confirmação de desbloqueio mantém prioridade.
9. Desenho dos ícones: traços se revelam na entrada, após a saída do anterior, junto do fade.
10. Fones redesenhados seguindo o contorno da referência enviada, com detalhes azuis. Percentual fica abaixo, antes dos pontos de sinal; maior que 15% verde, até 15% vermelho. Percentual ausente no acessório é omitido.
11. GPS acompanha o norte magnético com sensor de orientação, compensação da rotação da tela e suavização. Sensor ausente conserva ícone fixo e registra o motivo. Sensor desliga ao apagar a tela, entrar no bolso ou desativar a função.
12. Wi-Fi sem internet com exclamação vermelha no centro. Controle de tamanho dos ícones internos de 60% a 200%, com encaixe automático no espaço livre do anel para preservar textos, pontos e espessura.

## Preservação
- Renderer exclusivo Android Canvas; nenhum asset ou runtime Rive no APK. Interface Studio e ícone do aplicativo mantidos.
- Observador de desbloqueio da versão FX1, confirmada funcionando pelo usuário, preservado byte a byte. Check exclusivo por três segundos e retorno por fade, independente dos estados ativos.
- Configurações existentes preservadas. Novidades vêm desligadas; uma coluna JSON opcional por orientação transporta os ajustes. Leitura de versões antigas permanece compatível.
- Todos os registros e sensores novos têm encerramento correspondente. Acesso indisponível não remove a barra nem exige ganchos Samsung não medidos.

## Uso
Instale por cima da versão Canvas anterior, mantenha o escopo SystemUI no LSPosed e reinicie o aparelho para carregar o módulo atualizado.
Em Efeitos, toque Ativar todas as novidades. O tamanho dos ícones e o GPS com bússola ficam em Experiência expandida. Abra a prévia interativa para experimentar e depois toque Aplicar na barra.
Em Música e captura, conecte o app aos eventos do aparelho caso música/capturas não apareçam, e permita identificar capturas salvas. Esse acesso opcional usa um serviço local de acesso a notificações para sessões de mídia e nomes de imagens; nenhum conteúdo de notificação é processado e nenhuma imagem é aberta ou enviada.
As permissões Android precisam ser habilitadas no telefone; não podem ser concedidas por este APK automaticamente.

## Validação
- 196 testes locais, zero falhas, erros ou ignorados. Regressões de anexação, cliques, ajustes, desbloqueio, carrossel completo, carga, sensores, configurações e UI.
- Renderização nativa Canvas das quatro páginas Studio em temas claro/escuro, dos ícones novos, estados de entrada/saída, bússola, fones ampliados, cor do percentual, carga e música; imagens inspecionadas no computador.
- Prévia testada com simulação de eventos, pausa/retomada, preset e aplicação explícita dos ajustes; ativação de novidades preserva o tamanho escolhido e permite desativar a bússola isoladamente.
- Compilação, bytecode, assinatura v3 e alinhamento de 16 KB verificados. Mesmo certificado das versões Canvas anteriores, sem chaves ou senhas no ZIP de fontes.
- Não houve instalação deste APK no Samsung neste ambiente. Os testes locais não reproduzem todos os serviços, sensores ou a composição de janelas reais do telefone. Integração de mídia, capturas, bússola e painel expansível ainda exige confirmação no aparelho.

## Referências Android
- [Sessões de mídia e acesso por serviço de notificações](https://developer.android.com/reference/android/media/session/MediaSessionManager).
- [Estimativa de carga](https://developer.android.com/reference/android/os/BatteryManager).
- [Metadados de mídia compartilhada](https://developer.android.com/training/data-storage/shared/media).
- [Orientação e compensação dos eixos da tela](https://developer.android.com/reference/android/hardware/SensorManager).

Código correspondente GPL no ZIP.
