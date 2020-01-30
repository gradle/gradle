The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:

[Stefan Neuhaus](https://github.com/stefanneuhaus),
[EthanLozano](https://github.com/EthanLozano),
[Pavlos-Petros Tournaris](https://github.com/pavlospt),
[Márton Braun](https://github.com/zsmb13),
[Thomas Iguchi](https://github.com/tiguchi),
[Vladimir Sitnikov](https://github.com/vlsi/),
[Peter Stöckli](https://github.com/p-),
[Sebastian Schuberth](https://github.com/sschuberth),
[Frieder Bluemle](https://github.com/friederbluemle),
[ColtonIdle](https://github.com/ColtonIdle),
and [Roberto Perez Alcolea](https://github.com/rpalcolea).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<a name="dependency-verification"></a>
## Dependency verification

Typical projects use a large number of external dependencies which put them at risk of using untrusted code.
What if you accidentally introduced malicious code via a transitive dependency?
Similarly, what if your build script itself is vulnerable to malicious code execution via a compromised plugin?

In an effort to mitigate the risks, Gradle 6.2 ships with [dependency verification](userguide/dependency_verification.html).
Dependency verification is a major step towards a safer ecosystem by making it possible to verify both the checksums and the signatures of dependencies and plugins used during a build.

By enabling dependency verification, Gradle will:

- make sure that the dependencies haven't been tampered with (by verifying their _checksums_)
- ensure the provenance of dependencies and plugins you use (by verifying their _signatures_)

and therefore dramatically reduce the risks of shipping malicious code to production.

With dependency verification, you maintain an XML file with checksums and optionally also signatures of all external artifacts used in your project, which includes, but is not limited to, all jars (binaries, sources, ...) and plugins.
Gradle will immediately fail the build if an artifact is not trusted or missing from the configuration file.

Please refer to the [userguide](userguide/dependency_verification.html) for a complete explanation about how to setup dependency verification. 

We would like to give special thanks to [Vladimir Sitnikov](https://github.com/vlsi) for his feedback and inspiration.
A lot of the work on this feature is, in particular, available to previous versions of Gradle via his [Checksum Dependency Plugin](https://github.com/vlsi/vlsi-release-plugins/tree/master/plugins/checksum-dependency-plugin).

<a name="shared-dependency-cache"></a>
## Shared dependency cache

Improving on [relocatable dependency caches introduced in the previous release](https://docs.gradle.org/6.1.1/release-notes.html#ephemeral-ci:-reuse-gradle's-dependency-cache), Gradle 6.2 now offers the ability to **share** a dependency cache between multiple Gradle instances.
In the context of ephemeral builds on disposable containers, this makes it possible to have a single, shared, directory between containers which contains most, if not all, the dependencies required by all builds:

- each container will have access to the shared read-only dependency cache, avoiding redundant downloads between builds
- this cache can be shared between containers without copying it, reducing the overall disk usage.

Please refer to the [userguide](userguide/dependency_resolution.html#sec:dependency_cache) to learn how to setup the shared dependency cache.

## Deprecation messages link to documentation

Deprecation messages now include links to relevant documentation that can provide more context around the deprecation and explain how to migrate to a new API or avoid the deprecated behavior. 

For example:
> The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead. Consult the upgrading guide for further information: [https://docs.gradle.org/6.2/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations](userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations)

In some terminals, this link will be clickable and take you directly to the documentation.

## Gradle API source code for plugin developers in IDEs

Plugin authors will now have the sources of the `gradleApi()`, `gradleTestKit()` and `localGroovy()` dependencies attached for navigation in the IDE.

This works out of the box with [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship).

For IDEA, the sources for `gradleApi()` are only attached when the [Gradle wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper) is used with an `-all` distribution. 
This will change once [IDEA-231667](https://youtrack.jetbrains.com/issue/IDEA-231667) is resolved. All Gradle API sources will be downloaded on-demand.

## Gradle Wrapper Verification GitHub Action

We have released an official GitHub Action that enables GitHub users to automatically verify that the `gradle-wrapper.jar` in their repository or
contributed by external developers was officially released by us and not malicious.

You can find out more about why this is important for your project's security and how to apply this action to your project
[here](https://github.com/gradle/wrapper-validation-action).

If you're looking for other ways to verify the provenance of the `gradle-wrapper.jar` external to GitHub, more information can be found in our
[Wrapper guide](userguide/gradle_wrapper.html#sec:wrapper_checksum_verification).

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
