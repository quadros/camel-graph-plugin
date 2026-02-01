# Análise de Segurança - Camel Graph Plugin

**Data da Análise**: 2026-02-01  
**Analista**: Expert em Segurança da Informação  
**Versão Analisada**: 1.0.5

---

## Resumo Executivo

O Camel Graph Plugin é um **plugin local** (IntelliJ/JBCefBrowser) que renderiza HTML/JS com dados extraídos do código do usuário. Por isso, o principal risco prático é **injeção de conteúdo/script (XSS) dentro do WebView do IDE** e **exposição de informações sensíveis** no grafo.

Nesta revisão (2026-02-01) não foram encontradas vulnerabilidades **críticas** evidentes no código atual. Foram identificados **2 riscos de severidade MÉDIA** e **2 riscos BAIXOS**, principalmente relacionados a:

- CSP com `unsafe-inline` (necessário hoje pela forma de geração do HTML)
- Possível exposição de segredos presentes em URIs (ex.: credenciais embutidas)

**Severidade Geral**: 🟡 **MÉDIA** (pela superfície XSS inerente a HTML/JS inline + risco de segredos em URIs)

---

## Vulnerabilidades / Riscos Identificados

### 🟡 MÉDIO - VUL-2026-001: CSP permissiva (`unsafe-inline`) aumenta impacto de XSS

**Localização**: `src/main/kotlin/com/example/camelgraph/ui/GraphHtmlGenerator.kt` (meta CSP)

**Descrição**:
O HTML gerado define CSP com `script-src 'unsafe-inline'` e `style-src 'unsafe-inline'`. Isso reduz a efetividade da CSP contra XSS, pois permite execução de scripts inline caso um payload consiga ser injetado.

**Código relevante**:
```kotlin
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';">
```

**Impacto**:
- Se houver uma falha de sanitização futura, o payload teria mais chance de executar no WebView

**Recomendação**:
- Migrar para scripts/estilos **não-inline**:
  - carregar Cytoscape via `<script src="...">` (recurso local)
  - mover o JS do grafo para um arquivo local (ex.: `/js/graph.js`)
  - usar CSP sem `unsafe-inline` (ou com nonce gerado por render)

**Prioridade**: 🟡 MÉDIA

---

### 🟡 MÉDIO - VUL-2026-002: Exposição potencial de segredos em URIs exibidas no grafo

**Localização**: `CamelPsiParser.kt` (extração de URI/label) + `GraphHtmlGenerator.kt` (exibição de labels)

**Descrição**:
URIs Camel podem conter informações sensíveis (ex.: `ftp://user:pass@host`, `?password=...`, `token=...`). O plugin exibe a URI (ou parte dela) como label do nó/edge. Mesmo sendo “local”, isso pode vazar segredos em:

- screenshots / screen sharing
- logs de suporte (prints)
- gravações de tela

**Impacto**:
- Exposição de credenciais/tokens presentes no código (ou em constantes) para terceiros

**Recomendação**:
- Implementar **redação** (masking) em `SecurityUtils.sanitizeUri` e/ou `sanitizeLabel`, por exemplo:
  - mascarar `user:pass@` em URIs
  - mascarar query params típicos: `password`, `passwd`, `pwd`, `secret`, `token`, `apikey`, `key`
  - opcional: preferir exibir apenas o “scheme + endpoint lógico” (ex.: `direct:foo`, `seda:bar`) e ocultar componentes sensíveis

**Prioridade**: 🟡 MÉDIA

---

### 🟢 BAIXO - VUL-2026-003: JavaScript inline grande aumenta risco de regressão de sanitização

**Localização**: `GraphHtmlGenerator.kt`

**Descrição**:
O HTML/JS é montado como uma string grande. Isso aumenta o risco de regressões (um futuro `innerHTML`, interpolação sem sanitização, etc.). Hoje não foi encontrado uso de `eval`, `innerHTML` ou `document.write`.

**Recomendação**:
- Extrair JS do grafo para arquivo local versionado e testável (`/js/graph.js`)
- Adicionar testes de sanitização (unit tests) para `SecurityUtils` e geração do JSON

**Prioridade**: 🟢 BAIXA

---

### 🟢 BAIXO - VUL-2026-004: Performance/DoS local por grafos muito grandes

**Localização**: `GraphHtmlGenerator.kt` (loops de layout/packing e listeners)

**Descrição**:
Projetos com muitas rotas podem gerar grafos grandes. Embora existam limites de tamanho (`sanitizeUri` e `sanitizeLabel`), ainda há risco de “DoS local” (IDE lento) por layout/packing e quantidade de nós/edges.

**Recomendação**:
- Introduzir limites de render:
  - cap de nós/edges renderizados (com mensagem “grafo muito grande”)
  - opção de filtrar por pacote/classe/rota
- Evitar re-layout completo quando usuário apenas arrasta containers

**Prioridade**: 🟢 BAIXA

---

## Análise de Dependências (inventário)

**Nota**: esta revisão foi feita offline (sem consulta ativa a bases CVE). Recomenda-se rodar ferramentas automatizadas (Dependabot/OWASP Dependency Check) no pipeline.

### Kotlin `1.9.24`
- **Status**: ⚠️ Não verificado automaticamente por CVE nesta execução
- **Ação**: manter atualizado e monitorar advisories JetBrains

### Gson `2.10.1`
- **Status**: ⚠️ Não verificado automaticamente por CVE nesta execução
- **Ação**: manter atualizado e monitorar advisories

### Cytoscape.js (bundle local em `src/main/resources/js/cytoscape.min.js`)
- **Status**: ⚠️ Não verificado automaticamente por CVE nesta execução
- **Ação**:
  - manter versão registrada/documentada
  - atualizar periodicamente
  - validar integridade (origem do bundle) antes de releases

---

## Boas Práticas de Segurança Implementadas

✅ **Sem dependência de CDN**: Cytoscape.js incluído localmente  
✅ **CSP presente** (ainda que permissiva por `unsafe-inline`)  
✅ **Escape HTML** para labels/mensagens (`SecurityUtils.escapeHtml`)  
✅ **Sanitização e limites de tamanho** (`sanitizeUri`, `sanitizeLabel`)  
✅ **Sanitização de file paths** para reduzir exposição de dados do ambiente  
✅ **Sem uso de `eval/innerHTML/document.write`** no código Kotlin/JS gerado

---

## Recomendações Gerais

### Imediatas (antes do próximo release)
1. **Mask de segredos em URIs exibidas** (VUL-2026-002)
2. **Automatizar varredura de dependências** (SCA) no pipeline

### Curto prazo (próxima versão)
3. Remover `unsafe-inline` movendo scripts/estilos para arquivos locais (VUL-2026-001)
4. Testes unitários para `SecurityUtils` e geração do JSON do grafo

### Médio prazo
5. Controles de tamanho do grafo (cap, filtros, paginação/virtualização)

---

## Plano de Ação Prioritário

### Fase 1 - Médio (prioritário)
- [ ] VUL-2026-002: Redação de segredos em URIs/labels
- [ ] VUL-2026-001: Remover `unsafe-inline` via scripts/estilos externos locais

### Fase 2 - Baixo (melhoria)
- [ ] VUL-2026-004: Limites de render/layout para grafos grandes
- [ ] VUL-2026-003: Refatorar JS inline para arquivo local + testes

---

## Conclusão

O plugin apresenta um bom baseline de segurança para um cenário local (IDE), com sanitização e sem dependências externas. O principal risco residual vem de **HTML/JS inline** (CSP permissiva) e da **possível exposição de segredos presentes em URIs**.

**Recomendação Final**: 🟡 **APROVADO COM RESSALVAS** (aplicar masking de segredos e melhorar CSP no próximo ciclo)
