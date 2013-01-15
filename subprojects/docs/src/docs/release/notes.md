First of all this release comes with massive performance improvements in various areas (see below). This release also makes working with Scala and Groovy projects more convenient by auto detecting the
Scala and Groovy libraries. The dependency reporting has been improved again to deal with unresolvable dependencies. TestNG report generation has been much improved by using the Gradle reporting engine
instead of the default TestNG report engine. There are also some exciting new incubating features: We introduced a new, very powerful way of dealing with dependency resolution and version conflicts by
providing dependency resolve hooks. There is a configuration-on-demand feature to speed up configuration time of large multi-module builds. There is also a new java library distribution plugin and a
new feature to aggregate test reports within multi-module builds.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Performance and memory consumption

Gradle 1.4 introduces some important performance improvements, resulting in faster builds that use less heap space. These improvements affect
dependency resolution, task up-to-date checks and test execution. In other words, everything that a typical Java based project uses in its build.

A typical Java project might see a 10-20% improvement in build time. For builds with many small projects and many unit tests, in combination with the
Gradle daemon, we are seeing a 50% improvement in build time.

#### Faster dependency resolution

As mentioned below, resolution of Maven SNAPSHOT versions is now faster, due to fewer network requests and various other internal changes.

Artifact meta-data is now cached more efficiently, making it much faster to read and write to disk. This means that resolution of all
dependencies can be performed more quickly. In particular, reading of cached meta-data is much faster, so that resolution of dependencies that
have been cached is much faster.

This in turn means that task up-to-date checks are faster, as typically a large portion of the time to perform an up-to-date check is made up of
the time it takes to resolve the dependencies that form the task inputs.

#### Faster test execution

Test execution typically has a significant effect on build time. While your actual test code won't run any faster in Gradle 1.4, Gradle is now much
more efficient in managing test execution and generating result and report files.

Gradle runs tests in a separate worker JVM, to keep the build and tests isolated from each other. The mechanism for controlling these workers and
dispatching tests to them is now much more efficient, meaning that tests will start to execute in the worker processes more quickly, and that the CPU
cores can spend more of their time executing tests. This mechanism is now much more robust and can handle a very large number of test classes, fixing a
number of deadlock conditions.

Previously, Gradle collected the test results as XML files and generated the HTML report from these XML files. Test results are now written in an
efficient binary format, and the XML files and HTML report generated from this. The work of generating the results has moved from the test worker
processes to the parent build process, meaning that the workers can spend more of their time executing tests, while the results are generated
asynchronously by the parent. In addition, all result and report file generation streams the content to file, keeping heap usage at a minimum.

### Dependency resolution improvements

As with every release, the dependency resolution engine has been improved with bug fixes and performance optimizations.

These improvements are in addition to the new incubating support for [Dependency Resolve Rules](#dependency-resolve-rules), which give you more control over dependency resolution.

#### Fewer network requests when checking for Maven SNAPSHOT artifact updates (performance)

When checking whether a Maven SNAPSHOT dependency has been updated remotely, fewer network requests are now made to the repository. Previously, multiple 
requests were made to the `maven-metadata.xml` file where now only one request is made (GRADLE-2585).

This results in faster dependency resolution when using Maven SNAPSHOT dependencies, in particular when importing into an IDE.

#### Faster searching for locally available dependency artifacts (performance)

Before Gradle downloads a dependency artifact from a remote repository, it will selectively search the local file system for that exact same file 
(i.e. a file with the exact same checksum). For example, Gradle will search the user's “local maven” repository. If the file is found, it will be 
copied from this location which is much faster than downloading the file (which would be exactly the same) over the network. This is completely 
transparent and safe.

The algorithm used to search for “local candidates” has been improved and is now faster. This affects all builds using dependency management, especially when building for the first time (GRADLE-2546).

#### Maven SNAPSHOT artifacts with classifiers are now correctly “changing”

For dependencies originating in Maven repositories, Gradle follows Maven semantics and treats any dependency artifact whose version number ends 
in '`-SNAPSHOT`' as “changing”, 
which means that it can change over time and Gradle should periodically check for updates instead of caching indefinitely 
(see “[controlling caching](/userguide/dependency_management.html#sec:controlling_caching)”). Previously, artifacts with classifiers 
(e.g `sources` or `javadoc`) were not being checked for changes. This has been fixed in this release (GRADLE-2175).

#### More robust `--offline` mode

Previously, Gradle discarded cached artifacts just prior to attempting to fetch an updated version from the remote source. If the fetch of the remote 
artifact failed (e.g. network disruption), there was no longer a cached version available to be used in `--offline` mode.
This resulted in some situations where trying to use `--offline` mode in response to unexpected network issues would not work well.

Gradle now only discards old artifacts after a newer version has been cached, making `--offline` mode more reliable and useful (GRADLE-2364).

#### Using a “maven” layout with an Ivy repository

By default, an Ivy repository would store the module "org.group:module:version" under `baseurl/org.my.group/module`, while a maven repository would store the same module under `baseurl/org/my/group/module`.
It is now possible to configure an `ivy` repository that uses the maven directory layout, [using the new `m2compatible` flag with the `pattern` layout](userguide/userguide_single.html#N14575).

### Dependencies that failed to be resolved are now listed in dependency reports

Dependency resolution reports now show dependencies that couldn't be resolved. 

Here is an example for the `dependencies` task:

<pre><tt>compile - Classpath for compiling the sources.
\--- foo:bar:1.0
     \--- foo:baz:2.0 FAILED
</tt>
</pre>

The `FAILED` marker indicates that `foo:baz:2.0`, which is depended upon by `foo:bar:1.0`, couldn't be resolved.

A similar improvement has been made to the `dependencyInsight` task:

<pre><tt>foo:baz:2.0 (forced) FAILED

foo:baz:1.0 -> 2.0 FAILED
\--- foo:bar:1.0
     \--- compile
</tt>
</pre>

In this example, `foo:baz` was forced to version `2.0`, and that version couldn't be resolved.

### Filter dependency resolution reports by configuration

The `dependencies` task now accepts an optional `--configuration` parameter that restricts its output to a particular configuration:

    $ gradle dependencies --configuration compile

This command will display the dependency tree rooted at the `compile` configuration, and (assuming a standard
Java project) omit the dependency trees for the `runtime`, `testCompile`, and `testRuntime` configurations.

### Automatic configuration of Groovy dependency used by `GroovyCompile` and `Groovydoc` tasks

The `groovy-base` plugin now automatically detects the Groovy dependency used on the compile class path of any `GroovyCompile` or `Groovydoc` task,
and appropriately configures the task's `groovyClasspath`.
As a consequence, the Groovy dependency can now be configured directly for the configuration(s) that need it, and it is no longer necessary
to use the `groovy` configuration.

Old (and still supported):

    dependencies {
        groovy "org.codehaus.groovy:groovy-all:2.0.5"
    }

New (and now preferred):

    dependencies {
        compile "org.codehaus.groovy:groovy-all:2.0.5"
    }

Automatic configuration makes it easier to build multiple artifact variants targeting different Groovy versions, or to only use Groovy
for selected source sets:

    dependencies {
        testCompile "org.codehaus.groovy:groovy-all:2.0.5"
    }

Apart from the `groovy-all` Jar, Gradle also detects usages of the `groovy` Jar and `-indy` variants. Automatic configuration is disabled
if a task's `groovyClasspath` is non-empty (for example because the `groovy` configuration is used) or no repositories are declared
in the project.

### Automatic configuration of Scala dependency used by `ScalaCompile` and `Scaladoc` tasks

The `scala-base` plugin now automatically detects the `scala-library` dependency used on the compile class path of any `ScalaCompile` or `ScalaDoc` task,
and appropriately configures for the task's `scalaClasspath`.
As a consequence, it is no longer necessary to use the `scalaTools` configuration.

Old (and still supported):

    dependencies {
        scalaTools "org.scala-lang:scala-compiler:2.9.2"
        compile "org.scala-lang:scala-library:2.9.2"
    }

New (and now preferred):

    dependencies {
        compile "org.scala-lang:scala-library:2.9.2"
    }

Automatic configuration makes it easier to build multiple artifact variants targeting different Scala versions. Here is one way to do it:

    apply plugin: "scala-base"

    sourceSets {
        scala2_8
        scala2_9
        scala2_10
    }

    sourceSets.all { sourceSet ->
        scala.srcDirs = ["src/main/scala"]
        resources.srcDirs = ["src/main/resources"]

        def jarTask = task(sourceSet.getTaskName(null, "jar"), type: Jar) {
            baseName = sourceSet.name
            from sourceSet.output
        }

        artifacts {
            archives jarTask
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        scala2_8Compile "org.scala-lang:scala-library:2.8.2"
        scala2_9Compile "org.scala-lang:scala-library:2.9.2"
        scala2_10Compile "org.scala-lang:scala-library:2.10.0-RC5"
    }

Note that we didn't have to declare the different `scala-compiler` dependencies, nor did we have to assign them
to the corresponding `ScalaCompile` and `ScalaDoc` tasks. Nevertheless, running `gradle assemble` produces:

<pre><tt>$ ls build/libs
scala2_10.jar scala2_8.jar  scala2_9.jar
</tt>
</pre>

With build variants becoming a first-class Gradle feature, building multiple artifact variants targeting different
Scala versions will only get easier.

Automatic configuration isn't used if a task's `scalaClasspath` is non-empty (for example because the `scalaTools`
configuration is used) or no repositories are declared in the project.

### Brand new TestNG reports are generated by default

Gradle 1.3 introduced several incubating improvements to TestNG reports.
In Gradle 1.4 the improved reports are turned on by default.
The TestNG users will be delighted to learn that:

* The new HTML report is much easier to read and browse than the standard TestNG report. It's also quite pretty.
* Both reports, XML (for your CI server) and HTML (for you), contain the test output (i.e. messages logged to the standard streams or via the standard logging toolkits).
This is extremely useful for debugging certain test failures.
* The reports neatly work with TestNG and Gradle parallel testing ([test.maxParallelForks](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:maxParallelForks)).

The implementation of the new reports is now a part of Gradle.
Previously, the report generation was delegated to TestNG's default listeners that are shipped with TestNG library.
You can switch off the HTML report generation by configuring the [test.testReport](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:testReport) property.
If you prefer the old TestNG reports please refer to the [documentation](groovydoc/org/gradle/api/tasks/testing/testng/TestNGOptions.html#useDefaultListeners).

### Easier embedding of Gradle via [Tooling API](userguide/embedding.html)

The [Tooling API](userguide/embedding.html), the standard way to embed Gradle, is now more convenient to use.
As of Gradle 1.4 it ships as a single jar with the only external dependency being an SLF4J implementation.
All other dependencies are packaged inside the Jar and shaded to avoid conflicts with the embedder's classpath.

<!--
## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.


### Example promoted
-->

## Fixed issues

## Incubating features

Incubating features are intended to be used, but not yet guaranteed to be backwards compatible.
By giving early access to new features, real world feedback can be incorporated into their design.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the new incubating features or changes to existing incubating features in this Gradle release.

### Dependency resolve rules

A “dependency resolve rule” is a user specified algorithm that can influence the resolution of a particular dependency.
Dependency resolve rules can be used to solve many challenging dependency management problems.

For example, a dependency resolve rule can be used to force all versions with a particular “group” to be of the same version:

    configurations.all {
        resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (details.requested.group == 'org.gradle') {
                details.useVersion '1.4'
            }
        }
    }

The rule (i.e. the closure given to the `eachDependency` method above) is called for each dependency that is to be resolved. The 
[`DependencyResolveDetails`](javadoc/org/gradle/api/artifacts/DependencyResolveDetails.html)
object passed to the rule implementation represents the originally _requested_ and the finally _selected_ version (after conflict resolution
has been applied). The rule can make a programmatic choice to change how the dependency should be resolved.

This is an “incubating” feature. In Gradle 1.4, it is only possible to affect the version of the dependency that will be resolved. In future versions,
more control will be allowed via the `DependencyResolveDetails` type.

It is a much more powerful feature than just enforcing a certain version of a dependency in advance (which [you can also do](dsl/org.gradle.api.artifacts.ResolutionStrategy.html) with Gradle).
Many interesting use cases can be implemented with the dependency resolve rules:

* [Blacklisting a version](userguide/dependency_management.html#sec:blacklisting_version) with a replacement
* Implementing a [custom versioning scheme](userguide/dependency_management.html#sec:custom_versioning_scheme)
* [Modelling a releasable unit](userguide/dependency_management.html#sec:releasable_unit) - a set of related libraries that require a consistent version

For more information, including more code samples, please refer to this [user guide section](userguide/dependency_management.html#sec:dependency_resolve_rules).

### Improved scalability via configuration on demand

In Gradle, all projects are configured before any task gets executed (see [the build lifecycle](userguide/userguide_single.html#sec:build_phases)).
Huge multi-project builds may have a noticeable configuration time for that reason.
To improve the experience of working with large multi-project builds "configuration on demand" mode is introduced, where only those projects required
by the build are configured. This mode is incubating and currently it is not guaranteed to work with every multi-project build.
It should work very well with builds that have [decoupled projects](userguide/userguide_single.html#sec:decoupled_projects)
(e.g. avoiding subprojects accessing each other's model).
Before you start configuring on demand, please read the section in the [user guide](userguide/userguide_single.html#sec:configuration_on_demand).
Then update your gradle.properties file:

    #gradle.properties file
    systemProp.org.gradle.configuration.ondemand=true

### The new 'java-library-distribution' plugin

The new incubating '[`java-library-distribution`](userguide/javaLibraryDistributionPlugin.html)' plugin, contributed by Sébastien Cogneau, makes it is much easier to create a
standalone distribution for a JVM library.

Let's walk through a small example. Let's assume we have a project with the following layout:

<pre><tt>MyLibrary
|____build.gradle
|____libs // a directory containing third party libraries
| |____a.jar
|____src
| |____main
| | |____java
| | | |____SomeLibrary.java
| |____dist // additional files that should go into the distribution
| | |____dir2
| | | |____file2.txt
| | |____file1.txt
</tt>
</pre>

In the past, it was necessary to declare a custom `zip` task that assembles the distribution. Now, the 'java-library-distribution' will do the job for you:

    apply plugin: 'java-library-distribution'

    dependencies {
        runtime files('libs/a.jar')
    }

    distribution {
        name = 'MyLibraryDistribution'
    }

Given this configuration, running `gradle distZip` will produce a zip file named `MyLibraryDistribution.zip` that contains the library itself,
its runtime dependencies, and everything in the `src/dist` directory.

To add further files to the distribution, configure the `distZip` task accordingly:

    distZip {
        from('aFile')
        from('anotherFile') {
            into('dist')
        }
    }

### Stand-alone test report task

A new, incubating `TestReport` task type is now available. This task takes the test results generated by one or more `Test` tasks and generates
a combined HTML test report from them. For example, you can use this task to generate a single test report for all the projects in the build:

    task testReport(type: TestReport) {
        destinationDir = file("$buildDir/reports/all-tests")
        reportOn subprojects*.test
    }

The test report task currently combines test results, but does not aggregate the test results for a given class. So, if a given class is run by
multiple `Test` tasks, only one execution of the class will be included in the report and the other executions of that class
will be discarded. This will be addressed in a later Gradle version.

For more details, see the [user guide](userguide/java_plugin.html#test_reporting)

### Generate `ivy.xml` without publishing

The incubating '`ivy-publish`' plugin introduces a new `GenerateIvyDescriptor` task generates the Ivy metadata file (a.k.a. `ivy.xml`) for publication.
The task name for the default Ivy publication is '`generateIvyModuleDescriptor`'.

This function used to be performed internally by the `PublishToIvyRepository` task. By having this function be performed by a separate task
you can generate the `ivy.xml` metadata file without having to publish your module to an Ivy repository, which makes it easier to test/check
the descriptor. 

The `GenerateIvyDescriptor` task also allows the location of the generated Ivy descriptor file to changed from its default location at ‘`build/publications/ivy/ivy.xml`’.
This is done by setting the `destination` property of the task:

    apply plugin: 'ivy-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    generateIvyModuleDescriptor {
        destination = 'generated-ivy.xml'
    }

Executing `gradle generateIvyModuleDescriptor` will result in the Ivy module descriptor being written to the file specified. This task is automatically wired
into the respective `PublishToIvyRepository` tasks, so you do not need to explicitly call this task to publish your module.

### The new ‘maven-publish’ plugin

The new incubating ‘maven-publish’ plugin is an alternative to the existing ‘maven’ plugin, and will eventually replace it. This plugin builds on the new publishing model
that was introduced in Gradle 1.3 with the ‘ivy-publish’ plugin. The new publication mechanism (which is currently “incubating”, including this plugin) will
expand and improve over the subsequent Gradle releases to provide more convenience and flexibility than the existing publication mechanism plus very powerful features to wire your components across builds & teams.

In the simplest case, publishing to a Maven repository looks like…

    apply plugin: 'maven-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    publishing {
        repositories {
            maven {
                url 'http://mycompany.org/repo'
            }
        }
    }

To publish, you simply run the `publish` task. The POM file will be generated and the main artifact uploaded to the declared repository.
To publish to your local Maven repository (ie 'mvn install') simply run the `publishToMavenLocal` task. You do not need to have `mavenLocal` in your
`publishing.repositories` section.

To modify the generated POM file, you can use a programmatic hook that modifies the descriptor content as XML.

    publications {
        maven {
            pom.withXml {
                asNode().appendNode('description', 'A demonstration of maven POM customisation')
            }
        }
    }

In this example we are adding a ‘`description`’ element for the generated POM. With this hook, you can modify any aspect of the POM.
For example, you could replace the version range for a dependency with the actual version used to produce the build.
This allows the POM file to describe how the module should be consumed, rather than be a description of how the module was built.

For more information on the new publishing mechanism, see the [new User Guide chapter](userguide/publishing_maven.html).

## Deprecations

### Changing certain task configuration during and after execution

Much of a task's configuration influences how, or even if, a task should execute. After the task has executed, changing the configuration has no useful effect.
For example, it does not make sense to add an action via the `doFirst()` method to a task that is executing or has already executed.
Changing such configuration has been deprecated and this will become an error condition in Gradle 2.0.

#### Changing the action list

Once a task has started executing, its action list should no longer be changed. This includes calling the following methods on `Task` objects:

* `setActions()`
* `doLast()` - including using the synonymous `<<` operator
* `doFirst()`

Mutating the collection returned by `getActions()` is also deprecated after the task has started executing.

#### Changing task dependencies 

Once a task has started executing, its dependencies should no longer be changed. This includes calling the following methods on `Task` objects:

* `dependsOn()`
* `setDependsOn()`

#### Changing execution conditions

Once a task has started executing, its configuration controlling whether it will be execution should no longer be changed. 
This includes calling the following methods on `Task` objects:

* `onlyIf()`
* `setOnlyIf()`
* `setEnabled()`

#### Changing task inputs

Once a task has started executing, its “inputs” configuration should no longer be changed. 
This includes calling the following methods on `TaskInputs` objects:

* `files()`
* `file()`
* `dir()`
* `property()`
* `properties()`
* `source()`
* `sourceDir()`

#### Changing task outputs

Once a task has started executing, its “outputs” configuration should no longer be changed. 
This includes calling the following methods on `TaskOutputs` objects:

* `upToDateWhen()`
* `files()`
* `file()`
* `dir()`

## Potential breaking changes

### `DependencyReportTask` and `DependencyInsightReportTask` no longer fail when dependencies cannot be resolved

Previously, these tasks types would fail if one or more dependencies could not be resolved. Now, they no longer fail and instead display the failed
dependencies in the appropriate place in the output.

### `DependencyInsightReportTask` throws better exception on bad configuration

Previously, when the task's configuration was invalid a `ReportException` would be thrown when the task started to execute. For consistency with other tasks, it
now throws a `InvalidUserDataException`.

### Copying a `Configuration` also copies its resolution strategy

Previously, a copied `Configuration` object shared the same `resolutionStrategy` object as the `Configuration` that it was copied from. This meant that changes 
to `resolutionStrategy` of the source or the copy effected both instances and resulted in undesirable side affects. Copying a `Configuration` now also creates a
discrete copy of the `resolutionStrategy`.

### Removed `Jvm.getSupportsAppleScript()`

In the deprecated internal class `org.gradle.util.Jvm` the method `getSupportsAppleScript()` has been removed.

If you need to check if the running JVM supports AppleScript, you can use the following code:

    import javax.script.ScriptEngine
    import javax.script.ScriptEngineManager

    ScriptEngineManager mgr = new ScriptEngineManager();
    ScriptEngine engine = mgr.getEngineByName("AppleScript");
    boolean isAppleScriptAvailable = engine != null;

### Changes to new Ivy publishing support

Breaking changes have been made to the new, incubating, Ivy publishing support.

Previously, it was possible to set the `descriptorFile` property on an IvyPublication object. This property has been removed with the introduction of the new
`GenerateIvyDescriptor` task. To specify where the `ivy.xml` file should be generated, set the `destination` property of the `GenerateIvyDescriptor` task.

Previously _all_ configurations of the project were published. Now, only the ‘`archives`’ configuration together with the ‘`default`’ configuration and its 
ancestors will be published. In practice, this means that a Java project's `testCompile` and `testRuntime` configurations will no longer be published by default.

### Changed default value for `TestNGOptions.useDefaultListeners`

The default value for [TestNGOptions.useDefaultListeners](groovydoc/org/gradle/api/tasks/testing/testng/TestNGOptions.html#useDefaultListeners) has changed from `true` to `false`
so that Gradle can take over generation of the reports.
This way Gradle can provide invaluable improvements to the reporting - for more information read the earlier section on TestNG reports.

### Updated default versions of Checkstyle and CodeNarc

The default version of Checkstyle used for the '`checkstyle`' plugin has been updated from `5.5` to `5.6`.

The default version of CodeNarc used for the '`codenarc`' plugin has been updated from `0.16.1` to `0.18`.

### `eclipseWtpComponent` task overrides dependent modules

Previously, the `eclipse-wtp` plugin's `eclipseWtpComponent` task would add generated `dependent-module` entries to those already contained in the
`.settings/org.eclipse.wst.common.component` file. This could lead to stale and duplicated entries (see `GRADLE-2526`). Now, existing
entries are overridden with generated entries, just like it's done for `classpathentry` elements in `.classpath` files.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* Sébastien Cogneau - Contributing the `java-library-distribution` plugin
* James Bengeyfield - `showViolations` flag for `Checkstyle` task (GRADLE-1656)
* Dalibor Novak - `m2compatible` flag on `PatternRepositoryLayout` (GRADLE-1919)
* Brian Roberts, Tom Denley - Support multi-line JUnit test names (for better ScalaTest compatibility) (GRADLE-2572)
* Sean Gillespie - Extend the application plugin to build a tar distribution

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
