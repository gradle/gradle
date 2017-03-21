# Public API equivalents for highly-used internal APIs

## High-level goals

* Empowering the community by giving them public, well-supported, and forward-compatible APIs.
* Dogfooding of public APIs in Gradle core plugins. Work towards the goal of avoid the use of any internal API in core plugins.
* Establishing good examples for plugin development.

## Technical details

* Most of the discussed internal API already exist in Gradle core in one form or another.
* Any of public APIs planned to be exposed will be implemented with Java.

## Story: Identify and promote first-class support for mapping between extension and task properties

Plugin developers are currently using the internal API for convention mapping to map extension values to DSL objects. The typical use case would be the mapping of a extension properties to task properties. Currently, convention mapping is the only effective method to avoid evaluation order issues for this use case with the current model. The software model solves the problem by defining [rule based model configuration](https://docs.gradle.org/current/userguide/software_model.html).

We need to identify a first-class, public API solution for the problem. As we won't promote the software model for the JVM domain, we'll need a different solution.

Tracked issue: https://github.com/gradle/gradle/issues/726

### Options

1. Turn convention mapping into a public API as is with minor changes.

    **Pros:**

    - Existing plugins that use convention mapping will only have to switch to public API.
    - No change in behavior.
    
    **Cons:**
    
    - Convention mapping as it stands right now has pitfalls (e.g. requires the user to call getter methods to work properly).
    - Convention mapping does not carry the information on how the value is built and what its inputs are.

2. Formalize the concept of a lazy or derived value by introducing representative types.

    **Pros:**
    
    - Provides a strongly-typed API which aligns with our vision of Gradle's API.
    - It carries provenance information with it, eg, where did this value come from?
    - It carries information on how to build the value and what its inputs are, eg which tasks should be run before we can query the value?
    - It allows detection of mutation after the value has been used as a task input.
    - It allows caching of the derived value.
    
    **Cons:**
    
    - The new API would be a stand-in replacement for convention mapping.
    - Existing plugins will need to change the types as well as getters/setters for properties.
    - Plugin developers might find it confusing to use different APIs (e.g. internal convention mapping and the new API) to solve the same problem.

Both options would break backwards compatibility of a plugin. Optimally, the new API would be introduced with a major version of Gradle.

It was decided to go with option 2 as the approach has various benefits over convention mapping as is.

#### User visible changes

Introduce a new interface named `Provider` representing the provider for a value:

    public interface Provider<T> {
        /**
         * Returns the value defined for the provider.
         *
         * @return Value
         */
        T get();
    }

Users can create a new `Provider` implementation with the following methods on `Project`:

    public interface Project {
        /**
         * Creates a {@code Provider} implementation based on the provided class.
         * The value returned by the provider is represented by the default value of the data type.
         *
         * @param clazz The class to be used for provider
         * @return The provider. Never returns null.
         * @throws org.gradle.api.InvalidUserDataException If the provided class is null.
         */
        <T> Provider<T> defaultProvider(Class<T> clazz);
    
        /**
         * Creates a new {@code Provider} for the Closure that lazily evaluates to a specific value.
         *
         * @param value The value to be used for type returned by the provider
         * @return The provider. Never returns null.
         * @throws org.gradle.api.InvalidUserDataException If the provided value is null.
         */
        <T> Provider<T> provider(Callable<T> value);
        
        /**
         * Creates a new {@code Provider} that eagerly evaluates to a specific value.
         *
         * @param value The value to be used for type returned by the provider
         * @return The provider. Never returns null.
         * @throws org.gradle.api.InvalidUserDataException If the provided value is null.
         */
        <T> Provider<T> provider(T value);
    }

Example usage:

    task myTask(type: MyTask) {
        enabled = provider(true)
        outputFiles = provider(files("$buildDir/output.txt"))
    }
    
    class MyTask extends DefaultTask {
        private Provider<Boolean> enabled = project.defaultProvider(Boolean)
        private Provider<FileCollection> outputFiles = project.defaultProvider(FileCollection)
        
        @Input
        boolean getEnabled() {
            enabled.getValue()
        }
        
        void setEnabled(Provider<Boolean> enabled) {
            this.enabled = enabled
        }
        
        @OutputFiles
        FileCollection getOutputFiles() {
            outputFiles.getValue()
        }

        void setOutputFiles(Provider<FileCollection> outputFiles) {
            this.outputFiles = outputFiles
        }
        
        @TaskAction
        void doSomething() { ... }
    }

#### Implementation

* Introduce a provider implementation that can return a lazily calculated or eagerly fetched value.
    * `@Input` annotations can infer task dependency.
* Introduce new methods on `Project` for creating a provider instance.
    * These methods are usable from Java, Kotlin and Groovy.
    * A lazy value can be provided via a `Callable`.
    * A method allows for return a default value for a specific type.
* (Potentially) introduce a way to auto-generate getters/setters for fields of type `Provider` to reduce boiler-plate code.
    * Options for implementing the solution include ASM or annotation processing.
    * The solutions needs work across multiple JVM-based languages e.g. Java, Groovy and Kotlin.
* Use the new concept in a representative Gradle core plugin (e.g. JaCoCo plugin) as POC.
* Mark newly introduced public API with `@Incubating` and `@since` annotations.
* Properly document public API with Javadocs.
* Enhance user guide sections covering plugins and tasks and cover appropriate information.
    * Explain the concept with the help of a concrete use case.
    * Demonstrate how to use the concept with Java and Groovy.
* Add one or two samples to the Gradle core distribution that uses the concept.
* Create follow-up issue for a guide covering concept in the context of plugin development.

#### Test coverage

* Custom task implementation can use provider type to defined properties for Java and Groovy implementations.
* Extension properties can be lazily mapped to task properties.
* A provider instance can be used as task dependency for a task.
* Convention mapping and the provider concept can be used together for a custom task implementation to provide backward compatibility for existing plugin.
* Existing test coverage of plugin using the new concept works as before.

## Story: FileCollection is-a Provider

A `FileCollection` provides a set of files. Therefore, a `FileCollection` is a `Provider` for files.

### Implementation

* Change the mutable interface for `FileCollection` and `FileTree` to reflect the behavior.
* Instead of using `Provider<ConfigurableFileCollection>` the user can just use `ConfigurableFileCollection`.
* Instead of using `Provider<ConfigurableFileTree>` the user can just use `ConfigurableFileTree`.

## Story: Provider implements Buildable and can be used for task inference

### Implementation

* The interface `Provider` implements `Buildable`.
* A new interface is introduced that allows for defining tasks that produce the value of the provider.
<!-- -->
    /**
     * Returns the set of tasks which build the value of this provider.
     *
     * @return The set. Returns an empty set when there are no such tasks.
     */
    Set<Object> getBuiltBy();

    /**
     * Sets the tasks which build the value of this provider.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    ConfigurableFileCollection setBuiltBy(Iterable<?> tasks);

    /**
     * Registers some tasks which build the value of this provider.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    ConfigurableFileCollection builtBy(Object... tasks);

* The provider factory returns the new, configurable type of the provider and property state.

## Story: Use Provider concept in all Gradle core plugins

Initially the JaCoCo plugin was used a POC playground for the provider concept. Now all Gradle core plugins need to switch from convention mapping to the use of `Provider`.

## Story: DependencyHandler exposes methods for declaring public and internal Gradle API

Developers have to rely on the `gradleApi()` dependency when implementing a Gradle plugin. This dependency exposes the public _and_ the private API. Given that the internal API is not clearly separated from the public API plugins can easily break in future versions of Gradle. This story aims for exposing just the public API so users do not accidentally use the internal API leading to more stable plugins and better cross-version compatibility.

Tracked issue: https://github.com/gradle/gradle/issues/1156

### User visible changes

Introduce a new method for exposing the Gradle public API:

    public interface DependencyHandler {

        /**
         * Creates a dependency on the public API of the current version of Gradle.
         *
         * @return The dependency.
         */
        Dependency gradlePublicApi();
        
        /**
         * Creates a dependency on the internal API of the current version of Gradle.
         *
         * @return The dependency.
         */
        Dependency gradleInteralApi();
        
        /**
         * @deprecated Replaced by the methods {@see #gradlePublicApi()} and {@see #gradleInteralApi()}.
         */
        @Deprecated
        Dependency gradleApi();
    }

### Usage example

Declaring a dependency on the Gradle core public and internal API:

    dependencies {
        compile gradlePublicApi()
        compile gradleInteralApi()
    }

### Implementation

* The method `gradlePublicApi()` only exposes Gradle's public API and no internal API.
    * Optimally `gradlePublicApi()` should only use Gradle's API or the Java API but does not have any external dependencies.
    * The dependency includes the public API of Gradle core plugins.
    * Reuse existing public API mapping file `api-mapping.txt` to identify classes that need to be packaged. If a classes to be included declares an internal class then it should also be included e.g. `AntBuilder.AntMessagePriority`.
    * The JAR file containing the public API is generated at runtime upon first usage similar to `gradleApi()` and `gradleTestKit()`.
    * The target output directory is locked while creating the JAR files.
* The method `gradleInternalApi()` only exposes Gradle's internal API and no public API.
    * Include any other class that was included in the public API JAR.
    * Apply shading for classes referred to by the internal API.
    * The JAR file containing the public API is generated at runtime upon first usage similar to `gradleApi()` and `gradleTestKit()`.
* Deprecate the method `gradleApi()`.
* Mark the new methods with `@Incubating` and `@since`.
* Change Gradle core code base to use `gradlePublicApi()` instead of `gradleApi()`.
* Clearly explain the benefits of the new methods in the user guide.
* Update user guide, sample projects and guide to use the new method.

### Test coverage

* Existing test cases pass.
* A project depending on the public API cannot import classes from the Gradle internal API.
* A project depending on the internal API can import classes from the Gradle internal API.
* Methods for public and internal API can be applied to the same project.
* `ProjectBuilder` can use use the new methods.
* Users of `gradleApi()` receive a warning that method is deprecated.

## Story: DependencyHandler exposes methods for declaring public API for Gradle core plugins

The goal of this story is to separate Gradle core public API from the core plugins public API. As a result none of the Gradle core plugins will be included in `gradlePublicApi()`. Users will have to declare a new dependency on individual plugin APIs. This change will allow for more fine-grained control on dependencies needed for a plugin project. In turn this information can be used for documentation purposes for consumers of plugins.

This is a breaking changes for `gradlePublicApi()` but should not have a huge impact on plugin developers. Upon upgrading to the Gradle version introducing the change, user will have to declare specific plugin APIs instead of just `gradlePublicApi()`. The produced plugin artifacts will not change.

### User visible changes

Introduce a new method for exposing the Gradle public API:

    public interface DependencyHandler {

        /**
         * Creates a dependency on the public API of the current version of Gradle.
         * The API declared by this dependency <b>does not</b> include the API of Gradle core plugins.
         *
         * @return The dependency.
         */
        Dependency gradlePublicApi();

        /**
         * Creates a dependency on a plugin API.
         * <p>
         * The following options are available:
         * <p>
         * <ul>
         * <li>{@code id}: The identifier of the plugin.</li>
         * </ul>
         *
         * @return The dependency.
         */
        Dependency plugin(Map<String, ?> options);
    }

### Usage example

Declaring a dependency on the Gradle core public API and the public API of the Java and JaCoCo plugin:

    dependencies {
        compile gradlePublicApi()
        compile plugin id: 'java'
        compile plugin id: 'jacoco'
    }

### Implementation

* Remove any plugin-related classes from the dependency `gradlePublicApi()`.
* Introduce a new method for declaring a dependency on the public API of a core plugin.
    * Identify _what_ the public API of a plugin really is.
    * The identifier parameter indicates the plugin which is the same that is used for applying a plugin.
    * The dependency on a plugin does not include the internal API of a plugin.
    * External dependencies of a plugin need to be shaded but included in the JAR.
    * 
* Indicate the breaking change in Javadocs and release notes.
* Add sample project using both Gradle public API and the public API of a core plugin.
* Update user guide, sample projects and guide.

### Test coverage

* A plugin project that doesn't use a core plugin only needs to declare `gradlePublicApi()` to compile properly.
* A plugin project that applies one or many core plugins needs to declare a dependency on the public API of those plugins to compile properly. Compilation fails if the relevant plugin is not applied.
* The dependency on a core plugin does not include the internal API of the plugin.
* `ProjectBuilder` can use use the new methods.

## Open issues

* In the light of modularizing Gradle core plugins should we think about how to resolve external plugins as well? What exactly does that mean for plugin developers of external plugins? Do they publish two artifacts - one containing the public API and another one for the internal API of a plugin? We'd definitely need guidelines for plugin developers. What exactly does this mean to the process and tooling of publishing to the plugin portal?
* If you apply the public API of a plugin does the dependency automatically pull in the a composite plugin e.g. Java plugin  _\<\<applies\>\>_ Java Base Plugin _\<\<applies\>\>_ Base Plugin.
* Do classes of a shaded dependencies have to live in their own shading namespace per plugin or do they all use the same?
