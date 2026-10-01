WITH base AS
(
SELECT trunc(months_between(:datareferencia,
                            pes.pes_dat_nasc) / 12) idade,
       b.bnf_ind_grau_depcia grau_dp,
       b.cv_nro,
       pes.pes_ind_sexo
FROM dbaunimed.bnfrio b
INNER JOIN dbaunimed.pessoa pes
        ON pes.pes_cod = b.bnf_cod_pessoa
INNER JOIN dbaunimed.cntrat_venda ctv
        ON ctv.cv_nro = b.cv_nro
INNER JOIN dbaunimed.plano pln
        ON pln.plano_nro_reg_ans = ctv.plano_nro_reg_ans
LEFT JOIN dbaunimed.emp_contrt emp
       ON emp.empcn_cod = ctv.empcn_cod
WHERE b.bnf_dat_inic_vigen <= :datareferencia
AND instr(',' || replace(:empresas, ' ', '') || ',', ',' || to_char(emp.empcn_cod_pessoa) || ',') > 0
AND ctv.plano_nro_reg_ans NOT LIKE '%GRATUITO%'
AND (
       b.bnf_dat_excl IS NULL
       OR b.bnf_dat_excl = DATE '0001-01-01'
       OR b.bnf_dat_excl > :datareferencia
    )
),

dados AS
(
SELECT
       CASE
            WHEN idade BETWEEN 0 AND 18 THEN '0 a 18'
            WHEN idade BETWEEN 19 AND 23 THEN '19 a 23'
            WHEN idade BETWEEN 24 AND 28 THEN '24 a 28'
            WHEN idade BETWEEN 29 AND 33 THEN '29 a 33'
            WHEN idade BETWEEN 34 AND 38 THEN '34 a 38'
            WHEN idade BETWEEN 39 AND 43 THEN '39 a 43'
            WHEN idade BETWEEN 44 AND 48 THEN '44 a 48'
            WHEN idade BETWEEN 49 AND 53 THEN '49 a 53'
            WHEN idade BETWEEN 54 AND 58 THEN '54 a 58'
            WHEN idade >= 59 THEN '59 a 999'
       END faixa_etaria,

       CASE
            WHEN grau_dp = 0 THEN 'TITULAR'
            WHEN grau_dp IN (61,90,99) THEN 'AGREGADO'
            ELSE 'DEPENDENTE'
       END grau_dependente,

       cv_nro,
       pes_ind_sexo
FROM base
),

faixas AS
(
SELECT '0 a 18' faixa_etaria,1 ordem FROM dual
UNION ALL
SELECT '19 a 23',2 FROM dual
UNION ALL
SELECT '24 a 28',3 FROM dual
UNION ALL
SELECT '29 a 33',4 FROM dual
UNION ALL
SELECT '34 a 38',5 FROM dual
UNION ALL
SELECT '39 a 43',6 FROM dual
UNION ALL
SELECT '44 a 48',7 FROM dual
UNION ALL
SELECT '49 a 53',8 FROM dual
UNION ALL
SELECT '54 a 58',9 FROM dual
UNION ALL
SELECT '59 a 999',10 FROM dual
),

contratos AS
(
SELECT DISTINCT cv_nro
FROM dados
),

contrato_faixa AS
(
SELECT
       c.cv_nro,
       f.faixa_etaria,
       f.ordem
FROM contratos c
CROSS JOIN faixas f
),

pivot_dados AS
(
SELECT *
FROM
(
SELECT cv_nro,
       faixa_etaria,
       grau_dependente || '_' ||
       CASE
            WHEN pes_ind_sexo='M' THEN 'MASCULINO'
            WHEN pes_ind_sexo='F' THEN 'FEMININO'
       END tipo,
       COUNT(*) total
FROM dados
GROUP BY
       cv_nro,
       faixa_etaria,
       grau_dependente,
       pes_ind_sexo
)
PIVOT
(
SUM(total)
FOR tipo IN
(
'TITULAR_MASCULINO' AS tit_masc,
'TITULAR_FEMININO' AS tit_fem,
'DEPENDENTE_MASCULINO' AS dep_masc,
'DEPENDENTE_FEMININO' AS dep_fem,
'AGREGADO_MASCULINO' AS agr_masc,
'AGREGADO_FEMININO' AS agr_fem
)
)
),

resultado_final AS
(
SELECT
       cf.cv_nro,
       cf.faixa_etaria,
       cf.ordem,

       NVL(pd.tit_masc,0) tit_masc,
       NVL(pd.tit_fem,0) tit_fem,
       NVL(pd.dep_masc,0) dep_masc,
       NVL(pd.dep_fem,0) dep_fem,
       NVL(pd.agr_masc,0) agr_masc,
       NVL(pd.agr_fem,0) agr_fem,

       NVL(pd.tit_masc,0)+
       NVL(pd.tit_fem,0)+
       NVL(pd.dep_masc,0)+
       NVL(pd.dep_fem,0)+
       NVL(pd.agr_masc,0)+
       NVL(pd.agr_fem,0) total

FROM contrato_faixa cf

LEFT JOIN pivot_dados pd
ON pd.cv_nro = cf.cv_nro
AND pd.faixa_etaria = cf.faixa_etaria

UNION ALL

SELECT
       cv_nro,
       'TOTAL GERAL',
       99,

       SUM(NVL(tit_masc,0)),
       SUM(NVL(tit_fem,0)),
       SUM(NVL(dep_masc,0)),
       SUM(NVL(dep_fem,0)),
       SUM(NVL(agr_masc,0)),
       SUM(NVL(agr_fem,0)),
       SUM(NVL(tit_masc,0)+
           NVL(tit_fem,0)+
           NVL(dep_masc,0)+
           NVL(dep_fem,0)+
           NVL(agr_masc,0)+
           NVL(agr_fem,0))
FROM
(
SELECT
       cf.cv_nro,
       NVL(pd.tit_masc,0) tit_masc,
       NVL(pd.tit_fem,0) tit_fem,
       NVL(pd.dep_masc,0) dep_masc,
       NVL(pd.dep_fem,0) dep_fem,
       NVL(pd.agr_masc,0) agr_masc,
       NVL(pd.agr_fem,0) agr_fem
FROM contrato_faixa cf
LEFT JOIN pivot_dados pd
ON pd.cv_nro = cf.cv_nro
AND pd.faixa_etaria = cf.faixa_etaria
)
GROUP BY cv_nro
),

relatorio AS
(
SELECT
       cv_nro,
       'CONTRATO '||cv_nro faixa_etaria,
       NULL tit_masc,
       NULL tit_fem,
       NULL dep_masc,
       NULL dep_fem,
       NULL agr_masc,
       NULL agr_fem,
       NULL total,
       0 ordem
FROM contratos

UNION ALL

SELECT
       cv_nro,
       faixa_etaria,
       tit_masc,
       tit_fem,
       dep_masc,
       dep_fem,
       agr_masc,
       agr_fem,
       total,
       ordem
FROM resultado_final
)

SELECT
       faixa_etaria,
       tit_masc,
       tit_fem,
       dep_masc,
       dep_fem,
       agr_masc,
       agr_fem,
       total,
       ROW_NUMBER() OVER (ORDER BY cv_nro, ordem) AS UT_EXPORT_ORD
FROM relatorio
WHERE 1 = 1
/*FILTROS*/
