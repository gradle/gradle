## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Set the character set used for filtering files in CopySpec

By default, file filtering using `CopySpec` uses the default platform character set to read and write filtered files.
This can cause problems if, for example, the files are encoded using `UTF-8` but the default platform character set is another one.

You can now define the character set to use when reading and writing filtered files per `CopySpec`, e.g.:

    task filter(type: Copy) {
        from 'some/place'
        into 'somewhere/else'
        expand(version: project.version)
        filteringCharset = 'UTF-8'
    }

See the “[Filtering files](userguide/working_with_files.html#sec:filtering_files)” section of the “Working with files chapter” in the user guide for more information and examples of using this new feature.

This was contributed by [Jean-Baptiste Nizet](https://github.com/jnizet).

### Apply gradle core plugins by id

Some gradle core plugins can now be applied by id:

    plugins {
        id 'standard-toolchains' // StandardToolChainsPlugin
        id 'gcc-compiler' // GccCompilerPlugin
        id 'component-base' // ComponentBasePlugin
        id 'component-model-base' // ComponentModelBasePlugin
        id 'reporting-base' // ReportingBasePlugin
        id 'clang-compiler' // ClangCompilerPlugin
        id 'native-component-model' // NativeComponentModelPlugin
        id 'lifecycle-base' // LifecycleBasePlugin
        id 'visualcpp-compiler' // MicrosoftVisualCppPlugin
    }

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### `StartParameter.consoleOutput` property

The `StartParameter.consoleOutput` property has been promoted and is now stable.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### Support for running Gradle on Java 6

Running Gradle using Java 6 is now deprecated, and support will be removed in Gradle 3.0.

It will continue to be possible to build JVM based projects for Java 6, by running Gradle using Java 7 and configuring Gradle to use Java 6 to compile, test and run your code.

### `StartParameter.colorOutput` property

The `StartParameter.colorOutput` property has been deprecated and will be removed in Gradle 3.0. You should use the `consoleOutput` property instead.

## Potential breaking changes

### Gradle implementation dependencies are not visible to plugins at development time

Implementing a Gradle plugin requires the declaration of `gradleApi()`
to the `compile` configuration. The resolved dependency encompasses the
entire Gradle runtime including Gradle's third party dependencies
(e.g. Guava). Any third party dependencies declared by the plugin might
conflict with the ones pulled in by the `gradleApi()` declaration. Gradle
does not apply conflict resolution. As a result The user will end up with
two addressable copies of a dependency on the compile classpath and in
 the test runtime classpath.

In previous versions of Gradle the dependency `gradleTestKit()`, which
relies on a Gradle runtime, attempts to address this problem via class
relocation. The use of `gradleApi()` and `gradleTestKit()` together
became unreliable as classes of duplicate name but of different content
were added to the classpath.

With this version of Gradle proper class relocation has been implemented
 across the dependencies `gradleApi()`, `gradleTestKit()` and the published
 Tooling API JAR. Projects using any of those dependencies will not
 conflict anymore with classes from third party dependencies used by
 the Gradle runtime. Classes from third-party libraries provided by
 the Gradle runtime are no longer "visible" at compile and test
 time.

### Change in plugin id

`ComponentModelBasePlugin` can no longer be applied using id `component-base`. Its new id is `component-model-base`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Igor Melnichenko](https://github.com/Myllyenko) - fixed Groovydoc up-to-date checks ([GRADLE-3349](https://issues.gradle.org/browse/GRADLE-3349))
- [Sandu Turcan](https://github.com/idlsoft) - add wildcard exclusion for non-transitive dependencies in POM ([GRADLE-1574](https://issues.gradle.org/browse/GRADLE-1574))
- [Jean-Baptiste Nizet](https://github.com/jnizet) - add `filteringCharset` property to `CopySpec` ([GRADLE-1267](https://issues.gradle.org/browse/GRADLE-1267))
- [Simon Herter](https://github.com/sherter) - add thrown exception to Javadocs for `ExtensionContainer`

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
