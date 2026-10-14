# CT10: remarcarAgendamento

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E7 |
| Como chamar | `PATCH /agendamentos/{agendamentoId}/horario`. Sem login; o `clienteTelefone` vai no corpo e precisa ser o do agendamento. O serviço continua o mesmo. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| clienteTelefone | texto | sim | +5519988887777 |
| inicio | data e hora | sim | 2026-10-21T10:00:00 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| inicio | data e hora | sim | 2026-10-21T10:00:00 |
| fim | data e hora | sim | 2026-10-21T10:45:00 |
| status | um de: confirmado, cancelado | sim | confirmado |
| atualizadoEm | data e hora | sim | 2026-10-18T10:05:00 |

## Erros

| Quando | O que volta |
|---|---|
| O agendamento não existe, ou o clienteTelefone não é o do agendamento | HTTP 404, erro `AGENDAMENTO_NAO_ENCONTRADO` |
| O agendamento já está cancelado | HTTP 409, erro `AGENDAMENTO_JA_CANCELADO` |
| O horário do agendamento já passou | HTTP 409, erro `AGENDAMENTO_JA_PASSOU` |
| O novo inicio está fora do formato ou no passado | HTTP 400, erro `DATA_INVALIDA` |
| O novo horário está ocupado ou fora da grade do profissional | HTTP 409, erro `HORARIO_INDISPONIVEL` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
