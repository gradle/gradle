The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

<!-- 
## 1

details of 1

## 2

details of 2

## n
-->

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<a name="shared-dependency-cache"></a>
## Shared dependency cache

Improving on [relocatable dependency caches introduced in the previous release](https://docs.gradle.org/6.1.1/release-notes.html#ephemeral-ci:-reuse-gradle's-dependency-cache), Gradle 6.2 now offers the ability to **share** a dependency cache between multiple instances.
In the context of ephemeral builds on disposable containers, this makes it possible to have a single, shared, directory between containers which contains most, if not all, the dependencies required by all builds:

- each container will have access to the shared read-only dependency cache, avoiding redundant downloads between builds
- this cache can be shared between containers without copying it, reducing the overall disk usage.

Please refer to the [userguide](userguide/dependency_resolution.html#sec:dependency_cache) to learn how to setup the shared dependency cache.

## Deprecation messages link to documentation

Deprecation messages now include links to relevant documentation that can provide more context around the deprecation and explain how to migrate to a new API or avoid the deprecated behavior. 

For example:
> The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead. Consult the upgrading guide for further information: [https://docs.gradle.org/6.2/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations](userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations)

In some terminals, this link will be clickable and take you directly to the documentation.

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
