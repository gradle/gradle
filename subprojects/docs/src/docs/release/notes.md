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

### Cleaner build scripts with `plugins.withId`

New <a href="javadoc/org/gradle/api/plugins/PluginContainer.html#withId(java.lang.String, org.gradle.api.Action)">plugins.withId()</a>
enables referring to plugins more conveniently.
In previous releases, some times it was necessary for the client of a custom plugin to know the fully qualified type of the plugin:

    import com.my.custom.InterestingPlugin
    plugins.withType(InterestingPlugin) { ...

    //now possible, given InterestingPlugin uses "interesting-plugin" id:
    plugins.withId("interesting-plugin") { ...

Benefits of the new API for the users:

* less pressure to know the exact java class of the plugin
* build scripts are more likely to be decoupled from the plugin types (e.g. it's easier for plugin author to refactor/change the type)
* some build scripts are cleaner and more consistent because plugins are applied by 'id' and are also filtered by 'id'

### Support for Ivy and Maven repositories with SFTP scheme

In addition to `file`, `http` and `https`, Ivy and Maven repositories now also support the `sftp` transport scheme. Currently, authentication with the SFTP server only works based on
providing username and password credentials.

Here is an example usage of resolving dependencies from a SFTP server with Ivy:

    apply plugin: 'java'

    repositories {
        ivy {
            url 'sftp://127.0.0.1:22/repo'
            credentials {
                username 'sftp'
                password 'sftp'
            }
            layout 'maven'
        }
    }

    dependencies {
        compile 'org.apache.commons:commons-lang3:3.3.1'
    }

Resolving dependencies from a SFTP server with Maven works accordingly. Publishing is not supported yet. The following example demonstrates the use case:

    apply plugin: 'java'

    repositories {
        maven {
            url 'sftp://127.0.0.1:22/repo'
            credentials {
                username 'sftp'
                password 'sftp'
            }
        }
    }

    dependencies {
        compile 'org.apache.commons:commons-lang3:3.3.1'
    }

Here is an example usage of publishing an artifact to an Ivy repository hosted on a SFTP server:

    apply plugin: 'java'
    apply plugin: 'ivy-publish'

    version = '2'
    group = 'org.group.name'

    publishing {
        repositories {
            ivy {
                url 'sftp://127.0.0.1:22/repo'
                credentials {
                    username 'sftp'
                    password 'sftp'
                }
                layout 'maven'
            }
        }
        publications {
            ivy(IvyPublication) {
                from components.java
            }
        }
    }

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

### Source and Javadoc artifacts declared in ivy.xml are recognised by IDE plugins

The Gradle `eclipse` and `idea` plugins are able to find and download the source artifacts for external dependencies, and link
these artifacts into the generated IDE files.

In addition to the conventional classifier-based scheme for locating source and javadoc artifacts, Gradle will now recognise
artifacts declared in a specific configuration of an `ivy.xml` file.

For an IDE project that references an external module located in an `ivy` repository, Gradle will now include:

* Source artifacts declared in a `sources` configuration in `ivy.xml`
* Javadoc artifacts declared in a `javadoc` configuration in `ivy.xml`
* Source artifacts conventionally named with a `sources` classifier: eg. 'module-1.0-sources.jar'
* Javadoc artifacts conventionally named with a `javadoc` classifier: eg. 'module-1.0-javadoc.jar'

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

### Deprecated method in Tooling API

The [`org.gradle.tooling.model.Task.getProject()`](javadoc/org/gradle/tooling/model/Task.html#getProject()) method is now deprecated and
can throw `UnsupportedMethodException`. There is no replacement as it is expected that the caller has a reference to project prior calling to this method.

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
