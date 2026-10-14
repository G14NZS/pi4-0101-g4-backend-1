# CT14: profissional

| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Backend 1, que é o único módulo que lê e grava nesta tabela |
| Quem responde | Banco |
| Atende | E1, E2, E4, E6, E7 e E8 |
| Como chamar | Tabela `profissional`, chave `id` |

## Entrada e saída

Os campos gravados são os mesmos campos lidos.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| id | inteiro | sim | 3 |
| nome | texto | sim | Ana Beatriz Costa |
| email | texto | sim | ana@exemplo.com |
| senha_hash | texto | sim | $2a$10$Qx...k2 |
| telefone | texto | sim | +5519999990000 |
| slug | texto | sim | ana-unhas |
| criado_em | data e hora | sim | 2026-10-01T09:00:00 |

## Erros

| Quando | O que volta |
|---|---|
| email já usado por outro profissional | O banco recusa a gravação (valor único) |
| slug já usado por outro profissional | O banco recusa a gravação (valor único) |
