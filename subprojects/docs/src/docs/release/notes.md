## New and noteworthy

Here are the new features introduced in this Gradle release.

### Preparing for Gradle 2.0

We've started some initial preparations for a Gradle 2.0 release early next year. At this stage, we're cleaning up the API and marking a
number of features as deprecated, for removal in Gradle 2.0. You'll find more details below.

Removing unwanted features allows us to reduce the complexity of Gradle, and this means fewer bugs, more features and faster builds for you.

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

Previously, in the no-tests scenario, the test task was still executed. This meant that dependency resolution would occur and an empty html report was generated.
This new behavior results in faster builds when there are no tests. No negative impacts on existing builds are expected.

## Contributors

On behalf of the Gradle community, the Gradle development team would like to thank the following people who contributed to this version of Gradle:

* [Kyle Mahan](https://github.com/kylewm) - some Javadoc fixes

Contributions are an important part of the continuous improvement of Gradle. 

If you would like to contribute to Gradle, please see [gradle.org/contribute](http://gradle.org/contribute) for how to start.

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
