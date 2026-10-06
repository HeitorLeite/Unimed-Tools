-- ============================================================
-- UNIMED TOOLS - MIGRAÇÃO 004
-- Remoção do MFA/TOTP e dos desafios de autenticação
-- Execute após as migrações anteriores em instalações existentes.
-- ============================================================

USE DBUNIMED;

-- Sessões permanecem válidas por cookie opaco, sem marca de validação MFA.
ALTER TABLE sessao_usuario
    DROP COLUMN IF EXISTS mfa_validada_em;

-- A tabela era usada somente pelo antigo fluxo TOTP.
DROP TABLE IF EXISTS desafio_autenticacao;

-- Remove os campos TOTP das contas existentes.
ALTER TABLE usuario
    DROP COLUMN IF EXISTS mfa_segredo_criptografado,
    DROP COLUMN IF EXISTS mfa_ativado,
    DROP COLUMN IF EXISTS mfa_ativado_em,
    DROP COLUMN IF EXISTS ultimo_passo_mfa;

-- Não altera senha, perfil, permissões, sessões, bloqueios nem auditoria.
