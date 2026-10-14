# CT13: registrarLembrete

| Linha | Valor |
|---|---|
| Fronteira | Socket TCP (mensagens JSON) |
| Quem chama | Backend 2 |
| Quem responde | Backend 1 |
| Atende | E6 |
| Como chamar | Mensagem JSON com `operacao` igual a `registrarLembrete`. O campo `chaveInterna` leva a chave combinada entre Backend 1 e Backend 2, armazenada em variável de ambiente. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| resultado | um de: enviado, falhou | sim | enviado |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| resultado | um de: enviado, falhou | sim | enviado |
| registradoEm | data e hora | sim | 2026-10-20T11:15:03 |

## Erros

| Quando | O que volta |
|---|---|
| Chave interna ausente ou errada | erro `ACESSO_NEGADO` |
| O agendamento não existe | erro `AGENDAMENTO_NAO_ENCONTRADO` |
| resultado fora da lista | erro `DADOS_INVALIDOS` |
| O lembrete desse agendamento já foi registrado | erro `LEMBRETE_JA_REGISTRADO`. O Backend 2 ignora e segue. |

Todo erro volta em uma mensagem JSON com `ok` igual a `false` e os campos `erro` e `mensagem`, conforme definido na visão geral.
