New DSL makes it easy to apply community plugins. First Gradle release that is integrated with the plugin portal.
Easier to find and use plugins.

Incremental Java compilation means faster builds.

New publishing plugins received some improvements.

IDE integration continues to be a focus. Can now cancel a build through the tooling API, which is the API used by the IDE integrations.

20 community contributions is a world record for any Gradle release.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Easier use of community plugins (i)

The new plugin resolution mechanism, backed by the new [Gradle Plugin Portal](http://plugins.gradle.org), makes it easier to use community Gradle plugins.
Instead of combining a `buildscript` script block and an `apply`, both statements can be replaced by a
[plugins script block](dsl/org.gradle.plugin.use.PluginDependenciesSpec.html).

    plugins {
        id 'com.company.myplugin' version '1.3'
    }

Gradle will query the Plugin Portal for the implementation details of the specified plugins.
The Plugin Portal's plugin browsing interface provides copy/paste friendly snippets.

Many enhancements and features are planned for both the Plugin Portal and plugins in general.
The new `plugins {}` block is not yet a complete replacement for the existing `apply()` method that is used to apply plugins.
Its functionality will be expanded over coming releases.

### Incremental Java compilation (i)

Gradle 2.1 brings incubating support for compiling Java code incrementally.
When this feature is enabled, only classes that are considered stale are recompiled.
This way not only the compiler does less work, but also fewer output class files are touched.
The latter feature is extremely important for scenarios involving JRebel - the less output files are touched the quicker the jvm gets refreshed classes.

We will improve the speed and capability of the incremental Java compiler. Please give use feedback on how well it works for your builds.
For more details please see the user guide section on “[Incremental compilation](userguide/java_plugin.html#sec:incremental_compile)”.
To enable the feature, configure the [JavaCompile](dsl/org.gradle.api.tasks.compile.JavaCompile.html) task accordingly:

    //configuring a single task:
    compileJava.options.incremental = true

    //configuring all tasks from root project:
    subprojects {
        tasks.withType(JavaCompile) {
            options.incremental = true
        }
    }

We are very excited about the progress on the incremental Java compilation.
Class dependency analysis of compiled classes is useful for a number of other features that are planned for Gradle, such as:

* detection of unused jars/classes
* detection of duplicate classes on classpath
* detection of tests to execute
* and more

### Use of HTTPS for mavenCentral() and jcenter() dependency repositories

The commonly used Maven Central and Bintray jCenter repositories are now accessed over the HTTPS protocol.
No change is required to builds to take advantage of this change.

If you are using the `mavenCentral()` or `jcenter()` [repository notations](dsl/org.gradle.api.artifacts.dsl.RepositoryHandler.html) 
your build will now access these repositories via HTTPS.

### Groovy version upgraded to 2.3.6

Gradle 2.1 includes Groovy 2.3.6, where Gradle 2.0 included Groovy 2.3.4.

This is a non breaking change.
All build scripts and plugins that work with Gradle 2.0 will continue to work without change.

### Child processes started by Gradle are better described

At the [Gradle Summit 2014 Conference](http://www.gradlesummit.com/conference/santa_clara/2014/06/home)
we ran a [Contributing To Gradle Workshop](http://www.gradlesummit.com/conference/santa_clara/2014/06/session?id=31169).
During the session, [Rob Spieldenner](https://github.com/rspieldenner)
contributed a very nice feature that gives much better insight into the child processes started by Gradle.
The example output of `jps -m` command now also contains the function of the worker process:

    28649 GradleWorkerMain 'Gradle Test Executor 17'
    28630 GradleWorkerMain 'Gradle Compiler Daemon 1'

### Groovy Compiler Configuration Script Support (i)

It is now possible to perform advanced Groovy compilation configuration by way of the new 
[`GroovyCompileOptions.configurationScript`](dsl/org.gradle.api.tasks.compile.GroovyCompileOptions.html#org.gradle.api.tasks.compile.GroovyCompileOptions:configurationScript) 
property 
(the `GroovyCompileOptions` instance is available as the 
[`groovyOptions` property of the `GroovyCompile` task](dsl/org.gradle.api.tasks.compile.GroovyCompile.html#org.gradle.api.tasks.compile.GroovyCompile:groovyOptions)). 
This makes it possible to impose global compiler transformations and other configuration.

For example, to globally enable Groovy's strict type checking, a compiler config script can be created with…

    import groovy.transform.TypeChecked
    
    withConfig(configuration) {
        ast(TypeChecked)
    }
    
And specified in the build script as…

    compileGroovy {
      groovyOptions.configurationScript = file("myConfigScript.groovy")
    }

Where `file("myConfigScript.groovy")` contains the Groovy code from above.
 
This feature was contributed by [Cédric Champeau](https://github.com/melix).

### Java Gradle Plugin plugin (i)

This is a plugin to assist in developing gradle plugins.  It validates the plugin structure during the jar task and emits warnings
if the plugin metadata is not valid.

    apply plugin: 'java-gradle-plugin'

### PMD Console Output (i)

It is now possible to have [PMD static analysis](userguide/pmd_plugin.html) print results directly to the console.

    pmd {
      consoleOutput = true
    }

Output will be written to `System.out` in addition to any configured reports.

This feature was contributed by [Vyacheslav Blinov](https://github.com/dant3).

### Dependency exclusions are included in POM file by `maven-publish` plugin (i)

The incubating [maven-publish](userguide/publishing_maven.html) plugin will now handle dependency excludes when generating a POM file for publishing.

So for a dependency declaration like:

    dependencies {
        compile("my.org:my-module:1.2") {
            exclude group: 'commons-logging', module: 'commons-logging'
            exclude group: 'commons-collections'
        }
    }

The generated POM file will contain the following content:

    <dependency>
        <groupId>my.org</groupId>
        <artifactId>my-module</artifactId>
        <version>1.2</version>
        <scope>runtime</scope>
        <exclusions>
            <exclusion>
                <groupId>commons-logging</groupId>
                <artifactId>commons-logging</artifactId>
            </exclusion>
            <exclusion>
                <groupId>commons-collections</groupId>
                <artifactId>*</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

This feature addresses [GRADLE-2945] was contributed by [Biswa Dahal](https://github.com/ffos).

### Support for the 'branch' attribute when publishing or resolving Ivy modules (i)

The incubating [ivy-publish](userguide/publishing_ivy.html) plugin now supports setting the 'branch' attribute on the module being published:

    publishing {
        publications {
            ivy(IvyPublication) {
                descriptor.branch = 'testing'
            }
        }
    }

When resolving Ivy modules, component metadata rules can also access the branch attribute via the
[IvyModuleDescriptor](javadoc/org/gradle/api/artifacts/IvyModuleDescriptor.html) interface.

    dependencies {
        components {
            eachComponent { ComponentMetadataDetails details, IvyModuleDescriptor ivyModule ->
                if (details.id.group == 'my.org' && ivyModule.branch == 'testing') {
                    details.changing = true
                }
            }
        }
    }

### Support for publishing extra 'info' elements when publishing Ivy modules (i)

The incubating [ivy-publish](userguide/publishing_ivy.html) plugin now supports publishing extra 'info' elements to the ivy.xml file generated
via the [IvyModuleDescriptorSpec](javadoc/org/gradle/api/publish/ivy/IvyModuleDescriptorSpec.html) interface.
Configured extra info elements are added as children of the ivy 'info' element.

    publishing {
        publications {
            ivy(IvyPublication) {
                descriptor.extraInfo 'http://my.namespace', 'myElement', 'Some value'
            }
        }
    }

Note that the [ivy schema](http://ant.apache.org/ivy/schemas/ivy.xsd) demands that any extra info elements be added after any child elements
of 'info' that are defined in the schema (e.g. description or ivyauthor).  This means that any [withXml()](javadoc/org/gradle/api/publish/ivy/IvyModuleDescriptorSpec.html#withXml%28org.gradle.api.Action%29)
actions must take care to insert any schema-defined 'info' child elements before any extra 'info' elements that may have been added.

### Task visibility is exposed in Tooling API (i)

Tasks and selectors accessible from the Tooling API now expose information about their [visibility](javadoc/org/gradle/tooling/model/Launchable.html) as the `public` property.

### Cancellation support in Tooling API (i)

The Tooling API now provides cancel [operations](javadoc/org/gradle/tooling/LongRunningOperation.html) which use a
[CancellationTokenSource](http://www.gradle.org/docs/nightly/javadoc/org/gradle/tooling/CancellationTokenSource.html)
to submit cancel requests.  The current implementation attempts to cancel the build first, and then will resort to stopping the daemon.

### Command line report to show details of the components produced by the build (i)

Sometimes it can be difficult to figure out exactly how Gradle has been configured and what a given build produces.
To help address this, Gradle now includes a new command line report that shows you some useful details about the components
that your project produces. To use the report, simply run `gradle components`.

In this release, the report shows details of the native libraries and executables defined by the native language plugins. It also shows
some basic details about the components defined by the Jvm language plugins. Over the next few releases, this report will grow to include more
information about other types of components.

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

### Upgrade to Groovy 2.3.6

The version of Groovy that Gradle uses to compile and run build scripts has been upgraded from 2.3.4 to 2.3.6. This should be a non-breaking change.

### Changed Java compiler integration for joint Java - Scala compilation

The `ScalaCompile` task type now uses the same Java compiler integration as the `JavaCompile` and `GroovyCompile` task types for performing joint Java - Scala
compilation. Previously it would use the old Ant-based Java compiler integration, which is no longer supported in the Gradle 2.x stream.

This change should be backwards compatible for all users, and should improve compilation time when compiling Java and Scala together.

### jcenter() repository notation now uses HTTPS instead of HTTP

The `jcenter()` repository definition now uses HTTPS instead of HTTP. This should be backwards compatible for all users. If for any reason you want 
to use explicitly HTTP for connecting the Bintray's JCenter repository you can simply reconfigure the URL:
 
    repositories {
        jcenter {
            url = "http://jcenter.bintray.com/"
        }
    }

### mavenCentral() repository notation now uses HTTPS instead of HTTP

The `mavenCentral()` repository definition now uses HTTPS instead of HTTP. 
This should be backwards compatible for all users. 
If for any reason you want to use explicitly HTTP for connecting the Maven Central repository you can simply add the repo with the HTTP protocol explicitly:
 
    repositories {
        maven {
            url = "http://repo1.maven.org/maven2/"
        }
    }

### Default FindBugs version was upgraded to 3.0.0

This way the FindBugs plugin works out of the box with newer Java versions (most notably: Java 1.8).
If you use Java 1.6 you need to configure an older version of FindBugs explicitly:

    findbugs {
        toolVersion = '2.0.3'
    }
 
### Changes to incubating native language plugins

TODO: Note about major breaking changes and suggestions about when to upgrade
TODO: Clean up this entire section

#### Native language plugins no longer apply the base plugin

The native language plugins now apply the [`LifecycleBasePlugin`](dsl/org.gradle.language.base.plugins.LifecycleBasePlugin) instead of the `BasePlugin`. This means
that the default values defined by the `BasePlugin` are not available.

TBD - make this more explicit re. what is actually not longer available.

#### Domain model reorganisation

- Merged NativeTestSuite and ProjectComponentNativeTestSuite
- NativeTestSuiteBinary no longer extends NativeExecutableBinary
- Merged TestSuiteExecutableBinary into NativeTestSuiteBinary
- Renamed CUnitTestSuite -> CUnitTestSuiteSpec
- Renamed CUnitTestSuiteBinary -> CUnitTestSuiteBinarySpec
- Renamed CUnitTestSuiteExecutableBinary -> CUnitTestSuiteBinary
- Renamed ProjectNativeComponent -> NativeComponentSpec
- Renamed ProjectNativeExecutable -> NativeExecutableSpec
- Renamed ProjectNativeTestSuite -> NativeTestSuiteSpec
- Renamed ProjectNativeLibrary -> NativeLibrarySpec
- TODO: document all of the changes once they are finalised

#### Changes to native cross compilation and custom platforms support

In [PlatformConfigurableToolChain](dsl/org.gradle.nativebinaries.toolchain.PlatformConfigurableToolChain.html) we removed

* target(Platform, Action)
* target(Platform)
* target(Iterable<? extends Platform>)
* target(List<String>)
* target(String... platformNames)
* target(Iterable<? extends Platform>, Action<? super TargetedPlatformToolChain>)

#### Changes to `sources` DSL

As part of our migration to use model rules, there have been some changes to the behaviour of the `sources` container.

TODO: double check as this has changed in last stories.

The primary `FunctionalSourceSet` for a component is not created eagerly when the component is defined. This means that you cannot
reference these source sets directly via dot-notation, but should instead use the `sources` container to optionally create them when configuring.

For example:

    executables {
        main
    }
    // No longer works, since 'sources.main' doesn't yet exist
    sources.main.cpp.lib library: 'foo'

    // Still works, because 'main' will be created if it doesn't yet exist
    sources {
        main.cpp.lib library: 'foo'
    }
    
If a language plugin is applied, an according language source set is not automatically 
added to all functional sourcet set declared using the `sources` DSL. Instead it must be declared 
manually with name and type.

    apply plugin:'cpp'
    
    sources {
        lib {
            // explicitly create a cpp source set of type CppSourceSet
            cpp(CppSourceSet)
        }
    }

For a declared component an functional sourceSet is created and appropriate sourceSets

#### Changes to CUnit configuration DSL

* The C language source set for CUnit test sources has been renamed from 'cunit' to 'c'. This means that by convention Gradle
  will look for test sources in `src/<test-suite-name>/c`.
* The CUnit test suite components are created via model rules, and must be configured via model rules:

    model {
        testSuites {
            helloTest {
                binaries.all {
                    lib library: "cunit", linkage: "static"
                }
            }
        }
    }

* The source set for a test suite component is created via model rules, and must be configured via model rules:

    model {
        sources {
            variantTest {
                c {
                    lib sources.hello.c
                    lib sources.helloTest.cunitLauncher
                }
            }
        }
    }

* The `RunTestExecutable` task now implements `ExecSpec`, allow test execution to be further configured.
    * The `RunTestExecutable.testExecutable` property has been removed and replaced by `RunTestExecutable.executable`.

#### Removed old mechanism for declaring dependencies

Very early versions of the `cpp-lib` and `cpp-exe` plugins had rudimentary support for publishing and resolving native components.
This support was never fully functional, and has now been completely removed in preparation for full support in the upcoming releases.

### Changes to incubating language base plugin

The `projectComponents` container was renamed to `componentSpecs`. 
We extracted the creation of the `componentSpecs` container and the LanguageRegistry `languages` out of `LanguageBasePlugin` 
into `ComponentModelBasePlugin`.

#### Default creation of SourceSets

Each declared component in the build creates an according functional source set in the build tight to the component.

    executables {
        main
    }


Creates a native executable component and will also create a functional sourceset named `main`. 

In prior Gradle versions each language registered in the LanguageRegistry caused the creation of an according default language sourceset for each
FunctionalSourceSet in the project. With Gradle 2.1 LanguageSourceSets are created only for sourceSets coupled to a component. 
The component knows it's relevant LanguageSourceSets. 

For the native executable component declared above for example there will be no default java sourceSet as it's a native component. 
Vice versa the following component declaration of a jvm library named `myLib` will not cause the creation of a `c` or `cpp` sourceSet for `myLib`
when the `C` and `CPP` plugin is applied.

    jvm {
        libraries {
            myLib
        }
    }
    
If a sourceSet of a component needs further configuration it is currently necessary to put this configuration (using the `sources` DSL _after_ 
the component declaration:

    executables {
        main
    }
    
    sources {
        main {
            cpp {
                source {
                    srcDirs "src/main/cpp", "src/shared/c++"
                    include "**{@literal /}*.cpp"
                }
            }
        }
    }

#### Domain model reorganisation

- Renamed ProjectComponent -> ComponentSpec
- Renamed ProjectComponentContainer -> ComponentSpecContainer
- Renamed ComponentSpecIdentifier -> NamedProjectComponentIdentifier
- Renamed ProjectBinary -> BinarySpec

### Changes to incubating Java language plugins

To better support the production of multiple binary outputs for a single set of sources, a new set of Java
language plugins was been introduced in Gradle 1.x. This development continues in this release, with the removal of the
`jvm-lang` plugin, and the replacement of the `java-lang` plugin with a completely new implementation.

The existing `java` plugin is unchanged: only users who explicitly applied the `jvm-lang` or `java-lang` plugins
will be affected by this change.

The plugin classes `org.gradle.api.plugins.JvmLanguagePlugin` and `org.gradle.api.plugins.JavaLanguagePlugin` were merged into
`org.gradle.api.plugins.LegacyJavaComponentPlugin` to avoid confusions with `org.gradle.language.java.plugins.JavaLanguagePlugin`.

The plugin class `org.gradle.language.java.plugins.LegacyJavaComponentPlugin` does not register a factory for `JavaSourceSet` 
and `ResourceSourceSet` on each functional source set anymore.

#### Domain model reorganisation

- Renamed ProjectClassDirectoryBinary -> ClassDirectoryBinarySpec
- Renamed ProjectJarBinary -> JarBinarySpec

### Generated maven pom contains dependency exclusions

The `maven-publish` plugin will now correctly add required 'exclusion' elements to the generated POM. If you have a build or plugin that
applies these exclusions itself, the generated POM file may contain duplicate 'exclusion' elements.

### Internal methods removed

- The internal method `Javadoc.setJavadocExecHandleBuilder()` has been removed. You should use `setToolChain()` instead.

### Changes to JUnit class loading

Previously, Gradle initialized test classes before trying to execute any individual test case.
As of Gradle 2.1, classes are not initialized until the execution of the first test case (GRADLE-3114).
This change was made for compatibility with the popular Android unit testing library, [Robolectric](http://robolectric.org).

This change impacts how classes that fail to initialize are reported.
Previously a single failure would be reported with a test case name of `initializerError` with the details of the failure.
After this change, the first test case of the class that cannot be initialized will contain details of the failure, 
while subsequent test cases of the class will fail with a `NoClassDefFoundError`.

This change will not cause tests that previously passed to start failing.

### configuration.exclude now validates the input

Previously, a typo in a configuration-level dependency exclude rule remained undetected and led to problems like GRADLE-3124.
Now the build fails fast when exclude rule is configured with a wrong key.

    //fails fast now, 'module' is the correct key
    configurations.compile.exclude modue: "kafka"

We suspect that the impact will be minimal to none hence we don't deprecate this behavior.

### Container creation methods now take precedence over other methods with the same signature

In response to the Gradle 2.0 regression GRADLE-3126, a change has been made to how container element configuration methods are dispatched.
This is unlikely to impact builds as the actual implementation now matches what is usually the intended behavior.

Prior to Gradle 2.1, the following build script would fail:

    apply plugin: "java"
    
    task integrationTest {}
    
    sourceSets {
      integrationTest {}
    }
    
    assert sourceSets.findByName("integrationTest") != null
    
The `integrationTest` source set would not be created because there is already a viable `integrationTest {}` method.

As of Gradle 2.1 the above script will not fail because it is interpreted that the intent is to create a new source set named `integrationTest`.
This applies to all named domain object containers in Gradle.
 
### ModelRule, ModelFinalizer, ModelRules removed

These incubating classes formed the API being used to manage model configuration by plugins.
They have been removed in favor of a different approach.

The replacement mechanism is currently undocumented and not yet designed for public use.

### TaskParameter replaced with TaskExecutionRequest

- Incubating class `TaskParameter` has been replaced with `TaskExecutionRequest`.
- Incubating property `StartParameter.taskParameters` has been replaced with `StartParameter.taskRequests`.

### IvyModuleDescriptor renamed to IvyModuleDescriptorSpec

- Incubating class `IvyModuleDescriptor` has been renamed to `IvyModuleDescriptorSpec`

### IvyModuleMetadata renamed to IvyModuleDescriptor

- Incubating class `IvyModuleMetadata` has been renamed to `IvyModuleDescriptor`
- Incubating method `IvyModuleDescriptor.getExtraInfo()` now returns an [IvyExtraInfo](javadoc/org/gradle/api/artifacts/ivy/IvyExtraInfo.html) instead of Map<String, String>

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Rob Spieldenner](https://github.com/rspieldenner) - Made the worker processes better described in the process list.
* [Vyacheslav Blinov](https://github.com/dant3) - PMD console output.
* [Thibault Kruse](https://github.com/tkruse) - Documentation improvements.
* [Biswa Dahal](https://github.com/ffos) - Dependency exclude support for `maven-publish`.
* [Marcin Zajączkowski](https://github.com/szpak) - Improvements to groovy-library build template.
* [Chris Earle](https://github.com/pickypg) - Improvements to the distribution plugin.
* [Curtis Mahieu](https://github.com/curtpm) - JUnit eager class initialization fix (GRADLE-3114)
* [Cédric Champeau](https://github.com/melix) - Support for Groovy compiler configuration scripts.
* [Martin](https://github.com/effrafax) - improvements to EAR plugin.
* [Stevo Slavić](https://github.com/sslavic) - updates to wrapper sample.
* [Björn Kautler](https://github.com/Vampire) - Changing rootProject projectDir from settings.gradle does not work (GRADLE-3086)
* [Viktor Nordling](https://github.com/viktornordling) - Don't re-check repository for missing module every 24 hours (GRADLE-3107)
* [Harald Schmitt](https://github.com/surfing)
    - Restrict SFTP authentication attempts to password (GRADLE-3133)
    - Improvements to internal test infrastructure
* [Joern Huxhorn](https://github.com/huxi) - Documentation improvements.
* [Sebastian Schuberth](https://github.com/sschuberth) - Documentation improvements.
* [Daniel Lacasse](https://github.com/Shad0w1nk)
    - Specific subtype for `CUnit` executable binary
    - `RunTestExecutable` task implements `ExecSpec`
* [Michael Klishin](https://github.com/michaelklishin) - Documentation improvements.
* [Mike Meessen](https://github.com/netmikey) - support EAR descriptors with DOCTYPE declaration (GRADLE-3150).

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
