The Gradle team is excited to announce Gradle @version@.

Gradle now supports [Java 22](#java-22).

This release introduces a preview feature to configure the Gradle daemon JVM [using toolchains](#daemon-toolchains) and [improved IDE performance](#ide-perf) with large projects.

Additionally, this release includes many notable improvements to [build authoring](#build-authoring), [error and warning messages](#error-warning), the [build cache](#build-cache), and the [configuration cache](#config-cache).

We would like to thank the following community members for their contributions to this release of Gradle:
[Björn Kautler](https://github.com/Vampire),
[Denes Daniel](https://github.com/pantherdd),
[Fabian Windheuser](https://github.com/fawind),
[Hélio Fernandes Sebastião](https://github.com/helfese),
[Jay Wei](https://github.com/JayWei1215),
[jhrom](https://github.com/jhrom),
[jwp345](https://github.com/jwp345),
[Jörgen Andersson](https://github.com/jorander),
[Kirill Gavrilov](https://github.com/gavvvr),
[MajesticMagikarpKing](https://github.com/yctomwang),
[Maksim Lazeba](https://github.com/M-Lazeba),
[Philip Wedemann](https://github.com/hfhbd),
[Robert Elliot](https://github.com/Mahoney),
[Róbert Papp](https://github.com/TWiStErRob),
[Stefan M.](https://github.com/StefMa),
[Tibor Vyletel](https://github.com/TiborVyletel),
[Tony Robalik](https://github.com/autonomousapps),
[Valentin Kulesh](https://github.com/unshare),
[Yanming Zhou](https://github.com/quaff),
[김용후](https://github.com/who-is-hu)

Be sure to check out the [public roadmap](https://blog.gradle.org/roadmap-announcement) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<a name="java-22"></a>
### Full Java 22 support

With this release, Gradle supports running on [Java 22](https://jdk.java.net/22/).
This means you can now use Java 22 for the [daemon](userguide/gradle_daemon.html) in addition to [toolchains](userguide/toolchains.html).

For details, see the full [compatibility documentation](userguide/compatibility.html#java).

<a name="daemon-toolchains"></a>
### Gradle daemon JVM configurable via toolchain

Previously, Gradle did not support capturing the JVM requirements of the build, which could lead to build failures when using the wrong JVM version.
This made such builds harder to import into IDEs or run locally.

With this release, users can [configure the JVM](userguide/gradle_daemon.html#sec:daemon_jvm_criteria) used to run a Gradle build.
This feature is built on top of [Java toolchains](userguide/toolchains.html) and works similarly to how the [Gradle wrapper](userguide/gradle_wrapper.html) captures Gradle version requirements.

This is an incubating feature that will change in future releases.

<a name="ide-perf"></a>
### Improved IDE performance for large projects

When invoking large builds from an IDE, the [Tooling API's](userguide/third_party_integration.html) execution of extensive task graphs suffered from a performance penalty caused by transferring unnecessary information.

Eliminating this transfer results in performance improvements of up to 12% in large up-to-date builds with over 15,000 tasks in their task graph.

We want to thank a [community member](https://github.com/M-Lazeba) for identifying and fixing this issue.

Updating your Gradle version will immediately benefit Android Studio, IntelliJ IDEA, Eclipse, and other [Tooling API clients](userguide/gradle_ides.html).

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides rich APIs for plugin authors and build engineers to develop custom build logic.

#### Allow version catalog plugin aliases without a version

Previously, a [version catalog plugin alias](userguide/platforms.html#sec:plugins_ver) could be defined without a version, but attempting to use it would result in an exception.
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

#### Accessors for `Settings` extensions in Kotlin DSL

Previously, extensions registered in [`Plugin<Settings>`](userguide/custom_plugins.html#project_vs_settings_vs_init_plugins) weren't available in `settings.gradle.kts`.
Now, type-safe accessors for these extensions are generated.

```kotlin
interface MySettingsExtension {
    val myProperty: Property<Int>
}
```

Assuming you register the extension above as `mySettingsExtension`, then you can access it directly:

```kotlin
// In settings.gradle.kts

// accessor function
mySettingsExtension {
    myProperty = 42
}

// accessor property
println(mySettingsExtension.myProperty)
```

This fixes [a long-standing issue](https://github.com/gradle/gradle/issues/11210).

#### Ability to set conventions on file collections

Plugin-provided tasks often expose file collections that build engineers need to customize, such as the classpath for the JavaCompile task.
Until now, plugin authors defined default values for these collections by setting initial values.

With this release, Gradle introduces a more flexible approach using [conventions](userguide/implementing_gradle_plugins_binary.html#sec:plugin_conventions).
Conventions allow plugin authors to recommend default values, which users can then accept, extend, or completely replace.

This release introduces a pair of [`convention(...)`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html#convention-java.lang.Object...-) methods on `ConfigurableFileCollection` that define the default value of a file collection if no explicit value is previously set via `setFrom(...)` or `from(...)`:

```kotlin
val files = objects.fileCollection().convention("dir1")
files.from("dir2")

println(files.elements.get()) // [.../dir1, .../dir2]
```

`#from(...)` will honor the convention if one is configured when invoked, so the order of operations will matter.

To forcefully override or prevent a convention (i.e., regardless of the order of those operations), use `#setFrom():

```kotlin
val files = objects.fileCollection().convention("dir1")
files.setFrom("dir2")

println(files.elements.get()) // [.../dir2]
```

This is analogous to the [`convention(...)`](javadoc/org/gradle/api/provider/Property.html#convention-T-) methods that have been available on lazy properties since Gradle 5.1.

#### New Gradle lifecycle callbacks

This release introduces a new [`GradleLifecycle`](javadoc/org/gradle/api/invocation/GradleLifecycle.html) API, accessible via `gradle.lifecycle`, which plugin authors and build engineers can use to register actions to be executed at certain points in the build lifecycle.

Actions registered as `GradleLifecycle` callbacks (currently, `beforeProject` and `afterProject`) are *[isolated](javadoc/org/gradle/api/IsolatedAction.html)*, running in an isolated context that is private to every project.
This will allow Gradle to perform additional performance optimizations and will be required in the future to take advantage of parallelism during the build configuration phase.

While the existing callbacks continue to work, we encourage everyone to adopt the new API and provide us with early feedback.

The example below shows how this new API could be used in a settings script or [settings plugins](userguide/custom_plugins.html#project_vs_settings_vs_init_plugins) to apply configuration to all projects, 
while avoiding [cross-project configuration](userguide/sharing_build_logic_between_subprojects.html#sec:convention_plugins_vs_cross_configuration):

```kotlin
// settings.gradle.kts
include("sub1")
include("sub2")

gradle.lifecycle.beforeProject {
    apply(plugin = "base")
    repositories {
        mavenCentral()
    }
}
```

#### Isolated project views

There is now support for obtaining an isolated view of a project as an  [`IsolatedProject`](javadoc/org/gradle/api/project/IsolatedProject.html) via [`Project.getIsolated()`](javadoc/org/gradle/api/Project.html#getIsolated--).

The view exposes only those properties that are safe to access across project boundaries when running the build configuration phase in parallel (to be supported in a future release).

The example below shows how the API could be used from a `Project` configuration callback to query the root project directory in a parallel-safe way:

```kotlin
gradle.lifecycle.beforeProject {
    val rootDir = project.isolated.rootProject.projectDirectory
    println("The root project directory is $rootDir")
}
```

<a name="error-warning"></a>
### Error and warning reporting improvements

Gradle provides a rich set of error and warning messages to help you understand and resolve problems in your build.

#### Improved error handling for toolchain resolvers

This update improves how Gradle handles errors when downloading [Java toolchains from configured resolvers](userguide/toolchains.html#sub:download_repositories).

Previously, if a resolver threw an exception while mapping toolchain specs to download URLs or during the auto-provisioning process, Gradle did not try other configured resolvers.
Gradle will now handle these errors better by attempting to use other resolvers in such cases.

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

This replaces the previous low-level incompatibility message, which was difficult to understand without knowledge of Gradle internals.
This new error message can be especially helpful for users upgrading the Spring Boot dependencies or the Spring Boot Gradle plugin to version 3+, which requires Java 17 or later.

#### Fixed error message when buildscript dependencies fail to resolve

When a build script fails to [resolve dependencies on its classpath](userguide/writing_build_scripts.html#3_add_dependencies), the error message will now more clearly state the issue:

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

When Gradle determines that a [particular repository is unavailable](userguide/writing_build_scripts.html#2_define_the_locations_where_dependencies_can_be_found) when requesting a dependency, it will stop trying to resolve any dependencies from that repository.
This can prevent other dependencies from resolving successfully.

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

The following improvements are for IDE integrators.
They will become available to end-users in future IDE releases once IDE vendors adopt them.

#### Tests metadata improvements in Tooling API

IDEs and other tools leverage the [tooling API](userguide/third_party_integration.html) to access information about tests executed by Gradle.
Each test event sent via the tooling API includes a test descriptor containing metadata such as a human-readable name, class name, and method name.

Previously, the test display name could only be obtained by parsing the operation display name, which was not always reliable.
A new method to the [`TestOperationDescriptor`](javadoc/org/gradle/tooling/events/test/TestOperationDescriptor.html) interface called [`getTestDisplayName`](javadoc/org/gradle/tooling/events/test/TestOperationDescriptor.html#getTestDisplayName--) provides the test display name.

For [JUnit5](https://junit.org/junit5/) and [Spock](https://spockframework.org/), we updated the test descriptor for dynamic and parameterized tests to include information about the class name and method name containing the test.
These enhancements enable IDEs to offer improved navigation and reporting capabilities for dynamic and parameterized tests.

<a name="build-cache"></a>
### Build cache changes

The [Gradle build cache](userguide/build_cache.html) is a mechanism designed to save time by reusing local or remote outputs from previous builds.

#### Improved control of local build cache cleanup

With this release, local build cache cleanup is configurable via the [standard init-script mechanism](userguide/directory_layout.html#dir:gradle_user_home:cache_cleanup), providing improved control and consistency.
- Specifying `Cleanup.DISABLED` or `Cleanup.ALWAYS` will now prevent or force the cleanup of the local build cache
- Build cache entry retention is now configured via `Settings.caches.buildCache.setRemoveUnusedEntriesAfterDays()`

```kotlin
//init.gradle.kts
beforeSettings {
   caches {
 cleanup = Cleanup.ALWAYS
       buildCache.setRemoveUnusedEntriesAfterDays(30)
   }
}
```

Previously, the retention period was configured via the `DirectoryBuildCache.removeUnusedEntriesAfterDays` setting.
See the [upgrade guide](userguide/upgrading_version_8.html#directory_build_cache_retention_deprecated) for details on how to adopt the new API.

#### Groovy build script compilation build cache support is disabled

In Gradle 8.7, we introduced support for using the remote build cache with [Groovy build script](userguide/groovy_build_script_primer.html) compilation.
However, after receiving reports of slower compile times with the remote cache, we conducted further investigation.
Our findings showed that the cache was not delivering the expected performance improvements.

As a result, we have disabled the remote build cache for Groovy build script compilation in this release.

<a name="config-cache"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing it for subsequent builds.
This feature can significantly improve build performance.

#### Support for Java Record classes and `Externalizable` instances

The configuration cache now supports:
* [Java Record classes](https://docs.oracle.com/en/java/javase/21/language/records.html)
* [java.io.Externalizable instances](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Externalizable.html)

<a name="other"></a>
### Other improvements

#### Allow specifying source encoding for Jacoco report tasks

For the [JaCoCo Plugin](userguide/jacoco_plugin.html#sec:jacoco_report_configuration), the source encoding of `JacocoReport` tasks may now be specified via the `sourceEncoding` property:

```groovy
jacocoTestReport {
   sourceEncoding = 'UTF-8'
}
```

#### Filter standard output and error output in XML test reports

When testing Java projects using common frameworks, [reports are typically produced in XML format](userguide/java_testing.html#junit_xml_configuration_output_filtering).
The new [`includeSystemOutLog` and `includeSystemErrLog` options](userguide/java_testing.html#junit_xml_configuration_output_filtering) control whether output written to standard output and standard error output during testing is included in those XML test reports.
This report format is used by JUnit 4, JUnit Jupiter, and TestNG, despite the name of the report format, and can be configured when using any of these test frameworks.

Disabling these options can be useful when running a test task, as they result in a large amount of standard output or standard error data that is irrelevant to testing.
It is also useful for preserving disk space when running jobs on CI.

You can set these options by configuring the [JUnitXmlReport](javadoc/org/gradle/api/tasks/testing/JUnitXmlReport.html) options block:

```kotlin
tasks.test {
   reports.junitXml {
       includeSystemOutLog = false
       includeSystemErrLog = true
   }
}
```

#### Setting custom POM values in Maven publications for plugins

The [Maven-publish plugin](userguide/publishing_maven.html#header) provides a way to set custom POM values for a Gradle plugin publication, as demonstrated in the following example:

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

Now, any values configured in the `pom` block will take precedence if present and be written to the published POM for that publication:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
 <name>CustomPublicationName</name>
 <description>CustomPublicationDesc</description>
 ...
</project>
```

This fixes [a long-standing issue](https://github.com/gradle/gradle/issues/12259).

#### Custom `dependencies` blocks

Since Gradle 8.7, it's been possible for plugins to define their own dependencies-like block.
A custom dependencies block allows users to declare dependencies in a type-safe and context-aware way:

```java
// ExamplePlugin.java
public class ExamplePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ExampleExtension example = project.getExtensions().create("example", ExampleExtension.class);
    }
}
```

```kotlin
// build.gradle.kts
example {
    dependencies {
        implementation("junit:junit:4.13")
    }
}
```

This is currently used by the [JVM test suite plugin](userguide/jvm_test_suite_plugin.html).

See the [user manual](userguide/implementing_gradle_plugins_binary.html#custom_dependencies_blocks) to learn more about using this incubating feature.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### File Permissions API is now stable

The File Permissions API for defining file permissions using UNIX style values (added in Gradle 8.3) is now stable; see:

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
