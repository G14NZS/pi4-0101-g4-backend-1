# pi4-0101-g4-backend-1
Back-end 1 - Projeto Integrador 4

Java 21 puro: `com.sun.net.httpserver.HttpServer` com pool fixo de threads (ADR 0002), JDBC com MySQL (ADR 0001),
JWT (HS256, com `exp`) e bcrypt. Os contratos estão em [`docs/contratos/`](docs/contratos/).

## O que já está pronto

| História | Contratos | Endpoint |
|---|---|---|
| H02.1 | CT1 abrirSessao, CT14 profissional | `POST /sessoes` |
| H02.2 | CT2 cadastrarServico, CT3 listarServicos, CT15 servico | `POST /servicos`, `GET /profissionais/{slug}/servicos` |

Todo erro volta como `{ "erro": CODIGO, "mensagem": texto }`. O CORS libera as origens de `CORS_ORIGENS`
(padrão `http://localhost:5175`) com os cabeçalhos `Authorization` e `Content-Type`.

## Como rodar (só precisa de Docker)

```sh
docker compose up -d --build        # MySQL na 3306 + API em http://localhost:8080
docker compose run --rm testes      # roda os testes automáticos (mvn test) contra o MySQL do compose
docker compose down -v              # derruba tudo e apaga o banco
```

Na primeira subida o MySQL carrega `db/schema.sql` e `db/dados-dev.sql`, que cria a profissional de exemplo
**ana@exemplo.com / segredo123** (slug `ana-unhas`). Depois de mudar esses arquivos, use `docker compose down -v`
para o banco ser recriado.

Ainda não há cadastro de profissional pela API. Para criar um direto no banco, gere o `senha_hash`:

```sh
docker compose exec api java -cp backend1.jar br.com.reservice.ferramentas.GerarHashSenha MINHA_SENHA
```

## Testando na mão (PowerShell)

```powershell
# CT1 - login
$s = Invoke-RestMethod -Method Post http://localhost:8080/sessoes -ContentType 'application/json' -Body '{"email":"ana@exemplo.com","senha":"segredo123"}'
# CT2 - cadastrar serviço
Invoke-RestMethod -Method Post http://localhost:8080/servicos -ContentType 'application/json' -Headers @{ Authorization = "Bearer $($s.token)" } -Body '{"servicoNome":"Pedicure","duracaoMinutos":40,"preco":30.50}'
# CT3 - listar serviços
Invoke-RestMethod http://localhost:8080/profissionais/ana-unhas/servicos | ConvertTo-Json -Depth 5
# Erros: curl.exe mostra o status e o corpo
curl.exe -i -X POST http://localhost:8080/servicos -H "Content-Type: application/json" -d '{}'
```

## Configuração (variáveis de ambiente)

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_URL` | sim | | URL JDBC do MySQL |
| `DB_USUARIO` / `DB_SENHA` | sim / não | | Credenciais do banco |
| `JWT_SEGREDO` | sim | | Segredo do HS256, com 32 caracteres ou mais |
| `JWT_VALIDADE_MINUTOS` | não | `120` | Validade do token (campo `exp`) |
| `CORS_ORIGENS` | não | `http://localhost:5175` | Origens liberadas, separadas por vírgula |
| `PORTA` / `THREADS` | não | `8080` / `16` | Porta HTTP e tamanho do pool de threads |
