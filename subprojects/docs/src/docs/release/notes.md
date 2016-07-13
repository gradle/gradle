The Gradle team is pleased to announce Gradle 3.0 M2.

This second release leading up to Gradle 3.0 builds on [3.0 M1](https://github.com/gradle/gradle/releases/tag/v3.0.0-M1) with several key improvements listed below. For more information, see the [announcement blog post](http://gradle.org/blog/gradle-3-0-m2-java-9-support/).

Check out some of [the improvements we've made](#all-improvements) since Gradle 2.0. Lots of reasons to upgrade!

## Compatibility and Support

This release is intended as a _preview_ for new features we'll be releasing soon in Gradle 3.0. As such, it is not intended to be used in a production environment and features may change significantly before they are released in Gradle 3.0.

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### New Features and Improvements since 3.0 M1

 * **Initial Java 9 Support.** Gradle now runs properly when run on the latest JDK 9 EAP builds, and users can build and run tests for their own projects against JDK 9 as well. Note, however, that Gradle does not yet support Jigsaw modules or JDK 9-specific compile options such as `-release` and `-modulepath`.
 * **Performance Improvements and new Performance Guide.** A number of performance improvements have acculmulated over the last several Gradle releases, and it's a good time to try them out for yourself in 3.0 M2. For details on many of these improvements, see [Cédric's blog post](http://gradle.org/blog/gradle-3-0-m2-java-9-support/). We're also pleased to make available a draft of our [new Performance Guide](https://gradle.github.io/performance-guide). This is intended to be a short (13-page) guide that allows you to dramatically improve your build performance over the course of an afternoon. Check it out and please provide any feedback via the guide's [GitHub Issues](https://github.com/gradle/performance-guide/issues).
 * **Improved Kotlin build scripting.** Gradle 3.0 M2 includes the newly-released Gradle Script Kotlin 0.2.0. Users can now modify the build script classpath and apply plugins in Kotlin-based build scripts, and project import into IDEA is now seamless. See the Gradle Script Kotlin [0.2.0 release notes](https://github.com/gradle/gradle-script-kotlin/releases/tag/0.2.0) for details, samples and getting started instructions.

### Improved Gradle Daemon, now enabled by default

The performance improvement gained by using the Daemon is staggering: our performance tests [show that builds could be up to 75% faster](http://gradle.org/blog/gradle-3-0-m1-unleash-the-daemon/), just by enabling the Gradle Daemon.

We have been working hard to make the Gradle Daemon aware of its health and impact on the system it's running on; and we believe that it is now robust enough to be **enabled by default**.

We encourage you to give the improved Daemon a try. If for some reason you encounter problems, you can [disable the Daemon](userguide/gradle_daemon.html#daemon_faq). Please [submit feedback to us](https://discuss.gradle.org/c/3-0-m1/) if you encounter instability so that we can make further improvements.

### List Running Gradle Daemons with `gradle --status`

You can now check the status of running Daemons. A sample:


    $> gradle --status
     PID   VERSION                 STATUS
     28411 3.0                     IDLE
     34247 3.0                     BUSY

Note that currently this does not list Gradle Daemons with version < 3.0. More details available in the [User Guide](userguide/gradle_daemon.html#status).

### Up-to-date checks more robust against task implementation changes

Previously if a task's implementation class name changed, the class was deemed out-of-date even if its inputs and outputs matched the previous execution. However, if only the code of the task, or a dependent library changed, the task was still considered up-to-date. Since this version Gradle notices if the code of a task or its dependencies change between executions, and marks tasks as out-of-date when needed.

### `plugins` DSL can resolve plugins without applying them

There are times when you want to resolve a plugin without actually applying it to the current project, e.g.

- you only want to reuse a task class from that plugin
- you want to apply that plugin to subprojects of the current one

This is now possible using the following syntax

    plugins {
        id 'my.special.plugin' version '1.0' apply false
    }

    subprojects {
        if (someCondition) {
            apply plugin 'my.special.plugin'
        }
    }

### Improved handling of external dependencies in `eclipse-wtp` plugin

Before Gradle 3.0, the `eclipse-wtp` plugin always defined external dependencies the WTP component descriptor. This caused several problems, listed at [GRADLE-2123](https://issues.gradle.org/browse/GRADLE-2123). To resolve this, the plugin now defines external dependencies in the Eclipse classpath and marks them as deployed/non-deployed accordingly. For each library a classpath entry similar to the one below is generated:

    <classpathentry kind="lib" path=“/path/to/lib-1.0.0.jar" exported=“false">
        <attributes>
            <attribute name="org.eclipse.jst.component.dependency" value="WEB-INF/lib"/>
        </attributes>
    </classpath>

If the project is not a Java project (and thus has no classpath), the dependencies are added to the component descriptor as before.

### Improved dependency resolution for `eclipse-wtp` plugin

The `eclipse-wtp` plugin now fully leverages Gradle's dependency resolution engine. As a result, all dependency customisations like substitution rules and forced versions work in WTP projects.

### `eclipse` plugin also applies `eclipse-wtp` for web projects

If a project applies the `war` or `ear` plugins, then applying the `eclipse` plugin also applies `eclipse-wtp`. This improves the out-of-the box experience for Eclipse Buildship users.

### Tooling API exposes more information via the `EclipseProject` model.

The `EclipseProject` model was supplemented with a set of new features:

- The [EclipseSourceDirectory](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html) exposes the following information:
    - exclude and include patterns: [getExcludes()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getExcludes%28%29) and [getIncludes()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getIncludes%28%29),
    - classpath attributes: [getClasspathAttributes()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getClasspathAttributes%28%29),
    - output folder: [getOutput()](javadoc/org/gradle/tooling/model/eclipse/EclipseSourceDirectory.html#getOutput%28%29).
- The classpath container definition is available via the [EclipseProject.getClasspathContainers()](javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html#getClasspathContainers%28%29) method.
- The project output location is available via the [EclipseProject.getOutputLocation()](javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html#getOutputLocation%28%29) method.
- All classpath entries (project and external dependencies, classpath containers and source folders) expose their access rules via [EclipseClasspathEntry.getAccessRules()](javadoc/org/gradle/tooling/model/eclipse/EclipseClasspathEntry.html#getAccessRules%28%29).

With these features Tooling API clients can provide a more complete IDE integration. Buildship will make use of them very soon.

### Initial Java 9 support

Gradle 3.0 contains initial support for Java 9. This means that running Gradle on Java 9 and compiling,
testing and running Java 9 applications is supported out of the box.

The following plugins are known to have some issues with Java 9:

- [PMD plugin](userguide/pmd_plugin.html): Runs on Java 9 but cannot analyze Java 9 Bytecode as this is not yet supported by
  the latest version of PMD (5.5.0)
- [Jetty plugin](userguide/jetty_plugin.html): The version of Jetty used for the Jetty plugin does not work with Java 9
- [Scala plugin](userguide/scala_plugin.html): The Zinc compiler does not work with Java 9
- [FindBugs plugin](userguide/findbugs_plugin.html): The latest release (3.0.1) does not work with Java 9
- [OSGi plugin](userguide/osgi_plugin.html): The latest version of BND does not work with Java 9

Also, for publishing to S3 backed Maven and Ivy repositories, `-addmods java.xml.bind` has to be added to the JVM parameters. This can be accomplished by setting

    GRADLE_OPTS="-addmods java.xml.bind '-Dorg.gradle.jvmargs=-addmods java.xml.bind'"

CAVEAT: Your mileage may vary. If you run into any problems please report those on the forums.

### Upgrade of BND library used by OSGi plugin

The OSGi plugin now uses the version 3.2.0 of the BND library.

### Upgrade of the default Jacoco version

The Jacoco plugin has been updated to use Jacoco version 0.7.7.201606060606 by default. This is required for Java 9 support.

### Upgrade of the default PMD version

The PMD plugin has been updated to use PMD version 5.5.0 by default.

### Parallel task execution improvements

The `Test` task type now honors the `max-workers` setting for the test processes that are started. This means that Gradle will now run at most `max-workers` tasks and test processes at the same time.

### Improvements since Gradle 2.0
<a id="all-improvements" name="all-improvements"/>

- Performance improvements, faster builds and reduced memory usage
    - Configuration time, incremental build, incremental native compilation, build script compilation, test execution
- Gradle plugin portal
    - Publishing plugin
    - Maven and Ivy plugin repositories
- Dependency management
    - Compile-only dependencies for Java projects
    - Improved component meta-data rules
    - Component selection rules
    - Component replacement rules
    - Dependency substitution rules
    - Support for S3 repositories
    - Configurable HTTP authentication, including preemptive HTTP authentication
    - Artifact query API access to ivy.xml and pom.xml
    - Depend on a particular Maven snapshot
- Daemon
    - Health and performance monitoring
    - Proactive resource awareness and action
    - List running Daemons
- Continuous build
- Incremental Java compile
- Tooling API
    - Composite builds
    - Rich test, task and build progress events
    - Run test classes or methods
    - Cancellation
    - Color output
    - Eclipse builders and natures, Java source and runtime version, build JDK
- IDE
    - Improved Eclipse WTP integration, Scala integration
    - Java source and runtime version
- TestKit
- publish plugins
    - Publish to SFTP and S3 repositories
    - Maven dependency exclusions, dependency classifiers
    - Ivy extra attributes, dependency exclusions
- Groovy annotation processing
- Build environment report
- Code quality and application plugins
    - Various improvements
- Native
    - Parallel compilation
    - Cross compilation
    - Precompiled headers
    - Google test support
- Community
    - More frequent releases
    - More pull requests
- Play support
- Text resources
- Software model    
    - Dependency management for JVM libraries, target platform aware
        - inter-project, intra-project and external libraries
    - JVM library API definition, compile avoidance
    - JUnit support
    - Components report, model report
    - Validation and defaults rules, apply rules to all subjects with type
    - More managed model features
    - Better extension by plugins
    - Model DSL

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

### Plural task output registration APIs

The `@OutputFiles` and `@OutputDirectories` annotations support only properties with a `Map` type, where the values in the map can be resolved to individual files, while the keys represent unique identifiers for each file. Using either of the annotations with an `Iterable<File>` or `FileCollection` property is deprecated.

The following APIs have been deprecated:

`TaskOutputs.files()` method is now deprecated. Use either of the new `TaskOutputs.namedFiles()` methods instead.

## Potential breaking changes

### Running Gradle on Java 6 is no longer supported.

Gradle itself now requires Java 7 or better to run, but compiling project sources and running tests with Java 6 remains supported.
See [Compiling and testing for Java 6](userguide/java_plugin.html#sec:java_cross_compilation) in the Userguide. There are also
instructions on how to compile and test [Groovy](userguide/groovy_plugin.html#sec:groovy_cross_compilation) and
[Scala](userguide/scala_plugin.html#sec:scala_cross_compilation) for Java 6.

Support for compiling and testing on Java 5 has been dropped.

### Sonar plugin has been removed

The legacy Sonar plugin has been removed from the distribution. It is superceded by the official plugin from SonarQube (http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle).

### Test result and report directory take task name into account

The defaults for the outputs of tasks of type Test have changed to take the task name into account when used with the `Java Plugin`.
This allows having multiple tasks of type Test with non conflicting default report and result folders.
When the Java Plugin is applied, the report directory for a task of type `Test` with name `test` is now `$buildDir/reports/tests/test`. The `test-result` folder for task `test` is now `$buildDir/test-results/tests`.

To keep the previous behaviour, the reports output directory of Test tasks can be configured explicitly:

    test.reports.html.destination = testReportDir // build/reports/tests
    test.reports.xml.destination = testResultDir // build/test-results

### Ant-Based Scala Compiler has been removed

The deprecated Ant-Based Scala Compiler has been removed from Gradle
3.0. The Zinc Scala Compiler is now used exclusively. The following
properties have been removed from the ScalaCompile task:

1. `daemonServer`
1. `fork`
1. `useAnt`
1. `useCompileDaemon`

### Support for TestNG javadoc annotations has been removed

The support for declaring TestNG tests via javadoc annotations has been removed. The `Test.testSrcDirs` and the methods on `TestNGOptions` were removed, too,
since they are not needed any more.

### Task property annotations on implemented interfaces

In previous versions, annotations on task properties like `@InputFile` and `@OutputDirectory` were only taken into account when they were declared on the task class itself, or one of its super-classes. Since Gradle 3.0 annotations declared on implemented interfaces are also taken into account.

### `eclipse-wtp` handling of external dependencies changed

For Java projects, the `eclipse-wtp` plugin no longer adds external dependencies to the WTP component file, but to the classpath instead. Any customizations related to external dependencies that were made in the `eclipse.wtp.component.file` hooks now need to be done in the `eclipse.classpath.file` hooks instead.

### `eclipse-wtp` is automatically applied when the `war` or `ear` plugins are applied

User who are building `war` projects with Eclipse, but for any reason do not want to have WTP enabled can deactivate WTP like this:

    eclipse.project {
        natures.removeAll { it.startsWith('org.eclipse.wst') }
        buildCommands.removeAll {         
            it.name.startsWith('org.eclipse.wst')
        }
    }
### NamedDomainObjectContainers no longer create objects when using explicit parameter syntax

The following snippet used to create a new source set called `foo`:

    sourceSets {
        it.foo {}
    }
    
This behavior was unintended and has been removed. The above code will now result in an exception if `foo` is not already defined. 

Creation now only happens when using the implicit syntax

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

### Types no longer extend `GroovyObject`

* org.gradle.api.tasks.bundling.Jar

#### Eclipse model contains classpath attributes for project and external dependencies

The `EclipseProjectDependency` and `EclipseExternalDependency` models now contain `ClasspathAttribute`s. By default the JavaDoc location attribute and WTP deployment attributes are populated.

Any customizations done via `eclipse.classpath.file.beforeMerged` and `eclipse.classpath.file.whenMerged` are also reflected in these tooling models.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Tatsiana Drabovich](https://github.com/blestka) - Fixed TestNG Listener adapters equality (GRADLE-3189)
 - [Michael Ottati](https://github.com/mottati) - Allow Jetty daemon instances to be shut down. (GRADLE-2263)
 - [Gregorios Leach](https://github.com/simtel12) - Include directories when using a S3Client to list objects in a prefix. (GRADLE-3453)
 - [Mahmoud  Khater](https://github.com/mahmoud-k) - Fix a problem with determining the version of Findbugs on the classpath (GRADLE-3457)
 - [Ryan Ernst](https://github.com/rjernst) - Upgrade to Groovy 2.4.7
 - [James Ward](https://github.com/jamesward) - Fixed launching Gradle from Finder on Mac OS
 - [Ramon Wirsch](https://github.com/ramonwirsch) - Fix NullPointerException when processing annotations in the new Java software model
 - [Vladislav Bauer](https://github.com/vbauer) - ShadeJar: Use ClassRemapper instead of deprecated RemappingClassAdapter
 - [Matias Hernandez](https://github.com/matiash) - Removed 4NT-specific code in bat files (GRADLE-3476)
 - [Andreas Dangel](https://github.com/adangel) - Add sourceSet.output to PMD classpath (GRADLE-3488)
 - [Alexander Shorin](https://github.com/kxepal) - Allow local connections for daemon and messaging services from all network devices (GRADLE-3121)
 - [Martin Mosegaard Amdisen](https://github.com/martinmosegaard) - Correct some typos in depMngmt.xml and README.md
 - [Ethan Hall](https://github.com/ethankhall) - Fixing documentation from candidate.name to candidate.module
 - [Sebastian Schuberth](https://github.com/sschuberth) - Minor style fixes

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

[GRADLE-3491](https://issues.gradle.org/browse/GRADLE-3491) - Android Library projects do not pick up changes
