The Gradle team is pleased to announce Gradle 2.14.

The team is always working to improve the overall performance of Gradle, and this release does not disappoint. Gradle's configuration time has dropped considerably through the application of some careful optimizations. The Gradle build itself has seen a **50% reduction in configuration time**. You'll see the biggest impact on multi-project builds, but everyone will benefit to some degree. This is reason enough to upgrade.

In other news, the [Gradle daemon](userguide/gradle_daemon.html) has become self-aware. Not in the AI sense, sadly, but you will find the daemon to be much more **robust and resource-efficient** now because it monitors its memory usage. It's much less likely that you will need to manually kill daemons.

There are several other quality-of-life improvements for various users, including **IntelliJ IDEA support for Play framework projects**, and a fix that makes authoring plugins easier. In addition, we mentioned in our 2.13 release notes that **composite build support** is coming in Buildship 2.0. This amazing new feature will require Gradle 2.14 as a minimum version.

Finally, it's time to start preparing for the departure of an old friend. Gradle 2.14 sees the **deprecation of Java 6**. It has been with us a long time now, reaching its official End of Life in 2013. You will still be able to use Java 6 with Gradle 2.14 and future versions, but you won't be able to run Gradle 3.0 on it.

Enjoy the new version and let us know what you think!


## New and noteworthy

Here are the new features introduced in this Gradle release that impact all Gradle users.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Faster Gradle builds

Gradle 2.14 brings significant improvements in configuration time. The Gradle build itself has seen a 50% reduction in its startup time. You should see similar improvements for complex builds or those with lots of subprojects. But even small projects will see a noticeable improvement.

If you're interested in how this was done, here are some of the optimizations that were implemented:

- Reduced the time taken to create tasks
- Made the services and dependency injection infrastructure more efficient, so that applying plugins takes less time
- Improved memory usage, resulting in less garbage collection
- More efficient dynamic property and method lookup
- Removed dynamic code constructs in a lot of places in favor of static ones

### More robust and memory-efficient Daemon

The Gradle daemon now actively monitors garbage collection to identify when it's running out of resources. When this happens, Gradle will restart the daemon as soon as the current build finishes.

The monitoring is enabled by default, but you can disable it by configuring this project property:

    org.gradle.daemon.performance.enable-monitoring=false

If you want to disable daemon monitoring for all projects, add this setting to _«USER_HOME»/.gradle/gradle.properties_. Otherwise, just add it to a _gradle.properties_ file in the root of a project.

In addition to this self-monitoring feature, Gradle now attempts to limit its overall consumption of system resources by shutting down daemons that are no longer in use. Gradle previously stopped daemons after a fixed period of inactivity. In 2.14, idle daemons will expire more quickly when memory pressure is high, thus freeing up system resources faster than before.

### Deprecation of Java 6 support 

Java 6 reached end of life in February 2013 and support for this version is becoming more difficult as libraries and tools move to Java 7 and later. As a result, Gradle 2.14 will be the last version of Gradle that will run on Java 6, with Gradle 3.0 requiring Java 7 at a minimum.

Please note that while it won't be possible to run Gradle itself on Java 6, your builds will still be able to target Java 6 as a runtime platform with the appropriate `sourceCompatibility` and `targetCompatibility` settings.

### Play/IDEA Integration

If you're using the [Play plugin](userguide/play_plugin.html#play_ide) along with IntelliJ IDEA, you'll be glad to learn that you no longer have to manually configure your projects in the IDE. Instead, you can now apply the [IDEA plugin](userguide/idea_plugin.html), run the `idea` task, and load the resulting project file into the IDE. Much easier!

### Easier publication and consumption of plugins to and from custom repositories

Gradle 2.1 first introduced the `plugins {}` block for specifying the plugins that your build uses, but this only worked for plugins that were published to the Gradle plugin portal. With the release of 2.14, you can now specify custom repositories via the new `pluginRepositories {}` block. This means that you can take advantage of the `plugins {}` syntax for your organization's private plugins.

To enable this feature, you add the new syntax to the project's _settings.gradle_ file, like so:

    pluginRepositories {
        maven {
            url 'https://private.mycompany.com/m2'
        }
        gradlePluginPortal()
        ivy {
            url 'https://repo.partner.com/m2'
        }
    }

The `plugins {}` block looks the same as before, it's just that the plugins can be resolved from different repositories:

    plugins {
        id 'java'                               // Resolved from the Gradle Distribution.
        id 'org.public.plugin' version '1.2.3'  // Resolved from the Gradle Plugin Portal.
        id 'com.mycompany.secret' version '1.0' // Resolved from your private Maven repository.
        id 'com.partner.helpful' version '2.0'  // Resolved from your partner's Ivy repository.
    }

The repositories are consulted in the order in which they were specifed in the `pluginRepositories {}` block.

**Note** The new plugin repository definitions **do not work** for the `apply plugin: <name>` syntax. By extension, that also means they won't work with the `allprojects {}`/`subprojects {}` feature. 

For this feature to work with your own plugins, you will need to publish them alongside a special [Plugin Marker Artifact](userguide/plugins.html#sec:plugin_markers). Don't worry, this step happens automatically when you use either the [_maven-publish_](userguide/publishing_maven.html) or [_ivy-publish_](userguide/publishing_ivy.html) plugins
with the [_java-gradle-plugin_](userguide/javaGradle_plugin.html) plugin.

To demonstrate how easy it is to publish a plugin with the new marker artifact, here's an example plugin build:

    plugins {
        id 'java-gradle-plugin'
        id 'maven-publish'
    }

    version = 1.0
    group = "com.mycompany"

    gradlePlugin {
        plugins {
            secret {
                id = 'com.mycompany.secret'
                implementationClass = 'org.mycompany.plugins.TopSecretPlugin'
            }
        }
    }

    publishing {
        repositories {
            maven {
                url 'https://private.mycompany.com/m2'
            }
        }
    }

When you run `./gradlew publish`, the sample plugin above, _com.mycompany:secretPlugin:1.0_, and the plugin marker artifact, _com.mycompany.secret:com.mycompany.secret.gradle.plugin:1.0_, will both be published to the configured custom Maven repository.

You can learn more about defining custom plugin repositories in the [user guide](userguide/plugins.html#sec:custom_plugin_repositories).

### Configuration of character encoding when filtering files in a CopySpec

By default, any file filtering performed by a `CopySpec` uses the default platform character encoding to read and write the filtered files. This can cause problems if the files are encoded with something else, such as _UTF-8_.

You can now control this behavior by specifying the character encoding to use during the filtering process on a per-`CopySpec` basis:

    task filter(type: Copy) {
        from 'some/place'
        into 'somewhere/else'
        expand(version: project.version)
        filteringCharset = 'UTF-8'
    }

See the “[Filtering files](userguide/working_with_files.html#sec:filtering_files)” section of the “Working with files” chapter in the user guide for more information and examples of how to use this new feature.

We're grateful to [Jean-Baptiste Nizet](https://github.com/jnizet) for his contribution of this feature.

### Support for inter-project dependency classifiers 

When you're publishing artifacts via the `maven` plugin, Gradle has to map inter-project dependencies to `<dependency>` declarations in the generated POM file. This normally works fine, but Gradle was previously ignoring the `classifier` attribute on any project artifacts, which resulted in a single `<dependency>` entry in the POM for those inter-project dependencies. This meant that the published POM was incorrect and the project could not be properly resolved from a Maven repository.

The mapping of inter-project dependencies into POM dependency declarations has been improved in 2.14. Gradle will now produce correct POM files for the following cases`:

- The depended-on project configuration produces a single artifact with a classifier. In this case, the `classifier` attribute will be included in the `<dependency>` entry.
- The depended-on project configuration produces multiple artifacts. In this case, a `<dependency>` entry with the appropriate `classifier` attribute will be created for each artifact.

As an example, consider the following Gradle project definitions:

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

The generated POM file for `project1` will now include these dependency entries:

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
        <classifier>tests</classifier>
        <scope>test</scope>
      </dependency>
    </dependencies>
    ...

Prior to 2.14, the POM would only contain a single `<dependency>` entry for 'project2', omitting the `classifier` attribute altogether.

Many thanks to [Raymond Navarette](https://github.com/rnavarette) for contributing this feature.

### Better control over Ant message logging

In previous versions of Gradle, the mapping of Ant message priorities to Gradle logging levels was fixed and the default _LIFECYCLE_ log level was set to between Ant's "warn" and "info" priorities. This meant that to show output from Ant tasks logged at the common "info" priority, the Gradle logging level had to be set to _INFO_ or _DEBUG_, potentially exposing unwanted output. Similarly, to suppress unwanted messages from Ant tasks, the Gradle logging level would need to be set to a lower verbosity, potentially suppressing other desirable output.

You can now control the level of Ant logging by changing the message priority that maps to the Gradle LIFECYCLE logging level, like so:

    ant {
        lifecycleLogLevel = "INFO"
    }

This causes any Ant messages logged at the specified priority - "info" in this case - to be logged at Gradle's LIFECYCLE logging level. Any messages logged at a higher priority than "info" will also be logged at LIFECYCLE level. Messages logged at a lower priority than the specified priority will be logged at INFO level or below.

## New for plugin authors

There is only one change that directly impacts plugin authors, but it's an important one.

### Better isolation of internal Gradle classes with `gradleApi()`

In previous versions, Gradle's internal implementation dependencies were visible to plugins at build (i.e. compile and test) but not at runtime.
This caused problems when plugins depended on libraries that conflicted with Gradle's internal dependencies, such as Google Guava.

This has been fixed in Gradle 2.14.
Only classes that are part of Gradle's public API are now visible to plugins at build time, which more accurately represents the runtime environment for the plugins.

This change fixes both GRADLE-3433 and GRADLE-1715, and requires no changes to your build scripts.

## New for tooling API consumers

There is just one minor change to the Tooling API, related to composite build support.

### New identifier properties for IDE Tooling API models

New identifier properties on `EclipseProject` and `IdeaModule` make it easier to find the IDE model corresponding to a project dependency. Tools should now use [`EclipseProject.getIdentifier()`](javadoc/org/gradle/tooling/model/eclipse/HierarchicalEclipseProject.html#getIdentifier--) and [`EclipseProjectDependency.getTarget()`](javadoc/org/gradle/tooling/model/eclipse/EclipseProjectDependency.html#getTarget--) for Eclipse models, and [`IdeaModule.getIdentifier()`](javadoc/org/gradle/tooling/model/idea/IdeaModule.html#getIdentifier--) and [`IdeaModuleDependency.getTarget()`](javadoc/org/gradle/tooling/model/idea/IdeaModuleDependency.html#getTarget--) for IDEA models.

As mentioned in the deprecation notes, these properties supersede `EclipseProjectDependency.getTargetProject()` and  `IdeaModuleDependency.getDependencyModule()`.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to our backwards compatibility policy.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### `StartParameter.consoleOutput` property

The `StartParameter.consoleOutput` property has been promoted and is now stable.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### Setting the log level from build logic

The ability to set the log level from build logic using [LoggingManager.setLevel()](javadoc/org/gradle/api/logging/LoggingManager.html#setLevel%28org.gradle.api.logging.LogLevel%29)
is now deprecated and scheduled for removal in the next release of Gradle.  If you are using this feature to control logging of messages from Ant
tasks, please use the [AntBuilder.setLifecycleLogLevel()](javadoc/org/gradle/api/AntBuilder.html#setLifecycleLogLevel%28java.lang.String%29) method instead.

### Support for running Gradle on Java 6

Running Gradle using Java 6 is now deprecated, and support will be removed in Gradle 3.0.

It will continue to be possible to build JVM based projects for Java 6 using Gradle 3.0, by running Gradle using Java 7 or newer and configuring your build to use Java 6 to compile, test and run your code.

Using the Gradle Tooling API and TestKit on Java 6 is also deprecated, and support will be removed in Gradle 3.0.

### `Test` task support for running tests on Java 5

Support for using Java 5 with the `Test` task type is now deprecated and support will be removed in Gradle 3.0.

### `StartParameter.colorOutput` property

The `StartParameter.colorOutput` property has been deprecated and will be removed in Gradle 3.0. You should use the `consoleOutput` property instead.

### Tooling API support for older Gradle versions

Using the Gradle tooling API to run Gradle builds for Gradle versions 1.1 and older is now deprecated. Support for Gradle versions older than 1.2 will be removed in Gradle 3.0.

Using Gradle from a Gradle tooling API version 1.12 and older is now deprecated. Support for Gradle tooling API versions older than 2.0 will be removed in Gradle 3.0. 

### TestKit support for older Gradle versions

Using the Gradle TestKit to run Gradle build for Gradle versions 1.1 and older is now deprecated. Support for Gradle versions older than 1.2 will be removed in Gradle 3.0.

### Build comparison plugin support for older Gradle versions

Using the Gradle build comparison plugin to compare builds for Gradle versions 1.1 and older is now deprecated. Support for Gradle versions older than 1.2 will be removed in Gradle 3.0.

### Tooling API model properties

- `EclipseProjectDependency.getTargetProject()` has been deprecated, use `EclipseProjectDependency.getTarget()` instead.
- `IdeaModuleDependency.getDependencyModule()` has been deprecated, use `IdeaModuleDependency.getTarget()` instead.

### TestNG Javadoc annotations

Support for using TestNG Javadoc annotations is deprecated and will be removed in Gradle 3.0. 
Support for Javadoc annotations was removed from TestNG 5.12, in early 2010. You will be able to use these old versions of TestNG from Gradle 3.0, however you will need to use JDK annotations.

As a result of this deprecation, the following methods are also deprecated and will be removed in Gradle 3.0:

- `Test.getTestSrcDirs()`
- `Test.setTestSrcDirs()`
- `TestNGOptions.getAnnotations()`
- `TestNGOptions.setAnnotationsOnSourceCompatibility()`
- `TestNGOptions.jdkAnnotations()`
- `TestNGOptions.javadocAnnotations()`
- `TestNGOptions.getJDK_ANNOTATIONS()`
- `TestNGOptions.getJAVADOC_ANNOTATIONS()`
- `TestNGOptions.getJavadocAnnotations()`
- `TestNGOptions.isJavadocAnnotations()`
- `TestNGOptions.setJavadocAnnotations()`
- `TestNGOptions.getTestResources()`
- `TestNGOptions.setTestResources()`

### Deprecated task methods

The `setName()` and `setProject()` methods in `AbstractTask` have been deprecated and will be removed in Gradle 3.0.

## Potential breaking changes

### Gradle implementation dependencies are not visible to plugins at development time

Implementing a Gradle plugin requires use of the `gradleApi()` dependency in order to compile against Gradle classes. 
Previously, this encompassed the entire Gradle runtime including Gradle's third party dependencies (e.g. Guava). 
If the plugin depended on a library that Gradle also depended on, the behavior was unpredictable as multiple versions of classes could end up on the classpath. 
This would often manifest as unexpected compile errors or runtime linking errors.
 
Starting with Gradle 2.14, `gradleApi()` no longer exposes Gradle's implementation dependencies.
As such, the compile and test classes for plugins have changed with this release.

This is expected to be a transparent and compatible change for most plugin builds.
While Gradle's implementation dependencies were previously visible at build time, they were not at runtime.
As such, it was not possible to successfully ship a plugin that relied on access to Gradle's implementation dependencies.

However, if you were previously using Gradle implementation dependencies in the _tests_ for your plugin, you will need to add these dependencies to your build.
For example, if the tests for your plugins use the Guava version shipped by Gradle, you will know need to explicitly declare Guava as a test time dependency.
The good news though is that you are now able to choose your own version of Guava (and other Gradle internal dependencies) to use at test time, instead of being bound to the version that Gradle uses.

### Change in plugin id

`ComponentModelBasePlugin` can no longer be applied using id `component-base`. Its new id is `component-model-base`.

### Tests now respect java.util.logging.config.file by default

Previous versions of Gradle ignored the `java.util.logging.config.file` system property when running tests. Now, the system property will be honored when tests are running. This can break your tests if you are expecting the default ConsoleHandler to be used during tests, and checking expectations based on that output. If you tests begin failing with this release of Gradle, you can disable reading the logging conifguration file with a system propoerty.

    test {
        systemProperty 'org.gradle.readLoggingConfigFile' 'false'
    }
    
Support for this system property will be dropped in Gradle 3.0. Please be sure to correct the expectations in your tests if you are using a logging configuration file.

### JAR metadata and Manifest content encoding

Previous versions of Gradle used to encode JAR/WAR/EAR files metadata and Manifests content using the platform default character set instead of UTF-8. Both are bugs and have been fixed in this release, see the related fixed issues above.

Following this, merged manifests are now read using UTF-8 instead of the platform default charset.

If necessary, convenience properties have been added to [`Jar`](dsl/org.gradle.api.tasks.bundling.Jar.html), [`War`](dsl/org.gradle.api.tasks.bundling.War.html), [`Ear`](dsl/org.gradle.plugins.ear.Ear.html) tasks and both [`Manifest`](javadoc/org/gradle/api/java/archives/Manifest.html) and [`ManifestMergeSpec`](javadoc/org/gradle/api/java/archives/ManifestMergeSpec.html) types to control which character set to use when merging manifests.

In order to fall back to the old behavior you can do the following:

    jar {
        // JAR metadata
        metadataCharset = Charset.defaultCharset().name()
        // Manifest content
        manifestContentCharset = Charset.defaultCharset().name()
        manifest {
            // Merged manifest content
            from(file('path/to/some/manifest/to/merge')) {
                contentCharset = Charset.defaultCharset().name()
            }
        }
    }

### Additional POM `<dependency>` attributes generated for some project dependencies

As described above, POM files generated by the `maven` plugin now include classifiers and all artifacts for project dependencies. This improvement may break existing Gradle builds, particularly those that include a specific workaround for the previous behavior. These workarounds should no longer be required, and may need to be removed to ensure that Gradle 2.14 will create correct `<dependency>` attributes for project dependencies.

### A number of plugins were converted from Groovy to Java

The following plugins were fully converted to Java: `jacoco`, `scala`, `osgi`, `javascript`, `distribution` and `announce`.

Some other plugins were partially converted to Java, keeping tasks types as Groovy classes: `init`, `checkstyle`, `codenarc`, `findbugs`, `pmd`, `jdepend`, `java`, `war`, `ear`, `application`, `signing`, `comparison`, `idea` and `eclipse`. For the latter two, plugin types have also been kept in Groovy.

As a result, existing builds that use converted types as base types might see different behaviour.

### Source and target compatibility options are always passed to the Java compiler

If no target/sourceCompatibility option is set on for the Java plugin, then the version of the
JVM Gradle is running on is used.

Before this version, if the target/sourceCompatibility is the same as the version of the JVM Gradle
is running on no -source/-target options have been passed to the compiler process.

Beginning from this version the -source/-target options are always passed to the compiler.

### Experimental software model changes

- Subject of `@Validate` rules is immutable, and in the case of `ModelMap` and `ModelSet` types, readable.
- `components { }`, `binaries { }`, `testSuites { }` no longer allow elements to be accessed, except as a model reference.

For example:

    components {
        main.targetPlatform "x86"
    }
    
Should be changed to:

    components {
        main {
            targetPlatform "x86"
        }
    }

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Alex Afanasyev](https://github.com/cawka) - Support java.util.logging config files during tests. ([GRADLE-2524](https://issues.gradle.org/browse/GRADLE-2524))
- [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - fix NPE when configuring FileTree's builtBy by map ([GRADLE-3444](https://issues.gradle.org/browse/GRADLE-3444))
- [Ryan Ernst](https://github.com/rjernst) - always use -source/-target for the forked Java compiler 
- [Armin Groll](https://github.com/arming9) - Make Gradle source code compile inside Eclipse
- [Simon Herter](https://github.com/sherter) - add thrown exception to Javadocs for `ExtensionContainer`
- [Quinten Krijger](https://github.com/Krijger) - Fix description of leftShift task in error message
- [Igor Melnichenko](https://github.com/Myllyenko) - fixed Groovydoc up-to-date checks ([GRADLE-3349](https://issues.gradle.org/browse/GRADLE-3349))
- [Raymond Navarette](https://github.com/rnavarette) - add classifiers for project dependencies in generated POM files ([GRADLE-3030](https://issues.gradle.org/browse/GRADLE-3030))
- [Jean-Baptiste Nizet](https://github.com/jnizet) - add `filteringCharset` property to `CopySpec` ([GRADLE-1267](https://issues.gradle.org/browse/GRADLE-1267))
- [Sandu Turcan](https://github.com/idlsoft) - add wildcard exclusion for non-transitive dependencies in POM ([GRADLE-1574](https://issues.gradle.org/browse/GRADLE-1574))

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
