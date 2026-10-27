# Gradle Kotlin DSL

Kotlin DSL Provider and API.

## Gradle Kotlin DSL C4 Model

Core [C4 Model](https://c4model.com/#coreDiagrams) diagrams for the Gradle Kotlin DSL.

> **Note:** The optional 4th level, UML diagrams, isn't provided, favoring the source code and inspection tools.

### Level 1: System Context diagram

A developer uses the Gradle Kotlin DSL either via Gradle directly or in an IDE that uses the Kotlin DSL support via Gradle.
The Kotlin DSL is embedded into the Gradle Build Tool and, just like the IDE, makes use of the Kotlin toolchain.
Both the Kotlin DSL and the Kotlin toolchain leverage the Gradle Build Cache.

```mermaid
C4Context
    title Context diagram for the Gradle Kotlin DSL
    Person(dev, "Developer", "Developer using a Kotlin DSL based Gradle build")

    System_Boundary(gradleBound, "Gradle Build Tool") {
        System(gradleKotlinDsl, "Kotlin DSL")
        System_Ext(buildCache, "Gradle Build Cache", "Local, Develocity Build Cache Node, etc...")
    }

    System_Boundary(jetbrains, "JetBrains") {
        System_Ext(ide, "IntelliJ IDE", "IDEA or Android Studio with the Kotlin plugin enabled")
        System_Ext(kotlinTools, "Kotlin Toolchain", "kotlinc, Gradle plugin etc...")
    }

    Rel(dev, gradleKotlinDsl, "Uses", "Command Line")
    Rel(dev, ide, "Uses", "GUI")
    Rel(gradleKotlinDsl, kotlinTools, "Uses")
    Rel(gradleKotlinDsl, buildCache, "Uses")
    Rel(ide, gradleKotlinDsl, "Uses", "Tooling API")
    Rel(ide, kotlinTools, "Uses")
    Rel(kotlinTools, buildCache, "Uses")
    UpdateLayoutConfig($c4ShapeInRow="1", $c4BoundaryInRow="2")
```

### Level 2: Container diagram

When used for the compilation of scripts of a Gradle build, the Kotlin DSL Provider compiles scripts using an embedded Kotlin compiler.

When used for the compilation of Kotlin source sets, e.g. in `buildSrc`, the `kotlin-dsl` plugin provide Kotlin DSL features to source sets and compiles Kotlin code using the Kotlin Gradle Plugin.

When used for build script editing, a Kotlin DSL enabled IDE editor loads the Kotlin DSL support from its configured Gradle version and uses it to resolve script dependencies.
The Kotlin DSL Script Dependencies Resolver uses the Gradle Tooling API to build the script dependencies model that enables the IDE editor features.

```mermaid
C4Container
    title Container diagram for the Gradle Kotlin DSL
    Person(dev, "Developer", "Developer using a Kotlin DSL based Gradle build")

    System_Boundary(gradleBuildTool, "Gradle") {
        System_Ext(gradle, "Gradle Build Tool", "The Gradle Build Tool")

        Container_Boundary(gradleKotlinDsl, "Gradle Kotlin DSL") {
            Container(provider, "Kotlin DSL Provider", "", "The core of the Gradle Kotlin DSL")
            Container(tapiBuilder, "IDE ToolingAPI Models", "", "Calculates script dependencies and error reporting for editors")
            Container(plugin, "kotlin-dsl Plugin", "", "Gradle Plugin to develop Kotlin-based projects that contribute build logic")
            Container(resolver, "Kotlin DSL Script Dependencies Resolver", "", "Loaded from IntelliJ Project Gradle version")
            Rel(tapiBuilder, provider, "Uses")
            Rel(plugin, provider, "Uses")
            Rel(resolver, tapiBuilder, "Uses", "TAPI")
        }

        System_Ext(buildCache, "Gradle Build Cache", "Local, Develocity Build Cache Node, etc...")
        Rel(gradle, provider, "Uses", "Script compilation")
        Rel(gradle, plugin, "Uses", "e.g. in buildSrc")
    }

    System_Boundary(jetbrains, "JetBrains") {
        Container_Boundary(ide, "IntelliJ IDE") {
            System_Ext(editor, ".gradle.kts script editor", "In IntelliJ IDEA or Android Studio")
            System_Ext(ideKotlin, "IntelliJ Kotlin Plugin", "for IDEA or Android Studio")
            Rel(editor, ideKotlin, "Uses")
        }

        Container_Boundary(kotlinTools, "Kotlin Toolchain") {
            System_Ext(kgp, "Kotlin Gradle Plugin", "The org.jetbrains.kotlin.jvm Gradle plugin")
            System_Ext(kotlinc, "Kotlin Compiler", "kotlinc")
            Rel(kgp, kotlinc, "Uses", "Daemon")
        }
    }

    Rel(dev, gradle, "Uses", "Command Line")
    Rel(dev, editor, "Uses", "GUI")
    Rel(provider, kotlinc, "Uses", "Embedded")
    Rel(provider, buildCache, "Uses")
    Rel(ideKotlin, resolver, "Loads", "Embedded")
    Rel(ideKotlin, kotlinc, "Uses")
    Rel(plugin, kgp, "Applies")
    Rel(kgp, buildCache, "Uses")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="2")
```

### Level 3: Component diagram

The following diagram details each container's components and gets closer to how the Kotlin DSL is organized.
It should be useful enough to know where to find what in the Kotlin DSL source code.

> **Note:** This diagram diverges from the C4 Model because the scope is the whole Kotlin DSL system instead of having one diagram per container.
> This makes this diagram quite large but it shows how everything fits together and is simpler to maintain.

```mermaid
C4Component
    title Component diagram for the Gradle Kotlin DSL
    Person(dev, "Developer", "Developer using a Kotlin DSL based Gradle build")

    System_Boundary(gradleBuildTool, "Gradle") {
        Boundary(gradle, "Gradle Build Tool") {
            System_Ext(gradleScriptEvaluation, "Script Evaluation")
            System_Ext(gradlePluginApplication, "Plugin Application")
            System_Ext(buildCache, "Gradle Build Cache", "Local, Develocity Build Cache Node, etc...")
        }

        Container_Boundary(gradleKotlinDsl, "Gradle Kotlin DSL") {
            Container_Boundary(provider, "Kotlin DSL Provider") {
                Component(gradleKotlinDslApi, "Generated gradleKotlinDslApi() API JAR", "", "The Gradle Kotlin DSL API and Kotlin decorated gradleApi()")
                Component(providerFactory, "Gradle ScriptPluginFactory", "", "Provides script evaluation to Gradle, main entry point")
                Component(providerScriptTemplates, "Script Templates", "", "One per script target type, define scripts API, declare Kotlin compiler settings and plugins, declare editor dependencies resolver")
                Component(providerExecution, "Kotlin DSL Execution", "", "Partial evaluation interpreter for .gradle.kts scripts")
                Component(providerClasspath, "Script Source and Classpath", "", "Script source and classpath calculation")
                Component(providerAccessors, "Accessors", "", "Collects Project Schema, generates Accessors source and bytecode")
                Component(providerImports, "Implicit Imports", "", "Includes Gradle default imports")
                Rel(providerFactory, providerExecution, "Delegates to")
                Rel(providerExecution, providerScriptTemplates, "Uses", "script API for compilation")
                Rel(providerExecution, providerClasspath, "Uses", "for compilation")
                Rel(providerClasspath, providerAccessors, "Uses", "for compilation")
                Rel(providerExecution, gradleKotlinDslApi, "Uses", "for compilation")
                Rel(providerExecution, providerImports, "Queries", "for compilation")
            }

            Container_Boundary(plugin, "kotlin-dsl Gradle Plugin") {
                Component(pluginPlugin, "kotlin-dsl Gradle Plugin", "", "Configures the project for plugin development and Kotlin DSL features in source sets")
                Component(pluginEmbedded, "Embedded Kotlin", "", "Repository with Kotlin artifacts from Gradle distributions")
                Component(pluginPrecompiled, "Precompiled scripts support", "", "Configures Kotlin compiler for .gradle.kts scripts, infers plugin IDs from script names convention")
                System_Ext(pluginDevPlugin, "java-gradle-plugin")
                Rel(pluginPlugin, pluginEmbedded, "Configures")
                Rel(pluginPlugin, pluginPrecompiled, "Configures")
                Rel(pluginPrecompiled, pluginDevPlugin, "Applies", "and configures")
            }

            Container_Boundary(tapiBuilder, "IDE ToolingAPI Models") {
                Component(tapiModels, "TAPI Models", "", "classpath, sourcepath, implicit imports, user reports")
                Component(tapiModelBuilders, "TAPI Model Builders", "", "On-demand source distro download, Calculates editor warnings and errors")
                Rel(tapiModelBuilders, tapiModels, "Build")
                Rel(tapiModelBuilders, providerClasspath, "Queries", "Script classpath")
                Rel(tapiModelBuilders, providerImports, "Queries", "Implicit Imports")
            }

            Container_Boundary(resolverBound, "Loaded into IntelliJ") {
                Component(scriptResolver, "Dependencies Resolver", "", "one loaded per script target type")
            }

            Rel(providerScriptTemplates, scriptResolver, "Selects", "by configuration, one loaded per script target type")
            Rel(tapiModelBuilders, providerFactory, "Configures", "Lenient mode, collecting exceptions")
            Rel(tapiModelBuilders, providerFactory, "Queries", "Collected exceptions")
            Rel(pluginPlugin, gradleKotlinDslApi, "Adds", "dependency")
            Rel(pluginPrecompiled, providerImports, "Registers", "on Kotlin compiler")
            Rel(pluginPrecompiled, providerScriptTemplates, "Registers", "on Kotlin compiler")
        }

        Rel(gradleScriptEvaluation, providerFactory, "Selects", "Depending on file extension")
        Rel(gradlePluginApplication, pluginPlugin, "Applies")
    }

    System_Boundary(jetbrains, "JetBrains") {
        System_Boundary(ide, "IntelliJ IDEA or Android Studio") {
            Boundary(editor, ".gradle.kts script editor") {
                System_Ext(editorDependencies, "Dependencies", "classpath, sourcepath, implicit imports")
                System_Ext(editorReportPanel, "Report Panel", "Displays warnings and errors on top of the editor UI")
                System_Ext(editorHints, "Inline Hints", "Displays warning and error hints inline in the editor UI")
            }

            System_Ext(ideKotlin, "IntelliJ Kotlin Plugin", "for IDEA or Android Studio")
            Rel(editorDependencies, ideKotlin, "Uses")
            Rel(ideKotlin, providerScriptTemplates, "Selects", "by file extension")
        }

        Boundary(kotlinTools, "Kotlin Toolchain") {
            System_Ext(kotlinc, "Kotlin Compiler", "kotlinc")
            System_Ext(kgp, "Kotlin Gradle Plugin", "The org.jetbrains.kotlin.jvm Gradle plugin")
            Rel(kgp, kotlinc, "Uses", "Daemon")
        }
    }

    Rel(dev, gradleScriptEvaluation, "Uses", "Command Line")
    Rel(dev, editorDependencies, "Uses", "GUI")
    Rel(providerExecution, kotlinc, "Uses", "Embedded")
    Rel(pluginPlugin, kgp, "Applies and configures")
    Rel(scriptResolver, editorDependencies, "Provides")
    Rel(scriptResolver, editorReportPanel, "Reports")
    Rel(scriptResolver, editorHints, "Reports")
    Rel(scriptResolver, tapiModels, "Requests", "TAPI")
    Rel(gradleKotlinDslApi, kotlinc, "Uses", "Embedded")
    Rel(ideKotlin, scriptResolver, "Loads", "Embedded")
    Rel(ideKotlin, kotlinc, "Uses")
    Rel(kgp, buildCache, "Uses")
    Rel(providerExecution, buildCache, "Uses")
    UpdateLayoutConfig($c4ShapeInRow="1", $c4BoundaryInRow="2")
```
