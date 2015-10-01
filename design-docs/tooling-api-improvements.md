This specification defines a number of improvements to the tooling API.

# Use cases

## Bugfix: allow concurrent usage of different gradle distributions of the same version

When using the tooling api to work with different gradle distributions of the same version (e.g a gradle "-bin" and a "-all" distribution)
an OverlappingFileException can be thrown in the current implementation.
This is caused by loading the same version of the Gradle provider loaded up in multiple ClassLoaders (from each of the different distributions for the 2 different builds).
The provider loading must be changed to deal with this.

### implementation
- Instead of using the version string as cache key in `CachingToolingImplementation` use a hash of the files of the provider's classpath as a key to the cache.
- pass the distribution(by file reference) from the consumer across to the provider
- for provider versions is >= this implementation can use cached provider, otherwise use new one.

### integration test coverage

- Can load and use multiple distributions of the same gradle version for doing running multiple build requests via tooling api.
    - using the new tooling-api tested against multiple (older) gradle versions

### Open issues
- cannot handle init scripts in distributions init.d folder if as distribution of cached provider is used for resolving init scripts

## Tooling can be developed for Gradle plugins

Many plugins have a model of some kind that declares something about the project. In order to build tooling for these
plugins, such as IDE integration, it is necessary to make these models available outside the build process.

This must be done in a way such that the tooling and the build logic can be versioned separately, and that a given
version of the tooling can work with more than one version of the build logic, and vice versa.

## Replace or override the built-in Gradle IDE plugins

The Gradle IDE plugins currently implement a certain mapping from a Gradle model to a particular IDE model. It should be
possible for a plugin to completely replace the built-in plugins and assemble their own mapping to the IDE model.

Note that this is not the same thing as making the IDE plugins more flexible (although there is a good case to be made there
too). It is about allowing a completely different implementation of the Java domain - such as the Android plugins - to provide
their own opinion of how the project should be represented in the IDE.

## Built-in Gradle plugins should be less privileged

Currently, the built-in Gradle plugins are privileged in several ways, such that only built-in plugins can use
certain features. One such feature is exposing their models to tooling. The IDE and build comparison models
are baked into the tooling API. This means that these plugins, and only these plugins, can contribute models to tooling.

This hard-coding has a number of downsides beyond the obvious lack of flexibility:

* Causes awkward cycles in the Gradle source tree.
* Responsibilities live in the wrong projects, so that, for example, the IDE project knows how to assemble the
  build comparison project. This means that the IDE plugin must be available for the tooling API provider to work.
* Cannot replace the implementation of a model, as discussed above.
* A tooling model cannot be 'private' to a plugin. For example, the build comparison model is really a private cross-version
  model that allows different versions of the build comparison plugin to communicate with each other.
* A tooling model must be distributed as part of the core Gradle runtime. This makes it difficult to bust up the
  Gradle distribution.

Over time, we want to make the built-in plugins less privileged so that the difference between a 'built-in' plugin and
a 'custom' is gradually reduced. Allowing custom plugins to contribute tooling models and changing the build-in plugins
to use this same mechanism is one step in this direction.

# Implementation plan

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

# Feature: Fetching models

## Story: Tooling API client builds a complex tooling model in a single batch operation (DONE)

This story adds support for a tooling API to query portions of the Gradle model in an efficient way.

Introduce the concept of a `BuildAction`. A build action is provided by the tooling API client and is serialized across to the process where
the build is running. It can then query the models as required and return a result. This result is serialized back to the tooling API client.

This approach allows the action to make decisions about which models to request and to request multiple models efficiently within a single
build invocation. It can also later provide the action with access to features which cannot be serialized to the tooling API client.

    public interface BuildAction<T> extends Serializable {
        T execute(BuildController controller);
    }

    public interface BuildController {
        <T> T getModel(Class<T> modelType);
        // Later stories will add more here
    }

    public interface BuildActionExecutor<T> extends LongRunningOperation {
        T get();
        void get(ResultHandler<? super T> handler);
    }

    <T> ConsumerActionExecutor<T> execute(ConsumerAction<T> action);

The `BuildController` interface provides a single method `getModel()` which returns the tooling model of the given type for the default project
associated with the `ProjectConnection`. This method will return the same result as `ProjectConnect.getModel()` would.

For this story, the build action is executed before the build has been configured. If any model is requested, the build is fully configured. Later stories
will introduce a more efficient implementation, where the work done by the build is driven on demand by what the action asks for, and some caching of
models is used.

### Test cases

- Action builds a result and this result is received by the client.
    - Returns a custom result type
    - Returns null
    - Returns a built-in tooling model (such as `IdeaProject`)
    - Returns a custom result type that references a built-in tooling model
    - Returns a custom result type that references multiple tooling models
    - Returns a custom result type that references a custom tooling model whose implementation uses classes that conflict with the client
    - Client changes action implementation to return a different result
- Verify environment that the action executes in:
    - Logging is received by the client as part of the build output.
    - Context ClassLoader is set appropriately.
    - No Gradle-version specific classes are visible to the action.
- Client receives a reasonable error message when:
    - Target Gradle version does not support consumer actions.
    - The action throws an exception.
    - A failure occurs configuring the build.
    - A failure occurs building a requested model.
    - The action cannot be serialized or deserialized.
    - The action result cannot be serialized or deserialized.
    - The action throws an exception that cannot be serialized or deserialized.
    - The Gradle process is killed before the action completes.
    - The action requests an unknown model.
    - The action is compiled for Java 6 but the build runs with Java 5.

## Story: Tooling API build action requests a tooling model for a Gradle project

1. Add a new `GradleBuild` model which contains information about which projects are included in the build.
2. Add a new `BasicGradleProject` type to provide basic structural information about a project.
3. Extend `BuildController` to add methods to query a model for a given project.
4. Change `ProjectConnection.getModel(type)` and `BuildController.getModel(type)` to return only build-level models.
5. Change `BuildController.getModel(project, type)` to return only project-level models.

Here are the above types:

    interface BasicGradleProject { }

    interface GradleBuild {
        BasicGradleProject getRootProject();
        Set<? extends BasicGradleProject> getProjects();
    }

    interface BuildController {
        <T> T getModel(Model target, Class<T> modelType) throws UnknownModelException;
    }

Note: there is a breaking change here.

### Test cases

- Can request the `GradleBuild` model via `ProjectConnection`.
- Can request the `GradleBuild` model via a build action.
- Can request a model for a given project:
    - A `BasicGradleProject` for the specified project, not the root.
    - A `GradleProject` for the specified project, not the root.
    - An `IdeaModule` for the specified project, not the root.
    - An `EclipseProject` for the specified project, not the root.
- Cannot request a build model for a project:
    - Cannot request a `BuildModel`.
    - Cannot request a `BuildEnvironment`.
- Client receives decent feedback when
    - Requests a model from an unknown project.
    - Requests an unknown model.

## Story: Gradle provider builds build model efficiently

When the `GradleBuild` model is requested, execute only the settings script, and don't configure any of the projects.

## Story: Tooling API client determines whether model is available (DONE)

This story adds support for conditionally requesting a model, if it is available

    interface BuildController {
        <T> T findModel(Class<T> type); // returns null when model not present
        <T> T findModel(Model target, Class<T> type); // returns null when model not present
    }

### Test cases

- Client receives null for unknown model, for all target Gradle versions.
- Build action receives null for unknown model, for all target Gradle versions >= 1.8

## Story: Tooling API client changes implementation of a build action

Fix the `ClassLoader` caching in the tooling API so that it can deal with changing implementations.

## Story: Tooling API client launches a build using task selectors from different projects (DONE)

TBD

### Test cases

- Can execute task selectors from multiple projects, for all target Gradle versions
- Can execute overlapping task selectors.

## Story: Tooling API exposes project's implicit tasks as launchable (DONE)

Change the building of the `BuildInvocations` model so that:

- `getTasks()` includes the implicit tasks of the project.
- `getTaskSelectors()` includes the implicit tasks of the project and all its subprojects.

### Test cases

- `BuildInvocations.getTasks()` includes `help` and other implicit tasks.
    - Launching a build using one of these task instances runs the appropriate task.
- `BuildInvocations.getTaskSelectors()` includes the `help` and other implicit tasks.
    - Launching a build using the `dependencies` selector runs the task in the default project only (this is the behaviour on the command-line).
- A project defines a task placeholder. This should be visible in the `BuildInvocations` model for the project and for the parent of the project.
    - Launching a build using the selector runs the task.

## Story: Expose information about the visibility of a task (DONE)

This story allows the IDE to hide those tasks that are part of the implementation details of a build.

- Add a `visibility` property to `Launchable`.
- A task is considered `public` when it has a non-empty `group` property, otherwise it is considered `private`.
- A task selector is considered `public` when any task it selects is `public`, otherwise it is considered `private`.

### Test cases

- A project defines a public and private task.
    - The `BuildInvocations` model for the project includes task instances with the correct visibility.
    - The `BuildInvocations` model for the project includes task selectors with the correct visibility.
    - The `BuildInvocations` model for the parent project includes task selectors with the correct visibility.

## Story: Allow options to be specified for tasks

For example, allow something similar to `gradle test --tests SomePattern`

## Story: Tooling API build action requests a tooling model for a Gradle build (DONE)

This story adds support to build models that have a scope of a whole Gradle build (not just a project)

1. Add a new `GradleBuildToolingModelBuilder` similar to `ToolingModelBuilder`. Possibly an abstract class since this is an SPI.
2. Extend `ToolingModelBuilderRegistry` to allow registration of such a builder.
3. Change the way how models are queried from `ProjectConnection` to use this new builders (there is no project context passed).
   The only special case is the EclipseModel, which is actually built from the default project instead of the root project, so we'd need a specific implementation for that.
4. Extend `BuildController.getModel()` to support `GradleBuild` as model parameter or add `BuildController.getBuildModel(Class)`.
   Those would be using gradle model builder rather than project model builders

### Test cases

- Can register new model builder and
  - query it from client via `ProjectConnection`.
  - query it from client via build action.
- Can request a model via build action:
  - And get result from `GradleBuildToolingModelBuilder` for gradle build scope if passing `GradleBuild` as target.
  - And get result from `ToolingModelBuilder` for project scope if passing one of parameters describing project.
- Client receives decent feedback when requests an unknown model.

### Open issues

- A model builder should simply be a rule with some tooling model as output, and whatever it needs declared as inputs.

## Feature: Expose more details about the project

## Story: Expose the IDE output directories

Add the appropriate properties to the IDEA and Eclipse models.

## Story: Expose the project root directory

Add a `projectDir` property to `GradleProject`

### Test coverage

- Verify that a decent error message is received when using a Gradle version that does not expose the project directory

## Story: Expose the Java language level

Split out a `GradleJavaProject` model from `GradleProject` and expose this for Java projects.

Add the appropriate properties to the IDEA, Eclipse and GradleJavaProject models. For Eclipse, need to expose the appropriate
container and nature. For IDEA, need to choose between setting level on all modules vs setting level on project and inheriting.

## Story: Expose the Groovy language level

Split out a `GradleGroovyProject` model from `GradleProject` and expose this for Groovy projects.

Add the appropriate properties to the IDEA, Eclipse and GradleGroovyProject models.

## Story: Expose generated directories

It is useful for IDEs to know which directories are generated by the build. An initial approximation can be to expose
just the build directory and the `.gradle` directory. This can be improved later.

## Story: Add a convenience dependency for obtaining the tooling API JARs

Similar to `gradleApi()`

# Backlog

* Replace `LongRunningOperation.standardOutput` and `standardError` with overloads that take a `Writer`, and (later) deprecate the `OutputStream` variants.
* Change the tooling API protocol to allow the provider to inform the consumer that it is deprecated and/or no longer supported, and fix the exception
  handling in the consumer to deal with this.
* Test fixtures should stop daemons at end of test when custom user home dir is used.
* Introduce a `GradleExecutor` implementation backed by the tooling API and remove the in-process executor.
