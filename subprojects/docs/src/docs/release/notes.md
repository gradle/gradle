
## New and noteworthy

Here are the new features introduced in Gradle 1.2.

### Dependency reports improvements

The dependency report now includes extra information: the requested dependency versions.
This is invaluable information in many situations:

* Conflict resolution. It is now possible to infer from the dependency report why given dependency version is selected.
Previously, the dependency report included the dependency versions *after* the conflict resolution.
The new report makes it much easier to understand what happens during the conflict resolution
and to figure out why and how certain versions are used.
* Dynamic versions. Now the dependency report shows what version was requested and what version was selected.
For example: 1.3.+ -> 1.3.5 (requested -> selected).

We continue to work in the dependency reports area and plan more interesting features shortly.
For example, we want the report to present the dependency tree even though some dependency is unresolved.
We want to add other kinds of dependency reports, too. Improved dependency report should be also faster for projects that have very large dependency graphs.
Our performance tests for this particular kind of project show ~50% speed increase.

The improvements in the dependency report allows us to design the resolution result API.
For the Gradle user it means better programmatic access and hooks to the resolved dependency graph.

### Experimental resolution result API

We want to expose the API that our dependency reports are using.
This way Gradle users may develop their own dependency reporting driven by custom requirements.
Also it allows the users to develop build logic that can make decisions based on the content of the dependency graph.

The best way to start with the new API is to take a look at the javadocs
for 'ResolvedConfiguration.getResolutionResult()' (TODO link).

### Experimental support for building projects in parallel

Over the coming releases, we'll be adding support for parallel execution of independent projects in a multi-project build. By building separate projects in parallel, Gradle
will enable better hardware utilisation and faster build times.

Gradle 1.2 introduces the first experimental support for this feature, via the `--parallel-executor` and `--parallel-executor-threads` [command-line options](http://gradle.org/docs/nightly/userguide/gradle_command_line.html).
By using these options Gradle will attempt to _execute_ multiple projects in parallel build threads, after first configuring all projects sequentially.

Note that to guarantee successful parallel execution of projects, your multi-project build must contain only [decoupled projects](http://gradle.org/docs/nightly/userguide/multi_project_builds.html#sec:decoupled_projects).
While configuration-time decoupling is not strictly required for parallel project execution, we do not intend on supporting a separate model of decoupling that permits configuration-time
coupling with execution-time decoupling. At this time there are no checks implemented to ensure that projects are decoupled, and unexpected behaviour may result from executing a build with coupled
projects using the new parallel executor.

**This feature is pre-alpha and highly experimental. Many multi-project builds will behave unexpectedly when run using parallel project execution.**

### Documentation facelift

Our documentation has received a facelift to match our new style. Check out the new look [DSL Reference](dsl/index.html) and [User Guide](userguide/userguide.html).

The [DSL Reference](dsl/index.html) now indicates which features are deprecated or experimental.

### HTTP requests now provide Gradle related version information

Gradle, the Gradle Wrapper and the Gradle Tooling API now provide version information in the `User-Agent` header when HTTP resources are accessed.
Especially for larger organisations, this can be very helpful to gather information about which versions of Gradle are used in which environment.

#### What information is provided?

The `User-Agent` header now includes information about

* The used Gradle application (Gradle, Gradle Wrapper or Gradle Tooling API) + Version
* The Operating System (name, version, architecture)
* The Java version (vendor, version)

An example for a Gradle generated user-agent string: "**Gradle/1.2 (Mac OS X;10.8;amd64) (Oracle Corporation;1.7.0_04-ea;23.0-b12)**"

### Less usage of heap space

We've continued to improve our dependency resolution engine, so that it now requires much less heap space. A moderately sized multi-project build can
expect to see a 20-25% reduction in heap usage thanks to these improvements.

### Experimental bootstrap plugin

We would like to make it as easy as possible to migrate from a different build tool to Gradle.
This release includes an experimental [Bootstrap plugin](http://gradle.org/docs/nightly/userguide/bootstrap_plugin.html).
However quality-wise it is not yet at the level we expect a Gradle feature to be.
We will continue working on it and with Gradle 1.3 it will be officially announced.

### Configuration option for FindBugs plugin

Thanks to a contribution from Justin Ryan (https://github.com/quidryan), the FindBugs plugin now supports essential configuration options.

## Upgrading from Gradle 1.1

Please let us know if you encounter any issues during the upgrade to Gradle 1.2, that are not listed below.

### Deprecations

#### The `useMavenMetadata` property for Maven repositories

The `useMavenMetadata` property has been deprecated for resolvers returned by `repositories.mavenRepo()`. This property controls whether Gradle should
search for a `maven-metadata.xml` file when attempting to determine the versions that are available for a particular module. The default value is `true`,
which means Gradle will look for a `maven-metadata.xml` file and then fall back to a directory listing if not present. When set to `false` Gradle will
use a directory listing only.

Thanks to the various improvements we've made to make dependency management must more efficient, there is no longer a performance penalty for searching
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

## Fixed Issues

The list of issues fixed between 1.1 and 1.2 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.2-rc-1%22%29+ORDER+BY+priority&tempMax=1000).
