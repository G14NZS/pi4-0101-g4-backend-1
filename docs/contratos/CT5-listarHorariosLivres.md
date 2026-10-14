# CT5: listarHorariosLivres

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E2, E4 e E7 |
| Como chamar | `GET /profissionais/{profissionalSlug}/horarios-livres?servicoId={servicoId}&data={data}`. Sem login. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| profissionalSlug | texto | sim | ana-unhas |
| servicoId | inteiro | sim | 12 |
| data | data | sim | 2026-10-20 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| data | data | sim | 2026-10-20 |
| servicoId | inteiro | sim | 12 |
| horariosLivres | lista de data e hora | sim (pode vir vazia) | 2026-10-20T09:00:00, 2026-10-20T09:30:00 |

## Erros

| Quando | O que volta |
|---|---|
| Não existe profissional com esse profissionalSlug | HTTP 404, erro `PROFISSIONAL_NAO_ENCONTRADO` |
| O servicoId não existe ou não é desse profissional | HTTP 404, erro `SERVICO_NAO_ENCONTRADO` |
| data fora do formato ou anterior a hoje | HTTP 400, erro `DATA_INVALIDA` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
