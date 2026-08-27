-- Adiciona recorrência aos agendamentos já criados pela migração 003.
-- Registros existentes permanecem como execução única para preservar o contrato anterior.
ALTER TABLE relatorio_agendamento
    ADD COLUMN recorrencia VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'UNICA' AFTER agendado_para_epoch_ms,
    ADD COLUMN dias_semana VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin
        NULL AFTER recorrencia,
    ADD COLUMN dia_mes TINYINT UNSIGNED NULL AFTER dias_semana,
    ADD COLUMN fuso_horario VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'America/Sao_Paulo' AFTER dia_mes,
    ADD COLUMN execucoes_concluidas INT UNSIGNED NOT NULL DEFAULT 0 AFTER tentativas,
    ADD CONSTRAINT chk_relatorio_agendamento_recorrencia CHECK (
        recorrencia IN ('UNICA', 'DIARIA', 'SEMANAL', 'MENSAL')
    ),
    ADD CONSTRAINT chk_relatorio_agendamento_dia_mes CHECK (
        dia_mes IS NULL OR dia_mes BETWEEN 1 AND 31
    );
