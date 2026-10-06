# Duo Status Bar 1.4.0-canvas-fx2

APK modificado localmente da base oficial 1.4.0. Android 13 ou superior, arm64.
SHA-256: `9EEDF33C2836B9BCFD0CEE9EBB7F17DCE192692284B65C164EBD75262AC236DF`

## O que foi integrado

- Renderização Android Canvas no módulo, editor e prévias. Sem runtime ou arquivo Rive no APK.
- Anel, percentagem, sinal, rede 5G/4G, Wi-Fi, avião e Não Perturbe preservados; números centralizados e avião corrigido.
- Bluetooth, NFC com ondas de leitor, hotspot, fones com bateria disponível, câmera e microfone separados, alarme, VPN, uso de GPS, silencioso/vibração, mídia, carga sem fio, lanterna, gravação e Wi-Fi sem internet.
- Bateria dos fones: verde acima de 15%, vermelho em 15% ou menos. A leitura usa dispositivo de áudio conectado; sem leitura válida, não exibe percentagem inventada.
- Ícones ativos alternam a cada 3 segundos. Saída de 120 ms e entrada de 160 ms, sem sobreposição. Novos eventos de privacidade recebem prioridade.
- Check de desbloqueio ocupa sozinho o centro e devolve o Wi-Fi quando ele está disponível. Aviso de áudio dura 4 segundos.
- Feixe de carga sobre o arco existente, brilho de carga rápida por 3 segundos, sem círculo duplicado. Bateria crítica: 3 pulsos e ponto discreto.
- Vidro, molas, alinhamento automático, pausa de efeitos no bolso, transição do relógio, modo de ocultar na câmera, controles de espessura/escala e logs locais.
- Demonstração dentro do app, com entradas simuladas e o mesmo desenho Canvas: selecione cada ícone ou acione Check, Carga e Bateria fraca.

## Validação realizada

- Compilação e 145 testes: 0 falhas, 0 erros.
- Verificação da assinatura e alinhamento de 16 KB concluída.
- Renderização de 48 cenários do desenho real, inspecionada em computador. Fonte e compositor de Android podem diferir desta renderização.
- Nenhum símbolo, fonte ou binário SF Symbols da Apple foi incluído. Desenhos próprios inspirados em ícones do sistema, sem afirmar identidade com iOS 27. O pacote oficial SF Symbols 27 limita uso a produtos nas plataformas Apple (licença 2A–2D): https://developer.apple.com/sf-symbols/

## Limites ainda não verificados em aparelho

- Não houve instalação/teste no Samsung. Os testes locais não provam funcionamento perfeito de ganchos e permissões de todas as ROMs.
- Privacidade/GPS dependem do acesso a AppOps em SystemUI. Lanterna usa CameraManager. Bateria Bluetooth depende do que o fone e a ROM informam.
- Captura da tela usa a sessão de MediaProjection: também pode representar compartilhamento da tela. Se a API de sistema estiver indisponível, registra o limite e não inventa estado.
- Carga rápida usa BatteryStatus do sistema quando disponível ou estimativa de potência >=15 W. Leituras não confirmadas são registradas.
- Blur real depende do compositor e de `isCrossWindowBlurEnabled`; existe fundo translúcido de fallback. Alinhamento com câmera/relógio, desbloqueio/AOD e modo câmera ainda precisam de validação no dispositivo.
- Modo de ocultar na câmera começa desligado para permitir visualizar o novo indicador de câmera. Pode ser ativado nas configurações. Configurações já salvas são preservadas; ative os indicadores novos se atualizar uma versão Canvas anterior.

## Instalação

1. Instale o APK. Por ter assinatura local diferente da versão oficial, se o Android recusar a atualização, será necessário remover a versão oficial antes (isso apaga as configurações dessa instalação).
2. Ative o módulo para SystemUI no LSPosed e reinicie SystemUI ou o aparelho.
3. No app, use “Ver ícones e efeitos” para conferir o desenho e os efeitos com estados simulados.
4. Para o comportamento no aparelho, teste estados reais; o painel de eventos e o relatório local registram capacidades indisponíveis.

Código correspondente incluído no ZIP, sob a licença GPL do projeto original. Chaves de assinatura e senhas não estão incluídas.
