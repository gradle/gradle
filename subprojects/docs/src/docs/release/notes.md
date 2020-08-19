The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:

[SheliakLyr](https://github.com/SheliakLyr),
[Christian Edward Gruber](https://github.com/cgruber),
[Rene Groeschke](https://github.com/breskeby),
[Louis CAD](https://github.com/LouisCAD),
[Campbell Jones](https://github.com/serebit),
[Leonardo Bezerra Silva Júnior](https://github.com/leonardobsjr),
[Christoph Dreis](https://github.com/dreis2211),
[Matthias Robbers](https://github.com/MatthiasRobbers),
[Vladimir Sitnikov](https://github.com/vlsi),
and [Stefan Oehme](https://github.com/oehme).

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

## Configuration cache improvements

TBD - load from cache performance improvements and reduced memory consumption for Android builds

## Support kebab case formatting when launching a Gradle build with abbreviated names

When running Gradle builds, you can abbreviate project and task names. For example, you can execute the `compileTest` task by running `gradle cT`.

Until this release, the name abbreviation only worked for camel case names (e.g. `compileTest`). This format is recommended for task names, but it is unusual for project names. In the Java world the folder names are lower case by convention. 

Many projects - including Gradle - overcome that by using kebab case project directories (e.g. `my-awesome-lib`) and by defining different, camel case project names in the build scripts. This difference leads to unnecessary extra complexity in the build.

This release fixes the issue by adding support for kebab case names in the name matching. Now, you can execute the `compileTest` task in the `my-awesome-lib` subproject with the following command:
```
gradle mAL:cT
```

Note, that even though the kebab case name matching works with tasks too, the recommendation is still to use camel case for them. 

To learn more about name abbreviation, check out the [user guide](userguide/command_line_interface.html#task_name_abbreviation).  

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
