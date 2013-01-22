## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
### Example new and noteworthy
-->

### Improved Sonar plugin

The Sonar plugin has received a major overhaul. It is now based on the [Sonar Runner]
(http://docs.codehaus.org/display/SONAR/Analyzing+with+Sonar+Runner), the new and officially supported
way to invoke Sonar analysis. This makes the plugin compatible with Sonar 3.4 and future Sonar versions.
In addition, the Sonar plugin's object model has been updated to cover JaCoCo and debug settings, and
properties like `sonar.database.password` can now easily be set from the command line. Read on in the
revised [Sonar chapter](userguide/sonar_plugin.html) of the Gradle user guide.

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
It is now possible to change group, name and/or version of the dependency when it is resolved.
Previously, only version could be updated just before the dependency is resolved.

    configurations.all {
        resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (details.requested.group == 'groovy-all') {
                details.useTarget group: details.requested.group, name: 'groovy', version: details.requested.version
            }
        }
    }

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
* The task dependencies that reach out to a different project are now fully supported:


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

### Improvements to the 'maven-publish' plugin

The incubating '`maven-publish`' plugin is an alternative to the existing '`maven`' plugin, and will eventually replace it. This release adds more power to the plugin, including
 the ability to choose a software component to publish, customise the set of published artifacts, and generate a POM file without publishing.

For complete details on the '`maven-publish`' plugin, check out the [user guide chapter](userguide/publishing-maven.html) as well as the
[DSL reference](dsl/org.gradle.api.publish.maven.MavenPublication.html).

#### Choose a software component to publish

Gradle 1.5 includes the concept of a Software Component, which defines something that can be produced by a Gradle project, like a Java Library or a Web Application.
The 'maven-publish' plugin is component-aware, simplifying the process of publishing a component, which defines the set of artifacts and dependencies for publishing.
Presently, the set of components available for publishing is limited to 'java' and 'web', added by the 'java' and 'war' plugins respectively.

Publishing the 'web' component will result in the war file being published with no runtime dependencies (dependencies are bundled in the war):

    apply plugin: 'war'
    apply plugin: 'maven-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    publishing {
        repositories {
            maven {
                url 'http://mycompany.org/repo'
            }
        }
        publications {
            mavenWeb(MavenPublication) {
                from components.web
            }
        }
    }

#### Customise artifacts published

This release introduces an API/DSL for customising the set of artifacts to publish to a Maven repository. This DSL allows gives a Gradle build complete control over which artifacts
are published, and the classifier/extension used to publish them.

    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    task sourceJar(type: Jar) {
        from sourceSets.main.allJava
    }

    publishing {
        repositories {
            maven {
                url 'http://mycompany.org/repo'
            }
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
                }
            }
        }
    }

Be sure to check out the [DSL reference](dsl/org.gradle.api.publish.maven.MavenPublication.html) for complete details on how the set of artifacts can be customised.

#### Generate POM file without publishing

Pom file generation has been moved into a separate task, so that it is now possible to generate the Maven Pom file without actually publishing your project. All details of
the publishing model are still considered in Pom generation, including `components`, custom `artifacts`, and any modifications made via `pom.withXml`.

The task for generating the Pom file is of type [`GenerateMavenPom`](dsl/org.gradle.api.publish.maven.tasks.GenerateMavenPom.html), and it is given a name based on the name
of the publication: `generatePomFileFor<publication-name>Publication`. So in the above example where the publication is named 'mavenCustom',
the task will be named `generatePomFileForMavenCustomPublication`.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to new Maven publishing support

Breaking changes have been made to the new, incubating, Maven publishing support.

Previously the 'maven-publish' plugin added a MavenPublication for any java component on the project, which meant that with the 'java' plugin applied no addition configuration
was required to publish the jar file. It is now necessary to explicitly add a MavenPublication to the 'publishing.publications' container. The added publication can include
a software component ['java', 'web'], custom artifacts or both. If no MavenPublication is added when using the 'maven-publish' plugin, then nothing will be published.

### Changes to new Ivy publishing support

- Removed GenerateIvyDescriptor.xmlAction property. Set it on ivy.descriptor.withXml
- Publication must be explicitly added
- Nothing is included in publication by default
   - Can specify component to publish
   - Can specify additional artifacts to publish

## Switch to Sonar Runner

While every effort has been made to keep backwards compatibility with earlier versions of the Sonar plugin, the underlying switch to the
[Sonar Runner](http://docs.codehaus.org/display/SONAR/Analyzing+with+Sonar+Runner) may cause some differences in behavior.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* Joe Sortelli - Fixed incorrect handling of ivy.xml where dependency configuration contained wildcard values (GRADLE-2352)

* David M. Carr - When JUnit tests have assumption failures, treat them as "skipped" (GRADLE-2454)

* Marcin Erdmann - Make notify-send notifications transient in Gnome Shell



We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
