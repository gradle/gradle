The Gradle team is pleased to announce Gradle 3.0.

Performance continues to be a focus for the Gradle team and the third major release reflects this.  The [Gradle Daemon](userguide/gradle_daemon.html) is a key performance enhancer, making builds _up to 75% faster_, but it needed to be explicitly enabled in previous versions of Gradle.  This is no longer necessary as the Daemon is now _enabled by default_ in 3.0.  We've put a lot of effort into fixing the most significant issues with the Gradle Daemon, especially on Windows platforms. We have also been working hard to make the Gradle Daemon aware of its health and impact to the system it's running on and use this information for self-healing actions as well as better daemon status reporting.  The Gradle Daemon is the foundation for a great Gradle experience.

Ever wished for better IDE support when writing Gradle build scripts?  This release provides the first support for [Gradle Script Kotlin](https://github.com/gradle/gradle-script-kotlin), 
which is a Kotlin-based build language for Gradle scripts.  Its deep integration with both IDEA and Eclipse provides many of the things you would expect from an IDE such as auto-completion, 
refactoring, navigation to source, and more.  Groovy is still the primary build language for Gradle scripts and will always be supported, but we are working intensely to make Gradle Script 
Kotlin fully production ready by the end of the year in order to provide 
the best possible development experience to Gradle users.  See [Chris Beams's blog post](https://gradle.org/blog/kotlin-meets-gradle/) for more information about this exciting new feature.

Additionally, Gradle 3.0 provides support for running on the latest Java 9 EAP builds.  Users can also build and run tests using these early versions of JDK 9, but there are some limitations.
Check out the section on [Java 9 support](#java9-support) below for more details.

With the release of Gradle 3.0, it's a good time to reflect on the progress we've made over the last 2 years. Check out some of [the improvements](#all-improvements) since Gradle 2.0. 
Lots of reasons to upgrade!

We're also pleased to make available a draft of our [new Performance Guide](https://gradle.github.io/performance-guide). This is intended to be a short guide that shows you how to 
dramatically improve your build performance in the time it takes to eat lunch. Check it out and please provide any feedback via the guide's 
[GitHub Issues](https://github.com/gradle/performance-guide/issues).

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Improved Gradle Daemon, now enabled by default

As mentioned above, the Gradle Daemon is now more robust, efficient and self-aware.  It has now been _enabled by default_ to make your builds [faster than ever](http://gradle.org/blog/gradle-3-0-m1-unleash-the-daemon/).  If for some reason you encounter problems, it can always [be disabled](userguide/gradle_daemon.html#daemon_faq) if necessary. 

### View the status of Gradle Daemons

Before Gradle 3.0, there was no easy way to determine the status of a running Gradle Daemon or why a Daemon might have stopped.  With this release, you
can now check the status of running and recently stopped daemons using the `--status` command and get better insight into the state your Gradle environment.  

    $> gradle --status
       PID STATUS   INFO
     43536 BUSY     3.0
     43542 IDLE     3.0
     43418 STOPPED  (stop command received)
     43366 STOPPED  (stop command received)

Note that the status command currently does not list Gradle Daemons with version < 3.0. More details are available in the [User Guide](userguide/gradle_daemon.html#status).

### View Daemon information in Gradle Cloud Services

Information about the Gradle Daemon is now being captured in your Build Scans and can be viewed in [Gradle Cloud Services](https://gradle.com/).  You can see information such as the number of builds that have been run in the Daemon, the number of Daemons that were running on the system when the build occurred, as well as reasons for why a Daemon may have been stopped.  Along with all of the other great information in a Build Scan, this captures the state of the Daemon at the time a build executes and gives you insight into factors that might have affected that build's performance.  If you haven't created a [Build Scan](https://scans.gradle.com/s/pqmetew4bnofi) yet, [give it a try](https://scans.gradle.com/setup/step-1)!
 
### Better IDE support for writing build scripts

The Gradle team and JetBrains have been collaborating to provide the best possible IDE support for writing Gradle build scripts. Gradle 3.0 supports version 0.3.0 of  
[Gradle Script Kotlin](https://github.com/gradle/gradle-script-kotlin), a statically typed build language based on [Kotlin](http://kotlinlang.org/).  

So what does a Gradle Script Kotlin build look like?  Here's an example:

    import org.gradle.api.tasks.*
    
    apply<ApplicationPlugin>()
    
    configure<ApplicationPluginConvention> {
        mainClassName = "org.gradle.samples.HelloWorld"
    }
    
    repositories {
        jcenter()
    }
    
    dependencies {
        compile("commons-lang:commons-lang:2.4")
        testCompile("junit:junit:4.12")
    }
    
    task<Copy>("copyConfig") {
        from("src/main/conf")
        into("build/conf")
        exclude("**/*.old")
        includeEmptyDirs = false
    }

This looks very similar to a Groovy build script, but when you load it in either IDEA or Eclipse, suddenly development is a much better experience.  You now have code auto-completion, 
refactoring, and other features you would expect from an IDE in your `build.gradle.kts`.  You can still use all of your plugins written in Java or Groovy but also take advantage of the power of first-class development 
support.  Take a look and [give it a try](https://github.com/gradle/gradle-script-kotlin/tree/master/samples)!

We'll continue to enhance this support in future versions of Gradle, so if you discover any issues, please let us know via the project's [GitHub Issues](https://github.com/gradle/gradle-script-kotlin/issues).


### Parallel task execution improvements

Gradle 3.0 makes it easier to manage the resources that Gradle uses.  The `Test` task type now honors the `max-workers` setting for the test processes that are started. This means that Gradle will 
now run at most `max-workers` tasks and test processes at the same time.

If you need to return to the old behavior, you can limit the number of forked processes:

    tasks.withType(Test) {
         maxParallelForks = 1
    }

<a id="java9-support" name="java9-support"></a>
### Initial Java 9 support

Gradle 3.0 contains initial support for running Gradle on Java 9 as well as compiling, testing and running Java 9 applications from Gradle.

Preliminary support for the JDK 9 `-release` compiler flag has been added as well. It can be specified via
[compilerArgs](dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:compilerArgs), e.g.

    compileJava.options.compilerArgs.addAll(['-release', '7'])

The following plugins have known issues with Java 9:

- [PMD plugin](userguide/pmd_plugin.html): The latest version of [PMD](https://pmd.github.io/pmd-5.4.1/pmd-java/) (5.5.1) can run on Java 9 but does not yet support analysis of Java 9 Bytecode.  
- [Jetty plugin](userguide/jetty_plugin.html): The version of [Jetty](http://www.eclipse.org/jetty/) used with this plugin does not support Java 9.
- [Scala plugin](userguide/scala_plugin.html): The [Zinc compiler](https://github.com/typesafehub/zinc) does not currently support Java 9.
- [FindBugs plugin](userguide/findbugs_plugin.html): The latest release of [FindBugs](http://findbugs.sourceforge.net/) (3.0.1) does not support Java 9.
- [OSGi plugin](userguide/osgi_plugin.html): The latest version of [BND](http://bnd.bndtools.org/) does not work with Java 9.
- [Jacoco](userguide/jacoco_plugin.html): Starting from JDK 9u127 [Jacoco](http://www.eclemma.org/jacoco/) stopped working with Java 9.

When using [continuous build](userguide/continuous_build.html) on Java 9, the following constraints apply due to class access restrictions related to Jigsaw:

- On Mac OS X, Gradle will poll for file changes every 10 seconds instead of every 2 seconds.
- On Windows, continuous build may be slow to detect changes on very large projects.

Additionally, when publishing to S3 backed Maven and Ivy repositories, `-addmods java.xml.bind` will have to be added to the JVM parameters when using Java 9.

    GRADLE_OPTS="-addmods java.xml.bind '-Dorg.gradle.jvmargs=-addmods java.xml.bind'"

Please report any issues you may experience running or building with Java 9 on the [Gradle Forums](https://discuss.gradle.org/).

### Improved `plugins` DSL

There are times when it might be useful to resolve a plugin without actually applying it to the current project, for example:

- When you only want to reuse a task class from the plugin
- When you only want to apply the plugin to subprojects of the current project

Previously, this could only be done with the `buildscript` DSL syntax, but this is now possible via the `plugins` DSL, too:

    plugins {
        id 'my.special.plugin' version '1.0' apply false
    }

    subprojects {
        if (someCondition) {
            apply plugin 'my.special.plugin'
        }
    }

Note the `apply false` at the end of the plugin declaration.  This instructs Gradle to resolve the plugin and make it available on the classpath, but not to apply it.

### Incremental build improvements

#### Tracking changes in the task's code

A task is up-to-date as long as its inputs and outputs remain unchanged. Previous versions of Gradle did not consider _the code_ of the task as part of the inputs. This could lead to incorrect behavior where the implementation of a task could change but the task might still be marked as `UP-TO-DATE` even though it would actually create different outputs.  Gradle now recognizes when a task, its actions, or its dependencies change between executions and properly marks the task as out-of-date.

#### Tracking changes in the order of input files

Gradle now recognizes changes in the order of files for classpath properties as a reason to mark a task like `JavaCompile` out-of-date. The new `@OrderSensitive` annotation can be used on task input properties to turn this feature on in custom tasks.

#### New task property annotations

Since 3.0, every task property should specify its role via one of the task property annotations:

* an input or output of the task (`@Input`, `@Nested`, `@InputDirectory`, `@OutputFile` etc.)
* an injected service (`@Inject`)
* a property that influences only the console output of the task (the new `@Console` annotation)
* an internal property that should not be considered for up-to-date checks (the new `@Internal` annotation)

When using the [`java-gradle-plugin`](https://docs.gradle.org/current/userguide/javaGradle_plugin.html), a warning is printed during validation for any task property that is not annotated.

#### Tracking properties for input and output files

From now on Gradle tracks which property each input and output file belongs to. With this improvement it can now recognize when files are moved between properties. Registering the property name works automatically for task input and output properties annotated with `@InputFiles`, `@OutputFile` etc.

Input and output files registered via `TaskInputs.files()`, `TaskOutputs.dir()` and similar methods have a new mechanism to register the property name:
 
    task example {
        inputs.file "input.txt" withPropertyName "inputFile"
    }

### Improvements to the `eclipse-wtp` plugin

Before Gradle 3.0, the `eclipse-wtp` plugin defined external dependencies for a Java project in the WTP component descriptor. This lead to the issues detailed in 
[GRADLE-2123](https://issues.gradle.org/browse/GRADLE-2123). This has been fixed so that dependencies are now generated in the proper metadata locations according to the
type of project being configured.

Additionally, the `eclipse-wtp` plugin now fully leverages Gradle's dependency resolution engine. As a result, dependency customisations such as substitution rules and 
forced versions work with WTP projects.

Lastly, if a project applies the `war` or `ear` plugins, applying the `eclipse` plugin now also applies `eclipse-wtp`. This makes configuration simpler, especially when
using [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship).

### New features in the `EclipseProject` model.

The `EclipseProject` model has been enhanced with many new features:

- The [EclipseSourceDirectory](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html) exposes the following information:
    - exclude and include patterns: [getExcludes()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getExcludes%28%29) and [getIncludes()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getIncludes%28%29),
    - classpath attributes: [getClasspathAttributes()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getClasspathAttributes%28%29),
    - output folder: [getOutput()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getOutput%28%29).
- The classpath container definition is available via the [EclipseProject.getClasspathContainers()](javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html#getClasspathContainers%28%29) method.
- The project output location is available via the [EclipseProject.getOutputLocation()](javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html#getOutputLocation%28%29) method.
- All classpath entries (project and external dependencies, classpath containers and source folders) expose their access rules via [EclipseClasspathEntry.getAccessRules()](javadoc/org/gradle/tooling/model/eclipse/EclipseClasspathEntry.html#getAccessRules%28%29).

This allows Tooling API clients (such as [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship)) to provide more robust and complete IDE integration.

### Plugin library upgrades

Several libraries that are used by Gradle plugins have been upgraded:

- The OSGi plugin has been upgraded to use version 3.2.0 of the BND library.
- The Jacoco plugin has been upgraded to use Jacoco version 0.7.7.201606060606 by default.
- The PMD plugin has been upgraded to use PMD version 5.5.1 by default.
- The CodeNarc plugin has been upgraded to use CodeNarc version 0.25.2 by default.
- The Groovy version has been updated to 2.4.7. 
 
<a id="all-improvements" name="all-improvements"></a>
### Improvements since Gradle 2.0

A lot has changed since Gradle 2.0 was released in July of 2014.  First of all, performance has been improved dramatically in all phases of the build, including configuration time, build
script compilation, incremental builds and native compilation, as well as test execution and report generation to name a few.  We've improved the Daemon significantly, adding performance 
monitoring and resource awareness, fixing known issues, and ultimately enabling it by default so that all builds experience the performance gains it brings to the table.  Gradle 3.0 
represents a significantly faster and more efficient Gradle than it was two years ago.

We've also made good strides in improving the experience of plugin development.  The [Gradle TestKit](userguide/test_kit.html) is an out-of-the-box toolkit for functionally testing your Gradle plugins.
The [Plugin Development Plugin](userguide/javaGradle_plugin.html) helps you set up your plugin project by adding common dependencies to the classpath and providing validation of the plugin metadata
when building the archive.  Finally, the [Plugin Publishing Plugin](https://plugins.gradle.org/docs/publish-plugin) helps you to publish your plugins to the [Gradle Plugin Portal](https://plugins.gradle.org/) 
and share them with the rest of the community.

Dependency Management has gotten some love, too.  We've added [component selection rules](userguide/dependency_management.html#component_selection_rules), 
[module replacement rules](userguide/dependency_management.html#sec:module_replacement), and [dependency substitution rules](userguide/dependency_management.html#dependency_substitution_rules).  We've 
provided support for S3 repositories as well as configurable HTTP authentication, including preemptive authentication.  We've even added support for compile-only dependencies.  Publishing dependencies 
is also more powerful and you can now publish to S3 and SFTP repositories, implement Maven or Ivy dependency exclusions, as well as publish Ivy extra attributes in 
the artifact metadata.  You can even publish your plugins to a private repository and then [resolve them using the plugins DSL](userguide/plugins.html#sec:custom_plugin_repositories).

On the developer experience side of the house, you can now run [continuous builds](userguide/continuous_build.html), where Gradle actively detects changes to the inputs of your
tasks and proactively re-executes the build when changes occur.  Our Tooling API is now considerably better with support for build cancellation, build progress events, and the ability to run specific test classes or methods.  These improvements
have all contributed to the release of [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship) which provides first-class support for building, testing and running 
Gradle projects in Eclipse.

There's been substantial work on the plugins delivered with the Gradle distribution, too.  We've added the ability to build, test and run applications using the [Play Framework](userguide/play_plugin.html).
Support for [Native builds](userguide/native_software.html) continues to improve with support for parallel compilation, cross compilation and pre-compiled headers.  We've also introduced a DSL for 
declaring test suites and added support for testing native components with Google Test.  We've also continued to evolve [the Software Model](userguide/software_model_concepts.html) and 
[rule based model configuration](userguide/software_model.html) that these plugins are built on.  It is now possible to configure the model through DSL and view component and model reports as well as
create new types of rules such as validation and defaults rules.  

Gradle 3.0 represents a significant improvement over Gradle 2.0 in terms of functionality, performance and experience.  Looking forward, we'll continue to work on making Gradle the best build system on the 
planet, but for now, we hope you enjoy using 3.0 as much as we've enjoyed working on it!

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
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

### Chaining `TaskInputs` and `TaskOutputs` methods

Chaining the following method calls is now deprecated:

* `TaskInputs.dir()`
* `TaskInputs.file()`
* `TaskInputs.files()`
* `TaskOutputs.dir()`
* `TaskOutputs.file()`
* `TaskOutputs.files()`

With Gradle 3.0, the following now produces a deprecation warning:

    task myTask {
        inputs.file("input1.txt").file("input2.txt")
    }

> The chaining of the `file(Object)` method has been deprecated and is scheduled to be removed in Gradle 4.0. Please use the `file(Object)` method on `TaskInputs` directly instead.

### Jetty Plugin

The [Jetty plugin](userguide/jetty_plugin.html) has been deprecated and will be removed in Gradle 4.0.
You may wish to use the more feature-rich [Gretty plugin](https://github.com/akhikhl/gretty) instead of the Jetty plugin.

## Potentially breaking changes

### Running Gradle on Java 6 is no longer supported

Gradle itself now requires Java 7 or better to run, but compiling project sources and running tests with Java 6 is still supported.
See [Compiling and testing for Java 6](userguide/java_plugin.html#sec:java_cross_compilation) in the Gradle Userguide. There are also
instructions on how to compile and test [Groovy](userguide/groovy_plugin.html#sec:groovy_cross_compilation) and
[Scala](userguide/scala_plugin.html#sec:scala_cross_compilation) for Java 6.

### Compiling and testing with Java 5 is no longer supported

Support for compiling and testing on Java 5 has been removed.

### Sonar plugin has been removed

The legacy Sonar plugin has been removed from the Gradle distribution. It is superseded by [the official plugin from SonarQube](http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle).

### The eclipse-cdt plugin has been removed

The `eclipse-cdt` plugin is no longer supported and has been removed.

### Unique default test result and report directories for `Test` tasks

The default location of reports produced by tasks of type [Test](dsl/org.gradle.api.tasks.testing.Test.html) have changed to incorporate the task name when used with the `Java Plugin`.
This allows multiple tasks of type `Test` to produce non-conflicting default report and result directories without additional configuration.

When the Java, Groovy or Scala plugin is applied, the report directory for a task of type `Test` with the name **integrationTest** is now `$buildDir/reports/tests/integrationTest` and the test results directory is `$buildDir/test-results/integrationTest`.

This means the built-in `test` task reports are in a different location. To revert to the previous behaviour, the reports output directory of `Test` tasks can be configured explicitly:

    test.reports.html.destination = testReportDir // build/reports/tests
    test.reports.xml.destination = testResultDir // build/test-results

### Ant-Based Scala Compiler has been removed

The deprecated Ant-Based Scala Compiler has been removed from Gradle 3.0 and the Zinc Scala Compiler is now used exclusively. The following properties related to the Ant-Based compiler have been removed from the `ScalaCompile` task:

- `daemonServer`
- `fork`
- `useAnt`
- `useCompileDaemon`

### Support for TestNG JavaDoc annotations has been removed

The support for declaring TestNG tests via JavaDoc annotations has been removed. As such, the `Test.testSrcDirs` and the methods on `TestNGOptions` have also been removed.

### Task property annotations (e.g., @Input) on interfaces

In previous versions, annotations on task properties such as `@InputFile` and `@OutputDirectory` were only taken into account when they were declared on the task class itself (or one 
of its super-classes). With Gradle 3.0, annotations declared on implemented interfaces are also taken into account.

### `eclipse-wtp` handling of external dependencies changed

For Java projects, the `eclipse-wtp` plugin adds external dependencies to the classpath instead of the WTP component file. Any customizations related to external dependencies 
that were made in the `eclipse.wtp.component.file` hooks now need to be moved to the `eclipse.classpath.file` hooks instead.

### `eclipse-wtp` is automatically applied to `war` or `ear` projects with `eclipse`

Projects that have the `war` or `ear` plugins applied in conjunction with the `eclipse` plugin will now have the `eclipse-wtp` plugin applied automatically.
If desired, this support can be removed using the following configuration:

    eclipse.project {
        natures.removeAll { it.startsWith('org.eclipse.wst') }
        buildCommands.removeAll {         
            it.name.startsWith('org.eclipse.wst')
        }
    }

### Eclipse model contains classpath attributes for project and external dependencies

The `EclipseProjectDependency` and `EclipseExternalDependency` models now contain `ClasspathAttribute` objects. By default, the JavaDoc location attribute and WTP deployment attributes are also 
populated.

Any customizations made via `eclipse.classpath.file.beforeMerged` and `eclipse.classpath.file.whenMerged` are also reflected.

### NamedDomainObjectContainers no longer create objects when using explicit parameter syntax

In previous versions of Gradle, the following would create a new `SourceSet` named `foo`:

    sourceSets {
        it.foo {}
    }
    
This behavior was unintended and has been removed. The above code will now cause an exception if `foo` has not already been defined. 

Creation must now use the implicit syntax:

    sourceSets {
        foo {}
    }

### Changes to previously deprecated APIs

* The `AbstractTask` methods `setName()` and `setProject()` are removed.
* The `plus(Iterable<FileCollection>)` and `#minus(Iterable<FileCollection>)` methods have been removed from `FileCollection`.
* Changing configurations after they have been resolved now throws an error.
* Changing configurations after task dependencies have been resolved now throws an error.
* Declaring custom `check`, `clean`, `build` or `assemble` tasks is not allowed anymore when using the lifecycle plugin.
* Configuring the Eclipse project name during `beforeMerged` or `whenMerged` is not allowed anymore.
* Removed `--no-color` command-line option (use `--console=plain` instead).
* Removed `--parallel-threads` command-line option (use `--parallel` + `--max-workers` instead).
* Removed `Zip.encoding` (use `Zip.metadataCharset` instead).
* Removed `DistributionPlugin.addZipTask()` and `addTarTask()`.
* The `installApp` task is no longer created by the `application` plugin (use `installDist` instead).
* Removed `Groovydoc.overview` (use `overviewText` instead).
* Removed `LoggingManager.setLevel()`. It is now not possible to change the log level during the execution of a task.
  If you were using this method to expose Ant logging messages, please use `AntBuilder.setLifecycleLogLevel()` instead.
* Removed `AntScalaCompiler` in favor of `ZincScalaCompiler`.
* Removed `EclipseClasspath.noExportConfigurations` property.
* Removed `ProjectDependency.declaredConfigurationName` property.
* Removed `AbstractLibrary.declaredConfigurationName` property.
* Removed `BuildExceptionReporter`.
* Removed `BuildLogger`.
* Removed `BuildResultLogger`.
* Removed `TaskExecutionLogger`.
* Removed `ConflictResolution`.
* Removed `Module`.
* Removed `DeleteAction`.
* Removed `EclipseDomainModel`.
* Removed `AntGroovydoc`.
* Removed `AntScalaDoc`.
* Removed `BinaryType`.
* Removed `LanguageType`.
* Removed `ConventionValue`.
* Removed `org.gradle.platform.base.test.TestSuiteBinarySpec` replaced by `org.gradle.testing.base.TestSuiteBinarySpec`
* Removed `org.gradle.platform.base.test.TestSuiteContainer` replaced by `org.gradle.testing.base.TestSuiteContainer`
* Removed `org.gradle.platform.base.test.TestSuiteSpec` replaced by `org.gradle.testing.base.TestSuiteSpec`
* TestKit supports Gradle versions 1.2 or later.
* Build comparison plugin supports Gradle versions 1.2 or later.
* Removed `Specs.and()`, `Specs.or()` and `Specs.not()`
* Removed `StartParameter.getParallelThreadCount()` and `StartParameter.setParallelThreadCount()`
* Removed `PrefixHeaderFileGenerateTask.getHeaders()`
* Removed `org.gradle.tooling.model.Task.getProject()`
* Removed `Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER`
* Removed old wrapper properties `urlRoot`, `distributionName`, `distributionVersion` and `distributionClassifier`
* Removed deprecated `has()`, `get()` and `set()` dynamic methods exposed by `ExtraPropertiesDynamicObjectAdapter`

### Groovy to Java conversions

For performance reasons, all classes in Gradle's public API have been converted from Groovy to Java.

As a consequence, these classes no longer extend `GroovyObject`. In order to retain binary compatibility, public API classes that have been 
converted are decorated with `GroovyObject` at runtime. This means plugins written for Gradle 2.x should continue working with Gradle 3.x. 

We are planning to drop the runtime `GroovyObject` decoration with Gradle 4.0. This means that plugins compiled against Gradle 2.x will no longer work with Gradle 4.0. 
Plugins that are compiled with Gradle 3.0 will not have references to `GroovyObject` and will remain compatible with Gradle 4.0.
Plugins compiled with Gradle 3.0 will also work with Gradle 2.x as long as they confine themselves to the Gradle 2.x API.

When recompiling your plugin with Gradle 3.0, you may need to make some changes to make it compile.

One instance of this is when you use `+=` in a statically compiled Groovy class. See [GROOVY-7888](https://issues.apache.org/jira/browse/GROOVY-7888).

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Martin Mosegaard Amdisen](https://github.com/martinmosegaard) - Correct some typos in depMngmt.xml and README.md
 - [Vladislav Bauer](https://github.com/vbauer) - ShadeJar: Use ClassRemapper instead of deprecated RemappingClassAdapter
 - [Emmanuel Bourg](https://github.com/ebourg) - Use the JCIP ThreadSafe annotation instead of the one from httpcomponents
 - [Andreas Dangel](https://github.com/adangel) - Add sourceSet.output to PMD classpath (GRADLE-3488)
 - [Tatsiana Drabovich](https://github.com/blestka) - Fixed TestNG Listener adapters equality (GRADLE-3189)
 - [Ryan Ernst](https://github.com/rjernst) - Upgrade to Groovy 2.4.7
 - [Ethan Hall](https://github.com/ethankhall) - Fixing documentation from candidate.name to candidate.module
 - [Matias Hernandez](https://github.com/matiash) - Removed 4NT-specific code in bat files (GRADLE-3476)
 - [Gregorios Leach](https://github.com/simtel12) - Include directories when using a S3Client to list objects in a prefix. (GRADLE-3453)
 - [Mahmoud  Khater](https://github.com/mahmoud-k) - Fix a problem with determining the version of Findbugs on the classpath (GRADLE-3457)
 - [Michael Ottati](https://github.com/mottati) - Allow Jetty daemon instances to be shut down. (GRADLE-2263)
 - [Sebastian Schuberth](https://github.com/sschuberth) - Minor style fixes
 - [Alexander Shorin](https://github.com/kxepal) - Allow local connections for daemon and messaging services from all network devices (GRADLE-3121)
 - [Rob	Upcraft](https://github.com/upcrob) - Fix spelling in documentation
 - [James Ward](https://github.com/jamesward) - Fixed launching Gradle from Finder on Mac OS
 - [Ramon Wirsch](https://github.com/ramonwirsch) - Fix NullPointerException when processing annotations in the new Java software model

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

