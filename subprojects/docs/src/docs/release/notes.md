## New and noteworthy

Here are the new features introduced in this Gradle release.

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

## Incubating features

Incubating features are intended to be used, but not yet guaranteed to be backwards compatible.
By giving early access to new features, real world feedback can be incorporated into their design.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the new incubating features or changes to existing incubating features in this Gradle release.

### Configuration on demand

* respects 'external' task dependencies

### Choose a component to publish with the new ‘maven-publish’ plugin

The incubating ‘maven-publish’ plugin is an alternative to the existing ‘maven’ plugin, and will eventually replace it. This release adds the ability to choose which
software component should be published to a Maven repository. Presently, the set of components available for publishing is limited to 'java' and 'web', added by the 'java'
and 'war' plugins respectively.

Publishing the 'web' component to a Maven repository looks like…

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

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* Some Person - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
