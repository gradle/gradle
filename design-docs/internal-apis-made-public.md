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

### Option 1

#### User visible changes

* The implementation will only apply to the current model and not the software model.
* Users can use convention mapping out-of-the-box for any task extends `DefaultTask`.
* Core task types support convention mapping as before.

#### Implementation

* Move the following interfaces into the public package `org.gradle.api`:
    * `org.gradle.api.internal.ConventionMapping`
    * `org.gradle.api.internal.HasConvention`
    * `org.gradle.api.internal.MappedProperty`
    * `org.gradle.api.internal.IConventionAware`
        * Consider renaming the interface to `ConventionAware` to make it more expressive.
        * Use the new class in other places of use e.g. `org.gradle.api.internal.plugins.DslObject`.
* Any implementation class for the interfaces above stay in the package `org.gradle.api.internal`.
* Introduce convention mapping concept to `DefaultTask`.
    * Remove annotation `@NoConventionMapping` from `DefaultTask`.
    * Class implements the interface `IConventionAware`.
    * Merge implementation of `ConventionTask` into `AbstractTask`. Do not introduce the method `ConventionTask.conventionMapping` that takes a Closure as parameter. Groovy can [coerce the `Closure` parameter into a `Callable` type](http://docs.groovy-lang.org/next/html/documentation/core-semantics.html#closure-coercion).
    * Use `DefaultTask` instead of `ConventionTask` in core tasks.
    * Remove the super class `ConventionTask` from code base.
    * Getter methods continue to be generated with the [ASM library](http://asm.ow2.org/).
* Improvements to current functionality.
    * Remove the method `ConventionTask.conventionMapping` that takes a `Closure` as parameter.
    * Render a warning or fail the build if user tries to access a property (by variable and getter method) that was set via convention mapping. In practice users forget to use the getter method and reference the field itself resulting in a `null` value. The behavior would have to be more user-friendly.
    * Make convention mapping value immutable when the task starts executing.
* Mark newly introduced public API with `@Incubating` and `@since` annotations.
* Properly document public API with Javadocs.
* Enhance user guide sections covering plugins and tasks and cover appropriate information.
    * Explain the concept of convention mapping and the concrete use case it solves.
    * Clearly state the gotchas when using concept.
    * Demonstrate how to use the concept with Java and Groovy.
* Add one or two samples to the Gradle core distribution that use convention mapping.
* Add convention mapping to promoted features and breaking changes in release notes.
* Create follow-up issue for a guide covering convention mapping in the context of plugin development.

#### Test coverage

* Existing test coverage for core tasks work as before.
* User-defined ad-hoc tasks and task types can use convention mapping out-of-the-box.
* Task inputs and outputs properly participate in `UP-TO-DATE` checking. Existing test coverage for this functionality passes.
* Convention mapping can be used in real-world scenarios e.g. mapping extension property values to task properties.
* Additional improvements to the existing convention mapping functionality is properly covered in happy/sad path scenarios.
* Convention mapping can be used in a Java and Groovy implementation of a task.

#### Open issues

* The existing, internal convention mapping implementation needs to be kept for another major version of Gradle to avoid breaking existing plugins. The old and new implementation convention mapping might conflict during the transition phase e.g. when using ASM.

### Option 2

#### User visible changes

Introduce a new interface named `Provider` representing the provider for a value:

    public interface Provider<T> extends Buildable {
        /**
         * Returns the value defined for the provider.
         *
         * @return Value
         */
        T get();
    
        /**
         * Sets the tasks which build the files of this provider.
         *
         * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
         * @return this
         */
        Provider<T> builtBy(Object... tasks);
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
    * Implements the `Buildable` interface.
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