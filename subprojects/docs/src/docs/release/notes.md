## New and noteworthy

Here are the new features introduced in Gradle 1.3.

### New dependencyInsight report task

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

Here we see that while `slf4j-api:1.6.5` was *requested*, `slf4j-api:1.6.6` was *selected* due to the conflict resolution. We can also see which dependency pulled in the slf4j.

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

### Scala compilation in external process

Scala compilation can now be performed outside the Gradle JVM in a dedicated compiler process, which can help to deal with memory issues. External
compilation is supported both for the existing Ant-based and the new Zinc-based Scala compiler. The API is very similar to that for Java and
Groovy: [`ScalaCompile.fork = true`](dsl/org.gradle.api.tasks.scala.ScalaCompile.html#org.gradle.api.tasks.scala.ScalaCompile:fork)
activates external compilation, and [`ScalaCompile.forkOptions`](dsl/org.gradle.api.tasks.scala.ScalaCompile.html#org.gradle.api.tasks.scala.ScalaCompile:forkOptions)
allows to adjust memory settings.

### Improved Scala IDE integration

The [Eclipse Plugin](http://gradle.org/docs/current/userguide/eclipse_plugin.html) now automatically excludes dependencies already provided by the
 'Scala Library' class path container. This is necessary for [Scala IDE](http://scala-ide.org) to work correctly.

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

TestNG received a decent dose of love in Gradle 1.3. We are in the process of moving away from using native TestNG reporting provided by TestNG's 'default listeners'.
Instead we would like to take full advantage of our own infrastructure for test reporting.
For an example how nice the new TestNG reports can be take a look at the [reports generated by our CI builds](http://builds.gradle.org/repository/download/bt9/.lastSuccessful/reports/ide/integTest/index.html).
More details:

* Both reports: xml (for CI) and html reports (for you) contain the test output,  e.g. the messages logged to the standard streams or via the standard logging toolkits. This is extremely useful for debugging certain test failures.
* The html report is way, way easier to read and browse. Did I mention it contains the test output? Here is the [example](http://builds.gradle.org/repository/download/bt9/.lastSuccessful/reports/ide/integTest/index.html).
* The reports neatly work with Gradle parallel testing ([test.maxParallelForks](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:maxParallelForks)) and forking features ([test.forkEvery](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:forkEvery)). Run your tests in parallel so that the build is faster!

The reports are not yet turned on by default. They are @Incubating and we need more feedback before we make Gradle use them by default. The new report might exhibit increased heap usage for tests that eagerly log to the standard streams. In case your tests get hungry you can easily feed them: see the code samples [in the dsl reference](dsl/org.gradle.api.tasks.testing.Test.html). To enable the new test reports, please set the [test.testReport = true](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:testReport).

## Deprecations

### Ant-task based Java compiler integration

Gradle currently supports two different Java compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<javac>` Ant task. The native Gradle integration has been the default since Gradle 1.0-milestone-9. Some of its advantages are:

* Faster compilation
* Can run in Gradle compiler daemon (external process that is reused between tasks)
* More amenable to future enhancements

The Ant-task based integration has now been deprecated and will be removed in Gradle 2.0. As a result, the following properties of `CompileOptions` are also
deprecated and will be removed in Gradle 2.0:

* `useAnt`
* `optimize`
* `includeJavaRuntime`

### Ant-task based Groovy compiler integration

Gradle currently supports two different Groovy compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<groovyc>` Ant task. The native Gradle integration has been the default since Gradle 1.0. Some of its advantages are:

* Faster compilation
* Correct handling of AST transformations
* Can run in Gradle compiler daemon (external process that is reused between tasks)
* More amenable to future enhancements

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

## Changes to the existing incubating features

### Resolution result API

The entry point to the ResolutionResult API has changed, you can get access to the instance of the ResolutionResult via [configuration.incoming.resolutionResult](dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:incoming).
New convenience methods were added to API. For more information please refer to the javadoc for (ResolutionResult)[docs/javadoc/org/gradle/api/artifacts/result/ResolutionResult.html].

### Incubating C++ `Compile` task type removed

This was replaced by `CppCompile` in Gradle 1.2. You should use the replacement class instead.

### Incubating `GppCompileSpec` properties removed

The deprecated `task` property was removed from `GppCompileSpec`.

## Potential breaking changes

### Removed GraphvizReportRenderer (private API)

This type was an early contribution. It is unlikely anyone uses it because it does not work and it is an undocumented private type.

### Removed `org.gradle.api.publication` package

This package contained some early experiments in a new publication model. It was incomplete and undocumented. It is superseded by the new `org.gradle.api.publish` (incubating) package
introduced in Gradle 1.3 so has been removed.

### The behavior of (Test.testReport)[dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:testReport] property for TestNG.

* Test.testReport default value has been changed to 'false' for TestNG.
This property was ignored when running with TestNG - the html results were generated anyway.
Since the property was not really used we believe that it is safe to change its default value.
The reason we need to do it is because we've added a brand new TestNG reporting (see the section above).
The new reporting is still @Incubating so we would keep it off by default in this release.
* For more information on how the testReport property is used by TestNG see the (dsl reference)[dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:testReport]

## External Contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* Matt Khan - fixed resetting of `test.debug` to `false` when using `test.jvmArgs`  (GRADLE-2485)
* Gerd Aschemann - fixes for `application` plugins generated shell scripts (GRADLE-2501)
* Cruz Fernandez - fixes to the properties sample project
* Fadeev Alexandr - fixes for Gradle Daemon on Win 7 when `PATH` env var is not set (GRADLE-2461)
* Ben Manes - improved Scala IDE integration (https://github.com/gradle/gradle/pull/99)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).