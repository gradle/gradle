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

# Milestone 1 - Plugin uses rules to define model and tasks

## Story: Plugin declares a top level model to make available

Introduce some mechanism where a plugin can statically declare that a model object should be made available.

A mock up:

    public class SomePlugin implements Plugin<Project>
        @RuleSource
        static class Rules {
            @Model("something")
            MyModel createSomething() {
                ...
            }
        }
    }

    apply plugin: SomePlugin

    model {
        something {
            ...
        }
    }

### Test cases

- ~~Build script configuration closure receives the model instance created by the plugin.~~
- ~~Build script configuration closure is executed only when the model is used as input to some rule.~~
- ~~Reasonable error message when two rules create models with same name.~~
- ~~Reasonable error messages when creation rule or configuration closure fail.~~
- ~~Reasonable error messages when plugin does not correctly follow static pattern.~~
- ~~Creation rule returns null.~~
- ~~Rule can declare parameterized type with concrete type vars~~
- ~~Model type cannot be generic~~
- ~~Model type can contain type params~~
- ~~Model element declared with illegal name produces reasonable error message~~

### Open issues

- Need some mechanism for the ComponentReport task to determine whether the TestSuites model is available or not. The mechanism should be internal at this stage, eg add a `ModelRegistry.find()` or throw a specific exception thrown by `ModelRegistry.get()`.

> This is there via `ModelRegistry.element()`.

- Expose models as extensions as well:
    - Have to handle creation rules that take inputs: defer creation until the convention is used, and close the inputs at this point.
    - Once closed, cannot mutate an object.
    
> Not planning on doing this. Model elements will likely be subject to restrictions to facilitate persistence.
> For backwards compatibility, plugin authors can include a mutation rule for the model element and copy data from the extension to the model.
> Some stories (not fleshed out) have been added to the backlog to allow extensions as rule inputs.
    
- Exact pattern to use to determine which model(s) a plugin exposes
    - Alternative pattern that declares only the type and name and Gradle takes care of decoration, instantiation and dependency injection
    
> The pattern we've implemented is good enough for now.
> An alternative pattern that allows Gradle to take care of the instantiation could be added later if needed. (perhaps if the method is abstract).
> There may be persistence implications here. We may have to own construction for hydration to work.
    
- Should assert that every model object is decorated, however it happens to be created.

> UNANSWERED - deferring until we get into the persistence side of things as that is likely to have an impact here. 
> Also, don't really know exactly what we are going to need decoration for at this point.

- Also add an API where a plugin can declare models dynamically?

> Later, if the use case arises.

- DSL reference documents the model.

> Later story.

- Creation rule declares input of unknown type.

> Later story.

- Settings or init script configures model.

> Later story.

- How would a user verify that they got the signature/annotation correct in a unit test?

> Later story.

- Do we support generic types? including wildcard, covariant and contravariant types?

> We support parameterized types where all variables are concrete. 
> The method rule can't be generic so the only other possible cases are bounded types and the wildcard.

- How much thread safety do we build in right now? e.g. could two plugins be registered concurrently? 

> Out of scope for this story. Model rules are strictly within the project boundary and assuming serial execution at this time.

## Story: Plugin author unit tests plugin that declares model elements

This story adds a mechanism that plugin authors can use to test that their model declaring plugin is interpreted by Gradle in the way that they expect.

Mock up:

    package org.gradle.model.test
    
    abstract class ModelFixture {      
      
      static interface class Builder {
          Builder addSource(Class<?> source); // throws if source is invalid
          ModelFixture build();
        }
      }
      
      static Builder builder() {
        // …
      }
      
      // Methods return fully configured/finalized object
      
      Object get(String path);
      <T> get(Class<T> type);
      <T> get(Class<T> type, String path);
      <T> get(ModelType<T> type);
      <T> get(ModelType<T> type, String path);
    }

Example test:

    when:
    def builder = ModelFixture.builder()
    builder.add(MyPlugin)
    def fixture = builder.build()
    
    then:
    fixture.get(MyModelElement).var == "value"

To make this more useful the builder should be able to take mocks and other kinds of object _instances_ (not possible with the current APIs because rule sources are static). 
This will happen later.

This story will require making `ModelType` (or some facade) public. 

### Test Coverage

- Can add one or more plugin sources, and successfully retrieve model element
- Can add one or more plugin sources, and successfully retrieve model element by one of its many readable views
- model rule execution failure (e.g. attempt to execute rule where not all inputs can be bound) throws useful exception
- Can retrieve model element via compatible generic type
- Methods not taking a model path fail when multiple candidates for type exist

### Open Questions

- Shortcut methods for building from a list of sources? (i.e. instead of additive builder)

## Story: Plugin defines tasks using model as input

Introduce some mechanism where a plugin can static declare a rule to define tasks using its model object as input.

A mock up:

    // Part of Gradle API
    interface CollectionBuilder<T> {
        void add(String name); // Adds element of type T with given name and default implementation for T

        void add(String name, Class<? extends T> type); // Adds elements with given type

        void add(String name, Action<? super T> configAction);
        <S extends T> void add(String name, Class<S> type, Action<? super S> configAction);
    }

    public class SomePlugin {
        ...

        @Rule
        public createTasks(CollectionBuilder<Task> container, MyModel model) {
            // Invoked after MyModel has been configured
        }
    }

    apply plugin: SomePlugin

    model {
        something {
            ...
        }
    }

### Test cases

- ~~Build script configuration closure is executed before rule method is invoked.~~
- ~~Reasonable error message when two rules create tasks with the same name.~~
- ~~Item configuration action cannot create more items~~
- ~~Reasonable error message when rule method fails.~~
- Reasonable error message when rule method declares input of unknown type.
- Reasonable error message when rule method declares ambiguous input.

### Open issues

- Project and other things can leak out of `Task` instances when `TaskContainer` is provided to a rule.
    - Same with `Buildable` things, `BuildableModelElement`, `NativeBinaryTasks`, etc.
- Need another type to allow task instances to be defined without being created.
- Don't fire rule when tasks not required (eg building model via tooling API).
- Report on unknown target for configuration closure.
- Can take extensions as input too?

## Story: Build script configures tasks defined using configuration rule

Improve the model DSL to allow tasks to be configured in the build script:

    model {
        tasks {
            someTask {
                ...
            }
        }
    }

### Test cases

### Open issues

- Reasonable behaviour when `someTask { ... }` appears as a top level statement in build script.
- Replace `TaskContainerInternal.placeholderActions()` with something more general.
- DSL to allow tasks to be defined.

## Story: Model DSL rule uses a model as input

A mock up:

    model {
        someThing {
            prop = model.otherThing.value
        }
        tasks {
            someTask {
                prop = model.someThing.prop
            }
        }
    }

### Test cases

- Reasonable error message when configuration closure fails.
- Reasonable error message when unknown model element is requested as input.

### Open issues

- Build script declares model object.
- Change closure resolution strategy to delegate-only.
- Move `Project.afterEvaluate()` to fire after the build script has been executed.
- Include rule execution time in the profile report.

# Milestone 2 - Build author uses public rule DSL to configure model and tasks

## Story: Build user applies rule source class in similar manner to applying a plugin

This story makes it more convenient to write a plugin that is exclusively based on model rules, while keeping the implementation details of such a plugin transparent to the user.

The existing plugin mechanism must be extended to allow loading classes other than those that implement `Plugin`.

A plugin can be loaded via:

* `PluginAware.apply(plugin: «id»)`
* `PluginAware.apply(plugin: «class»)`
* `PluginAware.getPlugins().apply(«id»)`
* `PluginAware.getPlugins().apply(«class»)`

Where plugins are loaded by id, the implementation mapping for a plugin may map to a rule source class.
Where plugins are loaded by class, the class may be a rule source class.

A “rule source” class must be annotated with `@org.gradle.model.RuleSource`.
If the “plugin” class to be applied does not implement `Plugin`, and is not annotated with `@RuleSource` then it cannot be loaded and an error should occur (this is a change in behavior as Gradle will attempt to load any plugin class, assuming it implements `Plugin` and will fail with a ClassCastException).

This is a repurposing of the `RuleSource` annotation.
A replacement approach for a `Plugin` to “include” a set of rules will need to be added.

Gradle allows (non script) plugins to be applied to `Gradle`, `Settings` and `Project`.
At this time, model rules are only supported for `Project`.
As such, a rule source plugin cannot be applied to other types.
The `ModelRegistryScope` interface is currently used to indicate an object that model rules can be attached to.
Later stories cover making something like this public and documenting when/where rule source plugins can be used.

### Test Coverage

- Rule source plugin can be applied to Project via `apply()`
- Rule source plugin can be applied to Project via `plugins.apply()`
- Rule source plugin cannot be applied to `PluginAware` that is not model rule compatible (e.g. Gradle)
- Class that is not a `Plugin` or rule source fails with appropriate error message when applied
- `Plugin` impl can include rule source class
- Attempt to load rule source class that violates rule source constraints produces reasonable error message

### Open issues

- How to deal with the breaking API changes to `PluginContainer` (e.g. apply(id) returns `Plugin`, same for getPlugin(), more or less every method)
- Do we support the same kind of “plugin detection” mechanisms for these kinds of plugins (e.g. `withId()`, `withType()`, `whenPluginAdded()`)
- Replacement for current use of `RuleSource` to allow `Plugin` impl to include rules
- Need a story to allow these rule source plugins to be applied to any `PluginAware`, and for the `plugins { }` DSL to work for settings and init scripts.

## Story: Plugin model rule uses extension as rule input

- Creation and mutation rule

### Open issues

- Prevent access to extension after used as rule input?

## Story: DSL model rule uses extension as rule input

## Story: Make public the Model DSL

- Document how to use the DSL, include samples.

## Story: Make public the plugin rules mechanism

- Document how to use plugin mechanism, include samples.
- Indicate on `PluginAware` and `PluginContainer` what the conditions are for the plugin target to be able to take model rule plugins

### Open issues

- Improve logging and error message feedback, given that we have better insight into what's happening.

## Story: New language and publication plugins use plugin rules mechanism to define tasks

- Change the native language, jvm language and the publication plugins, to use this mechanism to define tasks from their models.

## Story: Build author is informed when model rule targets unknown model object

# Milestone x - Make things faster

## Feature: Tasks are not created or configured when not referenced in a build

Only fire the rules to create and configure a task when it is referenced in a build:

- Added to the task graph.
- When required for `gradle tasks`
- Building certain tooling API models.
- Using `TaskContainer` to query task instances.

### Test cases

- Build script configuration closure is executed when `someTask` is required:
    - Task is added to task graph
    - Running `gradle tasks`
    - Building `GradleProject` tooling API model.
    - Using `TaskContainer` to query task instances.
- Build script configuration closure is not executed when `someTask` is not required:
    - Running `gradle help`
    - Running `gradle someTask` in another project.
    - Using `TaskContainer` to query task names.

## Feature: Rule is not executed when its outputs are up to date

Short-circuit the execution of all configuration rules whose outputs are up-to-date:
    - Inputs have not changed.
    - Rule implementation has not changed.

Continue to execute all legacy DSL.

To implement this, model objects will need to be serializable in some form.

For up-to-date checks, the implementation of a rule also forms input to the rule. Need to include this and invalidate cached outputs. Fix this for tasks at the same time.

# Implementation plan - Later milestones

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

- Remove all usages of `ModelRegistry` outside the rules infrastructure.

### Open issues

- Add `ModelRules.register()` overload that is given an implementation class and takes care of instantiation and dependency injection.
- Replace `ModelRules.register()` method that takes a `Factory` and instead takes a rule that is inspected for its dependencies and return type.

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

- Change native language plugins so that they no longer use extensions (see `CreateNativeBinaries`).

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

## Internal properties and methods are not visible from the model DSL

The model DSL should expose only public methods and properties defined by the public API. All other methods and properties should be hidden.

- Reasonable error message when DSL uses unknown property, eg show matching candidates, inform user that method/property is internal.

## Plugin author uses model rules to define tasks after plugin model has been configured

A common problem when authoring a plugin is how to handle configuration that happens after the plugin is applied.

This story exposes model rules as a public (but very much experimental) feature which a plugin can use to solve this problem. Implementation-wise, this
story is mostly about exposing and documenting the features that already exist, possibly along with some sugar to help solve this very common use case.

- Provide a mechanism for the plugin to register a model object and some logic which later receives the immutable model object and defines some tasks from this object.
- Add documentation for the model rules API and samples.
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

### Open issues

When locating a library in another project at dependency resolve time, should use the model registry locate the target library:

1. Register the libraries in the model registry.
2. Add an implicit rule that the project build script must be executed before creating any domain objects.

Another example ordering problem:

    project(":exe") {
        evaluationDependsOn(":lib") // Closes the tasks for lib
        executables {
            main {}
        }
        sources.main.cpp.lib project: ':lib', library: 'hello'
    }
    project(":lib") {
        // This stuff is effectively ignored
        libraries {
            hello {}
        }
    }

## Native language plugins do not create tasks for binaries that cannot be built

Change the native language plugins so a single lifecycle task is created for each binary that is not buildable.

## Native language plugins do not create model objects that are not required

Change the visual studio plugin so that it does not create visual studio project instances that are not required by any visual studio solution,
or the associated generation tasks.

## Plugins use model rules to define implicit tasks

Replace usages of `TaskContainerInternal.addPlaceholderAction()`

## User discovers available model elements

- Add some command-line and HTML reporting tasks that can present the model, or parts of the model, to the user.
- Include the model DSL in the DSL reference guide.

## Publishing plugins use rules to define and configure publications and publishing tasks

Address some of the current problems with the publishing plugins:

- Almost always need to determine the project version before closing the publications. This may happen in various places.
- Warn when maven repositories are defined but no maven publications are defined, and vice versa. Same for Ivy.
- Don't allow the identifier of a publication to be changed after the identity has been used:
    - to generate a descriptor for a publication that depends on it.
    - used at resolve time.
    - exposed to tooling API client.
- Don't allow additional publications to be defined after the set of publication identities have been used (as per previous item).
- Validate publications once they have been configured.
- Use model rules to register outgoing publications to `ProjectPublicationRegistry`.
    - `BasePlugin`
    - `MavenPlugin`
    - `PublishingPlugin`
- Change `ProjectDependencyPublicationResolver` to use `ProjectPublicationRegistry` and remove explicit call to project `evaluate()`.
- Change `ProjectDependencyArtifactIdExtractorHack` to use `ProjectPublicationRegistry`.

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
- Allow task outputs to be used as inputs to rules. eg a `Report` produced by a task.
- Integration with old DSL.
- Enforcement of model element lifecycle (eg detect attempts to mutate a model element that has been closed).
- Configure model elements on demand.
- Native plugins: allow configuration of a default toolchain, without replacing the default toolchains. e.g. tweak `clang` without discarding `gcc`.
- Error handling and reporting.
- IDE integration.
- Flesh out the JVM language plugins.

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