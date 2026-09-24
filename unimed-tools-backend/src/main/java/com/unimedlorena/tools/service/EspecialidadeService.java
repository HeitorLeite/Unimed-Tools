/*
 * Responsabilidade: Preenche especialidades em planilhas a partir do cadastro de médicos.
 */
package com.unimedlorena.tools.service;

import com.unimedlorena.tools.dto.EspecialidadeStats;
import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EspecialidadeService {

  // ── Tabela TUSS fixa (mesma do Python) ─────────────────────────────────────
  private static final Map<String, String> TUSS_MAP = Map.of(
    "10101039",
    "CLÍNICA MÉDICA",
    "50000349",
    "FISIOTERAPIA",
    "50000470",
    "PSICOLOGIA",
    "50000560",
    "NUTRICIONISTA"
  );

  // ── Nomes aceitos para cada coluna ─────────────────────────────────────────
  private static final Set<String> NOMES_ESP = Set.of(
    "NOME ESPECIALIDADE",
    "NOME_ESPECIALIDADE"
  );
  private static final Set<String> NOMES_SOL = Set.of(
    "NOME_PRESTADOR_SOLIC",
    "NOME SOLICITANTE",
    "NOME_SOLICITANTE",
    "NOME_PRESTADOR"
  );
  private static final Set<String> NOMES_TUSS = Set.of(
    "COD_TUSS",
    "CODIGO_TUSS"
  );

  public record Resultado(byte[] arquivo, EspecialidadeStats stats) {}

  public Resultado processar(
    MultipartFile fileDespesas,
    MultipartFile fileMedicos
  ) throws Exception {
    // A referência é carregada primeiro para tornar a busca por linha constante.
    Map<String, String> mapaMedicos = carregarMedicos(fileMedicos);

    try (Workbook workbook = new XSSFWorkbook(fileDespesas.getInputStream())) {
      Sheet despesas = null;
      String nomeAba = null;

      for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
        String nomeAtual = workbook.getSheetName(i);
        if (nomeAtual.toLowerCase().contains("despesa")) {
          despesas = workbook.getSheetAt(i);
          nomeAba = nomeAtual;
          break;
        }
      }

      // Planilhas antigas nem sempre nomeiam a aba; a primeira é o fallback histórico.
      if (despesas == null) {
        despesas = workbook.getSheetAt(0);
        nomeAba = workbook.getSheetName(0);
      }

      Row header = despesas.getRow(0);
      int colEsp = -1;
      int colSol = -1;
      int colTuss = -1;

      for (Cell cell : header) {
        String valor = norm(cellStr(cell));
        if (NOMES_ESP.contains(valor)) colEsp = cell.getColumnIndex();
        if (NOMES_SOL.contains(valor)) colSol = cell.getColumnIndex();
        if (NOMES_TUSS.contains(valor)) colTuss = cell.getColumnIndex();
      }

      if (colEsp < 0) {
        throw new IllegalArgumentException(
          "Coluna 'Nome Especialidade' não encontrada."
        );
      }
      if (colSol < 0) {
        throw new IllegalArgumentException(
          "Coluna 'Nome Solicitante' não encontrada."
        );
      }

      int total = 0;
      int preenchidas = 0;
      int jaOk = 0;
      int semInfo = 0;

      for (int r = 1; r <= despesas.getLastRowNum(); r++) {
        Row row = despesas.getRow(r);
        if (row == null) continue;
        total++;

        String especialidadeAtual = cellStr(row.getCell(colEsp));
        if (!vazio(especialidadeAtual)) {
          jaOk++;
          continue;
        }

        String solicitante = cellStr(row.getCell(colSol));
        String tuss = colTuss >= 0 ? cellStr(row.getCell(colTuss)) : "";

        if (vazio(solicitante) && vazio(tuss)) {
          semInfo++;
          continue;
        }

        // Códigos TUSS conhecidos são usados somente quando não há solicitante.
        if (vazio(solicitante) && !vazio(tuss)) {
          String especialidade = TUSS_MAP.get(tuss.trim());
          if (especialidade != null) {
            setCelula(row, colEsp, especialidade);
            preenchidas++;
          } else {
            semInfo++;
          }
          continue;
        }

        String especialidade = mapaMedicos.get(
          solicitante.trim().toUpperCase()
        );
        if (especialidade != null) {
          setCelula(row, colEsp, especialidade);
          preenchidas++;
        } else {
          semInfo++;
        }
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return new Resultado(
        out.toByteArray(),
        new EspecialidadeStats(total, preenchidas, jaOk, semInfo, nomeAba)
      );
    }
  }

  // ── Carrega planilha de médicos ─────────────────────────────────────────────
  private Map<String, String> carregarMedicos(MultipartFile file)
    throws Exception {
    Map<String, String> mapa = new HashMap<>();
    try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
      Sheet medicos = workbook.getSheetAt(0);
      Row header = medicos.getRow(0);

      int colNome = -1;
      int colEsp = -1;
      for (Cell cell : header) {
        String valor = norm(cellStr(cell));
        if (valor.equals("PES_NOM_COMP")) colNome = cell.getColumnIndex();
        if (valor.equals("ESPMD_DES")) colEsp = cell.getColumnIndex();
      }
      if (colNome < 0 || colEsp < 0) {
        throw new IllegalArgumentException(
          "Planilha de médicos deve ter colunas PES_NOM_COMP e ESPMD_DES."
        );
      }

      for (int r = 1; r <= medicos.getLastRowNum(); r++) {
        Row row = medicos.getRow(r);
        if (row == null) continue;
        String nome = cellStr(row.getCell(colNome));
        String especialidade = cellStr(row.getCell(colEsp));
        if (!vazio(nome) && !vazio(especialidade)) {
          mapa.put(nome.trim().toUpperCase(), especialidade.trim());
        }
      }
    }
    return mapa;
  }

  // ── Utilitários ─────────────────────────────────────────────────────────────
  private String cellStr(Cell c) {
    if (c == null) return "";
    return switch (c.getCellType()) {
      case STRING -> c.getStringCellValue();
      case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
      case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
      default -> "";
    };
  }

  private void setCelula(Row row, int col, String valor) {
    Cell c = row.getCell(col);
    if (c == null) c = row.createCell(col);
    c.setCellValue(valor);
  }

  private boolean vazio(String v) {
    return v == null || v.isBlank() || v.equals("#N/DISP");
  }

  private String norm(String v) {
    return v == null ? "" : v.strip().toUpperCase();
  }
}
