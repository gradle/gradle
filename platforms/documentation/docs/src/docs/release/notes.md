The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [public roadmap](https://blog.gradle.org/roadmap-announcement) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

<a name="java-22"></a>
### Full Java 22 support

Gradle 8.7 supported compiling and testing with Java 22 using [Java toolchains](userguide/toolchains.html), but running Gradle itself on Java 22 still needed support.

With this release, Gradle fully supports compiling, testing, and running on [Java 22](https://jdk.java.net/22/).

For details, see the full [compatibility documentation](userguide/compatibility.html#java).

<a name="build-cache"></a>
### Build cache improvements

The [Gradle build cache](userguide/build_cache.html) is a mechanism designed to save time by reusing local or remote outputs from previous builds.

#### Improved control of local build cache cleanup

Previously, cleanup of the local build cache (`~/.gradle/caches/build-cache-1`) ran every 24 hours, and this interval could not be configured.
The retention period was configured via the `DirectoryBuildCache.removeUnusedEntriesAfterDays` setting.

With this release, local build cache cleanup is configurable via the [standard init-script mechanism](userguide/directory_layout.html#dir:gradle_user_home:cache_cleanup), providing improved control and consistency.
- Specifying `Cleanup.DISABLED` or `Cleanup.ALWAYS` will now prevent or force the cleanup of the local build cache
- Build cache entry retention is now configured via `Settings.caches.buildCache.setRemoveUnusedEntriesAfterDays()`

If you want build-cache entries to be retained for 30 days, remove any calls to the deprecated method:
<strike>
```kotlin
  buildCache {
      local {
          removeUnusedEntriesAfterDays = 30
      }
  }
```
</strike>

And add a block like this in `~/.gradle/init.d`:
```kotlin
beforeSettings {
    caches {
        buildCache.setRemoveUnusedEntriesAfterDays(30)
    }
}
```

<a name="config-cache"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing it for subsequent builds.
This feature can significantly improve build performance.

#### Support for Java Record classes and `Externalizable` instances

The configuration cache now supports:
* [Java Record classes](https://docs.oracle.com/en/java/javase/21/language/records.html)
* [java.io.Externalizable instances](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Externalizable.html)

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides rich APIs and [lazy configuration](userguide/lazy_configuration.html) for plugin authors and build engineers to develop custom build logic.

#### File Permissions API is now stable

The File Permissions API for defining file permissions using UNIX style values (added in [Gradle 8.3](https://docs.gradle.org/8.3/release-notes.html#kotlin-dsl-improvements)) is now stable; see:

* [FilePermissions](javadoc/org/gradle/api/file/FilePermissions.html)
* [ConfigurableFilePermissions](javadoc/org/gradle/api/file/ConfigurableFilePermissions.html)
* [CopyProcessingSpec.getFilePermissions()](javadoc/org/gradle/api/file/CopyProcessingSpec.html#getFilePermissions--)
* [CopyProcessingSpec.filePermissions(Action)](javadoc/org/gradle/api/file/CopyProcessingSpec.html#filePermissions-org.gradle.api.Action-)
* [CopyProcessingSpec.getDirPermissions()](javadoc/org/gradle/api/file/CopyProcessingSpec.html#getDirPermissions--)
* [CopyProcessingSpec.dirPermissions(Action)](javadoc/org/gradle/api/file/CopyProcessingSpec.html#dirPermissions-org.gradle.api.Action-)
* [FileCopyDetails.permissions(Action)](javadoc/org/gradle/api/file/FileCopyDetails.html#permissions-org.gradle.api.Action-)
* [FileCopyDetails.setPermissions(FilePermissions)](javadoc/org/gradle/api/file/FileCopyDetails.html#setPermissions-org.gradle.api.file.FilePermissions-)
* [FileSystemOperations.filePermissions(Action)](javadoc/org/gradle/api/file/FileSystemOperations.html#filePermissions-org.gradle.api.Action-)
* [FileSystemOperations.directoryPermissions(Action)](javadoc/org/gradle/api/file/FileSystemOperations.html#directoryPermissions-org.gradle.api.Action-)
* [FileSystemOperations.permissions(int)](javadoc/org/gradle/api/file/FileSystemOperations.html#permissions-int-)
* [FileSystemOperations.permissions(String)](javadoc/org/gradle/api/file/FileSystemOperations.html#permissions-java.lang.String-)
* [FileSystemOperations.permissions(Provider)](javadoc/org/gradle/api/file/FileSystemOperations.html#permissions-org.gradle.api.provider.Provider-)
* [FileTreeElement.getPermissions()](javadoc/org/gradle/api/file/FileTreeElement.html#getPermissions--)

#### Ability to set conventions on file collections

Plugin-provided tasks often expose file collections that are meant for build engineers to customize (for instance, the classpath for the JavaCompile task).
Up until now, for plugin authors to define default values for file collections, they have had to resort to configuring those defaults as initial values.

[Conventions](userguide/implementing_gradle_plugins_binary.html#sec:plugin_conventions) provide a better model wherein plugin authors recommend default values via conventions, and users choose to accept, add on top, or completely replace them when defining their actual value.

This release introduces a pair of [`convention(...)`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html#convention-java.lang.Object...-) methods on `ConfigurableFileCollection` that define the default value of a file collection if no explicit value is previously set via `setFrom(...)` or `from(...)`:

```kotlin
val files = objects.fileCollection().convention("dir1")
files.from("dir2")

println(files.elements.get()) // [.../dir1, .../dir2]
```

`#from(...)` will honor the convention if one is configured when invoked, so the order of operations will matter.

To forcefully override or prevent a convention (i.e., regardless of the order of those operations), one should use `#setFrom()` instead:

```kotlin
val files = objects.fileCollection().convention("dir1")
files.setFrom("dir2")

println(files.elements.get()) // [.../dir2]
```

This feature caters to plugin developers.
It is analogous to the [`convention(...)`](javadoc/org/gradle/api/provider/Property.html#convention-T-) methods that have been available on lazy properties since [Gradle 5.1](https://docs.gradle.org/5.1/release-notes.html#kotlin-dsl-improvements).

#### Updating lazy property based on its current value with `replace()`

Sometimes, it is necessary to modify a property based on its current value, for example, by appending something to it.
Previously, the only way to do that was to obtain the current value explicitly by calling `Property.get()`:

```kotlin
val property = objects.property<String>()
property.set("some value")
property.set("${property.get()} and more")

println(property.get()) // "some value and more""
```

This could lead to performance issues like configuration cache misses.

Trying to build the value [lazily](userguide/lazy_configuration.html), for example, by using `property.set(property.map { "$it and more" })`, causes build failure because of a circular reference evaluation.

[`Property`](javadoc/org/gradle/api/provider/Property.html#replace-org.gradle.api.Transformer-) and [`ConfigurableFileCollection`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html#replace-org.gradle.api.Transformer-) now provide their respective `replace(Transformer<...>)` methods that allow lazily building the new value based on the current one:

```kotlin
val property = objects.property<String>()
property.set("some value")
property.replace { it.map { "$it and more" } }

println(property.get()) // "some value and more"
```

Refer to the Javadoc for [`Property.replace(Transformer<>)`](javadoc/org/gradle/api/provider/Property.html#replace-org.gradle.api.Transformer-) and [`ConfigurableFileCollection.replace(Transformer<>)`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html#replace-org.gradle.api.Transformer-) for more details, including limitations.

<a name="error-warning"></a>
### Error and warning reporting improvements

Gradle provides a rich set of error and warning messages to help you understand and resolve problems in your build.

#### Improved error handling for toolchain resolvers

This update improves how Gradle handles errors when downloading Java toolchains from configured resolvers.

Previously, if a resolver threw an exception while mapping toolchain specs to download URLs or during the auto-provisioning process, Gradle did not try other configured resolvers.
Now, Gradle will handle these errors better by attempting to use other resolvers in such cases.

#### Improved JVM version mismatch error reporting

When depending on a library that requires a higher version of the JVM runtime than [is requested](userguide/building_java_projects.html#sec:java_cross_compilation) via the automatically supplied [`TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE` attribute](javadoc/org/gradle/api/attributes/java/TargetJvmVersion.html), or when [applying a plugin](userguide/plugins.html#sec:plugins_block) that requires a higher version or the JVM runtime than the current JVM supplies, dependency resolution will fail.

The error message in this situation will now clearly state the issue:

```text
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring root project 'example'.
> Could not determine the dependencies of task ':consumer:compileJava'.
  > Could not resolve all task dependencies for configuration ':consumer:compileClasspath'.
     > Could not resolve project :producer.
       Required by:
           project :consumer
        > project :producer requires at least a Java 18 JVM. This build uses a Java 17 JVM.

* Try:
> Run this build using a Java 18 JVM (or newer).
> Change the dependency on 'project :producer' to an earlier version that supports JVM runtime version 17.
```

The failure’s suggested resolutions will include upgrading your JVM or downgrading the version of the dependency.
This replaces the previous low-level incompatibility message, which contained details about all the attributes involved in the plugin request and all the available variants of the dependency.
This message could be quite long and difficult to understand.

This could be especially helpful for users upgrading the Spring Boot plugin to version 3+, which requires Java 17 or later.

#### Fixed error message when buildscript dependencies fail to resolve

When a build script fails to resolve dependencies on its classpath, the error message will now more clearly state the issue:

```text
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring root project 'unified-prototype'.
> Could not resolve all dependencies for configuration ':classpath'.
   > Could not resolve project :unified-plugin:plugin-android.
     ...
```

Previously, the error message contained a `null` and a possibly misleading reference to "task dependencies":

```text
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring root project 'unified-prototype'.
> Could not determine the dependencies of null.
   > Could not resolve all task dependencies for configuration ':classpath'.
      > Could not resolve project :unified-plugin:plugin-android.
        ...
```

#### Fixed error reporting when repositories are disabled

When Gradle determines that a particular repository is unavailable when requesting a dependency, it will stop trying to resolve any dependencies from that repository.
This can prevent other dependencies from resolving successfully.

Previously, this could result in an error message that only mentioned that dependencies failed to resolve due to "Skipped to earlier error", without printing the error itself:

```text
* What went wrong:
A problem occurred configuring root project 'fevi6'.
> Could not resolve all artifacts for configuration ':classpath'.
   > Could not resolve group:a:1.0.
     Required by:
         project :
      > Skipped due to earlier error
   > Could not resolve group:b:1.0.
     Required by:
         project :
      > Skipped due to earlier error
   > Could not resolve group:c:1.0.
     Required by:
         project :
      > Skipped due to earlier error
   ...
```

The new error message will now print the underlying cause of the issue:

```text
* What went wrong:
A problem occurred configuring root project 'fevi6'.
> Could not resolve all artifacts for configuration ':classpath'.
   > Could not resolve group:a:1.0.
     Required by:
         project :
      > Could not resolve group:a:1.0.
         > Could not get resource 'http://127.0.0.1:49179/repo/group/a/1.0/a-1.0.pom'.
            > Could not GET 'http://127.0.0.1:49179/repo/group/a/1.0/a-1.0.pom'.
               > Read timed out
            ...
```

#### Suppressed duplicate error reporting when multiple failures have the same cause

When multiple failures have the same cause, Gradle will now only print the first failure, which includes all the necessary details.

Any additional failures stemming from the same cause will be summarized at the end of the message, indicating how many were found:

```text
* What went wrong:
Execution failed for task ':resolve'.
> Could not resolve all files for configuration ':deps'.
   > Could not resolve group:a:1.0.
     Required by:
         project : > group:d:1.0
      > <SOME FAILURE CAUSE>
> There are 2 more failures with identical causes.
```

<a name="ide-integration"></a>
### IDE Integration improvements

Gradle is integrated into many IDEs using the [Tooling API](userguide/third_party_integration.html).

#### Tests metadata improvements in Tooling API

IDEs and other tools leverage the tooling API to access information about tests executed by Gradle.
Each test event sent via the tooling API includes a test descriptor containing metadata such as a human-readable name, class name, and method name.

We introduced a new method to the [`TestOperationDescriptor`](javadoc/org/gradle/tooling/events/test/TestOperationDescriptor.html) interface to provide the test display name – [`getTestDisplayName`](javadoc/org/gradle/tooling/events/test/TestOperationDescriptor.html#getTestDisplayName--).
It returns the display name of the test that IDEs can use to present the test in a human-readable format.
It is transparently passed from the frameworks, enabling IDEs to use them without requiring transformations.
Previously, the display name could be obtained only by parsing the operation display name, which was not always reliable.

Additionally, for [JUnit5](https://junit.org/junit5/) and [Spock](https://spockframework.org/), we updated the test descriptor for dynamic and parameterized tests to include information about the class name and method name containing the test.
These enhancements enable IDEs to offer improved navigation and reporting capabilities for dynamic and parameterized tests.

#### Fix IDE performance issues with large projects

A community member identified and fixed a performance issue in the Tooling API that caused delays at the end of task execution in large projects.
This problem occurred while transmitting task information for executed tasks to the IDE.

After executing approximately 15,000 tasks, the IDE would encounter a delay of several seconds.
The root cause was that much more information than needed was serialized via the Tooling API.
We added a test to the fix to ensure no future regression, and the results demonstrate a performance improvement of around 12%.
The environments that benefit from this fix are Android Studio, IntelliJ IDEA, Eclipse, and other Tooling API clients.

<a name="kotlin-dsl"></a>
### Kotlin DSL improvements

Kotlin DSL provides an alternative syntax to the traditional Groovy DSL and has been the default choice for new Gradle builds since [Gradle 8.2](https://docs.gradle.org/8.2/release-notes.html).

#### Accessors for `Settings` extensions in Kotlin DSL

Previously, extensions registered in `Plugin<Settings>` weren't available in `settings.gradle.kts`.

Now, type-safe accessors for these extensions are generated.

This fixes [https://github.com/gradle/gradle/issues/11210](https://github.com/gradle/gradle/issues/11210).

<a name="other"></a>
### Other improvements

#### Allow specifying source encoding for Jacoco report tasks

The source encoding of `JacocoReport` tasks may now be specified via the `sourceEncoding` property:

```groovy
jacocoTestReport {
    sourceEncoding = 'UTF-8'
}
```

#### Filter standard output and error output in XML test reports

The new [`includeSystemOutLog` and `includeSystemErrLog` options](userguide/java_testing.html#junit_xml_configuration_output_filtering) control whether output written to standard output and standard error output during testing is included in XML test reports.
This report format is used by JUnit 4, JUnit Jupiter, and TestNG, despite the name of the report format, and can be configured when using any of these test frameworks.
Disabling these options can be useful when running a test task, as they result in a large amount of standard output or standard error data that is irrelevant to testing.
It is also useful for preserving disk space when running jobs on CI.

Set these options by configuring the [JUnitXmlReport](javadoc/org/gradle/api/tasks/testing/JUnitXmlReport.html) options block.

```kotlin
tasks.test {
    reports.junitXml {
        includeSystemOutLog = false
        includeSystemErrLog = true
    }
}
```

#### Setting custom POM values in Maven publications for plugins

The maven-publish plugin provides a way to set custom POM values for a Gradle plugin publication, as demonstrated in the following example:

```groovy
gradlePlugin {
    plugins {
        register("some.plugin") {
            name = "SomePluginName"
            description = "SomePluginDesc"
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name = "CustomPublicationName"
            description = "CustomPublicationDesc"
        }
    }
}
```

Previously, this was not working as expected, and the published POM would contain the name and description values configured via the `plugins` block.

Now, any values configured in the `pom` block will take priority if present and be written to the published POM for that publication:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <name>CustomPublicationName</name>
  <description>CustomPublicationDesc</description>
  ...
</project>
```

This fixes [https://github.com/gradle/gradle/issues/12259](https://github.com/gradle/gradle/issues/12259).

#### Allow version catalog plugin aliases without a version

Previously, a version catalog plugin alias could be defined without a version, but attempting to use it would result in an exception.
It is now explicitly allowed to have a plugin alias with no version, and no exception will be thrown when using it:

```toml
# In libs.versions.toml
[plugins]
myPlugin = { id = "my.plugin.id" }
```

```kotlin
// In build.gradle(.kts)
plugins {
    alias(libs.plugins.myPlugin)
}
```

The `buildCache.local.removeUnusedEntriesAfterDays` method is now deprecated.
If set to a non-default value, this deprecated setting will take precedence over `Settings.caches.buildCache.setRemoveUnusedEntriesAfterDays()`.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

<!--
This section will be populated automatically
-->

## Known issues

Known issues are problems that were discovered post-release that are directly related to changes made in this release.

<!--
This section will be populated automatically
-->

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
