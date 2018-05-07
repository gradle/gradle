## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Better control over system include path for native compilation

In previous versions of Gradle, the native compile task include path was a single monolithic collection of files that was accessible through the `includes` property on the compile task.
In Gradle 4.8, system header include directories can now be accessed separately via the `systemIncludes` property. 
On GCC-compatible toolchains, the system header include directories specified with `systemIncludes` will be specified on the command line using the ["-isystem" argument](https://gcc.gnu.org/onlinedocs/gcc/Directory-Options.html), which marks them for special treatment by the compiler.   

### CodeNarc upgrade

The default version of CodeNarc used by the [`codenarc plugin`](userguide/codenarc_plugin.html) is [`1.1`](https://github.com/CodeNarc/CodeNarc/blob/master/CHANGELOG.md#version-11-jan-2018).

### Ant upgrade

The embedded version of Ant used by Gradle is [`1.9.11`](https://archive.apache.org/dist/ant/RELEASE-NOTES-1.9.11.html).

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

### TextResources can now be fetched from a URI

Text resources like a common checkstyle configuration file can now be fetched directly from a URI. Gradle will apply the same caching that it does for remote build scripts.

    checkstyle {
        config = resources.text.fromUri("http://company.com/checkstyle-config.xml)"
    }


## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

## Fixed issues

### Nested `afterEvaluate` requests are no longer silently ignored

Before this release, `afterEvaluate` requests happening during the execution of an `afterEvaluate` callback were silently ignored.

Consider the following code:

```gradle
afterEvaluate {
    println "> Outer"
    afterEvaluate {
        println "Inner"
    }
    println "< Outer"
}
```

In Gradle 4.7 and below, it would print:

```text
> Outer
< Outer
```

With the `Inner` part being silently ignored.

Starting with Gradle 4.8, nested `afterEvaluate` requests will be honoured asynchronously in order to preserve the callback _execute later_ semantics, in other words, the same code will now print:

```text
> Outer
< Outer
Inner
```

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

## Potential breaking changes

### Changed behaviour for missing init scripts

In previous releases of Gradle, an init script specified on the command line that did not exist would be silently ignored. In this release, the build will fail if any of the init scripts specified on the command line does not exist.

<!--
### Example breaking change
-->

### TaskContainer.remove() now actually removes the task

TBD - previously this was broken, and plugins may accidentally rely on this behaviour.

### Signature.setFile() no longer changes the file to be published

Previously, `Signature.setFile()` could be used to replace the file used for publishing a `Signature`. However, the actual signature file was still being generated at its default location. Therefore, `Signature.setFile()` is now deprecated and will be removed in a future release.

### Parsing of `exclusions` in Maven POM now accepts implicit wildcards

Previously, a Maven POM with an `exclusion` missing either the `groupId` or the `artifactId` was ignored by Gradle.
This is no longer the case and thus may cause modules to be excluded from a dependency graph that were previously included.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Lucas Smaira](https://github.com/lsmaira) Introduce support for running phased actions (gradle/gradle#4533)
- [Filip Hrisafov](filiphr) Add support for URI backed TextResource (gradle/gradle#2760)
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
