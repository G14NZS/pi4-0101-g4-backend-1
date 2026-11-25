# pi4-0101-g4-backend-1 — Reservice (v1.0)

Back-end principal e orquestração Docker Compose do sistema **Reservice** — Projeto Integrador 4.

Desenvolvido em **Java 21 puro**: `com.sun.net.httpserver.HttpServer` com pool fixo de threads (ADR 0002), persistência relacional JDBC com **MySQL** (ADR 0001), autenticação via **JWT** (HS256, com reivindicação `exp`), hash de senhas com **bcrypt** e servidor **TCP Socket** interno para comunicação com o worker de lembretes. Os contratos de interface estão documentados em [`docs/contratos/`](docs/contratos/).

---

## Equipe de Desenvolvimento

| Integrante | RA |
|---|---|
| Gianluca Prestello Crugel | 25008682 |
| Pedro Henrique Nunes Marchetti | 25004236 |
| Rafael Ferreira Lucietto | 25004572 |

---

## Repositórios do Ecossistema Reservice

- **Front-end (Vue.js SPA + Nginx):** https://github.com/G14NZS/pi4-0101-g4-frontend.git
- **Back-end 1 (API REST + MySQL + Socket Server):** https://github.com/G14NZS/pi4-0101-g4-backend-1.git
- **Back-end 2 (Worker Java + Twilio SMS):** https://github.com/G14NZS/pi4-0101-g4-backend-2.git

---

## Entregáveis Prontos (v1.0)

| História | Contratos | Endpoint / Mecanismo |
|---|---|---|
| H02.1 | CT1 `abrirSessao`, CT14 `profissional` | `POST /sessoes` |
| H02.2 | CT2 `cadastrarServico`, CT3 `listarServicos`, CT15 `servico` | `POST /servicos`, `GET /profissionais/{slug}/servicos` |
| H03.1 | CT4 `definirHorarios` | `PUT /horarios` (substitui a grade semanal inteira) |
| H03.2 | CT5 `listarHorariosLivres` | `GET /profissionais/{slug}/horarios-livres?servicoId=&data=` (público) |
| H04.1 | CT7 `criarAgendamento`, CT16 `horario_atendimento`, CT17 `cliente`, CT18 `agendamento` | `POST /agendamentos` |
| H05.1 | CT6 `buscarCliente` | Consulta de cliente por telefone (`ClienteRecurso`) |
| H06.1 | CT11 `listarAgendamentosDoDia` | `GET /agendamentos?data=AAAA-MM-DD` |
| H07.1 | CT8 `listarAgendamentosDoCliente`, CT9 `cancelarAgendamento` | `GET /agendamentos/do-cliente`, `PATCH /agendamentos/{id}/cancelamento` |
| H07.2 | CT10 `remarcarAgendamento` | Remarcação de agendamento ativo (`AgendamentoRecurso`) |
| H08.1 | CT12 `listarLembretesPendentes`, CT13 `registrarLembrete`, CT19 `enviarMensagem` | Socket TCP (`PORTA_SOCKET`) + worker `backend-2` (Twilio SMS) |
| H09.1 | Docs | README para máquina limpa com Compose, profissional de exemplo, variáveis Twilio e Release `v1.0` |
| H09.2 | Teste | Roteiro de demonstração ensaiado ponta a ponta |

Todo erro de negócio ou validação retorna HTTP semântico com o corpo `{ "erro": "CODIGO", "mensagem": "texto" }`. O CORS libera as origens definidas em `CORS_ORIGENS` (padrão `http://localhost:5175`) aceitando `Authorization` e `Content-Type`.

---

## Como Rodar em uma Máquina Limpa (Apenas Docker)

O `docker-compose.yml` sobe **MySQL**, **Backend 1**, **Backend 2** e **Frontend**. Os três repositórios devem ficar lado a lado dentro da mesma pasta pai (`pi4-0101-g4-backend-1`, `pi4-0101-g4-backend-2` e `pi4-0101-g4-frontend`). Senhas, chaves JWT e credenciais da Twilio são injetadas por variáveis de ambiente:

```sh
# 1. Clone os três repositórios lado a lado na mesma pasta pai
git clone https://github.com/G14NZS/pi4-0101-g4-backend-1.git
git clone https://github.com/G14NZS/pi4-0101-g4-backend-2.git
git clone https://github.com/G14NZS/pi4-0101-g4-frontend.git

# 2. Entre no repositório principal e crie o arquivo .env
cd pi4-0101-g4-backend-1
cp .env.example .env                # preencha os segredos e as variáveis do Twilio; .env está no .gitignore

# 3. Suba toda a stack
docker compose up -d --build        # MySQL 3306, API http://localhost:8080, Frontend http://localhost:5175

# 4. Comandos úteis de validação e manutenção
docker compose ps                   # verifica saúde e status dos containers
docker compose run --rm testes      # executa os testes automatizados (mvn test) contra o MySQL do compose
docker compose down                 # para os serviços mantendo os dados no volume
docker compose down -v              # derruba tudo e apaga o volume do banco (reset limpo)

# Alternativa para manter os segredos fora da pasta do repositório:
docker compose --env-file ../segredos-reservice.env up -d --build
```

### Profissional de Exemplo e Carga Inicial

Na primeira inicialização, o MySQL executa automaticamente `db/schema.sql` e `db/dados-dev.sql`, criando a profissional de exemplo:

- **E-mail:** `ana@exemplo.com`
- **Senha:** `segredo123`
- **Slug público:** `ana-unhas`
- **Horários padrão:** Segunda a sexta-feira das `09:00` às `18:00`; sábado das `09:00` às `13:00`.

Para recriar o banco após alterar scripts SQL, execute `docker compose down -v` e suba novamente. Caso queira criar outro profissional direto no banco, gere o `senha_hash` com:

```sh
docker compose exec backend1 java -cp backend1.jar br.com.reservice.ferramentas.GerarHashSenha MINHA_SENHA
```

---

## Configuração (Variáveis de Ambiente)

### Back-end 1 e Infraestrutura

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_URL` | Sim | — | URL JDBC do MySQL |
| `DB_USUARIO` | Sim | — | Usuário do banco MySQL |
| `DB_SENHA` | Não | — | Senha do banco MySQL |
| `JWT_SEGREDO` | Sim | — | Segredo do HS256, com 32 caracteres ou mais |
| `JWT_VALIDADE_MINUTOS` | Não | `120` | Validade do token (campo `exp`) em minutos |
| `CORS_ORIGENS` | Não | `http://localhost:5175` | Origens liberadas, separadas por vírgula |
| `CHAVE_INTERNA` | Sim | — | Chave enviada pelo Backend 2 em toda mensagem TCP (16+ caracteres) |
| `PORTA_SOCKET` | Não | `9090` | Porta do socket TCP usado pelo Backend 2 |
| `PORTA` | Não | `8080` | Porta HTTP da API REST |
| `THREADS` | Não | `16` | Tamanho do pool fixo de threads do `HttpServer` |

### Back-end 2 (Worker de Lembretes — Twilio SMS)

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `TWILIO_ACCOUNT_SID` | Sim* | — | Account SID da conta Twilio |
| `TWILIO_AUTH_TOKEN` | Sim* | — | Auth Token da conta Twilio |
| `TWILIO_PHONE_NUMBER` | Sim* | — | Número remetente Twilio habilitado para SMS (formato E.164, ex.: `+1...`) |

*\*Necessárias no `.env` do Compose para que o `backend-2` efetue o disparo real de SMS.*

---

## Verificação Manual Rápida (PowerShell)

```powershell
# CT1 - login
$s = Invoke-RestMethod -Method Post http://localhost:8080/sessoes -ContentType 'application/json' -Body '{"email":"ana@exemplo.com","senha":"segredo123"}'
# CT2 - cadastrar serviço
Invoke-RestMethod -Method Post http://localhost:8080/servicos -ContentType 'application/json' -Headers @{ Authorization = "Bearer $($s.token)" } -Body '{"servicoNome":"Pedicure","duracaoMinutos":40,"preco":30.50}'
# CT3 - listar serviços
Invoke-RestMethod http://localhost:8080/profissionais/ana-unhas/servicos | ConvertTo-Json -Depth 5
# CT7 - criar agendamento
Invoke-RestMethod -Method Post http://localhost:8080/agendamentos -ContentType 'application/json' -Body '{"servicoId":1,"inicio":"2026-11-03T14:00:00","clienteTelefone":"+5519988887777","clienteNome":"Maria Souza"}'
# CT8 - agendamentos futuros do cliente
Invoke-RestMethod 'http://localhost:8080/agendamentos/do-cliente?clienteTelefone=%2B5519988887777' | ConvertTo-Json -Depth 5
# CT9 - cancelar agendamento
Invoke-RestMethod -Method Patch http://localhost:8080/agendamentos/1/cancelamento -ContentType 'application/json' -Body '{"clienteTelefone":"+5519988887777"}'
```