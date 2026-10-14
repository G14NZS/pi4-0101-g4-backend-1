# CT18: agendamento

| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Backend 1, que é o único módulo que lê e grava nesta tabela |
| Quem responde | Banco |
| Atende | E2, E4, E5, E6, E7 e E8 |
| Como chamar | Tabela `agendamento`, chave `id` |

## Entrada e saída

Os campos gravados são os mesmos campos lidos.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| id | inteiro | sim | 87 |
| profissional_id | inteiro | sim | 3 |
| servico_id | inteiro | sim | 12 |
| cliente_id | inteiro | sim | 25 |
| inicio | data e hora | sim | 2026-10-20T14:00:00 |
| fim | data e hora | sim | 2026-10-20T14:45:00 |
| status | um de: confirmado, cancelado | sim | confirmado |
| lembrete_status | um de: pendente, enviado, falhou | sim | pendente |
| lembrete_registrado_em | data e hora | não (vazio enquanto pendente) | 2026-10-20T11:15:03 |
| criado_em | data e hora | sim | 2026-10-18T10:00:00 |
| atualizado_em | data e hora | sim | 2026-10-18T10:00:00 |

## Erros

| Quando | O que volta |
|---|---|
| profissional_id, servico_id ou cliente_id não existe | O banco recusa a gravação |
| fim não é depois de inicio | O banco recusa a gravação (restrição de verificação) |
| status ou lembrete_status fora da lista | O banco recusa a gravação (restrição de valores) |
