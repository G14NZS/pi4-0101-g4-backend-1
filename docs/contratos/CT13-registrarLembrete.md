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

## Quando o SMS foi aceito e o registro falhou

Se o Serviço de mensagens aceitou o SMS, mas o registrarLembrete não chegou ao Backend 1 ou voltou com erro que não seja `LEMBRETE_JA_REGISTRADO` (conexão caiu, Backend 1 fora do ar, `ERRO_INTERNO`), nada é gravado: o `lembrete_status` continua `pendente`. Assim, se o início ainda estiver dentro da janela, o agendamento volta na próxima listarLembretesPendentes (CT12) e o lembrete pode sair de novo no ciclo seguinte. Escolhemos o risco de um SMS repetido em vez do risco de o cliente ficar sem lembrete. O Backend 2 não tenta registrar de novo no mesmo ciclo.

O registro é gravado uma vez só por agendamento: quando dois registros do mesmo agendamento chegam juntos, só um grava e o outro recebe `LEMBRETE_JA_REGISTRADO`.
