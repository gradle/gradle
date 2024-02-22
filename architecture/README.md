<!-- 
  -- Note: this file contains a generated diagram. Use `./gradlew :architectureDoc` to generate 
  -->

# Gradle platform architecture

The diagram below shows the main components of the Gradle architecture. See [ADR4](standards/0004-use-a-platform-architecture.md) for more details.

<!-- This diagram is generated. Use `./gradlew :architectureDoc` to update it -->
```mermaid
    graph TD

    subgraph core["core platform"]

        core_runtime["core-runtime module"]
        style core_runtime stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;

        core_configuration["core-configuration module"]
        style core_configuration stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;

        core_execution["core-execution module"]
        style core_execution stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;
    end
    style core fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;

    documentation["documentation module"]
    style documentation stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;

    ide["ide module"]
    style ide stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;

    subgraph software["software platform"]
    end
    style software fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;
    software --> core

    subgraph jvm["jvm platform"]
    end
    style jvm fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;
    jvm --> core
    jvm --> software

    subgraph extensibility["extensibility platform"]
    end
    style extensibility fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;
    extensibility --> core
    extensibility --> jvm

    subgraph native["native platform"]
    end
    style native fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;
    native --> core
    native --> software

    enterprise["enterprise module"]
    style enterprise stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;

    build_infrastructure["build-infrastructure module"]
    style build_infrastructure stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;
```
