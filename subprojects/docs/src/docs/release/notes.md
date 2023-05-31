The Gradle team is excited to announce Gradle @version@.

Kotlin DSL continues to receive substantial improvements.
The new [reference documentation](#kotlin-dsl-reference) makes Kotlin DSL easier to understand.
Also, simple property assignment  with the `=` operator introduced to Kotlin DSL in the last release is now enabled by default.
Finally, Kotlin DSL is now the [default option](#gradle-init-defaults-to-the-kotlin-dsl) when generating a new project with the init task.

This release also brings a number of usability improvements including better [error messages](#improved-console-output), automated validation of [distribution URL](#wrapper-task-validates-distribution-url) in the wrapper task,
[progress display](#java-toolchains-discovery-progress-display) for discovery of Java toolchains, more efficient [dependency verification](#dependency-verification-improvements) and more.


<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:
[Bruno Didot](https://github.com/didot),
[Eric Vantillard](https://github.com/evantill),
[esfomeado](https://github.com/esfomeado),
[Jendrik Johannes](https://github.com/jjohannes),
[Jonathan Leitschuh](https://github.com/JLLeitschuh),
[Lee Euije](https://github.com/euije),
[Stefan Oehme](https://github.com/oehme),
[Todor Dinev](https://github.com/tdinev),
[Yanshun Li](https://github.com/Chaoba)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

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

### Kotlin DSL improvements

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an enhanced editing experience in supported IDEs compared to the traditional Groovy DSL — superior content assistance, refactoring, documentation, and more.

Kotlin DSL has received substantial improvements in the recent releases, leading to the announcement that [Kotlin DSL is Now the Default for New Gradle Builds](https://blog.gradle.org/kotlin-dsl-is-now-the-default-for-new-gradle-builds).
This release brings another series of improvements.

#### Kotlin DSL reference

A brand new [reference documentation](kotlin-dsl/index.html) for the Gradle Kotlin DSL is now published and versioned alongside the user manual.

You can use the [Kotlin DSL reference search functionality to drill through the APIs.

#### Simple property assignment in Kotlin DSL scripts enabled by default

The last release [introduced](/8.1/release-notes.html#experimental-simple-property-assignment-in-kotlin-dsl-scripts) the simpler way to assign values to [lazy property types](userguide/lazy_configuration.html#lazy_properties) in Kotlin scripts using the `=` operator instead of  the `set()` method.
This feature is now available by default.

The new assignment operator is still incubating.
In case of any issues you can opt-out by adding  `systemProp.org.gradle.unsafe.kotlin.assignment=false` to the `gradle.properties` file.

For more information see [Kotlin DSL Primer](userguide/kotlin_dsl.html#kotdsl:assignment).

#### Gradle `init` defaults to the Kotlin DSL

Starting with this release, bootstrapping a new project with the [`gradle init` command](userguide/build_init_plugin.html) now defaults to generating new builds using the Kotlin DSL instead of Groovy DSL.

In interactive mode you can choose which DSL to use and the Kotlin one is now listed first:

```text
Select build script DSL:
  1: Kotlin
  2: Groovy
Enter selection (default: Kotlin) [1..2]
```

See the [build init](userguide/build_init_plugin.html#sec:what_to_set_up) user manual chapter for more information.

#### Fail on script compilation warnings

Gradle [Kotlin DSL scripts](userguide/kotlin_dsl.html#sec:scripts) are compiled by Gradle during the configuration phase of your build.
Deprecation warnings found by the Kotlin compiler are reported on the console when compiling the scripts.

In order to catch potential issues faster, it is now possible to configure your build to fail on any warning emitted during script compilation by setting the `org.gradle.kotlin.dsl.allWarningsAsErrors` Gradle property to `true`:

```properties
# gradle.properties
org.gradle.kotlin.dsl.allWarningsAsErrors=true
```

More details can be found in the dedicated section of the [Kotlin DSL](userguide/kotlin_dsl.html#sec:compilation_warnings) user manual chapter.

### Improved console output

In the new release, we have taken the first step towards implementing the [clean and actionable error reporting](https://github.com/gradle/build-tool-roadmap/issues/49) item from our public roadmap, by making a number of improvements to the console output in case of build failures.

Suggestions that were previously a part of the error message are now displayed in the `* Try` section, making them more noticeable.
Additionally, the output is streamlined by eliminating unnecessary links to `help.gradle.org` for recoverable errors, such as compilation failures.

You can review the complete list of these console output enhancements [here](https://github.com/gradle/gradle/issues?q=is%3Aissue+sort%3Aupdated-desc+milestone%3A%228.2+RC1%22+label%3Ain%3Aconsole+is%3Aclosed).

### Wrapper task validates distribution URL

The [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) stores the URL of Gradle distribution in the `gradle-wrapper.properties` file.

Previously, specifying an invalid URL led to I/O exceptions during the build execution.

Starting from this release, the wrapper task automatically validates the configured distribution URL before writing it to the `gradle-wrapper.properties` file.This surfaces invalid URLSs early and prevents exceptions at execution time.

More details can be found in the dedicated section of the [Gradle Wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper) user manual chapter.

### Java toolchains discovery progress display

Using [Toolchains](https://docs.gradle.org/current/userguide/toolchains.html) is a recommended way of specifying Java versions for JVM projects.

Progress is now displayed during [Java toolchains discovery](userguide/toolchains.html#sec:auto_detection).

This can be especially useful during a cold-start of Gradle for users who have environments with a lot of JVM installations in them.

### Dependency verification improvements

To mitigate the security risks and avoid integrating compromised dependencies in your project, Gradle supports [dependency verification](userguide/dependency_verification.html).

[Exported PGP keys](userguide/dependency_verification.html#sec:local-keyring) are now stripped to contain only necessary data.
This feature can significantly reduce the size of the keyring files.

In addition, the exported keyring is sorted by key id and de-duplicated to ensure a consistent ordering of keys, which minimizes potential conflicts when updating the keyring.

In order to benefit from these changes, users will have to [generate again their keyring](userguide/dependency_verification.html#sec:local-keyring).

### Boolean task options generate opposite option

Task options of type `boolean`, `Boolean`, and `Property<Boolean>` now automatically generate an opposite option to make setting the value to `false` easier in the command-line interface.

This feature lets you conveniently set the Boolean option to `false`, simply by using the `--no-` prefix.
For example, `--no-foo` option is generated for the provided option `--foo`.

See the [task options](userguide/custom_tasks.html#sec:declaring_and_using_command_line_options) user manual section for more information.

### Built init can convert more Maven builds

Using [`gradle init`](userguide/build_init_plugin.html#sec:pom_maven_conversion) to convert a multimodule Maven build previously required the child submodule projects’ POMs to reference their parent POM using:

```
<parent>
  <artifactId>parent-project</artifactId>
  <groupId>com.example</groupId>
  <version>1.0</version>
</parent>
```

Without these backreferences, the conversion process would fail.

The process has been updated to succeed also when the `parent` element is absent from the submodules.

### Dependency Configurations Usage Checking

Certain methods in the Configuration API should only be called when the configuration is in a [particular “role”](userguide/declaring_dependencies.html#sec:resolvable-consumable-configs) as determined by the `isCanBeResolved()`/`isCanBeConsumed()`/`isCanBeDeclared()` methods.
For example, calling `resolve()` on a non-resolvable Configuration should not be done.
These proper usages are now mentioned on the `Configuration` type’s [javadoc](javadoc/org/gradle/api/artifacts/Configuration.html).
Calling a method that is not appropriate will now in many cases emit a deprecation warning.

### Dependency Configurations Name Checking

Creating a configuration with the same naming pattern used to create [detached configurations](dsl/org.gradle.api.artifacts.ConfigurationContainer.html#org.gradle.api.artifacts.ConfigurationContainer:detachedConfiguration(org.gradle.api.artifacts.Dependency[])) will now emit a warning.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
