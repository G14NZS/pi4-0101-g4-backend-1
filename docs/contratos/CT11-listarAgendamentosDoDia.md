# CT11: listarAgendamentosDoDia

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E8 |
| Como chamar | `GET /agendamentos?data={data}`. Devolve só os agendamentos confirmados do profissional nessa data, ordenados por inicio. Cabeçalho `Authorization: Bearer {token}`, com o token devolvido pelo CT1. O profissional é identificado pelo token. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| data | data | sim | 2026-10-20 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| data | data | sim | 2026-10-20 |
| agendamentos | lista de objeto AgendamentoDoDia | sim (pode vir vazia) | ver objeto AgendamentoDoDia |

## Objeto AgendamentoDoDia

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| inicio | data e hora | sim | 2026-10-20T14:00:00 |
| fim | data e hora | sim | 2026-10-20T14:45:00 |
| clienteNome | texto | sim | Maria Souza |
| clienteTelefone | texto | sim | +5519988887777 |
| servicoNome | texto | sim | Manicure simples |

## Erros

| Quando | O que volta |
|---|---|
| Token ausente, inválido ou vencido | HTTP 401, erro `NAO_AUTENTICADO` |
| data fora do formato | HTTP 400, erro `DATA_INVALIDA` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
