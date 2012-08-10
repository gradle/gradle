
## New and noteworthy

Here are the new features introduced in Gradle 1.2.

### Documentation facelift

Our documentation has received a facelift to match our new style. Check out the new look [DSL Reference](dsl/index.html) and [User Guide](userguide/userguide.html).

The [DSL Reference](dsl/index.html) now indicates which features are deprecated or experimental.

### HTTP requests now provide Gradle related version information

Gradle, the Gradle Wrapper and the Gradle Tooling API now provide version information in the `User-Agent` header when HTTP resources are accessed.
Especially for larger corporations, this can be very helpful to gather information about which versions of Gradle are used in which environment. The `User-Agent` header now includes information about

* the used Gradle application (Gradle, Gradle Wrapper or Gradle Tooling API) + Version
* the Operating System (name, version, architecture) 
* the Java version (vendor, version)

An example for a Gradle generated user-agent string: "**Gradle/1.2 (Mac OS X;10.8;amd64) (Oracle Corporation;1.7.0_04-ea;23.0-b12)**"

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

## Upgrading from Gradle 1.1

Please let us know if you encounter any issues during the upgrade to Gradle 1.2, that are not listed below.

### Deprecations

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
