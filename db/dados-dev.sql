-- Dados de exemplo só para desenvolvimento (carregados pelo docker-compose na primeira subida do MySQL).
-- Login: ana@exemplo.com / segredo123. Para outras senhas, ver a ferramenta GerarHashSenha no README.

INSERT INTO profissional (nome, email, senha_hash, telefone, slug, criado_em)
VALUES ('Ana Beatriz Costa', 'ana@exemplo.com',
        '$2a$10$TtTiwRlH5nw8zsb81YrZ.OPlAF0W3bCS/CRUmOrSNXlnRVNJb53ua',
        '+5519999990000', 'ana-unhas', '2026-10-01 09:00:00');

INSERT INTO servico (profissional_id, nome, duracao_minutos, preco, criado_em)
SELECT id, 'Manicure simples', 45, 35.00, '2026-10-13 19:20:00' FROM profissional WHERE slug = 'ana-unhas';
