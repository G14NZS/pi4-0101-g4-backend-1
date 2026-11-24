-- Esquema do Backend 1 (fichas CT14 a CT18).
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

-- Horas como texto "HH:MM" (CT16); com esse formato fixo, comparar texto é comparar horas.
CREATE TABLE IF NOT EXISTS horario_atendimento (
  id              INT     NOT NULL AUTO_INCREMENT,
  profissional_id INT     NOT NULL,
  dia_semana      ENUM ('domingo', 'segunda', 'terca', 'quarta', 'quinta', 'sexta', 'sabado') NOT NULL,
  hora_inicio     CHAR(5) NOT NULL,
  hora_fim        CHAR(5) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_horario_profissional FOREIGN KEY (profissional_id) REFERENCES profissional (id),
  CONSTRAINT ck_horario_hora_inicio CHECK (hora_inicio REGEXP '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  CONSTRAINT ck_horario_hora_fim CHECK (hora_fim REGEXP '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  CONSTRAINT ck_horario_faixa CHECK (hora_fim > hora_inicio),
  INDEX ix_horario_profissional_dia (profissional_id, dia_semana)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS cliente (
  id        INT          NOT NULL AUTO_INCREMENT,
  nome      VARCHAR(120) NOT NULL,
  telefone  VARCHAR(20)  NOT NULL,
  criado_em DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_cliente_telefone UNIQUE (telefone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS agendamento (
  id                     INT      NOT NULL AUTO_INCREMENT,
  profissional_id        INT      NOT NULL,
  servico_id             INT      NOT NULL,
  cliente_id             INT      NOT NULL,
  inicio                 DATETIME NOT NULL,
  fim                    DATETIME NOT NULL,
  status                 ENUM ('confirmado', 'cancelado')         NOT NULL,
  lembrete_status        ENUM ('pendente', 'enviado', 'falhou')   NOT NULL,
  lembrete_registrado_em DATETIME NULL,
  criado_em              DATETIME NOT NULL,
  atualizado_em          DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_agendamento_profissional FOREIGN KEY (profissional_id) REFERENCES profissional (id),
  CONSTRAINT fk_agendamento_servico FOREIGN KEY (servico_id) REFERENCES servico (id),
  CONSTRAINT fk_agendamento_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
  CONSTRAINT ck_agendamento_periodo CHECK (fim > inicio),
  INDEX ix_agendamento_profissional_inicio (profissional_id, inicio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
