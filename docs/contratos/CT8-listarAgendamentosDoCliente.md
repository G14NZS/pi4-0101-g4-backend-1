# CT8: listarAgendamentosDoCliente

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E7 |
| Como chamar | `GET /agendamentos/do-cliente?clienteTelefone={clienteTelefone}`. Sem login. Devolve só agendamentos futuros com status confirmado, ordenados por inicio. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| clienteTelefone | texto | sim | +5519988887777 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentos | lista de objeto AgendamentoDoCliente | sim (pode vir vazia) | ver objeto AgendamentoDoCliente |

## Objeto AgendamentoDoCliente

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| servicoId | inteiro | sim | 12 |
| servicoNome | texto | sim | Manicure simples |
| profissionalNome | texto | sim | Ana Beatriz Costa |
| profissionalSlug | texto | sim | ana-unhas |
| inicio | data e hora | sim | 2026-10-20T14:00:00 |
| fim | data e hora | sim | 2026-10-20T14:45:00 |

## Erros

| Quando | O que volta |
|---|---|
| clienteTelefone fora do formato +55DDDNÚMERO | HTTP 400, erro `TELEFONE_INVALIDO` |
| Telefone não cadastrado | HTTP 404, erro `CLIENTE_NAO_ENCONTRADO` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
