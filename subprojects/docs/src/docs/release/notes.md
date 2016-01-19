The Gradle team is pleased to bring you Gradle 2.11. This release delivers significant improvements to the new [software model](userguide/software_model.html), together with a number of bug fixes and smaller improvements.

The software model is the future of Gradle. Native and Play plugins already leverage it and we are working intensively on using it also for Java support. In this release, new Java plugins started to support testing with JUnit and became smarter in compile avoidance. Support for developing plugins with the new software model also got better.

Starting with this release, the [Tooling API](userguide/embedding.html) exposes more information that enables further improvements in Eclipse and IntelliJ IDEA in regard to integration with Gradle. Gradle's own [IDEA plugin](userguide/idea_plugin.html) used to generate IntelliJ IDEA configuration files has also been improved to detect project language level in a smarter way.

[Continuous build](userguide/continuous_build.html) got better in this release by taking into account changes happening during build execution.

A Gradle release would not be complete without contributions from the wonderful Gradle community. This release includes several improvements and fixes from community pull requests including support for:

- Controlling TestNG execution order in the [Java plugin](userguide/java_plugin.html)
- Different test frameworks in the [Build Init plugin](userguide/build_init_plugin.html)
- Configuring Twirl source sets to use Java default imports in the [Play plugin](userguide/play_plugin.html)
- Exclude information in [Ivy publishing](userguide/publishing_ivy.html)

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Software model improvements

#### Better support for developing plugins with the Software Model with managed source set types

The `LanguageSourceSet` type can now be extended via `@Managed` subtypes, allowing for declaration of `@Managed` source sets without having to provide a default implementation.

Example:

    @Managed
    interface MarkdownSourceSet extends LanguageSourceSet {
        boolean isGenerateIndex()
        void setGenerateIndex(boolean generateIndex)
        boolean isSmartQuotes()
        void setSmartQuotes(boolean smartQuotes)
    }
    class RegisterMarkdown extends RuleSource {
        @LanguageType
        void registerMarkdown(LanguageTypeBuilder<MarkdownSourceSet> builder) {
            builder.setLanguageName("Markdown")
        }
    }
    apply plugin: 'language-base'
    apply plugin: RegisterMarkdown

    model {
        md(MarkdownSourceSet) {
            generateIndex = true
        }
    }

#### `@ComponentBinaries` works for elements of `testSuites`

The `@ComponentBinaries` annotation can now be used to create binaries for any component type, regardless of its enclosing container. It means that it can be used to define binaries for components in `components`, like it used to, but also for those defined in `testSuites`, or any custom `ComponentSpec` container.

### Java software model improvements

#### Testing support

The Java software model now supports declaring a JUnit test suite as a software component, both as a standalone component or with a component under test. More information about declaring test suites can be found in the [userguide](userguide/java_software.html).

In addition, the changes in the software model infrastructure should make it easy to add support for new test frameworks.

#### Improved compile avoidance

This version of Gradle further optimizes on avoiding recompiling consuming libraries after non-ABI breaking changes. Since 2.9, if a library declares an API, Gradle creates a "[stubbed API jar](userguide/java_software.html)". This enables avoiding recompiling any consuming library if the application binary interface (ABI) of the library doesn't change. This version of Gradle extends this functionality to libraries that don't declare their APIs, speeding up builds with incremental changes in most Java projects, small or large. In particular, a library `A` that depend on a library `B` will not need to be recompiled in the following cases:

- a private method is added to `B`
- a method body is changed in `B`
- order of methods is changed in `B`

This feature only works for local libraries, not external dependencies. More information about compile avoidance can be found in the [userguide](userguide/java_software.html).

### Native software model improvements

#### Changes to native unit testing

By convention, when the CUnit plugin or the Google Test plugin is applied, test suites for components are created automatically. If you don't want the conventions to be automatically applied, you can opt-out and only apply the base test suite plugin, in which case you are required to provide the component under test explicitly, using the `testing $.components.someComponent` reference syntax.

The `cunit` plugin is now built on top of the `cunit-test-suite` plugin and applies the convention of creating a test suite for each native component automatically. Similarily, the `google-test` plugin is built on top of the `google-test-test-suite` plugin. This change should fix the issues with Gradle proactively creating test suites for components it should not. The native software model now uses the same pattern as the Java software model to define test suites.

#### Improved native header detection

In Gradle 2.10, compilation tasks for native languages were not considered out of date when a header file was added earlier in the include search path after a file with the same name had been found in a previous execution.

It's recommended that header files are included with a "namespace" to avoid naming conflicts.  For example, if you have a header "logger.h", you should put the header in a subdirectory and include it as "subdirectory/logger.h" instead.

See [GRADLE-3383](https://issues.gradle.org/browse/GRADLE-3383) for more details.

### IDE integration improvements

#### IDEA Plugin uses `sourceCompatibility` for each subproject to determine module and project language level

The Gradle 'idea' plugin can generate configuration files allowing a Gradle build to be opened and developed in IntelliJ IDEA. Previous versions of Gradle would only consider the `sourceCompatibility` setting on the _root_ project to determine the 'IDEA Language Level': this setting on any subprojects was not considered.

This behavior has been improved, so that the generated IDEA project will have a 'Language Level' matching the highest `sourceCompatibility` value for all imported subprojects. For a multi-project Gradle build that contains a mix of `sourceCompatibility` values, the generated IDEA module for a sub-project will include an override for the appropriate 'Language Level' where it does not match that of the overall generated IDEA project.

If a Gradle build script uses the DSL to explicitly specify `idea.project.languageLevel`, the `sourceCompatibility` level is _not_ taken into account. In this case only the generated IDEA project will contain a value for 'Language Level', and no module-specific overrides will be generated.

The generated values for 'Language Level' are used when creating the `.ipr` and `.iml` files for a Gradle project, as well as to populate the Tooling API model that is used by IntelliJ IDEA on Gradle project import.

#### Tooling API exposes language level on IDEA model

The `IdeaProject` and the `IdeaModule` model now exposes the Java language level via the <a href="javadoc/org/gradle/tooling/model/idea/IdeaProject.html#getJavaSourceSettings">`getJavaSourceSettings()`</a> method. IDE providers use this method to automatically determine the language level of a IDEA project and its associated Modules.

#### Tooling API exposes java runtime and target bytecode level on IDE models

The `IdeaProject` and the `IdeaModule` model now exposes the java sdk and the target bytecode version via the <a href="javadoc/org/gradle/tooling/model/idea/IdeaProject.html#getJavaSourceSettings">`getJavaSourceSettings()`</a> method. The target bytecode version for `IdeaModule` is derived from the <a href="groovydoc/org/gradle/api/plugins/JavaPluginConvention.html#getTargetCompatibility">`targetCompatibility`</a>
convention property.

The `EclipseProject` model now exposes the target java runtime and the target bytecode level via the <a href="javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html#getJavaSourceSettings">`getJavaSourceSettings()`</a> method. The target bytecode level is derived from the <a href="groovydoc/org/gradle/plugins/ide/eclipse/model/EclipseJdt.html#getTargetCompatibility">`eclipse.jdt.targetCompatibility`</a> property.

IDE providers use these new introduced methods to determine the target runtime and bytecode level information.

### Continuous build improvements

When introduced in Gradle 2.5, [continuous build](userguide/continuous_build.html) only observed changes that occurred after a build had completed. Continuous build will now trigger a rebuild when an input file is changed during build execution.

Following a build, if changes were detected, Gradle will report a list of file changes and begin execution of a new build.

### Support for controlling test execution order in TestNG

This version of Gradle adds support for TestNG preserveOrder and groupByInstances options to control test order execution. More information about these features can be found in the [userguide](userguide/java_plugin.html#test_execution_order).

New options can be enabled in the useTestNG block:

    test {
        useTestNG {
            preserveOrder true
            groupByInstances true
        }
    }

This feature was contributed by [Richard Bergoin](https://github.com/kenji21).

### Support for different test frameworks for Java projects in Build Init plugin

It is now possible to use [Spock framework](https://code.google.com/p/spock/) or [TestNG](http://testng.org/doc/index.html) instead of JUnit for Java projects in the [Build Init plugin](userguide/build_init_plugin.html) by using the following command:

    gradle init --type java-library --test-framework spock

or

    gradle init --type java-library --test-framework testng

This feature was contributed by [Dylan Cali](https://github.com/calid).

### Support for exclude information in Ivy publishing

The Ivy descriptor file generated by the ['ivy-publish'](userguide/publishing_ivy.html) plugin now includes dependency exclude information. Exclusions configured in your Gradle build script on project or external module dependencies will be included in the published _ivy.xml_ file.

This feature was contributed by [Eike Kohnert](https://github.com/andrena-eike-kohnert).

### Support for Twirl source sets to use Java default imports

Previously, when compiling Twirl source sets, Gradle would assume that Scala default imports should be used.  A developer can now specify that Java default imports should be used when compiling a Twirl source set.

    model {
        components {
            play {
                twirlTemplates {
                    defaultImports = TwirlImports.JAVA
                }
            }
        }
    }

## Fixed issues

## Potential breaking changes

### Excludes in published Ivy metadata

Dependency exclusions are now added to the published ivy.xml when using the 'ivy-publish' plugin. This may result in dependencies being resolved by consuming projects of newly published modules to change. If consuming projects depend on the excluded dependencies you may have to explicitly add these dependencies to the consuming project.

### Update to HttpClient 4.4.1

Gradle uses the Apache HttpComponents HttpClient library internally for features like dependency resolution and publication. This library has been updated from version 4.2.2 to 4.4.1. As part of this upgrade, certain system properties are no longer taken into account when creating clients used for resolving and publishing dependencies from HTTP repositories. Specifically, the 'http.keepAlive' and 'http.maxConnections' system properties are now ignored.

For more information regarding changes introduced in HttpClient 4.4.1 please see the HttpClient [release notes](http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.4.x.txt).

### Scala plugin no longer adds 'scalaConsole' tasks

Adding the 'scala' plugin to your build will no longer create 'scalaConsole' tasks which launch a Scala REPL from the Gradle build. This capability has been removed due to lack of documentation and support for running with the Gradle Daemon. If you wish to continue to have such a task as part of your build, you can explicitly configure a [`JavaExec`](dsl/org.gradle.api.tasks.JavaExec.html) task to do so.

### Eclipse Plugin adds explicit java target runtime to Classpath

The `.classpath` file generated via `eclipseClasspath` task provided by the Eclipse Plugin now points to an explicit Java Runtime Version instead of using the default JRE configured in the Eclipse IDE. The naming convention follows the Eclipse defaults and uses the `targetCompatibility` convention property to calculate the default java runtime name.

To tweak the name of the Java runtime to use, the name can be configured via

    eclipse {
        jdt {
            javaRuntimeName = "JavaSE-1.8"
        }
    }

### Changed return type for `EclipseProject.getJavaSourceSettings()`

In Gradle 2.10, this method returned an empty Eclipse-specific subtype of `JavaSourceSettings`: `EclipseJavaSourceSettings`. This method now returns the core base type instead.

The interface `EclipseJavaSourceSettings` has been removed.

### Non-stop cycle when using continuous build

When started in continuous build, Gradle will begin watching changes to task inputs just before a task executes. If a task modifies its own inputs, this can lead to a build cycle where each build triggers another build.

You can diagnose this sort of problem by looking at the list of files that changed at the end of the build and find the task that has those files as inputs.  Changing the logging level to [`--info`](current/userguide/logging.html#logLevelCommandLineOptions) can make it easier to identify which input files cause which tasks to become out-of-date.

### SourceTask adds injected getPatternSetFactory method

An injected getPatternSetFactory method has been added to the `org.gradle.api.tasks.SourceTask` class. This is a possible breaking change for unit tests of tasks that extend the SourceTask class.

### Changes to Play standalone distributions

#### Names of dependency jars

When copying dependencies into the Play distribution's `lib` directory, we now rename all jar files to include `group` or `project path` information.

#### Tar distribution task

The Gradle Play plugin now adds a `Tar` task to build standalone Play distributions (`createPlayBinaryTarDist`). A tar and zip distribution will be created when executing `dist`.

#### Zip distribution task

The name of the `Zip` task to create standalone Play distributions is now `createPlayBinaryZipDist`.

#### Name of distribution archives

If you would like to have a different archive name (by default, `playBinary.zip` or `playBinary.tar`), you must configure the `baseName` property for the `Zip` or `Tar` tasks. When determining the final archive name, Gradle will concatenate the `baseName` and `extension`.

For tar files, Gradle will automatically switch to using `.tgz` or `.tbz2` when the `compression` property is changed.

Previously, you could directly set the `archiveName`.  `version`, `appendix` and `classifier` are still ignored when determining the archive name.

### Change to default ruleset name for PMD Plugin

The value for `PmdExtension` is now `["java-basic"]` instead of `["basic"]`. This matches the value for the default version of PMD used by Gradle (5.2.3). Gradle will still convert 'java-basic' to 'basic' when a pre-5.0 version of PMD is used, so this change will only effect builds that use PMD 4.x and add additional rulesets to the list provided by the `PmdExtension`.

### Software model changes

- Deprecated `CollectionBuilder` interface removed.
- Deprecated `ManagedSet` interface removed.
- Interface `JvmBinaryTasks` removed, replaced with `JarBinarySpec.TasksCollection`, for consistency with the native binaries.
- Method `JvmBinarySpec.getTasks()` removed, replaced with `JarBinarySpec.getTasks()`.
- The `assemble` task now builds only those binaries for components defined in `components`. It does not build binaries for test suites defined in `testSuites`.
- The `LanguageBasePlugin` has been split into two separate plugins: `LanguageBasePlugin` and `BinaryBasePlugin`.
- The `LanguageBasePlugin` no longer applies the full component model.
- The `@LanguageType` annotation implicitly applies only the `LanguageBasePlugin`.
- The `@BinaryType` annotation implicitly applies only the `BinaryBasePlugin`.
- Model properties now follow the JavaBean specification and thus are on par with Groovy. This means that:
  - Properties are now addressable with coherent names in the DSL and by model path.
  - Properties with getters like `getcCompiler()` are now allowed and addressable via `cCompiler`.
  - Properties with getters like `getCFlags()` are now addressable via `cFlags` instead of the erroneous `CFlags`.
  - Properties with getters like `getURL()` are now addressable via `URL` instead of the erroneous `uRL`.

### TestKit indicates compatibility for target Gradle version

Gradle 2.9 exposes methods through the `GradleRunner` API for providing a target Gradle distribution used to executed the build. There are known, functional limitations of TestKit for particular Gradle versions. If a certain feature is not supported, TestKit throws an exception. Please check the [user guide](userguide/test_kit.html#sub:test-kit-compatibility) for an overview of known TestKit limitations.

### File details are read eagerly when creating a `FileVisitDetails`

Prior to Gradle 2.10, most implementations would delegate calls to `FileVisitDetails.getLastModified()` and `FileVisitDetails.getSize()` to the actual visited file. Gradle 2.10 introduced an optimisation where these values were read eagerly for some implementations of `FileVisitDetails` on some Java versions.

In Gradle 2.11, this behaviour is consistent across all Java versions and operating systems. The values for `lastModified` and `size` are determined eagerly when visiting a File tree. This provides a more consistent, reliable API and permits Gradle to make optimizations when reading these values.

### API Classes

- `Specs.or()` has been deprecated and will be removed in Gradle 3.0. You should use `Specs.union()` instead.
- `Specs.and()` has been deprecated and will be removed in Gradle 3.0. You should use `Specs.intersect()` instead.
- `Specs.not()` has been deprecated and will be removed in Gradle 3.0. You should use `Specs.negate()` instead.

### ApiJar task changes

- The task has been improved to accept multiple files instead of a single directory as input and as a consequence the `runtimeClassesDir` property has been removed. Inputs should be configured via the usual `task.inputs` mechanism.
- The separate output configuration properties `destinationDir` and `archiveName` have been consolidated into the single property `outputFile`.
- The `apiClassesDir` property was no longer necessary and has been removed.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Dylan Cali](https://github.com/calid) - Add support for Spock and TestNG frameworks for Java projects in [Build Init plugin](userguide/build_init_plugin.html)
- [Niklas Baudy](https://github.com/vanniktech) - PMD plugin code cleanup
- [Richard Bergoin](https://github.com/kenji21) - Test coverage for NTLM support
- [Illya Gerasymchuk](https://github.com/iluxonchik) - Fix typos in Windows batch scripts
- [Eike Kohnert](https://github.com/andrena-eike-kohnert) - Support for publishing dependency exclusions in Ivy descriptors
- [Johnny Lim](https://github.com/izeye) - Documentation improvements
- [Christopher O'Connell](https://github.com/lordoku) - Remove 'scalaConsole' task
- [Tobias Riemenschneider](https://github.com/riemenschneider) - Add support for TestNG's preserveOrder and groupByInstances options
- [Jochen Schalanda](https://github.com/joschi) - Support for configuring Twirl source sets to use Java default imports
- [Sebastian Schuberth](https://github.com/sschuberth) - Simplify LoggingCommandLineConverter
- [Martin Steiger](https://github.com/msteiger) - Eclipse and IDEA tasks warn on unresolvable dependencies
- [Günther Grill](https://github.com/guenhter) - Internal service registry improvements
- [Chris Purcell](https://github.com/cjp39) - Documentation improvements
- [Ryan Niehaus](https://github.com/ryanniehaus) - InstallExecutable task uses target platform operating system

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
