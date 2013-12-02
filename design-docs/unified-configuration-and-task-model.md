# Introduction

In almost every build, there are dependencies between the build logic that defines the Gradle model and the build logic
that uses that model to do something useful. Here's an example:

* The Java plugin adds domain objects that describe the source of the project, how to compile it, and so on. These domain
  objects are configured according to the Gradle convention.
* The root build script applies the Java plugin and applies some cross-cutting configuration to the domain objects
  to define the convention that the build uses.
* The project build script applies some project-specific configuration to define how this project varies from the
  convention.
* The Java plugin adds some tasks whose configuration is determined by the configuration of the domain objects, according
  to the Gradle convention.
* The root build script applies some configuration of these tasks.
* The project build script applies some configuration of these tasks.

Currently, the build logic execution is eager, and the domain objects are lazy so that they can deal with changes:

1. The root build script applies the Java plugin
2. The Java plugin adds the domain objects and tasks, and wires up the domain objects and tasks so that their configuration
   is calculated lazily, to deal with changes applied in the later steps.
3. The root build script applies cross-cutting configuration of the domain objects and tasks.
4. The project build script applies project specific configuration of the domain objects and tasks.

There are a number of problems with this approach:

1. It is difficult to implement lazy data structures. This has been observed in practise over the last few years of
   writing plugins both within the Gradle distribution and in the community.
2. It is not possible for a plugin to provide different task graphs based on the configuration of domain objects, as the
   tasks must be created before any organisation, build or project specific configuration has been applied.
3. Eager execution means that all build logic must be executed for each build invocation, and it is not possible to skip
   logic that is not required. This in turn means that all domain objects and tasks must be created for each build
   invocation, and it is not possible to short-cicuit the creation of objects that are not required.
4. Lazy data structures cannot be shared between threads for parallel execution. Lazy execution allows build logic to
   be synchronised based on its inputs and outputs.
5. Lazy data structures must retain references to their inputs, which results in a large connected graph of objects
   that cannot be garbage collected. When data is eager, the input objects can be released as soon as the build logic
   has completed (or short-circuited).
6. Validation must be deferred until task execution.

The aim of this specification is to define a mechanism to flip around the flow so that build logic is applied lazily and
domain objects are eager. The new publication DSL will be used to validate this approach. If successful, this will then
be rolled out to other incubating plugins, and later, to existing plugins in a backwards compatible way.

# Implementation plan

## Native language plugins use model rules to configure the native components model

`NativeBinariesModelPlugin` currently uses a sequence of project configuration actions to configure:

- Default source directories for each language source set, if none defined.
- A default tool chain, if none defined. This requires configuring the candidate tool chains and then determining which of these are available.
- A default platform, if none defined.
- A default build type, if none defined.
- Default flavours for each native component, if none defined.
- The binaries for each native component. This requires configuring each component, which in turn requires configuring all of the above.

These should be refactored to use model rules instead. Remove `ProjectConfigurationActionContainer` once this is complete.

There are a number of other places where the plugins use `container.all { }` to configure:

- `NativeBinariesPlugin` defines a default source set for each native library and executable.
- `NativeBinariesPlugin` determines whether each binary is buildable and attaches the tool implementation to the binary.
  This requires determining the set of binaries for each component. The target platform and tool chain for each binary are not configurable, so the
  binary does not need to be completely closed at this point.
- `CppLangPlugin` adds a C++ source set to each functional source set. This needs to happen such that the C++ source set can be configured as part of the
  functional source set.
- `CppNativeBinariesPlugin` adds C++ extensions to each binary. This needs to happen such that these extensions can be configured as part of the binary.

These should be refactored to use model rules instead. These will probably require some DSL and rules support.

## Native language plugins use model rules to define and configure tasks

There are a number of places where the native language plugins use `container.all { }` to configure:

- `LanguageBasePlugin` defines the lifecycle task for each binary.
- `NativeBinariesPlugin` defines the builder (link or assemble) task for each binary and attaches it as a dependency of the lifecycle task.
  This requires that the binary has been configured.
- `NativeBinariesPlugin` defines the install task for each executable binary and attaches the binary as input of the install task.
  This requires that the binary has been configured.
- `CppNativeBinariesPlugin` defines a compile task for each C++ source set which is input to each binary and attaches as input to the builder task.
  This requires that the binary and source set have been configured.

These should be refactored to use model rules instead.

## Build author uses the model DSL to configure the native components model

The model DSL should allow the native language model to be configured.

Currently, the native language plugins use extensions to represent the various parts of the model and the build author uses the extension DSL to configure the model.
There are two approaches we might take here:

1. Integrate the existing extension DSL and the model rules so that either can be used to configure the model. This isn't really required for the native language plugins,
   as they are still incubating, but will help drive backwards compatibility with the existing DSL for stable plugins.
2. Change the native language plugins so that they do not use extensions and ignore integration between the old and new DSL for now.

## Build author uses the model DSL to configure the native components tasks

The model DSL should allow the tasks for a native binary and component to be configured. The configuration should happen only when the task is added to the task graph.

Part of this work is to consider how the existing task DSL can be used to configure these tasks. Currently, the build author can configure a task in several locations:

1. As a top-level property of the project.
2. As an element of the `tasks` container attached to the project.
3. As an element of the `tasks` container attached to the native binary.

For locations #1 and #2, there are a few approaches we might take:

1. Fail with a "unknown task" error.
2. Trigger evaluation of rules to attempt to close the matching tasks.
3. Defer the configuration and fail only if the task is unknown.
4. Mark the block as "broken" and skip it. At the end of configuration, fail with an error message that informs the user that they need to use the new DSL to
   configure this task, or that the task is unknown.

For location #3, this is an incubating element, so we can simply defer all configuration.

Similarly, this work should consider how the existing task DSL can be used to access these tasks: Is it an error? Does it trigger model rules?

## Plugin author uses model rules to define tasks after plugin model has been configured

A common problem when authoring a plugin is how to handle configuration that happens after the plugin is applied.

This story exposes model rules as a public (but very much experimental) feature which a plugin can use to solve this problem. Implementation-wise, this
story is mostly about exposing and documenting the features that already exist, possibly along with some sugar to help solve this very common use case.

- Provide a mechanism for the plugin to register a model object and some logic which later receives the immutable model object and defines some tasks from this object.
- Add documentation and samples.
- Add some mechanism to expose the model object also as an extension, to provide the plugin with a migration path to the new DSL.
    - Generate a warning when the build script author uses the extension DSL to configure the model.
    - Generate a warning when the extension DSL is used after the model object has been closed.

### Open issues

- Detect and handle attempts to mutate the model object via the extension DSL after the model object has been closed.

## Model DSL allows native components and source sets to be configured in dependency order

A source set can take a native library as a dependency, and this library can in turn take another source set as input. The model DSL should not require the build
author to sequence their model rules in a particular way.

For example, something like this should be possible:

    sources {
        greeter { ... }
        exe {
            cpp {
                lib libraries.greeter
            }
        }
    }

    libraries {
        greeter { ... }
    }

    executables {
        exe { ... }
    }

A similar problem is that a native component may have dependency declarations:

    dependencies {
        myLib 'some-dep'
    }

    libraries {
        myLib { ... }
    }

## Plugin author uses model rules to define native language conventions plugin

Most (all?) organisations will need to be able define their own conventions for native languages and package these into plugins:

- source set layouts
- default platforms, toolchains, flavours and build types

Will need to document and provide samples for using model rules from plugins in general, and for native language conventions in particular.

## Generalise the native components DSL

The native language plugins currently use `executables { }` and `libraries { }` to declare the native executables and libraries. This has a number of issues:

- The terms `executables` and `libraries` are too general and will collide with executables and libraries for other runtimes, such as Java or Javascript libraries and
  applications.
- There are other types of native components we need to build that are neither an executable or a library, such as kernel drivers and firmware.
- There are specific types of executables we need to build, such as command-line applications, GUI applications, server daemons and so on.
- There are different ways that an executable or a library may be built. For example, the build may delegate to `make` to build the binaries for a native component.
- It is awkward to declare things like: for all native components define the 'debug' and 'release' build types.

It should be possible to configure native components and binaries based on their properties, rather than their name. Some examples:

- all binaries that use any version of visual C++, all binaries that use a toolchain other than visual C++, all binaries that use visual C++ 9 and later.
- all unix platforms, all windows platforms, all windows platforms for windows 2000 and later.
- all binaries that have linker settings (ExecutableBinary + SharedLibraryBinary).

The implementation should be able to detect and report on typos and predicates that will never match anything.

## Enforce model rule inputs and outputs

- Model rule uses an object which is not a declared input
- Model rule mutates an object which is not a declared output
- Model element is mutated after it is closed
- Integration with old DSL.

- Tool chain is modified once it has been used to define a binary:
    - Gcc or Visual Studio install path, Gcc tool configuration, etc.
- Tool chain is added once tool chains have been selected for a binary.

## Native language plugins do not create tasks for binaries that cannot be built

## Plugins use model rules to define implicit tasks

Replace usages of `TaskContainerInternal.addPlaceholderAction()`

## User discovers which model elements are available

- Add some command-line and HTML reporting tasks that can present the model, or parts of the model, to the user.
- Include the model DSL in the DSL reference guide.

## Publishing plugins use rules to define and configure publications and publishing tasks

Address some of the current problems with the publishing plugins:

- Almost always need to determine the project version before closing the publications. This may happen in various places.
- Warn when maven repositories are defined but no maven publications are defined, and vice versa. Same for Ivy.
- Don't allow the identifier of a publication to be changed after the identity has been used:
    - to generate a descriptor for a publication that depends on it
    - at resolve time.
- Validate publications once they have been configured.

Remove support for `@DeferredConfigurable` once this is complete.

## Configuration of publications depends on the output of some task

For example, generate source and then determine based on this which publications and artifacts to define for the project

## Native language tasks are not created when not required for the current build

For example, running `gradle help` should not configure any native binary tasks.

## Native language plugins do not define tasks for empty source sets

If a source set is empty and is not generated, then do not define the tasks for that source set.

## Command-line options are applied to tasks before closing the task

This story integrates command-line configuration with model rules, so ensure that the values provided on the command-line 'win' over those values
provided by the build logic, and are visible to build logic with uses the tasks.

## Build logic uses rules to influence dependency resolution

- Replaces `ResolutionStrategy.eachDependency()`
- Replaces `ComponentMetadataHandler.eachComponent()`
- Plus whichever other hooks have since been added
- Possibly expose the cache control hooks as rules too

## Other things to consider

- Integration with old DSL.
- Enforcement of model element lifecycle (eg detect attempts to mutate a model element that has been closed).
- Configure model elements on demand.
- Error handling and reporting.
- IDE integration.
- Flesh out the JVM language plugins.

# Old stories

These need to be garbage collected and reworked ...

## Prevent `object.properties` triggering configuration of any deferred configurable properties (GRADLE-2754)

## Allow the project version to be determined early in the build configuration

This story introduces a replacement for calculating the project version based on the contents of the task graph, so that the version is
known as early as possible during project configuration, rather than at the end.

A new concept, a _build type_, will be introduced:

    version = '1.0-SNAPSHOT'
    builds {
        release {
            version = '1.0'
            run 'clean', 'build'
        }
    }

Running `gradle release` is equivalent to running `gradle clean build` with the project version set to `1.0`.

Conditional configuration based on build type:

    apply plugin: 'java'
    builds {
        ci {
            apply plugin: 'code-coverage'
            tasks.check.dependsOn(tasks.converageReport)
            run tasks.clean, tasks.build
        }
    }

Running `gradle ci` performs a clean build and includes the code coverage report.

Running `gradle tasks` includes a listing of the available build types.

TBD - change command-line handling so that custom command-line options can be attached to build types.

TBD - need to figure out how this should interact with camel-case task name matching on the command-line.

## Warn when a domain object that is used as input for a publication is later changed

This story introduces a lifecycle for domain objects, so that a domain object is first configured completely and
then later used as input for build logic, either to configure further domain objects or to create and configure
tasks.

When a domain object is mutated after it has been used as input, a warning will be logged to inform the build
author that the configuration change will have no effect. This will become an error in Gradle 2.0.

    publishing {
        publications.ivy(IvyPublication) {
            from components.java
        }
    }

    // This change is fine as the compile time dependencies have not been used yet
    dependencies {
        compile 'some:module:1.2'
    }

    // Triggers the configuration of publications
    publishing.repositories.maven { ... }

    // This will generate a warning that this dependency will not be used
    dependencies {
        compile 'some:other:1.2'
    }

The following changes should be detected:

- The attributes and output file of a `PublishArtifact` used to define an artifact
- The attributes and output file of an `AbstractArchiveTask` used to define an artifact
- The output files of a `Task`.
- The project group or version.
- The elements of a collection used to define artifacts.
- The runtime dependencies or artifacts of a component.

## Allow arbitrary deferred configuration logic

This story allows arbitrary logic to be deferred until required. This will allow configuration logic to be sequenced naturally based on its dependencies.

## Defer the creation of publication tasks until after the publications have been configured

This story allows the creation and configuration of publication tasks to be deferred until after the publications have
been configured. It will introduce a public mechanism to allow plugins to implement this pattern.

It will allow the lazy configuration of tasks.

## Trigger the configuration of publications when tasks are referenced

This story adds support for triggering the configuration of the publications when the publication tasks are
configured in the build script:

    publishing {
        publications { ... }
    }

    // This triggers configuration of the publications and creation of the appropriate tasks
    generatePomFileForMavenPublication {
    }

TBD - Not sure if we want to support this for new plugins. Might add this for backwards compatibility to allow
older plugins to migrate.

## Warn when a publication is changed after the publication tasks have been configured

This story adds support to warn the build author that changes made to the publication after the publication tasks have
been created will be ignored. It will be implemented as a public mechanism that plugins can use.

- A publication is added or changed.
- An artifact is added or changed.
- A repository is added or changed.

## Do not create publication tasks when not required for the current build

## Do not create publications when not required for the current build

## Warn when domain objects are changed after used as input for dependency resolution

Reuse the domain object lifecycle mechanism to warn when:

- A property of a `Dependency` or `Configuration` or `ResolutionStrategy` is changed after resolution.
- Any inherited dependencies are added or changed after resolution.
- Dependencies referenced by a project dependency are added or changed after resolution, for transitive
  dependencies.
- Any repository is added or changed after resolution.

## Reuse in the sonar-runner plugin

## Reuse in all incubating plugins

# Open issues

- Fire events before and after configuration of domain objects.

# Spike

- Rules that operate on collections.
- Finer grained models and rules.
- DSL to configure tasks. Have this triggered on demand as tasks are requested.
- Validation, cycle detection.
- Extension <-> model bridging.
- Views.
- Rule ordering to close things as late as possible.
- Change detection.
- Persistence, immutable views, etc.
- Determine an id and display name for a rule.
- Detect rules which are mutating something outside of scope (eg `publishing { someTask { ... } }`)
- DSL convenience to define and reference shared values (eg constants)
- Scopes: project scope, global scope, maybe plugin scope?
- The rules for a scope may execute in different JVMs or concurrently in the same JVM.
- Things are private to the scope unless exported
- Maybe move the settings into the build script in a separate block
- Some way to statically infer the plugins that are relevant to a scope

Use it:

- Use to build the tooling API models. Don't configure things that aren't required.
- Use to replace task placeholders.
- Use to order the various parts of the IDE plugins.
- Use in the sonar-runner plugin to determine the sonar properties.
- Allow tasks to be attached to a model object, for each action that can be performed on that object.
- Use to apply command-line overrides.
- Ensure target of project dependency is closed when the dependency is used:
    - When resolving
    - When calculating task dependencies
    - When generating pom or ivy.xml from consuming project.
    - When building IDE model or generating IDE configuration.