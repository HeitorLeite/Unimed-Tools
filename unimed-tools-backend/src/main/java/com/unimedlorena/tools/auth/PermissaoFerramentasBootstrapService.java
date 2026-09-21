package com.unimedlorena.tools.auth;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém no banco o catálogo de permissões que corresponde às ferramentas
 * atuais da Home. A rotina é idempotente e permite atualizar uma instalação
 * existente sem depender de edição manual para exibir as novas permissões.
 */
@Component
@Order(0)
public class PermissaoFerramentasBootstrapService implements ApplicationRunner {

  private record Permissao(String codigo, String modulo, String descricao) {}

  private static final List<Permissao> PERMISSOES = List.of(
    new Permissao(
      "COMERCIAL_ACESSAR",
      "COMERCIAL",
      "Comercial — relatórios de empresas, receita, despesas e faixa etária."
    ),
    new Permissao(
      "ASSISTENCIAL_ACESSAR",
      "ASSISTENCIAL",
      "Assistencial — relatórios personalizados por colunas e filtros."
    ),
    new Permissao(
      "REVISAO_CONTAS_ACESSAR",
      "REVISAO_CONTAS",
      "Revisão de Contas — correção e conferência de XML TISS."
    ),
    new Permissao(
      "UNICA_ACESSAR",
      "UNICA",
      "Única — correção de rede e arquivos ANS."
    ),
    new Permissao(
      "HOSPITAL_ACESSAR",
      "HOSPITAL",
      "Hospital — consulta de autorizações ainda não convertidas em guia."
    ),
    new Permissao(
      "GESTAO_RISCO_ACESSAR",
      "GESTAO_RISCO",
      "Gestão de Risco — relatórios de rastreio e acompanhamento."
    )
  );

  private final JdbcTemplate jdbc;

  public PermissaoFerramentasBootstrapService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (Permissao permissao : PERMISSOES) {
      jdbc.update(
        """
        INSERT INTO permissao (codigo, modulo, descricao, ativo)
        SELECT ?, ?, ?, TRUE
        WHERE NOT EXISTS (SELECT 1 FROM permissao WHERE codigo = ?)
        """,
        permissao.codigo(),
        permissao.modulo(),
        permissao.descricao(),
        permissao.codigo()
      );
      jdbc.update(
        "UPDATE permissao SET modulo = ?, descricao = ?, ativo = TRUE WHERE codigo = ?",
        permissao.modulo(),
        permissao.descricao(),
        permissao.codigo()
      );
    }

    jdbc.update(
      """
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
      WHERE p.codigo = 'ADMINISTRADOR'
      """
    );

    migrarPermissao("RELATORIOS_ACESSAR", List.of(
      "COMERCIAL_ACESSAR",
      "ASSISTENCIAL_ACESSAR",
      "HOSPITAL_ACESSAR",
      "GESTAO_RISCO_ACESSAR"
    ));
    migrarPermissao("XML_ACESSAR", List.of("REVISAO_CONTAS_ACESSAR"));
    migrarPermissao("ANS_ACESSAR", List.of("UNICA_ACESSAR"));
  }

  private void migrarPermissao(String antiga, List<String> novas) {
    for (String nova : novas) {
      jdbc.update(
        """
        INSERT IGNORE INTO usuario_permissao (usuario_id, permissao_id, concedida_por)
        SELECT up.usuario_id, nova.id, up.concedida_por
        FROM usuario_permissao up
        JOIN permissao anterior ON anterior.id = up.permissao_id
        JOIN permissao nova ON nova.codigo = ?
        WHERE anterior.codigo = ?
        """,
        nova,
        antiga
      );
    }
  }
}
