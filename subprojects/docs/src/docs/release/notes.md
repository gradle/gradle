The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

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

## Debug support for forked Java processes

Gradle has now a new DSL element to configure debugging for Java processes.  
 
```groovy
project.javaexec {

  debugOptions {
     enabled = true
     port = 4455
     server = true
     suspend = true
   }
}
```

This configuration appends the following JVM argument to the process: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=4455` 
 
The `debugOptions` configuration is available for `project.javaExec` and for tasks using the `JavaExec` type, including the `test` task.
 
## Debug tests via the Tooling API
 
In addition to the new DSL element above, the Tooling API is capable of launching tests in debug mode. Clients can  invoke `TestLauncher.debugTestsOn(port)` to launch a test in debug mode. This feature will be used in the upcoming Buildship release.

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
