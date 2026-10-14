# CT7: criarAgendamento

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E4 e E5 |
| Como chamar | `POST /agendamentos`. Sem login. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| servicoId | inteiro | sim | 12 |
| inicio | data e hora | sim | 2026-10-20T14:00:00 |
| clienteTelefone | texto | sim | +5519988887777 |
| clienteNome | texto | não (obrigatório só quando o telefone ainda não está cadastrado) | Maria Souza |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| servicoId | inteiro | sim | 12 |
| servicoNome | texto | sim | Manicure simples |
| inicio | data e hora | sim | 2026-10-20T14:00:00 |
| fim | data e hora | sim | 2026-10-20T14:45:00 |
| clienteId | inteiro | sim | 25 |
| clienteNome | texto | sim | Maria Souza |
| status | um de: confirmado, cancelado | sim | confirmado |

## Erros

| Quando | O que volta |
|---|---|
| O servicoId não existe | HTTP 404, erro `SERVICO_NAO_ENCONTRADO` |
| inicio fora do formato ou no passado | HTTP 400, erro `DATA_INVALIDA` |
| clienteTelefone fora do formato +55DDDNÚMERO | HTTP 400, erro `TELEFONE_INVALIDO` |
| Telefone não cadastrado e clienteNome não enviado | HTTP 400, erro `NOME_OBRIGATORIO` |
| O horário já foi ocupado por outro cliente ou está fora da grade do profissional | HTTP 409, erro `HORARIO_INDISPONIVEL` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
