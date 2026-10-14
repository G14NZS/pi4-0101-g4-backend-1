# CT17: cliente

| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Backend 1, que é o único módulo que lê e grava nesta tabela |
| Quem responde | Banco |
| Atende | E4, E5, E6, E7 e E8 |
| Como chamar | Tabela `cliente`, chave `id` |

## Entrada e saída

Os campos gravados são os mesmos campos lidos.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| id | inteiro | sim | 25 |
| nome | texto | sim | Maria Souza |
| telefone | texto | sim | +5519988887777 |
| criado_em | data e hora | sim | 2026-10-18T10:00:00 |

## Erros

| Quando | O que volta |
|---|---|
| telefone já cadastrado | O banco recusa a gravação (valor único). O Backend 1 sempre procura pelo telefone antes de criar. |
