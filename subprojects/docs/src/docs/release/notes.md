Gradle 2.3 brings some nice new capabilities to dependency management and IDE support, as well as improvements to some core plugins.

A long requested feature has been the ability to access metadata artifacts like `ivy.xml` and `pom.xml`, that Gradle uses to perform dependency resolution.
Using the Artifact Query API, you now have direct access to these raw metadata artifacts. This could be useful for generating an offline
repository, inspecting the files for custom metadata, and much more.

IDE support in Gradle continues to improve, and Gradle 2.3 brings enhancements to the Gradle tooling API together with numerous bug fixes in our IDE plugins.
Of note, this release brings much better integration with the Eclipse Web Tools Platform via the `eclipse-wtp` plugin.

As always, this Gradle release benefits from a large number of community contributions. These include substantial enhancements to the
`antlr`, `compare-gradle-builds` and `application` plugins, as well as many bug fixes and improvements.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Access Ivy and Maven metadata artifacts via the Artifact Query API (i)

Gradle 2.0 introduced an incubating 
[query API for resolving component artifacts](http://www.gradle.org/docs/2.0/release-notes#new-api-for-resolving-source-and-javadoc-artifacts).

Gradle 2.3 extends this API to allow for retrieving the metadata artifacts for Ivy and Maven modules. 
This means that a build can access the raw `ivy.xml` or `module.pom` file that Gradle used when resolving a dependency.

Given that the dependency is resolved from an Ivy repository, the Ivy descriptor artifact can be retrieved as follows:

    task resolveIvyDescriptorFiles {
        doLast {
            def componentIds = configurations.compile.incoming.resolutionResult.allDependencies.collect { it.selected.id }

            def result = dependencies.createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(IvyModule, IvyDescriptorArtifact)
                .execute()

            for (component in result.resolvedComponents) {
                component.getArtifacts(IvyDescriptorArtifact).each { assert it.file.name == 'ivy.xml' }
            }
        }
    }

For a dependency that is resolved from a Maven repository, the Maven POM artifact can be retrieved as follows:

    task resolveMavenPomFiles {
        doLast {
            def componentIds = configurations.compile.incoming.resolutionResult.allDependencies.collect { it.selected.id }

            def result = dependencies.createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(MavenModule, MavenPomArtifact)
                .execute()

            for(component in result.resolvedComponents) {
                component.getArtifacts(MavenPomArtifact).each { assert it.file.name == 'some-artifact-1.0.pom' }
            }
        }
    }

See the [ArtifactResolutionQuery API reference](dsl/org.gradle.api.artifacts.query.ArtifactResolutionQuery.html) for more details.

### Component metadata rule enhancements (i)

Gradle 2.0 introduced 'component metadata rules', allowing certain aspects of dependency metadata to be modified based on custom build logic.
For example, these rules can be used to specify that all components produced by a particular organisation have a custom 'status scheme', 
or that all components for a particular Ivy module with a custom 'status' attribute should be considered 'changing'.

In this release, the interface for defining component metadata rules has been enhanced. 
It is now possible to define rules that only apply to a particular module, as well as rules that apply to all components.

- Use <a href="dsl/org.gradle.api.artifacts.dsl.ComponentMetadataHandler.html#org.gradle.api.artifacts.dsl.ComponentMetadataHandler:all(groovy.lang.Closure)">`components.all`</a> 
  to define a rule that applies to all components.
- Use <a href="dsl/org.gradle.api.artifacts.dsl.ComponentMetadataHandler.html#org.gradle.api.artifacts.dsl.ComponentMetadataHandler:withModule(java.lang.Object, groovy.lang.Closure)">
  `components.withModule('group:name')`</a> to define a rule that applies to any component that matches the supplied 'group' and 'name' attributes.

Note that `components.eachComponent` has been replaced with `components.all`, with the former being deprecated for removal in Gradle 2.4.

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
        }
    }
    
    
Furthermore, rules can now also be specified as `rule source` objects, allowing them to be defined more easily in Java code:

    dependencies {
        components {
            // This rule uses a rule source object that applies only to the "my.org:api" module
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

### Improvements to the ANTLR plugin

Gradle has built-in support for parser generation using [ANTLR](http://www.antlr.org) using the [ANTLR Plugin](userguide/antlr_plugin.html). 
Gradle 2.3 contains improvements to this support.

Adding to the existing support for ANTLR version `2.x`, Gradle now supports ANTLR versions `3.x` and `4.x` of .
To use ANTLR version 3 or 4 in a build, simply add the correct dependency to the `antlr` configuration:

    apply plugin: "java"
    apply plugin: "antlr"

    repositories() {
        jcenter()
    }

    dependencies {
        antlr 'org.antlr:antlr4:4.3'
    }


Additionally, the [`AntlrTask`](dsl/org.gradle.api.plugins.antlr.AntlrTask.html) now executes ANTLR in a separate process. 
This allows more fine grained control over memory settings for the ANTLR process.

See the [user guide chapter on the Antlr Plugin](userguide/antlr_plugin.html) for further details.

These improvements were contributed by [Rob Upcraft](https://github.com/upcrob).

### Build Comparison plugin now compares nested archives (i)

The [Gradle Build Comparison plugin](userguide/comparing_builds.html) provides support for comparing the outcomes (e.g. the produced binary archives) of two builds.
This support is greatly improved in Gradle 2.3, with the ability to compare entries of nested archives.
A common type of nested archive that benefits from this change is a `WAR` file containing `JAR` files.

Previously, when comparing an archive for changes, the entries of that archive were treated as binary blobs. 
So Gradle would detect if a particular entry had changed, but would not inspect more deeply to see _how_ it had changed. 
As of Gradle 2.3, entries of archive entries are inspected recursively where possible. 
This means that archive entries that are themselves archives are compared entry by entry.

This feature was contributed by [Björn Kautler](https://github.com/Vampire).

### Better integration with Eclipse Web Tools Platform

In this release, support for the Eclipse Web Tools Platform via the [`eclipse-wtp`](userguide/eclipse_plugin.html) plugin has been vastly improved.

Previous versions of this plugin were highly dependent on having the `ear` or `war` plugin applied. 
By removing this dependency, Gradle 2.3 enables users to leverage the Eclipse WTP support in more circumstances.
Examples where this may be useful include projects producing a plain EJB module, or projects that prefer to configure
facets directly on the Eclipse project.

A further improvement in this release is that generated Eclipse `.classpath` files now include correct dependency attributes (GRADLE-1422),
fixing the case where certain Gradle builds could not be easily exported to Eclipse.

Thanks to [Andreas Schmid](https://github.com/aaschmid), who contributed many of these improvements.

### Tooling API improvements

The Gradle tooling API provides a stable API that tools such as IDEs can use to embed Gradle. 

In Gradle 2.3, the tooling API now supports generating colored build output, identical to that generated by Gradle on the command-line. 
This feature was contributed by [Lari Hotari](https://github.com/lhotari).

When using the `GradleConnector` to create a tooling API connection, 
the method [`GradleConnector.useBuildDistribution()`](javadoc/org/gradle/tooling/GradleConnector.html#useBuildDistribution--) can now be used 
to explicitly declare that the distribution defined by the target Gradle build should be used.

### New `PluginManager` interface for applying and managing plugins by id (i)

In the past, the [`PluginContainer`](javadoc/org/gradle/api/plugins/PluginContainer.html) API has been used for applying and querying plugins. This interface has a number of problems,
including only supporting instances of `Plugin`, providing mutation methods that are not properly supported, and requiring that plugin instances
be instantiated immediately on applying.

Gradle 2.3 introduces a new [`PluginManager`](dsl/org.gradle.api.plugins.PluginManager.html) API which addresses these issues, fully supporting `RuleSource` plugins and dealing better with
plugin identification. While `PluginContainer` is not deprecated, build authors should prefer to use `PluginManager` methods where possible.

Useful methods include:

- <a href="dsl/org.gradle.api.plugins.PluginManager.html#org.gradle.api.plugins.PluginManager:hasPlugin(java.lang.String)">`PluginManager.hasPlugin(String id)`</a>
- <a href="dsl/org.gradle.api.plugins.PluginManager.html#org.gradle.api.plugins.PluginManager:findPlugin(java.lang.String)">`PluginManager.findPlugin(String id)`</a>
- <a href="dsl/org.gradle.api.plugins.PluginManager.html#org.gradle.api.plugins.PluginManager:withPlugin(java.lang.String, org.gradle.api.Action)">`PluginManager.withPlugin(String id, Action action)`</a>

These methods should be used when reacting to the presence of another plugin or for ad-hoc reporting.

### Application plugin is integrated with the Distribution plugin

The [Application Plugin](userguide/application_plugin.html) now leverages the [Distribution Plugin](userguide/distribution_plugin.html) for doing packaging. 
By configuring the 'main' distribution, a packaged application is treated as any other distribution.

For example, you can use `gradle installDist` to create an image of your application,
`gradle distZip` to create a ZIP distribution of your application, and `gradle assemble` to build all application distributions. 

This feature was contributed by [Sébastien Cogneau](https://github.com/scogneau).

### Groovy version upgraded to 2.3.9

Gradle 2.3 includes Groovy 2.3.9 (upgraded from Groovy 2.3.6 in Gradle 2.2.1).

This is a non breaking change.
All build scripts and plugins that work with Gradle 2.2.1 should continue to work without change or recompilation.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

### Renamed method on ComponentMetadataHandler

The `eachComponent` method on the incubating `ComponentMetadataHandler` interface has been deprecated and replaced with `all`.

As this is an incubating feature, the deprecated method will be removed in Gradle 2.4.

### `--no-color` command-line option

The `--no-color` option has been replaced by the more general `--console` option. You can use `gradle --console plain ...` instead of `gradle --no-color ...`.

The `--no-color` option will be removed in Gradle 3.0.

### Deprecated 'installApp' task in Application Plugin

The `installApp` task of the application plugin is deprecated and will be removed in Gradle 3.0. You can use the `installDist` task instead.

## Potential breaking changes

### Major changes to incubating 'native-component' and 'jvm-component' plugins

As we develop a new configuration and component model for Gradle, we are also developing an underlying infrastructure to allow
the easy implementation of plugins supporting new platforms (native/jvm/javascript) and languages (C/C++/Java/Scala/CoffeeScript).

This version of Gradle takes a big step in that direction, by migrating the existing component-based plugins to sit on top of this
new infrastructure. This includes the incubating 'jvm-component' and 'java-lang' plugins, as well as all of the plugins providing
support for building native applications.

Due to this, the DSL for defining native executables and libraries has fundamentally changed. The `executables` and `libraries` containers
have been removed, and components are now added by type to the `components` container owned by the model registry. Another major change is
that source sets for a component are now declared directly within the component definition, instead of being configured on the `sources` block.

For comparison, the following native components defined in Gradle 2.2:

    libraries {
        hello {}
    }

    executables {
        main {}
    }

    sources {
        main {
            cpp {
                lib library: "hello"
                source {
                    srcDir "src/source"
                    include "**/*.cpp"
                }
                exportedHeaders {
                    srcDir "src/include"
                }
            }
        }
    }
    
are defined as follows in Gradle 2.3:

    model {
        components {
            hello(NativeLibrarySpec)

            main(NativeExecutableSpec) {
                sources {
                   cpp {
                        lib library: "hello"
                        source {
                            srcDir "src/source"
                            include "**/*.cpp"
                        }
                        exportedHeaders {
                            srcDir "src/include"
                        }
                   }
                }
            }
        }
    }

The new syntax is more flexible to be used across different component domains, and will continue to be enhanced in the future.
Please take a look at the sample applications found in `samples/native-binaries` and `samples/jvmComponents` to get a better idea of how 
you may migrate your Gradle build to the new syntax.

Note that this functionality is a work-in-progress, and in some cases it may be preferable to remain on an earlier version of Gradle until
it has stabilised.

### Gradle no long builds native binaries for all defined platforms

For the new `jvm-component` plugins, the set of available Java platforms is not defined by the user. This is different from how the native language
plugins worked, in that they assumed that the build script defined the entire set of known platforms.

Going forward, we feel that the jvm model is better, where Gradle can resolve a platform from an unbounded set of platforms. 
Platform definitions could come from a number of sources: downloaded via plugins, defined in a build script, or by inspecting the local environment.

This version of Gradle takes a step in that direction, by changing the semantic of which platform variants will be built for a defined
`NativeExecutableSpec` or `NativeLibrarySpec`. If no `targetPlatform` is specified, Gradle will make an attempt to build for the 'current'
platform. If you wish to build for a different platform (or if Gradle gets the 'current' platform wrong), you will need to specify
the `targetPlatform` for your native binary.

Expect this mechanism to change further in future Gradle releases.

### AntlrTask has incremental task action

The [`AntlrTask`](dsl/org.gradle.api.plugins.antlr.AntlrTask.html) now processes input files incrementally.
To do so, the [`execute`](javadoc/org/gradle/api/plugins/antlr/AntlrTask.html#execute-org.gradle.api.tasks.incremental.IncrementalTaskInputs-) has been changed to accept an
`IncrementalTaskInputs` parameter.

This will break any code that calls the `execute` method of this task directly.

### Mapping of Maven version range selectors

The maven version declaration `LATEST` is mapped to `latest.integration` and `RELEASE` is mapped to `latest.release` when resolving a dependency from
a maven repository. The reverse mapping is applied when generating a pom file in Gradle (e.g. for publishing via `maven-publish` or `maven` plugin).

### Ivy dependency exclude rules

Previous versions of Gradle improperly handled the `name` attribute for [dependency exclude rules](http://ant.apache.org/ivy/history/latest-milestone/ivyfile/artifact-exclude.html).
Instead of excluding the matching artifact(s), the whole module was excluded. This behavior has been fixed with this version of Gradle. Keep in mind that this change
may cause a slightly different dependency resolution behavior if you heavily rely on Ivy excludes.

In this context, we also fixed the incorrect handling of the `artifact` attribute for [module exclude rules](http://ant.apache.org/ivy/history/latest-milestone/ivyfile/exclude.html). For
more information see [GRADLE-3147](https://issues.gradle.org/browse/GRADLE-3147).

### Changes in application plugin

With the application plugin applied, `gradle assemble` will now also build the application distributions (application zip and tar)
and not only the `jar`.

### Manually declared facets in eclipse-wtp plugin

In previous Gradle versions, declaring a custom facet for `eclipse.wtp` caused the default facets to be removed.

    eclipse {
        wtp {
            facet {
                facet name: 'someNeededFacet', version: '1.3'
            }
        }
    }

This behaviour has been fixed in this version of Gradle: declaring a facet in this way will add to the set of default facets.

This fix was contributed by [Andreas Schmid](https://github.com/aaschmid).

### Tooling API `GradleProject` model includes implicit tasks

The `GradleProject` model now includes the implicit tasks of a project, such as `help`, `tasks`, `dependencies`, and `wrapper`.

This change could impact code that relied on the fact that these tasks formerly were absent from [`GradleProject.getTasks()`](javadoc/org/gradle/tooling/model/GradleProject.html#getTasks--).

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Lari Hotari](https://github.com/lhotari) - improvements to output handling in tooling API (GRADLE-2687) and coloring for the output
* [Sébastien Cogneau](https://github.com/scogneau) - share distribution plugin logic with application plugin
* [Greg Chrystall](https://github.com/ported) - IDEA plugin generates wrong sources jar for multi artifacts dependencies (GRADLE-3170)
* [Rob Upcraft](https://github.com/upcrob) - add support for ANTLR v3 and v4 to antlr plugin (GRADLE-902)
* [Andreas Schmid](https://github.com/aaschmid)
    - changes to Eclipse classpath generating when using WTP (GRADLE-1422, GRADLE-2186, GRADLE-2362, GRADLE-2221)
    - don't remove default facets when manually declaring a Eclipse WTP facet
* [Björn Kautler](https://github.com/Vampire) - improvements to build comparison plugin
* [Michal Srb](https://github.com/msrb) - update bouncycastle dependency to the latest version
* [Stefan Wolf](https://github.com/wolfs) - support Maven publications that have multiple artifacts without classifier
* [Daniel Lacasse](https://github.com/Shad0w1nk) - improvements to static task references available for each native binary type
* [Spencer Wood](https://github.com/malibuworkcrew) - support `configfailurepolicy` option for TestNG
* [Adam Dubiel](https://github.com/adamdubiel) - wrapper respects -q/--quiet option
* [Harald Schmitt](https://github.com/surfing) - format numbers in tests in a locale independent way
* [Wojciech Gawroński](https://github.com/afronski) - add a toggle for wrapping long lines of 'code' blocks in test reports
* [Ben McCann](https://github.com/benmccann)
    - use HTTPS when downloading Play binaries in integration tests
    - update supported Play version to 2.3.7
* [Mike Meesen](https://github.com/netmikey) - support external DTDs in EAR application descriptors when building with Java 8
* [Andrew Kowal](https://github.com/akowal) - fix test class detection when using jdk8 '`-parameters`' compiler option (GRADLE-3157)
* [Victor Bronstein](https://github.com/victorbr) - optimise creation of static builder in ModuleVersionSelectorParsers

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
