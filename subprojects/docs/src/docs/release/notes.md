## New and noteworthy

Here are the new features introduced in this Gradle release.

### Improved performance and memory consumption

Gradle 1.8 uses less heap than previous versions. The less heap Gradle uses, the less expensive garbage collection is, and the faster your builds are.
Some builds, especially those really big ones, should see some significant improvements in performance.

#### Serialization of the resolution results.

The heart of this performance improvement is to avoid creating the full dependency resolution results. These
results consume a lot of memory in large builds.
Instead, the information is streamed to disk during the resolution process and the results assembled in heap only when requested.
The information Gradle stores on the disk is usually not needed by typical builds.
Therefore, typical builds get faster and all builds will use less heap.

Note that the results API has not changed and is fully backwards compatible with previous Gradle versions.

### Component metadata rules

Dependency modules (also called _components_) have metadata associated with them, such as their group, name, version, and dependencies.
Typically, this metadata is specified in a module descriptor (Ivy file or Maven POM). Component metadata rules allow you to manipulate this metadata
from within the build script or a plugin. They are evaluated during dependency resolution, before a particular module version has been selected for a dependency.
This makes metadata rules another instrument for customizing dependency resolution.

As of Gradle 1.8, only two pieces of module metadata can be manipulated: A module's *status scheme*, and its *status*. The former describes the
increasing levels of maturity that the module transitions through over time. The latter describes the module's current maturity,
and needs to correspond to one of the values listed in the module's status scheme.

A module's status scheme defaults to `integration`, `milestone`, `release` (in that order). Its status defaults to `integration` for Ivy modules
(if not specified in the Ivy file) and Maven snapshot modules, and to `release` for Maven modules other than snapshots.

What can a status (scheme) be used for? Most notably, a dependency can request the highest module version with at least the stated status:

    dependencies {
        // the highest version with status milestone or release
        compile "org.foo:bar:latest.milestone
    }

Ivy users will be familiar with this feature. 'Latest' version resolution also works together with custom status schemes:

    dependencies {
        // the highest version with status silver, gold, or platinum
        compile "org.foo:bar:latest.silver"
        componentMetadata {
            eachComponent { ComponentMetadataDetails details ->
                if (details.id.group == "org.foo") {
                    // declare a custom status scheme
                    details.statusScheme = ["bronze", "silver", "gold", "platinum"]
                }
            }
        }
    }

For API details, see [`ComponentMetadataHandler`](javadoc/org/gradle/api/artifacts/dsl/ComponentMetadataHandler.html).
Future Gradle versions will likely allow more pieces of module metadata to be manipulated.

### Create native libraries and executables from C and Assembler sources (i)

With Gradle 1.8, it is now possible to include 'C' and 'Assembler' source files to create a native library or executable. The C sources are compiled
with relevant compiler settings, and Assembler sources are translated directly to object files by the assembler.

Including C and Assembler sources in your project is straightforward. Whereas C++ sources are contained in a 'cpp' source directory,
C source files should be located in a 'c' directory and Assembler source files in a 'asm' directory. These directory locations are conventional, and
can be updated in your build script.

Here's an example of how you can customize which source files and directories to include:

    sources {
        main {
            c {
                source {
                    srcDirs "sourcefiles"
                    include "**/*.c"
                }
                exportedHeaders {
                    srcDirs "includes"
                }
            }
            asm {
                source {
                    srcDirs "sourcefiles", "assemblerfiles"
                    include "**/*.s"
                }
            }
        }
    }

Note that support for building native binaries is under active development, and this functionality is very likely to be changed and improved in upcoming
releases.

### New duplicate file handling strategies

Gradle 1.7 introduced some strategies for dealing with duplicates when copying files and building archives such as ZIPs and JARs. The Gradle 1.8 release adds two
more:

* `warn`, which includes the duplicates in the result but logs a warning.
* `fail`, which fails when attempting to include duplicate files in the result.

Thanks to [Kyle Mahan](https://github.com/kylewm) for this contribution.

### Tooling API

This release includes a number of improvements to the tooling API. The tooling API is used by tools such as IDEs, CI servers and other
applications to execute and query Gradle builds. You can also use the tooling API to embed Gradle in your own applications.

* Information about the build script for a project is now available via the `GradleProject` tooling model.
* A new `GradleBuild` model provides basic information about a Gradle build without requiring the entire build to be configured,
  making it a more efficient alternative to the `GradleProject` model.
* Batch operations can be performed in a single request using the new `BuildAction` interface. This means much faster tooling integrations.

### Early preparations for Gradle 2.0

This release sees the start of initial preparations for a Gradle 2.0 release early next year. At this stage, this means some cleanup of API and
deprecating some old features, for removal in Gradle 2.0. You'll find more details below.

Removing unwanted features allows the implementation of Gradle to be simplified, and this means fewer bugs, more features and faster builds for you.

Please be aware that we'll be following our usual feature lifecycle for removing features. Almost every deprecated feature has a non-deprecated replacement
and this is documented in the deprecation descriptions below. However, some deprecated features do not have a replacement. If you find a feature that
you use has been deprecated, and there doesn't seem to be a replacement for it that you can use , please let us know as soon as possible via the
[forums](http://forums.gradle.org).

<!--
### Example new and noteworthy
-->

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
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

### Support for using Ivy `DependencyResolver` implementations

For several years (since Gradle 1.0-milestone-3), there have been two ways that you can define an Ivy repository for Gradle to use. The first, and preferred, way is to
use Gradle's native `repositories.ivy { }` DSL. The second way was to register an Ivy `DependencyResolver` implementation using the `repositories.add()` method.

Support for using Ivy `DependencyResolver` instances will be discontinued in Gradle 2.0. Please note that Gradle will continue to
support Ivy repositories through its native DSL.

The following methods have been deprecated and will be removed in Gradle 2.0:

* The `RepositoryHandler.mavenRepo()` method has been deprecated. You should use the `maven()` method instead:
* The `ArtifactRepositoryContainer.add(DependencyResolver)` method. You should use one of the other repository methods instead.
* The `ArtifactRepositoryContainer.addFirst(Object)` method. You should use the `addFirst(ArtifactRepository)` instead.
* The `ArtifactRepositoryContainer.addBefore()` and `addAfter()` methods. There is no replacement for these methods.

<table>
    <tr><th>Gradle 1.7</th><th>Gradle 1.8</th></tr>
    <tr>
    <td>
        <pre>repositories {
    mavenRepo url: 'http://my.server/'
    add(new org.apache.ivy.plugins.resolver.FileSystemResolver()) {
        addArtifactPattern('/some/dir/[organisation]/[module]-[revision].[ext]')
}</pre>
    </td>
    <td>
        <pre>repositories {
    maven {
        url 'http://my.server/'
    }
    ivy {
        artifactPattern '/some/dir/[organisation]/[module]-[revision].[ext]'
    }
}</pre>
    </td>
    </tr>
</table>

### Using `GradleLauncher` to run Gradle builds

There are currently 3 ways you can programmatically run a build. The first, and recommended, way is to use the Gradle tooling API. You can also use
the `GradleBuild` task type. The third way is to use the `GradleLauncher` API. The `GradleLauncher` API is now deprecated and will be removed in Gradle
2.0.

* The `GradleLauncher` class has been deprecated and will be removed in Gradle 2.0.
* The `GradleLauncher.newInstance()` and `createStartParameter()` methods have been deprecated and will be removed in Gradle 2.0.

### Unused constants on `ArtifactRepositoryContainer`

A number of constants on `ArtifactRepositoryContainer` are no longer used. These have been deprecated and will be removed in Gradle 2.0.

### Unused classes

The following classes will be removed in Gradle 2.0. They are no longer used:

* `IllegalOperationAtExecutionTimeException`
* `AntJavadoc`

### Open API classes

The Open API has been deprecated for over two years (since Gradle 1.0-milestone-4) and is due to be removed in Gradle 2.0. The entry points to the
Open API were marked deprecated at that time.

To make the deprecation of the Open API clearer, all of the Open API classes are now marked as deprecated, in addition to the entry points. All of the
Open API classes will be removed in Gradle 2.0.

<!--
### Example deprecation
-->

## Potential breaking changes

### Upgraded to Ant 1.9.2

The version of Ant used by Gradle has been upgraded to Ant 1.9.2. This should be backwards compatible.

### Changes in task arguments evaluation

All arguments passed for task creation are evaluated to be valid. In earlier Gradle versions, a typo in the arguments was silently ignored.
The following snippet will now fail with an error message, giving a hint that 'Type' is not a valid argument.

<pre>
    task myCopy(Type: copy) {
        from "..."
        into "..."
    }
}</pre>


### Changes to incubating C++ support

* Renamed task class `AssembleStaticLibrary` to `CreateStaticLibrary`, with the default task instance also being renamed from `assemble${StaticLibraryName}` to
  `create${StaticLibraryName}`
* Renamed plugin class `BinariesPlugin` to `NativeBinariesPlugin`.
* Without any defined tool chains, only a single default tool chain is added to the `toolChains` list. When relying on a default tool chain,
  configuration should be applied based on the tool chain type instead of comparing with a particular tool chain:

    binaries.all {
        if (toolChain in VisualCpp) {
            // Visual C++ configuration
        }
        if (toolChain in Gcc) {
            // GCC configuration
        }
    }

The DSL for defining C++ source sets has changed, with the `cpp` extension being removed. This change makes the C++ plugin DSL consistent with
the new Gradle DSL for multiple source sets.

<table>
    <tr><th>Gradle 1.7</th><th>Gradle 1.8</th></tr>
    <tr>
    <td>`cpp.sourceSets.main`</td>
    <td>`sources.main.cpp`</td>
    </tr>
    <tr>
    <td><pre>cpp {
    sourceSets {
        main {
            source {
                srcDirs "..."
                exportedHeaders "..."
            }
        }
    }
}</pre>
    </td>
    <td>
<pre>sources {
    main {
        cpp {
            source {
                srcDirs "..."
                exportedHeaders "..."
            }
        }
    }
}</pre>
    </td>
    </tr>
</table>

### The order of resolved files and artifacts has changed

The order of resolved artifacts and resolved files has changed. This change is transparent to the vast majority of builds.

This change was necessary to implement important performance improvements mentioned above, and may impact the order of files of a
resolved configuration, consequently, it may impact some classpaths.

### Changes to handling of Ivy `DependencyResolver` implementations

In order to improve performance and heap usage during dependency resolution, this release includes some internal changes to the way meta-data is
parsed. If you use an Ivy `DependencyResolver` implementation to define repositories, meta-data parsing is now delegated to Ivy instead of
using Gradle's parser implementations. This means that these resolvers will no longer take advantage of performance improvements in Gradle's
meta-data parsing and handling. However, the changes should generally be backwards compatible.

Note that using Ivy `DependencyResolver` implementations is deprecated, and we recommend that you use Gradle's repository implementations instead.

### Ivy `DependencyResolver` implementations returned by Gradle APIs no longer support `latestStrategy` methods

Ivy `DependencyResolver` implementations returned by Gradle APIs such as `repositories.mavenRepo` no longer support the following methods:
`getLatestStrategy()`, `setLatestStrategy()`, `getLatest()`, `setLatest()`. Calling one of these methods will now throw an `UnsupportedOperationException`.

Note that using Ivy `DependencyResolver` implementations is deprecated, and we recommend that you use Gradle's repository implementations instead.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

## Contributors

On behalf of the Gradle community, the Gradle development team would like to thank the following people who contributed to this version of Gradle:

* [Kyle Mahan](https://github.com/kylewm) - some Javadoc fixes & duplicate file handling improvements
* [Stephane Gallés](https://github.com/sgalles) - Documentation improvements
* [Bobby Warner](https://github.com/bobbywarner) - Documentation improvements
* [René Scheibe](https://github.com/darxriggs) - Documentation improvements
* [Harald Schmitt](https://github.com/surfing) - Fixed unit test case that was broken for German locales
* [Jeremy Maness](https://github.com/jmaness) - Support for Ivy 2.3 content in ivy.xml (GRADLE-2743)

Contributions are an important part of the continuous improvement of Gradle. 

If you would like to contribute to Gradle, please see [gradle.org/contribute](http://gradle.org/contribute) for how to start.

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
