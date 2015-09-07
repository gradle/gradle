
This spec defines a number of features to improve the developer IDE experience with Gradle. It covers changes in Gradle to improve those features
that directly affect the IDE user experience. This includes the tooling API and tooling models, the Gradle daemon, and the Gradle IDE plugins
(i.e. the plugins that run inside Gradle to provide the IDE models).

This spec does not cover more general features such as improving dependency resolution or configuration performance.

# Implementation plan

Below is a list of stories, in approximate priority order:

## Feature -  Improve IDE project naming deduplication strategy

### Motivation

If we got a project structure like this:

    :foo:app
    :bar:app
    :foobar:app

it currently results in the following eclipse project names:

    app
    bar-app
    foobar-app

This behaviour can be quite confusing in the IDE. To make things clearer the deduplication strategy
should be changed in a way that it results in:

    foo-app
    bar-app
    foobar-app


# Implementation Plan

The general algorithm will look like this:

1. if no duplicate of project name is found in the hierarchy, use the original name as IDE project name
2. root project name keeps the original name in IDE
3. for all non unique project names

    3.1 the IDE project name candidate is changed to be _deduplicated_ `project.parent.name` + `-` + `project.name`

    3.2 duplicate words in the IDE project name candidate are removed.( eg `gradle-gradle-core` becomes `gradle-core`

    3.3 skip 3.2 for identical parent and child project name

    3.4 repeat starting from 3.1 with all projects with non unique names yet

4. deprecate setting ide project name in whenMerged/beforeMerged hook.

# Test Coverage

* _gradle eclipse_ / _gradle idea_ on root of multiproject with given project layout containing duplicate names:

```
    root
    \- foo
       \- app
    \- bar
       \- app
```

results in IDE project names

```
    root
    \- foo
       \- foo-app
    \- bar
       \- bar-app
```
* _gradle eclipse_ / _gradle idea_ on root of multiproject with given project layout containing with no duplicate names:

```
    root
    \- foo
       \- bar
    \- foobar
       \- app
```

results in IDE project names

```
    root
    \- foo
       \- bar
    \- foobar
       \- app
```


* explicit configured IDE project name take precedence over deduplication:
    given

```
     root
     \- foo
        \- app
           \-build.gradle // contains e.g. eclipse.project.name = "custom-app" / ideaModule.module.name = "custom-app"
     \- bar
        \- app
```

results in

```
     root
     \- foo
        \- custom-app
     \- bar
        \- app
```


* given project layout

```
    app
    \- app
    \- util
```

results in

```
    app
    \- app-app
    \- util
```

* given project layout

```
    root
    \- app
    \- services
       \- bar
          \- app
```

results in

```
    root
    \- root-app
    \- services
       \- bar
          \- bar-app
```

* given project layout

```
    root
    |- foo-bar
    |- foo
    |  \- bar
    \- baz
       \- bar
```

results in

```
    root
    \- foo-bar
    \- foo
       \- root-foo-bar
    \- baz
       \- root-baz-bar

```


* given project layout

```
    myproject
    \- myproject-app
    \- myproject-bar
       \- myproject-app
```

results in

```
    myproject
    \- myproject-app (instead of myproject-myproject-app)
    \- myproject-bar
       \- myproject-bar-app (instead of myproject-bar-myproject-app)
```

* deduplication logics works with deduplicated parent module name. given project layout:

```
   root
    \- bar
        \- services
            \- rest
    \- foo
            \- services
                \- rest
```

results in

```
    root
    \- bar
        \- bar-serivces
            \- bar-services-rest
    \- foo
        \- foo-serivces
            \- foo-services-rest
```

* setting ide project name within `whenMerged` results in a deprecation warning.
* setting ide project name within `beforeMerged` results in a deprecation warning.
* tests work with IDE gradle plugins and with IDE model queried via tooling api

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

### GRADLE-2434 - IDE visualises and runs task selectors (DONE)

On the command-line I can run `gradle test` and this will find and execute all tasks with name `test` in the current project
and all its subprojects.

Expose some information to allow the IDE to visualise this and execute builds in a similar way.

See [tooling-api-improvements.md](tooling-api-improvements.md#story-gradle-2434---expose-the-aggregate-tasks-for-a-project)

### IDE hides implementation tasks (DONE)

On the command-line I can run `gradle tasks` and see the public tasks for the build, and `gradle tasks --all` to see all the tasks.

Expose some information to allow the IDE to visualise this.

See [tooling-api-improvements.md](tooling-api-improvements.md#expose-information-about-the-visibility-of-a-task)

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

## Feature - Tooling API client cancels an operation (DONE)

Add some way for a tooling API client to request that an operation be cancelled.

The implementation will do the same thing as if the daemon client is disconnected, which is to drop the daemon process.
Later stories incrementally add more graceful cancellation handling.

See [tooling-api-improvements.md](tooling-api-improvements.md#story-tooling-api-client-cancels-a-long-running-operation)

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

## Feature - Expose build script compilation details

See [tooling-api-improvements.md](tooling-api-improvements.md#story-expose-the-compile-details-of-a-build-script):

Expose some details to allow some basic content assistance for build scripts:

- Expose the classpath each build script.
- Expose the default imports for each build script.
- Expose the Groovy version for each build script.

## Story - Tooling API stability tests

Introduce some stress and stability tests for the tooling API and daemon to the performance test suite. Does not include
fixing any leaks or stability problems exposed by these tests. Additional stories will be added to take care of such issues.

## Feature - Daemon usability improvements

### Story - Build script classpath can contain a changing jar

Fix ClassLoader caching to detect when a build script classpath has changed.

Fix the ClassLoading implementation to avoid locking these Jars on Windows.

### Story - Can clean after compiling on Windows

Fix GRADLE-2275.

### Story - Prefer a single daemon instance

Improve daemon expiration algorithm so that when there are multiple daemon instances running, one instance is
selected as the survivor and the others expire quickly (say, as soon as they become idle).

## Feature - Expose project components to the IDE

### Story - Expose Java components to the IDE

See [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Java language level and other details about a Java component.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose Groovy components to the IDE

See [tooling-api-improvements.md](tooling-api-improvements.md):

Expose Groovy language level and other details about a Groovy component.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose Scala components to the IDE

Expose Scala language level and other details about a Scala component.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose Web components to the IDE

Expose Web content, servlet API version, web.xml descriptor, runtime and container classpaths, and other details about a web application.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose J2EE components to the IDE

Expose Ear content, J2EE API versions, deployment descriptor and other details about a J2EE application.

Expose the corresponding Eclipse and IDEA model.

### Story - Expose artifacts to IDEA

Expose details to allow IDEA to build various artifacts: http://www.jetbrains.com/idea/webhelp/configuring-artifacts.html

## Feature - Daemon usability improvements

### Story - Daemon handles additional immutable system properties

Some system properties are immutable, and must be defined when the JVM is started. When these properties change,
a new daemon instance must be started. Currently, only `file.encoding` is treated as an immutable system property.

Add support for the following properties:

- The jmxremote system properties (GRADLE-2629)
- The SSL system properties (GRADLE-2367)
- 'java.io.tmpdir' : this property is only read once at JVM startup

### Story - Daemon process expires when a memory pool is exhausted

Improve daemon expiration algorithm to expire more quickly a daemon whose memory is close to being exhausted.

### Story - Cross-version daemon management

Daemon management, such as `gradle --stop` and the daemon expiration algorithm should consider daemons across all Gradle versions.

### Story - Reduce the default daemon maximum heap and permgen sizes

Should be done in a backwards compatible way.

## Feature - Tooling API client listens for changes to a tooling model

Provide a subscription mechanism to allow a tooling API client to listen for changes to the model it is interested in.

## Feature - Tooling API client receives test execution events

Allow a tooling API client to be notified as tests are executed

## Feature - Interactive builds

### Story - Support interactive builds from the command-line

Provide a mechanism that build logic can use to prompt the user, when running from the command-line.

### Story - Support interactive builds from the IDE

Extend the above mechanism to support prompting the user, when running via the tooling API.

# More candidates

Some more features to mix into the above plan:

- `IdeaSingleEntryLibraryDependency` should expose multiple source or javadoc artifacts.
- Honour same environment variables as command-line `gradle` invocation.
- Richer events during execution:
    - Task execution
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

### Story - Classpath export across projects in multi-project builds

Project files generated by the Gradle Idea and Eclipse plugins are responsible for deriving the classpath from the declared list of dependencies in the build file. The current
 behavior is best explained by example. Let's assume project A and B. Both projects are part of a multi-project build. Project B declares a project dependency on project A. The generated classpath
 of project B is a union of the classpath of project A (the generated JAR file plus its dependencies) and its own declared top-level dependencies and transitive dependencies. Classpath
 ordering matters. In practice this means the following: given that project A and B depend on a specific library with different versions, the "exported" dependency versions win as they happens
 to be listed first in classpath of project B. This behavior might lead to compilation and runtime issues in the IDE as no conflict-resolution takes place across projects.

[Concrete example](https://github.com/bmuschko/eclipse-export): Project B declares a project dependency on project A. Project A  declares a dependency in Guava 15.0, Project B declares a dependency on
Guava 16.0.1. Project B depends on A. If you generate the project files with the Gradle Eclipse plugin, project A is exported to B and appears to be on the classpath as the first entry (exported project).
As a result Guava 15 is found and not 16.0.1. Any code in B that relies on Guava's API from 16.0.1 will cause a compilation error.

The other use case to consider is the generation of project files directly via the IDE's import capabilities. In this scenario the Gradle IDE plugins might be used or only partially depending on
the product.

- IntelliJ: To our knowledge depends on classes from Gradle's internal API to generate classpath (`org.gradle.plugins.ide.internal.resolver.DefaultIdeDependencyResolver`).
- Eclipse: STS uses a custom model for the tooling API to generate the classpath. On top of the project/external classpath provided by the tooling API, the solution uses so called classpath containers
which are built using [Eclipse APIs](https://github.com/spring-projects/eclipse-integration-gradle/blob/f40acf8033db935270225e6ff4b5989f2f45abb4/org.springsource.ide.eclipse.gradle.core/src/org/springsource/ide/eclipse/gradle/core/classpathcontainer/GradleDependencyComputer.java#L116).
A classpath container is reflect in a single classpath entry in the generated `.classpath` file. The Gradle IDE plugins can be invoked optionally to derive additional customizations for the project files.
- Buildship: See Eclipse. Is there any difference in behavior? How is the classpath generated right now?

#### Implementation

Expose the `exported` flag for `EclipseProjectDependency` and `ExternalDependency`.

- Buildship should honor this flag when available, as later Gradle versions may use a different mapping and Buildship should not make assumptions regarding how the mapping works.

Change the Eclipse classpath mapping to contain transitive closure of all dependencies:

- All projects
- All external libraries
- All dependencies with `exported` set to `false`
- Deprecate `EclipseClasspath.noExportConfigurations`. This would no longer be used.
- Deprecate `AbstractLibrary.declaredConfigurationName`. This would no longer be used.
- Currently `DefaultIdeDependencyResolver.getIdeProjectDependencies()` returns direct project dependencies, should locate all project dependencies in the graph (see `ResolutionResult.getAllComponents()`).
- Currently `DefaultIdeDependencyResolver.getIdeRepoFileDependencies()` ignores anything reachable via a project dependency, should all external dependencies in the graph (as above).
- Currently `DefaultIdeDependencyResolver.getIdeLocalFileDependencies()` returns direct dependencies, should locate all self-resolving dependencies in the graph.
    - Implementation should simply iterate over the files of the configuration and remove anything that is associated with an artifact.
- Should end up with much simpler implementations for the methods of `DefaultIdeDependencyResolver`.

Change the IDEA classpath mapping to do something similar.

- Should be applied on a per-scope basis.
- Must continue to remove duplicates inherited from other scopes.
- IDEA mapping shares `DefaultIdeDependencyResolver` with the Eclipse mapping, so IDEA mapping should just work.

#### Test cases

- Verify the `exported` flag is available for Tooling API `EclipseProject` model's project and external dependencies.
- The classpath generated by the Gradle IDE plugins need to contain all dependencies of a project, including transitive dependencies:
    - Project A depends on project B depends on project C => IDE model for project A should include dependencies on both project B and project C.
    - Project A depends on project B depends on external library 'someLib' => IDE model for project A should include dependencies on both project B and 'someLib.
    - Project A depends on project B depends on file dependency => IDE model for project A should include dependencies on both project B and the file dependency.
    - No dependencies should be exported.
- In a multi-project build have two projects with conflicting version on an external dependency.
    - Project A depends on Guava 15.
    - Project B depends on Guava 16.0.1. A class in this project depends on API of version 16.0.1.
    - The generated project files for project B use Guava version 16.0.1 to allow for proper compilation.
    - This behavior should be observed using the IDE plugins as well as the tooling API.
    - *Manually* test that this scenario works with both Eclipse and IDEA, using the generated IDE project files.
- Existing test cases for the Idea and Eclipse plugins and Tooling API need to be modified to reflect changed export behavior.

#### Open issues

- Should we consider the use a classpath container solution? Given that some users check in the project files into VCS (for good or bad - it does exist in reality), every regenerated project file
 would need to be checked back into version control. With frequent changes that could get cumbersome. The benefit of using a classpath container is that the `.classpath` file doesn't change over
 time. The downside of using a classpath container is that it requires the use of Eclipse APIs and additional dependencies to Gradle core. As a side note: This model is used for other Eclipse IDE
  integrations like M2Eclipse and IvyDE so it's not uncommon. My guess is that it would require almost a rewrite of the existing code in the Gradle Eclipse plugin.

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
