The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [Public Roadmap](https://blog.gradle.org/roadmap-announcement) for insight into what's planned for future releases.

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

### Configuration cache improvements

The configuration cache now supports:
* [Java Record classes](https://docs.oracle.com/en/java/javase/21/language/records.html)
* [java.io.Externalizable instances](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Externalizable.html)

#### Ability to set conventions on file collections

Plugin-provided tasks often expose file collections that are meant to be customizable by build engineers (for instance, the classpath for the JavaCompile task).
Up until now, for plugin authors to define default values for file collections, they have had to resort to configuring those defaults as initial values.
Conventions provide a better model for that: plugin authors recommend default values via conventions, and users choose to accept, add on top, or completely
replace them when defining their actual value.

This release introduces a  pair of [`convention(...)`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html#convention-java.lang.Object...-) methods
on `ConfigurableFileCollection` that define the default value of a file collection if no explicit value is previously set via `setFrom(...)` or `from(...)`.

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
It is analogous to the [`convention(...)`](javadoc/org/gradle/api/provider/Property.html#convention-T-) methods that have been available on lazy properties since Gradle 5.1.

<a name="replace-method"></a>
#### Updating lazy property based on its current value with `replace()`

[Lazy configuration](userguide/lazy_configuration.html) delays calculating a property’s value until it is required for the build.
Sometimes it is necessary to modify the property based on its current value, for example, by appending something to it.
Previously, the only way to do that was to obtain the current value explicitly by calling `Property.get()`:

```
val property = objects.property<String>()
property.set("some value")
property.set("${property.get()} and more" })

println(property.get()) // "some value and more""
```

This could lead to performance issues like configuration cache misses.
Trying to build the value lazily, for example, by using `property.set(property.map { "$it and more" })`, causes build failure because of a circular reference evaluation.

[`Property`](javadoc/org/gradle/api/provider/Property.html#replace-org.gradle.api.Transformer-) and [`ConfigurableFileCollection`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html#replace-org.gradle.api.Transformer-) now provide their respective `replace(Transformer<...>)` methods that allow lazily building the new value based on the current one:

```
val property = objects.property<String>()
property.set("some value")
property.replace { it.map { "$it and more" } }

println(property.get()) // "some value and more"
```

Refer to the Javadoc for [`Property.replace(Transformer<>)`](javadoc/org/gradle/api/provider/Property.html#replace-org.gradle.api.Transformer-) and [`ConfigurableFileCollection.replace(Transformer<>)`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html#replace-org.gradle.api.Transformer-) for more details, including limitations.

#### Improved error handling for toolchain resolvers

When attempting to download Java toolchains from the configured resolvers, errors will be better handled now, and all resolvers will be tried.

While mapping toolchain specs to download URLs, resolvers aren't supposed to throw exceptions.
But it is possible for them to do that, and when it happens, Gradle should try to use other configured resolvers in their stead.
However, it wasn't the case before this fix.

Also, auto-provisioning can fail even after a successful toolchain spec to URL mapping (for example, during the actual download and validating of the toolchain)
In such a case, Gradle should retry the auto-provisioning process with other configured resolvers.
This was also not the case before the fix.


<a name="other"></a>
### Other improvements

#### Tests metadata improvements in tooling API

IDEs and other tools leverage the tooling API to access information about tests executed by Gradle.
Each test event sent via the tooling API includes a test descriptor containing metadata such as a human-readable name, class name, and method name.

We introduced a new method to the `TestOperationDescriptor` interface to provide the test display name – `getTestDisplayName`.
It returns the display name of the test that can be used by IDEs to present the test in a human-readable format.
It is transparently passed from the frameworks, enabling IDEs to use them without requiring transformations.
Previously, the display name could be obtained only by parsing the operation display name, which was not always reliable.

Additionally, for JUnit5 and Spock, we updated the test descriptor for dynamic and parameterized tests to include information about the class name and method name containing the test.
These enhancements enable IDEs to offer improved navigation and reporting capabilities for dynamic and parameterized tests.

#### Fix IDE performance issues with large projects

A performance issue in the Tooling API causing delays at the end of task execution in large projects has been identified and fixed by a community member.
This problem occurred while transmitting task information for executed tasks to the IDE.

After executing approximately 15,000 tasks, the IDE would encounter a delay of several seconds.
The root cause was that much more information than needed was serialized via the Tooling API.
We added a test to the fix to ensure there will be no future regression, demonstrating a performance improvement of around 12%.
The environments that benefit from this fix are Android Studio, IntelliJ IDEA, Eclipse, and other Tooling API clients.

#### Filter standard output and error output in XML test reports

The new [`includeSystemOutLog` and `includeSystemErrLog` options](userguide/java_testing.html#junit_xml_configuration_output_filtering) control whether or not output written to standard output and standard error output during testing is included in XML test reports.
This report format is used by JUnit 4, JUnit Jupiter, and TestNG, despite the name of the report format, and can be configured when using any of these test frameworks.
Disabling these options can be useful when running a test task results in a large amount of standard output or standard error data that is irrelevant for testing.
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

Previously, this was not working as expected and the published POM would contain the name and description values configured via the `plugins` block.

Now, any values configured in the `pom` block will take priority if present, and be written to the published POM for that publication:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <name>CustomPublicationName</name>
  <description>CustomPublicationDesc</description>
  ...
</project>

```

This fixes https://github.com/gradle/gradle/issues/12259.

#### Accessors for `Settings` extensions

Previously, extensions registered in `Plugin<Settings>` weren't available in `settings.gradle.kts`. Access from Groovy work as expected.  

Now, type-safe accessors for these extensions are being generating.

This fixes https://github.com/gradle/gradle/issues/11210.
<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

### Allow specifying source encoding for Jacoco report tasks

The source encoding of `JacocoReport` tasks may now be specified via the `sourceEncoding` property.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

This Gradle release promotes the following features to stable:

### File permissions API

The new API for defining file permissions (added in Gradle 8.3) is now stable, see:

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
