## New and noteworthy

Here are the new features introduced in Gradle 1.3.

### New 'dependencyInsight' report task

The new `dependencyInsight` report complements the `dependencies` report (that was [improved in Gradle 1.2](http://gradle.org/docs/1.2/release-notes#improved-dependency-report)). Where the `dependencies` report provides information on the whole dependency graph, the `dependencyInsight` report focusses on providing information on one (or more) dependencies within the graph.

The report can be used to answer (very common) questions such as:

* Why is this dependency in the dependency graph?
* Exactly which dependencies are pulling this dependency into the graph?
* What is the actual version (i.e. *selected* version) of the dependency that will be used? 
    * Is it the same as what was *requested*?
* Why is the *selected* version of a dependency different to the *requested*?

The *selected* version of a dependency can be different to the *requested* (i.e. user declared) version due to dependency conflict resolution or by explicit dependency force rules. The `dependencyInsight` report makes this clear.

The `dependencyInsight` report task is invaluable when investigating how and why a dependency is resolved, and it is available on all projects out of the box.

#### Example usage

Given the following build script:

    apply plugin: 'java'

    repositories {
        maven { url "http://repo.gradle.org/gradle/repo" }
    }

    dependencies {
		compile 'org.gradle:gradle-tooling-api:1.2'
        compile 'org.slf4j:slf4j-api:1.6.5'
    }

Let's find out about the `org.gradle:gradle-messaging` dependency:

<pre><tt>&gt; gradle dependencyInsight --dependency org.gradle:gradle-messaging
:dependencyInsight
org.gradle:gradle-messaging:1.2
+--- org.gradle:gradle-tooling-api:1.2
|    \--- compile
\--- org.gradle:gradle-core:1.2
     \--- org.gradle:gradle-tooling-api:1.2 (*)

(*) - dependencies omitted (listed previously)</tt>
</pre>

Using this report, it is easy to see that the `gradle-messaging` dependency is being included transitively through `gradle-tooling-api` and `gradle-core` (which itself is a dependency of `gradle-tooling-api`).

Whereas the `dependencies` report shows the path from the top level dependencies down through the transitive dependencies, the `dependencyInsight` report shows the path from a particular dependency to the dependencies that pulled it in. That is, it is an *inverted* view of the `dependencies` report.

The `dependencyInsight` report is also useful for understanding the difference between *requested* and *selected* versions.  

<pre><tt>&gt; gradle dependencyInsight --dependency slf4j --configuration runtime
:dependencyInsight
org.slf4j:slf4j-api:1.6.6 (conflict resolution)
+--- org.gradle:gradle-tooling-api:1.2
|    \--- runtime
+--- org.gradle:gradle-messaging:1.2
|    +--- org.gradle:gradle-tooling-api:1.2 (*)
|    \--- org.gradle:gradle-core:1.2
|         \--- org.gradle:gradle-tooling-api:1.2 (*)
+--- org.gradle:gradle-base-services:1.2
|    +--- org.gradle:gradle-tooling-api:1.2 (*)
|    +--- org.gradle:gradle-messaging:1.2 (*)
|    \--- org.gradle:gradle-core:1.2 (*)
\--- org.gradle:gradle-core:1.2 (*)

org.slf4j:slf4j-api:1.6.5 -> 1.6.6
\--- runtime

(*) - dependencies omitted (listed previously)</tt>
</pre>

Here we see that while `slf4j-api:1.6.5` was *requested*, `slf4j-api:1.6.6` was *selected* due to conflict resolution and that it was pulled in by the other Gradle dependencies.

In this case, we were interested in the `runtime` dependency configuration. The default configuration the report uses is 'compile'.

For more information, see the [DependencyInsightReportTask documentation](dsl/org.gradle.api.tasks.diagnostics.DependencyInsightReportTask.html).

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to our backwards compatibility policy.

## Fixed Issues

The list of issues fixed between 1.2 and 1.3 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.3-rc-1%22%29+ORDER+BY+priority&tempMax=1000).

## Incubating features

We will typically introduce new features as _incubating_ at first, giving you a chance to test them out.
Typically the implementation quality of the new features is already good but the API might still change with the next release based on the feedback we receive.
For some very challenging engineering problems like the Gradle Daemon or parallel builds, it is impossible to get the implementation quality right from the beginning.
So we need you here also as beta testers.
We will iterate on the new feature based on your feedback, eventually releasing it as stable and production-ready.
Those of you who use new features before that point gain the competitive advantage of early access to new functionality in exchange for helping refine it over time.
To learn more read our [forum posting on our release approach](http://forums.gradle.org/gradle/topics/the_gradle_release_approach).

### Improved TestNG html report

(in progress)

### Resolution result API

* (in progress)
* The entry point to the ResolutionResult API has changed, you can get access to the instance of the ResolutionResult from the ResolvableDependencies.

## Upgrading from Gradle 1.2

Please let us know if you encounter any issues during the upgrade to Gradle 1.3, that are not listed below.

### Deprecations

#### Ant-task based Java compiler integration

Gradle currently supports two different Java compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<javac>` Ant task. The native Gradle integration has been the default since Gradle 1.0-milestone-9.

The Ant-task based integration has now been deprecated and will be removed in Gradle 2.0. As a result, the following properties of `CompileOptions` are also
deprecated and will be removed in Gradle 2.0:

* `useAnt`
* `optimize`
* `includeJavaRuntime`

#### Ant-task based Groovy compiler integration

Gradle currently supports two different Groovy compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<groovyc>` Ant task. The native Gradle integration has been the default since Gradle 1.0.

The Ant-task based integration has now been deprecated and will be removed in Gradle 2.0. As a result, the following properties of `GroovyCompileOptions` are also
deprecated and will be removed in Gradle 2.0:

* `useAnt`
* `stacktrace`
* `includeJavaRuntime`

### Potential breaking changes

#### Incubating C++ `Compile` task type removed

This was replaced by `CppCompile` in Gradle 1.2. You should use the replacement class instead.

#### Incubating `GppCompileSpec` properties removed

The deprecated `task` property was removed from `GppCompileSpec`.

#### Removed GraphvizReportRenderer (private API)

This type was an early contribution. It is unlikely anyone uses it because it does not work and it is an undocumented private type.

#### Removed `org.gradle.api.publication` package

This package contained some early experiments in a new publication model. It was incomplete and undocumented. It is superseded by the new `org.gradle.api.publish` (incubating) package
introduced in Gradle 1.3 so has been removed.