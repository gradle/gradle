
This spec defines a number of features to improve the developer experience using Gradle from the IDE. It covers changes in Gradle to improve those features
that directly affect the IDE user experience. This includes the tooling API and tooling models, the Gradle daemon, and the Gradle IDE plugins
(i.e. the plugins that run inside Gradle to provide the IDE models).

Tooling API stories that are not related directly to the IDE experience should go in the `tooling-api-improvements.md` spec.

# Implementation plan

## Feature - Expose source and target platforms of JVM language projects

Spec moved to [features/ide-integration/source-and-target-jvm].

### Story - Introduce JavaProject

#### Estimate

- 3.5 days

#### Implementation

- add new `JavaProject` to model IDE agnostic Java Projects
- add details about Java Source level to the `JavaModel`
    - should be based on projects `sourceCompatibility` level
- add details about dependencies to the project
    - project and external dependencies
- add details about source folders to the `JavaProject` model
- add details of the target JVM:
    - should be based on projects `targetCompatibility` level
    - Java version
    - Install directory

- TBD: Classpath
- For older Gradle versions:
    - TBD - reasonable defaults for Java language version

#### Test Coverage

- Query `JavaProject` for older Gradle providers throws meaningful error message
- Query `JavaProject` for non java projects fails with meaningful error message
- `JavaProject` model for multi project build contains information about
    - source folders
    - thirdparty dependencies
    - project dependencies
    - target sdk
    - source compatibility
- respects customizations of sourcesets
    - additional sourcesets
    - additional sourcefolders to sourcesets are respected

- TBD: provide test and runtime classpath


### Story - Expose Groovy components to the IDE

TBD

## Feature - Tooling API parity with command-line for task visualisation and execution

This feature exposes via the tooling API some task execution and reporting features that are currently available on the command-line.

- Task selection by name, where I can run `gradle test` and this will find and execute all tasks with name `test` in the current project and its subprojects.
  However, this selection logic does have some special cases. For example, when I run `gradle help` or `gradle tasks`, then the task from the current project
  only is executed, and no subproject tasks are executed.
- Task reporting, where I can run `gradle tasks` and this will show me the things I can run from the current project. This report, by default, shows only the public
  interface tasks and hides the implementation tasks.

The first part of this feature involves extracting the logic for task selection and task reporting into some reusable service, that is used for command-line invocation
and exposed through the tooling API. This way, both tools and the command-line use the same consistent logic.

The second part of this feature involves some improvements to the task reporting, to simplify it, improve performance and to integrate with the component
model introduced by the new language plugins:

- Task reporting treats as public any task with a non-empty `group` attribute, or any task that is declared as a public task of a build element.
- All other tasks are treated as private.
- This is a breaking change. Previously, the reporting logic used to analyse the task dependency graph and treated any task which was not a dependency of some other task
  as a public task. This is very slow, requires every task in every project to be configured, and is not particularly accurate.

#### Open issues

- Split `gradle tasks` into 'what can I do with this build?' and a 'what are all the tasks in this project?'.

### Simplify task visibility logic

Change the task visibility logic introduced in the previous stories so that:

- A task is public if its `group` attribute is not empty.
- A task selector is public if any of the tasks that it selects are public.

### Test cases

- Tooling API and `gradle tasks` treats as public a task with a non-empty `group` attribute.
- Tooling API and `gradle tasks` treats as public a selector that selects a public task.
- Tooling API and `gradle tasks` treats as private a task with null `group` attribute.
- Tooling API and `gradle tasks` treats as private a selector that selects only private tasks.

### Expose the lifecycle tasks of build elements as public tasks

Change the task visibility logic so that the lifecycle task of all `BuildableModelElement` objects are public, regardless of their group attribute.

### Test cases

- Tooling API and `gradle tasks` treats as public the lifecycle task of a `BuildableModelElement` with a null `group` attribute.

#### Open issues

- Should other tasks for a `BuildableModelElement` be private, regardless of group attribute?

### GRADLE-2017 - Correctly map libraries to IDEA dependency scope

A new model will be available for defining IDEA scopes: these scopes will formed by combining and excluding Gradle configurations and other scopes.
The model can statically restrict the available scopes to 'compile', 'runtime', 'provided' and 'test'.

#### Model

Introduce a model for binaries that run on the JVM and that are built locally:

    interface Classpath { // This type already exists
        ...
        libs(Object) // Adds libraries to this classpath
    }

    interface JvmPlatform {
        Classpath compile
        Classpath runtime
    }

    interface JvmBinary {
        JvmPlatform platform
        Classpath compile
        Classpath runtime
    }

    interface MainJvmBinary extends JvmBinary {
        JvmBinary tests
    }

The Java base plugin registers an implementation of `MainJvmBinary`, and the Java and WAR plugins fill it in so that:

- `configurations.compile` is added to `jvmBinary.compile`
- `configurations.runtime` is added to `jvmBinary.runtime`
- `configurations.testCompile` is added to `jvmBinary.test.compile`
- `configurations.testRuntime` is added to `jvmBinary.test.runtime`
- `configurations.providedCompile` is added to `jvmBinary.platform.compile`
- `configurations.providedRuntime` is added to `jvmBinary.platform.runtime`

Introduce IDEA scope model:

    interface IdeaScope {
        String name
        List<Dependency> libraries // read-only, calculated on demand
    }

    interface IdeaScopes {
        IdeaScope provided
        IdeaScope compile
        IdeaScope runtime
        IdeaScope test
    }

    class IdeaModule { // this type already exists
        ...
        IdeaScopes ideaScopes
    }

The IDEA plugin calculates the contents of the scopes based on the `JvmBinary` model defined above:

- scope `provided` should contain `jvmBinary.platform.compile`
- scope `compile` should contain `jvmBinary.compile` minus scope `provided`.
- scope `runtime` should contain (`jvmBinary.runtime` union `jvmBinary.platform.runtime`) minus scope `compile`.
- scope `test` should contain (`jvmBinary.test.compile` minus scope `compile`) union (`jvmBinary.test.runtime` minus scope `runtime`).

An example customisation:

    binaries {
        jvm {
            test.compile configurations.integTestCompile
            test.runtime configurations.integTestRuntime

            platform.compile configurations.myCustomProvided
        }
    }

TODO: Example DSL

The new DSL (and model defaults) will be used to configure an IDEA module when the current `scopes` map has not been altered by the user.
Once the new DSL is stabilised we will deprecate and remove the `scopes` map.

#### Implementation

- Add `ResolvableComponents` which represents a set of requirements that can be resolved into a graph or set of `Components`
- Add an implementation of `ResolvableComponents` that wraps a `Configuration` instance
- Add an implementation of `ResolvableComponents` that can combine other `ResolvableComponents` as required for the Idea scope DSL
- Update each method on `IdeDependenciesExtractor`so that it takes a `ResolvableComponents` parameter in place of the
  current `Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations`.
    - For now, these methods can simply unpack the `ResolvableComponents` to a set of added and excluded configurations.
    - The current scope map DSL should be mapped to the new API
- Add domain classes as required for the new Idea scopes DSL, with appropriate defaults.

#### Test cases

- When using the tooling API to fetch the IDEA model or the `idea` task to generate the IDEA project files, verify that:
    - When a java project has a dependency declared for `testCompile` and `runtime`, the dependency appears with `test` and `runtime` scopes only.
    - When a java project has a dependency declared for `testRuntime` and `runtime`, the dependency appears with `runtime` scope only.
    - When a java project has a dependency declared for `compile` and `testCompile`, the dependency appears with `compile` scope only.
    - When a war project has a dependency declared for `providedCompile` and `compile`, the dependency appears with `provided` scope only.
- Current defaults are used when the `scopes` maps is configured by user.
- User is informed of failure when both `scopes` map and new DSL are configured.

#### Open issues

- Similar new DSL for the Eclipse model (except that it’s much simpler).
- ArtifactResolutionQuery API takes a `ResolvableComponents` as input
    - Include JvmLibrary main artifact in query results
    - Replace `IdeDependenciesExtractor.extractRepoFileDependencies` with single ArtifactResolutionQuery

## Feature - Expose dependency resolution problems

- For the following kinds of failures:
    - Missing or broken module version
    - Missing or broken jar
    - Missing or broken source and javadoc artifact
- Change the IDE plugins to warn for each such problem it ignores, and fail on all others.
- Change the tooling model include the failure details for each such problem.

### Test coverage

- Change the existing IDE plugin int tests to verify the warning is produced in each of the above cases.
- Add test coverage for the tooling API to cover the above cases.

## Feature: Expose the compile details of a build script

This feature exposes some information about how a build script will be compiled. This information can be used by an
IDE to provide some basic content assistance for a build script.

## Story: Expose the Groovy version used for a build script

Add a `groovyVersion` property to `GradleScript` to expose the Groovy version that is used.

### Test coverage

## Story: Expose the default imports used for a build script

Add a `defaultImports` property to `GradleScript` to expose the default imports applied to the script.

### Test coverage

## Story: Expose the classpath used for a build script

1. Introduce a new hierarchy to represent a classpath element. Retrofit the IDEA and Eclipse models to use this.
    - Should expose a set of files, a set of source archives and a set of API docs.
2. Add `compileClasspath` property to `GradleScript` to expose the build script classpath.
3. Script classpath includes the Gradle API and core plugins
    - Should include the source and Javadoc
4. Script classpath includes the libraries declared in the `buildscript { }` block.
5. Script classpath includes the plugins declared in the `plugins { }` block.
6. Script classpath includes the libraries inherited from parent project.

### Test coverage

- Add a new `ToolingApiSpecification` integration test class that covers:
    - Gradle API is included in the classpath.
    - buildSrc output is included in the classpath, if present.
    - Classpath declared in script is included in the classpath.
    - Classpath declared in script of ancestor project is included in the classpath.
    - Source and Javadoc artifacts for the above are included in the classpath.
- Verify that a decent error message is received when using a Gradle version that does not expose the build script classpath.

### Open issues

- Need to flesh out the classpath types.
- Will need to use Eclipse and IDEA specific classpath models

## Story: Tooling API client requests build script details for a given file

Add a way to take a file path and request a `BuildScript` model for it.

## Feature - Expose project components to the IDE

## Story: Expose the IDE output directories

Add the appropriate properties to the IDEA and Eclipse models.

### Story - Expose Scala components to the IDE

Expose Scala language level and other details about a Scala component.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose Web components to the IDE

Expose Web content, servlet API version, web.xml descriptor, runtime and container classpaths, and other details about a web application.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose J2EE components to the IDE

Expose Ear content, J2EE API versions, deployment descriptor and other details about a J2EE application.

Expose the corresponding Eclipse and IDEA model.

## Story: Expose generated directories

It is useful for IDEs to know which directories are generated by the build. An initial approximation can be to expose
just the build directory and the `.gradle` directory. This can be improved later.

### Story - Expose artifacts to IDEA

Expose details to allow IDEA to build various artifacts: http://www.jetbrains.com/idea/webhelp/configuring-artifacts.html

### Story - IntelliJ directory-based project metadata generation

A couple of versions ago, IntelliJ introduced the directory-based project format. The file-based project format is [considered to be deprecated](https://devnet.jetbrains.com/thread/461951).
In the long run, the file-based project will be taken out of action. The Gradle Idea plugin only allows for generating file-based project files. Sooner or later Gradle will need to be able
 to generate the directory-based project format.

From a user's perspective two different formats are confusing. If you start by importing a Gradle project with IntelliJ's built-in capabilities, the default project format is directory-based
(though it is configurable). Any customization of the project settings that are part of the build logic (implemented with the help of the Gradle Idea plugin) will only work properly if
the user made sure to select the file-based format during the initial import.

The goal would be to make the directory-based project format the default format when generating project files with the Gradle Idea plugin. Optionally, allow users to pick between file- and directory-
based project generation. Some users might never upgrade to a version of IntelliJ that already supports the directory-based format.

#### Implementation

From the [IntelliJ documentation](https://www.jetbrains.com/idea/help/project.html):

> When the directory-based format is used, there is a `.idea` directory in the project directory.

> The `.idea` directory contains a set of configuration files (.xml). Each file contains only a portion of configuration data pertaining to a certain functional area which is reflected in the name of a
> file, for example, `compiler.xml`, `encodings.xml`, `modules.xml`.

Given that the existing logical structure of projects (project, module, workspace) is broken up into different, dedicated files might be a good indicator that the new functionality should live in a new
plugin. This fact will help with introducing a different DSL for directory-based configuration. User can dedicated decide whether they prefer the old or new project format by picking a specific plugin.

- Creating a new plugin alongside the existing Idea plugin with the identifier `idea-directory`. We might want to think about moving the file-based plugin into a new plugin named `idea-file`. The existing
 `idea` plugin could act as an aggregation plugin that determines which format to pick based on existing project files in the workspace. A descriptive plugin identifier will be key.
- Implement the same functionality we already have in place for file-based project generation for directory-based generation.
- Introduce an new extension for directory-based project file generation. The exposed DSL elements depend on how much we want to abstract the logical structure. The alternative is to expose an element
per configuration file. The DSL of the old and new format may not conflict.

#### Test cases

- Existing test cases for the file-based format pass.
- Build a similar set of test cases for the directory-based format.
- If no existing project metadata is found in the working directory, generate the directory-based format.
- If existing project metadata is found, regenerating project files uses the existing format.
- Based on user input, a specific format can be picked for project generation.

#### Open issues

- Can we keep the existing logical structure of projects (project, module, workspace) and use the same abstraction for the directory-based project file generation? Introducing a new DSL
might be confusing to users.
- Is there any feature parity between the file-based and directory-based project formats?
- There's no overarching, publicly-available documentation or specification on directory-based project files. It might be worth to contact JetBrains for a good resource.

# More candidates

Some more features to mix into the above plan:

- `IdeaSingleEntryLibraryDependency` should expose multiple source or javadoc artifacts.
- Honour same environment variables as command-line `gradle` invocation.
- Richer events during execution:
    - Custom events
- Richer build results:
    - Test results
    - Custom results
    - Compilation and other verification failures
    - Task failures
    - Build configuration failures
- Expose unresolved dependencies.
- Expose dependency graph.
- Expose component graph.
- Provide some way to search repositories, to offer content assistance with dependency notations.
- Don't configure the projects when `GradleBuild` model is requested.
- Configure projects as required when using configure-on-demand.
- Don't configure tasks when they are not requested.
- Deal with non-standard wrapper meta-data location.
- More accurate content assistance.
- User provides input to build execution.
- User edits dependencies via some UI.
- User upgrades Gradle version via some UI.
- User creates a new build via some UI.
- Provide some way to define additional JVM args and system properties (possibly via command-line args)
- Provide some way to locate Gradle builds, eg to allow the user to select which build to import.
