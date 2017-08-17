## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Support for Google Cloud Storage backed repositories

It is now possible to consume dependencies from, and publish to, [Google Cloud Storage](https://cloud.google.com/storage/) buckets when using [`MavenArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.MavenArtifactRepository.html) or [`IvyArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html).

    repositories {
        maven {
            url "gcs://someGcsBucket/maven2"
        }

        ivy {
            url "gcs://someGcsBucket/ivy"
        }
    }

Downloading dependencies from Google Cloud Storage is supported for Maven and Ivy type repositories as shown above. Publishing to Google Cloud Storage is supported with both the [Ivy Publishing](userguide/publishing_ivy.html) and [Maven Publishing](userguide/publishing_maven.html) plugins, as well as when using an `IvyArtifactRepository` with an `Upload` task (see section [publishing artifacts of the user guide](userguide/artifact_management.html#sec:publishing_artifacts)).

Please see the [repositories section of the dependency management chapter](userguide/dependency_management.html#sec:repositories) in the user guide for more information on configuring Google Cloud Storage repository access.

### Features for easier plugin authoring

#### Nested DSL elements

While it is easy for a plugin author to extend the Gradle DSL to add top level blocks to the DSL using project extensions, in previous versions of Gradle it was awkward to create a deeply nested DSL inside these top level blocks, often requiring the use of internal Gradle APIs.

In this release of Gradle, API methods have been added to allow a plugin author to create nested DSL elements. See the [example in the user guide](userguide/custom_plugins.html#sec:nested_dsl_elements) section on custom plugins.

#### Declaring the output of a task as a publish artifact

In previous versions of Gradle, it was not possible to declare the output of a task as a publish artifact in a way that deals with changes to the build directory and other configuration. A publish artifact makes a file or directory available to be referenced by a project dependency or published to a binary repository.

TBD: The publish artifact DSL now accepts `Provider<File>`, `Provider<RegularFile>` and `Provider<Directory>` instances, which allows a plugin author to easily wire up a particular task output as a publish artifact in a way that respects configuration changes.

#### Groovy DSL support for properties of type `PropertyState`

The last several version of Gradle have been steadily adding new features for plugin authors that build on the `Provider<T>` and `PropertyState<T>` types. This version of Gradle adds some DSL conveniences for working with these types.

TBD: The Groovy DSL adds convenience methods to set the value of a property whose type is `PropertyState<T>` using any value of `T` or a `Provider<T>`. This makes the DSL clearer when configuring such a property, including wiring the output of one task in as the input of some other task or setting output locations relative to some configurable value, such as the build directory.  

### Safer handling of stale output files

In previous releases, tasks could produce incorrect results when output files were left behind during upgrades or when processes outside of Gradle created files in a shared output directory.
Gradle is able to detect these situations and automatically remove stale files, if it is safe to do so.
Only files within `buildDir`, paths registered as targets for the `clean` task and source set outputs are considered safe to remove.

### CLI abbreviates long test names

In Gradle 4.1, the Gradle CLI began displaying tests in-progress. We received feedback that long packages and test names caused the test name to be truncated or omitted. This version will abbreviate java packages of long test names to 60 characters to make it highly likely to fit on terminal screens.

### Better support for script plugins loaded via HTTP

Script plugins are applied to Gradle settings or projects via the `apply from: 'URL'` syntax. Support for `http://` and `https://` URLs has been improved in this release:

- HTTP script plugins are cached for [`--offline`](userguide/dependency_management.html#sub:cache_offline) use.
- Download of HTTP script plugins honours [proxy authentication settings](userguide/build_environment.html#sec:accessing_the_web_via_a_proxy).

### Naming task actions defined with doFirst {} and doLast {}

Task actions that are defined in build scripts can now be named using `doFirst("First things first") {}` or `doLast("One last thing") {}`. Gradle uses the names for logging, which allows the user, for example, to see the order in which actions are executed in the task execution views of IDEs. The action names will also be utilised in build scans in the future. This feature is supported in both Kotlin and Groovy build scripts.

### Improved Play support

#### Support for Play 2.6

TODO Placeholder

#### Only trigger a rebuild when someone reloads the Play app

### Faster zipTree and tarTree

The `zipTree` and `tarTree` implementations had a major performance issue, unpacking files every time the tree was traversed. This has now been fixed and should speed up builds using these trees a lot.

TODO Placeholder

#### Support for built-in Twirl template types

Gradle now supports built-in Twirl templates for HTML, JavaScript, TXT and XML.

#### Support for custom Twirl template formats

A [`TwirlSourceSet`](dsl/org.gradle.language.twirl.TwirlSourceSet.html) can now be configured to use user-defined template formats.

#### Support for additional imports in Twirl templates

Gradle now supports adding arbitrary imports for packages and classes to the Scala code generated by the Play Twirl template compiler.

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
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Removed `TaskFilePropertyBuilder.withPathSensitivity` and `TaskOutputFilePropertyBuilder.withPathSensitivity`

These methods where not meant to be used, since Gradle does not allow to customize the PathSensitivity for output files.

### Upgraded the bndlib to 3.3.0

Gradle previously depended on `biz.aQute.bnd:biz.aQute.bndlib:3.2.0`. That version of the library didn't support Java 9.
To fix [issue 2583](https://github.com/gradle/gradle/issues/2583), we upgraded to version 3.3.0 of the library. This
should cause no changes as it is a minor version release.

<!--
### Example breaking change
-->

### FindBugs plugin does not render analysis progress anymore

As observed by many users the FindBugs plugin renders a lot of progress information by default leading to longer, unmanageable logs. The output behavior changes with this release. By default the FindBugs plugin will render no more analysis progress. If you happen to post-process the output and relied on the pre-4.2 behavior, then you can enable the progressing logging with the property `FindBugsExtension.showProgress`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
 -->
 - [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Use correct signature for Test::useJunit (#2675)
 - [Marcin Erdmann](https://github.com/erdi) - Add compilationClasspath property to CodeNarc task (#2325)
 - [Bo Zhang](https://github.com/blindpirate) - Add an option to FindBugs for toggling logging of analysis progress (#2181)
 - [Josué Lima](https://github.com/josuelima) - Fix typo on S3 AwsImAuthentication log message (#2349)
 - [Ian Kerins](https://github.com/CannedYerins) - Fix grammar error in logging documentation (#2482)
 - [Yannick Welsch](https://github.com/ywelsch) - Use GNU-style release flag for Java 9 compiler (#2474)
 - [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Register classloaders as parallelCapable (#772)
 - [Lance](https://github.com/uklance) - Fix Maven BOM evaluation order (#2282)
 - [Jokubas Dargis](https://github.com/eleventigerssc) - Add GCS transport protocol support for declaring dependencies (#2258)
 - [Thomas Halm](https://github.com/thhalm) - Maintain order of classpath when generating start scripts (#2513)
 - [Colin Dean](https://github.com/colindean) - Prevent NullPointerException if any of the signing properties is null but signing isn't required (#2268)
 - [Ben McCann](https://github.com/benmccann) - Add support for Play 2.6 (#1992)
 - [Ethan Hall](https://github.com/ethankhall) - Cache script plugins loaded via HTTP (#1944)
 - [Bo Zhang](https://github.com/blindpirate) - Handle null Throwable stack trace to avoid NullPointerException (#2168)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
