This is a backport release, Gradle @version@.

This is the fourth patch release for Gradle 6.9.

It fixes the following issues:
* [#23680](https://github.com/gradle/gradle/issues/23680) Dependency graph resolution: Equivalent excludes can cause un-necessary graph mutations [backport 6.x]
* [#23945](https://github.com/gradle/gradle/issues/23945) Backport trusting only full GPG keys in dependency verification [Backport 6.9.4]
* [#23950](https://github.com/gradle/gradle/issues/23950) Exclude rule merging: missing optimization [backport 6.x]

Issues fixed in the third patch release:
* [#19523](https://github.com/gradle/gradle/issues/19523) Fix buffer overflow error in KryoBackedDecoder [Backport 6.x]
* [#20189](https://github.com/gradle/gradle/issues/20189) Support constraints without version in GMM [Backport 6.9.x]
* [#22358](https://github.com/gradle/gradle/issues/22358) Missing exclude rule merging optimizations

Issues fixed in the second patch release:
* [#18163](https://github.com/gradle/gradle/issues/18163) Fix excludes for substituted dependencies
* [#18164](https://github.com/gradle/gradle/issues/18164) POSIX shell scripts improvements
* [#18697](https://github.com/gradle/gradle/issues/18697) Fix corrupted resolution result from replacement / capability conflict
* [#19328](https://github.com/gradle/gradle/issues/19328) Mitigations for log4j vulnerability in Gradle builds
* [#19372](https://github.com/gradle/gradle/issues/19372) Multiple transformed artifacts selected

Issues fixed in first patch release:
* [#17949](https://github.com/gradle/gradle/issues/17949) Gradle's up-to-date checks do not work on Windows FAT drives
* [#17950](https://github.com/gradle/gradle/issues/17950) Renaming and recreating the project directory causes Gradle to lose track of changes on Windows
* [#18089](https://github.com/gradle/gradle/issues/18089) Deprecate jcenter() repository

We recommend users upgrade to @version@ instead of 6.9.

Given the context of the Log4Shell vulnerability, make sure you take a look at [our blog post](https://blog.gradle.org/log4j-vulnerability) on this topic.

----

This release features bugfixes and other changes that were [backported](#backports) from Gradle 7.x to Gradle 6.x.

We would like to thank the following community contributors to this release of Gradle:
[Ståle Undheim](https://github.com/staale),
[Fodor Zoltan](https://github.com/archfz)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

NOTE: Gradle 6.9 has had **two** patch release, which fixes several issues from the original release.
We recommend always using the latest patch release.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## Backports

### Limited support for Java 16

This release does not support _running_ Gradle with JDK 16, but you can use [Java toolchains](userguide/toolchains.html) to request Java 16 and compile your project.

### Using dynamic versions in the plugins block

Until now, the `plugins { }` block only supported fixed versions for community plugins. All [version string notations Gradle supports](userguide/single_versions.html) are now accepted, including `+` or `latest.release`.

We recommend using the `plugins {}` block for applying plugins using Gradle 7. The old `apply plugin:` mechanism will be deprecated in the future.

Note that dynamic versions will introduce non-deterministic behavior to your build process and should be used judiciously. You can use [dependency locking](userguide/dependency_locking.html) to save the set of dependencies resolved when using dynamic versions.

### Native support for Apple Silicon

Previous Gradle versions were able to run on new Macs with Apple Silicon processors with some disadvantages:

* With a native ARM JDK, Gradle features like the [rich console](userguide/command_line_interface.html#sec:command_line_customizing_log_format) and [file system watching](userguide/gradle_daemon.html#sec:daemon_watch_fs) would be disabled.
* With an Intel JDK, Gradle would run at about half speed through the Rosetta2 compatibility layer.

With this release, every feature is now supported using a native ARM JDK.
If you're using a new Mac with Apple Silicon, you should use Gradle with a native ARM JDK for optimal performance.

### Other backports

Please refer to the list below for all issues backported from Gradle 7.0.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
