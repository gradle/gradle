## New and noteworthy

Here are the new features introduced in this Gradle release.

### New API for artifact resolution (i)

Gradle 1.12 introduces a new, incubating API for resolving component artifacts. With this addition, Gradle now offers separate dedicated APIs for resolving
components and artifacts. (Component resolution is mainly concerned with computing the dependency graph, whereas artifact resolution is
mainly concerned with locating and downloading artifacts.) The entry points to the component and artifact resolution APIs are `configuration.incoming` and
`dependencies.createArtifactResolutionQuery()`, respectively.

TODO: This API examples are out of date. Add new tested samples to the Javadoc or Userguide and link instead

Here is an example usage of the new API:

    def query = dependencies.createArtifactResolutionQuery()
        .forComponent("org.springframework", "spring-core", "3.2.3.RELEASE")
        .forArtifacts(JvmLibrary)

    def result = query.execute() // artifacts are downloaded at this point

    for (component in result.components) {
        assert component instanceof JvmLibrary
        println component.id
        component.sourceArtifacts.each { println it.file }
        component.javadocArtifacts.each { println it.file }
    }

    assert result.unresolvedComponents.isEmpty()

Artifact resolution can be limited to selected artifact types:

    def query = dependencies.createArtifactResolutionQuery()
        .forComponent("org.springframework", "spring-core", "3.2.3.RELEASE")
        .forArtifacts(JvmLibrary, JvmLibrarySourcesArtifact)

    def result = query.execute()

    for (component in result.components) {
        assert !component.sourceArtifacts.isEmpty()
        assert component.javadocArtifacts.isEmpty()
    }

Artifacts for many components can be resolved together:

    def query = dependencies.createArtifactResolutionQuery()
        .forComponents(setOfComponentIds)
        .forArtifacts(JvmLibrary)

So far, only one component type (`JvmLibrary`) is available, but others will follow, also for platforms other than the JVM.

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
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Custom TestNG listeners are applied before Gradle's listeners

This way the custom listeners are more robust because they can affect the test status.
There should be no impact of this change because majority of users does not employ custom listeners
and even if they do healthy listeners will work correctly regardless of the listeners' order.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Marcin Erdmann](https://github.com/erdi) - Support an ivy repository declared with 'sftp' as the URL scheme

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
