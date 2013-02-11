## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
### Example new and noteworthy
-->

### IDEA plugin is now Scala aware

When the IDEA plugin encounters a Scala project, it will now add additional configuration to make the
project compile in IntelliJ IDEA out of the box. In particular, the plugin adds a Scala facet and
a Scala compiler library that matches the Scala version used on the project's class path.

### Improved test report generation

The test report generation was refactored and is now slightly faster than in previous Gradle releases.
 
## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Incubating features

Incubating features are intended to be used, but not yet guaranteed to be backwards compatible.
By giving early access to new features, real world feedback can be incorporated into their design.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the new incubating features or changes to existing incubating features in this Gradle release.

### More robust dependency resolve rules

The dependency resolve rules introduced in Gradle 1.4 are getting more robust.
It is now possible to change group, name and/or version of a requested dependency when it is resolved.
Previously, only the version could be updated via a dependency resolve rule.

    configurations.all {
        resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (details.requested.group == 'groovy-all') {
                details.useTarget group: details.requested.group, name: 'groovy', version: details.requested.version
            }
        }
    }

With this change, dependency resolve rules can now be used to solve some interesting dependency resolution problems:

- Substituting an alternative implementation for some module. For example, replace all usages of `log4j` with a compatible version of `log4j-over-slf4j`.
- Dealing with conflicting implementations of some module. For example, replace all usages of the various `slf4j` bindings with `slf4j-simple`.
- Dealing with conflicting packaging of some module. For example, replace all usages of `groovy` and `groovy-all` with `groovy`.
- Dealing with modules that have changed their (group, module) identifier. For example, replace `ant:ant:*` with `org.apache.ant:ant:1.7.0` and let conflict resolution take care of the rest.
- Substituting different implementations at different stages. For example, substitute all servlet API dependencies with `'javax.servlet:servlet-api:2.4'` at compile time and the jetty implementation at test runtime.

For more information, including more code samples, please refer to
[this user guide section](userguide/userguide_single.html#module_substitution).

### Improved scalability with configuration on demand

If you already enjoy configuration on demand please note the following improvements and changes:

* Tooling API deals nicely with configure-on-demand.
 Building model configures all projects but running tasks via the Tooling API takes full advantage of the feature.
* New gradle property "org.gradle.configureondemand" should be used enable the feature for all builds of the given project.
 This way it is configurable consistently with other [build settings](userguide/build_environment.html#sec:gradle_configuration_properties).
 Note that the property has changed - see the example below how to configure your gradle.properties.


    #gradle.properties file
    org.gradle.configureondemand=true

* New handy command line option "--configure-on-demand" enables the feature per build.
* The task dependencies declared via task path are supported and cause relevant projects configured:

    //depending on task from a different project:
    someTask.dependsOn(":someProject:someOtherProject:someOtherTask")

If you didn't know that you can configure on demand let's dive into this feature really quickly.
In Gradle, all projects are configured before any task gets executed (see [the build lifecycle](userguide/build_lifecycle.html#sec:build_phases)).
In "configuration on demand" mode only those projects required by the build are configured.
This should speed up the configuration time of huge multi-project builds.
This mode is still incubating but should work very well with builds that have
[decoupled projects](userguide/multi_project_builds.html#sec:decoupled_projects)
(e.g. avoiding having a subproject accessing the model of another project).
The best place to start configuring on demand is diving into [this section in the user guide](userguide/multi_project_builds.html#sec:configuration_on_demand).

### New gradle property 'org.gradle.parallel'

New Gradle property can be used to configure your [build environment](userguide/build_environment.html#sec:gradle_configuration_properties).
The incubating parallel build execution can now be configured in a persistent fashion:

    //gradle.properties file
    org.gradle.parallel=true

### Easy publication of software components with the 'maven-publish' or 'ivy-publish' plugins

Gradle 1.5 includes the concept of a Software Component, which defines something that can be produced by a Gradle project, like a Java Library or a Web Application.
Both the 'ivy-publish' and 'maven-publish' plugins are component-aware, simplifying the process of publishing a module. The component defines the set of artifacts and dependencies for publishing.
Presently, the set of components available for publishing is limited to 'java' and 'web', added by the 'java' and 'war' plugins respectively.

Publishing the 'web' component will result in the war file being published with no runtime dependencies (dependencies are bundled in the war):

    apply plugin: 'war'
    apply plugin: 'maven-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    publishing {
        repositories {
            maven { url 'http://mycompany.org/mavenRepo' }
            ivy { url 'http://mycompany.org/ivyRepo' }
        }
        publications {
            mavenWeb(MavenPublication) {
                from components.web
            }
            ivyWeb(IvyPublication) {
                from components.java // Include the standard java artifacts
            }
        }
    }

### Customise artifacts published with the 'maven-publish' or 'ivy-publish' plugins

This release introduces an API/DSL for customising the set of artifacts to publish to a Maven repository or an Ivy repository.
Due to differences in the capabilities of Ivy vs Maven repositories, the DSL is slightly different between IvyPublication and MavenPublication.
This DSL allows gives a Gradle build complete control over which artifacts are published, and the classifier/extension used to publish them.

    apply plugin: 'java'
    apply plugin: 'maven-publish'
    apply plugin: 'ivy-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    task sourceJar(type: Jar) {
        from sourceSets.main.allJava
        classifier "source"
    }

    publishing {
        repositories {
            maven { url 'http://mycompany.org/mavenRepo' }
            ivy { url 'http://mycompany.org/ivyRepo' }
        }
        publications {
            mavenCustom(MavenPublication) {
                from components.java // Include the standard java artifacts
                artifact sourceJar {
                    classifier "source"
                }
                artifact("project-docs.htm") {
                    classifier "docs"
                    extension "html"
                    builtBy myDocsTask
                }
            }
            ivyCustom(IvyPublication) {
                from components.java // Include the standard java artifacts
                artifact(sourceJar) {
                    type "source"
                    conf "runtime"
                    classifier "source"
                }
                artifact("project-docs.htm") {
                    classifier "docs"
                    extension "html"
                    builtBy myDocsTask
                }
            }
        }
    }

Be sure to check out the DSL reference for [MavenPublication](dsl/org.gradle.api.publish.maven.MavenPublication.html) and [IvyPublication](dsl/org.gradle.api.publish.ivy.IvyPublication.html)
for complete details on how the set of artifacts can be customised.

### Generate POM file without publishing with the 'maven-publish' plugin

Pom file generation has been moved into a separate task, so that it is now possible to generate the Maven Pom file without actually publishing your project. All details of
the publishing model are still considered in Pom generation, including `components`, custom `artifacts`, and any modifications made via `pom.withXml`.

The task for generating the Pom file is of type [`GenerateMavenPom`](dsl/org.gradle.api.publish.maven.tasks.GenerateMavenPom.html), and it is given a name based on the name
of the publication: `generatePomFileFor<publication-name>Publication`. So in the above example where the publication is named 'mavenCustom',
the task will be named `generatePomFileForMavenCustomPublication`.

### Distribution Plugin

The distribution plugin extends the language plugins with common distribution related tasks.
It allows bundling a project including binaries, sources and documentation.

This plugin adds a main default distribution. The plugin adds one `main` distribution. The `distZip` task can be used to create a ZIP containing the main distribution.

You can define multiple distributions:

    distributions {
        enterprise
		community
    }

To build the additional distributions you can run the generated Zip tasks enterpriseDistZip and communityDistZip.

### Java Library Distribution Plugin

The java library distribution plugin now extends the new introduced distribution plugin. The `distribution` extension was removed. The `main` distribution is now accessible using the distributions extension:

    distributions {
        main {
            ...
        }
    }

### Build Dashboard Plugin

The Build Dashboard plugin adds a task to projects to generate a build dashboard html report which contains references to all reports that were generated during the build. In the following example,
the `build-dashboard` plugin is added to a project which has also the `groovy` and the `codenarc` plugin applied:

	apply plugin: 'groovy'
    apply plugin: 'build-dashboard'
	apply plugin: 'codenarc'
	
By running the `buildDashboard` task after other tasks that generate reports (e.g. by running `gradle check buildDashboard`), the generated build dashboard contains links to the `test` and the `codenarc` reports.
 
### New Sonar Runner plugin

Gradle 1.5 ships with a new *Sonar Runner* plugin that is set to replace the existing Sonar plugin. As its name indicates,
the new plugin is based on the [Sonar Runner](http://docs.codehaus.org/display/SONAR/Analyzing+with+Sonar+Runner),
the new and official way to integrate with Sonar. Unlike the old Sonar plugin, the new Sonar Runner plugin
is compatible with the latest Sonar versions (3.4 and above). To learn more, check out the [Sonar Runner Plugin](userguide/sonar_runner_plugin.html)
chapter in the Gradle user guide, and the `sonarRunner` samples in the full Gradle distribution.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to incubating Maven publishing support

Breaking changes have been made to the incubating 'maven-publish' plugin, which provides an alternative means to publish to Maven repositories.

Previously the 'maven-publish' plugin added a MavenPublication for any java component on the project, which meant that with the 'java' plugin applied no addition configuration
was required to publish the jar file. It is now necessary to explicitly add a MavenPublication to the 'publishing.publications' container. The added publication can include
a software component ['java', 'web'], custom artifacts or both. If no MavenPublication is added when using the 'maven-publish' plugin, then nothing will be published.

### Changes to incubating Ivy publishing support

Breaking changes have been made to the incubating 'ivy-publish' plugin, which provides an alternative means to publish to Ivy repositories.

- An IvyPublication must be explicitly added to the `publications` container; no publication is added implicitly.
    - If no IvyPublication is configured, nothing will be published to Ivy.
- An IvyPublication does not include any artifacts or dependencies by default; these must be added directly or via a SoftwareComponent.
    - If no artifacts are configured, an ivy.xml file will be published with no artifacts or dependencies declared.
- Removed `GenerateIvyDescriptor.xmlAction` property. The `ivy.descriptor.withXml()` method provides a way to customise the generated module descriptor.

### Changes to new PMD support

The default value for ruleset extension has changed from ["basic"] to []. We moved the default to the `Pmd` task, so everything should just work as it did before.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* Joe Sortelli - Fixed incorrect handling of `ivy.xml` where dependency configuration contained wildcard values (GRADLE-2352)
* David M. Carr
    * When JUnit tests have assumption failures, treat them as "skipped" (GRADLE-2454)
    * Documentation cleanups.
* Sébastien Cogneau - Introduce a basic distribution plugin
* Kenny Stridh
	* Allow specifying `targetJdk` for PMD code analysis (GRADLE-2106)
	* Add support for PMD version 5.0.+
* Marcin Erdmann
	* Add `build-dashboard` plugin
	* Make notify-send notifications transient in Gnome Shell
* Michael R. Maletich
	* Add `maxHeapSize` property to `FindBugs` task to allow setting the max heap size for spawned FindBugs java process
	* Add `contentsCompression` property to the `Zip` task type to specify the compression level of the archive
* Barry Pitman - Fixed Maven conversion problem (GRADLE-2645)
* Kallin Nagelberg - Fixed grammar in the `SourceSetOutput` documentation
* Klaus Illusioni - Fixed Eclipse wtp component generation issue (GRADLE-2653)
* Alex Birmingham - Fixed PMD Javadoc
* Matthieu Leclercq - Fixed the nested configuration resolution issue (GRADLE-2477)
* Dan Stine - Userguide cleanups.

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
