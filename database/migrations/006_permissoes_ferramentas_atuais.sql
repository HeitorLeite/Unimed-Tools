-- ============================================================
-- UNIMED TOOLS - MIGRAÇÃO 006
-- Permissões alinhadas às ferramentas atuais da Home
-- ============================================================

USE DBUNIMED;

INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'COMERCIAL_ACESSAR', 'COMERCIAL', 'Comercial — relatórios de empresas, receita, despesas e faixa etária.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = 'COMERCIAL_ACESSAR');

INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'ASSISTENCIAL_ACESSAR', 'ASSISTENCIAL', 'Assistencial — relatórios personalizados por colunas e filtros.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = 'ASSISTENCIAL_ACESSAR');

INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'REVISAO_CONTAS_ACESSAR', 'REVISAO_CONTAS', 'Revisão de Contas — correção e conferência de XML TISS.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = 'REVISAO_CONTAS_ACESSAR');

INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'UNICA_ACESSAR', 'UNICA', 'Única — correção de rede e arquivos ANS.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = 'UNICA_ACESSAR');

INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'HOSPITAL_ACESSAR', 'HOSPITAL', 'Hospital — consulta de autorizações ainda não convertidas em guia.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = 'HOSPITAL_ACESSAR');

INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'GESTAO_RISCO_ACESSAR', 'GESTAO_RISCO', 'Gestão de Risco — relatórios de rastreio e acompanhamento.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = 'GESTAO_RISCO_ACESSAR');

-- Administradores recebem automaticamente todas as novas ferramentas.
INSERT IGNORE INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id
FROM perfil_acesso p
JOIN permissao pe ON pe.codigo IN (
  'COMERCIAL_ACESSAR',
  'ASSISTENCIAL_ACESSAR',
  'REVISAO_CONTAS_ACESSAR',
  'UNICA_ACESSAR',
  'HOSPITAL_ACESSAR',
  'GESTAO_RISCO_ACESSAR'
)
WHERE p.codigo = 'ADMINISTRADOR';

-- Compatibilidade para usuários operacionais já existentes.
-- Quem tinha a permissão técnica antiga recebe as páginas equivalentes novas.
INSERT IGNORE INTO usuario_permissao (usuario_id, permissao_id, concedida_por)
SELECT up.usuario_id, nova.id, up.concedida_por
FROM usuario_permissao up
JOIN permissao antiga ON antiga.id = up.permissao_id
JOIN permissao nova ON nova.codigo IN (
  'COMERCIAL_ACESSAR',
  'ASSISTENCIAL_ACESSAR',
  'HOSPITAL_ACESSAR',
  'GESTAO_RISCO_ACESSAR'
)
WHERE antiga.codigo = 'RELATORIOS_ACESSAR';

INSERT IGNORE INTO usuario_permissao (usuario_id, permissao_id, concedida_por)
SELECT up.usuario_id, nova.id, up.concedida_por
FROM usuario_permissao up
JOIN permissao antiga ON antiga.id = up.permissao_id
JOIN permissao nova ON nova.codigo = 'REVISAO_CONTAS_ACESSAR'
WHERE antiga.codigo = 'XML_ACESSAR';

INSERT IGNORE INTO usuario_permissao (usuario_id, permissao_id, concedida_por)
SELECT up.usuario_id, nova.id, up.concedida_por
FROM usuario_permissao up
JOIN permissao antiga ON antiga.id = up.permissao_id
JOIN permissao nova ON nova.codigo = 'UNICA_ACESSAR'
WHERE antiga.codigo = 'ANS_ACESSAR';
