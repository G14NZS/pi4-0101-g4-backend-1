# CT6: buscarCliente

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E5 |
| Como chamar | `GET /clientes?clienteTelefone={clienteTelefone}`. Sem login. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| clienteTelefone | texto | sim | +5519988887777 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| clienteId | inteiro | sim | 25 |
| clienteNome | texto | sim | Maria Souza |
| clienteTelefone | texto | sim | +5519988887777 |

## Erros

| Quando | O que volta |
|---|---|
| clienteTelefone fora do formato +55DDDNÚMERO | HTTP 400, erro `TELEFONE_INVALIDO` |
| Telefone ainda não cadastrado (primeiro agendamento) | HTTP 404, erro `CLIENTE_NAO_ENCONTRADO`. Não é falha: o Frontend deixa o campo de nome vazio para o cliente preencher. |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
