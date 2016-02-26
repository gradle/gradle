The Gradle team is pleased to bring you Gradle 2.12. This release brings support for compile only dependencies, improved build script compilation speed and even better IDE support.

Gradle 2.12 now finally includes support for modeling compile only dependencies when using the [Java plugin](userguide/java_plugin.html). This capability establishes a much clearer migration path for those coming from Maven and have build requirements for which leveraging 'provided' scope was typically the solution.

We've introduced some improvements to test execution from the Gradle command line. Support has been added for executing specific test suites and parameterized tests making test-driven development using Gradle even easier.

With each Gradle release we strive to not only make the command line Gradle experience better but also using Gradle in conjunction with your favorite IDE even more enjoyable. Using Gradle with IntelliJ IDEA and the [IDEA plugin](userguide/idea_plugin.html) now works better than ever. Manually managing Java target compatibility settings is no longer necessary as the [IDEA plugin](userguide/idea_plugin.html) will now ensure that Gradle and the IDE consistently use the same compiler settings.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Support for declaring compile time only dependencies with Java plugin

You can now declare dependencies to be used only at compile time in conjunction with the [Java plugin](userguide/java_plugin.html). Compile only dependencies are used only during source compilation and are not included in the runtime classpath or exposed to dependent projects. This behavior is similar to that of the 'provided' scope available in Maven based builds. However, unlike Maven provided dependencies, compile only dependencies in Gradle are not included on the test classpath.

Compile only dependencies should be assigned to the relevant source set's 'compileOnly' configuration.

    dependencies {
        compileOnly 'javax.servlet:servlet-api:2.5'
    }

More details about compile only configurations can be found in the [Java plugin chapter](userguide/java_plugin.html#sec:java_plugin_and_dependency_management) of the user guide.

### Faster build script compilation

Significant optimizations have been made to build script compilation. You can expect reductions in startup times of up to 20% when running a build for the first time or after making changes to build scripts.

### Test filtering support for Test Suites and (in JUnit) Parameterized Tests

The [test filtering](userguide/java_plugin.html#test_filtering) capabilities provided by the Java plugin have been improved to allow matching against TestSuite names in both JUnit and TestNG test tasks. You can also filter against Parameterized Tests when using JUnit. Examples of such test filters include:

    gradle test --tests "com.example.MyTestSuite"        // Includes the tests in the given suite.
    gradle test --tests "com.example.ParameterizedTest"  // All iterations of methods in the test.
    gradle test --tests "*ParameterizedTest.foo*"        // All iterations of the foo method in the test.
    gradle test --tests "*ParameterizedTest.*[2]"        // Only iteration 2 of all methods in the test.

These same patterns can also be used in the [`filter { }`](javadoc/org/gradle/api/tasks/testing/TestFilter.html) block of the test task directly in your `build.gradle` file.

This feature was contributed by [Alex Landau](https://github.com/AlexLandau).

### IDE integration improvements

#### IDEA Plugin uses targetCompatibility for each subproject to determine module and project bytecode version

The Gradle [IDEA plugin](userguide/idea_plugin.html) generates configuration files allowing a Gradle project to be developed using IntelliJ IDEA. Previous versions of Gradle did not consider `targetCompatibility` settings for Java projects when generating IDEA configuration. This behavior has been improved, so that the generated IDEA project will have a 'bytecode version' matching the highest `targetCompatibility` value of all imported subprojects. For multi-project Gradle builds that contain a mix of `targetCompatibility` values, the generated IDEA module for subprojects deviating from the project bytecode version will include an override for the appropriate 'module bytecode version'.

#### Scala support for Intellij IDEA versions 14 and later using 'idea' plugin

Beginning with IntelliJ IDEA version 14, Scala projects are no longer configured using a project facet but instead use a [Scala SDK](http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/1/) project library. This affects how the IDEA metadata should be generated. Using the 'idea' plugin in conjunction with Scala projects for newer IDEA version would cause errors due to the Scala facet being unrecognized. The 'idea' plugin now by default creates an IDEA 14+ compatible Scala SDK project library as well as adds this library to all Scala modules. More information, including how to configure your project to continue to support older IDEA versions, can be found in the [user guide](userguide/scala_plugin.html#sec:intellij_idea_integration).

This feature was contributed by [Nicklas Bondesson](https://github.com/nicklasbondesson).

### Experimental software model improvements

#### Model data report

The model report is quite verbose by default. For each node of the model, it displays the types of all properties as well as any rules that created or mutated them. However, you might only want to see the state of data in the model in order to validate your build configuration. In this case, you can now request a more concise model report using `gradle model --format short`. By default, Gradle still outputs the most complete report, which is equivalent to calling `gradle model --format full`.

#### Fine grained application of rules

TBD - A new kind of rule method is now available, which can be used to apply additional rules to some target.

This kind of method is annotated with `@Rules`. The first parameter defines a `RuleSource` type to apply, and the second parameter defines the target element to apply the rules to.

Two new annotations have been added:

- `@RuleInput` can be attached to a property of a `RuleSource` to indicate that the property defines an input for all rules on the `RuleSource`.
- `@RuleTarget` can be attached to a property of a `RuleSource` to indicate that the property defines the target for the `RuleSource`.

#### Apply rule to all descendant elements matching type in scope

It is frequently a requirement to apply a rule to all elements in a scope that match a certain type. E.g. to set a default output directory for every binary produced, regardless of exactly where the binary is defined in the model. With the new `@Each` annotation this is now possible. All elements are matched regardless of their location in the model as long as a) they are a descendant of the scope element, and b) they match the given type. More information about this can be found in the [Sotfware model](userguide/software_model.html#binding_all_elements_in_scope) section of the user guide.

#### Declaration of local JVM installations

It is now possible to declare the local installations of JVMs (JDK or JRE) in your model. Gradle will probe the declared installations and automatically detect which version, vendor and type of JVM it is. This information can be used to customize your `JavaCompile` tasks, and future versions of Gradle will leverage this to select the appropriate toolchain when compiling Java sources. More information about this can be found in the [Java software model](userguide/java_software.html#declaring_java_toolchains) section of the user guide.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed in the next major Gradle version (Gradle 3.0). See the User guide section on the [Feature Lifecycle](userguide/feature_lifecycle.html) for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### Ant-based Scala compiler deprecation

The [Zinc Scala compiler](https://github.com/typesafehub/zinc) is now sufficiently stable and considerably more efficient than the Ant-based Scala compiler. We have changed the default value of [`ScalaCompileOptions#isUseAnt`](javadoc/org/gradle/api/tasks/scala/ScalaCompileOptions.html#isUseAnt%28%29) to `false` and we are deprecating the option. Also, since the Zinc Scala Compiler only works in `fork` mode, we are also defaulting that option to `true` and deprecating it. Both the `useCompileDaemon` and `daemonServer` were only supported by the Ant-based Scala Compiler and are also deprecated. Please update your build scripts to remove uses of these options. For more information, see the [Scala Plugin](userguide/scala_plugin.html) section of the User Guide.

## Potential breaking changes

### Changes to 'idea' plugin Scala projects

Scala projects using the 'idea' plugin now generate IntelliJ IDEA metadata targeting versions 14 and newer. Users of IDEA versions older than 14 will need to update their build scripts to specify that metadata should be generated for an earlier IDEA version.

    idea {
        targetVersion = '13'
    }


### Change in exclude file pattern matching

Projects which use certain wildcards patterns to `exclude` files may notice that fewer files are excluded than in previous versions of Gradle.

For example, given the following directory structure:

    files
    ├── valid
    │   └── Some.java
    ├── alsoValid
    │   └── Some.class
    └── ignoreMe.txt

And a `FileTree` configured with a single-level wildcard `'*'`:

    fileTree('files') {
        exclude('*')
    }

In previous versions of Gradle, the `FileTree` would exclude all files (even those in subdirectories).

Gradle 2.12 changes this behavior, such that the `FileTree` will now only exclude the files in the top-level directory (i.e. `ignoreMe.txt`), but will retain the files in the subdirectories. This behavior is now consistent with how `include` patterns work.

Should you want to exclude all files, including those in subdirectories, a multi-level wildcard should be used.

    fileTree('files') {
        exclude('**')
    }

Note, this change affects all implementations of [`PatternFilterable`](javadoc/org/gradle/api/tasks/util/PatternFilterable.html), not just `FileTree`.

### Undocumented `force` option on [Scala Plugin](userguide/scala_plugin.html) changed to `boolean`

You may have a `force = <something>` option in your build file. Previously, this option was ignored by Gradle's Zinc Scala Compiler, but now it will be read and interpreted as a `boolean` value. Read more at [Scala Plugin](userguide/scala_plugin.html)

### Changes to the experimental software model

One of the objectives of the software model is to describe _what_ you want to build instead of _how_. In particular, you might want to describe that you are building a web application. That application can be seen as an aggregate of libraries, but also sources, static resources, etc. A library can be seen as an aggregate of sources, producing different binaries depending on the target platform, the CPU architecture, ... A source set may also be viewed as a component that can itself be built (e.g. generated sources). To reflect this idea that "everything is a component" and "components are composed of other components", the `ComponentSpec` hierarchy has been reorganised. This will greatly simplify the creation of custom components, as well as reduce the amount of component type specific code, by composing behavior, rather than inheriting it.

- The `sources` and `binaries` properties of `ComponentSpec` have been moved to dedicated subtypes (resp. `SourceComponentSpec` and `VariantComponentSpec`)
- `GeneralComponentSpec` is the base interface for all components that are built from sources and can generate multiple variants (typically, depending on the target platform)
- `LibrarySpec` and `ApplicationSpec` are two typical examples of components that extend `GeneralComponentSpec`, that can be leveraged to build your own component types.
- `BuildableModelElement` has been renamed to `BuildableComponentSpec`, which in turn now extends `ComponentSpec`.

#### Type registration

- `LanguageTypeBuilder`, `ComponentTypeBuilder`, `BinaryTypeBuilder` have been unified into a single `TypeBuilder`.
- The `BinaryType` and `LanguageType` annotations are no longer supported. They have been replaced by a single `ComponentType` annotation.
- The `languageName` property has been removed from `LanguageTypeBuilder`. This property was not used and does not make sense for `LanguageSourceSet` subtypes that don't represent an actual language.

#### Other changes

- Deprecated properties `ComponentSpec.source` and `BinarySpec.source` have been removed. These have been replaced by the `sources` property.

### Java plugin 'compile' configuration no longer represents all compile time dependencies

As a result of the addition of [compile only dependencies](#support-for-declaring-compile-time-only-dependencies-with-java-plugin), references to `configurations.compile` no longer
accurately represent the compile classpath used to build a given source set, as this will not include 'compileOnly' dependencies. When needing a reference to a source set's compile
classpath, [`SourceSet#getCompileClasspath()`](dsl/org.gradle.api.tasks.SourceSet.html#org.gradle.api.tasks.SourceSet:compileClasspath) should be used instead.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Nicklas Bondesson](https://github.com/nicklasbondesson) - Support IntelliJ IDEA 14+ when using 'scala' and 'idea' plugins
* [Lewis Cowles](https://github.com/LewisCowles1986) - Installation documentation clarification about GRADLE_HOME
* [Alex Landau](https://github.com/AlexLandau) - Fix for JUnit Test Filtering with the `--test` flag to work for Parametrized tests and TestSuites
* [Stuart Armitage](https://github.com/maiflai) - Fix some inconsistencies between Ant and Zinc scala compiler implementations
* [Pavel Fadeev](https://github.com/bearmug) - Makes `exclude('*')` semantically consistent with `include('*')` excluding only files in the baseDir
* [Tony Abbott](https://github.com/tonyabbott) - Fix for native testing plugins when test binaries are not buildable

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
