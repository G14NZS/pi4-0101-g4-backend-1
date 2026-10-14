# CT16: horario_atendimento

| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Backend 1, que é o único módulo que lê e grava nesta tabela |
| Quem responde | Banco |
| Atende | E2, E4, E5 e E7 |
| Como chamar | Tabela `horario_atendimento`, chave `id` |

## Entrada e saída

Os campos gravados são os mesmos campos lidos.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| id | inteiro | sim | 5 |
| profissional_id | inteiro | sim | 3 |
| dia_semana | um de: domingo, segunda, terca, quarta, quinta, sexta, sabado | sim | terca |
| hora_inicio | texto | sim | 09:00 |
| hora_fim | texto | sim | 18:00 |

## Erros

| Quando | O que volta |
|---|---|
| O profissional_id não existe na tabela profissional | O banco recusa a gravação |
| dia_semana fora da lista | O banco recusa a gravação (restrição de valores) |
