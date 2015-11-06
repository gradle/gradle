
This spec defines a number of features to improve the developer experience using Gradle from the IDE. It covers changes in Gradle to improve those features
that directly affect the IDE user experience. This includes the tooling API and tooling models, the Gradle daemon, and the Gradle IDE plugins
(i.e. the plugins that run inside Gradle to provide the IDE models).

Tooling API stories that are not related directly to the IDE experience should go in the `tooling-api-improvements.md` spec.

# Implementation plan

## Feature - Expose source and target platforms of JVM language projects

### Story - Expose natures and builders for projects

The Motiviation here is to model java projects better within the EclipseProject model. Therefore we want to provide
the Eclipse Model with information about natures and builders. A Java Project is identified
by having an EclipseModel providing a java nature. IDEs should not guess which natures and builders to add but get the information
from the TAPI.

#### Estimate

- 3 days

#### The API

    interface EclipseProject {
        ...
        ...
        List<String> getProjectNatures(List<String> defaults)
        List<BuildCommand> getBuildCommands(List<BuildCommand> defaults)
        ...
    }

    interface BuildCommand {
        String getName()
        Map<String,String> getArguments()
    }


#### Implementation

- Add `List<String> getProjectNatures(List<String> defaults)` to the EclipseProject interface.
- Add `List<String> projectNatures` property (setter + getter) to `DefaultEclipseProject`
- Change EclipseModelBuilder to
    - Add `org.eclipse.jdt.core.javanature` nature for all java projects in the multiproject build to EclipseProject.
    - Add all natures declared via `eclipse.project.buildCommand` to EclipseProject model.

- Add `getBuildCommand(List<BuildCommand> defaults)` to the EclipseProject interface.
- Add `List<BuildCommand> buildCommands` property to `DefaultEclipseProject`
- Change EclipseModelBuilder to
    - Add build command with name `org.eclipse.jdt.core.javabuilder` and no arguments for all java projects in multiproject build
    - Apply custom added build commands (via `eclipse.project.buildCommand`)

#### Test coverage

- `EclipseProject#getProjectNatures(List<String>)` of a Java project contains java nature (`org.eclipse.jdt.core.javanature`)
- `EclipseProject#getProjectNatures(List<String>)` respects custom added natures (via `eclipse.eclipse.project.natures`)
- older Gradle versions return default value when calling `EclipseProject#getProjectNatures(List<String> defaultValue)`

- `EclipseProject#getBuildCommands(List<BuildCommand>)` of a Java project contains java nature (`org.eclipse.jdt.core.javanature`)
- `EclipseProject#getBuildCommands(List<BuildCommand>)` respects custom added build commands.
- older Gradle versions return default value when calling `EclipseProject#getBuildCommands(List<String> defaultValue)`


### Story - Expose Java source level for Java projects to Eclipse

The IDE models should provide the java source compatibility level. To model java language specific information
we want to have a dedicated model for eclipse specific java information and gradle specific java information.

#### Estimate

- 3 days

#### The API

    interface JavaLanguageLevel {
        String getLevel()
    }

    interface JavaSourceSettings {
        JavaSourceLevel getLanguageLevel()
    }

    interface JavaSourceAware {
        JavaSourceSettings getJavaSourceSettings()
    }

    interface EclipseProject extends JavaSourceAware {
    }


- The `JavaSourceSettings` interface describes Java-specific details for a model.
  It initially defines only one attribute, describing the `sourceLanguageLevel`.
- The `getLanguageLevel()` returns the `eclipse.jdt.sourceCompatibility` level that is configurable within the `build.gradle` or per default uses
similar version as `JavaConvention.sourceCompatibility` configuration.
- For a no Java Project `JavaSourceAware.getJavaSourceSettings()` returns null
- For older Gradle version the `JavaSourceAware.getJavaSourceSettings()` throws `UnsupportedMethodException`.

#### Implementation
- Introduce `JavaLanguageLevel` which contains a String representation of the java language level via `getLevel()`.
- Add a `JavaSourceSettings` implementation with `JavaLanguageLevel getLanguageLevel()`.
- Introduce `JavaSourceAware` interface abstracting a common `JavaSourceSettings.getJavaSourceSettings()` method.
- Update `EclipseProject` to extend `JavaSourceAware`.
- Update DefaultEclipseProject to implement new `.getJavaSourceSettings()` method
- Update `EclipseModelBuilder` to set values for the `JavaSourceSettings`
    - return `null` if the project doesn't apply the `java-base` plug-in.
    - otherwise `JavaSourceSettings.getLanguageLevel()` returns the value of `eclipse.jdt.sourceCompatibility`.

#### Test coverage

- `EclipseProject.getJavaSourceSettings()` throws `UnsupportedMethodException`for older target Gradle version.
- `EclipseProject.getJavaSourceSettings()` returns null if no java project.
- Java project, with 'eclipse' plugin not defining custom source compatibility via `eclipse.jdt.sourceCompatibility`
- Java project, with 'eclipse' plugin defining custom source compatibility via `eclipse.jdt.sourceCompatibility`
- Multiproject java proect build with different source levels per subproject

### Story - Expose Java source level for Java projects to IDEA

This is the IDEA-counterpart of the _Expose Java source level for Java projects to Eclipse_ story. The goal is to expose the source
language levels for each module in a project.

#### Estimate

- 2 days

#### The API

    interface IdeaJavaLanguageLevel extends JavaLanguageLevel {
        boolean isInherited()
    }
    interface IdeaJavaSourceSettings extends JavaSourceSettings {
        IdeaJavaLanguageLevel getLanguageLevel
    }

    interface IdeaModule extends JavaSourceAware {
        IdeaJavaSourceSettings getJavaSourceSettings()
    }

    interface IdeaProject extends JavaSourceAware {
    }

#### Implementation
- Introduce `IdeaJavaLanguageLevel` extending `JavaLanguageLevel`
- Introduce `IdeaJavaSourceSettings` extending `JavaSourceSettings`.
- Let the `IdeaModule` model extend `JavaSourceAware` and expose specialized `IdeaJavaSourceSettings`.
- Let the `IdeaProject` model extend `JavaSourceAware` to expose `JavaSourceSettings`
- Update `DefaultIdeaProject` to implement new `getJavaSourceSettings()` method
- Update `DefaultIdeaModule` to implement new `getJavaSourceSettings()` method
- Update `IdeaModelBuilder` to set values for `getJavaSourceSettings()` for `IdeaProject` and `IdeaModule`
    - return `null` if not a Java project
    - otherwise configure it as follows
        - `IdeaProject.getLanguageLevel().getLevel()` is calculated from `org.gradle.plugins.ide.idea.model.IdeaProject.getLanguageLevel()`.
        - `IdeaModule.getLanguageLevel().getLevel()` returns same value as root `IdeaProject.getLanguageLevel().getLevel()`
        - `IdeaModule.getLanguageLevel().isInherited` returns true

#### Test coverage
- `IdeaModule.getJavaSourceSettings()` returns null for non java projects
- `IdeaProject.getJavaSourceSettings()` returns null for non java projects
- `IdeaModule.getJavaSourceSettings().isInherited()` returns true.
- `IdeaProject.getJavaSourceSettings()` throws `UnsupportedMethodException`for older target Gradle version.
- `IdeaModule.getJavaSourceSettings()` throws `UnsupportedMethodException`for older target Gradle version.
- `IdeaProject.getJavaSourceSettings().getLanguageLevel()` matches language level information obtained from `project.sourceCompatibility`.
- `IdeaModule.getJavaSourceSettings().getLanguageLevel()` matches custom `idea.project.languageLevel`.
- Can handle multi project builds with different source levels per subproject.

#### Open questions
- configuring per-module source level is currently not supported Idea plugin?


### Story - Expose target JDK for Java projects to Eclipse

#### Estimate
- 3 days

#### Implementation

- For `EclipseProject`, add details of the target JVM:
    - JDK name
    - JDK Java version
    - JDK install directory
- Improve the target SDK detection. Name and version should be based on the target compatibility of the project.
    - consume this information from the eclipse plugin configuration if provided (`eclipse.jdt.targetCompatibility`)

- For older Gradle versions:
    - TBD - reasonable defaults for Java language version

#### Implementation

- Implement SDK detection (pointer: have a look at testfixture we already have for this: `AvailableJavaHomes`)
- Extend EclipseProject to add TargetJvm Details (name, version, installdir)

#### Test coverage

- EclipseProject#targetSdk points to matching SDK
    - for java projects targetCompatibility matches specified project#targetCompatibility
    - for projects with eclipse plugin targetCompatibility matches `eclipse.jdt.targetCompatibility`
    point to exact match
    - point to exact match if available (requested 1.6 -> installed jdk 1.6 found)
    - points to complian sdk if exact match not available

### Story - Expose target JDK for Java projects to IDEA

#### Estimate

- 2 days

#### Implementation

- For `IdeaModule`, add details of the module SDK:
    - Inherit from project?
    - SDK name
    - Java version
    - Installation directory
- For `IdeaProject`, add more details to the IDEA project SDK:
    - Java version
    - Installation directory
- Improve the target SDK detection. Name and version should be based on the target compatibility of the project.
- IDEA Project SDK should be the highest version used by the modules.
- Include the appropriate details in the files generated by `gradle idea`.
- For older Gradle versions:
    - Default inherit from project to `true`.
    - TBD - reasonable defaults for other versions?


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

## Feature - Developer uses projects from multiple Gradle builds from IDE

Projects might define a binary dependency on a library produced by another project. Usually, these projects are built separately from each other and live in distinct source code repositories.
 The typical workflow for a developer that has to work on both projects would be to make a change to project A, publish its artifact and build project B with the changed dependency. If the
 library version of project A changes, project B needs to depend on the new artifact version. For developers this workflow is cumbersome and time-consuming. This feature allows a developer to work
 on multiple projects in a single IDE session that would normally be independent.

### Story - Introduce the concept of a "workspace" to the Tooling API

Introduce the concept of a "workspace" to the Tooling API. This is simply a collection of Gradle projects that the IDE user is working on. These projects may come from different Gradle builds.

A Tooling API client will be able to define a workspace and query it as if it were a single, unified Gradle build containing all the projects in the workspace. Where possible, binary dependencies
would be replaced with source dependencies between IDE modules.

For example, application A and library B might normally be built separately, as part of different builds. In this instance, application A would have a binary dependency on library B,
consuming it as a jar downloaded from a binary repository. When application A and library B are both imported in the same workspace, however, application A would have a source dependency on
library B.

#### Estimate

-

#### Implementation

    public interface ProjectWorkspace {
        void addProject(GradleProject gradleProject);
        void removeProject(GradleProject gradleProject);
        DomainObjectSet<GradleProject> getProjects();
    }

- Introduce a `ProjectWorkspace` interface that describes the collection of independent projects as a whole.
- Projects can be added or removed from the workspace. A workspace can return the list of projects that have been registered.
- A workspace does not depend on the Eclipse or Idea models.

#### Test cases

- If a project was added to the workspace, it can be retrieved from the list of projects.
- If a project already exists in the workspace, adding it again doesn't add a duplicate.
- If a project was removed from the workspace, the list of projects does not contain it anymore.
- If a project is requested to be removed from the workspace and doesn't exist, the list of projects isn't modified.
- The order of projects in the list is maintained.

#### Open issues

- We could potentially merge this story with the next one and just make it Eclipse-specific? Maybe we should just deal with the concrete IDE-specific `GradleProject` implementations
 e.g. `EclipseProject`.

### Story - Expose "workspace" concept for Eclipse

The generic workspace concept should be usable from the Eclipse Tooling model. Expose ways to match project coordinates to coordinates of a module
dependency.

#### Estimate

-

#### Implementation

    public interface ComposableEclipseProject {
        String getName();
        DomainObjectSet<? extends EclipseProject> getProjects();
    }

    public class DefaultComposableEclipseProject {
        private final ProjectWorkspace projectWorkspace = new ProjectWorkspace();

        public void addProject(DefaultEclipseProject project) {
            projectWorkspace.addProject(project.getGradleProject());
        }

        public void removeProject(DefaultEclipseProject project) {
            projectWorkspace.addProject(project.getGradleProject());
        }

        public List<DefaultEclipseProject> getProjects() {
            // Turn the list of GradleProjects into a list of DefaultEclipseProjects
            return projectWorkspace.getProjects();
        }
    }

    public interface GradleProjectIdentity {
        GradleModuleVersion getGradleModuleVersion();
    }

    public class PartialBasicGradleProject implements GradleProjectIdentity {
        private GradleModuleVersion gradleModuleVersion;

        public void setGradleModuleVersion(GradleModuleVersion gradleModuleVersion) {
            this.gradleModuleVersion = gradleModuleVersion;
        }

        @Override
        public GradleModuleVersion getGradleModuleVersion() {
            return gradleModuleVersion;
        }
    }

- Introduce a `ComposableEclipseProject` interface that describes the collection of independent projects as a whole for the Eclipse model.
- Allow for providing a name to be able to better identify it.
- The `ComposableEclipseProject` interface allows for returning all projects of a composable build (which effectively represents a workspace).
- The current `GradleProject` API does not expose its identity through coordinates (`group`, `name`, `version`). Introduce a `GradleModuleVersion` to the model.
- The `GradleModuleVersion` is used to compare a project coordinate with coordinates of its declared dependencies `DefaultEclipseExternalDependency.getGradleModuleVersion()`.
If a match can be determined, the module dependency can be substituted for a project dependency. For later stories, the project coordinates can also be used to substitute
a source dependency with a binary dependency (see follow-up story).

#### Test cases

- The `ComposableEclipseProject` can be named and populated with projects.
- A `GradleProject` populates its module version properly. The module version can retrieved from `DefaultEclipseProject`.
- A project and an external dependency can be matched based on the module version.

#### Open issues

- Support for IDEA is out-of-scope.

### Story - Define a "workspace" in Buildship

From Buildship, a developer should be able to point to one or many projects in the local file system to form a workspace.
If any of the module dependencies matches on the coordinates of one of the selected projects, they'll be able to form
 a workspace. Buildship will treat the matching projects as source dependencies instead of binary dependencies.

#### Estimate

-

#### Implementation

- Expose a new dialog in Buildship (maybe some new type of import?) that allows for selecting projects that should form a workspace.
    - At least two projects have to be selected.
    - Projects can live in any directory on the local filesystem.
    - The project has to be a valid Gradle project and define the properties `group`, `name` and `version`.
- Buildship will indicate which projects are substitutable based on their coordinates (potentially with a preview).
    - Iterate over all dependencies of a project accessible via `DefaultEclipseProject.getClasspath()`.
    - Each dependency provides its coordinates through `DefaultEclipseExternalDependency.getGradleModuleVersion()`.
    - Compare the dependency coordinates with the coordinates of the project through `DefaultEclipseProject.getGradleModuleVersion()`.
    - If a match is determined, use the project path. If duplicate project paths are found, render a error message and disallow import.
    - Give a visual indication (e.g. icon) that a substitution was performed for a project.
- Allow for a context menu that brings up the original substitution dialog in case the user wants to modify the project selection.
- After selecting the projects the exposed Tooling API is consumed to form the workspace.
- Buildship renders the workspace in the project view as a multi-project build.
- The developer can make changes to any project's build script. Newly established (and substitutable) dependencies between projects are resolved as project dependencies.
- A workspace needs to be persistable.

#### Test cases

- Buildship doesn't allow creating a workspace if only 0 or 1 projects were selected.
- Project that do not define the proper coordinates cannot be used to form a workspace.
- If no substitutable module dependency can be determined, the dialog will render a error message.
- Projects that are part of a workspace can be built together based on established project dependencies.
- If the coordinates of a substituted module dependency is changed, Buildship needs to react properly.
    - If coordinates don't match up anymore, Buildship will depend on the module dependency.
    - If coordinates do match up, Buildship will re-establish the project dependency in the underlying model.
    - Eclipse project synchronization is initiated.
- Closing and re-opening Buildship will re-establish a workspace.

#### Open issues

- Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or from
the command-line. This would be an additional feature.
- De-duping of duplicate project names is out-of-scope for this story. It's going to be addressed in a separate story.
- The [concept of workspace exists in Eclipse](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.user/concepts/cworkset.htm?cp=0_2_1_6)
 which could be used to define a [custom extension point](http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workingSets.html?cp=2_1_1_202).
 However, investigation is needed if and how can we use them.

### Story - Ensure unique project names for a "workspace" in Buildship

Selected projects in a workspace might have the same project name. At the moment Buildship does not allow more than one project with the same even though they might live
in different locations of the project hierarchy (see [reported issue](https://bugs.eclipse.org/bugs/show_bug.cgi?id=479609)). The same situation might occur for imported
projects for a workspace. This story implements a du-duping mechanism in Buildship.

#### Estimate

-

#### Implementation

- De-duping should work from the regular import dialog as well as the "workspace" import dialog.
- If Buildship encounters two projects with the same project name, an algorithm will de-dedupe the project names. De-duped project names are only logic references to the
original projects. The actual project name stays unchanged. Buildship should be able to render actual and logic project names e.g. `myProject [my-project-1]`, `myProject [my-project-2]`.
- The logic should be implemented in a way that makes it reusable from different contexts within Buildship.
- Gradle core implements a similar algorithm for the IDE plugins. Reuse it if possible.

#### Test cases

- If the names of all imported projects are unique, de-duping doesn't have to kick in.
- If at least two imported projects have the same name, de-dupe the names. De-duped project names still reference the original project. The original and de-duped project names
should be rendered in Eclipse's project view section.
- De-duping may be required for more that one duplicate project name.
- Multi-project builds can contain duplicate project names in any leaf of the project hierarchy.

## Feature - Developer uses subset of projects for a Gradle build from IDE

Large multi-project builds may consist of hundreds of projects. If a developer only works on a subset of these projects (usually not more than 5), the developer pays a penalty
in terms of build execution performance as project dependencies have to be rebuilt (even though up-to-date checks might kick in). Another factor that slows down development
is the re-indexing of changed files in the IDE. This feature allows for selectively substituting project dependencies with binary dependencies in Buildship.

For example, application A and library B might normally be built together as part of the same build. Application A would have a source dependency on library B. When application A is imported
 in a workspace and library B is not, application A would have a binary dependency on library B, either using a jar downloaded from a repository or built locally.

### Story - Select subset of project for a "workspace" in Buildship

Build on the workspace concept from the previous feature, to replace source dependencies with binary dependencies.

#### Estimate

-

#### Implementation

- In the import screen of Buildship, allow the user to select projects that define project dependencies. For these projects try to resolve the binary dependency.
    - The user can decide to substitute none or (any number of projects - 1). This assumes that the developer wants to work on the source code of at least on project.
    - The project selected substitution has to define the properties `group`, `name` and `version` in order to build the module coordinates for external lookup.
- Buildship will indicate which projects are substitutable based on their coordinates (potentially with a preview).
    - Iterate over all project dependencies of a project accessible via `DefaultEclipseProject.getProjectDependencies()`.
    - Each dependency provides its coordinates through `DefaultEclipseProjectDependency.getTarget().getGradleModuleVersion()`.
    - Check for resolvable binary dependency available in any of the repositories defined for the coordinates.
    - If a match is determined, use the module coordinates. Replace the source dependency with a binary dependency.
    - Give a visual indication (e.g. icon) that a substitution was performed for a project.
- Allow for a context menu that brings up the original substitution dialog in case the user wants to modify the project selection.
- A workspace needs to be persistable.

#### Test cases

- A source dependency can only be replaced if the binary dependency exists in any of the declared binary repositories. Otherwise, don't allow substitution.
- Substituted projects are not built as part of the multi-project build (e.g. no tasks are invoked to compile the code and create the JAR file).
- The workspace as a whole is buildable. That means all dependencies can be resolved, no compilation issues occur.
- If the list of repositories was changed in the build script (e.g. by editing the file), BuildShip will need to check if substituted dependencies are still valid.
- If the coordinates of a substituted module dependency are changed, Buildship needs to react properly.
    - If coordinates don't match up anymore, Buildship will depend on the project dependency.
    - If coordinates do match up, Buildship will re-establish the binary dependency in the underlying model.
    - Eclipse project synchronization is initiated.
- Closing and re-opening Buildship will re-establish a workspace.

#### Open issues

- Out-of-scope for this feature would be the ability to run builds using the workspace definition from the IDE or the command-line.
- The import screen is only one entry point for this functionality. Should we have other dialogs/screen to allow for substitution? What about changing a workspace after
it has been created?

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
