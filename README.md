# DUO Recreate

[Português](README.md) · [English](docs/README.en.md) · [Español](docs/README.es.md)

Sua bateria e os estados do aparelho em um anel na barra de status. Um fork independente do [Duo Status Bar](https://github.com/kvmy666/duoStatusBar), de **kvmy666**, redesenhado com Android Canvas.

**[Baixar APK e ZIP das interfaces](https://github.com/edugrepo1-jpg/duoStatusBar/releases/tag/v1.4.3-recreate)** · [Todas as versões](https://github.com/edugrepo1-jpg/duoStatusBar/releases) · [Galeria completa](docs/GALERIA.md)

Android **13+**, aparelho **arm64** e um ambiente **LSPosed** funcionando. Pacote: `io.github.RECREATE.statusbar`. Esta distribuição é uma prévia: os testes locais não substituem a confirmação na sua ROM.

## Conheça a interface

<table><tr><td align="center">Início</td><td align="center">Tema escuro</td><td align="center">Função e controle</td></tr><tr>
<td><img src="docs/images/home-pt-BR-claro-01.png" width="220" alt="Página inicial clara" /></td>
<td><img src="docs/images/home-pt-BR-escuro-01.png" width="220" alt="Página inicial escura" /></td>
<td><img src="docs/images/nfc-ativado.png" width="220" alt="Explicação do NFC com controle azul ativado" /></td>
</tr></table>

As imagens são renderizações do aplicativo real, com estados simulados e sem dados pessoais. A galeria reúne páginas, seções roláveis, cartões, idiomas, prévia e ilha. O desfoque gráfico deve ser confirmado no aparelho.

## Para que serve

- **Anel Canvas:** bateria, porcentagem, sinal e ícones internos. Tamanho, espessura, posição e escala dos ícones até 200%, com ajustes por orientação.
- **20 categorias de estado:** Wi-Fi/dados, avião, Não Perturbe, Bluetooth, NFC, roteador, fones/bateria, check, carga, câmera e microfone separados, alarme, VPN, GPS/bússola, silêncio/vibração, mídia, indução, lanterna, gravação e Wi-Fi sem internet com `!` vermelho.
- **Efeitos:** saída termina antes da entrada; check exclusivo de 3 segundos; brilho de carga, ondas e progresso musical, previsão de carga, captura de tela, volume e tempo de gravação. O evento de música/gravação mais recente fica no centro até parar; pausa da música libera o anel.
- **Ritmo:** permanência de 1–60 segundos, tempo universal ou individual, entrada/saída ajustáveis, desenho dos traços e modo somente Wi-Fi/dados.
- **Ilha compacta:** segure o anel; arraste para baixo para expandir e consultar estados, bateria, mídia e atalhos. Arraste para cima no cabeçalho para recolher.
- **Painéis separados:** em **Visual → Painéis do sistema**, escolha exibir nas notificações e nos ajustes rápidos. Cada painel tem tamanho próprio de 50–200%, limitado pelo espaço físico do cabeçalho.
- **Prévia interativa:** simule estados e efeitos antes de aplicar. Temas sistema/claro/escuro e tradução em português, inglês e espanhol.

Um indicador só aparece quando o Android fornece evidência. Bateria de fones desconhecida não vira 0%; acima de 15% é verde e até 15% é vermelha. Mídia, captura, GPS e privacidade dependem das permissões, sensores e APIs da ROM.

## Instalar ou atualizar

1. Baixe o APK nos **Assets** da versão e instale. Se você já usa DUO Recreate, esta edição mantém o pacote e a assinatura; instale por cima para preservar os ajustes.
2. Na primeira instalação, escolha o idioma. No LSPosed, ative **DUO Recreate**, selecione **System UI** como escopo e reinicie o telefone. Desative módulos Duo antigos para evitar duas injeções.
3. Abra **Início → Personalizar a barra → Configurar** e ligue o controle azul. Visual, Efeitos e Ajustes organizam o restante. **Configurar** abre a explicação e o interruptor, sem sair da página.
4. Em **Efeitos**, abra a prévia. Ajustes simulados só chegam à barra quando você toca em **Aplicar na barra**.
5. Se desejar mídia/capturas, conceda os acessos opcionais em **Música e captura**. Reinicie o System UI depois de atualizar para carregar o novo módulo.

Migração do pacote Canvas anterior: mantenha a edição anterior assinada instalada até abrir Recreate uma vez. A importação é única e não copia logs, permissões ou autorização de root. O app original não usa a mesma assinatura.

## Diagnósticos e bateria

Em **Ajustes**, salve ou compartilhe o diagnóstico. A exportação não exige root: registra fabricante, modelo, Android, kernel, estado do módulo, preferências, funções habilitadas/detectadas/executadas, falhas, indisponibilidades e contadores de trabalho. Informações privadas e conteúdo de notificações/músicas não pertencem ao relatório.

Os dados de bateria descrevem o **aparelho inteiro**, sem atribuir mAh ou autonomia ao módulo. Não há timer ou wakelock adicional para essa telemetria. Para diagnosticar um cabeçalho ausente, abra o painel antes de coletar o relatório; layouts sem âncora reconhecida mantêm os ícones originais.

## Transparência e manutenção

[Validação executada](docs/fork/VALIDACAO.md) · [Mudanças desta edição](docs/fork/UI-E-PAINEIS.md) · [Auditoria detalhada](docs/fork/AUDITORIA.md) · [Histórico completo](docs/fork/HISTORICO-COMPLETO.md) · [Runtime e bateria](docs/fork/RUNTIME-E-BATERIA.md)

Atualizações e suporte apontam para este fork. O instalador verifica SHA-256, pacote, assinatura instalada e código de versão maior. Nenhum contato, apoio ou envio automático de logs ao desenvolvedor original é usado. O crédito e a licença **GPL-3.0** foram preservados.

Para compilar: Java 17+, Android SDK/Build Tools 36, Gradle 8.13. O projeto seleciona `app/build-canvas.gradle` por padrão.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 tools/route-module-logs.py --check
```

Assine o APK com sua própria chave protegida; uma assinatura diferente não atualiza a distribuição instalada. Não publique chaves, tokens ou logs privados. Arquivos históricos Rive não fazem parte do renderizador nem do APK.

[LICENSE](LICENSE) · [Documentação original preservada](docs/fork/UPSTREAM-README.md). Não afiliado à Apple, Samsung ou Xiaomi. Sensores, desfoque por GPU, encaixe OEM e consumo físico exigem validação no dispositivo.
