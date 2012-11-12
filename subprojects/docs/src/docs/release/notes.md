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

The `dependencyInsight` report task is invaluable when investigating how and why a dependency is resolved, and it is available in all projects out of the box.

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

In this case, we were interested in the `runtime` dependency configuration. The default configuration the report uses is `compile`.

For more information, see the [DependencyInsightReportTask documentation](dsl/org.gradle.api.tasks.diagnostics.DependencyInsightReportTask.html).

### Incremental Scala compilation

Due to the sophistication of the language and type system, Scala programs can take a long time to compile. Gradle 1.3 addresses this problem by
integrating with [Zinc](https://github.com/typesafehub/zinc), a standalone version of [sbt](https://github.com/harrah/xsbt)'s incremental Scala
compiler. By compiling only classes whose source code has changed since the previous compilation, and classes affected by these changes,
Zinc can significantly reduce Scala compilation time. It is particularly effective when frequently compiling small code
increments, as is often done at development time.

To switch the `ScalaCompile` task from the default Ant based compiler to the new Zinc based compiler, use `scalaCompileOptions.useAnt = false`. 
Except where noted in the [API documentation](http://gradle.org/docs/current/dsl/org.gradle.api.tasks.scala.ScalaCompile.html), the Zinc based 
compiler supports exactly the same configuration options as the Ant based compiler.

Just like the Ant based compiler, the Zinc based compiler supports joint compilation of Java and Scala code. By default, all Java and Scala code
under `src/main/scala` will participate in joint compilation. With the Zinc based compiler, even Java code will be compiled incrementally.

To learn more about incremental Scala compilation, see the [Scala plugin](userguide/scala_plugin.html#N12A97) chapter in the Gradle User Guide.

### Scala compilation in external process

Scala compilation can now be performed outside the Gradle JVM in a dedicated compiler process, which can help to deal with memory issues. 

External compilation is supported both for the existing Ant-based and the new Zinc-based Scala compiler. The API is very similar to that for Java and
Groovy: [`ScalaCompile.fork = true`](dsl/org.gradle.api.tasks.scala.ScalaCompile.html#org.gradle.api.tasks.scala.ScalaCompile:fork)
activates external compilation, and [`ScalaCompile.forkOptions`](dsl/org.gradle.api.tasks.scala.ScalaCompile.html#org.gradle.api.tasks.scala.ScalaCompile:forkOptions)
allows to adjust memory settings.

### Improved Scala IDE integration

The [Eclipse Plugin](http://gradle.org/docs/current/userguide/eclipse_plugin.html) now automatically excludes dependencies already provided by the
 'Scala Library' class path container. This improvement is essential for [Scala IDE](http://scala-ide.org) to work correctly. It also takes effect
 when using the [Eclipse Gradle Integration](https://github.com/SpringSource/eclipse-integration-gradle).

### Dependency management improvements

With this release of Gradle, we have continued to improve our dependency management implementation. The focus for these improvements in Gradle 1.3 is on
stability and this release includes a number of important fixes.

#### Artifact cache stability

This release fixes a number of issues that resulted in corruption of the artifact cache. In particular, Gradle will be much more stable in the case
where you have many concurrent builds running on a machine, such as a CI build server, and these builds have dependencies that change frequently, such
as Maven snapshots, or are using a very short cache expiry time.

* [GRADLE-2544](http://issues.gradle.org/browse/GRADLE-2544) - Potential corruption of artifact cache on concurrent access.
* [GRADLE-2458](http://issues.gradle.org/browse/GRADLE-2458) - Failures to store meta-data are silently ignored.
* [GRADLE-2457](http://issues.gradle.org/browse/GRADLE-2457) - Potential corruption of artifact cache after a crash.

#### Smarter handling of missing modules

Gradle caches the fact that a given module is missing from a given repository. It uses this information to avoid unnecessary network requests when you
are using multiple repositories, and when resolving dynamic versions. Previous versions of Gradle were overly keen in using this information, leading
to problems where a misconfiguration would cause Gradle to decide that a certain module is missing and will be missing forever. In this release, Gradle
uses a more sensible strategy to decide when to verify that something that it considers to be missing is, in fact, missing.

* [GRADLE-2455](http://issues.gradle.org/browse/GRADLE-2455) - Smarter handling of missing module versions.

#### Ivy latest status improvements

Previous Gradle releases had a number of issues resolving dynamic versions such as 'latest.integration' and we recently introduced some regressions in
this area as we've attempted to fix these issues. We've reworked the implementation to simplify it internally and added many more integration tests,
to avoid further regressions in the future.

* [GRADLE-2502](http://issues.gradle.org/browse/GRADLE-2502) - Latest status resolution broken in Gradle 1.1.
* [GRADLE-2504](http://issues.gradle.org/browse/GRADLE-2504) - NPE resolving dynamic versions from multiple repositories.

#### Other fixes

This release includes a number of other useful fixes:

* [GRADLE-2547](http://issues.gradle.org/browse/GRADLE-2547) - Temporary files are left in $GRADLE_HOME/caches/artifacts-*/filestore.
* [GRADLE-2543](http://issues.gradle.org/browse/GRADLE-2543) - Performance regression in local repository access.
* [GRADLE-2486](http://issues.gradle.org/browse/GRADLE-2486) - Dependency resolution failures when running in parallel execution mode.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to the backwards compatibility policy.

### Improved test logging APIs

[Gradle 1.1 introduced much improved test logging output](http://gradle.org/docs/1.1/release-notes#test-logging). The APIs that provided the ability to configure this logging
were introduced in the incubating state. In this Gradle release, these APIs are being promoted and are no longer subject to change without notice.

For more information on the test logging functionality, see [this forum post](http://forums.gradle.org/gradle/topics/whats_new_in_gradle_1_1_test_logging) that introduced the feature.

## Fixed issues

The list of issues fixed between 1.2 and 1.3 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.3-rc-1%22%29+ORDER+BY+priority&tempMax=1000).

## Incubating features

New features as typically introduced as “_incubating_”. The key characteristic of an incubating feature is that it may change in an incompatible way in a future Gradle release.
At some time in the future, after incorporating invaluable real-world feedback from Gradle users, the feature will be deemed stable and “_promoted_”. At this point it is no longer subject to
incompatible change. That is, the feature is now subject to the backwards compatibility policy.

Typically, the implementation quality of incubating features is sound. For some very challenging engineering problems, such as the Gradle Daemon or parallel task execution, it is 
impossible to get the implementation quality right from the beginning as the feature needs real world exposure. The feedback from the incubation phase can be used to iteratively 
improve the stability of the feature.

By releasing features early in the incubating state, users gain a competitive advantage through early access to new functionality in exchange for helping refine it over time, 
ultimately making Gradle a better tool.

To learn more, see the [User Guide chapter on the Feature Lifecycle](userguide/feature_lifecycle.html).

<h3 id="new-publish">New Ivy publishing mechanism</h3>

This release introduces a new mechanism for publishing to Ivy repositories in the Ivy format. It also introduces some new general publishing constructs. This new publishing support is *incubating* and will co-exist with the [existing methods for publication](userguide/artifact_management.html) until the time where it supersedes it, at which point the old mechanism will 
become deprecated. The functionality included in this release is the first step down the path of providing a better solution for sharing the artifacts built in your Gradle builds.

In this release, we have focussed on laying the groundwork and providing the ability to modify the Ivy module descriptor that is published during a publish operation. It has long been possible
to fully customise the `pom.xml` when publishing in the Maven format; it is now possible to do the same when publishing in the Ivy format.

#### The new 'ivy-publish' plugin

The new functionality is provided by the '`ivy-publish`' plugin. In the simplest case, publishing using this plugin looks like…

    apply plugin: "ivy-publish"
    
    // … declare dependencies and other config on how to build
    
    publishing {
        repositories {
            ivy {
                url "http://mycompany.org/repo"
            }
        }
    }

To publish, you simply run the “`publish`” task.

To modify the descriptor, you use a programmatic hook that modifies the descriptor content as XML. This is the same approach that you take in Gradle when modifying IDE metadata XML or Maven POM XML content.

    publishing {
        publications {
            ivy {
                descriptor {
                    withXml {
                        asNode().dependencies.dependency.find { it.@org == "junit" }.@rev = "4.10"
                    }
                }
            }
        }
    }

In this example we are modifying the version that is expressed of our `junit` dependency. With this hook, you can modify any aspect of the descriptor. You could for example easily build a functionality similar to Ivy deliver on top of this in conjunction with the new Resolution Result API. In general it can be useful to optimize the descriptor for consumption instead of having it be an accurate record of how the module was built. 

For more information on the new publishing mechanism, see the [new User Guide chapter](userguide/publishing_ivy.html).

<h3 id="improved-testng-report">Improved TestNG HTML report</h3>

Gradle has long shipped with HTML report functionality for JUnit test results that improves on the Ant default. It is now possible to use the same HTML report format for TestNG test results.
See the [reports generated by the Gradle automated builds](http://builds.gradle.org/repository/download/bt9/.lastSuccessful/reports/ide/integTest/index.html) as an example of the new improved report.

The reports are not yet turned on by default. To enable the new TestNG test reports, simply set 
[testReport = true](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:testReport) on your test task.

Note: The new report might exhibit increased heap usage for tests that log many messages to the standard streams. You can increase the heap allocated to the test process via the 
[jvmArgs property](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:jvmArgs) of the test task.

#### Improvement details

* The html report is easier to read and browse than the standard TestNG report
* Both reports, xml (for CI) and html (for you), contain the test output (i.e. messages logged to the standard streams or via the standard logging toolkits). This is extremely useful for debugging certain test failures.
* The reports neatly work with Gradle parallel testing ([test.maxParallelForks](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:maxParallelForks)) and forking features ([test.forkEvery](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:forkEvery)). The standard TestNG reports are not compatible with Gradle's parallel test execution and can contain confusing or incorrect information.

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

A deprecation warning will be issued if a name change is attempted. It has been deprecated because changing the name of a repository after it has been added to the 
container can cause problems when tasks are automatically created for created repositories.

## Changes to existing incubating features

### Resolution result API

The entry point to the ResolutionResult API has changed. You now access the ResolutionResult via [configuration.incoming.resolutionResult](dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:incoming).
New convenience methods were also added to the API. For more information please refer to the javadoc for [ResolutionResult](javadoc/org/gradle/api/artifacts/result/ResolutionResult.html).

### Incubating C++ `Compile` task type removed

This was replaced by `CppCompile` in Gradle 1.2. You should use the replacement class instead.

### Incubating `GppCompileSpec` properties removed

The deprecated `task` property was removed from `GppCompileSpec`.

## Potential breaking changes

### The behavior of [Test.testReport](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:testReport) property for TestNG

The default value of the [testReport property value of Test tasks](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:testReport) has been changed to `false` for TestNG. Previously, this property was ignored when running with TestNG - the html results were generated regardless.

This property now enables/disables the new [improved TestNG report introduced in this release](#improved-testng-report).

### Removed GraphvizReportRenderer

This was an early contribution that did not work and was an undocumented private type.

### Removed `org.gradle.api.publication` package

This package contained some early experiments in a new publication model. It was incomplete and undocumented. It is superseded by the new [publishing functionality introduced in this release](#new-publish), so has been removed.

## Community contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* Matt Khan - fixed resetting of `test.debug` to `false` when using `test.jvmArgs`  (GRADLE-2485)
* Gerd Aschemann - fixes for `application` plugins generated shell scripts (GRADLE-2501)
* Cruz Fernandez - fixes to the properties sample project
* Fadeev Alexandr - fixes for Gradle Daemon on Win 7 when `PATH` env var is not set (GRADLE-2461)
* Ben Manes - improved Scala IDE integration ([pull request #99](https://github.com/gradle/gradle/pull/99))

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).