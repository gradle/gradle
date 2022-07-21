
The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:

[altrisi](https://github.com/altrisi),
[aSemy](https://github.com/aSemy),
[Ashwin Pankaj](https://github.com/ashwinpankaj),
[BJ Hargrave](https://github.com/bjhargrave),
[Daniel Lin](https://github.com/ephemient),
[David Morris](https://github.com/codefish1),
[Edmund Mok](https://github.com/edmundmok),
[Frosty-J](https://github.com/Frosty-J),
[Gabriel Feo](https://github.com/gabrielfeo),
[Jendrik Johannes](https://github.com/jjohannes),
[John](https://github.com/goughy000),
[Joseph Woolf](https://github.com/jsmwoolf),
[Karl-Michael Schindler](https://github.com/kamischi),
[Konstantin Gribov](https://github.com/grossws),
[Leonardo Brondani Schenkel](https://github.com/lbschenkel),
[Martin d'Anjou](https://github.com/martinda),
[Sam Snyder](https://github.com/sambsnyd),
[sll552](https://github.com/sll552),
[teawithbrownsugar](https://github.com/teawithbrownsugar),
[Thomas Broadley](https://github.com/tbroadley),
[urdak](https://github.com/urdak),
[Xin Wang](https://github.com/scaventz)


## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

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

## New features and usability improvements

### Improvements for IDE integrators

#### Tooling API progress events expose difference between test assertion failures and test framework failures

Gradle 7.6 introduces new failure types for the `Failure` interface returned by [FailureResult.getFailures()](javadoc/org/gradle/tooling/events/FailureResult.html#getFailures--): TestAssertionFailure and TestFrameworkFailure.
IDEs can now easily distinguish between different failures using standard progress event listeners.
Moreover, `TestAssertionFailure` exposes the expected and actual values if the used test framework supply such information.

#### Task execution with TestLauncher

The [TestLauncher](javadoc/org/gradle/tooling/TestLauncher.html) interface now allows Tooling API clients to execute any tasks along with the selected tasks.

```
ProjectConnection connection = ...
connection.newTestLauncher()
          .withTaskAndTestClasses("integTest", ["org.MyTest"])
          .forTasks("startDB")
          .run()
```

Note, that the task execution only works if the target Gradle version is >=7.6.

#### Fine-grained test selection with TestLauncher

The [TestLauncher](javadoc/org/gradle/tooling/TestLauncher.html) interface now allows Tooling API clients to select test classes, methods, packages and patterns with a new API.

```
TestLauncher testLauncher = projectConnection.newTestLauncher();
testLauncher.withTestsFor(spec -> {
    spec.forTaskPath(":test")
        .includePackage("org.pkg")
        .includeClass("com.TestClass")
        .includeMethod("com.TestClass")
        .includePattern("io.*")
}).run();
```

Note, that the new test selection interface only works if the target Gradle version is >=7.6.

### Improved Maven Conversion

The `init` task now adds compile-time Maven dependencies to Gradle's `api` configuration when converting a Maven project. This sharply reduces the number of compilation errors resulting from the automatic conversion utility. See the [Build Init Plugin](userguide/build_init_plugin.html#sec:pom_maven_conversion) for more information.

<a name="configuration-cache-improvements"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing this for subsequent builds.

#### New compatible tasks

The `dependencies`, `buildEnvironment`, `projects` and `properties` tasks are now compatible with the configuration cache.

### Resolvable configurations report displays attribute precedence order

The `resolvableConfigurations` reporting task will now print the order that [attribute disambiguation rules](userguide/variant_attributes.html#sec:abm_disambiguation_rules) will be checked when resolving project dependencies.  These rules are used if multiple variants of a dependency are available with different compatible values for a requested attribute, and no exact match.  Disambiguation rules will be run on all attributes with multiple compatible matches in this order to select a single matching variant.

```
--------------------------------------------------
Disambiguation Rules
--------------------------------------------------
The following Attributes have disambiguation rules defined.

    - flavor
    - org.gradle.category (1)
    - org.gradle.dependency.bundling (5)
    - org.gradle.jvm.environment (6)
    - org.gradle.jvm.version (3)
    - org.gradle.libraryelements (4)
    - org.gradle.plugin.api-version
    - org.gradle.usage (2)

(#): Attribute disambiguation precedence
```

### Configurable wrapper download network timeout

It is now possible to configure the network timeout for downloading the wrapper files.
The default value is 10000ms and can be changed in several ways:

From the command line:
```shell
gradle wrapper --network-timeout=30000
```

In your build scripts or convention plugins:
```kotlin
tasks.wrapper {
    networkTimeout.set(30000)
}
```

Or in `gradle/wrapper/gradle-wrapper.properties`:
```properties
networkTimeout=30000
```

See the [user manual](userguide/gradle_wrapper.html#sec:adding_wrapper) for more information.

### Command-line improvements

#### Tasks can be re-run selectively 

A new built-in `--rerun` option is now available for every task. The effect is similar to `--rerun-tasks`, but it forces a rerun only on the specific task to which it was directly applied. For example, you can force tests to run ignoring up-to-date checks like this:
```
gradle test --rerun
```

See the [documentation](userguide/command_line_interface.html#sec:builtin_task_options) for more information.

### Improvements for plugin authors

#### Integer task options

It is now possible to pass integer task options declared as `Property<Integer>` from the command line.

For example, the following task option:
```java
@Option(option = "integer-option", description = "Your description")
public abstract Property<Integer> getIntegerOption();
```

can be passed from the command line as follows:
```shell
gradle myCustomTask --integer-option=123
```

### JVM language support improvements

#### Java and Groovy incremental compilation after a failure

Gradle already supports [Java incremental compilation](userguide/java_plugin.html#sec:incremental_compile) by default and [Groovy incremental compilation](userguide/groovy_plugin.html#sec:incremental_groovy_compilation) as an opt-in experimental feature.
In previous versions after a compilation failure the next compilation was not incremental but a full recompilation instead.
With this version, Java and Groovy incremental compilation will work incrementally also after a failure.
This improves experience with compilation when working iteratively on some Java or Groovy code, e.g. when iteratively running compile or test tasks from an IDE.

#### Better test compatibility with Java 9+

When running on Java 9+, Gradle no longer opens the `java.base/java.util` and `java.base/java.lang` JDK modules for all `Test` tasks. In some cases, this would cause code to pass during testing but fail at runtime.  

This change may cause new test failures and warnings. When running on Java 16+, code performing reflection on JDK internals will now fail tests. When running on Java 9-15, illegal access warnings will appear in logs. While this change may break some existing builds, most failures are likely to uncover suppressed issues which would have only been detected at runtime.

For a detailed description on how to mitigate this change, please see the [upgrade guide for details](userguide/upgrading_version_7.html#removes_implicit_add_opens_for_test_workers).

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

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
