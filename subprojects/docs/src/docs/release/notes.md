## New and noteworthy

Here are the new features introduced in Gradle 1.2.

### Dependency reports improvements

The dependency report now includes extra information: requested dependency versions. This is invaluable information in many situations:

* For conflict resolution it is now possible to infer from the dependency report why a given dependency version is selected. Previously, the dependency report included the dependency versions *after* conflict resolution has been applied. Now you can see the version before the conflict resolution and the version it was replaced with. The new report makes it much easier to understand what happens during conflict resolution and to figure out why certain versions are used.
* For dynamic versions the dependency report shows what version was requested and what version was selected. For example: `1.3.+ -> 1.3.5` (requested -> selected).

#### Improved readability

In order to keep the report clean and tidy we decided to remove the 'resolved target configurations' from the report.
They used to appear on the right hand of every listed dependency and it was often a source of confusion.
Example report that includes versions selected by conflict resolution and some dependency with a dynamic version:

<pre><tt>+--- org.gradle:some-lib:1.0
|    \--- org.gradle:some-util:1.5 -> 2.0
+--- org.gradle:some-other:1.0
|    \--- org.gradle:some-util:1.0 -> 2.0
+--- org.gradle:some-util:2.0
\--- org.something:dynamic-version:1.+ -> 1.8
</tt>
</pre>

We will continue to work in the dependency reports area and plan more interesting features shortly. For example, we want the report to present the dependency tree even though some dependency is unresolved. We also want to add other kinds of dependency reports.
We currently work on a report that focuses on a single dependency and shows all paths where it is referenced.
We would also like to include the reason a version was selected (is it conflict resolution or perhaps the version was forced?).
The report for 'org.gradle:some-util' dependency might look like:

<pre><tt>org.gradle:some-util:2.0
\--- *

org.gradle:some-util:1.5 -> 2.0
\--- org.gradle:some-lib:1.0
     \--- *

org.gradle:some-util:1.0 -> 2.0
\--- org.gradle:some-other:1.0
     \--- *
</tt>
</pre>

The new dependency report should also be faster for projects that have very large dependency graphs. Our performance tests for a particular larger project we are working on showed a 50% speed increase. The improvements in the dependency report drove our design of the new resolution result API. For Gradle users it means better programmatic access and hooks to the resolved dependency graph.

### Lower memory usage

We've continued to improve our dependency resolution engine, so that it now requires much less heap space. A moderately sized multi-project build can
expect to see a 20-25% reduction in heap usage thanks to these improvements.

### Continue on failure (--continue) no longer experimental

Often a high-level task is dependent on multiple smaller tasks. By default, Gradle will stop executing any task and fail the build
as soon as one of these sub-tasks fails. This means that the build will finish sooner, but it does not reveal failures in other, independent sub-tasks.
There are many times when you would like to find out as many failures as possible in a single build execution. For example, when you kick off a build
before heading out to lunch, or running a nightly CI job. The `--continue` command-line option allows you to do just that.

With the addition of [nicer reporting of multiple build failures](#multile_build_failures), we now consider `--continue` a fully-fledged capability of Gradle, and have removed the `experimental` flag from this option. Please see the [User Guide section](userguide/tutorial_gradle_command_line.html#sec:continue_build_on_failure) for more details.

### <a id="multile_build_failures"></a>Reporting of multiple build failures

When running the build with `--continue` or the new `--parallel` option (see [below](#parallel)) it is possible to have multiple failures in your build. While executing Gradle will now report on tasks that fail,
as well as producing output detailing _all_ build failures on build completion.

The new output clearly indicates the cause and possible analysis of each failure, making it easier to track down the underlying issue.
Detailing _all_ failures makes the `--continue` command line option much more useful: it is now possible to use this option to
discover as many failures as possible with a single build execution.

### Configuration option for FindBugs plugin

Thanks to a contribution from [Justin Ryan](https://github.com/quidryan), the FindBugs plugin now supports more configuration options.

### HTTP requests now provide Gradle related version information

Gradle, the Gradle Wrapper and the Gradle Tooling API now provide version information in the `User-Agent` header whenever HTTP resources are accessed.
Especially for larger organisations, this can be very helpful to gather information about which versions of Gradle are used in which environment.

#### What information is provided?

The `User-Agent` header now includes information about

* The Gradle application (Gradle, Gradle Wrapper or Gradle Tooling API) + Version making the request.
* The Operating System (name, version, architecture).
* The Java version (vendor, version).

An example for a Gradle generated user-agent string: "**Gradle/1.2 (Mac OS X;10.8;amd64) (Oracle Corporation;1.7.0_04-ea;23.0-b12)**"

### Documentation style improvements

Our documentation has received a facelift to match our new style. Check out the new look [DSL Reference](dsl/index.html) and [User Guide](userguide/userguide.html).

The [DSL Reference](dsl/index.html) now indicates which features are deprecated or experimental.

## Fixed Issues

The list of issues fixed between 1.1 and 1.2 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.2-rc-1%22%29+ORDER+BY+priority&tempMax=1000).

## New experimental and unstable features

We will typically introduce big new features as experimental or unstable at first, giving you a chance to test them out. Experimental means that the quality of the behaviour might not match the quality you are used to with Gradle. Unstable means that the quality is good but the API might still change with the next release. We will iterate on the new feature based on your feedback, eventually releasing it as stable and production-ready. Those of you who use new features before that point gain the competitive advantage of early access to new functionality in exchange for helping refine it over time. To learn more read our [forum posting on our release approach](http://forums.gradle.org/gradle/topics/the_gradle_release_approach).

###<a id="parallel"></a> Support for building projects in parallel (highly experimental with known open issues)

Over the coming releases, we'll be adding production-quality support for parallel execution of independent projects in a multi-project build. By building separate projects in parallel, Gradle will enable better hardware utilisation and faster build times.

We are excited that Gradle 1.2 introduces the first experimental support for this feature, via the `--parallel` and `--parallel-threads` [command-line options](userguide/gradle_command_line.html). By using these options Gradle will attempt to _execute_ multiple projects in parallel build threads, after first configuring all projects sequentially. We are seeing significant performance benefits with this approach, in particular when the build is not already CPU bound.

Note that to guarantee successful parallel execution of projects, your multi-project build must contain only [decoupled projects](userguide/multi_project_builds.html#sec:decoupled_projects).
While configuration-time decoupling is not strictly required for parallel project execution, we do not intend on supporting a separate model of decoupling that permits configuration-time coupling with execution-time decoupling. At this time there are no checks implemented to ensure that projects are decoupled, and unexpected behaviour may result from executing a build with coupled projects using the new parallel executor.

To find out more about our plans for parallel execution, have a read of the [parallel-project-execution](https://github.com/gradle/gradle/blob/master/design-docs/parallel-project-execution.md) specification.

**This feature is pre-alpha and highly experimental. Many multi-project builds will behave unexpectedly when run using parallel project execution. You will get a warning when you are using it.***

One known issue is that the Gradle compiler daemon is currently not thread-safe. So if multiple projects attempt to compile java code simultaneously with `fork=true`,
exceptions will result. Workaround: don't use `options.fork=true` to compile when running with `--parallel`.

### New dependency resolution result API (unstable)

We are exposing the new API that our improved dependency reports are using. It provides a powerful for developing your own custom dependency reports. It also allows to develop smart build logic that can make decisions based on the content of the dependency graph.

The best way to start with the new API is to take a look at the Javadocs
for <a href="javadoc/org/gradle/api/artifacts/ResolvedConfiguration.html#getResolutionResult()">`ResolvedConfiguration.getResolutionResult()`</a>.

**The new resolution API is not stable yet and may change with the next releases. Therefore you will get a warning when you are using it.**

### Build Comparison (Gradle upgrade assistance)

Gradle 1.2 delivers the first iteration of our support for comparing the _outcomes_ (e.g. the produced binary archives) of two builds. There are several reasons why you may want to compare the outcomes of two builds. You may want to compare: 

* A build with a newer version of Gradle than it's currently using.
* A Gradle build with a build executed by another tool (e.g. Apache Ant, Apache Maven).
* The same Gradle build, with the same version, before and after a change to the build.

The build comparison support manages the execution of the “source” build and the “target” build, the association of outcomes between the two, the comparison of the outcomes and generation of a report that identifies any encountered differences. You can then use this report to go ahead with the Gradle upgrade, build system migration or build configuration change with confidence that the outcomes are identical, or that the differences are acceptable.

For Gradle 1.2, we have focussed on supporting the case of comparing the current build with a newer Gradle version and zip file binary archive outcomes (e.g. zip, jar, war, ear etc.). This feature will continue to evolve to encompass comparing more kinds of outcomes and smart integration with other build systems (such as Apache Maven) for convenient comparisons. 

See the new [User Guide chapter](userguide/comparing_builds.html) for more detail on this new capability.

#### How can I use it to try new Gradle versions?

You simply add a plugin and configure the comparison task. 

    apply plugin: 'compare-gradle-builds'
    
    compareGradleBuilds {
      targetBuild.gradleVersion "1.3"
    }

(note: Gradle 1.3 is unreleased at this time, so the above will not work. You can use 1.2 as the value in the meantime to play with this feature though)

Then simply…

<pre><tt>./gradlew compareGradleBuilds</tt></pre>

If there are _any_ differences found, a link to the HTML report identifying the differences will be given in the output.

### JSR-330 dependency injection for plugins and tasks (unstable)

We've taken some steps towards allowing JSR-330 style dependency injection for plugins and tasks. At this stage, the changes are mostly internal. To find out why we want to use dependency injection, and our plans for this, have a read of the [dependency-injection-for-plugins](https://github.com/gradle/gradle/blob/master/design-docs/dependency-injection-for-plugins.md) specification.

At this stage, only internal Gradle services are available for injection. Over time we will add public services that can be injected into plugin and task implementations.

## Upgrading from Gradle 1.1

Please let us know if you encounter any issues during the upgrade to Gradle 1.2, that are not listed below.

### Deprecations

If you make use of the deprecated features below you will get a warning from now on. But you can rest assured that those features will be supported at least until the release of Gradle 2.0, our next major release. To learn more read our [forum posting on our release and backwards compatibility approach](http://forums.gradle.org/gradle/topics/the_gradle_release_approach).

#### The `useMavenMetadata` property for Maven repositories

The `useMavenMetadata` property has been deprecated for resolvers returned by `repositories.mavenRepo()`. This property controls whether Gradle should
search for a `maven-metadata.xml` file when attempting to determine the versions that are available for a particular module. The default value is `true`,
which means Gradle will look for a `maven-metadata.xml` file and then fall back to a directory listing if not present. When set to `false` Gradle will
use a directory listing only. It is part of our former internal usage of Ivy for dependency resolution.

Thanks to the various improvements we've made to make dependency management more efficient, there is no longer a performance penalty for searching
for the `maven-metadata.xml` file. This means this property is no longer useful and will be removed in Gradle 2.0.

#### Task class renames

To avoid ambiguity, the Java and C++ `Compile` task classes have been renamed. The Java `org.gradle.api.tasks.compile.Compile` task class has been renamed to `org.gradle.api.tasks.compile.JavaCompile`, and
the experimental C++ `org.gradle.plugins.binaries.tasks.Compile` task class has been renamed to `org.gradle.plugins.cpp.CppCompile`.

For backwards compatibility, the old classes are still available, but are now deprecated. The old Java `Compile` class will be removed in Gradle 2.0.
The old experimental C++ `Compile` class will be removed in Gradle 1.3.

<a name="constructors"> </a>
#### Changes to plugin and task constructor handling

As a first step towards handling JSR-330 style dependency injection for plugin and task instances, we have made some changes to how constructors for these types
are handled by Gradle. These changes are fully backwards compatible, but some combinations of constructors are now deprecated.

If your plugin or task implementation class has exactly one default constructor, nothing has changed. This should be the case for the majority of implementations.

If your implementation class has multiple constructors, you will need to add an `@javax.inject.Inject` annotation to the default constructor. The implementation will continue to work
without this, but you will receive a deprecation warning. In Gradle 2.0, a plugin or task implementation with multiple constructors will be required to annotate exactly one
constructor with an `@Inject` annotation.

### Potential breaking changes

See [constructor handling](#constructors) above. The changes should be backwards compatible. Please let us know if you come across a situation where
a plugin or task implementation that worked with previous versions of Gradle does not work with Gradle 1.2.
