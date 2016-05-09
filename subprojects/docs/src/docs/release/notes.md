## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Faster Gradle

TBD

### Deprecation of support for Java 6

Java 6 reached end of life in February 2013. Support for this version of Java is becoming more difficult as libraries and tools move to Java 7 and later, and as a result
Gradle 2.14 will be the last version of Gradle to run on Java 6. Support for running Gradle on Java 6 will be removed in Gradle 3.0.

Please note, however, that while it won't be possible to run Gradle itself on Java 6, it will be possible to build software using Java 6, by configuring your build to compile and test using Java 6. 

### More accurate Gradle class visibility during plugin development

In previous versions, Gradle's internal implementation dependencies were visible to plugins at build (i.e. compile and test) but not at runtime.
This caused problems when plugins depended on libraries that conflicted with Gradle's internal dependencies, such as Google Guava.
This has been fixed in Gradle 2.14.
Only classes that are part of Gradle's public API are visible to plugins at build time, which more accurately represents the runtime environment.

This fixes GRADLE-3433 and GRADLE-1715.
No build script changes are necessary to take advantage of these improvements.

### Plugins can more easily be published to and resolved from custom repositories

When writing plugins for private use by your organization, you can now take advantage of the simplified syntax of the `plugins {}` block and 
new `pluginRepositories {}` block when specifying the plugins to be applied to your build. For example, in your `settings.gradle` file you
can specify:

    pluginRepositories {
        maven {
            url 'https://private.mycompany.com/m2'
        }
        gradlePluginPortal()
        ivy {
            url 'https://repo.partner.com/m2'
        }
    }
    
And then use the `plugins {}` block to specify the plugins you want applied to your build projects.

    plugins {
        id 'java'                               // Resolved from the Gradle Distribution.
        id 'org.public.plugin' version '1.2.3'  // Resolved from the Gradle Plugin Portal.
        id 'com.mycompany.secret' version '1.0' // Resolved from your private maven repository.
        id 'com.partner.helpful' version '2.0'  // Resolved from your partner's ivy repository.
    }

The repositories are consulted in the order in which they were specifed in the `pluginRepositories {}` block.

To be able to resolve a plugin's implementation jar from just the specification of its `id` and `version` in an ivy or maven
repository, we require that a special [Plugin Marker Artifact](userguide/plugins.html#sec:plugin_markers) also be published to
the repository. This artifact has the coordinates `pluginId:pluginId.gradle.plugin:version` and will be automatically published
if you use the [maven-publish](userguide/publishing_maven.html) or [ivy-publish](userguide/publishing_ivy.html) plugins in
combination with the [java-gradle-plugin](userguide/javaGradle_plugin.html) plugin when publishing your plugin to your custom
repository. For example:

    version = 1.0
    group = "com.mycompany"

    plugins {
        id 'java-gradle-plugin'
        id 'maven-publish'
    }
    
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

When you run `./gradlew publish` your plugin `com.mycompany:secretPlugin:1.0` and the *Plugin Marker Artifact*
`com.mycopmany.secret:com.mycompany.secret.gradle.plugin:1.0` will both published to your maven repository for use in your company's
Gradle projects.

### Set the character set used for filtering files in CopySpec

By default, file filtering using `CopySpec` uses the default platform character set to read and write filtered files.
This can cause problems if, for example, the files are encoded using `UTF-8` but the default platform character set is another one.

You can now define the character set to use when reading and writing filtered files per `CopySpec`, e.g.:

    task filter(type: Copy) {
        from 'some/place'
        into 'somewhere/else'
        expand(version: project.version)
        filteringCharset = 'UTF-8'
    }

See the “[Filtering files](userguide/working_with_files.html#sec:filtering_files)” section of the “Working with files” chapter in the user guide for more information and examples of using this new feature.

This was contributed by [Jean-Baptiste Nizet](https://github.com/jnizet).

### Generated POM files now include classifiers and all artifacts for project dependencies

When publishing from a multi-project build to a Maven repository using the `maven` plugin, Gradle needs to map project dependencies to `<dependency>` declarations in the generated POM file. Previously, Gradle was ignoring the classifier attribute on any project artifacts, and would simply create a single `<dependency>` entry in the POM for any project dependency. This meant that the published POM for a project didn't contain the necessary dependencies to allow that project to be properly resolved from a Maven repository.

The mapping of project dependencies into POM file dependencies has been improved, and Gradle will now produce correct POM files for the following cases:

 - When the depended-on project configuration produces a single artifact with a classifier: this classifier will be included in the POM `<dependency>` entry.
 - The depended-on project configuration produces multiple artifacts: a `<dependency>` entry will be created for each artifact, with the appropriate classifier attribute for each.

As an example, given the following project definitions in Gradle:

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

The generated POM file for `project1` will include these dependency entries:

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

Previously, only a single `<dependency>` entry would have been generated for 'project2', omitting the 'classifier' attribute altogether.

Many thanks to [Raymond Navarette](https://github.com/rnavarette) for contributing this feature.

### Better control over Ant message logging

In previous versions of Gradle, the mapping of Ant message priorities to Gradle logging levels was fixed and the default "lifecycle"
log level was set in between the Ant "warn" and "info" priorities.  This meant that to show output from Ant tasks logged at the common "info"
priority, the Gradle logging level had to be set to a higher verbosity, potentially exposing unwanted output.  Similarly, to suppress
unwanted messages from Ant tasks, the Gradle logging level would need to be set to a lower verbosity, potentially suppressing other
desirable output.

You can now control the level of Ant logging by changing the message priority that maps to the Gradle lifecycle logging level:

    ant {
        lifecycleLogLevel = "INFO"
    }

This causes any Ant messages logged at the specified priority to be logged at the lifecycle logging level.  Any messages logged at a
higher priority will also be logged at lifecycle level (or above if it is already mapped to a higher logging level).  Messages logged
at a lower priority than the specified priority will be logged at the "info" logging level or below.

### Dependency substitution in composite build defined via `GradleConnection`

- Placeholder

### Identifier properties for IDE Tooling API models

- Placeholder

### Play/IDEA Integration

Projects using the [Play plugin](userguide/play_plugin.html#play_ide) can now generate IDEA metadata when the [IDEA plugin](userguide/idea_plugin.html) is also applied. 

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### `StartParameter.consoleOutput` property

The `StartParameter.consoleOutput` property has been promoted and is now stable.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### Setting the log level from build logic

The ability to set the log level from build logic using [LoggingManager.setLevel()](javadoc/org/gradle/api/logging/LoggingManager.html#setLevel%28org.gradle.api.logging.LogLevel%29)
is now deprecated and scheduled for removal in the next release of Gradle.  If you are using this feature to control logging of messages from Ant
tasks, please use the [AntBuilder.setLifecycleLogLevel()](javadoc/org/gradle/api/AntBuilder.html#setLifecycleLogLevel%28java.lang.String%29) method instead.

### Support for running Gradle on Java 6

Running Gradle using Java 6 is now deprecated, and support will be removed in Gradle 3.0.

It will continue to be possible to build JVM based projects for Java 6 using Gradle 3.0, by running Gradle using Java 7 and configuring your build to use Java 6 to compile, test and run your code.

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

This is expected to be a transparent and compatible change for all plugin builds.
While Gradle's implementation dependencies were previously visible at build time, they were not at runtime.
As such, it was not possible to successfully ship a plugin that relied on access to Gradle's implementation dependencies.

### Change in plugin id

`ComponentModelBasePlugin` can no longer be applied using id `component-base`. Its new id is `component-model-base`.

### JAR metadata and Manifest content encoding

Previous versions of Gradle used to encode JAR/WAR/EAR files metadata and Manifests content using the platform default character set instead of UTF-8. Both are bugs and have been fixed in this release, see the related fixed issues above.

In order to keep backward compatibility, merged manifests are still read using the platform default character set.

If necessary, convenience properties have been added to [`Jar`](dsl/org.gradle.api.tasks.bundling.Jar.html), [`War`](dsl/org.gradle.api.tasks.bundling.War.html), [`Ear`](dsl/org.gradle.plugins.ear.Ear.html) tasks and both [`Manifest`](javadoc/org/gradle/api/java/archives/Manifest.html) and [`ManifestMergeSpec`](javadoc/org/gradle/api/java/archives/ManifestMergeSpec.html) types to control which character set to use when merging manifests.

In order to fall back to the old behaviour you can do the following:

    jar {
        // JAR metadata
        metadataCharset = Charset.defaultCharset().name()
        manifest {
            // Manifest content
            contentCharset = Charset.defaultCharset().name()
        }
    }

### Additional POM `<dependency>` attributes generated for some project dependencies

As described above, POM files generated by the `maven` plugin now include classifiers and all artifacts for project dependencies. This improvement may break existing Gradle builds, particularly those that include a specific workaround for the previous behaviour. These workarounds should no longer be required, and may need to be removed to ensure that Gradle 2.14 will create correct `<dependency>` attributes for project dependencies.

### A few types from the `signing` plugin were converted from Groovy to Java

And as a result might cause different behavior when used as base types. The affected types are:

 - `SigningPlugin`, `SigningExtension`, `Signature` and `SignOperation`
 - `AbstractSignatureType`, `BinarySignatureType` and `ArmoredSignatureType`
 - `AbstractSignatureTypeProvider` and `DefaultSignatureTypeProvider`
 - `SignatorySupport`, `PgpKeyId`, `PgpSignatory`, `PgpSignatoryProvider` and `PgpSignatoryFactory`

### Source and target compatibility options are always passed to the Java compiler

If no target/sourceCompatibility option is set on for the Java plugin, then the version of the
JVM Gradle is running on is used.

Before this version, if the target/sourceCompatibility is the same as the version of the JVM Gradle
is running on no -source/-target options have been passed to the compiler process.

Beginning from this version the -source/-target options are always passed to the compiler.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Igor Melnichenko](https://github.com/Myllyenko) - fixed Groovydoc up-to-date checks ([GRADLE-3349](https://issues.gradle.org/browse/GRADLE-3349))
- [Sandu Turcan](https://github.com/idlsoft) - add wildcard exclusion for non-transitive dependencies in POM ([GRADLE-1574](https://issues.gradle.org/browse/GRADLE-1574))
- [Jean-Baptiste Nizet](https://github.com/jnizet) - add `filteringCharset` property to `CopySpec` ([GRADLE-1267](https://issues.gradle.org/browse/GRADLE-1267))
- [Simon Herter](https://github.com/sherter) - add thrown exception to Javadocs for `ExtensionContainer`
- [Raymond Navarette](https://github.com/rnavarette) - add classifiers for project dependencies in generated POM files ([GRADLE-3030](https://issues.gradle.org/browse/GRADLE-3030))
- [Armin Groll](https://github.com/arming9) - Make Gradle source code compile inside Eclipse
- [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - fix NPE when configuring FileTree's builtBy by map ([GRADLE-3444](https://issues.gradle.org/browse/GRADLE-3444))
- [Ryan Ernst](https://github.com/rjernst) - always use -source/-target for the forked Java compiler 

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
