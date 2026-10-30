-- Esquema do Backend 1 (fichas CT14 e CT15).
-- Datas sem fuso, sempre no horário de Brasília (visão geral).

CREATE TABLE IF NOT EXISTS profissional (
  id         INT          NOT NULL AUTO_INCREMENT,
  nome       VARCHAR(120) NOT NULL,
  email      VARCHAR(254) NOT NULL,
  senha_hash VARCHAR(100) NOT NULL,
  telefone   VARCHAR(20)  NOT NULL,
  slug       VARCHAR(60)  NOT NULL,
  criado_em  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_profissional_email UNIQUE (email),
  CONSTRAINT uq_profissional_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- A collation padrão (utf8mb4_0900_ai_ci) ignora maiúsculas e acentos,
-- então "Manicure" e "manicure" contam como o mesmo serviço.
CREATE TABLE IF NOT EXISTS servico (
  id              INT           NOT NULL AUTO_INCREMENT,
  profissional_id INT           NOT NULL,
  nome            VARCHAR(100)  NOT NULL,
  duracao_minutos INT           NOT NULL,
  preco           DECIMAL(10,2) NOT NULL,
  criado_em       DATETIME      NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_servico_profissional FOREIGN KEY (profissional_id) REFERENCES profissional (id),
  CONSTRAINT uq_servico_profissional_nome UNIQUE (profissional_id, nome),
  CONSTRAINT ck_servico_duracao CHECK (duracao_minutos BETWEEN 15 AND 480),
  CONSTRAINT ck_servico_preco CHECK (preco >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
