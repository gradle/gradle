The Gradle team is pleased to announce Gradle 2.13. This release brings many exciting execution performance improvements, community PRs and a new feature for Tooling API users.

Our commitment to improving the developer experience with each release continues. For Gradle 2.13, we've targeted performance improvements during Gradle's execution phase, where we have measured **10-25%** improvements to build time in our performance tests. No changes to your build script are necessary to start taking advantage of these improvements.

We've improved [Gradle TestKit](userguide/test_kit.html), so that plugin authors no longer need to add boilerplate to their build scripts. The [development plugin](userguide/javaGradle_plugin.html) automatically adds the necessary configuration to make it easier to test Gradle plugins.

The [Gradle Community](you) has contributed many fixes and new features in this release.  The Checkstyle and FindBugs plugins now allow you to customize their HTML reports with stylesheets.
The [Signing plugin](userguide/signing_plugin.html) supports OpenPGP subkeys, so you can keep your master signing keys safely off your CI server. These are just some of the great contributions we've received.

[As we announced on our forums](https://discuss.gradle.org/t/exciting-new-buildship-features-coming-up/14728), we're introducing a new way of putting multiple Gradle builds together with [Eclipse Buildship](http://projects.eclipse.org/projects/tools.buildship). We're calling this feature **Composite Build**.
A composite build allows you to combine multiple Gradle builds and replace external binary dependencies with project dependencies as if you were using a single multi-project build. For projects that use multiple distinct Gradle builds, this will allow you to mix and match your separate builds into one build in a very flexible way.
Eventually, composite builds will allow you to use different versions of Gradle in each build and execute tasks across projects in the composite from the command-line.  Gradle 2.13 has an initial implementation, so Tooling API users (i.e., IDEs) can start building upon it.

Upgrade to Gradle 2.13 and let us know what you think.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster Gradle builds

A number of performance improvements have been targeted at Gradle's [execution phase](userguide/build_lifecycle.html#sec:build_phases).
This is part of the build lifecycle where Gradle executes the tasks defined in a build.

Improvements introduced in this release:

* A performance bottleneck when reading `gradle.properties` has been removed.
* Test report generation is now done in parallel.
* Forked processes start up faster because they load fewer JAR files.
* There is less communication overhead between Gradle and worker processes.
* Compiled build scripts are more likely to be reused from the cache.
* Console messages are rendered more efficiently to prevent unnecessary updates and flickering.

Performance tests that run as part of Gradle's build pipeline have demonstrated up to a 25% improvement in build time.  For example, the
build time for a full assemble of a 500 project build has been reduced by 10% and a partial assemble of the same 500 project build has been
reduced by 18%. The time for assemble and test of a 25 project build has been reduced by 25%.

No change is required to build scripts to leverage these performance improvements.

### Convenient testing of plugins with Gradle TestKit

Gradle 2.6 introduced the [Gradle TestKit](userguide/test_kit.html), which made it easier to thoroughly test Gradle plugins and build logic.
The TestKit has improved and matured with subsequent Gradle releases.

The [Java Gradle Plugin Development Plugin](userguide/javaGradle_plugin.html) now makes the plugin-under-test's implementation classpath discoverable at test time automatically.
This means you need less manual build configuration in order to test your plugin.

If you build your plugin with Gradle 2.13, you no longer need to manually inject your plugin's classpath in tests if you are testing against Gradle 2.8 or newer.
If you need to test against a version of Gradle older than 2.8, you will still need to include your plugin's classpath in the `buildscript {}` block.

See the [TestKit chapter in the Gradle User Guide](userguide/test_kit.html#sub:test-kit-automatic-classpath-injection) for more information and examples of using this new feature.

### Initial support for creating Composite Builds

Up until this release, the only way to retrieve models and execute tasks from the Tooling API was through the [ProjectConnection API](javadoc/org/gradle/tooling/ProjectConnection.html).
This API was limited to a single connection to a single project in a build (regardless if the build was multi-project or single-project). This makes it difficult and expensive to retrieve information
about each project in a build.

For Composite Build, we need a way to retrieve multiple models from a group of Gradle builds (each of which may be a single project or multiple projects). We also need a way to execute tasks in the context of a
composite.  In the future, we'll use the composite context to identify dependency substitutions that should be made and correctly wire together task dependencies.

We have introduced a new [GradleConnection API](javadoc/org/gradle/tooling/connection/GradleConnection.html), which will eventually replace `ProjectConnection`.

We have also introduced [ProjectIdentifier](javadoc/org/gradle/tooling/model/ProjectIdentifier.html) and [BuildIdentifier](javadoc/org/gradle/tooling/model/BuildIdentifier.html) model types. These types will be used
to correlate results from a `GradleConnection` back to the appropriate Gradle build or Gradle project.

`GradleConnector` remains the main entry point into the Tooling API. Samples of using the `GradleConnection` API are available in the Gradle distribution (`samples/tooling-api/composite-.*`) and in the Javadoc for `GradleConnection`.

### Customized HTML reports for Checkstyle and FindBugs

The HTML reports generated by the Checkstyle and FindBugs plugins can now be customized with XSLT stylesheets.

Sample stylesheets are available from each tool's website.

- [Checkstyle XSL](https://github.com/checkstyle/contribution/tree/master/xsl)
- [FindBugs XSL](https://github.com/findbugsproject/findbugs/tree/master/findbugs/src/xsl)

See the documentation for the [Checkstyle](userguide/checkstyle_plugin.html#sec:customize_xsl) and [FindBugs](userguide/findbugs_plugin.html#sec:customize_xsl) plugins for more details.

### Support for new Groovydoc flags

[Groovy 2.4.6](http://www.groovy-lang.org/changelogs/changelog-2.4.6.html) adds two new flags to its Groovydoc command.

Enabling these flags will cause [Groovydoc](dsl/org.gradle.api.tasks.javadoc.Groovydoc.html) to produce documentation pages [without a timestamp or Groovy version](https://issues.apache.org/jira/browse/GROOVY-7612).
This means that it will be harder to determine when a page was generated or which version of Groovy was used to produce the pages, but documentation pages will remain unchanged across Groovy versions unless the content really changes.

You must be using Groovy 2.4.6 to use these flags. When using the [`groovy` plugin](userguide/groovy_plugin.html), you can enable the flags as follows:

    dependencies {
        compile 'org.codehaus.groovy:groovy-all:2.4.6'
    }

    groovydoc {
        noTimestamp = true
        noVersionStamp = true
    }

The flags are ignored for older versions of Groovy prior to 2.4.6.

### Signing with OpenPGP subkeys

OpenPGP supports a type of subkey, which are like normal keys, except they're bound to a master key pair.

These subkeys can be stored independently of master keys. OpenPGP subkeys can also be revoked independently of the master keys, which makes key management easier.
You only need the subkey for signature operations, which allows you to deploy only your signing subkey to a CI server.

The [Signing plugin](userguide/signing_plugin.html#sec:subkeys) now supports subkeys, see the documentation for more details.

<!--
## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

<!--
### Example breaking change
-->

### Delete no longer follows symlinks

The [Delete](dsl/org.gradle.api.tasks.Delete.html) task will no longer follow symlinks by default and
<a href="dsl/org.gradle.api.Project.html#org.gradle.api.Project:delete(java.lang.Object[])">project.delete(paths)</a> will not follow symlinks at all.
This was done to prevent issues where Gradle would attempt to delete files outside of Gradle's build directory (e.g. NPM installed in a user-writeable location).

Previous versions of Gradle would follow symlinks when deleting files. If you need the `Delete` task to follow symlinks set `followSymlinks = true`.
If you need `project.delete()` to follow symlinks, replace it with [ant.delete()](https://ant.apache.org/manual/Tasks/delete.html).

### Project Dependencies now include classifiers and all artifacts in generated POM files

TODO: Review

Previously, dependencies between projects in a multi-project gradle build, would not respect classifiers when generating `pom` files for publishing to maven
repositories. It is possible that this caused gradle users to create workarounds and post-processing of `pom` files before archiving them. If the `pom` files
generated by this release of Gradle for your project are broken compared to previous releases this is probably why. Make sure your project dependencies,
specified in one of thes two forms:

    dependencies {
        <configuration> project(':<project>')
        <configuration> project(path: ':<project>', configuration: '<targetConfiguration>')
    }

Is actually pulling in exactly what you want.

Before this change, generating the `pom` file for uploading the archives generated by a build, would NOT include any classifiers specified on the default artifacts
produced by the project on which the current project was depending.  In addition, ONLY the single default artifact exported in a given configuration would be
included in the generated `pom`.

After this change, all of the artifacts (instead of just the default one) exported in a given configuration on the project will be included in the generated `pom`,
and any artifact which has a classifier specified will also show that classifier in the generated `pom`.

The best way to understand what is changed is through an example.  Suppose you have two projects in your build `project1` depends on `project2`'s `default`
configuration in the `compile` configuration, and on `project2`'s `testCompile` configuration in its `testRuntime` configuration. `project2` adds the `defaultJar`
classifier to the default jar file generated by the project, and creates a `testJar` which contains the test classes used to test `project2` and adds that jar
to the `testRuntime` configuration's artifacts. Now, since the `testRuntime` extends from the `compile` runtime, it should contain both the default jar and the
test jar.

    allprojects {
        group = 'org.gradle.test'
        version = 1.9
    }

    subprojects {
        apply plugin: 'java'
        apply plugin: 'maven'

        uploadArchives {
            repositories {
                mavenDeployer {
                    repository(url: "file:///$rootProject.projectDir/maven-repo")
                }
            }
        }
    }

    project(':project1') {
        dependencies {
            compile project(':project2')
            testCompile project(path: 'project2', configuration: 'testRuntime')
        }
    }

    project(':project2') {
        jar {
            classifier = 'defaultJar'
        }

        task testJar(type: Jar, dependsOn: classes) {
            from sourceSets.test.output
            classifier = 'tests'
        }

        artifacts {
            testRuntime  testJar
        }
    }

In the previous version of Gradle, the resulting `project1-1.9.pom` would have included these dependencies (comments added):

    ...
    <dependencies>
     <dependency>
        <groupId>org.gradle.test</groupId>
        <artifactId>project2</artifactId>
        <version>1.9</version>
        <!-- Missing the 'defaultJar' classifier -->
        <scope>compile</scope>
      </dependency>
     <dependency>
        <groupId>org.gradle.test</groupId>
        <artifactId>project2</artifactId>
        <version>1.9</version>
        <!-- Missing the 'defaultJar' classifier -->
        <scope>test</scope>
      </dependency>
      <!-- Missing the dependency on 'testJar' completely -->
    </dependencies>
    ...

In this version of Gradle, the POM file for `project1` would have included this dependency:

    ...
    <dependencies>
      <dependency>
        <groupId>org.gradle.test</groupId>
        <artifactId>project2</artifactId>
        <version>1.9</version>
        <classifier>defaultJar</classifier>
        <scope>compile</scope>
      </dependency>
      <dependency>
        <groupId>org.gradle.test</groupId>
        <artifactId>project2</artifactId>
        <version>1.9</version>
        <classifier>defaultJar</classifier>
        <scope>test</scope>
      </dependency>
      <dependency>
        <groupId>org.gradle.test</groupId>
        <artifactId>project2</artifactId>
        <version>1.9</version>
        <classifier>tests</classifier>
        <scope>test</scope>
      </dependency>
    </dependencies>
    ...

This improved behavior may mean you no longer need workarounds in your build scripts, but you might have to change those same build scripts so that the existing
workarounds don't break the `pom` or add duplicate entries.

### Task input property names now follow the JavaBean specification

Task input properties now correctly follow the JavaBean specification. For most properties, this will have no effect.
For some properties that have unusual capitalization, you may need to use a different name when accessing the property from the Map of input properties.
Input properties are now addressed with the same names in validation error messages, DSL and through `getInputs().getProperties()`.

For example:

- If you have a getter named `getcFlags` and you have marked it as an `@Input`,
    - This property would be named `cFlags` according to the JavaBean specification.
    - You can reference this as `cFlags` or `getcFlags()` in the DSL.
    - It will also be available from the Map of input properties as `getInputs().getProperties().get("cFlags")` or `inputs.properties.cFlags`.
    - This behavior has not changed.
- If you have a getter named `getCFlags` and you have marked it as an `@Input`,
    - This property would be named `CFlags` according to the JavaBean specification.
    - You can reference this as `CFlags` or `getCFlags()` in the DSL.
    - It will also be available from the Map of input properties as `getInputs().getProperties().get("CFlags")` or `inputs.properties.CFlags`.
    - **This behavior is different.** Previously, the property would have been found with the name `cFlags` (e.g., `getInputs().getProperties().get("cFlags")` or `inputs.properties.cFlags`).
- Input properties with getters like `getURL()` are now found with `getInputs().getProperties().get("URL")` or `inputs.properties.URL` instead of the erroneous `uRL`.

Most builds are unlikely to be using the names of the properties available from `getInputs().getProperties()`.

### JaCoCo version upgrade to 0.7.6

The [JaCoCo plugin](userguide/jacoco_plugin.html) uses [JaCoCo 0.7.6](http://eclemma.org/jacoco/trunk/doc/changes.html) by default.

To downgrade to the previous version:

    jacoco {
        toolVersion = "0.7.1.201405082137"
    }

### Apache Commons Collections upgrade to 3.2.2

Gradle now bundles [Apache Commons Collections 3.2.2](https://commons.apache.org/proper/commons-collections/release_3_2_2.html).

This is an internal dependency, but `buildSrc` plugins may inadvertently use classes from this dependency.

This was upgraded to fix a security vulnerability.

### Apache Ant upgrade to 1.9.6

Gradle now bundles [Apache Ant 1.9.6](https://archive.apache.org/dist/ant/RELEASE-NOTES-1.9.6.html) instead of Apache Ant 1.9.3.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Alexander Afanasyev](https://github.com/cawka) - allow configuring java.util.logging in tests ([GRADLE-2524](https://issues.gradle.org/browse/GRADLE-2524))
* [Randall Becker](https://github.com/rsbecker) - bypass ulimit in NONSTOP os
* [Bryan Bess](https://github.com/squarejaw) - fix documentation layout for scala and groovy plugins
* [Thomas Broyer](https://github.com/tbroyer) - add design doc for better/built-in Java annotation processing support
* [Schalk Cronjé](https://github.com/ysb33r) - update docs about `getFile()` and filters
* [Jeffrey Crowell](https://github.com/crowell) - upgrade Apache Commons Collections to v3.2.2
* [Ryan Ernst](https://github.com/rjernst) - support system properties from the command-line for buildSrc builds ([GRADLE-2475](https://issues.gradle.org/browse/GRADLE-2475))
* [Endre Fejese](htts://github.com/fejese) - fix a typo in a javadoc comment
* [Ethan Hall](https://github.com/ethankhall) - make Delete tasks not follow symlinks ([GRADLE-2892](https://issues.gradle.org/browse/GRADLE-2892))
* [Alpha Hinex](https://github.com/alphahinex) - upgrade to Ant 1.9.6
* [Jeremie Jost](https://github.com/jjst) - fix a typo in the application plugin documentation
* [Paul King](https://github.com/paulk-asert) - support groovydoc's `noTimestamp` and `noVersionStamp` properties
* [Maciej Kowalski](https://github.com/fkowal) - move `DEFAULT_JVM_OPTS` after `APP_HOME` in application plugin
* [Denis Krapenko](https://github.com/dkrapenko) - support multiple values for some command-line options
* [Guillaume Laforge](https://github.com/glaforge) - remove extraneous `public` keywords from build.gradle
* [Evgeny Mandrikov](https://github.com/Godin) - upgrade default JaCoCo version to 0.7.6
* [Raymond Navarette](https://github.com/rnavarette) - add classifiers for project dependencies in generated POM files ([GRADLE-3030](https://issues.gradle.org/browse/GRADLE-3030))
* [Pierre-Etienne Poirot](https://github.com/pepoirot) - support for stylesheets with FindBugs and Checkstyle
* [Oliver Reissig](https://github.com/oreissig) - improve error message when `tools.jar` is not found
* [Andrew Reitz](https://github.com/pieces029) - fix a broken link to the groovy documentation
* [Baruch Sadogursky](https://github.com/jbaruch) - add jcenter repository example to the userguide
* [Marcin Zajączkowski](https://github.com/szpak) - Support for OpenPGP subkeys in the signing plugin, add Project.findProperty method, fix a test which was failing against the OpenJDK

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
