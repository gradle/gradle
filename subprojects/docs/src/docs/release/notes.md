The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Andrew K.](https://github.com/miokowpak)

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

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

## Improvements for plugin authors

### Task dependencies are honored for `@Input` properties of type `Property`

TBD - honors dependencies on `@Input` properties.

### Property methods

TBD - added `getLocationOnly()`. 

## Building native software with Gradle

All new C++ documentations including new user manual chapters for [building](userguide/building_cpp_projects.html) and [testing](userguide/cpp_testing.html) C++ projects, [DSL reference for C++ components](dsl/index.html#N10808), [C++ plugins reference chapters](userguide/plugin_reference.html#native_languages) and [Visual Studio and Xcode IDE plugins reference chapters](userguide/plugin_reference.html#ide_integration).
The [C++ guides](https://gradle.org/guides/?q=Native) were also improved to reflect all the new features available to C++ developers.
See more information about the [Gradle native project](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-55).

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

## Improved handling of ZIP archives on classpaths

Compile classpath and runtime classpath analysis will now detect the most common zip extension instead of only supporting `.jar`.
It will inspect nested zip archives as well instead of treating them as blobs. This improves the likelihood of cache hits for tasks
that take such nested zips as an input, e.g. when testing applications packaged as a fat jar.

The ZIP analysis now also avoids unpacking entries that are irrelevant, e.g. resource files on a compile classpath. 
This improves performance for projects with a large amount of resource files.

<!--
### Example promoted
-->

## Incubating support for Groovy compilation avoidance

Gradle now supports experimental compilation avoidance for Groovy. This accelerates Groovy compilation by avoiding re-compiling dependent projects if only non-ABI changes are detected.
See [Groovy compilation avoidance](userguide/groovy_plugin.html#sec:groovy_compile_avoidance) for more details.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
