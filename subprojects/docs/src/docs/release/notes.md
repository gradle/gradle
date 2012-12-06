## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
### Example new and noteworthy
-->

### Easier to embed Gradle via [Tooling API](userguide/embedding.html)

We continuously look for ways to improve the experience of embedding Gradle.
The standard way to embed Gradle, [The Tooling API](userguide/embedding.html) used to ship in multiple jars, including some 3rd party libraries.
In Gradle 1.4 we refactored the publication and packaging of the Tooling API. The Tooling API is now shipped in a single jar.
All you need to work with the Tooling API is the tooling api jar and slf4j.
Furthermore, we repackaged the Tooling API's 3rd party transitive dependencies to avoid conflicts
with different versions you might already have on your classpath. Happy embedding!Now go and embed Gradle!

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

<!--
### Example promoted
-->

## Incubating features

Incubating features are intended to be used, but not yet guaranteed to be backwards compatible.
By giving early access to new features, real world feedback can be incorporated into their design.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the new incubating features or changes to existing incubating features in this Gradle release.

<!--
### Example incubating feature
-->

## Deprecations

### Certain task configuration after execution of task has been started.

Changing certain task configuration does not make sense when the task is already being executed.
For example, imagine that at execution time, the task adds yet another doFirst {} action.
The task is already being executed so adding a *before* action is too late and it is probably a user mistake.
In order to provide quicker and higher quality feedback on user mistakes
we want to prevent configuring certain task properties when the task is already being executed.
For backwards compatibility reasons, certain task configuration is deprecated. This includes:

* Mutating Task.getActions()
* Calling Task.setActions()
* Calling Task.dependsOn()
* Calling Task.setDependsOn()
* Calling Task.onlyIf()
* Calling Task.setOnlyIf()
* Calling Task.doLast()
* Calling Task.doFirst()
* Calling Task.leftShift()
* Calling Task.setEnabled()
* Calling TaskInputs.files()
* Calling TaskInputs.file()
* Calling TaskInputs.dir()
* Calling TaskInputs.property()
* Calling TaskInputs.properties()
* Calling TaskInputs.source()
* Calling TaskInputs.sourceDir()
* Calling TaskOutputs.upToDateWhen()
* Calling TaskOutputs.files()
* Calling TaskOutputs.file()
* Calling TaskOutputs.dir()

## Potential breaking changes

### Incubating DependencyInsightReport throws better exception

For consistency, InvalidUserDataException is thrown instead of ReportException when user incorrectly uses the dependency insight report.

### Removed getSupportsAppleScript() in org.gradle.util.Jvm

In the deprecated internal class `org.gradle.util.Jvm` we removed the method `getSupportsAppleScript()` to check that AppleScriptEngine is available on the Jvm.
As a workaround you can dynamically check if the AppleScriptEngine is available:

    import javax.script.ScriptEngine
    import javax.script.ScriptEngineManager

    ScriptEngineManager mgr = new ScriptEngineManager();
    ScriptEngine engine = mgr.getEngineByName("AppleScript");
    boolean isAppleScriptAvailable = engine != null;

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

James Bengeyfield - `showViolations` flag for `Checkstyle` task (GRADLE-1656)

Dalibor Novak - `m2compatible` flag on `PatternRepositoryLayout` (GRADLE-1919)

Brian Roberts, Tom Denley - Support multi-line JUnit test names (for better ScalaTest compatibility) (GRADLE-2572)

<!--
* Some Person - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).