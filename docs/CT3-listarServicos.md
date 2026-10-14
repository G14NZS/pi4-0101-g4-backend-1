# CT3: listarServicos

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E1 e E4 |
| Como chamar | `GET /profissionais/{profissionalSlug}/servicos`. Sem login. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| profissionalSlug | texto | sim | ana-unhas |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| profissionalNome | texto | sim | Ana Beatriz Costa |
| profissionalTelefone | texto | sim | +5519999990000 |
| servicos | lista de objeto Servico | sim (pode vir vazia) | ver objeto Servico |

## Objeto Servico

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| servicoId | inteiro | sim | 12 |
| servicoNome | texto | sim | Manicure simples |
| duracaoMinutos | inteiro | sim | 45 |
| preco | decimal | sim | 35.00 |

## Erros

| Quando | O que volta |
|---|---|
| Não existe profissional com esse profissionalSlug | HTTP 404, erro `PROFISSIONAL_NAO_ENCONTRADO` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
