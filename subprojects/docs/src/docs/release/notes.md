The Gradle team is pleased to announce Gradle 4.8.

First and foremost, this release of Gradle features [dependency locking](#locking-of-dynamic-dependencies): a mechanism for locking dependency versions which allows builds to become reproducible in the face of dynamic versions or version ranges. 
Read the [user manual chapter on dependency locking](userguide/dependency_locking.html) to learn how to take advantage of this exciting new feature.
 
The publishing plugins get some much-needed improvements in this release:

 * The Signing Plugin now supports [signing all artifacts of a publication](#signing-publications).
 * The Maven Publish Plugin now provides a dedicated, type-safe [DSL to customize the POM generated](#customizing-the-generated-pom) as part of a Maven publication.
 * Configuration-wide [dependency excludes are now published](#configuration-wide-dependency-excludes-are-now-published)

The `maven-publish` and `ivy-publish` plugins are now considered stable and use of the `maven` plugin is discouraged as it will eventually be deprecated — please migrate.

User experience for [incremental annotation processing is improved](#improved-incremental-annotation-processing). 
Compilation will no longer fail when a processor does something that Gradle detects will not work incrementally. 
Unused non-incremental processors no longer prevent incremental compilation. 
Finally, annotation processors are now able to decide dynamically if they are incremental or not. 
This allows processors with extension mechanisms to check extensions for incrementality before enabling incremental annotation processing.

New native plugins continue to improve with [better control over system include path](#better-control-over-system-include-path-for-native-compilation) for native compilation and [other improvements](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-48). 

Gradle 4.8 includes Kotlin DSL 0.17.4 bringing the latest Kotlin 1.2.41 release and many improvements to the user experience including location aware runtime error reporting, convenient configuration of nested extensions, faster and leaner configuration time and TestKit support.
At the same time the IntelliJ IDEA Kotlin Plugin fixed many long standing build script editing related issues.
See details and examples in the [Kotlin DSL v0.17 release notes](https://github.com/gradle/kotlin-dsl/releases/tag/v0.17.4).

We hope you build happiness with Gradle 4.8, and we look forward to your feedback [via Twitter](https://twitter.com/gradle) or [on GitHub](https://github.com/gradle).

## Upgrade instructions

Switch your build to use Gradle 4.8 RC1 quickly by updating your wrapper properties:

    gradle wrapper --gradle-version=4.8-rc-1

Standalone downloads are available at [gradle.org/release-candidate](https://gradle.org/release-candidate). 

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Locking of dynamic dependencies

Gradle now provides a mechanism for locking dynamic versions.
It makes builds reproducible even when declaring dependencies using version ranges.

This enables, amongst others, the following scenarios:

* Companies dealing with multi repositories no longer need to rely on `-SNAPSHOT` or changing dependencies,
which sometimes result in cascading failures when a dependency introduces a bug or incompatibility.
Now dependencies can be declared against major or minor version range, enabling to test with the latest versions on CI while leveraging locking for stable developer builds.
* Teams that want to always use the latest of their dependencies can use dynamic versions, locking their dependencies only for releases.
The release tag will contain the lock files, allowing that build to be fully reproducible when bug fixes need to be developed.

In order to use dependency locking, you first enable it on configurations:

    dependencyLocking {
        lockAllConfigurations()
    }

You then run a build, telling Gradle to persist lock state:

    gradle test --write-locks

Assuming you add the lock files to source control, from this point onward, all configurations that have a lock state will resolve the locked versions.

Changes to published dependencies will not impact your build, you will have to re-generate or update the lock before they are considered as dependencies.
Similarly, changes to your build script that would impact the resolved set of dependencies will cause it to fail,
ensuring the dependencies do not change without a matching update in the lock file.

Head over to the [dependency locking documentation](userguide/dependency_locking.html) for more details on using this feature.

### Configuration time reduction

We eliminated some configuration bottlenecks, which improved configuration time by 10-15% in our performance builds across Java and Android projects. The Gradle build saw a 12% improvement (1.7 seconds to 1.5 seconds). Configuration time improvements speed up IDE import and IDE interactions with multi-project builds with many subprojects.

### Signing Publications

The [Signing Plugin](userguide/signing_plugin.html) now supports signing all artifacts of a publication, e.g. when publishing artifacts to a Maven or Ivy repository.

    publishing {
      publications {
        mavenJava(MavenPublication) {
          from components.java
        }
      }
    }

    signing {
      sign publishing.publications
    }

### Customizing the generated POM

The [Maven Publish Plugin](userguide/publishing_maven.html) now provides a dedicated, type safe DSL to customize the POM generated as part of a Maven publication. The following sample demonstrates some of the new properties and methods. Please see the [DSL Reference](dsl/org.gradle.api.publish.maven.MavenPom.html) for the complete documentation.

    publishing {
      publications {
        mavenJava(MavenPublication) {
          from components.java
          pom {
            name = "Demo"
            description = "A demonstration of Maven POM customization"
            url = "http://www.example.com/project"
            licenses {
              license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
              }
            }
            developers {
              developer {
                id = "johnd"
                name = "John Doe"
                email = "john.doe@example.com"
              }
            }
            scm {
              connection = "scm:svn:http://subversion.example.com/svn/project/trunk/"
              developerConnection = "scm:svn:https://subversion.example.com/svn/project/trunk/"
              url = "http://subversion.example.com/svn/project/trunk/"
            }
          }
        }
      }
    }

### Configuration-wide dependency excludes are now published

The [Ivy Publish Plugin](userguide/publishing_ivy.html) now writes dependency exclude rules defined on a configuration (instead of on an individual dependency) into the generated Ivy module descriptor; the [Maven Publish Plugin](userguide/publishing_maven.html) now repeats them for each dependency in the generated POM.

### Improved incremental annotation processing

This version of Gradle provides the following improvements to [incremental annotation processing APIs](userguide/java_plugin.html#sec:incremental_annotation_processing).

 * Compilation will no longer fail when a processor does something that Gradle knows won't work incrementally. Instead Gradle will simply do a full recompile and inform the user about the problem.
 * Unused non-incremental processors will no longer prevent incremental compilation. This means that incremental and non-incremental processors can be safely packaged together. As long as the user doesn't use the non-incremental processor in their code, everything will remain incremental.
 * Processors will be able to decide dynamically if they are incremental or not. Some annotation processors have extension mechanisms that first need to check all extensions for incrementality before they can opt-in. This new mechanism will supersede the META-INF registration.
 
If you maintain a annotation processor, please read [the documentation](userguide/java_plugin.html#sec:incremental_annotation_processing) to learn how to enable incremental annotation processing for your library. 

### Better control over system include path for native compilation

[The Gradle Native project continues](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-48) to improve and evolve the native ecosystem support for Gradle.

### TextResources can now be fetched from a URI

Text resources like a common checkstyle configuration file can now be fetched directly from a URI. Gradle will apply the same caching that it does for remote build scripts.

    checkstyle {
        config = resources.text.fromUri("http://company.com/checkstyle-config.xml")
    }


### Create tasks with constructor arguments

You can now create a task and pass values to its constructor.
This can be useful for tasks created by plugins that should not be configured directly by users.

In order to pass values to the constructor, the constructor has to be annotated with `@javax.inject.Inject`.
Given the following `Task` class:

    class CustomTask extends DefaultTask {
        final String message
        final int number

        @Inject
        CustomTask(String message, int number) {
            this.message = message
            this.number = number
        }

        @TaskAction
        void doSomething() {
            println("Hello $number $message")
        }
    }

You can then create a task, passing the constructor arguments at the end of the parameter list.

    tasks.create('myTask', CustomTask, 'hello', 42)

In a Groovy build script, you can create the task using `constructorArgs`.

    task myTask(type: CustomTask, constructorArgs: ['hello', 42])

In a Kotlin build script, you can pass constructor arguments using the reified extension function on the `tasks` `TaskContainer`.

    tasks.create<CustomTask>("myTask", "hello", 42)

More details are available in the [User guide](userguide/more_about_tasks.html#sec:passing_arguments_to_a_task_constructor).

### Immutable file collections

It is now possible to create immutable file collections by using [`ProjectLayout.files(Object...)`](javadoc/org/gradle/api/file/ProjectLayout.html#files-java.lang.Object...-).
There is also a new method for creating configurable file collections, [`ProjectLayout.configurableFiles(Object...)`](javadoc/org/gradle/api/file/ProjectLayout.html#configurableFiles-java.lang.Object...-), which will replace `Project.files()` in the long run.
`FileCollection`s created by `ProjectLayout.files()` are performing slightly better and should be used whenever mutability is not required.
For example, they fit nice when creating tasks with constructor arguments.

### CodeNarc upgrade

The default version of CodeNarc used by the [`codenarc plugin`](userguide/codenarc_plugin.html) is [`1.1`](https://github.com/CodeNarc/CodeNarc/blob/master/CHANGELOG.md#version-11-jan-2018).

### Ant upgrade

The embedded version of Ant used by Gradle is [`1.9.11`](https://archive.apache.org/dist/ant/RELEASE-NOTES-1.9.11.html).

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Ivy Publish and Maven Publish Plugins marked stable

The [Ivy Publish Plugin](userguide/publishing_ivy.html) and [Maven Publish Plugin](userguide/publishing_maven.html) that have been _incubating_ since Gradle 1.3 are now marked as stable. Both plugins now [support signing](#signing-publications) and [publish configuration-wide dependency excludes](#configuration-wide-dependency-excludes-are-now-published). The [Maven Publish Plugin](userguide/publishing_maven.html) introduces a new dedicated DSL for [customizing the generated POM](#customizing-the-generated-pom). In addition, some usage quirks with the `publishing` extension have been addressed which now [behaves like other extension objects](https://github.com/gradle/gradle/issues/4945). Thus, these plugins are now the preferred option for publishing artifacts to Ivy and Maven repositories, respectively.

## Fixed issues

### Nested `afterEvaluate` requests are no longer silently ignored

Before this release, `afterEvaluate` requests happening during the execution of an `afterEvaluate` callback were silently ignored.

Consider the following code:

    afterEvaluate {
        println "> Outer"
        afterEvaluate {
            println "Inner"
        }
        println "< Outer"
    }

In Gradle 4.7 and below, it would print:

    > Outer
    < Outer

With the `Inner` part being silently ignored.

Starting with Gradle 4.8, nested `afterEvaluate` requests will be honoured asynchronously in order to preserve the callback _execute later_ semantics, in other words, the same code will now print:

    > Outer
    < Outer
    Inner

Please note that `beforeEvaluate` and other similar hooks have *not* been changed and will still silently ignore nested requests, that behaviour is subject to change in a future Gradle release (gradle/gradle#5262).

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Deferred configuration of the publishing block

Until Gradle 4.7, the `publishing {}` block was implicitly treated as if all the logic inside it was executed after the project is evaluated.
This caused quite a bit of confusion, because it was the only block that behaved that way.
As part of the stabilization effort in Gradle 4.8, we are deprecating this behavior and asking all users to migrate their build.

Please see the [migration guide](userguide/publishing_maven.html#publishing_maven:deferred_configuration) for more information.

### Methods on `FileCollection`

- `FileCollection.add()` is now deprecated. Use `ConfigurableFileCollection.from()` instead. You can create a `ConfigurableFileCollection` via `Project.files()`.
- `FileCollection.stopExecutionIfEmpty()` is deprecated without a replacement. You can use `@SkipWhenEmpty` on a `FileCollection` property, or throw a `StopExecutionException` in your code manually instead.

### Method on `Signature`

`Signature.getToSignArtifact()` should have been an internal API and is now deprecated without a replacement.

### `SimpleFileCollection`

The internal `SimpleFileCollection` implementation of `FileCollection` has been deprecated.
You should use `Project.files()` instead.

### Use of single test selection system property

The [use of a system property](userguide/java_testing.html#sec:single_test_execution_via_system_properties) to select which tests to execute is deprecated.  The built-in `--tests` filter has long replaced this functionality.

### Use of remote debugging test system property

The use of a system property (`-Dtest.debug`) to enable remote debugging of test processes is deprecated.  The built-in `--debug-jvm` flag has long replaced this functionality.

### Overwriting Gradle's built-in tasks

Defining a custom `wrapper` or `init` task is deprecated. Please configure the existing tasks instead.

I.e. instead of this:

    task wrapper(type:Wrapper) {
        //configuration
    }
    
Do this:

    wrapper {
        //configuration
    }

## Potential breaking changes

### Changed behaviour for missing init scripts

In previous releases of Gradle, an init script specified on the command line that did not exist would be silently ignored. In this release, the build will fail if any of the init scripts specified on the command line does not exist.

<!--
### Example breaking change
-->

### TaskContainer.remove() now actually removes the task

Previously, calling `TaskContainer.remove()` didn't _actually_ remove the task but now it does.
Since plugins may have accidentally relied on this behavior, please check whether you're calling this method and, if so, verify your plugin works as expected.

### Signature.setFile() no longer changes the file to be published

Previously, `Signature.setFile()` could be used to replace the file used for publishing a `Signature`. However, the actual signature file was still being generated at its default location. Therefore, `Signature.setFile()` is now deprecated and will be removed in a future release.

### Parsing of `exclusions` in Maven POM now accepts implicit wildcards

Previously, a Maven POM with an `exclusion` missing either the `groupId` or the `artifactId` was ignored by Gradle.
This is no longer the case and thus may cause modules to be excluded from a dependency graph that were previously included.

### Changes to the Gradle Kotlin DSL

The Kotlin DSL now respects the JSR-305 package annotations.
As a result, annotated Java elements whose types were previously seen as Kotlin platform types, thus non-nullable, will now be seen as effectively either non-nullable or nullable types depending on the JSR-305 annotations.
This change could cause script compilation errors.

See the [release notes](https://github.com/gradle/kotlin-dsl/releases/tag/v0.17.4) for more information.

### Changes to plain console behavior

In Gradle 4.7, the plain console was changed so that task output was grouped and it was easy to determine which task produced a given item of output.
With this change, error messages were also grouped and sent to the standard output stream so that they could also be grouped with other task output.
Unfortunately, this made it impossible to redirect error messages to a different file handle and while it improved the experience for console users, it worsened the experience for others who preferred to keep error output separate.
We have changed this behavior now so that error messages are only grouped on standard output when a console is attached to both standard output and standard error.
In all other scenarios, error messages are sent to standard error similar to how they were handled in versions previous to Gradle 4.7. 

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Lucas Smaira](https://github.com/lsmaira) Introduce support for running phased actions (gradle/gradle#4533)
- [Filip Hrisafov](https://github.com/filiphr) Add support for URI backed TextResource (gradle/gradle#2760)
- [Florian Nègre](https://github.com/fnegre) Fix distribution plugin documentation (gradle/gradle#4880)
- [Andrew Potter](https://github.com/apottere) Update pluginManagement documentation to mention global configuration options (gradle/gradle#4999)
- [Patrik Erdes](https://github.com/patrikerdes) Fail the build if a referenced init script does not exist (gradle/gradle#4845)
- [Emmanuel Debanne](https://github.com/debanne) Upgrade CodeNarc to version 1.1 (gradle/gradle#4917)
- [Alexandre Bouthinon](https://github.com/alexandrebouthinon) Fix NullPointerException (gradle/gradle#5199)
- [Paul Eddie](https://github.com/paul-eeoc) Fix typo (gradle/gradle#5180)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
