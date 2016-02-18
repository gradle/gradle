TODO preamble:

talk about the key features that have relevance for most of our users:

- compile only dependencies
- test filtering
- IDE integration improvements

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Support for declaring compile time only dependencies with Java plugin

You can now declare dependencies that are used only during source compilation in conjunction with the [Java plugin](userguide/java_plugin.html). Compile only dependencies are used
only during source compilation and are not included in the runtime classpath or exposed to dependent projects. Compile only dependencies are assigned to the relevant source set's
'compileOnly' configuration.

    dependencies {
        compileOnly 'javax.servlet:servlet-api:2.5'
    }

More details about declaring compile only configurations can be found in the [Java plugin chapter](userguide/java_plugin.html#sec:java_plugin_and_dependency_management) of the user guide.

### Faster build script compilation

TBD

### Test filtering support for Test Suites and (in JUnit) Parameterized Tests

You can use [test filtering](userguide/java_plugin.html#test_filtering) in the Java Plugin to execute a specific subset of tests. Now, you can match against TestSuite names in both JUnit and TestNG test tasks. You can also filter against Parameterized Tests in JUnit test tasks. Try some of the following command-line invocations:

    gradle test --tests "com.example.MyTestSuite"        // Includes the tests in the given suite.
    gradle test --tests "com.example.ParameterizedTest"  // All iterations of methods in the test.
    gradle test --tests "*ParameterizedTest.foo*"        // All iterations of the foo method in the test.
    gradle test --tests "*ParameterizedTest.*[2]"        // Only iteration 2 of all methods in the test.

These same patterns can also be used in the [filter section](userguide/java_plugin.html#testfiltering) of the test task directly in your `build.gradle` file.

### IDE integration improvements

#### Idea Plugin uses targetCompatibility for each subproject to determine module and project bytecode version

The Gradle 'idea' plugin can generate configuration files allowing a Gradle build to be opened and developed in IntelliJ IDEA.
Previous versions of Gradle did not consider any `targetCompatibility` settings for java projects.
This behavior has been improved, so that the generated IDEA project will have a 'bytecode version' matching the highest targetCompatibility value for all imported subprojects.
For a multi-project Gradle build that contains a mix of targetCompatibility values, the generated IDEA module for a sub-project will include an override for the appropriate 'module bytecode version' where it does not match that of the overall generated IDEA project.

#### Scala support for Intellij IDEA versions 14 and later using 'idea' plugin

Beginning with IntelliJ IDEA version 14 Scala projects are no longer configured using a project facet and instead use a
[Scala SDK](http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/1/) project library. This affects how the IDEA metadata should be
generated. Using the 'idea' plugin in conjunction with Scala projects for newer IDEA version would cause errors due to the Scala facet being unrecognized. The 'idea' plugin
now by default creates a Scala SDK project library as well as adds this library to all Scala modules. More information can be found in the
[user guide](https://docs.gradle.org/current/userguide/scala_plugin.html#sec:intellij_idea_integration).

This feature was contributed by [Nicklas Bondesson](https://github.com/nicklasbondesson).

### Software model improvements

#### Model data report

The model report can be very verbose by default: for each node of the model, it will show you the types of the properties as well as the rules that created or mutated them. However, you might only want to see the data that the model contains, and only the data, for example to validate your build configuration. In that case, you can now do this by calling `gradle model --format=short`. By default, Gradle still outputs the most complete report, which is equivalent to calling `gradle model --format=full`.

#### Fine grained application of rules

TBD - A new kind of rule method is now available, which can be used to apply additional rules to some target.

This kind of method is annotated with `@Rules`. The first parameter defines a `RuleSource` type to apply, and the second parameter defines the target element to apply the rules to.

Two new annotations have been added:

- `@RuleInput` can be attached to a property of a `RuleSource` to indicate that the property defines an input for all rules on the `RuleSource`.
- `@RuleTarget` can be attached to a property of a `RuleSource` to indicate that the property defines the target for the `RuleSource`.

#### Apply rule to all descendant elements matching type in scope

It is frequently a requirement to apply a rule to all elements in a scope that match a certain type. E.g. to set a default output directory for every binary produced, regardless of exactly where the binary is defined in the model. With the new `@Each` annotation this is now possible. All elements are matched regardless of their location in the model as long as a) they are a descendant of the scope element, and b) they match the given type. More information about this can be found in the “[Sotfware model section of the userguide](userguide/software_model.html#binding_all_elements_in_scope).”

#### Declaration of local JVM installations

It is now possible to declare the local installations of JVMs (JDK or JRE) in your model. Gradle will probe the declared installations and automatically detect which version, vendor and type of JVM it is. This information can be used to customize your `JavaCompile` tasks, and will subsequently be used by Gradle itself to select the appropriate toolchain when compiling Java sources. More information about this can be found in the “[Java sotfware model section of the userguide](userguide/java_software.html.html)”

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

### Ant-based Scala compiler deprecation

The [Zinc Scala comiler](https://github.com/typesafehub/zinc) is now sufficiently stable and considerably more efficient than the Ant-based Scala compiler. We have changed the default value of `useAnt` to `false` and we are deprecating the option. Please update your build scripts to remove uses of that option.

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to 'idea' plugin Scala projects

Scala projects using the 'idea' plugin now generate IntelliJ IDEA metadata targeting versions 14 and newer. Users of IDEA versions older than 14 will need to update
their build scripts to specify that metadata should be generated for an earlier IDEA version.

    idea {
        targetVersion = '13'
    }


### Change in exclude file pattern matching (May Exclude Fewer!)

Projects which use wildcards in patterns to `exclude` files from certain tasks may notice that fewer files are excluded than in previous versions of Gradle.

For example, if the project had a directory structure like:

    files/ignoreMe.txt
    files/valid/Some.class
    file/alsoValid/Some.Java

And a FileTree configured with a single-level wildcard `('*')`:

    fileTree('files') {
        exclude('*')
    }

In previous versions of Gradle, the FileTree would exclude all files (even those in subdirectories).

This version of Gradle changes the behavior (see: [GRADLE-3182](https://issues.gradle.org/browse/GRADLE-3182)) so that FileTree will exclude only the files in the top-level directory (`files/ignoreMe.txt`) and will contain the files in the subdirectories. This behavior is now consistent with how `include` patterns work.

To reproduce the behavior of earlier releases, change the single-level wildcard to a multi-level wildcard:

    fileTree('files') {
        exclude('**')
    }

### Changes to the experimental software model

One of the objectives of the software model is to describe _what_ you want to build instead of _how_. In particular, you might want to describe that you are building a web application. That application as an aggregate of libraries, but also sources, static resources, ... A library can be seen as an aggregate of sources, producing different binaries depending on the target platform, the CPU architecture, ... A source set may also be viewed as a component that can itself be built (think of generated sources). To reflect this idea that "everything is a component" and "components are composed of other components", the `ComponentSpec` hierarchy has been reorganised. This will greatly simplify the creation of custom components, as well as allowing to reduce the amount of specific code for each component type by composing behavior instead of inheriting behavior:

- The `sources` and `binaries` properties of `ComponentSpec` have been moved to dedicated subtypes (resp. `SourceComponentSpec` and `VariantComponentSpec`)
- `GeneralComponentSpec` is the base interface for all components that are built from sources and can generate multiple variants (typically, depending on the target platform)
- `LibrarySpec` and `ApplicationSpec` are two typical examples of components that extend `GeneralComponentSpec`, that you can leverage to build your own component types.
- `BuildableModelElement` has been renamed to `BuildableComponentSpec`, which in turn now extends `ComponentSpec`.

#### Type registration

- `LanguageTypeBuilder`, `ComponentTypeBuilder`, `BinaryTypeBuilder` have been unified into a single `TypeBuilder`.
- The `BinaryType` and `LanguageType` annotations are no longer supported. They have been replaced by a single `ComponentType` annotation.
- The `languageName` property has been removed from `LanguageTypeBuilder`. This property was not used and does not make sense for `LanguageSourceSet` subtypes that don't represent an actual language.

#### Other changes

- Deprecated properties `ComponentSpec.source` and `BinarySpec.source` have been removed. These were replaced by the `sources` property.

### Java plugin 'compile' configuration no longer represents all compile time dependencies

As a result of the addition of [compile only dependencies](#support-for-declaring-compile-time-only-dependencies-with-java-plugin), references to `configurations.compile` no longer
accurately represent the compile classpath used to build a given source set, as this will not include 'compileOnly' dependencies. When needing a reference to a source set's compile
classpath, [`SourceSet#getCompileClasspath()`](dsl/org.gradle.api.tasks.SourceSet.html#org.gradle.api.tasks.SourceSet:compileClasspath) should be used instead.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Nicklas Bondesson](https://github.com/nicklasbondesson) - Support IntelliJ IDEA 14+ when using 'scala' and 'idea' plugins
* [Lewis Cowles](https://github.com/LewisCowles1986) - Installation documentation clarification about GRADLE_HOME
* [Alex Landau](https://github.com/AlexLandau) - Fix for JUnit Test Filtering with the `--test` flag to work for Parametrized tests and TestSuites
* [Stuart Armitage](https://github.com/maiflai) - Fix some inconsistencies between ant and zinc scala compiler implementations
* [Pavel Fadeev](https://github.com/bearmug) - Makes `exclude('*')` semantically consistent with `include('*')` excluding only files in the baseDir
* [Tony Abbott](https://github.com/tonyabbott) - Fix for native testing plugins when test binaries are not buildable

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
