# FX5: falha de inicialização na FX4

## Evidência

Os relatórios `duo-log-20261006-014014.txt`, `duo-log-20261006-014101.txt` e
`duo-log-20261006-014134.txt` identificam a versão 1.4.0-canvas-fx4, código 21.
Todos registram na linha 233:

```
monitor start failed: IllegalStateException: Receiver ... registered with differing handler
(was Handler (android.app.ActivityThread$H) ... now Handler (android.os.Handler) ...)
```

O relatório 014134 registra bateria de 14%, `carregando=true` na linha 424.
A anexação do Canvas não é a falha: o monitor fica parcialmente inicializado.

## Causa e correção

A FX4 registrava a mesma instância de BroadcastReceiver primeiro sem scheduler explícito,
depois com outro Handler para eventos Bluetooth. O contrato de ReceiverDispatcher não permite
agendadores diferentes para a mesma instância/contexto. A exceção ocorria antes de effects.start()
e da leitura inicial da bateria. A primeira inscrição continuava recebendo eventos, mas draw()
retornava porque running ainda era false. O sinalizador registered era marcado cedo demais.

Na FX5 o Bluetooth recebe uma instância separada, encaminhando para o tratamento existente.
Sua inscrição é opcional e protegida separadamente. O registro principal é marcado somente após
sucesso; falha posterior desfaz inscrições e tarefas. stop() remove ambos os receptores e eventos
atrasados não reativam o monitor. Não há mudanças de desenho, geometria ou duração dos efeitos.

## Verificação

Os testes DuoStateMonitorTest chamam a inicialização real do monitor, sem forçar running=true.
O Context de teste reproduz a restrição de identidade de Handler observada nos relatórios,
entrega a bateria sticky carregando e despacha eventos pelos filtros registrados.
Cobrem raio e brilho de carga na alternância, tela/desbloqueio/check exclusivo com fade out,
Bluetooth negado sem interromper efeitos, nova tentativa após falha e stop/start sem duplicação.

Validação local não equivale a instalação no Samsung. O próximo relatório do aparelho deve
mostrar `state monitor up`, trocas de ícones e `Check` em vez da falha de registro.
