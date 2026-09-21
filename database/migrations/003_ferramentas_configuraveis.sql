-- ============================================================
-- UNIMED TOOLS - MIGRAÇÃO 003
-- Ferramentas configuráveis administradas pela TI
-- ============================================================

USE DBUNIMED;

CREATE TABLE IF NOT EXISTS ferramenta_configuravel (
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
    atualizado_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ferramenta_configuravel PRIMARY KEY (id),
    CONSTRAINT uk_ferramenta_configuravel_slug UNIQUE (slug),
    CONSTRAINT fk_ferramenta_criado_por FOREIGN KEY (criado_por)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_ferramenta_atualizado_por FOREIGN KEY (atualizado_por)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    INDEX idx_ferramenta_ativo (ativo)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO permissao (codigo, modulo, descricao, ativo)
SELECT 'FERRAMENTAS_ADMINISTRAR', 'TI', 'Permite criar e administrar ferramentas configuráveis.', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM permissao WHERE codigo = 'FERRAMENTAS_ADMINISTRAR'
);

INSERT IGNORE INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id
FROM perfil_acesso p
JOIN permissao pe ON pe.codigo = 'FERRAMENTAS_ADMINISTRAR'
WHERE p.codigo = 'ADMINISTRADOR';
