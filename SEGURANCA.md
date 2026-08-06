# Política de Segurança para IAs e Desenvolvimento Seguro

Este documento estabelece os controles mínimos que uma inteligência artificial, agente de programação ou pessoa desenvolvedora deve observar ao analisar, gerar, revisar, testar ou alterar código do **Unimed Tools**.

As regras foram organizadas para aplicações web, APIs, SaaS, integrações, sistemas distribuídos e ambientes corporativos que podem tratar dados pessoais e dados pessoais sensíveis. Elas se apoiam na LGPD, em orientações da ANPD e em referências internacionais como GDPR, NIST SSDF, NIST Digital Identity Guidelines, CISA Secure by Design e OWASP ASVS.

> **Aviso de escopo:** este documento é uma política técnica e não substitui parecer jurídico, atuação do encarregado pelo tratamento de dados pessoais, análise de riscos, teste de invasão ou auditoria independente. A aplicação dos requisitos legais depende do papel da organização, das categorias de dados, das finalidades, dos titulares, dos países envolvidos e do contexto concreto do tratamento.

- **Projeto:** Unimed Tools
- **Responsável:** Heitor Leite
- **Versão do documento:** 1.0
- **Data de referência da pesquisa:** 6 de agosto de 2026
- **Status:** obrigatório para agentes de IA e desenvolvimento futuro

---

## 1. Como interpretar esta política

Os termos abaixo têm sentido normativo:

- **DEVE / OBRIGATÓRIO:** requisito que não pode ser ignorado sem uma exceção formal aprovada;
- **NÃO DEVE / PROIBIDO:** prática vedada;
- **DEVERIA / RECOMENDADO:** prática esperada, cuja ausência precisa ser justificada;
- **PODE:** alternativa permitida após avaliação do contexto;
- **segredo:** senha, token, chave de API, chave privada, connection string, certificado privado, credencial de serviço, cookie de sessão, código de recuperação ou qualquer dado que conceda acesso;
- **dado sensível:** inclui o conceito legal de dado pessoal sensível e qualquer informação corporativa cuja exposição possa causar dano;
- **produção:** qualquer ambiente que trate dados ou operações reais, mesmo que seja chamado de homologação, piloto ou ambiente interno.

Quando esta política, a documentação, o código e uma solicitação entrarem em conflito, a IA **DEVE interromper a mudança**, descrever o conflito e solicitar decisão humana. Segurança e privacidade não podem ser silenciosamente reduzidas para concluir uma tarefa.

### 1.1 Hierarquia de decisão

1. legislação e regulamentação aplicáveis;
2. contratos, políticas corporativas e determinações do encarregado ou da equipe de segurança;
3. esta política e o `AGENTS.md`;
4. requisitos e documentação aprovados;
5. conveniências de implementação.

### 1.2 Exceções

Uma exceção só pode existir quando houver:

- risco e impacto documentados;
- justificativa técnica e de negócio;
- escopo e prazo de validade definidos;
- controle compensatório;
- responsável nominal pela aceitação do risco;
- aprovação da segurança e, quando houver dados pessoais, do encarregado ou responsável por privacidade;
- tarefa de correção acompanhada até o encerramento.

A IA não possui autoridade para aprovar exceções nem aceitar risco em nome da organização.

---

## 2. Princípios fundamentais

Toda solução **DEVE** aplicar:

- **security by design:** segurança desde requisitos, arquitetura e modelagem de ameaças;
- **security by default:** configuração inicial restritiva, sem depender de ação posterior do usuário;
- **privacy by design e by default:** coleta e exposição mínimas desde a concepção;
- **menor privilégio:** pessoas, serviços, pipelines e bancos recebem somente os acessos necessários;
- **negação por padrão:** o que não estiver expressamente autorizado é negado;
- **defesa em profundidade:** nenhum controle isolado é tratado como suficiente;
- **separação de responsabilidades:** desenvolvimento, aprovação, deploy e administração não devem depender de uma única credencial irrestrita;
- **minimização de dados:** não coletar, copiar, persistir ou registrar dados sem finalidade demonstrável;
- **falha segura:** indisponibilidade, erro ou configuração ausente não pode liberar acesso;
- **rastreabilidade:** decisões e eventos de segurança relevantes precisam ser demonstráveis;
- **isolamento:** dados, credenciais e recursos de ambientes e clientes distintos não podem se misturar;
- **simplicidade:** reduzir superfícies, privilégios e componentes desnecessários;
- **zero trust:** rede interna, origem conhecida ou aplicação parceira não substituem autenticação, autorização e validação.

Segurança não é considerada concluída apenas porque o código compila, o teste funcional passa ou a interface oculta uma ação.

---

## 3. Protocolo obrigatório da IA antes de escrever código

Antes de propor ou editar uma funcionalidade, a IA **DEVE**:

1. ler `AGENTS.md`, este documento, `README.md` e a documentação do fluxo;
2. identificar dados de entrada, saída, persistência, logs, cache e integrações;
3. classificar os dados e identificar se existem dados pessoais ou sensíveis;
4. localizar fronteiras de confiança: navegador, backend, banco, fila, storage, provedor externo e pipeline;
5. identificar atores, permissões e regras de autorização;
6. verificar isolamento entre usuários, perfis, empresas e tenants;
7. mapear ameaças relevantes, ao menos falsificação, adulteração, repúdio, exposição, indisponibilidade e elevação de privilégio;
8. verificar contratos, bases legais, retenção e transferências internacionais quando houver dados pessoais;
9. conferir configurações, dependências, testes e consumidores do contrato alterado;
10. definir como os controles serão testados, inclusive em cenários negativos.

### 3.1 Condições de parada obrigatória

A IA **DEVE parar e pedir orientação** quando:

- encontrar segredo em código, log, histórico, arquivo anexado ou prompt;
- a mudança exigir dados reais de beneficiários, pacientes, colaboradores ou clientes;
- não estiver claro quem pode acessar ou alterar um recurso;
- a regra de autorização depender apenas do frontend;
- houver risco de acesso cruzado entre tenants;
- a finalidade, a base legal, a retenção ou o compartilhamento de dados pessoais não estiverem definidos;
- a mudança envolver dado de saúde, biometria, criança ou adolescente sem avaliação específica;
- uma integração enviar dados para outro país ou fornecedor sem validação da transferência;
- for solicitado desativar TLS, validação de certificado, autenticação, autorização, auditoria ou proteção de pipeline;
- for necessário contornar ACL, WAF, rate limit, proteção anti-bot ou política de rede;
- a solução depender de criptografia própria ou algoritmo obsoleto;
- não for possível validar uma alteração de alto risco.

### 3.2 Conduta da IA

A IA **NÃO DEVE**:

- inventar que uma solução é segura, compatível ou aderente a uma norma;
- afirmar que um teste, scanner ou auditoria foi executado sem evidência;
- enviar código, segredo ou dado corporativo a serviço externo não aprovado;
- inserir dados reais em exemplos, fixtures, testes ou documentação;
- sugerir credencial ampla para evitar uma falha de permissão;
- desativar um controle para “fazer funcionar”;
- adicionar fallback inseguro quando um segredo ou certificado estiver ausente;
- ocultar uma vulnerabilidade encontrada durante a tarefa;
- corrigir silenciosamente uma regra de negócio com impacto em privacidade;
- copiar uma recomendação desatualizada sem verificar a documentação oficial da versão utilizada.

---

## 4. Classificação e tratamento da informação

Antes de armazenar ou transmitir dados, classifique-os:

| Classe       | Exemplos                                                                                  | Controles mínimos                                                                                               |
| ------------ | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Pública      | documentação publicada, material institucional aprovado                                   | integridade e controle de publicação                                                                            |
| Interna      | arquitetura, nomes de serviços, métricas não públicas                                     | acesso corporativo, retenção e logs controlados                                                                 |
| Confidencial | contratos, dados de clientes, e-mails, relatórios internos                                | menor privilégio, TLS, criptografia em repouso conforme risco, auditoria                                        |
| Restrita     | dados de saúde, documentos, credenciais, chaves, tokens, dados financeiros ou biométricos | acesso estritamente necessário, criptografia, segregação, monitoramento, retenção mínima e aprovação específica |

No contexto da Unimed, informações de saúde são **dados pessoais sensíveis** pela LGPD. Arquivos TISS, relatórios, guias e dados de beneficiários devem ser tratados no nível mais restritivo aplicável, mesmo quando o sistema for interno.

Dados pseudonimizados continuam sendo dados pessoais quando a reidentificação for possível. A IA não pode declarar um conjunto “anonimizado” sem método documentado, avaliação de risco de reidentificação e aprovação responsável.

---

## 5. Variáveis de ambiente e gestão de segredos

### 5.1 Proibição de prefixos e bundles públicos

É **terminantemente proibido** colocar informação sensível em variáveis expostas ao cliente, incluindo:

- `NEXT_PUBLIC_*` no Next.js;
- `VITE_*` no Vite;
- `REACT_APP_*` em configurações que as publiquem;
- `PUBLIC_*` em frameworks que façam injeção no cliente;
- arquivos `environment.ts` do Angular;
- `window.__ENV__`, JSON de configuração pública ou HTML gerado;
- qualquer valor substituído no bundle, source map ou ativo estático durante o build.

O nome da variável não cria confidencialidade. **Todo código e toda configuração entregue ao navegador devem ser considerados públicos.** Em caso de dúvida, o segredo deve permanecer no backend.

Uma URL pública, identificador não privilegiado ou feature flag pode ser exposta somente quando sua publicidade for intencional e documentada. Uma chave chamada “public key” ainda precisa ser analisada: ela só pode ir ao cliente se o protocolo tiver sido projetado para isso e sua exposição não conceder privilégio.

### 5.2 Arquivos `.env`

- Arquivos `.env` com valores reais **NUNCA DEVEM** ser versionados.
- O `.gitignore` **DEVE** cobrir `.env`, `.env.*` e variações locais, preservando somente modelos explicitamente seguros como `.env.example`.
- `.env.example` **DEVE** conter apenas nomes, descrições e valores fictícios; nunca fragmentos de uma credencial real.
- Arquivos `.env` não devem ser enviados por e-mail, chat, issue, pull request, prompt de IA ou anexo de suporte.
- Permissões do arquivo local devem ser restritas ao usuário ou processo necessário.
- Ambientes compartilhados não devem depender de `.env` copiado manualmente.

### 5.3 Fluxo obrigatório pelo GitNode ou cofre aprovado

Antes de qualquer `build`, publicação ou deploy, variáveis e segredos **DEVEM** passar pelo **GitNode**, entendido neste projeto como a plataforma corporativa aprovada para configuração e gestão de segredos, ou por seu substituto formalmente aprovado.

O fluxo **DEVE**:

1. obter o segredo em tempo de execução ou implantação;
2. autenticar o workload sem credencial estática sempre que a plataforma permitir;
3. conceder escopo mínimo por serviço e ambiente;
4. separar desenvolvimento, teste, homologação e produção;
5. manter histórico de acesso e alteração;
6. permitir rotação e revogação;
7. impedir que o valor apareça em logs e artefatos;
8. falhar de forma segura se a configuração obrigatória não existir.

A IA **NÃO DEVE** executar ou recomendar deploy com segredo fornecido diretamente em argumento de linha de comando, arquivo versionado, imagem Docker, `Dockerfile ARG`, cache de build, artefato, saída de teste ou variável pública.

### 5.4 Ciclo de vida dos segredos

- Preferir credenciais de curta duração, identidade de workload e federação OIDC a chaves estáticas.
- Uma credencial deve pertencer a um serviço e ambiente, nunca a uma equipe inteira.
- Escopos devem ser mínimos e operações administrativas devem usar identidade separada.
- Rotação deve ser possível sem recompilar o frontend.
- Chaves de criptografia devem ser gerenciadas em KMS/HSM ou solução equivalente, separadas dos dados.
- Segredos devem ser mascarados nos logs da aplicação, CI/CD e plataforma.
- Secret scanning e push protection devem bloquear novas exposições no repositório.
- Dependências, forks e ambientes de preview não devem receber segredos de produção por padrão.

### 5.5 Resposta a segredo exposto

Ao encontrar um segredo, a IA **DEVE**:

1. não reproduzir o valor na resposta, no diff ou no log;
2. interromper o uso da credencial;
3. informar o responsável de forma reservada;
4. solicitar revogação ou rotação imediata;
5. investigar logs, artefatos, forks, caches e histórico;
6. remover a exposição sem tratar a simples exclusão do arquivo como solução suficiente;
7. registrar o incidente segundo o processo organizacional.

Reescrever o histórico do Git não invalida uma credencial já copiada. A rotação vem primeiro.

---

## 6. Autenticação, credenciais e ciclo de vida de sessão

### 6.1 Arquitetura preferencial para aplicações web

Para aplicações web, a opção preferencial é:

- sessão opaca mantida no servidor ou padrão Backend for Frontend (BFF);
- identificador de sessão em cookie com `HttpOnly`, `Secure` e `SameSite=Lax` ou `Strict`;
- `SameSite=None` somente quando o fluxo entre sites realmente exigir, sempre acompanhado de `Secure` e de defesa contra CSRF;
- prefixo `__Host-` quando aplicável, `Path=/` e sem `Domain` amplo;
- proteção contra CSRF em toda operação que altere estado;
- expiração por inatividade e duração absoluta no servidor;
- renovação do identificador após autenticação e mudança de privilégio;
- invalidação no logout, troca de senha, revogação e evento de risco.

`HttpOnly` reduz o roubo do cookie por JavaScript, mas não corrige XSS nem impede que um script malicioso faça requisições na sessão ativa. CSP, codificação de saída, sanitização e proteção contra CSRF continuam obrigatórias.

### 6.2 Armazenamento de tokens no navegador

- É **PROIBIDO** persistir JWT, access token, refresh token, session ID ou credencial em `localStorage`.
- Também é proibido usar IndexedDB, cache da aplicação, Service Worker, URL, query string, hash, cookie acessível por JavaScript ou estado serializado como cofre de segredos.
- Access tokens usados por um cliente público devem ser curtos e mantidos **somente em memória**, quando a arquitetura exigir que o JavaScript os manipule.
- `sessionStorage` é permitido **apenas como exceção documentada**, para token de curta duração, quando BFF/cookie seguro e memória não forem viáveis.
- Memória limita a persistência, mas um XSS executado durante a sessão ainda pode agir em nome do usuário ou capturar o token.
- `sessionStorage` é acessível por JavaScript e continua vulnerável a XSS. O navegador normalmente o remove ao encerrar a sessão da aba, mas restauração ou duplicação de abas e comportamentos do navegador impedem tratá-lo como fronteira de segurança ou garantia de revogação.
- Refresh tokens não devem ser expostos ao JavaScript do navegador. Quando inevitáveis em cliente público, exigem rotação, detecção de reutilização, escopo mínimo e análise específica do fluxo OAuth.

### 6.3 JWT e tokens autocontidos

Ao utilizar JWT, o backend **DEVE** validar:

- assinatura com algoritmo explicitamente permitido;
- emissor (`iss`), audiência (`aud`), expiração (`exp`) e, quando aplicável, `nbf`;
- finalidade e tipo do token;
- escopos, tenant e autorização atuais;
- identificador para revogação ou detecção de repetição quando necessário.

Não aceitar `alg=none`, troca de algoritmo, chave indicada de forma não confiável, token expirado ou assinatura sem validação. Dados pessoais e segredos não devem ser colocados no payload: JWT assinado não é necessariamente criptografado.

### 6.4 OAuth 2.0 e OpenID Connect

- Usar Authorization Code com PKCE para clientes públicos.
- Validar `state`, `nonce`, redirect URIs exatos, issuer e audience.
- Não usar fluxo implícito para novos sistemas.
- Não registrar authorization codes, tokens ou client secrets.
- Solicitar somente scopes necessários.
- Separar cliente de administração dos clientes de uso comum.
- Usar bibliotecas mantidas; não implementar protocolo de identidade manualmente.

### 6.5 Senhas

- Nunca armazenar senha em texto puro ou com criptografia reversível.
- Preferir `Argon2id`; usar `scrypt`, `bcrypt` ou `PBKDF2` somente com parâmetros atuais, salt único e biblioteca confiável.
- Não usar MD5, SHA-1 ou SHA-256 simples para senha.
- Permitir senhas longas, sem truncamento silencioso, e comparar novas senhas com lista de valores comuns ou comprometidos.
- Não impor troca periódica arbitrária; exigir troca quando houver solicitação ou evidência de comprometimento.
- Não usar perguntas de segurança como autenticador ou recuperação.
- Aplicar limitação de tentativas sem criar bloqueio permanente que permita negação de serviço contra a vítima.

### 6.6 MFA, recuperação e ações críticas

- MFA é obrigatório para cockpit, administração, acesso a produção e operações de alto impacto.
- Preferir autenticadores resistentes a phishing, como WebAuthn/passkeys ou chaves de segurança.
- Recuperação de conta deve ser tão segura quanto a autenticação normal.
- Códigos de recuperação devem ser de uso único, armazenados com proteção equivalente a senha e nunca registrados.
- Mudança de e-mail, senha, MFA, papel, permissões, dados bancários ou exportação massiva exige reautenticação ou step-up conforme risco.
- Ações críticas devem gerar trilha de auditoria e, quando adequado, notificação ao usuário.

---

## 7. Autorização e isolamento de acesso

Autenticação responde “quem é”; autorização responde “o que pode fazer”. Toda requisição protegida **DEVE** ser autorizada no servidor.

- Negar por padrão e conceder apenas ações explícitas.
- Verificar autorização em endpoint, operação, objeto e campo sensível.
- Não confiar em botão oculto, rota Angular/React, role no cliente ou ID recebido no corpo.
- Validar propriedade do recurso para evitar IDOR/BOLA.
- Derivar usuário e tenant da identidade validada; não confiar em `tenantId` enviado pelo cliente.
- Usar identificadores imprevisíveis apenas como defesa adicional, nunca como autorização.
- Revisar permissões após promoção, desligamento, mudança de função ou incidente.
- Separar conta administrativa da conta de uso diário.
- Aplicar menor privilégio a APIs internas, filas, jobs e bancos, não apenas a usuários humanos.
- Registrar alterações de papéis, permissões e políticas.

Testes de autorização **DEVEM** cobrir acesso horizontal, vertical, entre tenants, a objetos inexistentes e a operações fora da sequência de negócio.

---

## 8. Transporte e comunicação segura

### 8.1 HTTPS obrigatório

Todo cockpit, módulo administrativo, API, webhook ou serviço que trafegue autenticação, sessão, dado pessoal ou informação confidencial **DEVE operar exclusivamente por HTTPS/TLS**.

É **PROIBIDO** transmitir credenciais, cookies, tokens ou conteúdo sensível em HTTP puro. Uma sessão autenticada nunca pode fazer downgrade de HTTPS para HTTP.

Requisitos mínimos:

- TLS 1.2 ou superior; preferir TLS 1.3;
- certificados válidos, cadeia e hostname verificados;
- HSTS em produção, após avaliar subdomínios e preload;
- redirecionamento de HTTP para HTTPS sem processar credenciais;
- cookies sensíveis com `Secure`;
- ausência de mixed content;
- algoritmos e cifras obsoletos desabilitados;
- validação de certificado nunca desativada para “resolver” integração.

Em desenvolvimento local, HTTP só é aceitável em loopback, sem dados ou credenciais reais. Ambientes acessíveis por rede devem usar TLS.

### 8.2 Serviço a serviço

- Autenticar os dois lados quando o risco exigir, usando mTLS, identidade de workload ou requisições assinadas.
- Aplicar timeout, limite de conexão, retry com backoff e jitter e circuit breaker quando apropriado.
- Não repetir automaticamente operação não idempotente.
- Restringir egress e destinos possíveis.
- Validar assinatura e timestamp de webhooks; impedir replay.
- Não colocar senha, token ou API key na URL, pois URLs aparecem em históricos, proxies e logs.

### 8.3 CORS

- Usar allowlist exata de origens necessárias.
- Nunca combinar credenciais com origem curinga.
- Restringir métodos e headers.
- Não refletir automaticamente o header `Origin`.
- CORS não é mecanismo de autenticação ou autorização.

---

## 9. Limitação de requisições e proteção contra abuso

Toda API exposta deve possuir limites proporcionais ao custo e ao risco. O controle deve existir no gateway ou proxy **e** na aplicação quando regras por usuário, tenant ou operação forem necessárias.

### 9.1 Dimensões obrigatórias

Considerar limites por:

- conta ou identidade autenticada;
- tenant ou organização;
- endereço IP e rede, sem depender apenas de IP;
- chave de API ou cliente OAuth;
- endpoint e método;
- volume de bytes, registros ou arquivos;
- concorrência e duração;
- custo computacional e financeiro.

### 9.2 Regras

- Retornar `429 Too Many Requests` e, quando útil, `Retry-After`.
- Aplicar limites mais rigorosos a login, MFA, recuperação, convites, pesquisa ampla, upload, exportação e relatórios.
- Limitar tamanho de body, cabeçalhos, arquivo, lote, página e resposta.
- Limitar profundidade, aliases e custo em GraphQL.
- Usar paginação e teto máximo; não permitir `limit` ilimitado.
- Evitar lockout permanente baseado somente em falhas, pois pode ser explorado contra a vítima.
- Aplicar atraso progressivo, alertas e detecção de credential stuffing.
- Definir cotas e concorrência para jobs e exportações por tenant.
- Usar chave de idempotência em operações que não podem ser duplicadas.
- Monitorar rejeições e ajustar limites com dados reais, sem desativá-los em produção.

Os números devem ser definidos por ameaça, capacidade, contrato e teste de carga. A IA não deve inventar um limite universal nem alterar limites existentes sem avaliar consumidores e impacto operacional.

---

## 10. Validação de entradas e prevenção de injeção

Toda entrada externa é não confiável, incluindo headers, cookies, claims, arquivos, nomes de arquivo, dados de banco, filas, webhooks e respostas de APIs parceiras.

### 10.1 Regras gerais

- Validar no servidor, mesmo que exista validação no frontend.
- Preferir allowlist e schema explícito.
- Validar tipo, tamanho, faixa, formato, cardinalidade, enumeração e relações entre campos.
- Canonicalizar uma vez e validar a representação canônica.
- Rejeitar campos desconhecidos quando a compatibilidade permitir.
- Não confundir sanitização com validação.
- Codificar a saída no contexto correto: HTML, atributo, URL, JavaScript, CSS, CSV ou comando.
- Evitar regex sujeita a backtracking catastrófico.
- Retornar erro útil sem revelar implementação interna.

### 10.2 Banco e consultas

- Usar queries parametrizadas, prepared statements ou ORM corretamente configurado.
- Proibir concatenação de entrada em SQL, JPQL, LDAP, XPath ou comandos de busca.
- Allowlist para nomes de coluna, direção de ordenação e partes que não aceitam parâmetro.
- Conta de banco com menor privilégio; aplicação não deve usar superusuário.
- Migrações devem ser revisadas quanto a exposição, retenção e reversibilidade.

### 10.3 XSS e conteúdo ativo

- Manter escaping automático do framework.
- Evitar APIs de bypass como `innerHTML`, `dangerouslySetInnerHTML`, `bypassSecurityTrust*` e equivalentes.
- Se HTML do usuário for requisito real, usar sanitizador mantido e configuração restritiva.
- Implementar Content Security Policy sem depender dela como única defesa.
- Bloquear URLs e esquemas perigosos.
- Não inserir dados não confiáveis em contexto executável.

### 10.4 Upload e download de arquivos

- Permitir somente tipos necessários e documentados.
- Validar extensão, MIME e assinatura do conteúdo; não confiar no header do cliente.
- Limitar tamanho, quantidade, taxa e tempo de processamento.
- Gerar nome interno; nunca usar diretamente o caminho ou nome enviado.
- Impedir path traversal, sobrescrita, symlink e acesso fora do diretório controlado.
- Armazenar uploads fora da raiz pública e com permissões mínimas.
- Verificar malware quando o risco justificar.
- Tratar ZIP bombs, arquivos aninhados e descompressão excessiva.
- Servir downloads com tipo, disposição e nome seguros.
- Nunca sobrescrever o original enviado pelo usuário.
- Remover temporários com processo controlado e sem registrar conteúdo sensível.

### 10.5 XML, planilhas e CSV

- Desabilitar DTD e entidades externas para prevenir XXE, salvo requisito excepcional aprovado.
- Limitar profundidade, entidades, tamanho e tempo de parsing.
- Tratar planilhas e CSV como entrada ativa: fórmulas iniciadas por `=`, `+`, `-` ou `@` podem executar no software de destino.
- Escapar ou neutralizar fórmula em campos exportados que possam conter conteúdo do usuário.
- Preservar codificação e estrutura quando forem parte do contrato, após validar limites.

### 10.6 SSRF e URLs fornecidas pelo usuário

- Preferir allowlist de esquema, host, porta e caminho.
- Permitir apenas `https` quando aplicável.
- Bloquear loopback, link-local, metadados de nuvem, redes privadas e destinos resolvidos após redirects, salvo necessidade expressa.
- Resolver DNS e validar o endereço efetivamente conectado para reduzir DNS rebinding.
- Limitar redirects, tamanho, tempo e protocolos.

### 10.7 Comandos, templates e desserialização

- Evitar shell; usar APIs com argumentos separados.
- Nunca concatenar entrada em comandos.
- Não avaliar código, expressão ou template fornecido pelo usuário.
- Evitar desserialização nativa de objetos não confiáveis; usar formatos simples e schema.
- Manter allowlist de tipos quando desserialização polimórfica for indispensável.

---

## 11. Segurança do navegador e cabeçalhos

Aplicações web deveriam definir, conforme o fluxo:

- `Content-Security-Policy`, incluindo `frame-ancestors`;
- `X-Content-Type-Options: nosniff`;
- `Referrer-Policy` restritiva;
- `Permissions-Policy` com recursos desnecessários desabilitados;
- HSTS em produção;
- cookies `HttpOnly`, `Secure` e `SameSite`;
- política contra framing, preferencialmente via CSP;
- Cache-Control adequado para respostas autenticadas ou sensíveis.

Source maps de produção não devem ser públicos quando revelarem código ou detalhes internos sem necessidade. Dados sensíveis não podem ser incluídos em HTML inicial, estado de hidratação, telemetria ou ferramentas de analytics.

---

## 12. Proteção de dados, LGPD e GDPR

### 12.1 Governança antes do código

Para todo tratamento de dados pessoais, deve ser possível responder:

- quem é o controlador, operador e eventual suboperador;
- qual é a finalidade específica;
- qual hipótese legal autoriza o tratamento;
- quais dados são necessários;
- quem são os titulares;
- com quem os dados são compartilhados;
- onde são armazenados e processados;
- por quanto tempo são mantidos;
- como o titular exerce seus direitos;
- como exclusão, correção, portabilidade e bloqueio afetam backups e integrações;
- quais riscos e salvaguardas existem.

Consentimento não deve ser escolhido por conveniência. Quando for usado, deve ser livre, informado, inequívoco, específico, demonstrável e revogável. A revogação deve ser tão acessível quanto a concessão.

### 12.2 Princípios obrigatórios da LGPD

O código e a arquitetura devem respeitar finalidade, adequação, necessidade, livre acesso, qualidade, transparência, segurança, prevenção, não discriminação e responsabilização/prestação de contas.

Na prática:

- coletar apenas campos necessários;
- não reutilizar dados para finalidade incompatível;
- informar tratamento de forma clara;
- manter dados corretos quando isso for relevante;
- limitar acesso, compartilhamento e retenção;
- demonstrar decisões, controles e testes;
- incorporar segurança desde a concepção até o encerramento do tratamento.

### 12.3 Dados pessoais sensíveis

Dados de saúde, biometria e genética exigem proteção reforçada e hipótese legal apropriada. Para o Unimed Tools:

- usar dados sintéticos em desenvolvimento e teste;
- evitar copiar arquivos reais para estação, issue, chat ou prompt;
- impedir que relatórios e exportações sejam acessados por perfil não autorizado;
- registrar acesso administrativo e exportação sem registrar o conteúdo do dado;
- aplicar retenção mínima e descarte verificável;
- avaliar criptografia, segregação e mascaramento por campo conforme risco.

### 12.4 Direitos dos titulares e retenção

- Os dados devem ser localizáveis de forma controlada para atender solicitações válidas.
- Exclusão deve alcançar cópias ativas, caches e integrações conforme obrigação e política.
- Backups devem possuir prazo e processo de expiração; restauração não pode reativar silenciosamente dados eliminados.
- Retenção deve ter fundamento legal, regulatório ou contratual e prazo documentado.
- “Pode ser útil no futuro” não justifica retenção indefinida.
- Dados agregados só deixam o regime de dados pessoais quando a anonimização for efetiva.

### 12.5 RIPD/DPIA e alto risco

Um Relatório de Impacto à Proteção de Dados Pessoais (RIPD) ou Data Protection Impact Assessment (DPIA), conforme o regime aplicável, deve ser considerado antes de tratamento de alto risco, especialmente quando houver:

- larga escala de dados sensíveis;
- monitoramento sistemático;
- decisão automatizada com efeito relevante;
- combinação de bases;
- biometria, localização precisa ou dados de crianças;
- nova tecnologia com impacto significativo;
- transferência ou compartilhamento de grande alcance.

A IA deve sinalizar a necessidade, não produzir sozinha uma aprovação jurídica.

### 12.6 Transferência internacional

Antes de enviar dados pessoais a cloud, SaaS, observabilidade, suporte ou IA fora do Brasil, verificar:

- localização real do tratamento e dos backups;
- subprocessadores;
- hipótese legal do tratamento;
- mecanismo válido da Resolução CD/ANPD nº 19/2024, como decisão de adequação, cláusulas-padrão, cláusulas específicas ou normas corporativas globais, conforme aplicável;
- medidas técnicas e contratuais complementares;
- transparência ao titular e registro da operação.

Quando o GDPR for aplicável, observar também o Capítulo V e seus mecanismos de transferência. Contrato não substitui avaliação técnica do fornecedor.

### 12.7 Fornecedores

- Realizar due diligence proporcional ao risco.
- Formalizar instruções de tratamento, confidencialidade, segurança, suboperadores, incidentes, retorno e exclusão.
- Dar ao fornecedor somente dados e acessos necessários.
- Definir prazo de notificação do fornecedor menor que o prazo regulatório do controlador.
- Validar eliminação e revogação no encerramento.

### 12.8 Novas jurisdições e referenciais internacionais

A entrada em outro país exige uma matriz de aplicabilidade; não se deve marcar todas as leis conhecidas como se fossem universalmente obrigatórias.

- O GDPR pode alcançar organizações fora da União Europeia nas hipóteses previstas por seu alcance territorial.
- Nos Estados Unidos, HIPAA não é uma lei geral para todo software de saúde: sua aplicação depende de a organização atuar como _covered entity_ ou _business associate_ e tratar PHI no contexto regulado.
- CCPA/CPRA e outras leis estaduais possuem escopo e critérios próprios.
- Contratos empresariais podem impor requisitos adicionais mesmo quando uma lei estrangeira não se aplicar diretamente.
- ISO/IEC 27001:2022, ISO/IEC 27701:2025 e ISO/IEC 27018:2025 podem orientar sistemas de gestão e controles, mas citar a norma não equivale a certificação ou conformidade auditada.

Antes de disponibilizar o SaaS em nova jurisdição, jurídico, privacidade e segurança devem confirmar papéis, registros, direitos, retenção, contratos, localização, resposta a incidentes e mecanismo de transferência.

---

## 13. Criptografia e gestão de chaves

- Não criar algoritmo criptográfico próprio.
- Usar bibliotecas conhecidas, mantidas e configurações atuais.
- Preferir criptografia autenticada, como AES-GCM ou ChaCha20-Poly1305, quando adequada.
- Usar gerador criptograficamente seguro para tokens, IDs de segurança, nonces e chaves.
- Separar chaves por ambiente, finalidade e, quando necessário, tenant.
- Nunca guardar chave junto ao dado criptografado sem proteção independente.
- Implementar versionamento e rotação de chaves.
- Criptografia em repouso do provedor não substitui autorização, isolamento e minimização.
- Hash não é criptografia; senha exige função de derivação lenta própria.
- Não usar base64, ofuscação ou UUID como controle de confidencialidade.
- Backup deve receber proteção equivalente aos dados originais.

Detalhes de algoritmo, parâmetros e gestão de chave devem ser revisados por especialista quando o impacto for alto.

---

## 14. Logs, auditoria, erros e observabilidade

### 14.1 O que registrar

Registrar com data, origem, identidade correlacionável e resultado, conforme risco:

- autenticação, logout e falhas;
- MFA, recuperação e mudanças de credenciais;
- negações de autorização;
- mudanças de papéis e permissões;
- acesso administrativo;
- criação, alteração, exclusão, importação e exportação sensíveis;
- mudança de configuração e segredo, sem registrar o valor;
- falhas de validação relevantes;
- eventos de rate limit e comportamento suspeito;
- falhas de integração e TLS;
- ações de suporte ou impersonação.

### 14.2 O que não registrar

É proibido registrar:

- senhas, tokens, cookies, códigos MFA ou de recuperação;
- API keys, connection strings e chaves criptográficas;
- conteúdo integral de request/response sem classificação e redaction;
- dados de saúde, documentos e identificadores pessoais sem necessidade legal e técnica;
- arquivos enviados;
- headers de autorização;
- stack trace na resposta pública.

Quando correlação de sessão for necessária, usar identificador derivado ou hash com salt próprio, não o token real.

### 14.3 Proteção dos logs

- Centralizar logs e restringir leitura.
- Proteger integridade e disponibilidade.
- Sincronizar relógios.
- Sanitizar quebra de linha e conteúdo para prevenir log injection.
- Definir retenção por finalidade e obrigação, não por conveniência.
- Monitorar eventos e alertas; log não acompanhado não é controle completo.
- Garantir que ambientes de observabilidade e suporte cumpram as mesmas regras de privacidade.

### 14.4 Erros

- Retornar mensagens genéricas ao cliente e código de correlação.
- Não revelar stack trace, consulta, caminho interno, versão, segredo ou existência de conta.
- Registrar detalhe técnico sanitizado no servidor.
- Tratar exceções sem transformar falha em autorização ou retorno parcial inseguro.

---

## 15. Segurança específica para SaaS e multi-tenancy

Em sistemas multi-tenant, isolamento é requisito de segurança primário.

- O tenant deve ser obtido de contexto autenticado e validado em cada requisição.
- Toda query, cache key, objeto de storage, índice de busca, mensagem, job e exportação deve carregar e validar o contexto do tenant.
- Recursos globais e específicos de tenant devem ser distinguíveis.
- Jobs assíncronos devem restaurar contexto de identidade e tenant de forma assinada ou confiável.
- URLs assinadas devem ter escopo, objeto, tenant, operação e prazo limitados.
- Chaves e integrações de um tenant não podem ser reutilizadas por outro.
- Rate limit, quota e custo devem considerar tenant para evitar noisy neighbor e denial of wallet.
- Administração global exige autenticação reforçada, auditoria e justificativa.
- Função de suporte que assume identidade deve ser visível, temporária, autorizada e auditada.
- Backups, restauração e exclusão devem preservar isolamento.

Testes automatizados devem tentar acessar o mesmo identificador a partir de tenants diferentes.

---

## 16. APIs e integrações externas

- Publicar contrato e versão; evitar alteração incompatível silenciosa.
- Autenticar e autorizar cada operação sensível.
- Permitir somente métodos HTTP necessários.
- Definir limites de tamanho, paginação, tempo e concorrência.
- Validar `Content-Type` e retornar tipo correto.
- Não expor detalhes internos em resposta.
- Usar idempotência para criação, cobrança, conversão ou processamento repetível.
- Verificar integridade e autenticidade de webhooks.
- Armazenar API keys somente como segredo; preferir hash quando apenas comparação for necessária.
- Oferecer revogação, rotação, escopo e expiração.
- Não usar API key como única proteção de recurso crítico quando identidade e autorização forem necessárias.
- Documentar dados enviados, finalidade, retenção e comportamento de falha do terceiro.

Integrações externas são não confiáveis: suas respostas também precisam ser validadas antes de chegar a banco, arquivo, template ou log.

---

## 17. Dependências e cadeia de suprimentos

Antes de adicionar dependência, a IA deve verificar necessidade, manutenção, origem, licença, versão e histórico de segurança.

- Preferir dependências diretas mínimas.
- Usar lockfiles e instalação reprodutível.
- Não executar pacote desconhecido apenas para avaliá-lo em ambiente com segredos.
- Revisar scripts de instalação e geração.
- Executar SCA e manter processo de atualização de vulnerabilidades.
- Gerar SBOM para releases relevantes.
- Fixar ações de CI e imagens por versão imutável ou digest quando possível.
- Usar imagens mínimas, mantidas e sem ferramentas desnecessárias.
- Verificar assinatura, checksum ou proveniência de artefatos quando disponível.
- Impedir que pull requests não confiáveis acessem segredos.
- Preferir identidade federada e credenciais efêmeras no CI/CD.
- Proteger branch, revisão e ambiente de produção.
- Separar build de promoção: o mesmo artefato validado deve avançar entre ambientes quando possível.

Uma vulnerabilidade conhecida não deve ser ignorada apenas porque não há exploit público. O risco deve considerar alcançabilidade, dados, privilégios e exposição.

---

## 18. Infraestrutura, containers e cloud

- Executar processos sem root sempre que possível.
- Usar filesystem somente leitura e volumes mínimos.
- Não embutir segredos em imagem ou camada.
- Definir limites de CPU, memória, processos e armazenamento.
- Expor somente portas necessárias.
- Segmentar rede e restringir egress.
- Desabilitar serviços, consoles e endpoints de debug em produção.
- Proteger endpoints de health, métricas e administração conforme sensibilidade.
- Aplicar patches de sistema e runtime.
- Validar IaC com revisão e scanner.
- Criptografar backups, limitar acesso e testar restauração.
- Documentar RTO, RPO e recuperação de desastre conforme criticidade.
- Evitar console cloud e conta raiz no uso diário; exigir MFA.
- Registrar mudanças administrativas e separar funções.

Configuração segura deve ser automatizada e testável. Correções manuais não documentadas não são estado confiável.

---

## 19. Desenvolvimento seguro e gates do ciclo de entrega

### 19.1 Requisitos e arquitetura

- Definir requisitos de segurança e privacidade junto aos funcionais.
- Modelar ameaças em fluxos novos ou alterados de alto risco.
- Registrar decisões arquiteturais relevantes.
- Definir abuso, falha e comportamento de recuperação.
- Revisar fronteiras de confiança e exposição de dados.

### 19.2 Implementação e revisão

- Mudanças sensíveis exigem revisão humana independente.
- Revisar autenticação, autorização, validação, criptografia, concorrência e lógica de negócio.
- Comentários devem explicar por que um controle existe; não inserir segredo em comentário.
- Remover debug, bypass, conta padrão e feature flag insegura.
- Não adicionar `TODO de segurança` sem issue, prioridade e responsável.

### 19.3 Automação mínima

O pipeline deveria incluir, conforme a tecnologia:

- testes unitários, integração e segurança negativos;
- SAST;
- SCA e verificação de licença;
- secret scanning e push protection;
- análise de IaC, container e permissões;
- DAST em ambiente controlado para aplicação exposta;
- geração de SBOM;
- validação de assinatura/proveniência do artefato;
- bloqueio por severidade e política definidas.

Scanner não substitui revisão de lógica de negócio, autorização ou isolamento de tenant.

### 19.4 Gestão de vulnerabilidades

- Registrar vulnerabilidade com severidade, evidência, ativo, responsável e prazo.
- Priorizar por risco real, não apenas pelo CVSS.
- Mitigação temporária não encerra o item sem correção ou aceitação formal.
- Testar regressão após a correção.
- Não publicar detalhes exploráveis antes de contenção e coordenação.

---

## 20. Testes mínimos de segurança

Antes de considerar uma mudança concluída, testar o que for aplicável:

### Identidade e acesso

- acesso sem autenticação;
- token ausente, inválido, expirado, revogado e de audiência incorreta;
- logout e invalidação;
- mudança de privilégio e renovação de sessão;
- acesso horizontal e vertical;
- acesso cruzado entre tenants;
- CSRF em operação de escrita;
- proteção contra brute force e recuperação abusiva.

### Entrada e saída

- limites mínimos e máximos;
- campos inesperados e tipos incorretos;
- SQL/NoSQL/LDAP/XPath injection conforme tecnologia;
- XSS armazenado, refletido e baseado em DOM;
- path traversal;
- upload com extensão, MIME e assinatura divergentes;
- XML com entidade externa;
- CSV/formula injection;
- SSRF para loopback, metadados e redes privadas.

### Operação

- rate limit e concorrência;
- timeout e falha do terceiro;
- headers de segurança e HTTPS;
- ausência de segredo em bundle, log, artefato e source map;
- restauração e isolamento de backup quando pertinente;
- alertas e trilha de auditoria;
- dependências e imagem sem vulnerabilidade bloqueante conforme política.

Testes de segurança devem usar dados sintéticos e ambiente autorizado. A IA não deve realizar varredura, exploração ou teste de invasão em sistema externo ou de produção sem autorização explícita e escopo definido.

---

## 21. Resposta a incidentes de segurança e privacidade

Ao suspeitar de incidente:

1. preservar a segurança das pessoas e a continuidade essencial;
2. acionar imediatamente o canal interno de incidente;
3. conter sem destruir evidências;
4. preservar logs, horários, versões, identidades e artefatos relevantes;
5. revogar ou rotacionar credenciais comprometidas;
6. identificar sistemas, tenants, titulares e dados afetados;
7. avaliar confidencialidade, integridade, disponibilidade e risco aos titulares;
8. envolver segurança, jurídico, encarregado e responsáveis pelo negócio;
9. cumprir comunicações regulatórias e contratuais;
10. corrigir causa raiz e registrar lições aprendidas.

Pela regulamentação atual da ANPD, quando um incidente puder acarretar risco ou dano relevante, o controlador deve comunicar a ANPD e os titulares em **três dias úteis**, salvo prazo específico. Informações incompletas podem ser complementadas de forma fundamentada no prazo regulatório. Quando o GDPR for aplicável, a autoridade competente deve ser notificada, em regra, em até **72 horas** após a ciência, salvo quando o incidente for improvável de resultar em risco.

A IA não deve decidir sozinha se há obrigação de notificar, entrar em contato com titular ou produzir declaração pública. Ela deve preservar evidências, informar os responsáveis e apoiar a análise.

---

## 22. Checklist obrigatório antes de finalizar uma alteração

### Segredos e configuração

- [ ] Nenhum segredo está em código, `.env` versionado, log, teste, documentação, bundle ou histórico novo.
- [ ] Nenhum dado sensível usa `NEXT_PUBLIC_*`, `VITE_*`, `REACT_APP_*`, Angular `environment.ts` ou configuração pública.
- [ ] Build e deploy obtêm segredos pelo GitNode ou plataforma aprovada.
- [ ] Credenciais têm escopo mínimo, ambiente correto, rotação e revogação.
- [ ] Secret scanning foi executado ou está ativo no pipeline.

### Autenticação e autorização

- [ ] Token de autenticação não é persistido em `localStorage`.
- [ ] A escolha entre cookie seguro, memória e exceção de `sessionStorage` está documentada.
- [ ] Sessão possui expiração, logout, renovação e revogação.
- [ ] Operações críticas exigem MFA ou step-up conforme risco.
- [ ] Toda autorização é validada no servidor, inclusive objeto, campo e tenant.

### Comunicação e API

- [ ] HTTPS/TLS é obrigatório em todo fluxo sensível.
- [ ] CORS possui allowlist e não substitui autorização.
- [ ] Rate limit, tamanho, paginação, concorrência e timeout estão definidos.
- [ ] Segredos não aparecem em URL, resposta ou mensagem de erro.
- [ ] Webhooks e integrações validam autenticidade e replay.

### Entrada, dados e privacidade

- [ ] Entradas são validadas no servidor com schema e limites.
- [ ] Queries são parametrizadas e saídas são codificadas por contexto.
- [ ] Uploads, XML, CSV, URLs e arquivos possuem controles específicos.
- [ ] Dados pessoais têm finalidade, hipótese legal, retenção e responsáveis identificados.
- [ ] Testes usam dados sintéticos.
- [ ] Transferência internacional e fornecedores foram avaliados quando aplicável.
- [ ] Logs não contêm credenciais, dados de saúde ou conteúdo desnecessário.

### Entrega

- [ ] Testes funcionais e negativos relevantes passaram.
- [ ] SAST, SCA, secret scan e demais gates aplicáveis foram verificados.
- [ ] O diff foi revisado e não contém bypass, debug ou fallback inseguro.
- [ ] Documentação, modelo de ameaças e contratos foram atualizados.
- [ ] Riscos e validações pendentes foram informados sem ocultação.

Se algum item aplicável não puder ser marcado, a alteração não deve ser apresentada como pronta para produção.

---

## 23. Critérios de aceite para código produzido por IA

Código produzido ou alterado por IA só pode ser aceito quando:

- atende ao requisito sem ampliar privilégios ou coleta;
- possui validação no lado confiável;
- não cria segredo no cliente;
- trata autenticação e autorização separadamente;
- mantém isolamento de tenant e ambiente;
- falha de modo seguro;
- produz logs úteis e sanitizados;
- possui testes negativos proporcionais ao risco;
- usa dependências e primitivas mantidas;
- teve diff e fluxo completo revisados por pessoa responsável;
- documenta limitações e decisões;
- não representa conformidade legal ou segurança absoluta sem auditoria.

Para funções de administração, saúde, cobrança, identidade, criptografia, upload, integração ou tratamento em massa, revisão humana especializada é obrigatória.

---

## 24. Referências oficiais e técnicas

As referências abaixo fundamentam a política. Deve-se consultar a versão vigente antes de decisões de alto impacto.

### Brasil

- [Lei nº 13.709/2018 — Lei Geral de Proteção de Dados Pessoais (LGPD)](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- [ANPD — Guia Orientativo sobre Segurança da Informação para Agentes de Tratamento de Pequeno Porte](https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-sobre-seguranca-da-informacao-para-agentes-de-tratamento-de-pequeno-porte)
- [ANPD — Comunicação de Incidente de Segurança](https://www.gov.br/anpd/pt-br/canais_atendimento/agente-de-tratamento/comunicado-de-incidente-de-seguranca-cis)
- [ANPD — Transferência Internacional de Dados](https://www.gov.br/anpd/pt-br/assuntos/assuntos-internacionais/transferencia-internacional-de-dados)
- [ANPD — Resolução CD/ANPD nº 19/2024](https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd/resolucao-cd-anpd-no-19-de-23-de-agosto-de-2024)

### Proteção de dados internacional

- [União Europeia — Regulamento Geral sobre a Proteção de Dados (GDPR)](https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng)
- [EDPB — Guidelines 9/2022 on personal data breach notification under GDPR](https://www.edpb.europa.eu/documents/guideline/guidelines-92022-on-personal-data-breach-notification-under-gdpr_en)
- [U.S. HHS — Covered Entities and Business Associates under HIPAA](https://www.hhs.gov/hipaa/for-professionals/covered-entities/index.html)
- [California Department of Justice — California Consumer Privacy Act](https://oag.ca.gov/privacy/ccpa)

### Sistemas de gestão internacionais

- [ISO/IEC 27001:2022 — Information Security Management Systems](https://www.iso.org/standard/27001)
- [ISO/IEC 27701:2025 — Privacy Information Management Systems](https://www.iso.org/standard/27701)
- [ISO/IEC 27018:2025 — Protection of PII in Public Clouds](https://www.iso.org/standard/27018)

### Engenharia e desenvolvimento seguro

- [NIST SP 800-218 — Secure Software Development Framework (SSDF) 1.1](https://csrc.nist.gov/pubs/sp/800/218/final)
- [NIST SP 800-63B — Authentication and Authenticator Management](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [CISA — Secure by Design](https://www.cisa.gov/resources-tools/resources/secure-by-design)
- [OWASP Application Security Verification Standard 5.0](https://owasp.org/www-project-application-security-verification-standard/)
- [OWASP Cheat Sheet — Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [OWASP Cheat Sheet — Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP Cheat Sheet — HTML5 Security](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html)
- [OWASP Cheat Sheet — REST Security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html)
- [OWASP Cheat Sheet — Input Validation](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html)
- [OWASP Cheat Sheet — Transport Layer Security](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html)
- [OWASP Cheat Sheet — Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP Cheat Sheet — Logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [OWASP Cheat Sheet — Multi-Tenant Security](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html)
- [Next.js — Environment Variables](https://nextjs.org/docs/pages/guides/environment-variables)
- [GitHub — Push protection for secrets](https://docs.github.com/en/code-security/concepts/secret-security/push-protection)

---

Esta política deve ser revisada ao menos anualmente e também após incidente relevante, mudança regulatória, alteração de arquitetura, adoção de nova plataforma de identidade ou segredos, ou entrada do produto em nova jurisdição.
