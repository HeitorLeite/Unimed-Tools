-- ============================================================
-- UNIMED TOOLS
-- Esquema completo de autenticação, sessões, permissões e acesso
-- Banco: DBUNIMED | SGBD: MariaDB / MySQL
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS DBUNIMED
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE DBUNIMED;

CREATE TABLE perfil_acesso (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_perfil_acesso PRIMARY KEY (id),
    CONSTRAINT uk_perfil_acesso_codigo UNIQUE (codigo)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE permissao (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    modulo VARCHAR(50) NOT NULL,
    descricao VARCHAR(500) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_permissao PRIMARY KEY (id),
    CONSTRAINT uk_permissao_codigo UNIQUE (codigo),
    INDEX idx_permissao_modulo (modulo),
    INDEX idx_permissao_ativo (ativo)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE perfil_permissao (
    perfil_id SMALLINT UNSIGNED NOT NULL,
    permissao_id SMALLINT UNSIGNED NOT NULL,
    concedida_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_perfil_permissao PRIMARY KEY (perfil_id, permissao_id),
    CONSTRAINT fk_perfil_permissao_perfil FOREIGN KEY (perfil_id)
        REFERENCES perfil_acesso (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_perfil_permissao_permissao FOREIGN KEY (permissao_id)
        REFERENCES permissao (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE usuario (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nome VARCHAR(150) NOT NULL,
    login VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email VARCHAR(254),
    senha_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    perfil_id SMALLINT UNSIGNED NOT NULL,
    status VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDENTE_ATIVACAO',
    deve_trocar_senha BOOLEAN NOT NULL DEFAULT TRUE,
    senha_temporaria_expira_em DATETIME(6),
    tentativas_login SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    bloqueado_ate DATETIME(6),
    ultimo_login_em DATETIME(6),
    senha_alterada_em DATETIME(6),
    criado_por BIGINT UNSIGNED,
    atualizado_por BIGINT UNSIGNED,
    desativado_por BIGINT UNSIGNED,
    criado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    desativado_em DATETIME(6),
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_login UNIQUE (login),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT chk_usuario_status CHECK (
        status IN ('PENDENTE_ATIVACAO', 'ATIVO', 'BLOQUEADO', 'INATIVO')
    ),
    CONSTRAINT fk_usuario_perfil FOREIGN KEY (perfil_id)
        REFERENCES perfil_acesso (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_usuario_criado_por FOREIGN KEY (criado_por)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_usuario_atualizado_por FOREIGN KEY (atualizado_por)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_usuario_desativado_por FOREIGN KEY (desativado_por)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    INDEX idx_usuario_perfil (perfil_id),
    INDEX idx_usuario_status (status),
    INDEX idx_usuario_bloqueado_ate (bloqueado_ate),
    INDEX idx_usuario_criado_por (criado_por)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE usuario_permissao (
    usuario_id BIGINT UNSIGNED NOT NULL,
    permissao_id SMALLINT UNSIGNED NOT NULL,
    concedida_por BIGINT UNSIGNED,
    concedida_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_usuario_permissao PRIMARY KEY (usuario_id, permissao_id),
    CONSTRAINT fk_usuario_permissao_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_usuario_permissao_permissao FOREIGN KEY (permissao_id)
        REFERENCES permissao (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_usuario_permissao_concedida_por FOREIGN KEY (concedida_por)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    INDEX idx_usuario_permissao_permissao (permissao_id),
    INDEX idx_usuario_permissao_concedida_por (concedida_por)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE sessao_usuario (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT UNSIGNED NOT NULL,
    -- Somente o SHA-256 do token opaco é persistido.
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    criada_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ultima_atividade_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expira_em DATETIME(6) NOT NULL,
    revogada_em DATETIME(6),
    motivo_revogacao VARCHAR(200),
    endereco_ip VARCHAR(45),
    user_agent VARCHAR(500),
    CONSTRAINT pk_sessao_usuario PRIMARY KEY (id),
    CONSTRAINT uk_sessao_usuario_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_sessao_usuario_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    INDEX idx_sessao_usuario_usuario (usuario_id),
    INDEX idx_sessao_usuario_expiracao (expira_em),
    INDEX idx_sessao_usuario_revogacao (revogada_em),
    INDEX idx_sessao_usuario_ativa (usuario_id, revogada_em, expira_em)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE ferramenta_configuravel (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    slug VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500) NOT NULL,
    api_nome VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    filtros_json JSON NOT NULL,
    colunas_preview_json JSON NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por BIGINT UNSIGNED,
    atualizado_por BIGINT UNSIGNED,
    criado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ferramenta_configuravel PRIMARY KEY (id),
    CONSTRAINT uk_ferramenta_configuravel_slug UNIQUE (slug),
    CONSTRAINT fk_ferramenta_criado_por FOREIGN KEY (criado_por) REFERENCES usuario (id) ON DELETE SET NULL,
    CONSTRAINT fk_ferramenta_atualizado_por FOREIGN KEY (atualizado_por) REFERENCES usuario (id) ON DELETE SET NULL,
    INDEX idx_ferramenta_ativo (ativo)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE ferramenta_nativa_configuracao (
    ferramenta_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nome VARCHAR(120),
    descricao VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_por BIGINT UNSIGNED,
    atualizado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ferramenta_nativa_configuracao PRIMARY KEY (ferramenta_id),
    CONSTRAINT fk_ferramenta_nativa_atualizado_por FOREIGN KEY (atualizado_por)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE auditoria_acesso (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario_executor_id BIGINT UNSIGNED,
    usuario_alvo_id BIGINT UNSIGNED,
    evento VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resultado VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    endereco_ip VARCHAR(45),
    user_agent VARCHAR(500),
    -- Nunca incluir senha, token, API key ou dado de saúde.
    detalhes JSON,
    ocorrido_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_auditoria_acesso PRIMARY KEY (id),
    CONSTRAINT chk_auditoria_resultado CHECK (
        resultado IN ('SUCESSO', 'FALHA', 'BLOQUEADO')
    ),
    CONSTRAINT fk_auditoria_executor FOREIGN KEY (usuario_executor_id)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_auditoria_alvo FOREIGN KEY (usuario_alvo_id)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    INDEX idx_auditoria_executor (usuario_executor_id),
    INDEX idx_auditoria_alvo (usuario_alvo_id),
    INDEX idx_auditoria_evento (evento),
    INDEX idx_auditoria_resultado (resultado),
    INDEX idx_auditoria_ocorrido_em (ocorrido_em)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO perfil_acesso (codigo, nome, descricao, ativo) VALUES
('ADMINISTRADOR', 'Administrador', 'Acesso completo e gerenciamento de usuários.', TRUE),
('USUARIO', 'Usuário', 'Acesso aos módulos operacionais autorizados.', TRUE);

INSERT INTO permissao (codigo, modulo, descricao, ativo) VALUES
('APLICACAO_ACESSAR', 'GERAL', 'Permite entrar e acessar o Unimed Tools.', TRUE),
('USUARIOS_VISUALIZAR', 'USUARIOS', 'Permite visualizar usuários.', TRUE),
('USUARIOS_CRIAR', 'USUARIOS', 'Permite cadastrar usuários.', TRUE),
('USUARIOS_EDITAR', 'USUARIOS', 'Permite alterar usuários e perfis.', TRUE),
('USUARIOS_DESATIVAR', 'USUARIOS', 'Permite bloquear ou desativar usuários.', TRUE),
('XML_ACESSAR', 'XML', 'Permite acessar as ferramentas XML TISS.', TRUE),
('BI_ACESSAR', 'BI', 'Permite acessar as ferramentas de BI.', TRUE),
('RELATORIOS_ACESSAR', 'RELATORIOS', 'Permite acessar a Central de Relatórios.', TRUE),
('RELATORIO_PERSONALIZADO_ACESSAR', 'RELATORIOS', 'Permite gerar relatórios personalizados.', TRUE),
('RELATORIOS_ADMINISTRAR', 'RELATORIOS', 'Permite administrar definições de relatórios.', TRUE),
('RELATORIOS_DADOS_SENSIVEIS_ACESSAR', 'RELATORIOS', 'Permite acessar colunas sensíveis autorizadas.', TRUE),
('ANS_ACESSAR', 'ANS', 'Permite acessar as ferramentas ANS.', TRUE),
('FERRAMENTAS_ADMINISTRAR', 'TI', 'Permite criar e administrar ferramentas configuráveis.', TRUE),
('COMERCIAL_ACESSAR', 'COMERCIAL', 'Comercial — relatórios de empresas, receita, despesas e faixa etária.', TRUE),
('ASSISTENCIAL_ACESSAR', 'ASSISTENCIAL', 'Assistencial — relatórios personalizados por colunas e filtros.', TRUE),
('REVISAO_CONTAS_ACESSAR', 'REVISAO_CONTAS', 'Revisão de Contas — correção e conferência de XML TISS.', TRUE),
('UNICA_ACESSAR', 'UNICA', 'Única — correção de rede e arquivos ANS.', TRUE),
('HOSPITAL_ACESSAR', 'HOSPITAL', 'Hospital — consulta de autorizações ainda não convertidas em guia.', TRUE),
('GESTAO_RISCO_ACESSAR', 'GESTAO_RISCO', 'Gestão de Risco — relatórios de rastreio e acompanhamento.', TRUE);

INSERT INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id
FROM perfil_acesso p
CROSS JOIN permissao pe
WHERE p.codigo = 'ADMINISTRADOR' AND pe.ativo = TRUE;

SELECT p.codigo AS perfil, pe.codigo AS permissao, pe.modulo
FROM perfil_acesso p
INNER JOIN perfil_permissao pp ON pp.perfil_id = p.id
INNER JOIN permissao pe ON pe.id = pp.permissao_id
ORDER BY p.codigo, pe.modulo, pe.codigo;
