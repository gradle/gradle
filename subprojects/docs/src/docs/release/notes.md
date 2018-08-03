## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Use SNAPSHOT plugin versions with the `plugins {}` block

Starting with this release, it is now possible to use SNAPSHOT plugin versions in the `plugins {}` and `pluginManagement {}` blocks.

### Incremental Java compilation by default

This release fixes all known issues of the incremental compiler. It now

- deletes empty package directories when the last class file is removed
- recompiles all classes when module-info files change
- recompiles all classes in a package when that package's package-info changes

It's memory usage has also been reduced. For our own build, its heap usage dropped from 350MB to just 10MB.

We are now confident that the incremental compiler is ready to be used in every build, so it is now the new default setting.

### Nested included builds

Composite builds is a feature that allows a Gradle build to 'include' another build and conveniently use its outputs locally rather than via a binary repository. This makes some common workflows more convenient, such as working on multiple source repositories at the same time to implement a cross-cutting feature. In previous releases, it was not possible for a Gradle build to include another build that also includes other builds, which limits the usefulness of this feature for these workflows. In this Gradle release, a build can now include another build that also includes other builds. In other words, composite builds can now be nested.

There are a number of limitations to be aware of. These will be improved in later Gradle releases:

- A `buildSrc` build cannot include other builds, such as a shared plugin build.
- The root project of each build must have a unique name.

### Periodic cache cleanup

Caching has always been one of the strong suits of Gradle. Over time, more and more persistent caches have been added to improve performance and support new features, requiring more and more disk space on build servers and developer workstations. Gradle now addresses one of the most highly voted issues on GitHub and introduces the following cleanup strategies:

- Version-specific cache directories in `GRADLE_USER_HOME/caches/<gradle-version>/` are checked periodically (at most every 24 hours) for whether they are still in use. If not, directories for release versions are deleted after 30 days of inactivity, snapshot versions after 7 days of inactivity. Moreover, the corresponding Gradle distributions in `GRADLE_USER_HOME/wrapper/dists/` are deleted as well, if present.
- Similarly, after building a project, version-specific cache directories in `PROJECT_DIR/.gradle/<gradle-version>/` are checked periodically (at most every 24 hours) for whether they are still in use. They are deleted if they haven't been used for 7 days.
- Shared versioned cache directories in `GRADLE_USER_HOME/caches/` (e.g. `jars-*`) are checked periodically (at most every 24 hours) for whether they are still in use. If there's no Gradle version that still uses them, they are deleted.
- Files in shared caches used by the current Gradle version in `GRADLE_USER_HOME/caches/` (e.g. `jars-3` or `modules-2`) are checked periodically (at most every 24 hours) for when they were last accessed. Depending on whether the file can be recreated locally or would have to be downloaded from a remote repository again, it will be deleted after 7 or 30 days of not being accessed, respectively.

### Authorization for Maven repositories with custom HTTP headers

Now it is possible to define a custom HTTP header to authorize access to a Maven repository. This enables Gradle to access private Gitlab and TFS repositories
used as Maven repositories or any OAuth2 protected Maven repositories.

### Environment variables kept up-to-date on Java 9+

Early previews of Java 9 failed when Gradle tried to update the environment variables of the daemon to match the client that requested the build.
The final Java 9 version added ways to work around this, but Gradle was not updated accordingly, meaning that environment variables always had the same value as when the daemon was started.
This has now been fixed and the environment variables of the daemon will match the variables of the client again.
Changes to the PATH will be visible to `Exec` tasks and calling `System.getenv` will yield the expected result.

However, we strongly recommend that build and plugin authors use Gradle properties instead of `System.getenv` for a more idiomatic end user experience.

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

### Creating instances of JavaPluginConvention

Instances of this class are intended to be created only by the `java-base` plugin and should not be created directly. Creating instances using the constructor of `JavaPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `java-base` plugin.

### Creating instances of ApplicationPluginConvention

Instances of this class are intended to be created only by the `application` plugin and should not be created directly. Creating instances using the constructor of `ApplicationPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `application` plugin.

### Creating instances of WarPluginConvention

Instances of this class are intended to be created only by the `war` plugin and should not be created directly. Creating instances using the constructor of `WarPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `war` plugin.

### Creating instances of EarPluginConvention

Instances of this class are intended to be created only by the `ear` plugin and should not be created directly. Creating instances using the constructor of `EarPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `ear` plugin.

### Creating instances of BasePluginConvention

Instances of this class are intended to be created only by the `base` plugin and should not be created directly. Creating instances using the constructor of `BasePluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `base` plugin.

### Creating instances of ProjectReportsPluginConvention

Instances of this class are intended to be created only by the `project-reports` plugin and should not be created directly. Creating instances using the constructor of `ProjectReportsPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `project-reports` plugin.

### Adding tasks via TaskContainer.add() and TaskContainer.addAll()

These methods have been deprecated and the `create()` or `register()` methods should be used instead.

## Potential breaking changes

### Kotlin DSL breakages

- `project.java.sourceSets` is now `project.sourceSets`

### Restricting cross-configuration and lifecycle hooks from lazy configuration APIs

In Gradle 4.9, we [introduced a new API](https://blog.gradle.org/preview-avoiding-task-configuration-time) for creating and configuring tasks.

The following hooks are disallowed when called from these new APIs:

- `project.afterEvaluate(Action)` and `project.afterEvaluate(Closure)`
- `project.beforeEvaluate(Action)` and `project.beforeEvaluate(Closure)`

If you attempt to call any of these methods an exception will be thrown. Gradle restricts these APIs because mixing these APIs with lazy configuration can cause hard to diagnose build failures and complexity.

### Cross Account AWS S3 Artifact Publishing

The S3 [repository transport protocol](userguide/repository_types.html#sub:supported_transport_protocols) allows Gradle to publish artifacts to AWS S3 buckets. Starting with this release, every artifact uploaded to an S3 bucket will be equipped with `bucket-owner-full-control` canned ACL. Make sure the used AWS credentials can do `s3:PutObjectAcl` and `s3:PutObjectVersionAcl` to ensure successful artifacts uploads.

    {
        "Version":"2012-10-17",
        "Statement":[
            // ...
            {
                "Effect":"Allow",
                "Action":[
                    "s3:PutObject", // necessary for uploading objects
                    "s3:PutObjectAcl", // required starting with this release
                    "s3:PutObjectVersionAcl" // if S3 bucket versioning is enabled
                ],
                "Resource":"arn:aws:s3:::myCompanyBucket/*"
            }
        ]
    }

See the User guide section on “[Repository Types](userguide/repository_types.html#sub:s3_cross_account)” for more information.

### Changes to the Java Gradle Plugin plugin

- `PluginUnderTestMetadata` and `GeneratePluginDescriptors` were updated to use the Provider API.
- All setters were removed and can be replaced with calls to the new Property `set(...)` method.

## External contributions


We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

- [Björn Kautler](https://github.com/Vampire) - Update Spock version in docs and build init (gradle/gradle#5627)
- [Kyle Moore](https://github.com/DPUkyle) - Use latest Gosu plugin 0.3.10 (gradle/gradle#5855)
- [Mata Saru](https://github.com/matasaru) - Add missing verb into docs (gradle/gradle#5694)
- [Sébastien Deleuze](https://github.com/sdeleuze) - Add support for SNAPSHOT plugin versions in the `plugins {}` block (gradle/gradle#5762)
- [Ben McCann](https://github.com/benmccann) - Decouple Play and Twirl versions (gradle/gradle#2062)
- [Mike Kobit](https://github.com/mkobit) - Add ability to use `RegularFile` and `Directory` as publishable artifacts (gradle/gradle#5109)
- [Mészáros Máté Róbert](https://github.com/mrmeszaros) - Fix typo in userguide java plugin configuration image (gradle/gradle#6011)
- [Paul Wellner Bou](https://github.com/paulwellnerbou) - Authorization for Maven repositories with custom HTTP headers (gradle/gradle#5571)
- [Kenzie Togami](https://github.com/kenzierocks) - Docs are unclear on how JavaExec parses --args (gradle/gradle#6056)
- [Salvian Reynaldi](https://github.com/salvianreynaldi) - Give S3 bucket owner full control over the published Maven artifacts (gradle/gradle#5329)
- [Thomas Broyer](https://github.com/tbroyer) - Convert `java-gradle-plugin` to use lazy configuration API (gradle/gradle#6115)
- [Sebastian Schuberth](https://github.com/sschuberth) - Fix an application plugin example to work for Windows (gradle/gradle#5994)

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
