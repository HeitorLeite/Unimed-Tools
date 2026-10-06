-- ============================================================
-- UNIMED TOOLS - MIGRAÇÃO 005
-- Configuração visual das ferramentas nativas
-- ============================================================

USE DBUNIMED;

CREATE TABLE IF NOT EXISTS ferramenta_nativa_configuracao (
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
