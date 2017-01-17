# Public API equivalents for highly-used internal APIs

## High-level goals

* Empowering the community by giving them public, well-supported, and forward-compatible APIs.
* Dogfooding of public APIs in Gradle core plugins. Work towards the goal of avoid the use of any internal API in core plugins.
* Establishing good examples for plugin development.

## Technical details

* Most of the discussed internal API already exist in Gradle core in one form or another.
* Any of public APIs planned to be exposed will be implemented with Java.

## Story: Identify and promote first-class support for mapping between extension and task properties

Plugin developers are currently using the internal API for convention mapping to map extension values to task properties. Convention mapping is the only effective method to avoid evaluation order issues for this use case.

We need to identify a first-class, public API solution for the problem. The software model was supposed to solve the issue. As we won't promote the software model for the JVM domain, we'll need a different solution.

Related issue: https://github.com/gradle/gradle/issues/726

### User visible changes

* The implementation will only apply to the current model and not the software model.
* Users can use convention mapping out-of-the-box for any task extends `DefaultTask`.
* Core task types support convention mapping as before.

### Implementation

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
    * Render a warning or fail the build if user tries to access a property (by variable and getter method) that was set via convention mapping.
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

### Test coverage

* Existing test coverage for core tasks work as before.
* User-defined ad-hoc tasks and task types can use convention mapping out-of-the-box.
* Task inputs and outputs properly participate in `UP-TO-DATE` checking. Existing test coverage for this functionality passes.
* Convention mapping can be used in real-world scenarios e.g. mapping extension property values to task properties.
* Additional improvements to the existing convention mapping functionality is properly covered in happy/sad path scenarios.
* Convention mapping can be used in a Java and Groovy implementation of a task.

### Open issues

* Even though convention mapping is an internal concept right now, a lot of plugins are using it. This is a major breaking change. Should we think about keeping `ConventionTask` for while?