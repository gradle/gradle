The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Andrew K.](https://github.com/miokowpak),
[Noa Resare](https://github.com/nresare),
[Juan Martín Sotuyo Dodero](https://github.com/jsotuyod),
[Semyon Levin](https://github.com/remal),
[wreulicke](https://github.com/wreulicke),
[John Rodriguez](https://github.com/jrodbx),
[mig4](https://github.com/mig4),
[Evgeny Mandrikov](https://github.com/Godin),
[Bjørn Mølgård Vester](https://github.com/bjornvester),
[Simon Legner](https://github.com/simon04),
[Sebastian Schuberth](https://github.com/sschuberth),
[Ivo Anjo](https://github.com/ivoanjo),
[Stefan M.](https://github.com/StefMa),
[Dominik Giger](https://github.com/gigerdo),
and [Christian Fränkel](https://github.com/fraenkelc).

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

### Worker API improvements

TBD

## Improved handling of ZIP archives on classpaths

Compile classpath and runtime classpath analysis will now detect the most common zip extension instead of only supporting `.jar`.
It will inspect nested zip archives as well instead of treating them as blobs. This improves the likelihood of cache hits for tasks
that take such nested zips as an input, e.g. when testing applications packaged as a fat jar.

The ZIP analysis now also avoids unpacking entries that are irrelevant, e.g. resource files on a compile classpath. 
This improves performance for projects with a large amount of resource files.

## Support for PMD incremental analysis

TBD

This was contributed by [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod).

## Incubating support for Groovy compilation avoidance

Gradle now supports experimental compilation avoidance for Groovy. 
This accelerates Groovy compilation by avoiding re-compiling dependent projects if only non-ABI changes are detected.
See [Groovy compilation avoidance](userguide/groovy_plugin.html#sec:groovy_compilation_avoidance) for more details.

## Closed Eclipse Buildship projects

Closed gradle projects in an eclipse workspace can now be substituted for their respective jar files. In addition to this 
those jars can now be built during Buildship eclipse model synchronization.

The upcoming version of Buildship is required to take advantage of this behavior.

This was contributed by [Christian Fränkel](https://github.com/fraenkelc).

## Executable Jar support with `project.javaexec` and `JavaExec`

TBD

## File case changes when copying files on case-insensitive file systems are now handled correctly

On case-insensitive file systems (e.g. NTFS and APFS), a file/folder rename where only the case is changed is now handled properly by Gradle's file copying operations. 
For example, renaming an input of a `Copy` task called `file.txt` to `FILE.txt` will now cause `FILE.txt` being created in the destination directory. 
The `Sync` task and `Project.copy()` and `sync()` operations now also handle case-renames as expected.

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
