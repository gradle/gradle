The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
[Cristian Garcia](https://github.com/CristianGM),
[fransflippo](https://github.com/fransflippo),
[Victor Turansky](https://github.com/turansky),
[Gregor Dschung](https://github.com/chkpnt),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[kerr](https://github.com/hepin1989),
and [Erhard Pointl](https://github.com/epeee).
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

NOTE: Gradle 6.4 has had _one_ patch release, which fixed several issues from the original release. We recommend always using the latest patch release.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<!-- 
Add release features here!
## 1

details of 1

## 2

details of 2

## n
-->

<a name="lazy-dependencies"><a>
## Derive dependencies from user configuration

Gradle 6.5 now supports using a [`org.gradle.api.provider.Provider`](javadoc/org/gradle/api/provider/Provider.html) when adding dependencies. 

For example:
```groovy
dependencies {
    // Version of Guava defaults to 28.0-jre but can be changed via Gradle property (-PguavaVersion=...)
    def guavaVersion = providers.gradleProperty("guavaVersion").orElse("28.0-jre")

    api(guavaVersion.map { "com.google.guava:guava:" + it })
}
```

This is useful for plugin authors that need to supply different dependencies based upon other configuration that may be set by the user.

## Improvements for tooling providers

Tooling API clients can now use a new method from [`GradleConnector`](javadoc/org/gradle/tooling/GradleConnector.html) to asynchronously cancel all Tooling API connections without waiting for the current build to finish. 

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
