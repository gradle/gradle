The Gradle team is excited to announce Gradle @version@.

This release features improvements to [build init](#build-init) to generate plugin projects, 
[an API for transforming dependency artifacts](#artifact-transforms), 
the ability to declare [organization-wide Gradle properties](#gradle-properties), 
lots of new [native plugin documentation](#native-support) and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Martin d'Anjou](https://github.com/martinda),
[Ben Asher](https://github.com/benasher44),
[Mike Kobit](https://github.com/mkobit),
[Erhard Pointl](https://github.com/epeee),
[Sebastian Schuberth](https://github.com/sschuberth),
[Evgeny Mandrikov](https://github.com/Godin),
[Stefan M.](https://github.com/StefMa),
[Igor Melnichenko](https://github.com/Myllyenko),
[Björn Kautler](https://github.com/Vampire),
[Roberto Perez Alcolea](https://github.com/rpalcolea) and
[Christian Fränkel](https://github.com/fraenkelc).


## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<a name="artifact-transforms"/>

## Transforming dependency artifacts on resolution

A dependency’s artifacts can take many forms, and sometimes you might need one that is not readily available.
As an example, imagine you have a dependency on a Java module.
Its producer publishes a normal JAR file and an obfuscated JAR file to a binary repository.
As a consumer, you want to use the normal JAR for development.
But for production, you want to use an obfuscated version of the JAR rather than the version that’s published.

Let’s say you want to get hold of the obfuscated JAR, but it’s not available in any repository.
Why not just retrieve the un-obfuscated JAR and obfuscate it yourself?

Gradle now allows you to register an _artifact transform_ to do just that, by hooking into the dependency management resolution engine.
You can specify that whenever an obfuscated JAR is requested but can’t be found, Gradle should run an artifact transform that performs the obfuscation and makes the resulting artifact available transparently to the build.
 
For more information have a look at the [user manual](userguide/dependency_management_attribute_based_matching.html#sec:abm_artifact_transforms).

<a name="build-init"/>

## Build init plugin improvements

### Support for JUnit Jupiter

The `init` task now provides an option to use Junit Jupiter, instead of Junit 4, to test Java applications and libraries. You can select this test framework when you run the `init` task interactively, or use the `--test-framework` command-line option. See the [User manual](userguide/build_init_plugin.html) for more details.

Contributed by [Erhard Pointl](https://github.com/epeee)

### Generate Gradle plugin builds

The `init` task can now generate simple Gradle plugin projects. You can use these as a starting point for developing and testing a Gradle plugin. The `init` task provides an option to use either Java, Groovy or Kotlin for the plugin source. You can select a Gradle plugin when you run the `init` task interactively, or use the `--type` command-line option.

See the [User manual](userguide/build_init_plugin.html) for more details.

<a name="gradle-properties"/>

## Define organization-wide properties with a custom Gradle Distribution

Gradle now looks for a `gradle.properties` file in the Gradle distribution used by the build.  This file has the [lowest precedence of any `gradle.properties`](userguide/build_environment.html#sec:gradle_configuration_properties) and properties defined in other locations will override values defined here.

By placing a `gradle.properties` file in a [custom Gradle distribution](userguide/organizing_gradle_projects.html#sec:custom_gradle_distribution), an organization can add default properties for the entire organization or tweak the default Gradle daemon memory parameters with `org.gradle.jvmargs`.

<a name="native-support"/>

## Building native software with Gradle

Gradle's user manual now includes new chapters for [building](userguide/building_cpp_projects.html) and [testing](userguide/cpp_testing.html) C++ projects.  The DSL guide now includes [C++ related types](dsl/index.html#N10808).

Reference chapters were also created for [all of the C++ plugins](userguide/plugin_reference.html#native_languages) and [Visual Studio and Xcode IDE plugins](userguide/plugin_reference.html#ide_integration).

The [C++ guides](https://gradle.org/guides/?q=Native) have been updated to reflect all the new features available to C++ developers.

See more information about the [Gradle native project](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-55).

## Improved Eclipse project name deduplication in Buildship

When importing Gradle Eclipse projects into Buildship, the current Eclipse workpace state is taken into account. This allows Gradle to import/synchronize in Eclipse workspaces that include
non-Gradle projects that conflict with project names in the imported project.

The upcoming 3.1.1 version of Buildship is required to take advantage of this behavior.

Contributed by [Christian Fränkel](https://github.com/fraenkelc)

## Gradle Kotlin DSL compiler upgraded to Kotlin 1.3.31

The Gradle Kotlin DSL embedded Kotlin compiler has been upgraded from version `1.2.21` to version `1.3.31`, please refer to the [Kotlin 1.3.30 release blog entry](https://blog.jetbrains.com/kotlin/2019/04/kotlin-1-3-30-released/) and the [Kotlin 1.3.31 GitHub release notes](https://github.com/JetBrains/kotlin/releases/tag/v1.3.31) for details.

## `ObjectFactory` methods for creating domain object collections in plugins

The [`ObjectFactory`](javadoc/org/gradle/api/model/ObjectFactory.html) now has methods for creating [`NamedDomainObjectContainer`](javadoc/org/gradle/api/NamedDomainObjectContainer.html) and [`DomainObjectSet`](javadoc/org/gradle/api/DomainObjectSet.html).

Previously, it was only possible to create a domain object collection by using the APIs provided by a `Project`. Any place where a `ObjectFactory` is available can now create a domain object collection. 

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
