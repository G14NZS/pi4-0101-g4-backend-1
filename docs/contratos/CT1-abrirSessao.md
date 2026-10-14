# CT1: abrirSessao

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E1, E2 e E8 |
| Como chamar | `POST /sessoes`. Sem login (é a própria operação de login). |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| email | texto | sim | ana@exemplo.com |
| senha | texto | sim | segredo123 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| token | texto | sim | eyJhbGciOiJIUzI1NiJ9.abc |
| profissionalId | inteiro | sim | 3 |
| profissionalNome | texto | sim | Ana Beatriz Costa |
| profissionalSlug | texto | sim | ana-unhas |

## Erros

| Quando | O que volta |
|---|---|
| E-mail ou senha não enviados | HTTP 400, erro `DADOS_INVALIDOS` |
| E-mail não cadastrado ou senha errada | HTTP 401, erro `CREDENCIAIS_INVALIDAS` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
