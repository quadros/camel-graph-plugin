# Camel Graph Visualization (IntelliJ Plugin)

Visualize **Apache Camel Java DSL** routes as an interactive graph inside IntelliJ IDEA.

## Features

- Scans the project for classes extending `org.apache.camel.builder.RouteBuilder`
- Parses route entry points: `from`, `fromD`, `fromF`, `rest`
- Visualizes common steps: `to/toD`, `bean`, `process`, `choice/when/otherwise`, `doCatch`
- Cross-file route linking: `.to("direct:X")` points to the same node as `from("direct:X")`
- Visual grouping by class/file via containers (compound nodes)
- More readable edge labels (choice conditions) and improved spacing/layout
- Containers are draggable (moves contained nodes together)
- Auto-fits the graph on first render

## Usage

1. Open a project containing Apache Camel routes (Java DSL)
2. Open **View → Tool Windows → CamelGraph**
3. Click **Refresh Graph**

## Requirements

- IntelliJ IDEA 2023.2+
- Java 17+ (IDE runtime)

## Security

- All user-controlled strings shown in the UI are sanitized/escaped.
- The visualization is fully local (no network calls).
- See `SECURITY_ANALYSIS.md` for the latest security review.

## Known limitations

- XML/YAML DSL routes are not supported yet.
- Kotlin DSL is not supported yet.
- Cross-file linking is text-based (dynamic URIs/constants may not resolve to the same value).

