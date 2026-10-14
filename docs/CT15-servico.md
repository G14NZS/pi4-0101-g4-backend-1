# CT15: servico

| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Backend 1, que é o único módulo que lê e grava nesta tabela |
| Quem responde | Banco |
| Atende | E1, E2, E4, E5, E6, E7 e E8 |
| Como chamar | Tabela `servico`, chave `id` |

## Entrada e saída

Os campos gravados são os mesmos campos lidos.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| id | inteiro | sim | 12 |
| profissional_id | inteiro | sim | 3 |
| nome | texto | sim | Manicure simples |
| duracao_minutos | inteiro | sim | 45 |
| preco | decimal | sim | 35.00 |
| criado_em | data e hora | sim | 2026-10-13T19:20:00 |

## Erros

| Quando | O que volta |
|---|---|
| O profissional_id não existe na tabela profissional | O banco recusa a gravação |
| Já existe serviço com o mesmo profissional_id e nome | O banco recusa a gravação (par único). O Backend 1 devolve `SERVICO_DUPLICADO` no CT2. |
