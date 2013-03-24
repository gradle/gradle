
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

## Allow the configuration of publications to be deferred

This story allows the lazy configuration of publications, so that the configuration code is deferred until after all the
other things it uses as input have themselves been configured. It will introduce a public mechanism for plugins to use to
implement this pattern.

The `publishing { }` closure will become lazy, so that it is deferred until after the project has been configured.
Accessing the `publishing` extension directly in the script or programmatically will trigger the configuration of
the publishing extension.

The publishing tasks will continue to be created and configured as the publications are defined. Later stories will
allow deferred creation.

An example:

    apply plugin: 'maven-publish'

    publishing {
        // this closure is lazy and is not executed until after this build script has been executed
        repositories {
        }
        publications {
        }
    }

    // The following are not lazy
    publishing.repositories {
        maven { ... }
    }
    publishing.repositories.maven { ... }
    publishing.repositories.mavenLocal()
    publishing.repositories.myRepo.rootUrl = 'http://somehost/'
    def extension = project.extensions.getByType(PublishingExtension)

    // This will not work as the tasks have not been defined
    generatePomFileForMavenPublication {
    }
    tasks.generatePomFileForMavenPublication {
    }

For a multiproject build:

    // root build.gradle

    subprojects {
        apply plugin: 'java'
        apply plugin: 'maven-publish'
        publishing {
            repositories.maven { ... }
            publications {
                maven(MavenPublication) {
                    from components.java
                }
            }
        }
    }

    // project build.gradle

    dependencies { ... }

Once the publishing extension has been configured, it will be an error to make further calls to `publishing { ... }`.

### Implementation plan

1. Add a `DeferredConfigurable` annotation. This annotation marks an extension as requiring a single, deferred configuration event, rather than progressive configuration.
2. Update the ExtensionContainer so that for any added extension that is annotated with DeferredConfigurable:
    - `configure` will add an action for later execution when the target extension object is to be configured.
       It is an error to attempt to configure a DeferredConfigurable extension after it has been configured.
    - Accessing the extension triggers the execution of the registered configuration actions, if the extension has not already been configured.
3. Add `DeferredConfigurable` to `DefaultPublishingExtension`.

### Test coverage

- Update the publishing integration tests so that the publications are declared along with the other injected configuration in `allprojects`/`subprojects`
- A custom plugin can use a `DeferredConfigurable` extension to implement lazy configuration. Verify that that extension is configured before the
  project's afterEvaluate {} event is fired.
- Attempting to configure a `DeferredConfigurable` extension after access provides reasonable failure message.
- A reasonable error message is given when the configuration of an extension fails.
- A reasonable error message is given when attempting to access an extension whose configuration has previously failed.

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

## Reuse in the c++ plugins

# Open issues

- Fire events before and after configuration of domain objects.
