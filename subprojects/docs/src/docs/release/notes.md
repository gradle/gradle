## New and noteworthy

Here are the new features introduced in this Gradle release.

### Improved performance and memory consumption

Gradle 1.8 uses less memory. Some builds, especially those really big ones, will get faster.
The less memory Gradle uses, the less expensive garbage collection is, and effectively, the faster your builds are.
The heart of this performance improvement is avoiding creation of some expensive objects.
Instead, the information is streamed to disk and read back only when necessary.
The information Gradle stores on the disk is the extra resolution result information - it is not needed by typical builds.
Therefore, typical builds get faster and all builds will use less memory.

#### Serialization of the resolution results.

The dependency resolution results consume a lot of memory in big projects. Hence, in Gradle 1.8,
the resolution information (like [`ResolutionResult`](javadoc/org/gradle/api/artifacts/result/ResolutionResult.html)
or [`ResolvedConfiguration`](javadoc/org/gradle/api/artifacts/ResolvedConfiguration.html) is streamed to disk during the dependency resolution.
When this information is requested, it is read from the disk.
The api does not change, however, only the implementation details are different.
To avoid slowdowns with more I/O operations, the resolution results are cached.
Typical builds will get faster, some builds may get a bit slower, however the overall user experience will get better.
For example, the 'gradle clean build' may be faster but 'gradle idea' might get a little bit slower.
It is because in order to generate the IDE metadata, the 'idea' plugin needs to access the full dependency graph
and this information is read from the disk.

### Tooling API

Information about the build script for a project is now available via the `GradleProject` tooling API model.

### Early preparations for Gradle 2.0

We've started some initial preparations for a Gradle 2.0 release early next year. At this stage, we're cleaning up the API and marking a
number of features as deprecated, for removal in Gradle 2.0. You'll find more details below.

Removing unwanted features allows us to reduce the complexity of Gradle, and this means fewer bugs, more features and faster builds for you.

Please be aware that we'll be following our usual feature lifecycle for removing features. Almost every deprecated feature has a non-deprecated replacement
and this is documented in the deprecation descriptions below. However, some deprecated features do not have a replacement. If you find a feature that
you use has been deprecated, and there doesn't seem to be a replacement for it that you can use , please let us know as soon as possible via the
[forums](http://forums.gradle.org).

### New duplicate final handling strategies

TODO - placeholder

### Component metadata rules

Dependency modules (also called `components`) have metadata associated with them, such as their group, name, version, and dependencies.
Typically, this metadata is specified in a module descriptor (Ivy file or Maven POM). Component metadata rules allow to manipulate this metadata
from within the build script. They are evaluated during dependency resolution, before a particular module version has been selected for a dependency.
This makes metadata rules another instrument for customizing dependency resolution.

As of Gradle 1.8, two pieces of module metadata can be manipulated: A module's *status scheme*, and its *status*. The former describes the
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

    componentMetadata {
        eachComponent { ComponentMetadataDetails details ->
            if (details.id.group == "org.foo") {
                // declare a custom status scheme
                details.statusScheme = ["bronze", "silver", "gold", "platinum"]
            }
        }
    }

    dependencies {
        // the highest version with status silver, gold, or platinum
        compile "org.foo:bar:latest.silver"
    }

For further API details, see ... Future Gradle versions will likely allow more pieces of module metadata to be manipulated.

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

For several years, there have been two ways that you can define an Ivy repository for Gradle to use. The first, and preferred, way is to use Gradle's
native `repositories.ivy { }` DSL. The second way was to use an Ivy `DependencyResolver` implementation with the `repositories.add()` method.

Support for using Ivy `DependencyResolver` instances to define repositories will be discontinued in Gradle 2.0. Please note that Gradle will continue to
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

A number of constants on `ArtifactRepositoryContainer` have been deprecated and will be removed in Gradle 2.0.

### Open API classes

The Open API has been deprecated for over two years (since Gradle 1.0-milestone-4) and is due to be removed in Gradle 2.0. The entry points to the
Open API were marked deprecated at that time.

To make the deprecation of the Open API more explicit, all of the Open API classes are now marked as deprecated, in addition to the entry points. All of the
Open API classes will be removed in Gradle 2.0.

### Unused classes

The following classes will be removed in Gradle 2.0. They are no longer used:

* `IllegalOperationAtExecutionTimeException`
* `AntJavadoc`

<!--
### Example deprecation
-->

## Potential breaking changes

### Upgraded to Ant 1.9.2

The version of Ant used by Gradle has been upgraded to Ant 1.9.2. This should be backwards compatible.

### Changes in task arguments evaluation

All arguments passed for task creation are evaluated to be valid. In earlier Gradle versions, a typo in the arguments was silently ignored.
The following snippet will now fail with a decent error message, giving a hint, that 'Type' is not a valid argument.

<pre>
    task myCopy(Type: copy) {
        from "..."
        into "..."
    }
}</pre>


### Changes to incubating C++ support

* Renamed task class org.gradle.nativecode.base.tasks.AssembleStaticLibrary to org.gradle.nativecode.base.tasks.CreateStaticLibrary, with the
  default task instance also being renamed from 'assemble${StaticLibraryName}' to 'create${StaticLibraryName}'
* Renamed plugin class org.gradle.nativecode.base.plugins.BinariesPlugin to org.gradle.nativecode.base.plugins.NativeBinariesPlugin
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

The DSL for defining C++ source sets has changed, with the 'cpp' extension being removed. This change makes the C++ plugin DSL consistent with
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

### Changes to handling of Ivy `DependencyResolver` implementations

In order to improve performance and heap usage during dependency resolution, this release includes some internal changes to the way meta-data is
parsed. If you use an Ivy `DependencyResolver` implementation to define repositories, meta-data parsing is now delegated to Ivy instead of
using Gradle's parser implementations. This means that these resolvers will no longer take advantage of performance improvements in Gradle's
meta-data handling. The changes should generally be backwards compatible, however.

Note that using Ivy `DependencyResolver` implementations is deprecated, and we recommend that you use Gradle's repository implementations instead.

### The order of resolved artifacts is slightly different

todo

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
