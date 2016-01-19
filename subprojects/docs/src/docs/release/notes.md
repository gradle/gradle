The Gradle team is pleased to bring you Gradle 2.11. This release delivers significant improvements to the new [software model](userguide/software_model.html), together with improvements to IDE integration and continuous build.

The software model is the future of Gradle. The core software model is the basis of the native language and Play framework support in Gradle, and we are working intensively to bring full Java support. With this release, the new Java plugins support testing with JUnit and do a better job in compile avoidance. As well as developing our Java support, we continue to invest in the software model infrastructure, so support for developing any plugin with the new software model also got better.

Existing Java projects also benefit from Gradle 2.11. Improved IDE integration means that developing a Gradle project in IntelliJ IDEA or Eclipse is even better, with fewer tweaks to the IDE configuration required. These improvements encompass both the generated project files and the [Tooling API](userguide/embedding.html) used by IDEs to import Gradle projects.

By detecting changes that occur during build execution, [Continuous build](userguide/continuous_build.html) has become more dependable. We encourage users to try out this cool feature, which can really enhance the development experience with Gradle.

No Gradle release would be complete without contributions from the wonderful Gradle community. In Gradle 2.11, these contributions include:

- Controlling TestNG execution order with the [Java plugin](userguide/java_plugin.html).
- Specifying different test frameworks for a generated Java project using the [Build Init plugin](userguide/build_init_plugin.html).
- Specifying the Java default imports for a Twirl source set when developing with the [Play plugin](userguide/play_plugin.html).
- Publishing 'exclude' information to Ivy files using the [ivy-publish plugin](userguide/publishing_ivy.html).

The full list of contributions is below.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Better support for developing plugins with the software model

#### Plugins can use managed types for custom source set types

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

#### A `@ComponentBinaries` rule can create binaries for any `ComponentSpec` instance

The `@ComponentBinaries` annotation can now be used to create binaries for any component type, regardless of its enclosing container. It means that it can be used to define binaries for components in `components`, like it used to, but also for those defined in `testSuites`, or any custom `ComponentSpec` container.

### Improved building and testing Java libraries with the Java software model

#### Testing support

The Java software model now supports declaring a JUnit test suite as a software component, both as a standalone component or with a component under test. More information about declaring test suites can be found in the [userguide](userguide/java_software.html).

In addition, the changes in the software model infrastructure should make it easy to add support for new test frameworks.

#### Better compile avoidance

This version of Gradle further optimizes on avoiding recompiling consuming libraries after non-ABI breaking changes. Since 2.9, if a library declares an API, Gradle creates a "[stubbed API jar](userguide/java_software.html)". This enables avoiding recompiling any consuming library if the application binary interface (ABI) of the library doesn't change. This version of Gradle extends this functionality to libraries that don't declare their APIs, speeding up builds with incremental changes in most Java projects, small or large. In particular, a library `A` that depend on a library `B` will not need to be recompiled in the following cases:

- a private method is added to `B`
- a method body is changed in `B`
- order of methods is changed in `B`

This feature only works for local libraries, not external dependencies. More information about compile avoidance can be found in the [userguide](userguide/java_software.html).

### Enhanced support for developing Java projects in IntelliJ IDEA and Eclipse

Gradle supports IDE-centric development via both generated project files and direct IDE import using the Gradle Tooling API.

This release improves on this support for all Java projects built with Gradle, allowing the IDE view of a project to more closely model the Gradle configuration. Improvements to generated project files are available to try out immediately, while improvements to direct IDE import will require updates to IntelliJ IDEA and Buildship (Gradle support for Eclipse).

#### Generated IDEA files include correct module and project Java language level

The Gradle 'idea' plugin generates configuration files for a Gradle build to be opened and developed in IntelliJ IDEA. Previous versions of Gradle only considered the <a href="dsl/org.gradle.api.Project.html#org.gradle.api.Project:sourceCompatibility">`sourceCompatibility`</a> setting on the _root_ project to determine the 'IDEA Language Level': this setting on any subprojects was not considered.

This behavior has been improved, so that the generated IDEA project will have a 'Language Level' matching the highest `sourceCompatibility` value for all imported subprojects. For a multi-project Gradle build that contains a mix of `sourceCompatibility` values, the generated IDEA module for a sub-project will include an override for the appropriate 'Language Level' where it does not match that of the overall generated IDEA project.

If a Gradle build script uses the DSL to explicitly specify `idea.project.languageLevel`, the `sourceCompatibility` level is _not_ taken into account. In this case only the generated IDEA project will contain a value for 'Language Level', and no module-specific overrides will be generated.

The generated values for 'Language Level' are used when creating the `.ipr` and `.iml` files for a Gradle project, as well as to populate the Tooling API model that is used by IntelliJ IDEA on Gradle project import (see below).

#### Tooling API provides Java language settings for IntelliJ IDEA

The Tooling API exposes `IdeaProject` and the `IdeaModule` models that are used when importing a Gradle build into IntelliJ IDEA. These models not include information on the Java language settings that represent the Gradle configuration for an imported build, and should allow an imported Gradle project in the IDE to more closely match the build configuration.

Java language settings can be accessed via <a href="javadoc/org/gradle/tooling/model/idea/IdeaProject.html#getJavaLanguageSettings--">`IdeaProject.getJavaLanguageSettings()`</a> and <a href="javadoc/org/gradle/tooling/model/idea/IdeaModule.html#getJavaLanguageSettings--">`IdeaModule.getJavaLanguageSettings()`</a>.

#### Generated Eclipse `.classpath` files specify the Java runtime used

The `.classpath` file generated via `eclipseClasspath` task provided by the Eclipse Plugin now points to an explicit Java runtime version rather than using the default JRE configured in the Eclipse IDE. The naming convention follows the Eclipse defaults and uses the `targetCompatibility` convention property from the `java` plugin to determine the default java runtime name.

To the name of the Java runtime to use can be configured via the `javaRuntimeName` property on the `EclipseJdt` model.

    eclipse {
        jdt {
            javaRuntimeName = "JavaSE-1.8"
        }
    }

#### Tooling API provides more Java language settings for Eclipse

The <a href="javadoc/org/gradle/tooling/model/eclipse/EclipseJavaSourceSettings.html">Java language settings</a> obtained for an `EclipseProject` from the Tooling API now include the target bytecode version and the build JDK for a Java project. These values can be used to better configure the Eclipse project created when when importing these projects into Eclipse. The target bytecode level is derived from the <a href="groovydoc/org/gradle/plugins/ide/eclipse/model/EclipseJdt.html#getTargetCompatibility">`eclipse.jdt.targetCompatibility`</a> property, while the JDK value indicates the JDK used by Gradle to build the project.

Look for improved support for importing Java projects in an upcoming release of <a href="http://projects.eclipse.org/projects/tools.buildship">Buildship</a>.

### Continuous build detects changes that occur during build execution

When introduced in Gradle 2.5, [continuous build](userguide/continuous_build.html) only observed changes that occurred after a build had completed. Continuous build will now trigger a rebuild when an input file is changed during build execution.

The rebuild is scheduled to start as soon as the currently executing build is complete. No attempt is made to cancel the currently executing build.

Following a build, if changes were detected, Gradle will report a list of file changes and begin execution of a new build.

### Support for controlling test execution order in TestNG

This version of Gradle adds support for TestNG preserveOrder and groupByInstances options to control test order execution. More information about these features can be found in the [userguide](userguide/java_plugin.html#test_execution_order).

New options can be enabled in the `useTestNG` block:

    test {
        useTestNG {
            preserveOrder true
            groupByInstances true
        }
    }

This feature was contributed by [Richard Bergoin](https://github.com/kenji21).

### Test framework can be specified when bootstrapping a Java project

The [Build Init plugin](userguide/build_init_plugin.html) allows the bootstrapping of Gradle project via the built-in `init` task. It is now possible to use [Spock framework](https://code.google.com/p/spock/) or [TestNG](http://testng.org/doc/index.html) instead of JUnit for Java projects generated in this way. Specify the test framework as follows:

    gradle init --type java-library --test-framework spock

or

    gradle init --type java-library --test-framework testng

This feature was contributed by [Dylan Cali](https://github.com/calid).

### Published Ivy descriptor files include configured exclusions

The Ivy descriptor file generated by the ['ivy-publish'](userguide/publishing_ivy.html) plugin now includes dependency exclude information. Exclusions configured in your Gradle build script on project or external module dependencies will be included in the published _ivy.xml_ file.

This feature was contributed by [Eike Kohnert](https://github.com/andrena-eike-kohnert).

### Configure Twirl source sets to use Java default imports

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

### Separate convention plugins for native unit testing

By convention, the [CUnit](userguide/native_software.html#native_binaries:cunit) and [Google Test](userguide/native_software.html#native_binaries:google_test) plugins will create and configure a test suite for each component automatically. It is now possible to opt-out of this conventional behaviour by applying the a base 'test-suite' plugin, with the component under test being specified explicitly using the `testing $.components.someComponent` reference syntax.

The `cunit` plugin is now built on top of the `cunit-test-suite` plugin and applies the convention of creating a test suite for each native component automatically. Similarly, the `google-test` plugin is built on top of the `google-test-test-suite` plugin. This change allows finer-grained control over the creation of native test suites, addressing issues where Gradle proactively creates test suites for components it should not.

With this change, the native software model and Java software model use the same pattern for defining test suites.

### Changes to Play standalone distributions

As a result of some user feedback, some changes have been made to the outputs resulting from the `stage` and `dist` task in the incubating Play framework plugins.

#### Unique names for jar files within the `lib` directory

When copying dependencies into the Play distribution's `lib` directory, we now rename all jar files to include `group` or `project path` information. This fixes an issue where a Play component depends on multiple projects having the same name.

#### Tar distribution task

The Gradle Play plugin now adds a `Tar` task to build standalone Play distributions (`createPlayBinaryTarDist`). A tar and zip distribution will be created when executing `dist`.

The name of the `Zip` task to create standalone Play distributions is now `createPlayBinaryZipDist`.

#### Name of distribution archives

If you would like to have a different archive name (by default, `playBinary.zip` or `playBinary.tar`), you must configure the `baseName` property for the `Zip` or `Tar` tasks. When determining the final archive name, Gradle will concatenate the `baseName` and `extension`.

For tar files, Gradle will automatically switch to using `.tgz` or `.tbz2` when the `compression` property is changed.

Previously, it was possible to directly set the `archiveName` for the generated Zip. This property is now ignored. The `version`, `appendix` and `classifier` properties are still ignored when calculating the archive name.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

- `Specs.or()` has been deprecated and will be removed in Gradle 3.0. You should use `Specs.union()` instead.
- `Specs.and()` has been deprecated and will be removed in Gradle 3.0. You should use `Specs.intersect()` instead.
- `Specs.not()` has been deprecated and will be removed in Gradle 3.0. You should use `Specs.negate()` instead.

## Potential breaking changes

### Exclusions now published in Ivy module descriptors

Dependency exclusions are now added to the published ivy.xml when using the 'ivy-publish' plugin. This may result in dependencies being resolved by consuming projects of newly published modules to change. If consuming projects depend on the excluded dependencies you may have to explicitly add these dependencies to the consuming project.

### Update to HttpClient 4.4.1

Gradle uses the Apache HttpComponents HttpClient library internally for features like dependency resolution and publication. This library has been updated from version 4.2.2 to 4.4.1. As part of this upgrade, certain system properties are no longer taken into account when creating clients used for resolving and publishing dependencies from HTTP repositories. Specifically, the 'http.keepAlive' and 'http.maxConnections' system properties are now ignored.

For more information regarding changes introduced in HttpClient 4.4.1 please see the HttpClient [release notes](http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.4.x.txt).

### Scala plugin no longer adds 'scalaConsole' tasks

Adding the 'scala' plugin to your build will no longer create a 'scalaConsole' task to launch a Scala REPL from the Gradle build. This capability has been removed due to lack of documentation and support for running with the Gradle Daemon. If you wish to continue to have such a task as part of your build, you can explicitly configure a [`JavaExec`](dsl/org.gradle.api.tasks.JavaExec.html) task to do so.

### Possible unterminated build loop when using continuous build

When a continuous build is running, Gradle will begin monitoring changes to a task inputs just before that task executes. If a task modifies its own inputs, or the inputs of a task that it depends on, this can lead to a build cycle where each build triggers another build.

This problem can be diagnosed by inspecting the list of files reported when the next build is triggered.  Changing the logging level to [`--info`](current/userguide/logging.html#logLevelCommandLineOptions) can make it easier to identify which input files cause which tasks to become out-of-date.

### Injected `getPatternSetFactory` method added to SourceTask

An injected `getPatternSetFactory()` method has been added to the `org.gradle.api.tasks.SourceTask` class. This is a possible breaking change for unit tests of tasks that extend the SourceTask class.

### Change to default ruleset name for PMD Plugin

The value for `PmdExtension` is now `["java-basic"]` instead of `["basic"]`. This matches the value for the default version of PMD used by Gradle (5.2.3). Gradle will still convert 'java-basic' to 'basic' when a pre-5.0 version of PMD is used, so this change will only effect builds that use PMD 4.x and add additional rulesets to the list provided by the `PmdExtension`.

### File details are read eagerly when creating a `FileVisitDetails`

Prior to Gradle 2.10, most implementations would delegate calls to `FileVisitDetails.getLastModified()` and `FileVisitDetails.getSize()` to the actual visited file. Gradle 2.10 introduced an optimisation where these values were read eagerly for some implementations of `FileVisitDetails` on some Java versions.

In Gradle 2.11, this behaviour is consistent across all Java versions and operating systems. The values for `lastModified` and `size` are determined eagerly when visiting a File tree. This provides a more consistent, reliable API and permits Gradle to make optimizations when reading these values.

### TestKit indicates compatibility for target Gradle version

Gradle 2.9 exposes methods through the `GradleRunner` API for providing a target Gradle distribution used to executed the build. There are known, functional limitations of TestKit for particular Gradle versions. If a certain feature is not supported, TestKit throws an exception. Please check the [user guide](userguide/test_kit.html#sub:test-kit-compatibility) for an overview of known TestKit limitations.

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
- ApiJar task changes:
    - The task has been improved to accept multiple files instead of a single directory as input and as a consequence the `runtimeClassesDir` property has been removed. Inputs should be configured via the usual `task.inputs` mechanism.
    - The separate output configuration properties `destinationDir` and `archiveName` have been consolidated into the single `outputFile` property.
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
