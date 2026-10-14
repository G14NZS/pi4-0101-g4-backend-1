# CT4: definirHorarios

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 1 |
| Atende | E2 |
| Como chamar | `PUT /horarios`. Substitui a grade semanal inteira do profissional. Cabeçalho `Authorization: Bearer {token}`, com o token devolvido pelo CT1. O profissional é identificado pelo token. |

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| horarios | lista de objeto Horario | sim (lista vazia = não atende em nenhum dia) | ver objeto Horario |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| horarios | lista de objeto Horario | sim | ver objeto Horario |
| atualizadoEm | data e hora | sim | 2026-10-13T19:25:00 |

## Objeto Horario

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| diaSemana | um de: domingo, segunda, terca, quarta, quinta, sexta, sabado | sim | terca |
| horaInicio | texto | sim | 09:00 |
| horaFim | texto | sim | 18:00 |

## Erros

| Quando | O que volta |
|---|---|
| Token ausente, inválido ou vencido | HTTP 401, erro `NAO_AUTENTICADO` |
| diaSemana fora da lista | HTTP 400, erro `DADOS_INVALIDOS` |
| horaInicio ou horaFim fora do formato HH:MM, ou horaFim não é depois de horaInicio | HTTP 400, erro `HORARIO_INVALIDO` |
| Duas faixas do mesmo dia se sobrepõem | HTTP 400, erro `HORARIOS_SOBREPOSTOS` |

Todo erro HTTP volta com o corpo `{ "erro": CODIGO, "mensagem": texto para a tela }`, como declarado na visão geral.
