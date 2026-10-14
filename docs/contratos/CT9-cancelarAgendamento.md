# CT9: cancelarAgendamento

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E7 |
| Como chamar | `PATCH /agendamentos/{agendamentoId}/cancelamento`. Sem login; o `clienteTelefone` vai no corpo e precisa ser o do agendamento. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| clienteTelefone | texto | sim | +5519988887777 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| status | um de: confirmado, cancelado | sim | cancelado |
| atualizadoEm | data e hora | sim | 2026-10-18T10:05:00 |

## Erros

| Quando | O que volta |
|---|---|
| O agendamento não existe, ou o clienteTelefone não é o do agendamento | HTTP 404, erro `AGENDAMENTO_NAO_ENCONTRADO` |
| O agendamento já está cancelado | HTTP 409, erro `AGENDAMENTO_JA_CANCELADO` |
| O horário do agendamento já passou | HTTP 409, erro `AGENDAMENTO_JA_PASSOU` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
