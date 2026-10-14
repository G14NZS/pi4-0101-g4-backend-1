# CT2: cadastrarServico

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E1 |
| Como chamar | `POST /servicos`. Cabeçalho `Authorization: Bearer {token}`, com o token devolvido pelo CT1. O profissional é identificado pelo token. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| servicoNome | texto | sim | Manicure simples |
| duracaoMinutos | inteiro | sim | 45 |
| preco | decimal | sim | 35.00 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| servicoId | inteiro | sim | 12 |
| servicoNome | texto | sim | Manicure simples |
| duracaoMinutos | inteiro | sim | 45 |
| preco | decimal | sim | 35.00 |
| criadoEm | data e hora | sim | 2026-10-13T19:20:00 |

## Erros

| Quando | O que volta |
|---|---|
| Token ausente, inválido ou vencido | HTTP 401, erro `NAO_AUTENTICADO` |
| servicoNome vazio, duracaoMinutos fora de 15 a 480 ou preco negativo | HTTP 400, erro `DADOS_INVALIDOS` |
| O profissional já tem um serviço com o mesmo servicoNome | HTTP 409, erro `SERVICO_DUPLICADO` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
