## New and noteworthy

Here are the new features introduced in this Gradle release.

### Performance improvements for native compilation

Gradle needs to know all input properties, input files and output files of a task to perform incremental build checks.  When no input or output files have changed, Gradle can skip executing a task.

For native compilation tasks, Gradle used to consider the contents of all include directories as inputs to the task. This had performance problems when there were many include directories or when the project root directory was used as an include directory.
To speed up up-to-date checks, Gradle now treats the include path as an input property and only considers files included from source files as inputs. From our performance benchmarks, this can have a large positive impact on incremental builds for very large projects.

Due to the way Gradle determines the set of input files, using macros to `#include` files from native source files requires Gradle to fallback to its old mechanism of including all files in all include directories as inputs.

### TestKit dependency decoupled from Gradle core dependencies

The method `DependencyHandler.gradleTestKit()` creates a dependency on the classes of the Gradle TestKit runtime classpath. In previous versions
of Gradle the TestKit dependency also declared transitive dependencies on other Gradle core classes and external libraries that ship with the Gradle distribution. This might lead to
version conflicts between the runtime classpath of the TestKit and user-defined libraries required for functional testing. A typical example for this scenario would be Google Guava.
With this version of Gradle, the Gradle TestKit dependency is represented by a fat and shaded JAR file containing Gradle core classes and classes of all required external dependencies
to avoid polluting the functional test runtime classpath.

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

### Tooling API exposes Java source language level for Eclipse projects

The Tooling API now exposes the Java source language level that should be used for an Eclipse project via the
<a href="javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html#getJavaSourceSettings">`EclipseProject.getJavaSourceSettings()`</a> method.
IDE providers can use this to automatically configure the source language level in Eclipse, so that users no longer need to configure this themselves.

### Incremental Java compilation for sources provided via `File` or `DirectoryTree`

In order to perform incremental Java compilation, Gradle must determine the Class file name from each source file. To do so, Gradle infers the source directory roots based on the values supplied to `JavaCompile.source`.

This release enhances this inference to handle types in addition `SourceDirectorySet`. Both `File` and `DirectoryTree` types are now supported for incremental Java compilation. This means that sources provided via `project.fileTree('source-dir')` can be compiled incrementally.

### Component level dependencies for Java libraries

In most cases it is more natural and convenient to define dependencies on a component rather than on each if its source sets and it is now possible to do so when defining a Java library in the software model.

Example:

    apply plugin: "jvm-component"

    model {
      components {
        main(JvmLibrarySpec) {
          dependencies {
            library "core"
          }
        }

        core(JvmLibrarySpec) {
        }
      }
    }

Dependencies declared this way will apply to all source sets for the component.

### External dependencies for Java libraries

It is now possible to declare dependencies on external modules for a Java library in the software model:

    repositories {
        jcenter()
    }
    
    model {
        components {
            main(JvmLibrarySpec) {
                dependencies {
                    // external module dependency can start with either group or module
                    group 'com.acme' module 'artifact' version '1.0'
                    module 'artifact' group 'com.acme' version '1.0'

                    // shorthand module notation also works, version number is optional
                    module 'com.acme:artifact:1.42'
                }
            }
        }
    }

Module dependencies declared this way will be resolved against the configured repositories as usual. 
External dependencies can be declared for a Java library, Java source set or Java library API specification. 

### Software model changes

Binary names are now scoped to the component they belong to. This means multiple components can have binaries with a given name. For example, several library components
might have a `jar` binary. This allows binaries to have names that reflect their relationship to the component, rather than their absolute location in the software model.

### Support for `LanguageSourceSet` model elements

This release facilitates adding source sets (subtypes of `LanguageSourceSet`) to arbitrary locations in the model space. A `LanguageSourceSet` can be attached to any @Managed type as a property, or used for
the elements of a ModelSet or ModelMap, or as a top level model element in it's own right.

### Managed internal views for binaries and components

Now it is possible to attach a `@Managed` internal view to any `BinarySpec` or `ComponentSpec` type. This allows plugin authors to attach extra properties to already registered binary and component types like `JarBinarySpec`.

Example:

    @Managed
    interface MyJarBinarySpecInternal extends JarBinarySpec {
        String getInternal()
        void setInternal(String internal)
    }

    class CustomPlugin extends RuleSource {
        @BinaryType
        public void register(BinaryTypeBuilder<JarBinarySpec> builder) {
            builder.internalView(MyJarBinarySpecInternal)
        }

        @Mutate
        void mutateInternal(ModelMap<MyJarBinarySpecInternal> binaries) {
            // ...
        }
    }

    apply plugin: "jvm-component"

    model {
        components {
            myComponent(JvmLibrarySpec) {
                binaries.withType(MyJarBinarySpecInternal) { binary ->
                    binary.internal = "..."
                }
            }
        }
    }

Note: `@Managed` internal views registered on unmanaged types (like `JarBinarySpec`) are not yet visible in the top-level `binaries` container, and thus it's impossible to do things like:

    // This won't work:
    model {
        binaries.withType(MyJarBinarySpecInternal) {
            // ...
        }
    }

This feature is available for subtypes of `BinarySpec` and `ComponentSpec`.

### Managed binary and component types

The `BinarySpec` and `ComponentSpec` types can now be extended via `@Managed` subtypes, allowing for declaration of `@Managed` components and binaries without having to provide a default implementation. `LibrarySpec` and `ApplicationSpec` can also be extended in this manner.

Example:

    @Managed
    interface SampleLibrarySpec extends LibrarySpec {
        String getPublicData()
        void setPublicData(String publicData)
    }

    class RegisterComponentRules extends RuleSource {
        @ComponentType
        void register(ComponentTypeBuilder<SampleLibrarySpec> builder) {
        }
    }
    apply plugin: RegisterComponentRules

    model {
        components {
            sampleLib(SampleLibrarySpec) {
                publicData = "public"
            }
        }
    }

### Default implementation for unmanaged base binary and component types

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

### Internal views for unmanaged binary and component types

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

### Model DSL improvements

This release includes a number of improvements to the model DSL, which is the DSL you use to define and configure the software model from a build script.

#### Nested rules in the DSL

The `ModelMap` creation and configuration DSL syntax now defines nested rules, each with its own inputs. 
For example, this means that an element of a `ModelMap` can now be configured using the configuration of a sibling as input:

    model {
        components {
            mylib { ... }
            test {
                // Use `mylib` as input. When this code runs, it has been fully configured and will not change any further
                // Previously, this would have been treated as an input of the `components` rule, resulting in a dependency cycle 
                targetPlatform = $.components.mylib.targetPlatform
            }
        }
    }

And because `tasks` is a `ModelMap`, this means that a task can be configured using another task as input using the same syntax:

    model {
        tasks {
            jar { ... }
            dist(Zip) {
                // Use the `jar` task as input. It has been fully configured and will not change any further
                def jar = $.tasks.jar 
                from jar.output
                into someDir
            }
        }
    }

This is also available for the various methods of `ModelMap`, such as `all` or `withType`:

    model {
        components {
            all {
                // Adds a rule for each component
                ...
            }
            withType(JvmLibrarySpec) {
                // Adds a rule for each JvmLibrarySpec component
                ...
            }
        }
    }

#### Configure the properties of a `@Managed` type

The properties of a `@Managed` type can now be configured using nested configure methods:

    model {
        components {
            mylib {
                sources {
                    // Adds a rule to configure `mylib.sources`
                    ...
                }
                binaries {
                    // Adds a rule to configure `mylib.sources`
                    ...
                }
            }
        }
    }

This is automatically added for any property whose type is `@Managed`, or a `ModelMap<T>` or `ModelSet<T>`. 
Note that for this release, these nested closures do not define a nested rule, and the closure is executed as soon as it is encountered in the containing closure.
This will be improved in the next Gradle release.

See the <a href="userguide/software_model.html#model-dsl">model DSL</a> user guide section for more details and examples.

#### Convenient configuration of scalar properties

The model DSL now supports automatic conversions between various scalar types, making it very easy to use one type for another. In particular, you can use a `String` wherever a scalar type is expected. For example:

    enum FailType {
       FAIL_BUILD,
       WARNING
    }

    @Managed
    interface CoverageConfiguration {
       double getMinClassCoverage()
       void setMinClassCoverage(double minCoverage)

       double getMinPackageCoverage()
       void setMinPackageCoverage(double minCoverage)

       FailType getFailType()
       void setFailType(FailType failType)

       File getReportTemplateDir()
       void setReportTemplateDir(File templateDir)
    }

    model {
        coverage {
           minClassCoverage = '0.7' // can use a `String` where a `double` was expected
           minPackageCoverage = 1L // can use a `long` where a `double` was expected
           failType = 'WARNING' // can use a `String` instead of an `Enum`
           templateReportDir = 'src/templates/coverage' // creates a `File` which path is relative to the current project directory
        }
    }

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

### Changes to TestKit's runtime classpath

- External dependencies e.g. Google Guava brought in by Gradle core libraries when using the TestKit runtime classpath are no longer usable in functional test code. Any external dependency required by the test code needs to be declared for the test classpath.

### Changes to model rules DSL

- Properties and methods from owner closures are no longer visible.

### Changes to incubating software model

- All rule methods on `RuleSource` must not be private, and all other methods must be private.
- `BinarySpec.name` should no longer be considered a unique identifier for the binary within a project.
- The name for the 'build' task for a binary is now qualified with the name of its component. For example, `jar` in `mylib` will have a build task called `mylibJar`
- The name for the compile tasks for a binary is now qualified with the name of its component.
- The top-level `binaries` container is now a `ModelMap` instead of a `DomainObjectContainer`. It is still accessible as `BinaryContainer`.
- `ComponentSpec.sources` and `BinarySpec.sources` now have true `ModelMap` semantics. Elements are created and configured on demand, and appear in the model report.
- It is no longer possible to configure `BinarySpec.sources` from the top-level `binaries` container: this functionality will be re-added in a subsequent release.
- `FunctionalSourceSet` is now a subtype of `ModelMap`, and no longer extends `Named`
- The implementation object of a `ComponentSpec`, `BinarySpec` or `LanguageSourceSet`, if defined, is no longer visible. These elements can only be accessed using their public types or internal view types.
- The `DependentSpec` API is now polymorphic. For dependencies declared by project and library, use `ProjectDependencySpec`.

### Changes to incubating Java Software Model

- JVM libraries have a binary called `jar` rather than one qualified with the library name.
- When building a JVM library with multiple variants, the task and output directory names have changed. The library name is now first.
- The name of the task to build an API jar for a `JarBinarySpec` has been changed: what was previously `createMyLibApiJar` is now simply `myLibApiJar`.

### Changes to incubating Native Software Model

- Task names have changed for components with multiple variants. The library or executable name is now first.
- `org.gradle.language.PreprocessingTool` has moved to `org.gradle.nativeplatform.PreprocessingTool`
- Output directory names have changed.

### Native header files as inputs to compile task

Previously, Gradle considered all files in all include path directories as inputs to a compile task. This had performance problems and could cause tasks to be out of date when they should not be. This has been fixed but may cause some subtle differences to the way changes are detected for compilation tasks.

Gradle now considers a compile task to be out-of-date (and require full recompilation) when the include path is changed. In older releases, Gradle would only recompile source files if the resolved set of headers changed.  This means you could reorder the include path and not necessarily see any recompiled files.

Gradle only checks header files that are included from source files during compilation, except when an included header file cannot be found because it is included via a macro. When a header file cannot be resolved during task execution, a fallback mechanism is used to follow the old behavior--all files are added as inputs to the compilation task from all include directories. Gradle emits a warning at `--info` level if this fallback mechanism is used.

Gradle no longer detects changes where a new header file earlier in the include path that should replace an existing header file.  If a compilation task has an include path of `[ first/, second/ ]` and a source file includes `header.h` from `second/`, if a new file called `header.h` is added to `first/`, Gradle will not detect a change that requires recompilation of the source file.

The recommended way for dealing with the ambiguity of which `header.h` should be included is to namespace your header files.  Source files should include `second/header.h` or `first/header.h` with the appropriate include path.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Ethan Hall](https://github.com/ethankhall) - Addition of new `buildEnvironment` task.
* [Sebastian Schuberth](https://github.com/sschuberth) - Checkstyle HTML report.
* [Jeffry Gaston](https://github.com/mathjeff) - Debug message improvement.
* [Chun Yang](https://github.com/chunyang) - Play resources now properly maintain directory hierarchy.
* [Alexander Shoykhet](https://github.com/ashoykh) - Performance improvement for executing finalizer tasks with many dependencies.
* [Peter Ledbrook](https://github.com/pledbrook) - User Guide improvements.

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
