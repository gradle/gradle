## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Software model changes

TBD - Binary names are now scoped to the component they belong to. This means multiple components can have binaries with a given name. For example, several library components
might have a `jar` binary. This allows binaries to have names that reflect their relationship to the component, rather than their absolute location in the software model.

#### Default implementation for unmanaged base binary and component types

It is now possible to declare a default implementation for a base component or a binary type, and extend it via further managed subtypes.

    interface MyBaseBinarySpec extends BinarySpec {}

    class MyBaseBinarySpecImpl extends BaseBinarySpec implements MyBaseBinarySpec {}

    class BasePlugin extends RuleSource {
        @ComponentType
        public void registerMyBaseBinarySpec(ComponentTypeBuilder<MyBaseBinarySpec> builder) {
            builder.defaultImplementation(MyBaseBinarySpecImpl.class);
        }
    }

    @Managed
    interface MyCustomBinarySpec extends BaseBinarySpec {
        // Add some further managed properties
    }

    class CustomPlugin extends RuleSource {
        @ComponentType
        public void registerMyCustomBinarySpec(ComponentTypeBuilder<MyCustomBinarySpec> builder) {
            // No default implementation required
        }
    }

This functionality is available for unmanaged types extending `ComponentSpec` and `BinarySpec`.

#### Internal views for unmanaged binary and component types

The goal of the new internal views feature is for plugin authors to be able to draw a clear line between public and internal APIs of their plugins regarding model elements.
By declaring some functionality in internal views (as opposed to exposing it on a public type), the plugin author can let users know that the given functionality is intended
for the plugin's internal bookkeeping, and should not be considered part of the public API of the plugin.

Internal views must be interfaces, but they don't need to extend the public type they are registered for.

**Example:** A plugin could introduce a new binary type like this:

    /**
     * Documented public type exposed by the plugin
     */
    interface MyBinarySpec extends BinarySpec {
        // Functionality exposed to the public
    }

    // Undocumented internal type used by the plugin itself only
    interface MyBinarySpecInternal extends MyBinarySpec {
        String getInternalData();
        void setInternalData(String internalData);
    }

    class MyBinarySpecImpl implements MyBinarySpecInternal {
        private String internalData;
        String getInternalData() { return internalData; }
        void setInternalData(String internalData) { this.internalData = internalData; }
    }

    class MyBinarySpecPlugin extends RuleSource {
        @BinaryType
        public void registerMyBinarySpec(BinaryTypeBuilder<MyBinarySpec> builder) {
            builder.defaultImplementation(MyBinarySpecImpl.class);
            builder.internalView(MyBinarySpecInternal.class);
        }
    }

With this setup the plugin can expose `MyBinarySpec` to the user as the public API, while it can attach some additional information to each of those binaries internally.

Internal views registered for an unmanaged public type must be unmanaged themselves, and the default implementation of the public type must implement the internal view
(as `MyBinarySpecImpl` implements `MyBinarySpecInternal` in the example above).

It is also possible to attach internal views to `@Managed` types as well:

    @Managed
    interface MyManagedBinarySpec extends MyBinarySpec {}

    @Managed
    interface MyManagedBinarySpecInternal extends MyManagedBinarySpec {}

    class MyManagedBinarySpecPlugin extends RuleSource {
        @BinaryType
        public void registerMyManagedBinarySpec(BinaryTypeBuilder<MyManagedBinarySpec> builder) {
            builder.internalView(MyManagedBinarySpecInternal.class);
        }
    }

Internal views registered for a `@Managed` public type must themselves be `@Managed`.

This functionality is available for types extending `ComponentSpec` and `BinarySpec`.

### Visualising a project's build script dependencies

The new `buildEnvironment` task can be used to visualise the project's `buildscript` dependencies.
This task is implicitly available for all projects, much like the existing `dependencies` task.

The `buildEnvironment` task can be used to understand how the declared dependencies of project's build script actually resolve,
including transitive dependencies.

The feature was kindly contributed by [Ethan Hall](https://github.com/ethankhall).

### Checkstyle HTML report

The [`Checkstyle` task](dsl/org.gradle.api.plugins.quality.Checkstyle.html) now produces a HTML report on failure in addition to the existing XML report.
The, more human friendly, HTML report is now advertised instead of the XML report when it is available.

This feature was kindly contributed by [Sebastian Schuberth](https://github.com/sschuberth).

### Model rules improvements

#### Support for `LanguageSourceSet` model elements

This release facilitates adding source sets (subtypes of `LanguageSourceSet`) to arbitrary locations in the model space. A `LanguageSourceSet` can be attached to any @Managed type as a property, or used for
the elements of a ModelSet or ModelMap, or as a top level model element in it's own right.

### Support for external dependencies in the 'jvm-components' plugin

It is now possible to reference external dependencies when building a `JvmLibrary` using the `jvm-component` plugin.

TODO: Expand this and provide a DSL example.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to incubating software model

- `BinarySpec.name` should no longer be considered a unique identifier for the binary within a project.
- The name for the 'build' task for a binary is now qualified with the name of its component. For example, `jar` in `mylib` will have a build task called 'mylibJar'
- The name for the compile tasks for a binary is now qualified with the name of its component.
- JVM libraries have a binary called `jar` rather than one qualified with the library name.
- When building a JVM library with multiple variants, the task and output directory names have changed. The library name is now first.
- The top-level `binaries` container is now a `ModelMap` instead of a `DomainObjectContainer`. It is still accessible as `BinaryContainer`.

### Changes to incubating native software model

- Task names have changed for components with multiple variants. The library or executable name is now first.
- `NativeExecutableBinarySpec.executableFile` is now reachable via `NativeExecutableBinarySpec.executable.file`.
- `NativeTestSuiteBinarySpec.executableFile` is now reachable via `NativeTestSuiteBinarySpec.executable.file`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Ethan Hall](https://github.com/ethankhall) - Addition of new `buildEnvironment` task.
* [Sebastian Schuberth](https://github.com/sschuberth) - Checkstyle HTML report.

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
