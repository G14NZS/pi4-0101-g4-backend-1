# CT12: listarLembretesPendentes

| Linha | Valor |
|---|---|
| Fronteira | Socket TCP (mensagens JSON) |
| Quem chama | Backend 2 |
| Quem responde | Backend 1 |
| Atende | E6 |
| Como chamar | Mensagem JSON com `operacao` igual a `listarLembretesPendentes`. O campo `chaveInterna` leva a chave combinada entre Backend 1 e Backend 2, armazenada em variável de ambiente. |

Devolve os agendamentos com `status` confirmado, `lembrete_status` pendente e `inicio` depois de agora e até `ate`, inclusive (janela `(agora, ate]`), ordenados por `inicio`. O "agora" é o relógio do Backend 1, no horário de Brasília. Um `ate` igual ou anterior a agora devolve a lista vazia.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ate | data e hora | sim | 2026-10-20T12:15:00 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| lembretes | lista de objeto LembretePendente | sim (pode vir vazia) | ver objeto LembretePendente |

## Objeto LembretePendente

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| agendamentoId | inteiro | sim | 87 |
| clienteNome | texto | sim | Maria Souza |
| clienteTelefone | texto | sim | +5519988887777 |
| servicoNome | texto | sim | Manicure simples |
| profissionalNome | texto | sim | Ana Beatriz Costa |
| inicio | data e hora | sim | 2026-10-20T14:00:00 |

## Erros

| Quando | O que volta |
|---|---|
| Chave interna ausente ou errada | erro `ACESSO_NEGADO` |
| ate fora do formato | erro `DATA_INVALIDA` |

Todo erro volta em uma mensagem JSON com `ok` igual a `false` e os campos `erro` e `mensagem`, conforme definido na visão geral.
