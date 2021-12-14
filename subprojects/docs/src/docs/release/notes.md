The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Michael Bailey](https://github.com/yogurtearl),
[Jochen Schalanda](https://github.com/joschi),
[Jendrik Johannes](https://github.com/jjohannes),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Konstantin Gribov](https://github.com/grossws),
[Piyush Mor](https://github.com/piyushmor),
[Róbert Papp](https://github.com/TWiStErRob),
[Piyush Mor](https://github.com/piyushmor),
[Ned Twigg](https://github.com/nedtwigg),
[Nikolas Grottendieck](https://github.com/Okeanos),
[Lars Grefer](https://github.com/larsgrefer),
[Patrick Pichler](https://github.com/patrickpichler),
[Marcin Mielnicki](https://github.com/platan).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

NOTE: Gradle 7.3 has had *two* patch releases, which fixes several issues from the original release.
We recommend always using the latest patch release.

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

### JVM toolchains improvements

[Java toolchains](userguide/toolchains.html) provide an easy way to declare which Java version your project should be built with.
By default, Gradle will [detect installed JDKs](userguide/toolchains.html#sec:auto_detection) or automatically download new toolchain versions.

#### Changes following migration from AdoptOpenJDK to Adoptium

Following the migration of [AdoptOpenJDK](https://adoptopenjdk.net/) to [Eclipse Adoptium](https://adoptium.net/), a number of changes have been made for toolchains:
* `ADOPTIUM` and `IBM_SEMERU` are now recognized as vendors,
* Both of the above can be used as vendors and trigger auto-provisioning,
* Using `ADOPTOPENJDK` as a vendor and having it trigger auto-provisioning will emit a [deprecation warning](userguide/upgrading_version_7.html#adoptopenjdk_download).

See [the documentation](userguide/toolchains.html#sec:provisioning) for details.

### Kotlin DSL improvements

#### Type-safe accessors for extensions of `repositories {}`

The Kotlin DSL now generates type-safe model accessors for extensions registered on the `repositories {}` block.


For example, starting with this version of Gradle, the [`asciidoctorj-gems-plugin`](https://asciidoctor.github.io/asciidoctor-gradle-plugin/master/user-guide/#asciidoctorj-gems-plugin) can be configured directly via the generated type-safe accessors:


```kotlin
repositories {
    ruby {
        gems()
    }
}
```

Whereas before it required to use [`withGroovyBuilder`]():

```kotlin
repositories {
    withGroovyBuilder {
        "ruby" {
            "gems"()
        }
    }
}
```

or, required more tinkering in order to discover what names and types to use, relying on the API:
```kotlin
repositories {
    this as ExtensionAware
    configure<com.github.jrubygradle.api.core.RepositoryHandlerExtension> {
        gems()
    }
}
```
See [the documentation](userguide/kotlin_dsl.html#type-safe-accessors) for details.

### Dependency verification improvements

[Dependency verification](userguide/dependency_verification.html) is a feature that allows to verify the checksums and signatures of the plugins and dependencies that are used by the build of your project.

With this release, the generation of the dependency verification file has been improved to produce stable output.
This means that for the same inputs - build configuration and previous verification file - Gradle will always produce the same output.
This allows you to leverage [the verification metadata bootstrapping feature](userguide/dependency_verification.html#sec:bootstrapping-verification) as an update strategy when dependencies change in your project.
Have a look at [the documentation](userguide/dependency_verification.html#sec:verification-update) for more details.

### Gradle Option Improvements

#### Additional Daemon Debug Options

Additional options were added for use with `-Dorg.gradle.debug=true`. These allow specification of the port, server mode, and suspend mode.

See [the documentation](userguide/command_line_interface.html#sec:command_line_debugging) for details.


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

### Idle Connection Timeout

Some CI hosting providers like Azure automatically close idle connections after a certain period of time.
This caused problems with connections to the Gradle Build Cache which could have an open connection for the entire execution of the build.
This release of Gradle fixes this issue by automatically closing idle connections after 3 min by default.

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
