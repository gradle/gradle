## New and noteworthy

Here are the new features introduced in this Gradle release.

### Daemon health monitoring

The daemon actively monitors its health and may expire earlier if its performance degrades.
The current implementation monitors the overhead of garbage collector and may detect memory issues.
Memory problems can be caused by 3rd party plugins written without performance review.
We want the Gradle daemon to be rock solid and enabled by default in the future.
This feature is a big step forward towards the goal.
Down the road the health monitoring will get richer and smarter, providing the users the insight into daemon's performance
and deciding whether to restart the daemon process.

Incubating system property "org.gradle.daemon.performance.logging" can be used to switch on an elegant message emitted at the beginning of each build.
The new information presented in the build log helps getting better understanding of daemon's performance:

    Starting 3rd build in daemon [uptime: 15 mins, performance: 92%, memory: 65% of 1.1 GB]

The logging can be turned on by tweaking "org.gradle.jvmargs" property of the gradle.properties file:

    org.gradle.jvmargs=-Dorg.gradle.daemon.performance.logging=true

### Support for AWS S3 backed repositories

Gradle now supports S3 backed repositories. Here's an example on how to declare a S3 backed maven repository in gradle:

    repositories {
        maven {
            url "s3://someS3Bucket/maven2"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
        }

        ivy {
            url "s3://someS3Bucket/ivy"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
        }
    }

S3 backed repositories can be used with both the `ivy-publish` and `maven-publish` plugins, as well as an Ivy repository associated with an `Upload` task.

A big thank you goes to Adrian Kelly for implementing this feature.

### Improved performance with class loader caching

We want each new version of Gradle to perform better.
Gradle is faster and less memory hungry when class loaders are reused between builds.
The daemon process can cache the class loader instances, and consequently, the loaded classes.
This unlocks modern jvm optimizations that lead to faster execution of consecutive builds.
This also means that if the class loader is reused, static state is preserved from the previous build.
Class loaders are not reused when build script classpath changes (for example, when the build script file is changed).

In the reference project, we observed 10% build speed improvement for the initial build invocations in given daemon process.
Later build invocations perform even better in comparison to Gradle daemon without classloader caching.

### Google Test support (i)

- TBD

### Model rules

A number of improvements have been made to the model rules execution used by the native language plugins:

- Added a basic `model` report to allow you to see the structure of the model for a particular project.
- `@Defaults` annotation allow logic to be applied to attach defaults to a model element.
- `@Validate` annotation allow logic to be applied to validate a model element after it has been configured.
- `CollectionBuilder` allows rules to be applied to all elements in the collection, or to a particular element, or all elements of a given type.

### Tooling API improvements

There is a new API `GradleProject#getProjectDirectory` that returns the project directory of the project.

### Dependency substitution accepts projects

You can now replace an external dependency with a project dependency. The `DependencyResolveDetails` object
allows access to the `ComponentSelector` as well:

    resolutionStrategy {
        eachDependency { details ->
            if (details.selector instanceof ModuleComponentSelector && details.selector.group == 'com.example' && details.selector.module == 'my-module') {
                useTarget project(":my-module")
            }
        }
    }

### Parallel Native Compilation

Gradle uses multiple concurrent compilation processes when compiling all supported native languages. You can enable this 
with the incubating `--parallel` and `--parallel-threads=#` command-line options. Up until this release, Gradle compiled 
all native source files sequentially. 

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

### Dependency substitution changes

In previous Gradle versions you could replace an external dependency with another like this:

    resolutionStrategy {
        eachDependency { details ->
            if (details.requested.group == 'com.example' && details.requested.module == 'my-module') {
                useVersion '1.3'
            }
        }
    }

Now the `requested` property on `DependencyResolveDetails` is deprecated; you can use the `selector` property instead. This returns a `ComponentSelector` instead
a `ModuleVersionSelector`.

The `useVersion()` method is also deprecated. You can use the `useTarget()` method instead:

    resolutionStrategy {
        eachDependency { details ->
            if (details.selector instanceof ModuleComponentSelector && details.selector.group == 'com.example' && details.selector.module == 'my-module') {
                useTarget group: 'com.example', name: 'my-module', version: '1.3'
            }
        }
    }

Note that `ModuleComponentSelector` has a `module` property to return the module's name, while `ModuleVersionSelector` had a `name` property.

### Generate wrapper with specific version from command-line

Previously to generate a Gradle wrapper with a specific version, or a custom distribution URL,
you had to change the `build.gradle` file to contain a wrapper task with a configured `gradleVersion` property.

Now the target gradle version or the distribution URL can be configured from the command-line, without having
to add or modfify the task in `build.gradle`:

    gradle wrapper --gradle-version 2.3

And with a distribution URL:

    gradle wrapper --gradle-distribution-url https://myEnterpriseRepository:7070/gradle/distributions/gradle-2.3-bin.zip

## Potential breaking changes

### Model DSL changes

There have been some changes to the behaviour of the `model { ... }` block:

- The `tasks` container now delegates to a `CollectionBuilder<Task>` instead of a `TaskContainer`.
- The `components` container now delegates to a `CollectionBuilder<ComponentSpec>` instead of a `ComponentSpecContainer`.
- The `binaries` container now delegates to a `CollectionBuilder<BinarySpec>` instead of a `BinaryContainer`.

Generally, the DSL should be the same, except:

- Elements are not implicitly created. In particular, to define a task with default type, you need to use `model { tasks { myTask(Task) { ... } }`
- Elements are not created or configured eagerly, but are configured as required.
- The `create` method returns void.
- The `withType()` method selects elements based on the public contract type rather than implementation type.
- Using create syntax fails when the element already exists.
- There are currently no query method on this interface.

### Updated default zinc compiler version

The default zinc compiler version has changed from 0.3.0 to 0.3.5.3

### MavenDeployer no longer uses global Maven settings.xml

- User settings file was never used, but global settings.xml was considered
- Mirror settings no longer cause GRADLE-2681
- Authentication and Proxy settings are not used

- Local repository location in user settings.xml _is_ honoured when deploying (it was always honoured when installing)

### PublishToMavenLocal task ignores repository setting

Previously, the `PublishToMavenLocal` task could be configured with an `ArtifactRepository` instance, which would specify the
location to `install` to. The default repository was `mavenLocal()`.

It is no longer possible to override this location by supplying a repository to the `PublishToMavenLocal` task. Any supplied repository
will be ignored.

### DependencyResolveDetails.getTarget() is gone

There still is a `getTarget()` method on `DefaultDependencyResolveDetails`, but it returns a `ComponentSelector` instead of a `ModuleVersionSelector`.

### CommandLineToolConfiguration.withArguments() semantics have changed

`withArguments()` used to be called just before Gradle built the command-line arguments for the underlying tool for each source file. 
The arguments passed to this would include the path to the source file and output file. This hook was intended to capture "overall" 
arguments to the command-line tool instead of "per-file" arguments. We've changed it so that `withArguments()` is called once per 
task execution and does not contain any specific file arguments.  Changes to arguments using this method will affect all source files.

### On the fly compilation of Groovy classes located in external scripts when compiling build scripts has been disabled

TBD

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Adrian Kelly](https://github.com/adrianbk)
    - Support for resolving from AWS S3 backed Maven and Ivy repositories
    - Support for publishing to AWS S3 backed Maven and Ivy repositories
    - Don't run assemble task in pull-request validation builds on [travis-ci](https://travis-ci.org/gradle/gradle/builds)
* [Daniel Lacasse](https://github.com/Shad0w1nk) - support GoogleTest for testing C++ binaries
* [Victor Bronstein](https://github.com/victorbr) 
    - Convert NotationParser implementations to NotationConverter
    - Only parse Maven settings once per project to determine local maven repository location (GRADLE-3219) 
* [Vyacheslav Blinov](https://github.com/dant3) - fix for `test.testLogging.showStandardStreams = false` (GRADLE-3218)
* [Michal Bendowski](https://github.com/bendowski-google) - six webDist userguide example
* [Daniel Siwiec](https://github.com/danielsiwiec) - update `README.md`
* [Andreas Schmid](https://github.com/aaschmid) - add test coverage for facet type configuration in `GenerateEclipseWtpFacet`
* [Roman Donchenko](https://github.com/SpecLad) - fix a bug in `org.gradle.api.specs.OrSpecTest`
* [Lorant Pinter](https://github.com/lptr), [Daniel Vigovszky](https://github.com/vigoo) and [Mark Vujevits](https://github.com/vujevits) - implement dependency substitution for projects
* [Lorant Pinter](https://github.com/lptr) - add setting wrapper version on command-line

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
