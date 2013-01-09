## New and noteworthy

Here are the new features introduced in this Gradle release.

### Easier embedding of Gradle via [Tooling API](userguide/embedding.html)

The [Tooling API](userguide/embedding.html), the standard way to embed Gradle, is now more convenient to use. 
As of Gradle 1.4 it ships as a single jar with the only external dependency being an SLF4J implementation.
All other dependencies are packaged inside the Jar and shaded to avoid conflicts with the embedder's classpath.

### Dependency resolution improvements

As with every release, the dependency resolution engine has been improved with bug fixes and performance optimizations.

These improvements are in addition to the new support for [Dependency Resolve Rules](#dependency-resolve-rules), which give you more control over dependency resolution.

#### Maven SNAPSHOT artifacts with classifiers are now correctly “changing”

For dependencies originating in Maven repositories, Gradle follows Maven semantics and treats any dependency artifact whose version number ends 
in '`-SNAPSHOT`' as “changing”, 
which means that it can change over time and Gradle should periodically check for updates instead of caching indefinitely 
(see “[controlling caching](/userguide/dependency_management.html#sec:controlling_caching)”). Previously, artifacts with classifiers 
(e.g `sources` or `javadoc`) were not being checked for changes. This has been fixed in this release (GRADLE-2175).

#### More robust `--offline` mode

Previously, Gradle discarded cached artifacts just prior to attempting to fetch an updated version from the remote source. If the fetch of the remote 
artifact failed (e.g. network disruption), 
there was no longer a cached version available to be used in `--offline` mode. This could result in situations where trying to use `--offline` 
mode in response to unexpected network issues would not work well. 

Gradle now only discards old artifacts after a newer version has been cached, which makes `--offline` mode more reliable and useful (GRADLE-2364).

#### Fewer network requests when checking for Maven SNAPSHOT artifact updates (performance)

When checking whether a Maven SNAPSHOT dependency has been updated remotely, fewer network requests are now made to the repository. Previously, multiple 
requests were made to the `maven-metadata.xml` file where now only one request is made (GRADLE-2585).

This results in faster dependency resolution when using Maven SNAPSHOT dependencies.

#### Faster searching for locally available dependency artifacts (performance)

Before Gradle downloads a dependency artifact from a remote repository, it will selectively search the local file system for that exact same file 
(i.e. a file with the exact same checksum). For example, Gradle will search the user's “local maven” repository. If the file is found, it will be 
copied from this location which is much faster than downloading the file (which would be exactly the same) over the network. This is completely 
transparent and safe.

The algorithm used to search for “local candidates” has been improved and is now faster. This affects all builds using dependency management, especially when building for the first 
time (GRADLE-2546).

#### Using a “maven” layout with an Ivy repository

By default, an ivy repository would store the module "org.group:module:version" under `baseurl/org.my.group/module`, while a maven repository would store the same module under `baseurl/org/my/group/module`.
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

### Automatic configuration of Groovy dependency used by GroovyCompile and Groovydoc tasks

The `groovy-base` plugin now automatically detects the Groovy dependency used on the class path of any `GroovyCompile` or `Groovydoc` task,
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

### Automatic configuration of Scala dependency used by ScalaCompile and Scaladoc tasks

The `scala-base` plugin now automatically detects the `scala-library` dependency used on the class path of any `ScalaCompile` or `ScalaDoc` task,
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

### HTML reports for TestNG are generated by default

The HTML test report for TestNG Tests is now generated by default. You can switch off the report generation by setting the `testReport` property to false:

    test{
        useTestNG()
        testReport = false
    }
  
<!--
### Example new and noteworthy
-->

### Performance and memory consumption

- Faster snapshot resolution
- Faster caching, in particular at resolution time
- Faster test execution and result generation
- Test logging is streamed to file

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

Many interesting use cases can be implemented with the dependency resolve rules:

* [Blacklisting a version](userguide/dependency_management.html#sec:blacklisting_version) with a replacement
* Implementing a [custom versioning scheme](userguide/dependency_management.html#sec:custom_versioning_scheme)
* [Modelling a releasable unit](userguide/dependency_management.html#sec:releasable_unit) - a set of related libraries that require a consistent version

For more information, including more code samples, please refer to this [user guide section](userguide/dependency_management.html#sec:dependency_resolve_rules).

### Generate `ivy.xml` without publishing

The '`ivy-publish`' plugin introduces a new `GenerateIvyDescriptor` task generates the Ivy metadata file (a.k.a. `ivy.xml`) for publication. The task name for the default Ivy publication is '`generateIvyModuleDescriptor`'.

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

The new ‘maven-publish’ plugin is an alternative to the existing ‘maven’ plugin, and will eventually replace it. This plugin builds on the new publishing model
that was introduced in Gradle 1.3 with the ‘ivy-publish’ plugin. The new publication mechanism (which is currently “incubating”, including this plugin) will
expand and improve over the subsequent Gradle releases to provide more convenience and flexibility than the traditional publication mechanism.

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

### The new 'java-library-distribution' plugin

The new '[`java-library-distribution`](userguide/javaLibraryDistributionPlugin.html)' plugin, contributed by Sébastien Cogneau, makes it is much easier to create a 
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

Given this configuration, running `gradle distZip` will produce a zip file named `MyLibraryDistribution` that contains the library itself,
its runtime dependencies, and everything in the `src/dist` directory.

To add further files to the distribution, configure the `distZip` task accordingly:

    distZip {
        from('aFile')
        from('anotherFile') {
            into('dist')
        }
    }

## Deprecations

### Certain task configuration after execution of task has been started.

Changing certain task configuration does not make sense when the task is already being executed.
For example, imagine that at execution time, the task adds yet another doFirst {} action.
The task is already being executed so adding a *before* action is too late and it is probably a user mistake.
In order to provide quicker and higher quality feedback on user mistakes
we want to prevent configuring certain task properties when the task is already being executed.
For backwards compatibility reasons, certain task configuration is deprecated. This includes:

* Mutating Task.getActions()
* Calling Task.setActions()
* Calling Task.dependsOn()
* Calling Task.setDependsOn()
* Calling Task.onlyIf()
* Calling Task.setOnlyIf()
* Calling Task.doLast()
* Calling Task.doFirst()
* Calling Task.leftShift()
* Calling Task.setEnabled()
* Calling TaskInputs.files()
* Calling TaskInputs.file()
* Calling TaskInputs.dir()
* Calling TaskInputs.property()
* Calling TaskInputs.properties()
* Calling TaskInputs.source()
* Calling TaskInputs.sourceDir()
* Calling TaskOutputs.upToDateWhen()
* Calling TaskOutputs.files()
* Calling TaskOutputs.file()
* Calling TaskOutputs.dir()

### TestNGOptions.useDefaultListeners

We deprecated the `useDefaultListeners` property in org.gradle.api.tasks.testing.testng.TestNGOptions as Gradle now always generates test results in XML format for TestNG based Tests
and enabled the HTML report generation for TestNG by default.

## Potential breaking changes

### Incubating DependencyInsightReport throws better exception

For consistency, InvalidUserDataException is thrown instead of ReportException when user incorrectly uses the dependency insight report.

### Copying configurations also copies the resolution strategy

Previously, after performing the copy, the resolution strategy was shared between the target and source configuration.
This behavior is now corrected.
Copy operation copies all the resolution strategy settings and the target configuration contains own instance of the resolution strategy.
The impact of this change is minimal - it is mentioned here only for completeness. The new behavior is much better for all users.

### Removed getSupportsAppleScript() in org.gradle.util.Jvm

In the deprecated internal class `org.gradle.util.Jvm` we removed the method `getSupportsAppleScript()` to check that AppleScriptEngine is available on the JVM.
As a workaround you can dynamically check if the AppleScriptEngine is available:

    import javax.script.ScriptEngine
    import javax.script.ScriptEngineManager

    ScriptEngineManager mgr = new ScriptEngineManager();
    ScriptEngine engine = mgr.getEngineByName("AppleScript");
    boolean isAppleScriptAvailable = engine != null;

### Changes to new Ivy Publication support (incubating)

**Removed `descriptorFile` property from IvyPublication**

In Gradle 1.3, it was possible to set the `descriptorFile` property on an IvyPublication object. This property has been removed with the introduction of the new
GenerateIvyDescriptor task. To specify where the `ivy.xml` file should be generated, set the `destination` property of the GenerateIvyDescriptor task.

**Generated XML descriptor does not include all configurations**

As part of improving the way we publish modules to an Ivy repository, we no longer publish _all_ configurations to the generated `ivy.xml`. Only the 'archives' configuration,
together with the 'default' configuration and its ancestors will be published. In practice, this means that a Java project's 'testCompile' and 'testRuntime' configurations will
no longer be published by default.

### Changed default value for TestNGOptions.useDefaultListeners

The default value for TestNGOptions.useDefaultListeners has changed from `true` to `false` as Gradle now generates test results and reports for JUnit and TestNG.

### Updated default versions of Checkstyle and CodeNarc

* Checkstyle plugin: default Checkstyle version changed from 5.5 to 5.6.
* CodeNarc plugin: default CodeNarc version changed from 0.16.1 to 0.18.

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
