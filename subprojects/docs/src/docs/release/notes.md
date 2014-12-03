## New and noteworthy

Here are the new features introduced in this Gradle release.

### Component metadata rule enhancements

The interface for defining component metadata rules has been enhanced so that it now supports defining rules on a per module basis
as well as for all modules.  Furthermore, rules can now also be specified as `rule source` objects.

    dependencies {
        components {
            // This rule applies to all modules
            all { ComponentMetadataDetails details ->
                if (details.group == "my.org" && details.status == "integration") {
                    details.changing = true
                }
            }

            // This rule applies to only the "my.org:api" module
            withModule("my.org:api") { ComponentMetadetails details ->
                details.statusScheme = [ "testing", "candidate", "release" ]
            }

            // This rule uses a rule source object to define another rule for "my.org:api"
            withModule("my.org:api", new CustomStatusRule()) // See class definition below
        }
    }

    class CustomStatusRule {
        @Mutate
        void setComponentStatus(ComponentMetadataDetails details) {
            if (details.status == "integration") {
                details.status = "testing"
            }
        }
    }

See the [userguide section](userguide/dependency_management.html#component_metadata_rules) on component metadata rules for further information.

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

### Improved performance with class loader caching

We want each new version of Gradle to perform better.
Gradle is faster and less memory hungry when class loaders are reused between builds.
The daemon process can cache the class loader instances, and consequently, the loaded classes.
This unlocks modern jvm optimizations that lead to faster execution of consecutive builds.
This also means that if the class loader is reused, static state is preserved from the previous build.
Class loaders are not reused when build script classpath changes (for example, when the build script file is changed).

In the reference project, we observed 10% build speed improvement for the initial build invocations in given daemon process.
Later build invocations perform even better in comparison to Gradle daemon without classloader caching.

This incubating feature is not turned on by default at the moment.
It can be switched on via incubating system property "org.gradle.caching.classloaders".
Example setting in gradle.properties file:

    org.gradle.jvmargs=-Dorg.gradle.caching.classloaders=true

### New PluginAware methods for detecting the presence of plugins

The `PluginAware` interface (implemented by `Project`, `Gradle` and `Settings`) has the following new methods for detecting the presence of plugins, based on ID:

* findPlugin()
* hasPlugin()
* withPlugin()

These methods should be used when reacting to the presence of another plugin or for ad-hoc reporting.

TODO - more detail.

### ANTLR plugin supports ANTLR version 3.X and 4.X

Additionally to the existing 2.X support, the [ANTLR Plugin](userguide/antlrPlugin.html) now supports ANTLR version 3 and 4. 
To use ANTLR version 3 or 4 in a build, an according antlr dependency must be declared explicitly:

    apply plugin: "java"
    apply plugin: "antlr"
    
    repositories() {
        jcenter()
    }
    
    dependencies {
        antlr 'org.antlr:antlr4:4.3'
    }
  
This feature was contributed by [Rob Upcraft](https://github.com/upcrob).

### AntlrTask running in separate process

The [`AntlrTask`](dsl/org.gradle.api.plugins.AntlrTask.html) is now 
executed in a separate process. This allows more fine grained control over memory settings just for the ANTLR process.
See [Antlr Plugin](userguide/antlrPlugin.html) for further details. 

This feature was also contributed by [Rob Upcraft](https://github.com/upcrob).

### Build Comparison plugin now compares nested archives

The [Build Comparison plugin](userguide/comparing_builds.html) has been improved in this release to compare entries of nested archives.
Previously, when comparing an archive all archive entries were treated as binary blobs.
Now, entries of archive entries are inspected recursively where possible.
That is, archive entries that are themselves archives are compared entry by entry.
A common type of nested archive is a WAR file containing JAR files.

This feature was contributed by [Björn Kautler](https://github.com/Vampire).

### Tooling API improvements

The Gradle tooling API provides a stable API that tools such as IDEs can use to embed Gradle. In Gradle 2.3, the tooling API now supports generating
colored build output, identical to that generated by Gradle on the command-line. This feature was contributed by Lari Hotari.

Tooling API JAR is now OSGi compatible. Its manifest is generated using [Bnd](http://www.aqute.biz/Bnd/Bnd) tool.

### Access the Ivy and Maven metadata artifacts via the Artifact Query API for component ID(s)

Gradle 2.0 introduced an incubating [API for resolving component artifacts](http://www.gradle.org/docs/2.0/release-notes#new-api-for-resolving-source-and-javadoc-artifacts).
With this release the Artifact Query API also allows for retrieving the metadata artifacts for Ivy and Maven modules. The following examples assume the declaration of a single dependency
for the `compile` configuration:

    dependencies {
        compile 'some.group:some-artifact:1.0'
    }

Given that the dependency is resolved from an Ivy repository, the Ivy descriptor artifact can be retrieved as follows:

    task resolveIvyDescriptorFiles {
        doLast {
            def componentIds = configurations.compile.incoming.resolutionResult.allDependencies.collect { it.selected.id }

            def result = dependencies.createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(IvyModule, IvyDescriptorArtifact)
                .execute()

            Set<File> ivyFiles = result.getArtifactFiles()
            assert ivyFiles.size() == 1
            assert ivyFiles.collect { it.name } == ['ivy.xml']
        }
    }

Given that the dependency is resolved from a Maven repository, the Maven POM artifact can be retrieved as follows:

    task resolveMavenPomFiles {
            doLast {
                def componentIds = configurations.compile.incoming.resolutionResult.allDependencies.collect { it.selected.id }

                def result = dependencies.createArtifactResolutionQuery()
                    .forComponents(componentIds)
                    .withArtifacts(MavenModule, MavenPomArtifact)
                    .execute()

                Set<File> pomFiles = result.getArtifactFiles()
                assert pomFiles.size() == 1
                assert pomFiles.collect { it.name } == ['some-artifact-1.0.pom']
            }
        }

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

### Multiple `PluginContainer` methods are deprecated.

[`PluginContainer.apply(String)`](javadoc/org/gradle/api/plugins/PluginContainer.html#apply\(java.lang.String\)) and
[`PluginContainer.apply(Class)`](javadoc/org/gradle/api/plugins/PluginContainer.html#apply\(java.lang.Class\)) methods are deprecated, 
please use [`PluginAware.apply(Map)`](javadoc/org/gradle/api/plugins/PluginAware.html#apply\(java.util.Map\)) or 
[`PluginAware.apply(Closure)`](javadoc/org/gradle/api/plugins/PluginAware.html#apply\(groovy.lang.Closure\)) instead.

    // Instead of…
    project.plugins.apply("java")
    
    // Please use…
    project.apply(plugin: "java")

All other mutative methods of `PluginContainer` are deprecated without replacements:

* `add(Plugin)`
* `addAll(Collection<? extends Plugin>)`
* `clear()`
* `remove(Object)`
* `removeAll(Collection<?>)`
* `retainAll(Collection<?>)`

These methods have no useful purpose.   

The deprecated method will be removed in Gradle 3.0.

### Renamed method on ComponentMetadataHandler

The `eachComponent` method on the incubating `ComponentMetadataHandler` interface has been deprecated and replaced with `all`.
As this is an incubating feature, the deprecated method will be removed in Gradle 2.3.

### `--no-color` command-line option

The `--no-color` option has been replaced by the more general `--console` option. You can use `gradle --console plain ...` instead of `gradle --no-color ...`.

The `--no-color` option will be removed in Gradle 3.0.

## Potential breaking changes

### Major to incubating 'native-component' and 'jvm-component' plugins

As we develop a new configuration and component model for Gradle, we are also developing an underlying infrastructure to allow
the easy implementation of plugins supporting new platforms (native/jvm/javascript) and languages (C/C++/Java/Scala/CoffeeScript).

This version of Gradle takes a big step in that direction, by migrating the existing component-based plugins to sit on top of this
new infrastructure. This includes the incubating 'jvm-component' and 'java-lang' plugins, as well as all of the plugins providing
support for building native applications.

Due to this, the DSL for defining native executables and libraries has fundamentally changed. The `executables` and `libraries` containers
have been removed, and components are now added by type to the `components` container owned by the model registry. Another major change is
that source sets for a component are now declared directly within the component definition, instead of being configured on the `sources` block.

Please take a look at the sample applications found in `samples/native-binaries` to get a better idea of how you may migrate your Gradle build
file to the new syntax.

Note that this functionality is a work-in-progress, and in some cases it may be preferable to remain on an earlier version of Gradle until
it has stabilised.

### Ivy dependency exclude rules

Previous versions of Gradle improperly handled the `name` attribute for [dependency exclude rules](http://ant.apache.org/ivy/history/latest-milestone/ivyfile/artifact-exclude.html).
Instead of excluding the matching artifact(s), the whole module was excluded. This behavior has been fixed with this version of Gradle. Keep in mind that this change
may cause a slightly different dependency resolution behavior if you heavily rely on Ivy excludes.

In this context, we also fixed the incorrect handling of the `artifact` attribute for [module exclude rules](http://ant.apache.org/ivy/history/latest-milestone/ivyfile/exclude.html). For
more information see [GRADLE-3147](https://issues.gradle.org/browse/GRADLE-3147).

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Lari Hotari](https://github.com/lhotari) - improvements to output handling in Tooling API (GRADLE-2687) and coloring for the output
* [Sébastien Cogneau](https://github.com/scogneau) - share distribution plugin logic with application plugin
* [Greg Chrystall](https://github.com/ported) - idea plugin generates wrong sources jar for multi artifacts dependencies (GRADLE-3170)
* [Rob Upcraft](https://github.com/upcrob) - add support for ANTLR v3 and v4 to antlr plugin (GRADLE-902)
* [Andreas Schmid](https://github.com/aaschmid) - changes to Eclipse classpath generating when using WTP (GRADLE-1422, GRADLE-2186, GRADLE-2362, GRADLE-2221)
* [Björn Kautler](https://github.com/Vampire) - improvements to Build Comparison plugin
* [Michal Srb](https://github.com/msrb) - update bouncycastle dependency to the latest version
* [Stefan Wolf](https://github.com/wolfs) - support maven publications that have multiple artifacts without classifier
* [Daniel Lacasse](https://github.com/Shad0w1nk) - improvements to static task references available for each native binary type
* [Spencer Wood](https://github.com/malibuworkcrew) - support `configfailurepolicy` option for TestNG
* [Adam Dubiel](https://github.com/adamdubiel) - wrapper respects -q/--quiet option
* [Harald Schmitt](https://github.com/surfing) - format numbers in tests in a locale independent way
* [Wojciech Gawroński](https://github.com/afronski) - add a toggle for wrapping long lines of 'code' blocks in test reports
* [Ben McCann](https://github.com/benmccann) - use https when downloading Play binaries in integration tests
* [Mike Meesen](https://github.com/netmikey) - support external DTDs in ear application descriptors when building with Java 8
* [Andrew Kowal](https://github.com/akowal) - Fix test class detection when using jdk8 '`-parameters`' compiler option (GRADLE-3157)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
