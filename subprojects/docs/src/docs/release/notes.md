## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Features for easier plugin authoring

While it is easy for a plugin author to extend the Gradle DSL to add top level blocks to the DSL using project extensions, in previous versions of Gradle it was awkward to create a deeply nested DSL inside these top level blocks, often requiring the use of internal Gradle APIs.

In this release of Gradle, API methods have been added to allow a plugin author to create nested DSL elements. See the [example in the user guide](userguide/custom_plugins.html#sec:nested_dsl_elements) section on custom plugins.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
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
 - [Marcin Erdmann](https://github.com/erdi) - Add compilationClasspath property to CodeNarc task (#2325)
 - [Bo Zhang](https://github.com/blindpirate) - Add an option to FindBugs for toggling logging of analysis progress (#2181)
 - [Josué Lima](https://github.com/josuelima) - Fix typo on S3 AwsImAuthentication log message (#2349)
 - [Ian Kerins](https://github.com/CannedYerins) - Fix grammar error in logging documentation (#2482)
 - [Yannick Welsch](https://github.com/ywelsch) - Use GNU-style release flag for Java 9 compiler (#2474)
 - [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Register classloaders as parallelCapable (#772)
 - [Lance](https://github.com/uklance) - Fix Maven BOM evaluation order (#2282)
 - [Jokubas Dargis](https://github.com/eleventigerssc) - Add GCS transport protocol support for declaring dependencies (#2258)
 - [Thomas Halm](https://github.com/thhalm) - Maintain order of classpath when generating start scripts (#2513)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
