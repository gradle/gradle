The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:
[BJ Hargrave](https://github.com/bjhargrave),
[altrisi](https://github.com/altrisi),
[aSemy](https://github.com/aSemy),
[Ashwin Pankaj](https://github.com/ashwinpankaj),
[Frosty-J](https://github.com/Frosty-J),
[Gabriel Feo](https://github.com/gabrielfeo),
[Sam Snyder](https://github.com/sambsnyd),
[teawithbrownsugar](https://github.com/teawithbrownsugar),
[John](https://github.com/goughy000),
[sll552](https://github.com/sll552)

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

### Improvements for plugin authors

### Integer task options

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

See the [user manual](userguide/custom_tasks.html#sec:supported_task_option_data_types) for more information.

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
