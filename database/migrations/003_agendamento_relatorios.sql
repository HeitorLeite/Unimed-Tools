-- Agendamentos pertencem ao usuário criador. A configuração contém filtros
-- potencialmente sensíveis e chega a esta tabela já criptografada pelo backend.
CREATE TABLE relatorio_agendamento (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    usuario_id BIGINT UNSIGNED NOT NULL,
    tipo_relatorio VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    titulo_relatorio VARCHAR(150) NOT NULL,
    api_nome VARCHAR(150),
    configuracao_criptografada LONGTEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    formato VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nome_arquivo VARCHAR(180) NOT NULL,
    diretorio_referencia CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    diretorio_nome VARCHAR(255) NOT NULL,
    incluir_cabecalho BOOLEAN NOT NULL DEFAULT TRUE,
    agendado_para_epoch_ms BIGINT UNSIGNED NOT NULL,
    status VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDENTE',
    tentativas SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    reservado_ate_epoch_ms BIGINT UNSIGNED,
    erro_codigo VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin,
    erro_mensagem VARCHAR(300),
    criado_em_epoch_ms BIGINT UNSIGNED NOT NULL,
    atualizado_em_epoch_ms BIGINT UNSIGNED NOT NULL,
    concluido_em_epoch_ms BIGINT UNSIGNED,
    retencao_ate_epoch_ms BIGINT UNSIGNED,
    CONSTRAINT pk_relatorio_agendamento PRIMARY KEY (id),
    CONSTRAINT fk_relatorio_agendamento_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_relatorio_agendamento_tipo CHECK (
        tipo_relatorio IN ('MANUAL', 'PERSONALIZADO')
    ),
    CONSTRAINT chk_relatorio_agendamento_formato CHECK (
        formato IN ('csv', 'txt', 'xlsx')
    ),
    CONSTRAINT chk_relatorio_agendamento_status CHECK (
        status IN ('PENDENTE', 'EM_EXECUCAO', 'CONCLUIDO', 'FALHA', 'CANCELADO')
    ),
    INDEX idx_agendamento_usuario (usuario_id, criado_em_epoch_ms),
    INDEX idx_agendamento_pendente (status, agendado_para_epoch_ms),
    INDEX idx_agendamento_reserva (status, reservado_ate_epoch_ms),
    INDEX idx_agendamento_retencao (retencao_ate_epoch_ms)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
