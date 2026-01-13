# Prompt de Requisitos - Camel Graph Plugin

## Contexto e Objetivo do Projeto

Você está desenvolvendo um **plugin para o IntelliJ IDEA** que visa resolver um problema crítico enfrentado por desenvolvedores que trabalham com **Apache Camel**: a dificuldade de compreender e navegar por rotas Camel complexas em projetos grandes.

### Problema a Resolver

Em projetos Apache Camel de grande escala, as rotas tornam-se "espaguete" - múltiplas rotas interconectadas através de endpoints `direct:` e `seda:` tornam-se difíceis de rastrear manualmente. Seguir conexões como `.to("direct:A")` → `from("direct:A")` manualmente é:
- **Cognitivamente custoso**: Requer manter múltiplos contextos mentais
- **Propenso a erros**: Fácil perder conexões ou seguir caminhos errados
- **Trabalhoso**: Em projetos com dezenas ou centenas de rotas, torna-se impraticável
- **Sem visão holística**: Impossível ver o fluxo completo de dados de uma vez

### Solução Proposta

Criar uma **visualização gráfica interativa** das rotas Apache Camel que:
- Analisa automaticamente o código-fonte do projeto
- Identifica e extrai todas as rotas Camel definidas em Java DSL
- Apresenta as rotas como um grafo visual navegável
- Permite compreensão rápida do fluxo de dados entre componentes

**Analogia**: O plugin deve atuar como um "compilador mental" que lê a estrutura estática do código (`.from`, `.to`, `.bean`, etc.) e desenha o "mapa do metrô" das rotas Camel.

---

## Requisitos Funcionais

### RF-001: Análise Automática de Código-Fonte

**Descrição**: O plugin deve analisar automaticamente o código-fonte do projeto para identificar classes que contêm rotas Apache Camel.

**Critérios de Aceitação**:
- ✅ Deve identificar todas as classes que estendem `org.apache.camel.builder.RouteBuilder`
- ✅ Deve funcionar em projetos Java que utilizam Apache Camel
- ✅ Deve utilizar a API PSI (Program Structure Interface) do IntelliJ para análise semântica
- ✅ Não deve depender de análise textual (regex), mas sim de análise estrutural (AST)
- ✅ Deve funcionar independente de formatação de código ou estilo de escrita

**Prioridade**: 🔴 **CRÍTICA**

**Notas Técnicas**:
- Usar `ClassInheritorsSearch` para encontrar classes RouteBuilder
- Utilizar `JavaPsiFacade` para resolução de classes
- Escopo limitado ao projeto atual (não dependências externas)

---

### RF-002: Extração de Rotas Camel

**Descrição**: O plugin deve extrair informações sobre rotas Camel do método `configure()` das classes RouteBuilder.

**Critérios de Aceitação**:
- ✅ Deve identificar e extrair chamadas `.from()` como pontos de início de rota
- ✅ Deve identificar e extrair chamadas `.to()` e `.toD()` como endpoints
- ✅ Deve identificar e extrair chamadas `.bean()` como invocações de beans
- ✅ Deve identificar e extrair chamadas `.process()` como processadores
- ✅ Deve identificar e extrair chamadas `.choice()` como estruturas condicionais
- ✅ Deve extrair URIs dos argumentos de cada método
- ✅ Deve preservar a ordem e encadeamento das chamadas de método
- ✅ Deve funcionar com chamadas encadeadas: `from("a").to("b").to("c")`

**Prioridade**: 🔴 **CRÍTICA**

**Notas Técnicas**:
- Implementar `JavaRecursiveElementVisitor` para percorrer AST
- Navegar pela estrutura PSI invertida de chamadas encadeadas
- Extrair URIs de `PsiLiteralExpression` quando possível
- Tratar expressões dinâmicas como texto quando necessário

---

### RF-003: Construção de Modelo de Grafo

**Descrição**: O plugin deve construir um modelo de grafo representando as rotas extraídas.

**Critérios de Aceitação**:
- ✅ Deve criar nós (nodes) para cada componente identificado (`.from()`, `.to()`, `.bean()`, etc.)
- ✅ Deve criar arestas (edges) conectando componentes na ordem de execução
- ✅ Cada nó deve conter:
  - Identificador único (UUID)
  - Label (URI ou nome do componente)
  - Tipo (ROUTE_START, ENDPOINT, BEAN, PROCESSOR, CHOICE, UNKNOWN)
  - Caminho do arquivo fonte (opcional, sanitizado)
  - Número da linha no código (opcional)
- ✅ Cada aresta deve conter:
  - ID do nó origem
  - ID do nó destino
  - Label opcional
- ✅ Deve evitar duplicação de nós (mesmo componente não deve aparecer múltiplas vezes)

**Prioridade**: 🔴 **CRÍTICA**

**Notas Técnicas**:
- Usar estrutura de dados: `CamelRouteGraph` contendo `Map<String, CamelNode>` e `List<CamelEdge>`
- IDs devem ser UUIDs para garantir unicidade
- Caminhos de arquivo devem ser sanitizados para remover informações sensíveis

---

### RF-004: Visualização Gráfica Interativa

**Descrição**: O plugin deve apresentar o grafo de rotas em uma visualização gráfica interativa.

**Critérios de Aceitação**:
- ✅ Deve renderizar o grafo usando biblioteca de visualização de grafos (Cytoscape.js)
- ✅ Deve exibir nós com diferentes estilos visuais baseados no tipo:
  - Nós de início de rota (`.from()`) devem ser destacados visualmente (ex: verde, formato elipse)
  - Outros nós devem ter estilo padrão (ex: cinza, formato retângulo arredondado)
- ✅ Deve exibir arestas conectando os nós mostrando o fluxo
- ✅ Deve permitir interação do usuário:
  - Arrastar nós para reorganizar
  - Zoom in/out com roda do mouse
  - Pan (arrastar o canvas)
- ✅ Deve usar layout automático (ex: `cose` layout do Cytoscape.js)
- ✅ Deve exibir labels nos nós mostrando URIs ou nomes dos componentes

**Prioridade**: 🔴 **CRÍTICA**

**Notas Técnicas**:
- Usar `JBCefBrowser` (Chromium embutido) para renderizar HTML/JavaScript
- Cytoscape.js deve ser incluído localmente (bundle), não via CDN
- HTML gerado deve incluir Content Security Policy (CSP)
- Todos os dados devem ser sanitizados antes de renderização

---

### RF-005: Interface de Usuário no IntelliJ

**Descrição**: O plugin deve fornecer uma interface de usuário integrada ao IntelliJ IDEA.

**Critérios de Aceitação**:
- ✅ Deve criar uma janela de ferramentas (Tool Window) chamada "CamelGraph"
- ✅ A janela deve estar acessível na barra lateral do IntelliJ
- ✅ Deve conter um botão "Refresh Graph" para atualizar a visualização
- ✅ Deve exibir mensagens apropriadas quando:
  - Nenhuma rota é encontrada no projeto
  - Ocorre um erro durante a análise
- ✅ Deve ser acessível via menu: `View` → `Tool Windows` → `CamelGraph`
- ✅ Deve manter estado entre atualizações (quando possível)

**Prioridade**: 🔴 **CRÍTICA**

**Notas Técnicas**:
- Implementar `ToolWindowFactory` para criar a janela
- Usar `JPanel` com `BorderLayout` para organização
- Integrar `JBCefBrowser` para renderização do grafo

---

### RF-006: Atualização Manual do Grafo

**Descrição**: O usuário deve poder solicitar atualização do grafo manualmente.

**Critérios de Aceitação**:
- ✅ Deve haver um botão "Refresh Graph" visível na interface
- ✅ Ao clicar, deve re-analisar o projeto e atualizar o grafo
- ✅ Deve mostrar feedback visual durante o processamento (se possível)
- ✅ Deve lidar graciosamente com erros durante a atualização

**Prioridade**: 🟡 **ALTA**

---

### RF-007: Tratamento de Erros

**Descrição**: O plugin deve tratar erros de forma adequada e informar o usuário.

**Critérios de Aceitação**:
- ✅ Deve capturar exceções durante análise do código
- ✅ Deve exibir mensagens de erro amigáveis ao usuário
- ✅ Mensagens de erro devem ser sanitizadas (sem XSS)
- ✅ Não deve expor stack traces completos ao usuário (segurança)
- ✅ Deve continuar funcionando mesmo se algumas rotas falharem ao parsear

**Prioridade**: 🟡 **ALTA**

**Notas Técnicas**:
- Usar `try-catch` em pontos críticos
- Sanitizar mensagens de erro com `SecurityUtils.escapeHtml()`
- Logar erros detalhados internamente (se logging implementado)

---

## Requisitos Não Funcionais

### RNF-001: Performance

**Descrição**: O plugin deve ter performance adequada para projetos de tamanho médio a grande.

**Critérios de Aceitação**:
- ✅ Análise de projeto com até 100 classes RouteBuilder deve completar em menos de 10 segundos
- ✅ Renderização do grafo deve ser instantânea após análise
- ✅ Não deve bloquear a UI do IntelliJ durante análise (execução assíncrona preferível)
- ✅ Uso de memória deve ser razoável (não exceder 100MB para projetos médios)

**Prioridade**: 🟡 **ALTA**

**Notas Técnicas**:
- Usar índices do IntelliJ (`ClassInheritorsSearch`) para busca eficiente
- Processar arquivos sequencialmente, não carregar tudo em memória
- Considerar processamento assíncrono para projetos muito grandes

---

### RNF-002: Segurança

**Descrição**: O plugin deve ser seguro e não introduzir vulnerabilidades de segurança.

**Critérios de Aceitação**:
- ✅ Todos os dados interpolados em HTML devem ser sanitizados
- ✅ Mensagens de erro devem ser escapadas para prevenir XSS
- ✅ Caminhos de arquivo devem ser sanitizados para remover informações sensíveis
- ✅ Não deve depender de recursos externos (CDN) que possam ser bloqueados por firewall
- ✅ Deve implementar Content Security Policy (CSP) no HTML gerado
- ✅ Deve validar e limitar tamanho de dados de entrada para prevenir DoS

**Prioridade**: 🔴 **CRÍTICA**

**Notas Técnicas**:
- Implementar `SecurityUtils` com funções de sanitização
- Escape HTML para todos os dados de saída
- Sanitizar caminhos removendo nomes de usuário e informações sensíveis
- Limitar tamanho de URIs e labels (ex: 1000 caracteres para URIs, 200 para labels)
- Bundle local de bibliotecas JavaScript (não CDN)

---

### RNF-003: Compatibilidade

**Descrição**: O plugin deve ser compatível com versões específicas do IntelliJ IDEA e Apache Camel.

**Critérios de Aceitação**:
- ✅ Deve funcionar com IntelliJ IDEA 2024.1 ou superior
- ✅ Deve funcionar com projetos que usam Apache Camel (qualquer versão que tenha `RouteBuilder`)
- ✅ Deve funcionar com Java 21 (target do plugin)
- ✅ Deve funcionar em sistemas operacionais suportados pelo IntelliJ (Windows, macOS, Linux)

**Prioridade**: 🟡 **ALTA**

**Notas Técnicas**:
- Configurar `sinceBuild` e `untilBuild` no `plugin.xml`
- Testar com diferentes versões do Apache Camel
- Usar APIs estáveis do IntelliJ Platform SDK

---

### RNF-004: Manutenibilidade

**Descrição**: O código deve ser bem estruturado e fácil de manter.

**Critérios de Aceitação**:
- ✅ Código deve seguir padrões de design apropriados (Service, Visitor, Factory)
- ✅ Deve ter separação clara de responsabilidades (model, service, ui)
- ✅ Deve ter documentação inline para lógica complexa
- ✅ Deve usar convenções de nomenclatura consistentes
- ✅ Deve ser extensível para futuras melhorias

**Prioridade**: 🟢 **MÉDIA**

**Notas Técnicas**:
- Estrutura de pacotes: `model/`, `service/`, `ui/`, `util/`
- Usar Kotlin para sintaxe concisa e type safety
- Documentar decisões arquiteturais importantes

---

### RNF-005: Usabilidade

**Descrição**: A interface deve ser intuitiva e fácil de usar.

**Critérios de Aceitação**:
- ✅ Interface deve ser auto-explicativa (botão "Refresh Graph" claro)
- ✅ Visualização deve ser esteticamente agradável (tema escuro, cores apropriadas)
- ✅ Deve fornecer feedback visual quando nenhuma rota é encontrada
- ✅ Deve ser acessível via menu padrão do IntelliJ
- ✅ Não deve requerer configuração adicional do usuário

**Prioridade**: 🟡 **ALTA**

**Notas Técnicas**:
- Usar tema escuro consistente com IntelliJ
- Mensagens claras e informativas
- Layout responsivo que se adapta ao tamanho da janela

---

### RNF-006: Confiabilidade

**Descrição**: O plugin deve ser confiável e não causar crashes no IntelliJ.

**Critérios de Aceitação**:
- ✅ Não deve causar crashes ou freezes do IntelliJ IDEA
- ✅ Deve lidar graciosamente com projetos malformados ou incompletos
- ✅ Deve funcionar mesmo se Apache Camel não estiver no classpath (mostrar mensagem apropriada)
- ✅ Deve validar dados antes de processar para evitar exceções

**Prioridade**: 🟡 **ALTA**

**Notas Técnicas**:
- Usar safe calls (`?.`) e elvis operator (`?:`) para null safety
- Validar entrada antes de processar
- Tratar casos edge (projetos vazios, sem RouteBuilder, etc.)

---

### RNF-007: Portabilidade

**Descrição**: O plugin deve funcionar em diferentes ambientes corporativos.

**Critérios de Aceitação**:
- ✅ Deve funcionar sem conexão com internet (bundle local de dependências)
- ✅ Deve funcionar em redes corporativas com firewalls restritivos
- ✅ Não deve depender de serviços externos ou CDNs
- ✅ Deve respeitar políticas de segurança corporativas

**Prioridade**: 🟡 **ALTA**

**Notas Técnicas**:
- Incluir Cytoscape.js como recurso local
- Não fazer requisições HTTP externas
- Validar que todas as dependências são locais

---

## Restrições e Limitações

### RST-001: Escopo de Análise

**Descrição**: O plugin analisa apenas o projeto atual, não dependências externas.

**Limitações**:
- Não analisa rotas definidas em bibliotecas externas (JARs)
- Não analisa rotas definidas em XML DSL ou YAML DSL (apenas Java DSL)
- Requer que o projeto esteja indexado pelo IntelliJ

**Justificativa**: Foco inicial em Java DSL do projeto atual para manter simplicidade e performance.

---

### RST-002: Estruturas Complexas

**Descrição**: Suporte limitado para estruturas condicionais e complexas.

**Limitações**:
- Estruturas `choice()`, `when()`, `otherwise()` são identificadas mas não totalmente expandidas
- Apenas o nó `choice` é criado, sem detalhamento das branches
- Estruturas `split()`, `aggregate()` não são suportadas

**Justificativa**: MVP focado em rotas lineares simples. Expansão de estruturas complexas é melhoria futura.

---

### RST-003: Conexão entre Rotas

**Descrição**: O plugin não conecta automaticamente rotas que se referem entre si.

**Limitações**:
- Rotas que usam `.to("direct:A")` e rotas que usam `from("direct:A")` aparecem desconectadas
- Não há detecção automática de conexões via endpoints `direct:` ou `seda:`

**Justificativa**: Funcionalidade complexa que requer análise pós-processamento. Planejada para versão futura.

---

### RST-004: Expressões Dinâmicas

**Descrição**: Suporte limitado para URIs definidas dinamicamente.

**Limitações**:
- URIs definidas via concatenação de strings são extraídas como texto
- Variáveis e expressões complexas não são resolvidas
- Exemplo: `.to("direct:" + routeName)` é extraído como texto literal

**Justificativa**: Resolução de expressões dinâmicas requer análise de fluxo de dados complexa.

---

## Critérios de Aceitação Gerais

### Para Considerar o Plugin "Pronto para Produção"

1. ✅ **Funcionalidade Core**: Todos os RF-001 a RF-005 devem estar implementados e funcionando
2. ✅ **Segurança**: Todas as vulnerabilidades críticas de segurança devem ser corrigidas (RNF-002)
3. ✅ **Performance**: Deve funcionar adequadamente em projetos com até 50 rotas (RNF-001)
4. ✅ **Usabilidade**: Interface deve ser intuitiva e não requerer documentação extensa (RNF-005)
5. ✅ **Confiabilidade**: Não deve causar crashes ou problemas no IntelliJ (RNF-006)
6. ✅ **Compatibilidade**: Deve funcionar com IntelliJ 2024.1+ e projetos Apache Camel (RNF-003)

---

## Priorização de Requisitos

### Fase 1 - MVP (Minimum Viable Product) - 🔴 CRÍTICO

**Requisitos Essenciais para Funcionamento Básico**:
- RF-001: Análise Automática de Código-Fonte
- RF-002: Extração de Rotas Camel
- RF-003: Construção de Modelo de Grafo
- RF-004: Visualização Gráfica Interativa
- RF-005: Interface de Usuário no IntelliJ
- RNF-002: Segurança (todas as vulnerabilidades críticas corrigidas)

**Resultado Esperado**: Plugin funcional que visualiza rotas Camel básicas de forma segura.

---

### Fase 2 - Melhorias Essenciais - 🟡 ALTA

**Requisitos para Uso em Produção**:
- RF-006: Atualização Manual do Grafo
- RF-007: Tratamento de Erros
- RNF-001: Performance adequada
- RNF-003: Compatibilidade
- RNF-005: Usabilidade
- RNF-006: Confiabilidade
- RNF-007: Portabilidade

**Resultado Esperado**: Plugin robusto, seguro e usável em ambientes corporativos.

---

### Fase 3 - Melhorias Incrementais - 🟢 MÉDIA/BAIXA

**Requisitos para Versões Futuras**:
- Conexão automática entre rotas (`.to("direct:A")` → `from("direct:A")`)
- Expansão de estruturas complexas (`choice()`, `split()`, etc.)
- Filtros e busca de rotas
- Exportação de grafo (PNG, SVG, GraphML)
- Suporte a XML DSL e YAML DSL
- Análise estática avançada (detecção de problemas)
- Integração com debugger

**Resultado Esperado**: Plugin completo com funcionalidades avançadas.

---

## Definições e Glossário

### Termos Técnicos

- **PSI (Program Structure Interface)**: API do IntelliJ para análise estrutural de código
- **AST (Abstract Syntax Tree)**: Árvore de sintaxe abstrata representando estrutura do código
- **RouteBuilder**: Classe base do Apache Camel para definir rotas em Java DSL
- **JBCefBrowser**: Componente do IntelliJ que embute Chromium para renderizar HTML/JavaScript
- **Cytoscape.js**: Biblioteca JavaScript para visualização de grafos
- **CSP (Content Security Policy)**: Política de segurança para prevenir XSS
- **XSS (Cross-Site Scripting)**: Vulnerabilidade de segurança onde código malicioso é injetado

### Tipos de Nós no Grafo

- **ROUTE_START**: Ponto de início de rota (`.from()`)
- **ENDPOINT**: Destino de mensagem (`.to()`, `.toD()`)
- **BEAN**: Invocação de bean (`.bean()`)
- **PROCESSOR**: Processador customizado (`.process()`)
- **CHOICE**: Estrutura condicional (`.choice()`)
- **UNKNOWN**: Tipo não reconhecido

---

## Notas de Implementação

### Decisões Arquiteturais Importantes

1. **Kotlin como Linguagem Principal**:
   - Sintaxe concisa para trabalhar com PSI
   - Type safety e null safety
   - Interoperabilidade perfeita com APIs Java do IntelliJ

2. **PSI ao invés de Regex**:
   - Entende semântica do código, não apenas texto
   - Funciona independente de formatação
   - Acesso à AST completa

3. **Visualização Web (Cytoscape.js)**:
   - Visualização moderna e interativa
   - Layouts automáticos
   - Facilita futuras melhorias

4. **Bundle Local de Bibliotecas**:
   - Funciona sem internet
   - Compatível com políticas corporativas
   - Mais seguro (sem dependência de CDN)

---

## Validação e Testes

### Cenários de Teste Essenciais

1. **Projeto com Rotas Simples**:
   - Projeto com 1-5 rotas lineares
   - Verificar se todos os nós e arestas são criados corretamente

2. **Projeto sem Rotas**:
   - Projeto sem classes RouteBuilder
   - Verificar se mensagem apropriada é exibida

3. **Projeto com Múltiplas Rotas**:
   - Projeto com 20+ rotas
   - Verificar performance e renderização

4. **Projeto com Erros de Compilação**:
   - Projeto com código incompleto ou com erros
   - Verificar se plugin não crasha

5. **Testes de Segurança**:
   - Rotas com URIs contendo caracteres especiais
   - Verificar sanitização adequada

---

## Referências e Documentação

- [IntelliJ Platform SDK Documentation](https://plugins.jetbrains.com/docs/intellij/)
- [Apache Camel Documentation](https://camel.apache.org/manual/)
- [Cytoscape.js Documentation](https://js.cytoscape.org/)
- [PSI (Program Structure Interface) Guide](https://plugins.jetbrains.com/docs/intellij/psi.html)

---

**Versão do Documento**: 1.0  
**Data de Criação**: Janeiro 2025  
**Última Atualização**: Janeiro 2025
