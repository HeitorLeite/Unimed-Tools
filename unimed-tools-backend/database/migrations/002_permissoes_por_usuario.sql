-- ============================================================
-- UNIMED TOOLS - MIGRAÇÃO 002
-- Permissões individuais e acesso operacional negado por padrão
-- Execute uma única vez no banco DBUNIMED já existente.
-- ============================================================

USE DBUNIMED;

CREATE TABLE IF NOT EXISTS usuario_permissao (
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

-- Usuários operacionais passam a receber somente permissões concedidas
-- individualmente pela tela de gerenciamento.
DELETE pp
FROM perfil_permissao pp
INNER JOIN perfil_acesso p ON p.id = pp.perfil_id
WHERE p.codigo = 'USUARIO';

SELECT p.codigo AS perfil, COUNT(pp.permissao_id) AS permissoes_herdadas
FROM perfil_acesso p
LEFT JOIN perfil_permissao pp ON pp.perfil_id = p.id
GROUP BY p.id, p.codigo
ORDER BY p.codigo;
