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

The *selected* version of a dependency can be different to the *requested* (i.e. user declared) version due to dependency conflict resolution or by explicit dependency force rules. Similar to the standard gradle depenency report, the `dependencyInsight` report shows both versions. It also shows a requested dynamic version (e.g. "junit:junit:4.+") together with the actually selected version (e.g. "junit:junit:4.10"). Please keep in mind that Maven snapshot dependencies are not treated as dynamic versions but as changing modules, similar to what Maven does (for the difference see the [userguide](http://gradle.org/docs/nightly/userguide/dependency_management.html#sec:dependency_management_overview)). Maven snapshots might be treated as dynamic versions in a future version of Gradle which would provide nice insight into pom snapshot resolution.   

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

### Incremental Scala compilation

By compiling only classes whose source code has changed since the previous compilation, and classes affected by these changes, incremental
compilation can significantly reduce Scala compilation time. It is particularly effective when frequently compiling small code increments,
as is often done at development time.

The Scala plugin now supports incremental compilation by integrating with [Zinc](https://github.com/typesafehub/zinc),
a standalone version of [sbt](https://github.com/harrah/xsbt)'s incremental Scala compiler. To switch the `ScalaCompile` task from the default
Ant based compiler to the new Zinc based compiler, use `scalaCompileOptions.useAnt = false`. Except where noted in the
[API documentation](http://gradle.org/docs/current/dsl/org.gradle.api.tasks.scala.ScalaCompile.html), the Zinc based compiler supports exactly
the same configuration options as the Ant based compiler.

Just like the Ant based compiler, the Zinc based compiler supports joint compilation of Java and Scala code. By default, all Java and Scala code
under `src/main/scala` will participate in joint compilation. With the Zinc based compiler, even Java code will be compiled incrementally.

To learn more about incremental Scala compilation, see the [Scala plugin](userguide/scala_plugin.html#N12A97) chapter in the Gradle User Guide.

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

TestNG received a decent dose of love in Gradle 1.3. Your Gradle projects with TestNG tests now enjoy much better reports:

    * Both reports: xml (for CI) and html reports (for you) contain test output,  e.g. messages logged to standard streams or standard logging toolkits. This is extremely useful for debugging certain test failures. Xml results means
    * The html report is way, way easier to read and browse. Did I mention it contains the test output?
    * The reports neatly work with Gradle's parallel testing (test.maxParallelForks) and forking features (test.forkEvery). Run your tests in parallel so that they run faster!

The reports are not yet turned on by default... They are @Incubating and we need more feedback before we make Gradle use them by default. The new report might exhibit increased heap space usage for tests that eagerly log to the standard streams. In case your tests get hungry here's how you can feed them: To enable the new test reports, please set the test.testReport = true.

### Resolution result API

* (in progress)
* The entry point to the ResolutionResult API has changed, you can get access to the instance of the ResolutionResult from the ResolvableDependencies.

## Deprecations

### Ant-task based Java compiler integration

Gradle currently supports two different Java compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<javac>` Ant task. The native Gradle integration has been the default since Gradle 1.0-milestone-9.

The Ant-task based integration has now been deprecated and will be removed in Gradle 2.0. As a result, the following properties of `CompileOptions` are also
deprecated and will be removed in Gradle 2.0:

* `useAnt`
* `optimize`
* `includeJavaRuntime`

### Ant-task based Groovy compiler integration

Gradle currently supports two different Groovy compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<groovyc>` Ant task. The native Gradle integration has been the default since Gradle 1.0.

The Ant-task based integration has now been deprecated and will be removed in Gradle 2.0. As a result, the following properties of `GroovyCompileOptions` are also
deprecated and will be removed in Gradle 2.0:

* `useAnt`
* `stacktrace`
* `includeJavaRuntime`

### Changing the name of a repository once added to a repository container

The [`ArtifactRepository`](http://gradle.org/docs/current/javadoc/org/gradle/api/artifacts/repositories/ArtifactRepository.html) type has a `setName(String)` method that you
could use to change the repository name after it has been created. Doing so has been deprecated. The name of the repository should be specified at creation time via the DSL.

For example:

    repositories {
        ivy {
            name "my-ivy-repo"
        }
    }
    
    // The following is now deprecated
    repositories.ivy.name = "changed-name"

A deprecation warning will be issued if a name change is attempted.

## Potential breaking changes

### Incubating C++ `Compile` task type removed

This was replaced by `CppCompile` in Gradle 1.2. You should use the replacement class instead.

### Incubating `GppCompileSpec` properties removed

The deprecated `task` property was removed from `GppCompileSpec`.

### Removed GraphvizReportRenderer (private API)

This type was an early contribution. It is unlikely anyone uses it because it does not work and it is an undocumented private type.

### Removed `org.gradle.api.publication` package

This package contained some early experiments in a new publication model. It was incomplete and undocumented. It is superseded by the new `org.gradle.api.publish` (incubating) package
introduced in Gradle 1.3 so has been removed.

## External Contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* Matt Khan - fixed resetting of `test.debug` to `false` when using `test.jvmArgs`  (GRADLE-2485)
* Gerd Aschemann - fixes for `application` plugins generated shell scripts (GRADLE-2501)
* Cruz Fernandez - fixes to the properties sample project
* Fadeev Alexandr - fixes for Gradle Daemon on Win 7 when `PATH` env var is not set (GRADLE-2461)

We love getting contributions from the Gradle community. For information on contributing, please see (gradle.org/contribute)[http://gradle.org/contribute]