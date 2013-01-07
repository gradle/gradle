## New and noteworthy

Here are the new features introduced in this Gradle release.

### Easier embedding of Gradle via [Tooling API](userguide/embedding.html)

We are continuously looking for ways to improve the experience of embedding Gradle.
The standard way to embed Gradle, the [Tooling API](userguide/embedding.html) used to ship as multiple Jars, some of which were third-party libraries.
In Gradle 1.4, we refactored the publication and packaging of the Tooling API. It now ships as a single Jar whose only external dependency is SLF4J.
All other third-party dependencies are packaged inside the Jar and shaded to avoid conflicts with the embedder's classpath. Happy embedding! Now go and embed Gradle!

### Dependency resolution improvements

- GRADLE-2175 - Source, javadoc and classifier artifacts from Maven snapshots are correctly treated as changing.
- GRADLE-2364 - `--offline` works after resolving against a broken repository.
- GRADLE-2185 - Faster resolution of Maven snapshots.
- GRADLE-1919 - Added `m2Compatible` option.
- GRADLE-2546 - Faster searching for local candidates.

### Improvements to dependency resolution reports

Dependency resolution reports now show dependencies that couldn't be resolved. Here is an example for the `dependencies` task:

    compile - Classpath for compiling the sources.
    \--- foo:bar:1.0
         \--- foo:baz:2.0 FAILED

The `FAILED` marker indicates that `foo:baz:2.0`, which is depended upon by `foo:bar:1.0`, couldn't be resolved.

A similar improvement has been made to the `dependencyInsight` task:

    foo:baz:2.0 (forced) FAILED

    foo:baz:1.0 -> 2.0 FAILED
    \--- foo:bar:1.0
         \--- compile

In this example, `foo:baz` was forced to version `2.0`, and that version couldn't be resolved.

### Automatic configuration of Groovy dependency used by GroovyCompile and Groovydoc tasks

The `groovy-base` plugin now automatically detects the Groovy dependency used on the class path of any `GroovyCompile` or `Groovydoc` task,
and adds a corresponding dependency declaration (e.g. `org.codehaus.groovy:groovy-all:2.0.5`) for the task's `groovyClasspath`.
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
and adds a corresponding dependency declaration (e.g. `org.scala-lang:scala-compiler:2.9.2`) for the task's `scalaClasspath`.
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

    $ ls build/libs
    scala2_10.jar scala2_8.jar  scala2_9.jar

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

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Incubating features

Incubating features are intended to be used, but not yet guaranteed to be backwards compatible.
By giving early access to new features, real world feedback can be incorporated into their design.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the new incubating features or changes to existing incubating features in this Gradle release.

### Hooking into dependency resolution

In order to make Gradle's dependency resolution even more robust
we added a new way of influencing the dependency resolution process.
Since Gradle 1.4 it is possible to specify *dependency resolve rules*.
The rule is executed for each resolved dependency and offers ways
to manipulate the dependency metadata before the dependency is resolved.

The feature is incubating for now and not entire dependency metadata can be manipulated at this time.
We are very keen on your feedback and we will definitely grow this feature,
allowing more metadata to be manipulated, and more dependency resolution corner cases solved.
Even though dependency resolve rules are lower level hooks
in future we will use them to provide many high level features in Gradle's dependency engine.

Many interesting use cases can be implemented with the dependency resolve rules:

* [Blacklisting a version] (userguide/userguide_single.html#sec:blacklisting_version) with a replacement.
* Implementing a [custom versioning scheme](userguide/userguide_single.html#sec:custom_versioning_scheme).
* [Modelling a releasable unit](userguide/userguide_single.html#sec:releasable_unit) - a set of related libraries that require a consistent version

See below example on how to make all libraries from group 'org.gradle' use a consistent version:

    configurations.all {
        resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (details.requested.group == 'org.gradle') {
                details.useVersion '1.4'
            }
        }
    }

For more information, including more code samples, please refer to this [user guide section](userguide/userguide_single.html#sec:dependency_resolve_rules).

### Generate `ivy.xml` without publishing

The 'ivy-publish' plugin introduces a new GenerateIvyDescriptor task, which permits the generation of the `ivy.xml` metadata file without also publishing
your module to an Ivy repository. The task name for the default Ivy publication is 'generateIvyModuleDescriptor'.

The `GenerateIvyDescriptor` task also allows the location of the generated Ivy descriptor file to changed from its default location at `build/publications/ivy/ivy.xml`.
This is done by setting the `destination` property of the task:

    apply plugin: 'ivy-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    generateIvyModuleDescriptor {
        destination = 'generated-ivy.xml'
    }

Executing `gradle generateIvyModuleDescriptor` will result in the Ivy module descriptor being written to the file specified. This task is automatically wired
into the respective PublishToIvyRepository tasks, so you do not need to explicitly call this task to publish your module.

### The new maven-publish plugin

Continuing our work to improve the way that Gradle models and publishes your build artifacts, we have introduced the new 'maven-publish' plugin.
This plugin is the companion to the 'ivy-publish' plugin that was introduced in Gradle 1.3, but handles publishing modules to Maven repositories.

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

In this example we are adding a 'description' element for the generated POM. With this hook, you can modify any aspect of the POM.
For example, you could replace the version range for a dependency with the actual version used to produce the build.
This allows the POM file to describe how the module should be consumed, rather than be a description of how the module was built.

For more information on the new publishing mechanism, see the [new User Guide chapter](userguide/publishing_maven.html).

### [Java Library Distribution Plugin](userguide/javaLibraryDistributionPlugin.html)

Thanks to a contribution by Sébastien Cogneau, it is now much easier to create a standalone distribution for a JVM library.

Let's walk through a small example. Let's assume we have a project with the following layout:

    MyLibrary
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

In the past, it was necessary to declare a custom `zip` task that assembles the distribution. Now, the Java Library Distribution Plugin will do the job for you:

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
