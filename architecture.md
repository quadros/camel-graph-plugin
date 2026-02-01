# Arquitetura do Camel Graph Plugin

Este documento descreve a arquitetura atual do **Camel Graph Plugin**, com foco em **manutenibilidade** (entender rápido “onde mexer”) e em **como o grafo final é produzido** a partir de rotas Camel em Java DSL.

## Visão Geral

O plugin analisa código Java do projeto via PSI, extrai rotas Camel (principalmente `configure()`), constrói um modelo de grafo em memória e renderiza uma visualização interativa em uma Tool Window via **JBCefBrowser + Cytoscape.js (bundle local)**.

### Objetivo

Reduzir a complexidade cognitiva para entender fluxos Camel em projetos grandes, incluindo:

- Conectar referências entre rotas espalhadas em diferentes arquivos (`.to("direct:X")` ↔ `from("direct:X")`)
- Agrupar visualmente rotas por classe/arquivo (containers)
- Melhorar legibilidade (labels de edges, espaçamento e layout)

---

## Fluxo de Dados (alto nível)

```
┌──────────────────────────┐
│ Projeto Java (RouteBuilder│
│ + configure())            │
└───────────┬──────────────┘
            │ PSI / índices
            ▼
┌──────────────────────────┐
│ CamelRouteService         │  (orquestra varredura)
└───────────┬──────────────┘
            ▼
┌──────────────────────────┐
│ CamelPsiParser            │  (extrai rotas e cadeias .from/.to/.choice/...)
│ - IDs determinísticos p/  │
│   URIs (cross-file link)  │
│ - Containers por classe   │
└───────────┬──────────────┘
            ▼
┌──────────────────────────┐
│ CamelRouteGraph (model)   │
│ - CamelNode (parentId)    │
│ - CamelEdge (label)       │
└───────────┬──────────────┘
            ▼
┌──────────────────────────┐
│ GraphHtmlGenerator        │
│ - JSON (Gson) -> Cytoscape│
│ - Layout por container    │
│ - Drag de container       │
│ - Pinch zoom (trackpad)   │
└───────────┬──────────────┘
            ▼
┌──────────────────────────┐
│ JBCefBrowser (ToolWindow) │
│ Cytoscape.js (local)      │
└──────────────────────────┘
```

---

## Principais Classes e Responsabilidades

### `service/CamelRouteService.kt`

- **Responsabilidade**: Orquestrar a construção do grafo do projeto inteiro.
- **Como funciona**:
  - Tenta localizar `org.apache.camel.builder.RouteBuilder` via `JavaPsiFacade` (inclui libs).
  - Busca herdeiros via `ClassInheritorsSearch` (escopo do projeto).
  - Complementa com heurísticas para:
    - classes `extends RouteBuilder` mesmo quando índices falham
    - `new RouteBuilder(){...}` (classes anônimas, comuns em Spring `@Bean`)
  - Para cada arquivo Java candidato, chama `CamelPsiParser.parseFile(...)`.

### `service/CamelPsiParser.kt`

- **Responsabilidade**: Ler PSI de `configure()` e transformar em nós/arestas.

#### Extração de entrypoints

- Entry points suportados:
  - `from`, `fromD`, `fromF` → `NodeType.ROUTE_START`
  - `rest` (mantido como entry point visual, mas não participa do link `.to` ↔ `from` da mesma forma)

#### Cadeia Java DSL (chamadas encadeadas)

- Caminha “para cima” no PSI (estrutura invertida) para reconstruir a sequência.
- Cria nós para:
  - `to` / `toD` → `NodeType.ENDPOINT`
  - `bean` → `NodeType.BEAN` (label tenta usar o método do bean)
  - `process` → `NodeType.PROCESSOR`
  - `choice` → `NodeType.CHOICE` e cria edges de branches via `when/otherwise` com label de condição
  - `doCatch` → `NodeType.DO_CATCH`
- `end()` é tratado como **delimitador** (não vira nó no grafo).

#### Linking cross-file (sem pós-processamento)

O link entre `.to("direct:X")` e `from("direct:X")` é obtido por **IDs determinísticos**:

- Nós de URIs usam IDs estáveis: `uri:<URI_sanitizada>`
- Assim, se `from("direct:X")` e `.to("direct:X")` aparecem em arquivos diferentes, eles se fundem no mesmo `CamelNode` (chave do map) e o fluxo fica conectado naturalmente.

#### Containers por classe (compound nodes)

- Para cada classe com `configure()`, é criado um nó `NodeType.CONTAINER`.
- Nós extraídos recebem `parentId` apontando para o container (compound node do Cytoscape).
- Para um `.to(URI)` que referencia uma rota definida em outra classe, o nó do endpoint é associado ao container “dono” dessa `from(URI)` (quando conhecido), permitindo edges atravessando containers.

### `model/GraphModel.kt`

- **Responsabilidade**: Modelo em memória do grafo.
- **Pontos importantes**:
  - `CamelNode.parentId`: habilita compound nodes no Cytoscape (containers).
  - `CamelRouteGraph.addNode(...)`: faz **merge** quando o mesmo `id` reaparece (crítico para dedupe cross-file):
    - prioriza `ROUTE_START` quando um mesmo URI aparece como `from` e `to`
    - preserva `parentId/filePath/lineNumber` mais “informativos”

### `ui/GraphHtmlGenerator.kt`

- **Responsabilidade**: Renderização HTML/JS para o JBCefBrowser.
- **Principais features**:
  - Cytoscape.js é carregado **localmente** (`/js/cytoscape.min.js` embutido no plugin).
  - Define estilos por tipo de nó (ROUTE_START, CHOICE, BEAN, PROCESSOR, ENDPOINT, CONTAINER, DO_CATCH).
  - **Legibilidade**:
    - labels de edges com fundo e margem (ex.: condições de `choice`)
    - fonte maior para edges e containers
  - **Organização**:
    - layout determinístico por container (`breadthfirst`, `spacingFactor`)
    - `packContainers()` para evitar overlap visual entre containers
  - **Interação**:
    - drag de container movendo descendentes (compound drag manual)
    - tentativa de suporte a pinch-to-zoom no trackpad (wheel+ctrlKey / gesture events), levando em conta peculiaridades do JCEF

### `ui/CamelGraphToolWindowFactory.kt`

- **Responsabilidade**: Tool Window e UX “refresh”.
- **Pontos importantes**:
  - Executa `buildGraph()` em `ReadAction`.
  - Trata `DumbService`/indexing, `IndexNotReadyException` e `ProcessCanceledException`.
  - Mensagens HTML de erro **escapadas** (XSS mitigado) via `SecurityUtils.escapeHtml`.

### `util/SecurityUtils.kt`

- **Responsabilidade**: Sanitização/escape de dados exibidos no HTML.
- **Pontos importantes**:
  - `sanitizeLabel` → `escapeHtml` + limite de tamanho (DoS)
  - `sanitizeFilePath` → remove base path e padrões de username
  - `sanitizeUri` → remove caracteres perigosos comuns e limita tamanho

---

## Estrutura de Pacotes

```
com.example.camelgraph/
├── model/
│   └── GraphModel.kt
├── service/
│   ├── CamelRouteService.kt
│   └── CamelPsiParser.kt
├── ui/
│   ├── CamelGraphToolWindowFactory.kt
│   └── GraphHtmlGenerator.kt
└── util/
    └── SecurityUtils.kt
```

---

## Limitações Conhecidas (estado atual)

### Conexão entre rotas (cross-file)

- **Status**: ✅ implementado por **match textual** (ID determinístico por URI).
- **Limitação**: URIs dinâmicas/constantes só conectam se o texto extraído for idêntico (não há avaliação/execução de constantes).

### Estruturas complexas

- `choice/when/otherwise`: suportado com labels nos edges (condição).
- Outras estruturas Camel (ex.: `split`, `aggregate`, `doTry/doFinally`, etc.) ainda não têm representação dedicada.

### Layout/UX

- O layout combina: `breadthfirst` (por container) + “packing” de containers para evitar overlap.
- O drag de containers é implementado manualmente (mover descendentes) por limitações do Cytoscape core com compounds.

---

## Dependências Principais

- **IntelliJ Platform**: alvo `2023.2+` (Gradle IntelliJ Plugin)
- **Kotlin**: `1.9.24`
- **Gson**: `2.10.1`
- **Cytoscape.js**: bundle local em `src/main/resources/js/cytoscape.min.js`

---

## Referências

- Documentação do SDK IntelliJ: `https://plugins.jetbrains.com/docs/intellij/`
- Apache Camel: `https://camel.apache.org/manual/`
- Cytoscape.js: `https://js.cytoscape.org/`
