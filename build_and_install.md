# Build e Instalação do Camel Graph Plugin

Este documento descreve os passos detalhados para compilar e instalar o Camel Graph Plugin no IntelliJ IDEA.

## Resumo Rápido

**Build com Gradle (Recomendado)**:
```bash
./gradlew buildPlugin
# Plugin gerado em: build/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip
```

**Build com Maven**:
```bash
mvn clean package
# Plugin gerado em: target/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip
```

**Instalação**: `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...` → Selecione o ZIP

---

## Pré-requisitos

### 1. JDK 17
- **Instalação**: Baixe e instale o JDK 17 de [Oracle](https://www.oracle.com/java/technologies/downloads/#java17) ou [OpenJDK](https://adoptium.net/)
- **Verificação**: Verifique se o JDK está instalado corretamente:
  ```bash
  java -version
  ```
  Deve mostrar algo como: `openjdk version "17.x.x"` ou `java version "17.x.x"`

- **JAVA_HOME**: Configure a variável de ambiente `JAVA_HOME` apontando para o diretório do JDK 17:
  - **macOS/Linux**: Adicione ao seu `~/.zshrc` ou `~/.bashrc`:
    ```bash
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
    export PATH=$JAVA_HOME/bin:$PATH
    ```
  - **Windows**: Configure nas variáveis de ambiente do sistema ou via PowerShell:
    ```powershell
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    ```

### 2. IntelliJ IDEA
- Certifique-se de ter o IntelliJ IDEA instalado (versão 2023.2 ou superior)
- O plugin foi testado com IntelliJ IDEA Community Edition
- Compatibilidade: Build 232 (2023.2) até Build 242.* (2024.2)

### 3. Build Tool

O projeto suporta dois sistemas de build. Escolha o que melhor se adequa ao seu ambiente:

- **Gradle (Recomendado)**: 
  - Use o Gradle Wrapper incluído no projeto (`./gradlew`)
  - O wrapper usa **Gradle 8.10** (versão estável e compatível)
  - Não é necessário instalar o Gradle manualmente
  - Funcionalidades completas: `buildPlugin`, `runIde`, `verifyPlugin`, etc.
  - Melhor integração com IntelliJ Platform SDK
  - Build mais rápido com cache incremental

- **Maven**: 
  - Use Maven 3.6+ se preferir
  - Execute `mvn clean package` para compilar
  - Funcionalidades básicas de build e empacotamento
  - Para funcionalidades avançadas (runIde, sandbox), use Gradle
  - Útil para integração com sistemas CI/CD baseados em Maven

## Passos para Compilar o Plugin

### Passo 1: Preparar o Ambiente

1. **Clone ou navegue até o diretório do projeto**:
   ```bash
   cd /Users/georgequadros/desenvolvimento/workspace/camel-graph-plugin
   ```

2. **Verifique se está no diretório correto**:
   ```bash
   ls -la
   ```
   Você deve ver arquivos como `build.gradle.kts`, `pom.xml`, `settings.gradle.kts`, `src/`, etc.

### Passo 2: Escolher o Build Tool

Você pode compilar o plugin usando **Gradle** (recomendado) ou **Maven**. Escolha uma das opções abaixo:

---

## Opção A: Build com Gradle (Recomendado)

### Passo 2A: Compilar o Plugin com Gradle

1. **Execute o build do plugin**:
   ```bash
   ./gradlew buildPlugin
   ```
   
   **Nota**: 
   - A primeira execução pode demorar vários minutos, pois o Gradle baixará:
     - O IntelliJ Platform SDK (versão 2023.2)
     - Todas as dependências do projeto
     - O wrapper do Gradle (se necessário)
   - Em execuções subsequentes, o build será mais rápido

2. **Aguarde a conclusão do build**:
   - Você verá mensagens como:
     ```
     BUILD SUCCESSFUL in Xs
     ```
   - Se houver erros, verifique:
     - Se o JDK 17 está configurado corretamente
     - Se há problemas de conectividade (para baixar dependências)
     - Se há erros de compilação no código

### Passo 3A: Localizar o Arquivo Gerado (Gradle)

Após o build bem-sucedido, o arquivo do plugin estará localizado em:

```
build/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip
```

**Verificação**:
```bash
ls -lh build/distributions/
```

Você deve ver o arquivo `camel-graph-plugin-1.0-SNAPSHOT.zip` listado.

---

## Opção B: Build com Maven

### Passo 2B: Compilar o Plugin com Maven

1. **Verifique se o Maven está instalado**:
   ```bash
   mvn --version
   ```
   Deve mostrar Maven 3.6 ou superior.

2. **Execute o build do plugin**:
   
   ⚠️ **IMPORTANTE**: O IntelliJ Platform SDK não está disponível como dependência Maven padrão.
   
   **Opção A - Build no IntelliJ IDEA (Recomendado para Maven)**:
   - Abra o projeto no IntelliJ IDEA
   - O IntelliJ detecta o `pom.xml` e configura o classpath automaticamente
   - Use `Build` → `Build Project` no menu
   
   **Opção B - Build via linha de comando**:
   ```bash
   mvn clean package
   ```
   
   ⚠️ **Nota**: Este comando falhará na resolução de dependências do IntelliJ Platform SDK.
   Para build completo via linha de comando, **use Gradle** (`./gradlew buildPlugin`).
   
   **Nota sobre dependências**:
   - O Maven baixará: Gson, Kotlin stdlib, e plugins do Maven
   - O IntelliJ Platform SDK precisa ser fornecido pelo ambiente (IntelliJ instalado)

3. **Aguarde a conclusão do build**:
   - Você verá mensagens como:
     ```
     BUILD SUCCESS
     ```
   - Se houver erros, verifique:
     - Se o JDK 17 está configurado corretamente
     - Se o Maven está na versão 3.6 ou superior
     - Se há problemas de conectividade (para baixar dependências)
     - Se há erros de compilação no código

### Passo 3B: Localizar o Arquivo Gerado (Maven)

Após o build bem-sucedido, o arquivo do plugin estará localizado em:

```
target/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip
```

**Verificação**:
```bash
ls -lh target/distributions/
```

Você deve ver o arquivo `camel-graph-plugin-1.0-SNAPSHOT.zip` listado.

**Nota sobre Maven**: O build com Maven cria o plugin ZIP, mas não inclui todas as funcionalidades do Gradle (como `runIde` para sandbox). Para desenvolvimento completo, recomenda-se usar Gradle.

## Passos para Instalar o Plugin no IntelliJ IDEA

### Opção A: Instalação Manual (Plugin no IDE Principal)

Esta opção instala o plugin no seu IntelliJ IDEA principal para uso permanente.

1. **Abra o IntelliJ IDEA**

2. **Acesse as Configurações de Plugins**:
   - **macOS**: `IntelliJ IDEA` → `Settings` (ou `Preferences` com `Cmd + ,`)
   - **Windows/Linux**: `File` → `Settings` (ou `Ctrl + Alt + S`)

3. **Navegue até Plugins**:
   - No menu lateral esquerdo, clique em `Plugins`

4. **Instale o Plugin do Disco**:
   - Clique no ícone de **engrenagem (⚙️)** no canto superior direito da janela de plugins
   - Selecione `Install Plugin from Disk...`

5. **Selecione o Arquivo ZIP**:
   - **Se usou Gradle**: Navegue até `build/distributions/`
   - **Se usou Maven**: Navegue até `target/distributions/`
   - Selecione o arquivo `camel-graph-plugin-1.0-SNAPSHOT.zip`
   - Clique em `OK`

6. **Reinicie o IntelliJ IDEA**:
   - O IntelliJ solicitará um reinício
   - Clique em `Restart IDE` ou `OK` e reinicie manualmente

7. **Verifique a Instalação**:
   - Após reiniciar, vá em `File` → `Settings` → `Plugins`
   - Procure por "Camel Graph Visualization" na lista de plugins instalados
   - Deve aparecer como instalado e habilitado

### Opção B: Ambiente de Desenvolvimento (Sandbox)

Esta opção abre uma instância isolada do IntelliJ IDEA com o plugin carregado, sem instalar no IDE principal. Ideal para testes e desenvolvimento.

**Nota**: Esta funcionalidade está disponível apenas com **Gradle**. Se você estiver usando Maven, use a Opção A (instalação manual).

1. **Execute o comando runIde**:
   ```bash
   ./gradlew runIde
   ```

2. **Aguarde a inicialização**:
   - O Gradle compilará o plugin (se necessário)
   - Uma nova janela do IntelliJ IDEA será aberta automaticamente
   - Esta é uma instância "sandbox" isolada

3. **Use o Plugin**:
   - O plugin já estará carregado nesta instância
   - Abra um projeto que contenha rotas Apache Camel
   - A aba "CamelGraph" estará disponível na barra lateral

## Como Usar o Plugin

### Passo 1: Abrir um Projeto com Rotas Camel

1. Abra ou crie um projeto Java que contenha classes que estendem `org.apache.camel.builder.RouteBuilder`
2. Exemplo de classe RouteBuilder:
   ```java
   public class MyRouteBuilder extends RouteBuilder {
       @Override
       public void configure() throws Exception {
           from("direct:start")
               .to("bean:processOrder")
               .to("log:finish");
       }
   }
   ```

### Passo 2: Acessar a Janela do Plugin

1. **Localizar a aba CamelGraph**:
   - Na barra lateral direita do IntelliJ, procure pela aba **"CamelGraph"**
   - Se não estiver visível, vá em: `View` → `Tool Windows` → `CamelGraph`

2. **Alternativamente, use o atalho**:
   - A aba pode estar minimizada na barra lateral
   - Clique no ícone ou nome "CamelGraph" para expandir

### Passo 3: Gerar o Grafo

1. **Clique no botão "Refresh Graph"**:
   - O plugin irá:
     - Escanear o projeto em busca de classes que estendem `RouteBuilder`
     - Parsear os métodos `configure()` dessas classes
     - Extrair as rotas Camel (`.from()`, `.to()`, `.bean()`, etc.)
     - Gerar o grafo de visualização

2. **Visualizar o Grafo**:
   - O grafo será renderizado na janela usando Cytoscape.js
   - Você pode:
     - Arrastar os nós para reorganizar
     - Zoom in/out com a roda do mouse
     - Clicar nos nós para ver detalhes

### Passo 4: Interpretar o Grafo

- **Nós Verdes (Elipse)**: Representam pontos de início de rota (`.from()`)
- **Nós Cinza (Retângulo Arredondado)**: Representam endpoints (`.to()`), processadores (`.process()`), beans (`.bean()`), etc.
- **Arestas**: Mostram o fluxo entre os componentes da rota

## Solução de Problemas

### Problema: Build falha com erro de JDK

**Solução**:
- Verifique se o JDK 17 está instalado: `java -version`
- Configure o `JAVA_HOME` corretamente
- No IntelliJ IDEA, vá em `File` → `Project Structure` → `Project` e selecione JDK 17

### Problema: Plugin não aparece após instalação

**Solução**:
- Verifique se o plugin está habilitado em `Settings` → `Plugins`
- Reinicie o IntelliJ IDEA completamente
- Verifique se a versão do IntelliJ é compatível (2023.2 ou superior, build 232+)

### Problema: "No Camel Routes Found"

**Solução**:
- Certifique-se de que o projeto contém classes que estendem `org.apache.camel.builder.RouteBuilder`
- Verifique se o projeto foi indexado pelo IntelliJ (aguarde a indexação completar)
- Certifique-se de que as dependências do Apache Camel estão no classpath do projeto

### Problema: Grafo não renderiza ou aparece em branco

**Solução**:
- O plugin não requer conexão com internet (Cytoscape.js está incluído localmente)
- Abra o console do navegador embutido (se disponível) para ver erros JavaScript
- Tente clicar em "Refresh Graph" novamente
- Verifique os logs do IntelliJ em caso de erros de carregamento de recursos

### Problema: Erro ao executar `./gradlew`

**Solução (macOS/Linux)**:
- Torne o script executável: `chmod +x gradlew`
- Use: `bash gradlew buildPlugin` como alternativa

**Solução (Windows)**:
- Use: `gradlew.bat buildPlugin`

### Problema: Erro ao executar `mvn`

**Solução**:
- Verifique se o Maven está instalado: `mvn --version`
- Certifique-se de que o Maven está no PATH
- Se necessário, instale o Maven: https://maven.apache.org/download.cgi
- Verifique se o `pom.xml` está no diretório raiz do projeto
- Se o erro persistir, tente: `mvn clean install -U` (força atualização de dependências)
- Alternativamente, use Gradle (recomendado)

### Problema: Build Maven falha com erro de dependências

**Solução**:
- Limpe o cache do Maven: `mvn dependency:purge-local-repository`
- Force atualização: `mvn clean package -U`
- Verifique conectividade com Maven Central
- Se estiver atrás de proxy, configure o `settings.xml` do Maven

### Problema: Plugin ZIP não é gerado com Maven

**Solução**:
- Verifique se o arquivo `assembly/plugin.xml` existe
- Execute: `mvn clean package` (não apenas `mvn package`)
- Verifique os logs do Maven para erros no plugin de assembly
- Certifique-se de que todas as dependências foram baixadas corretamente

## Informações Técnicas

### Versões Utilizadas

- **Java**: 17 (compatível com IntelliJ 2023.2+)
- **Kotlin**: 1.9.24
- **IntelliJ Platform SDK**: 2023.2 (compatível com IntelliJ IDEA 2023.2 até 2024.2)
- **Gradle**: 8.10 (via wrapper) - **Recomendado para desenvolvimento completo**
- **Gradle IntelliJ Plugin**: 1.17.0
- **Maven**: 3.6+ (alternativa ao Gradle)
- **Maven Assembly Plugin**: 3.6.0
- **Maven Dependency Plugin**: 3.6.1
- **Gson**: 2.10.1

### Comparação: Gradle vs Maven

| Funcionalidade | Gradle | Maven |
|----------------|--------|-------|
| Build do plugin | ✅ `./gradlew buildPlugin` | ✅ `mvn clean package` |
| Sandbox (runIde) | ✅ `./gradlew runIde` | ❌ Não disponível |
| Verificação do plugin | ✅ `./gradlew verifyPlugin` | ❌ Não disponível |
| Cache incremental | ✅ Sim | ⚠️ Limitado |
| Configuração | `build.gradle.kts` | `pom.xml` |
| Wrapper incluído | ✅ Sim | ❌ Não |
| Tempo de build | Mais rápido | Mais lento |
| Recomendado para | Desenvolvimento | CI/CD Maven

### Compatibilidade de Versões

- **Versão Mínima Suportada**: IntelliJ IDEA 2023.2 (build 232)
- **Versão Máxima Suportada**: IntelliJ IDEA 2024.2 (build 242.*)
- O plugin foi testado e funciona com versões do IntelliJ IDEA de 2023.2 em diante

### Estrutura do Plugin Gerado

O arquivo ZIP contém a seguinte estrutura:

```
camel-graph-plugin-1.0-SNAPSHOT.zip
├── camel-graph-plugin-1.0-SNAPSHOT.jar  (classes compiladas)
├── META-INF/
│   └── plugin.xml                       (manifesto do plugin)
├── js/
│   └── cytoscape.min.js                 (biblioteca de visualização)
└── lib/
    ├── gson-2.10.1.jar                 (dependência Gson)
    └── kotlin-stdlib-1.9.24.jar        (biblioteca Kotlin)
```

**Componentes**:
- **JAR principal**: Contém todas as classes Kotlin compiladas do plugin
- **META-INF/plugin.xml**: Manifesto com metadados, dependências e compatibilidade
- **js/**: Recursos JavaScript (Cytoscape.js) para renderização do grafo
- **lib/**: Dependências externas necessárias em runtime

### Localização dos Arquivos

**Código Fonte e Recursos**:
- **Código Kotlin**: `src/main/kotlin/com/example/camelgraph/`
- **Recursos**: `src/main/resources/`
  - `META-INF/plugin.xml` - Manifesto do plugin
  - `js/cytoscape.min.js` - Biblioteca de visualização

**Arquivos Gerados pelo Build**:

*Gradle*:
- **Plugin ZIP**: `build/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip`
- **JAR principal**: `build/libs/camel-graph-plugin-1.0-SNAPSHOT.jar`
- **Classes compiladas**: `build/classes/kotlin/main/`
- **Recursos processados**: `build/resources/main/`
- **Sandbox**: `build/idea-sandbox/` (usado por `runIde`)

*Maven*:
- **Plugin ZIP**: `target/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip`
- **JAR principal**: `target/camel-graph-plugin-1.0-SNAPSHOT.jar`
- **Classes compiladas**: `target/classes/`
- **Dependências copiadas**: `target/lib/`
- **Recursos processados**: `target/classes/` (mesmo diretório das classes)

## Desenvolvimento Adicional

### Recompilar após Mudanças

Após fazer alterações no código:

**Com Gradle**:
```bash
./gradlew buildPlugin
```

**Com Maven**:
```bash
mvn clean package
```

**Próximos passos**:
1. Se estiver usando sandbox (`runIde` - apenas Gradle), reinicie a instância sandbox
2. Se instalou no IDE principal, reinstale o plugin seguindo os passos da Opção A

### Limpar Build Anterior

**Com Gradle**:
```bash
./gradlew clean buildPlugin
```

**Com Maven**:
```bash
mvn clean package
```

Isso remove arquivos de build anteriores e reconstrói tudo do zero.

### Comandos Úteis Adicionais

**Gradle**:
```bash
# Verificar o plugin (valida compatibilidade)
./gradlew verifyPlugin

# Executar testes (se houver)
./gradlew test

# Ver dependências do projeto
./gradlew dependencies

# Limpar completamente (inclui cache)
./gradlew clean --refresh-dependencies
```

**Maven**:
```bash
# Compilar sem testes
mvn clean package -DskipTests

# Ver dependências do projeto
mvn dependency:tree

# Limpar e atualizar dependências
mvn clean package -U

# Verificar estrutura do projeto
mvn validate
```

### Verificação do Build

Após o build, você pode verificar se o plugin foi gerado corretamente:

**Gradle**:
```bash
# Verificar se o ZIP foi criado
ls -lh build/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip

# Verificar conteúdo do ZIP (macOS/Linux)
unzip -l build/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip

# Verificar tamanho do arquivo
du -h build/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip
```

**Maven**:
```bash
# Verificar se o ZIP foi criado
ls -lh target/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip

# Verificar conteúdo do ZIP (macOS/Linux)
unzip -l target/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip

# Verificar tamanho do arquivo
du -h target/distributions/camel-graph-plugin-1.0-SNAPSHOT.zip
```

**Validação do plugin.xml**:
O arquivo `META-INF/plugin.xml` dentro do ZIP deve conter:
- `<idea-version since-build="232" until-build="242.*" />`
- Informações do plugin (id, name, vendor)
- Dependências corretas

## Suporte

Para problemas ou questões:
- Verifique os logs do IntelliJ IDEA em `Help` → `Show Log in Explorer`
- Consulte a documentação do IntelliJ Platform: https://plugins.jetbrains.com/docs/intellij/
