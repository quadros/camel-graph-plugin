# Arquitetura do Camel Graph Plugin

Este documento detalha a arquitetura, decisões de design e a "Cadeia de Pensamento" (Chain of Thoughts) utilizada na construção deste plugin para visualização de rotas Apache Camel no IntelliJ IDEA.

## Visão Geral

O **Camel Graph Plugin** é uma extensão para o IntelliJ IDEA que transforma código Java contendo rotas Apache Camel (DSL) em uma visualização gráfica interativa. O plugin analisa o código-fonte do projeto, identifica classes que estendem `RouteBuilder`, extrai as definições de rotas e as apresenta como um grafo visual usando bibliotecas web modernas.

### Objetivo

Resolver o problema de complexidade cognitiva ao entender fluxos de rotas Camel em projetos grandes, onde seguir manualmente conexões como `.to("direct:A")` → `from("direct:A")` se torna trabalhoso e propenso a erros.

---

## Chain of Thoughts (Cadeia de Pensamento)

### Fase 1: Entendimento do Problema

**Desafio**: 
- Rotas Apache Camel em projetos grandes tornam-se "espaguete"
- Seguir conexões entre rotas manualmente é cognitivamente custoso
- Falta de visão holística do fluxo de dados

**Solução Proposta**: 
- Automatizar o rastreamento de conexões entre rotas
- Criar uma visualização gráfica intuitiva e interativa
- Permitir navegação visual do fluxo completo

**Analogia**: 
O plugin atua como um "compilador mental": lê a estrutura estática (`.from`, `.to`, `.bean`, etc.) e desenha o "mapa do metrô" das rotas Camel.

### Fase 2: Decisões Técnicas Fundamentais

#### Linguagem: Kotlin

**Decisão**: Utilizar Kotlin como linguagem principal.

**Raciocínio**:
- O IntelliJ Platform SDK é otimizado para Kotlin/Java
- Kotlin oferece sintaxe concisa para lidar com a verbosidade da PSI (Program Structure Interface)
- Interoperabilidade perfeita com APIs Java do IntelliJ
- Type safety e null safety reduzem erros em tempo de execução

#### Parsing: PSI vs Regex

**Decisão**: Usar PSI (Program Structure Interface) do IntelliJ.

**Raciocínio**:
- Regex é frágil e não entende semântica do código
- O Camel DSL é código Java válido, não texto livre
- PSI permite entender a semântica: "Isso é um método chamado `from` que recebe uma String"
- Independe de formatação, espaços ou estilo de código
- Acesso à AST (Abstract Syntax Tree) completa do projeto

**Lógica de Parsing**:
- O parser implementa um `JavaRecursiveElementVisitor`
- Percorre a árvore de sintaxe abstrata (AST) do Java
- Quando encontra uma classe que estende `RouteBuilder`, isola o método `configure()`
- Extrai chamadas de método encadeadas (`.from()`, `.to()`, `.bean()`, etc.)

#### Visualização: JBCefBrowser vs Swing

**Decisão**: HTML/JavaScript via JBCefBrowser (Chromium embutido).

**Raciocínio**:
- Desenhar grafos complexos em Swing puro é trabalhoso e tende a ficar "feio"
- Bibliotecas web como Cytoscape.js são padrão ouro para visualização de grafos
- O IntelliJ permite renderizar uma WebView completa (JBCefBrowser)
- Entrega estética moderna e interatividade rica
- Facilita futuras melhorias na visualização

---

## Arquitetura do Sistema

### Fluxo de Dados

```
┌─────────────────┐
│  Código Java     │  Arquivos .java com classes RouteBuilder
│  (.java files)   │
└────────┬─────────┘
         │
         ▼
┌─────────────────┐
│  IntelliJ PSI    │  AST (Abstract Syntax Tree) do projeto
│  (AST)           │
└────────┬─────────┘
         │
         ▼
┌─────────────────┐
│ CamelPsiParser  │  Extração de rotas, nós e arestas
│  (Extração)      │
└────────┬─────────┘
         │
         ▼
┌─────────────────┐
│  GraphModel     │  Estrutura de dados: nós e arestas
│  (Nós/Arestas)  │
└────────┬─────────┘
         │
         ▼
┌─────────────────┐
│  JSON (Gson)    │  Serialização para formato Cytoscape.js
└────────┬─────────┘
         │
         ▼
┌─────────────────┐
│  WebView        │  Renderização via Cytoscape.js
│  (Cytoscape.js) │
└────────┬─────────┘
         │
         ▼
┌─────────────────┐
│  Visualização   │  Grafo interativo na janela do plugin
│  (Usuário)      │
└─────────────────┘
```

### Componentes Principais

#### 1. **CamelRouteService** (`service/CamelRouteService.kt`)

**Responsabilidade**: Orquestrar a busca e construção do grafo de rotas.

**Funcionalidades**:
- Busca todas as classes que estendem `org.apache.camel.builder.RouteBuilder` no projeto
- Utiliza `ClassInheritorsSearch` para encontrar herdeiros de `RouteBuilder`
- Coordena o parsing de múltiplos arquivos
- Retorna um `CamelRouteGraph` completo

**Padrão**: Service do IntelliJ Platform (nível de projeto)

#### 2. **CamelPsiParser** (`service/CamelPsiParser.kt`)

**Responsabilidade**: Extrair informações de rotas Camel do código usando PSI.

**Funcionalidades**:
- Parsear arquivos Java (`PsiJavaFile`)
- Identificar métodos `configure()` em classes RouteBuilder
- Extrair chamadas de método encadeadas:
  - `.from()` - início de rota
  - `.to()` / `.toD()` - endpoints
  - `.bean()` - invocação de beans
  - `.process()` - processadores
  - `.choice()` - estruturas condicionais
- Construir cadeia de nós e arestas do grafo

**Algoritmo de Parsing**:
1. Visita recursivamente a AST usando `JavaRecursiveElementVisitor`
2. Identifica chamadas `from()` como pontos de início
3. Percorre a cadeia de chamadas encadeadas navegando pelos pais na árvore PSI
4. Extrai URIs e argumentos de cada método
5. Cria nós e arestas no grafo

**Desafio Técnico**: 
A estrutura PSI representa chamadas encadeadas de forma invertida. Por exemplo, `from("a").to("b").to("c")` é representado como:
```
MethodCall: to("c") 
  └─> Reference: to 
      └─> MethodCall: to("b")
          └─> Reference: to
              └─> MethodCall: from("a")
```

O parser navega pelos pais para reconstruir a cadeia correta.

#### 3. **GraphModel** (`model/GraphModel.kt`)

**Responsabilidade**: Representar o grafo de rotas em memória.

**Estrutura**:
- **CamelNode**: Representa um nó no grafo
  - `id`: Identificador único
  - `label`: Rótulo para exibição (URI ou nome)
  - `type`: Tipo do nó (ROUTE_START, ENDPOINT, BEAN, PROCESSOR, CHOICE, UNKNOWN)
  - `filePath`: Caminho do arquivo fonte (opcional)
  - `lineNumber`: Número da linha no código (opcional)

- **CamelEdge**: Representa uma aresta (conexão) no grafo
  - `sourceId`: ID do nó origem
  - `targetId`: ID do nó destino
  - `label`: Rótulo da aresta (opcional)

- **CamelRouteGraph**: Container do grafo
  - `nodes`: Mapa de nós por ID
  - `edges`: Lista de arestas
  - Métodos: `addNode()`, `addEdge()`

#### 4. **GraphHtmlGenerator** (`ui/GraphHtmlGenerator.kt`)

**Responsabilidade**: Gerar HTML/JavaScript para visualização do grafo.

**Funcionalidades**:
- Serializar `CamelRouteGraph` para JSON usando Gson
- Gerar HTML completo com:
  - Importação do Cytoscape.js via CDN
  - Estilos CSS para visualização moderna
  - Script JavaScript para inicialização do grafo
- Formatar dados no formato esperado pelo Cytoscape.js

**Formato de Dados**:
```javascript
[
  { data: { id: "node1", label: "direct:start", type: "ROUTE_START" } },
  { data: { id: "node2", label: "bean:process", type: "BEAN" } },
  { data: { source: "node1", target: "node2" } }
]
```

#### 5. **CamelGraphToolWindowFactory** (`ui/CamelGraphToolWindowFactory.kt`)

**Responsabilidade**: Criar e gerenciar a janela de ferramentas do plugin.

**Funcionalidades**:
- Registrar a janela de ferramentas "CamelGraph" no IntelliJ
- Criar interface com botão "Refresh Graph"
- Integrar JBCefBrowser para renderizar HTML
- Coordenar atualização do grafo quando solicitado
- Tratamento de erros e mensagens informativas

**UI Components**:
- `JBCefBrowser`: Navegador embutido para renderizar HTML
- `JButton`: Botão para atualizar o grafo
- `JPanel`: Container principal com layout BorderLayout

---

## Estrutura de Pacotes

```
com.example.camelgraph/
├── model/
│   └── GraphModel.kt          # Modelos de dados (CamelNode, CamelEdge, CamelRouteGraph)
├── service/
│   ├── CamelRouteService.kt   # Serviço principal de orquestração
│   └── CamelPsiParser.kt      # Parser PSI para extração de rotas
└── ui/
    ├── CamelGraphToolWindowFactory.kt  # Factory da janela de ferramentas
    └── GraphHtmlGenerator.kt          # Gerador de HTML/JS para visualização
```

---

## Decisões de Design

### 1. Uso de PSI ao invés de Análise Estática Simples

**Vantagens**:
- Entende a semântica do código, não apenas texto
- Funciona independente de formatação
- Acesso a informações de tipo e resolução de referências
- Integração nativa com o IntelliJ IDEA

**Trade-offs**:
- Requer que o projeto esteja indexado pelo IntelliJ
- Depende da estrutura PSI do IntelliJ Platform

### 2. Busca de Classes via ClassInheritorsSearch

**Vantagens**:
- Utiliza índices do IntelliJ (rápido e eficiente)
- Encontra todas as classes RouteBuilder automaticamente
- Não requer varredura manual de arquivos

**Limitações**:
- Requer que o projeto esteja indexado
- Depende da resolução correta de classes (Apache Camel no classpath)

### 3. Visualização Web (Cytoscape.js)

**Vantagens**:
- Visualização moderna e interativa
- Suporte a layouts automáticos (cose, dagre, etc.)
- Fácil customização de estilos
- Zoom, pan, drag-and-drop nativos

**Trade-offs**:
- Requer conexão com internet (CDN) ou bundling local
- Dependência de biblioteca externa

### 4. Modelo de Dados Simples

**Decisão**: Estrutura de dados plana (nós + arestas).

**Vantagens**:
- Simplicidade de implementação
- Fácil serialização para JSON
- Compatível com formatos de grafo padrão

**Limitações Futuras**:
- Não captura hierarquia complexa (ex: choice/when/otherwise)
- Não conecta rotas que se referem entre si (ex: `.to("direct:A")` → `from("direct:A")`)

---

## Limitações Conhecidas

### 1. Conexão entre Rotas

**Status**: Não implementado

**Descrição**: O plugin atualmente visualiza cada rota individualmente, mas não conecta automaticamente rotas que se referem entre si. Por exemplo:
- Rota A: `.to("direct:process")`
- Rota B: `from("direct:process")`

Essas duas rotas aparecem como desconectadas no grafo.

**Solução Futura**: 
- Pós-processar o grafo após parsing completo
- Identificar endpoints `direct:` e `seda:` que aparecem tanto em `.to()` quanto em `.from()`
- Criar arestas virtuais conectando essas rotas

### 2. Estruturas Complexas

**Status**: Suporte parcial

**Descrição**: Estruturas como `choice()`, `when()`, `otherwise()` são identificadas mas não totalmente expandidas. Apenas o nó `choice` é criado, sem detalhamento das condições.

**Solução Futura**:
- Expandir estruturas condicionais em subgrafos
- Visualizar branches de `when()` e `otherwise()`

### 3. Expressões Dinâmicas

**Status**: Suporte limitado

**Descrição**: URIs definidas dinamicamente (ex: variáveis, concatenação de strings) são extraídas como texto, mas podem não ser totalmente precisas.

**Exemplo**:
```java
.to("direct:" + routeName)  // Extraído como texto, não como valor resolvido
```

### 4. Dependência de CDN

**Status**: ✅ Resolvido

**Descrição**: O Cytoscape.js era carregado via CDN, requerendo conexão com internet e causando problemas de segurança em redes corporativas.

**Solução Implementada**: 
- Bundle do Cytoscape.js incluído localmente no plugin
- JavaScript carregado de recursos estáticos (`/js/cytoscape.min.js`)
- Não requer conexão com internet
- Compatível com políticas de segurança corporativas

---

## Melhorias Futuras

### Curto Prazo
1. **Conexão entre Rotas**: Implementar detecção e conexão de rotas que se referem entre si
2. ✅ **Bundle Local do Cytoscape.js**: Implementado - Removida dependência de CDN
3. **Melhor Tratamento de Erros**: Mensagens mais informativas e logging

### Médio Prazo
1. **Expansão de Estruturas Complexas**: Visualizar `choice()`, `split()`, `aggregate()` em detalhes
2. **Filtros e Busca**: Permitir filtrar rotas por tipo, arquivo ou padrão
3. **Exportação**: Exportar grafo como imagem (PNG, SVG) ou formato de grafo (GraphML, DOT)

### Longo Prazo
1. **Análise Estática Avançada**: Detectar problemas comuns (rotas órfãs, ciclos, etc.)
2. **Integração com Debugger**: Conectar visualização com execução de rotas
3. **Suporte a Múltiplos Formatos**: Suporte para XML DSL e YAML DSL do Camel

---

## Dependências Principais

- **IntelliJ Platform SDK 2024.1**: Framework base do plugin
- **Kotlin 1.9.24**: Linguagem de programação
- **Gson 2.10.1**: Serialização JSON
- **Cytoscape.js 3.28.1**: Visualização de grafos (bundle local, sem CDN)

---

## Padrões e Convenções

### Padrões Utilizados
- **Service Pattern**: `CamelRouteService` como serviço do IntelliJ Platform
- **Visitor Pattern**: `JavaRecursiveElementVisitor` para percorrer AST
- **Factory Pattern**: `CamelGraphToolWindowFactory` para criação de UI
- **Builder Pattern**: Encadeamento de métodos no Camel DSL

### Convenções de Código
- Nomes em camelCase para métodos e variáveis
- Classes em PascalCase
- Pacotes seguindo estrutura de domínio (`com.example.camelgraph`)
- Documentação inline para lógica complexa

---

## Referências

- [IntelliJ Platform SDK Documentation](https://plugins.jetbrains.com/docs/intellij/)
- [Apache Camel Documentation](https://camel.apache.org/manual/)
- [Cytoscape.js Documentation](https://js.cytoscape.org/)
- [PSI (Program Structure Interface) Guide](https://plugins.jetbrains.com/docs/intellij/psi.html)
