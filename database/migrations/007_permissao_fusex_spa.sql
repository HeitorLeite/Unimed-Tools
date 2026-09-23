-- Catálogo novo sem concessão automática a usuários operacionais.
USE DBUNIMED;
INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'FUSEX_SPA_VALORIZAR', 'FUSEX_SPA', 'Valorizar guias Fusex-SPA — honorários faturados.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = 'FUSEX_SPA_VALORIZAR');

INSERT IGNORE INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id FROM perfil_acesso p
JOIN permissao pe ON pe.codigo = 'FUSEX_SPA_VALORIZAR'
WHERE p.codigo = 'ADMINISTRADOR';
