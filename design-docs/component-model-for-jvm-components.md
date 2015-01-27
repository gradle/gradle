
Currently, the JVM language plugins assume that a given set of source files is assembled into a single
output. For example, the `main` Java source is compiled and assembled into a JAR file. However, this is not
always a reasonable assumption. Here are some examples:

* When building for multiple runtimes, such as Scala 2.10 and Scala 2.11.
* When building multiple variants composed from various source sets, such as an Android application.
* When packaging the output in various different ways, such as in a JAR and a fat JAR.

By making this assumption, the language plugins force the build author to implement these cases in ways that
are not understood by other plugins that extend the JVM language plugins, such as the code quality and IDE
plugins.

This problem is also evident in non-JVM languages such as C++, where a given source file may be compiled and
linked into more than one binaries.

This spec describes some work to allow plugins to define the kinds of JVM components that they produce and consume,
and to allow plugins to define their own custom JVM based components.

## Use cases

### Multiple build types for Android applications

An Android application is assembled in to multiple _build types_, such as 'debug' or 'release'.

### Build a library for multiple Scala or Groovy runtimes

A library is compiled and published for multiple Scala or Groovy runtimes, or for multiple JVM runtimes.

### Build different variants of an application

An application is tailored for various purposes, with each purpose represented as a separate variant. For
each variant, some common source files and some variant specific source files are jointly compiled to
produce the application.

For example, when building against the Java 5 APIs do not include the Java 6 or Java 7 specific source files.

### Compose a library from source files compiled in different ways

For example, some source files are compiled using the aspectj compiler and some source files are
compiled using the javac compiler. The resulting class files are assembled into the library.

### Implement a library using multiple languages

A library is implemented using a mix of Java, Scala and Groovy and these source files are jointly compiled
to produce the library.

### Package a library in multiple ways

A library may be packaged as a classes directory, or a set of directories, or a single jar file, or a
far jar, or an API jar and an implementation jar.

### A note on terminology

There is currently a disconnect in the terminology used for the dependency management component model, and that used
for the component model provided by the native plugins.

The dependency management model uses the term `component instance` or `component` to refer to what is known as a `binary`
in the native model. A `component` in the native model doesn't really have a corresponding concept in the dependency
management model (a `module` is the closest we have, and this is not the same thing).

Part of the work for this spec is to unify the terminology. This is yet to be defined.

For now, this spec uses the terminology from the native component model, using `binary` to refer to what is also
known as a `component instance` or `variant`.

# Features

## Feature: Build author creates a JVM library with Java sources

### Story: Build author creates JVM library jar from Java sources (DONE)

When a JVM library is defined with Java language support, then binary is built from conventional source set locations:

- Has a single Java source set hardcoded to `src/myLib/java`
- Has a single resources source set hardcoded to `src/myLib/resources`

#### DSL

Java library using conventional source locations

    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib
        }
    }


Combining jvm-java and native (multi-lang) libraries in single project

    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    apply plugin: 'native-component'
    apply plugin: 'cpp-lang'
    apply plugin: 'c-lang'

    jvm {
        libraries {
            myJvmLib
        }
    }
    nativeRuntime {
        libraries {
            myNativeLib
        }
    }

#### Implementation plan

- Replace the current `java-lang` plugin with a simpler one that does not know about legacy conventions
- For each `JvmLibrary`:
    - Adds a single `ResourceSet` for `src/${component}/resources`
    - Adds a single `JavaSourceSet` for `src/${component}/java`
- Each created `JvmLibraryBinary` has the source sets of its `JvmLibrary`
- Create a `ProcessResources` task for each `ResourceSet` for a `JvmLibraryBinary`
    - copy resources to `build/classes/${binaryName}`
- Create a `CompileJava` task for each `JavaSourceSet` for a `JvmLibraryBinary`
    - compile classes to `build/classes/${binaryName}`
- Create a `Jar` task for each `JvmLibraryBinary`
    - produce jar file at `build/${binaryType}/${binaryName}/${componentName}.jar`
- Rejig the native language plugins so that '*-lang' + 'native-components' is sufficient to apply language support
    - Existing 'cpp', 'c', etc plugins will simply apply '*-lang' and 'native-components'

#### Test cases

- Define and build the jar for a java library (assert jar contents for each case)
    - With sources but no resources
    - With resources but no sources
    - With both sources and resources
- Creates empty jar with no sources or resources (a later story will make this a failure)
- Compiled sources and resources are available in a common directory
- Reports failure to compile source
- Incremental build for java library
    - Tasks skipped when all sources up-to-date
    - Class file is removed when source is removed
    - Copied resource is removed when resource is removed
- Can build native and JVM libraries in the same project
    - `gradle assemble` builds each native library and each jvm library
- Can combine old and new JVM plugins in the same project
    - `gradle assemble` builds both jars

## Feature: Plugin defines a custom component model

This features allows the development of a custom plugin that can contribute Library, Binary and Task instances to the language domain.

Development of this feature depends on the first 2 stories from the `unified-configuration-and-task-model` spec, namely:

- Story: Plugin declares a top level model to make available
- Story: Plugin configures tasks using model as input

### Story: plugin declares its own library type (DONE)

Define a sample plugin that declares a custom library type:
    
    interface SampleLibrary extends LibrarySpec { ... }
    class DefaultSampleLibrary extends DefaultLibrarySpec implements SampleLibrary { ... }

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        @ComponentModel(SampleLibrary.class, DefaultSampleLibrary.class)
        static class ComponentModel {
            @Model("mySample")
            SampleExtension createSampleExtension() {
                ...
            }

            @Mutate
            void createSampleLibraryComponents(CollectionBuilder<SampleLibrary> sampleLibraries, SampleExtension sampleExtension) {
                for (String libraryName : sampleExtension.getLibraryNames()) {
                    sampleLibraries.create(libraryName)
                }
            }
        }
    }

Libraries are then visible in libraries and components containers:

    // Library is visible in component container
    assert projectComponents.withType(SampleLibrary).size() == 2

A custom library type:
- Extends or implements the public base `LibrarySpec` type.
- Has no dependencies.
- Has no sources.
- Produces no artifacts.

A custom library implementation:
- Implements the custom library type
- Extends `DefaultLibrarySpec`
- Has a no-arg constructor

#### Implementation Plan

- ~~Rename the existing JVM and C++ model classes from `Project*` to `*Spec`.~~
- ~~Introduce a `LibrarySpec` interface that both `NativeLibrarySpec` and `JvmLibrarySpec` extend.~~
- ~~Add a default implementation of `LibrarySpec` named `DefaultLibrarySpec`. All custom library implementations extend this.~~
- ~~Replace `NamedProjectComponentIdentifier` with `ComponentSpecIdentifier` everywhere.~~
- ~~Add a new Sample for a custom plugin that uses model rules to add `SampleLibrary` instances to the `ComponentSpecContainer`~~
    - Should apply the `ComponentModelBasePlugin`
    - At the end of the story the sample will be adapted to use the new mechanism introduced
    - Add an integration test for the sample
- ~~Add a new incubating annotation to the 'language-base' project: `ComponentModel` with parameters defining the Library type and implementation classes~~
- ~~Add functionality to the 'language-base' plugin that registers a hook that inspects every applied plugin for a nested (static) class with the @ComponentModel annotation~~
    - Implement by making an `Action<? super PluginApplication>` available to the construction of `DefaultPluginContainer`, via `PluginServiceRegistry`.
- ~~When a plugin is applied that has a nested class with the `@ComponentModel(SampleLibrary)` annotation:~~
    - Automatically apply the `ComponentModelBasePlugin` before the plugin is applied
    - Register a factory with the `ComponentSpecContainer` for creating `SampleLibrary` instances with the supplied implementation
        - The factory implementation should generate a `ComponentSpecIdentifier` with the supplied name to instantiate the component
    - Add a `ModelCreator` to the `ModelRegistry` that can present a `CollectionBuilder<SampleLibrary>` view of the `ComponentSpecContainer`.
- ~~Update `DefaultLibrarySpec` so that it has a public no-arg constructor~~
    - ~~Inject the ComponentSpecIdentifier into the constructed library using a ThreadLocal and static setter method (see AbstractTask).~~

#### Test cases

- ~~Can register a component model with @Library without any rules for creating components (does not create components)~~
- ~~Can create library instances via `CollectionBuilder<LibrarySpec>` with a plugin that:~~
    - ~~Already has the `ComponentModelBasePlugin` applied~~
    - ~~Has a single nested class with both `@ComponentModel` and `@RuleSource` annotations~~
    - ~~Has separate nested classes with `@ComponentModel` and `@RuleSource` annotations~~
- ~~Rule for adding library instances can be in a separate plugin to the plugin declaring the component model~~
- ~~Can define and create multiple component types in the same plugin with multiple `@ComponentModel` annotations~~
- ~~Friendly error message when supplied library implementation:~~
    - ~~Does not have a public no-arg constructor~~
    - ~~Does not implement library type~~
    - ~~Does not extend `DefaultLibrarySpec`~~
- ~~Friendly error message when attempting to register the same library type with different implementations~~
- ~~Custom libraries show up in components report~~

### Story: Plugin uses rule to declare custom component type (DONE)

To avoid a future explosion of nested annotations, this story switches the mechanism for declaring a custom library type to use an
annotated method, rather than a type annotation.

This story also expands on the previous one by expanding the functionality to include any component type, not just `LibrarySpec` subtypes.

When a rule method with the `@ComponentType` annotation is found, the method is inspected to determine the type based on the generic
type of the `ComponentTypeBuilder` input. The ComponentTypeBuilder implementation will then register a rule that will
register a factory with the `ComponentSpecContainer` when the default implementation is set.

#### User visible changes

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class Rules {
            @ComponentType
            void defineType(ComponentTypeBuilder<SampleLibrary> builder) {
                builder.setDefaultImplementation(DefaultSampleLibrary)
            }
        }
    }

#### Test cases

- ~~Can register type that is not a subtype of `LibrarySpec`~~
- ~~Fails if a method with @ComponentType does not have a single parameter of type `ComponentTypeBuilder`~~
- ~~Fails if a method with @ComponentType has a return type~~
- ~~Fails if `setDefaultImplementation` is called multiple times~~
- ~~Empty @ComponentType method implementation is ok: no factory registered~~

### Story: Plugin declares the binary types and default implementations for a custom component (DONE)

This story provides a simple way for developers to specify the binary types that are relevant for a particular custom component.

#### User visible changes

Add a binary type to the sample plugin:

    // Define some binary types and reference them from the component
    interface SampleBinary extends BinarySpec {}
    interface OtherSampleBinary extends SampleBinary {}

    interface SampleComponent extends ComponentSpec {
    }

    // Define implementations for the binary types - these will go away at some point
    class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
    class DefaultOtherSampleBinary extends BaseBinarySpec implements OtherSampleBinary {}

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class ComponentModel {
            @BinaryType
            void defineBinaryType(BinaryTypeBuilder<SampleBinary> builder) {
                builder.setDefaultImplementation(DefaultSampleBinary)
            }

            @BinaryType
            void defineBinarySubType(BinaryTypeBuilder<OtherSampleBinary> builder) {
                builder.setDefaultImplementation(DefaultOtherSampleBinary)
            }
        }
    }

A custom binary type:
- Extends public `BinarySpec` type.
- Has no sources.
- Is buildable.
- Has some lifecycle task to build its outputs.

A custom binary implementation:
- Implements the custom binary type.
- Extends `BaseBinarySpec`.
- Has a public no-arg constructor.

#### Implementation Plan

- Add a `BaseBinarySpec` implementation or `BinarySpec` that has a no-arg constructor.
- Introduce a `@BinaryType` rule type for registering a binary type and implementation
    - Assert that the implementation class extends `BaseBinarySpec`, has a no-arg constructor and implements the type.
    - Register a factory for the type with the `BinaryContainer`.
- Generify DefaultSampleLibrary so that the `getBinaries()` method can return a set of binary subtypes.
- Introduce `LibraryBinarySpec` to represent binaries for produced from a `LibrarySpec`.
    - Similarly, add `ApplicationBinarySpec`.

#### Test cases

- A rule that mutates `BinaryContainer` can create instances of registered type
- Friendly error message when annotated `@BinaryType` rule method:
    - Does not have a single parameter of type BinaryTypeBuilder
    - Parameter does not have a generic type
    - Has a non-void return value
- Friendly error message when supplied binary type:
    - Does not extend `BinarySpec`
    - Equals `BinarySpec` (must be a subtype)
- Friendly error message when supplied binary implementation:
    - Does not have a public no-arg constructor
    - Does not implement binary type
    - Does not extend `BaseBinarySpec`
- Friendly error message when attempting to register the same binary type with different implementations

### Story: Plugin defines binaries for each custom component

This story introduces a mechanism by this a developer can define the binaries that should be built for a custom library.

These binaries are not visible to the build script author for configuration.

#### User visible changes

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class ComponentModel {
            @ComponentBinaries
            void createBinariesForSampleLibrary(CollectionBuilder<SampleBinary> binaries, SampleLibrary library) {
                binaries.create("${library.name}Binary")
                binaries.create("${library.name}OtherBinary", OtherSampleBinary)
            }

            @ComponentBinaries
            void createBinariesForSampleLibrary(CollectionBuilder<OtherSampleBinary> binaries, SampleLibrary library) {
                binaries.create("${library.name}OtherBinary2")
            }
        }
    }

Binaries are now visible in the appropriate containers:

    // Binaries are visible in the appropriate containers
    // (assume 2 libraries & 2 binaries per library)
    assert binaries.withType(SampleBinary).size() == 4
    assert binaries.withType(OtherSampleBinary).size() == 2
    projectComponents.withType(SampleLibrary).each { assert binaries.size() == 2 }

Running `gradle assemble` will execute lifecycle task for each binary.

#### Implementation Plan

- Introduce a `@ComponentBinaries` rule type
    - Subject must be of type `CollectionBuilder` with a generic type parameter extending `BinarySpec`
    - Exactly one input must be a type extending `ComponentSpec`
    - The binary type declared in the subject must be assignable to one of the binary types declared on the component input type
    - Other inputs are permitted
- For each `@ComponentBinaries` rule, register a mutate rule that iterates over all components conforming to the requested component type
    - Any created binaries should be added to the set of binaries for the component, as well as being added to the `BinaryContainer`
- For each created binary, create and register a lifecycle task with the same name as the binary
- Update the 'custom components' sample to demonstrate the new mechanism.

#### Test cases

- ~~Can create binaries via rules that declare these as input:~~
   - ~~`CollectionBuilder<BinarySpec>`~~
   - ~~`CollectionBuilder<SampleBinary>`~~
   - ~~`CollectionBuilder<SampleBinarySubType>`~~
- ~~Can execute lifecycle task of each created binary, individually and via 'assemble' task~~
- ~~Can access lifecycle task of binary via BinarySpec.buildTask~~
- Friendly error message when annotated binary rule method:
    - ~~Does not have a single parameter of type CollectionBuilder~~
    - ~~Parameter does not have a generic type~~
    - ~~Has a non-void return value~~

### Story: Plugin defines tasks from binaries

Add a rule to the sample plugin:

    class MySamplePlugin {
        ...

        @BinaryTasks
        void createTasksForSampleBinary(CollectionBuilder<Task> tasks, SampleBinary binary) {
            ... Add tasks that create this binary. Create additional tasks where signing is required.
        }
    }

Running `gradle assemble` will execute tasks for each library binary.

#### Implementation Plan

- Introduce a `@BinaryTasks` rule type:
    - Subject must be of type `CollectionBuilder<Tasks>`
    - Exactly one input must be a type extending `LibraryBinarySpec`
    - Other inputs are permitted
- For each `@BinaryTasks` rule, register a mutate rule that iterates over all binaries conforming to the requested binary type
    - Any created tasks should be added to the binary.tasks for this binary, and the binary will be `builtBy` those tasks
- The task-creation rule will be executed for each binary when closing the TaskContainer.
- Document in the user guide how to define a component, binaries and tasks for a custom model. Include some samples.

#### Test cases

- Friendly error message when annotated binary rule method:
    - ~~Does not have any parameters
    - ~~CollectionBuilder Parameter does not have a generic type~~
    - ~~Has a non-void return value~~
    - Has no Binary parameter

- Can create tasks via BinaryTask rules that declare these as input:
   - `CollectionBuilder<Task>`, SampleBinary

#### Open issues

- General mechanism to register a model collection and have rules that apply to each element of that collection.

## Feature: Build author uses model DSL for all component model configuration

### Story: Build author defines top level source set using model DSL

    model {
        sources {
            mysource {
                java(JavaSourceSet) { ... }
            }
        }
    }

Conventional source directory locations are used for source sets declared this way.

#### Test cases

- Source sets are visible in component report with correct source directories

### Story: Build author defines top level component using model DSL

    model {
        components {
            mylib(JvmLibrarySpec) { ... }
        }
    }

#### Test cases

- `gradle assemble` builds the jar for this component. Could reuse an existing test for this.

### Story: Configure the source sets of a component in the component definition

This story moves definition and configuration of the source sets for a component to live with the other component configuration.

1. Allow a component's source sets to be defined as part of the component definition:
    - Add `ComponentSpec.getSources()` which returns a `FunctionalSourceSet`.
    - Add a `ComponentSpec.sources(Action<? super FunctionalSourceSet>)` method.
    - When a component is created, create and attach the source set.
    - Add the component's `FunctionalSourceSet` to the `sources` container.
    - Attach source sets for a given language only to those `FunctionalSourceSet` source sets instances owned by a component.
1. Review samples to make use of this.
1. Simplify the naming scheme used for the transform tasks and output directories for the source sets owned by the component.

#### Example DSL

    model
        components {
            mylib(NativeLibrarySpec) {
                sources {
                    c {
                        lib libraries.otherlib
                    }
                    cpp {
                        include '**/*.CC'
                    }
                }
            }
        }
    }

#### Test cases

#### Open issues

- Merge `ProjectSourceSet` and `FunctionalSourceSet` into a more general `CompositeSourceSet`.
- Flatten out all source sets into `project.sources`. Would need to use something other than a named domain object container.
- Change `ComponentModelBasePlugin.createLanguageSourceSets` to a model rule
    - This means that source sets will not be created eagerly, which means that access to sources {} will need to be in a model block, or via ComponentSpec.sources()
    - In order for this to work, we need to be able to reference other model elements in a DSL model rule

### Story: Configure component model and source sets exclusively using model DSL

Remove the `jvm`, `nativeRuntime`, `binaries` and `sources` and other extensions registered by the Jvm and native plugins.

### Story: Build author configures all language source sets using model DSL

Add a container that contains all language source sets. When a functional source set is added to `sources` container or
defined for a component, then all language source sets for that functional source set should be visible:

    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    model {
        sources {
            custom {
                java(JavaLanguageSourceSet)
            }
        }
        components {
            myLib(JvmExecutableSpec)
        }
        // needs a better name or DSL
        allSource {
            // sources.custom.java should be visible here
            // components.myLib.sources.java should be visible here
        }
    }

Change the component report to use this new language source set container.

### Story: Build author configures all components using model DSL

    apply plugin: 'native-component'
    apply plugin: 'cunit'

    model {
        components {
            myexe(NativeExecutableSpec)
        }
        // needs a better name or DSL
        allComponents {
            // components.myexe is visible here
            // cunit test suite is visible here
        }
    }

Change the component report to use this new component container.

#### Open issues

- Add a testSuites container
- Add a way to navigate from component to test suite
- Allow a test suite to be added as a top level component

### Story: Build author configures all binaries using model DSL

Adds capability for an object to appear in multiple locations in the model. Use the model DSL to configure for all components:

- Binaries with type
- All binaries

This code should run between the configuration rules defined by the plugin, and the rules associated with each component. This way, a build script
can provide a convention that applies to all components, and the exceptions can be associated with the individual components.

#### Open issues

- This code should only run when the particular binary needs to be closed.
- Add the equivalent for source sets.
- Allow top level binaries to be declared.

### Story: Build author uses model DSL to configure binaries for a component

Adds capability to configure a child object after the parent object has been configured. Use the model DSL to configure
for a particular component:

- Binary by name
- Binaries with type
- All binaries

This code should run after the rules that defines the binaries for the component and the configuration rules defined by
plugin.

#### Open issues

- This code should only run when the particular component needs to be closed.
- Also needs to work for binaries declared for particular roles
- Add the equivalent for source sets
- Add the concept of 'convention' that rules can apply. The configure rules declared in the model DSL should run after the convention
rules have been applied to each binary.

## Feature: Plugin declares the roles of component model elements

### Story: Plugin statically declares roles for the binaries of a component

    interface JarBinarySpec extends BinarySpec { }

    interface SampleLibrary extends LibrarySpec {
        @Binaries
        DomainObjectSet<? extends JarBinarySpec> getJars();

        @Binary
        JarBinarySpec getApiJar();

        @Binary
        JarBinarySpec getImplJar();
    }

Given this, Gradle ensures that the values from each of these properties are visible as the outputs of the component, and in the binaries container:

    def lib = components.sampleLib
    assert lib.binaries == lib.jars + [lib.apiJar, lib.implJar] as Set
    assert binaries.containsAll(lib.binaries)

Running `gradle assemble` should build all of these binaries.

#### Open issues

- Rename 'binaries' to more general 'outputs'? This allows binaries to be used as inputs.
- Need to provide implementations of these values.
- Change JvmLibrarySpec to have `classes` and `jar` properties of the appropriate types, and push classes dir and resource dir down from JvmBinarySpec to
  ClassDirectoryBinarySpec.
- Change NativeLibrarySpec to have `sharedLibs` and `staticLibs` properties of the appropriate types.
- Change NativeExecutableSpec to have `executables` property of the appropriate type.
- Change component report to present this meta-data.

### Story: Plugin statically declares roles for the source sets of a component

    interface HeaderLanguageSourceSet extends LanguageSourceSet { }

    interface SampleLibrary extends LibrarySpec {
        @Source
        HeaderLanguageSourceSet getHeaders();

        @Sources
        DomainObjectSet<CLanguageSet> getCSources();
    }

    def lib = components.sampleLib
    assert lib.sources == lib.cSources + [lib.headers] as Set

#### Open issues

- Rename 'sources' to more general 'inputs'? This allows source sets to be produced as outputs.
- Change NativeComponentSpec to have `api` property and remove HeaderExportingSourceSet.
- Change component report to present this meta-data.

## Feature: Component model improvements

### Story: Validate the input source sets for a binary

1. Fail when an unsupported language is used as input to a binary. eg Can't use a Java source set as input to a native binary.
2. Fail when a binary has no inputs.

This story should introduce a general validation step in the model object lifecycle and make use of this.

### Story: Plugin declares transformations to produce a binary from intermediate files

Allow a plugin to declare the transformation tasks from some intermediate file type to the binary output.
For example, class files to Jar binary or object files to a executable binary.

Infrastructure automatically wires up the correct transformation rule for each binary.

### Story: Component, Binary and SourceSet names are limited to valid Java identifiers

### Open issues

- `BinarySpec` hierarchy
    - No general way to navigate to the owning component (if any).
    - `BinarySpec` assumes binary is built from source.
    - `NativeBinarySpec` assumes the binary belongs to a component.
    - `StaticLibraryBinarySpec` has no way to read the additional link files.
    - `SharedLibraryBinarySpec` declares separate link and runtime files for binaries that don't have separate files.
    - Compiler tools for `NativeBinarySpec` are not statically typed.
    - No general way to navigate to the component under test for a test suite (if any).
    - `NativeTestSuiteBinarySpec` assumes a single binary under test. In the case of a library, this isn't the case.
    - `JvmBinarySpec` assumes binary is built from intermediate classes and resources.
    - `JarBinarySpec` assumes binary belongs to a library.
    - `ClassDirectoryBinarySpec` assumes binary belongs to a library.
    - Existing JVM and native binary implementations should extend `BaseBinarySpec`
- `ComponentSpec` hierarchy
    - `ComponentSpec` assumes component is built from source.
    - `ComponentSpec` assumes component produces binaries.
    - `TestSuiteSpec` assumes a single component under test. In the case of an integration test, this may not be the case.
    - Binaries of a component are not strongly typed.
    - `TargetedNativeComponent` should have `spec` in its name
    - `TargetedNativeComponent` provides write-only access to targets, and only as strings.
    - Need to be able to specialise the `languages` and `binaries` collections in a subtype of `ComponentSpec`.
- `Component` hierarchy
    - `Component` is in `org.gradle.api.component` package.
    - `PrebuiltLibrary` is actually a prebuilt native library.
- Java lang plugin is called `JavaLanguagePlugin`, other language plugins are called, for example, `CLangPlugin`.
- Java compilation options per binary.
- `LanguageRegistration.applyToBinary()` should be replaced, instead use the output file types for the language and input file types for the binary.
- Use this to handle windows resources:
    - For windows binaries, add window `res` files as a candidate input file type.
    - For windows resources source files, the output type is `res`.
    - Fail if windows resources are input to a component for which there are no windows binaries.
- `PolymorphicDomainObjectContainer.containerWithType()` should instead override `withType()`.

## Feature: Build multiple platform variants of Java libraries

### Story: Build author declares target Java version for a Java library

For example:

    plugins {
        id 'java-lang'
    }

    jvm {
        libraries {
            myLib {
                target java("7")
            }
        }
    }

This declares that the bytecode for the binary should be generated for Java 7, and should be compiled against the Java 7 API.
Assume that the source also uses Java 7 language features.

For this story, only the current JDK will be considered as a candidate to perform the compilation. Later stories could add support for JDK discovery
(the test fixtures do this). When not specified, default to whichever target JVM the current JDK defaults to.

Target platform should be reachable from the `JvmBinarySpec`.

#### Implementation plan

- Add `org.gradle.language.java.JvmPlatform` interface and default implementation.
- Update samples to include a Java version declaration.

#### Test cases

- Running `gradle assemble` will build for Java 6 and the resulting bytecode will use the Java 6 bytecode version.
- Reasonable error message when the current JDK cannot build for the target Java version.
- Target JVM runtime appears in the output of the components report.

### Story: Build author declares that JVM library should be built for multiple JVM versions

For example:

    plugins {
        id 'java-lang'
    }

    jvm {
        libraries {
            myLib {
                target java("6")
                target java("8")
            }
        }
    }

This will result in 2 Jar binaries being defined for the `myLib` library. Running `gradle assemble` will build both these binaries. Reuse BinaryNamingScheme for now.

Add a sample to show a JVM library built for multiple Java versions.

#### Test cases

- A Jar binary is defined for each target platform.
- Running `gradle assemble` will build the Jars, and the bytecode in each Jar uses the correct bytecode version.
- Binaries with correct target JVM runtime appear in the output of the components report.
- `BinarySpec.buildable` is correctly reported for each JVM binary in the components report
    - Must be able to report both buildable and non-buildable binaries
- For both JVM and native
    - Fails gracefully when attempting to target an unknown platform
    - Fails gracefully when any one of a set of a target platforms is not known: reports the name of the invalid platform
    - Fails gracefully when attempting to target a JVM platform for a native component, and vice-versa
- When no JVM platform is targeted, attempts to build for current JVM
- Where some variants are not buildable on the current machine, `gradle assemble` will build only the buildable variants
- Useful error message from compilation task when attempting to build a binary that is not buildable

### Story: Build author chooses NativePlatform from pre-defined set

This story makes the production of native platform variants more consistent with the approach used for Java libraries,
by providing a common set of 'pre-defined' `NativePlatform` instances.

#### User visible changes

- A set of "sensible" platforms will always be available for a component to target, even if not defined in a `platforms` block.
- User-provided platforms may have arbitrary names for Architecture and Operating System
- Each defined `NativePlatform` must have a value specified for `OperatingSystem` and `Architecture`
    - There will be no special value available for "Tool chain default"
- When a NativeComponentSpec has no 'targetPlatform' defined, then only a single NativeVariantSpec will be produced
    - The `Platform` that best matches the os/arch of the build machine will be used by default

#### Test coverage

- Native component with no targetPlatform will be have one variant produced, for pre-defined platform that matches current
- Native component can target multiple pre-defined platforms, producing multiple variants
- NativeExecutable can link to NativeLibrary where neither defines target platform
- NativeExecutable can link to NativeLibrary where both define identical sets of targetPlatforms
- Error reported when defining platform:
    - with same name as built-in platform
    - with no value specified for architecture or operatingSystem
- Error reported when resolving libs for NativeExecutable
    - where Executable and Library define different target platforms
    - where Executable defines targets non-default platform and Library (implicitly) targets default platform

#### Open issues / Options

- Add infrastructure to coerce string to architecture or operating system types.
- Rename `targetPlatforms` to `platform` or `platforms` for both JVM and Native components
- Rename `PlatformContainer` and methods so it's clear we're 'resolving' a `Platform` based on requirements

### Story: Assemble task fails when no variants are buildable

Currently, if a user attempts to build a _specific_ binary and that binary cannot be built (e.g. no tool-chain is available) the user is given an informative message. 

If a user uses `gradle assemble` to build _all_ binaries, Gradle will attempt to build any binaries that are
considered 'buildable', skipping those that aren't. This means that a build script may define a component for multiple platforms, but `gradle assemble` will only build those variants where a tool chain is available.

However, `gradle assemble` will silently do nothing when _none_ of the binaries are buildable.

In this story, we will change this behaviour so that if _no_ binaries can be built with `assemble`, Gradle will report the reason that each defined binary is not buildable.

#### Implementation
- Create BinaryBuildAbility interface:
    public interface BinaryBuildAbility {
        boolean isBuildable();
        void explain(TreeVisitor<? super String> visitor);
    }
- Add getBuildAbility() to BinarySpecInternal
- Make setBuildable from BinarySpecInternal configure something of type BinaryBuildAbility
- Change BaseBinarySpec to determine isBuildable() using BinaryBuildAbility
- Create an AssembleBinariesTask class to replace the default assembly task.  This task should contain:
    - A list of binaries that are not buildable
    - A task action that, if the task has unbuildable binaries and no dependencies (i.e. at least one buildable binary was not found), throws an exception with the reason for each unbuildable binary
- When configuring the assemble task, if a binary is not buildable, add the binary to the list of unbuildable binaries in AssembleBinariesTask
- Change component report to output reasons for unbuildable binaries

#### Test Cases
- When there are no buildable binaries, assemble fails outputting unbuildable information for each binary
- Unbuildable information is NOT displayed when there is at least one buildable binary
- Unbuildable information is NOT displayed When there are no buildable binaries, but assemble has other configured dependencies
- When there are no binaries configured at all, no failure is produced

## Feature: Plugin implements custom language support

### Story: Plugin declares custom language source set

For example:

    interface CustomLanguageSourceSet extends LanguageSourceSet {
        String someProperty
    }
    class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
        ...
    }

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class ComponentModel {
            @LanguageType
            void defineMyLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> builder) {
                builder.setLanguageName("custom")
                builder.setDefaultImplementation(DefaultCustomLanguageSourceSet)
            }
        }
    }

Given this, can now define source sets of type `CustomLanguageSourceSet` in a `FunctionalSourceSet` or `ComponentSourceSet`:

    sources {
        main {
            custom(CustomLanguageSourceSet) { ... }
        }
    }

The source set is configured with conventional source directories based on the source set name.

For this story, the language is not included as input to any component. It is simply possible to define source sets for the custom
language.

#### Test cases

- Source set uses the conventional source directory.

#### Open issues

- Detangle 'the things I need to compile a language of this type' (a set of files, some settings) from
 'a way to configure an arbitrary set of source files of this language` (the source set). The plugin should only have to declare the things it
 needs to compile. A plugin might still be able to additionally declare a source set type, when some custom implementation is required.
- Platform should be attached to source set as well.
- Need to be able to register additional tools with a Tool Chain as well
    - E.g. Adding Objective-C language adds the objective-c compiler to GCC and Visual Studio
    - E.g. Adding Scala language registers the Scala tools

### Story: Plugin declares custom language implementation

For example:

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class ComponentModel {
            @LanguageTransform
            void compileMyLanguage(CollectionBuilder<Task> taskBuilder, CustomLanguageSourceSet source, SampleBinary binary) {
            }
        }
    }

Given this, a source set of the appropriate type is added when a component may include a binary of the type declared in the transform rule. A source
set is not added for components where the language cannot be transformed.

For this story, the source is not actually compiled or included in a binary, or tasks defined. This is added later.

#### Test cases

- Source set is added to components for which a transform is registered.
    - Source set uses the conventional source directory.
    - Source set is visible in the component reports
- Source set is not added to components for which a transform is not registered.

### Story: Plugin provides custom language implementation

For example:

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class ComponentModel {
            @LanguageTransform
            void compileMyLanguage(CollectionBuilder<Task> taskBuilder, CustomLanguageSourceSet source, SampleBinary binary) {
                ... uses the builder to define some tasks ...
            }
        }
    }

Running `gradle assemble` will invoke the compilation tasks defined by the transform rule, and the output included in the target binary.

The tasks are not defined if the language source set is empty or buildable by some other task.

Add a sample to show how to add a custom language for a JVM library. Add a sample to show how to implement a custom component type from some
custom language.

#### Test cases

- Can build a JVM library from a custom language.
    - Running `gradle assemble` compiles the custom language and includes the output in the JAR.
- Can build a custom binary from a custom language.
    - Running `gradle assemble` compiles the custom language and include the output in the
- Source set is not added to components for which a transform is not registered.
- Compile tasks are not defined if the source set is empty.

#### Open issues

- Need to be able to apply a naming scheme for tasks, and some way to inject an output location for each transformation.

### Story: Jvm component is built from Scala sources

### Story: Core plugins declare language implementations

Change the native, Java and classpath resource language plugins to replace usages of `LanguageRegistration` with the declarative approach above.

#### Open issues

- Probably don't need `TransformationFileType` any more.

## Feature: Build author describes target platform and language dialect for source set

TODO

## Feature: Custom binary is built from Java sources

Change the sample plugin so that it compiles Java source to produce its binaries

- Uses same conventions as a Java library.
- No dependencies.

## Feature: Build author declares a custom target platform for a Java library

For example:

    apply plugin: 'new-java'

    platforms {
        myContainer {
            runsOn platforms.java6
            provides {
                library 'myorg:mylib:1.2'
            }
        }
    }

    libraries {
        myLib {
            buildFor platforms.myContainer
        }
    }

This defines a custom container that requires Java 6 or later, and states that the library should be built for that container.

This includes the API of 'myorg:mylib:1.2' at compile time, but not at runtime. The bytecode for the library is compiled for java 6.

When a library `a` depends on another library `b`, assert that both libraries run on the same platform, or that `b` targets a JVM compatible with
the JVM for the platform of `a`.

### Open issues

- Rework the platform DSL for native component to work the same way.
- Retrofit into legacy java and web plugins.

## Feature: Build author declares target platform for custom library

Change the sample plugin to allow a target JVM based platform to be declared:

    apply plugin: 'my-sample'

    platforms {
        // Several target platforms are visible here
    }

    libraries {
        myCustomLib {
            minSdk 12 // implies: buildFor platforms.mySdk12
        }
    }

## Feature: User visualises project component model

### Story: User views outline of component model from command line

Present to the user some information about how a given project is composed.

- User runs `gradle components` and views report on console.
- Presents basic details of each project component:
    - JVM and native components
    - Legacy JVM library and application
    - War, Ear
    - Custom components
    - Test suites
    - Distribution
- Show source sets and binaries for each component.
- Show target platforms, flavors and build types for each component, where applicable.
- Show output files for each binary.
- Show generated source sets.
- Report on empty source sets, eg when source directory is configured incorrectly.

#### Implementation

- Rendering for custom types.
- Display install task for executables, test task for test suites.
- Display native language header directories.
- Display native tool locations and versions.
- Don't show the generated CUnit launcher source set as an input. Should be shown as an intermediate output, as should the object or class files.
- Add basic implementation for legacy component types.
- Add `description` to `ProjectComponent`.
- Sort things by name
- Move rendering of specific component types to live with the type and remove dependency on cpp project.
- Add some general 'show properties' rendering.
- Tweak report headers for single project builds.
- Don't show chrome when report task is the only task scheduled to run.
- Port HelpTasksPlugin and WrapperPluginAutoApplyAction to Java.

#### Issues discovered

- Issue: TestSuite components are not included in ProjectComponentContainer.
- Issue: Display name for CUnit executable is 'C unit exe' instead of `CUnit executable'
- Issue: Better naming scheme for output directories, eg `executables/someThing/...` instead of `binaries/someThingExecutable/...`

### Story: User views component model as HTML report

TBD

### Open Issue: TestSuite components are not in components container

I think there's some kind of concept of a hierarchy of components going on here - some components are the 'top level' or 'main' outputs of a project
and some components are 'owned' by some other component and form part of some larger aggregate component. For example, a library or application component
usually has some test suite associated with it, that represent the various tests that are relevant for that particular component.
But the test suite is itself a component, and a test suite could be the sole output of a particular project and not associated with any particular component.
It would therefore be the 'main' component for that particular project.

So, we want some way to define and navigate to the main components of a project, so for example, we can write a rule to say that the 'assemble' task
should build all the binaries of the main components. Through the main components we should also be able to navigate to the components that make up
each main component. eg let me configure the test suite for the hello world application.

However, we also want a way to apply rules to components regardless of where they sit in this hierarchy. For example, so we can implement rules like:
for each component, define a source set for each supported language that we can link/assemble into the binaries for the component.

This essentially means two 'containers' - one that holds the main components, and another that contains all components.

## Feature: Build user runs unit tests for JVM library

Apply a convention to define test suites for JVM components. Should be able to run the unit tests for each variant.

Test suites should be visible in the components report.

Should be possible to declare functional test suites for components.

Should be possible to declare stand-alone test suite with custom variants.

# Open issues and Later work

## Component model

- Should use rules mechanism, so that this DSL lives under `model { ... }`
- Reuse the local component and binary meta-data for publication.
    - Version the meta-data schemas.
    - Source and javadoc artifacts.
- Test suites.
- Libraries declare an API that is used at compile time.
- Gradle runtime defines Gradle plugin as a type of jvm component, and Gradle as a container that runs-on the JVM.
- The `application` plugin should also declare a jvm application.
- The `war` plugin should also declare a j2ee web application.
- The `ear` plugin should also declare a j2ee application.
- Configure `JvmLibrary` and `JvmLibraryBinary`
    - Customise manifest
    - Customise compiler options
    - Customise output locations
    - Customise source directories
        - Handle layout where source and resources are in the same directory - need to filter source files

## Language support

- Need a better name for `TransformationFileType`.
- Support multiple input source sets for a component and binary.
    - Apply conflict resolution across all inputs source sets.
- Generate API documentation from source.
- Java language support
    - Java language level.
    - Source encoding.
    - Copy tests from `:plugins:org.gradle.java` and `:plugins:org.gradle.compile` into `:languageJvm` and convert to new component model
- Groovy and Scala language support, including joint compilation.
    - Language level.
- ANTLR language support.
    - Improve the generated source support from the native plugins
    - ANTLR runs on the JVM, but can target other platforms.
- custom sourceSets declared via 'sources' DSL must always be declared with their type (even its type is obvious) e.g:

    apply plugin:'cpp'

    sources {
        lib {
            cpp(CppSourceSet)
        }
    }

- Need some convention or mechanism for source that is conditionally included based on the target platform.

## Platform support

- Build author defines a Java platform with a custom bootstrap classpath (for cross compilation, Android, etc).
- Plugin declares a custom platform type
- Plugin declares a custom platform type that includes another platform: e.g.
    - AndroidJvm platform includes a JavaPlatform and adds Android APIs
    - Scala platform includes a Java platform
    - Java platform includes a JVM platform
    - Web container platform includes a Java platform
- Target platform should be visible in the dependencies reports

## Tool Chain support

- Turn what is `ToolChain` into a tool chain locator or factory.
- Turn what is `PlatformToolChain` into an immutable tool chain. Resolve the tool chain during configuration, and use this as input for incremental build.
- Treat Java tool chain as input for incremental build.
- Link using `g++` or `clang++` only when a c++ runtime is required.
- Link with correct flags for a windows resource only binary (ie when neither c nor c++ runtime is required).
- Provide the correct flags to compile and link against Foundation framework when using objective-c
- Use mingw under cygwin when target is windows, gcc under cygwin when target is posix
- Should there be metadata-information about each `ToolChain` (`NativeToolChain`, `JavaToolChain`) so that a platform can know what capabilities it has.
- Discover or configure the JDK installations
- When using JDK to cross-compile for an earlier Java version, need to provide correct Java API in classpath
- Verify the java version of the tool chain, rather than assuming current

## Misc

- Consider splitting up `assemble` into various lifecycle tasks. There are several use cases:
    - As a developer, build me a binary I can play with or test in some way.
    - As part of some workflow, build all binaries that should be possible to build in this specific environment. Fail if a certain binary cannot be built.
      For example, if I'm on Windows build all the Windows variants and fail if the Windows SDK (with 64bit support) is not installed.
      Or, if I'm building for Android, fail if the SDK is not installed.
    - Build everything. Fail if a certain binary cannot be built.
- Lifecycle phase for binary to determine if binary can be built
    - Replace current `BinarySpec.buildable` flag
    - Attach useful error message explaining why the binary can't be built: no sources, no available toolchain, etc
    - Fail early when trying to build a binary that cannot be built
- Better cleanup when components, binaries and source sets are removed or renamed.
    - Clean up class files for binary when _all_ source files for a source set are removed.
    - Clean up class files for binary when source set is removed or renamed.
    - Clean up output files from components and binaries that have been removed or renamed.
- Expose the source and javadoc artifacts for local binaries.
- Deprecate and remove support for resolution via configurations.
- Add a report that shows the details for the components and binaries produced by a project.
- Bust up the 'plugins' project.

## Tech debt for 'platform-*' and 'language-*' subprojects

- Convert all production classes to java and use `src/main/java` instead of `src/main/groovy`
- Remove all classycle exclusions

