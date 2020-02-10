The Gradle team is excited to announce Gradle @version@.

This release adds [built-in dependency checksum and signature verification](#dependency-verification), provides a [shareable read-only dependency cache](#shared-dependency-cache) and emits [helpful documentation links when you have deprecations in your build](#deprecation-messages).

As always, there are several [bug fixes](#fixed-issues), [improvements for plugin authors](#plugin-dev) and more.

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

In an effort to mitigate these risks, Gradle 6.2 ships with [dependency verification](userguide/dependency_verification.html).
Dependency verification is a major step towards a safer ecosystem by making it possible to verify both the checksums and the signatures of dependencies and plugins used during a build.

By enabling dependency verification, Gradle will:

- make sure that the dependencies haven't been tampered with (by verifying their _checksums_)
- ensure the provenance of dependencies and plugins you use (by verifying their _signatures_)

and therefore reduce the risks of shipping malicious code to production.

With dependency verification, you maintain an XML file with checksums and optionally also signatures of all external artifacts used in your project, which includes, but is not limited to, all jars (binaries, sources, ...) and plugins.
Gradle will immediately fail the build if an artifact is not trusted or missing from the configuration file.

Please refer to the [user manual](userguide/dependency_verification.html) for a complete explanation about how to set up dependency verification. 

We would like to give special thanks to [Vladimir Sitnikov](https://github.com/vlsi) for his feedback and inspiration.
A lot of the work on this feature is, in particular, available to previous versions of Gradle via his [Checksum Dependency Plugin](https://plugins.gradle.org/plugin/com.github.vlsi.checksum-dependency).

<a name="shared-dependency-cache"></a>
## Shared dependency cache

Improving on [relocatable dependency caches introduced in the previous release](https://docs.gradle.org/6.1.1/release-notes.html#ephemeral-ci:-reuse-gradle's-dependency-cache), Gradle 6.2 now offers the ability to **share** a dependency cache between multiple Gradle instances.

In the context of ephemeral builds on disposable containers, this makes it possible to have a single shared directory that contains the dependencies required by all builds.

- Each container will have access to the shared read-only dependency cache, which avoids redundant downloads between builds.
- This cache can be safely shared between containers without creating separate copies of it.

Please refer to the [user manual](userguide/dependency_resolution.html#sec:dependency_cache) to learn how to set up the shared dependency cache.

<a name="exclusive-repository-content"></a>
## Declaring exclusive repository content

Gradle lets you [declare precisely which dependencies a repository contains](userguide/declaring_repositories.html#sec:repository-content-filtering). This can speed up builds by reducing the number of repositories that need to be searched and increases security by not leaking details about your internal projects.

As an example:
```
repositories {
   mavenCentral()
   myRepo {
       url "https://my-company.com/repo"
       content {
           includeGroup "com.mycompany"
       }
   }
```
If Gradle needs to resolve the dependency `com.mycompany:awesome-lib:1.0`, it will _first_ search in the repository Maven Central and then in your company repository.  This is unnecessary if `com.mycompany` dependencies will never be found in Maven Central.

Gradle 6.2 provides an _exclusive content_ API, which lets you convieniently declare that a dependency can be found in one repository and it shouldn't be searched for in any other.

```
repositories {
    jcenter()
    exclusiveContent {
       forRepository {
           myRepo {
               url "https://my-company.com/repo"
           }
       }
       filter {
           includeGroup "com.mycompany"
       }
    }
}
```

In this example, when Gradle needs to resolve the dependency `com.mycompany:awesome-lib:1.0`, it will _only_ search in your company repository. This avoids the unnecessary dependency lookup with Maven Central.

Please refer to the [user manual](userguide/declaring_repositories.html#declaring_content_exclusively_found_in_one_repository) for details.

<a name="deprecation-messages"></a>
## Deprecation messages link to documentation

Deprecation messages now include links to relevant documentation that can provide more context around the deprecation and explain how to migrate to a new API or avoid the deprecated behavior. 

For example:
> The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead. Consult the upgrading guide for further information: [https://docs.gradle.org/6.2/userguide/upgrading_version_5.html#dependencies](userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations)

In some terminals, this link will be clickable and take you directly to the documentation.

## Verifying Gradle Wrappers 

The [Gradle Wrapper](userguide/gradle_wrapper.html) is a binary blob of executable code that is checked into nearly
[2.8 Million GitHub Repositories](https://github.com/search?l=&q=filename%3Agradle-wrapper.jar&type=Code).  There are [security implications](https://blog.gradle.org/gradle-wrapper-checksum-verification-github-action) when accepting changes to `gradle-wrapper.jar` that may not be apparent.

We have created [an official GitHub Action](https://github.com/marketplace/actions/gradle-wrapper-validation) that allows projects on GitHub to automatically verify that the `gradle-wrapper.jar` in their repository was released by Gradle.

You can still manually verify the `gradle-wrapper.jar` by following the instructions in the
[user manual](userguide/gradle_wrapper.html#sec:wrapper_checksum_verification).

<a name="plugin-dev"></a>
## Improvements for Plugin Development

### Gradle API source code for plugin developers in IDEs

Plugin authors will now have the sources of the `gradleApi()`, `gradleTestKit()` and `localGroovy()` dependencies attached for navigation in the IDE.

This works out of the box with [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship).

For IntelliJ IDEA, the sources for `gradleApi()` are only attached when the [Gradle wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper) is used with an `-all` distribution. Sources for `gradleTestKit()` and `localGroovy()` are not attached at the moment.
This will change once [IDEA-231667](https://youtrack.jetbrains.com/issue/IDEA-231667) is resolved. Then, the sources for all Gradle APIs (`gradleApi()`, `gradleTestKit()` and `localGroovy()`) will be downloaded and attached on-demand regardless of the wrapper in use.

### Injectable services available to Settings plugins

Our `ExecOperations` and `FileSystemOperations` [injectable services](userguide/custom_gradle_types.html#services_for_injection) are now available to Settings plugins.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
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
