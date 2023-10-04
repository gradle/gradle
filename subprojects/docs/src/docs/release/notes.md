The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:
[Philipp Schneider](https://github.com/p-schneider),

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

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

<a name="ear-plugin"></a>
### Ear Plugin

The Ear plugin, which generates Java EE Enterprise Archive (EAR) files, now supports valid deployment descriptors for Java EE 8, Jakarta EE 9, and Jakarta EE 10.
You can specify the corresponding version in the `deploymentDescriptor` instead of having to use a [custom descriptor file](userguide/ear_plugin.html#sec:using_custom_app_xml):

```kotlin
tasks.ear {
    deploymentDescriptor {  // custom entries for application.xml:
        version = "10" // Now supporting version 8, 9, and 10
    }
}
```

<a name="wrapper-improvements"></a>
### Wrapper Improvements

The recommended way to execute any Gradle build is with the help of the [Gradle Wrapper](userguide/gradle_wrapper.html) (in short, “Wrapper”).
The Wrapper invokes a declared version of Gradle, downloading it beforehand if necessary.

#### Smaller Wrapper JAR

The Wrapper JAR file size was reduced from ~65K down to ~45K by eliminating unused code.

#### Wrapper JAR LICENSE File

The Wrapper JAR now contains a `META-INF/LICENSE` file.

This was done to alleviate any doubts regarding licensing of the Wrapper JAR file.
The Wrapper and the Gradle Build Tool are licensed under the [Apache Software License 2.0](https://github.com/gradle/gradle/blob/master/LICENSE).
The JAR file is now self-attributing so that you don't need to add a separate `LICENSE` file in your codebase.


<a name="kotlin-dsl"></a>
### Kotlin DSL improvements

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an enhanced editing experience in supported IDEs compared to the traditional Groovy DSL — auto-completion, smart content assist, quick access to documentation, navigation to source, and context-aware refactoring.

#### Version catalog API in precompiled scripts

The `versionCatalogs` extension accessor is now available in Kotlin DSL precompiled scripts.

It provides a [type unsafe API](userguide/platforms.html#sub:type-unsafe-access-to-catalog) for accessing version catalogs available on the projects where the precompiled script will be applied:

```kotlin
// buildSrc/src/main/kotlin/my-convention-plugin.gradle.kts
versionCatalogs.named("libs").findLibrary("assertj-core").ifPresent { assertjCore ->
    dependencies {
        testImplementation(assertjCore)
    }
}
```

Check the [version catalog API](javadoc/org/gradle/api/artifacts/VersionCatalog.html) for all supported methods.


<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
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

Known issues are problems that were discovered post release that are directly related to changes made in this release.

<!--
This section will be populated automatically
-->

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
