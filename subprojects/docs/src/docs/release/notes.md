The big news in Gradle 2.1 is the new, simpler, mechanism for using community plugins in your build, integrated with the new [Gradle Plugin Portal](https://plugins.gradle.org). 
This is one of many improvements planned to expand the Gradle platform by making the lives of both plugins developers and users through new features and tooling.

Another very new exciting feature is the addition of incremental Java compilation, which promises to significantly reduce compilation times during the development cycle.
Users of Apache Ant and Apache Maven may be familiar with incremental Java compilation from those tools.
Gradle's incremental Java compiler is not based on the same approach as these tools and does not suffer from the same set of problems that plague 
incremental compilation with these tools.
Please see the “Incremental Java compilation” section in “New and noteworthy” for more information.

The `maven-publish` and `ivy-publish` have been improved in this release.
When using the `maven-publish` plugin, dependency exclusions specified when consuming dependencies are now translated to the published POM when publishing in Maven format.
The `ivy-publish` plugin is continuing to expand and support more of Apache Ivy's extensive configuration options.
In this release it is now easier to specify the `branch` attribute for Ivy publications and to specify arbitrary “extra info”.

IDE integration continues to be a strong area of focus.
The [Tooling API](userguide/embedding.html), which is used by IDEs and other tooling to embed Gradle, now supports canceling a running operation or build.
This is a particularly welcome improvement for Android Studio users, who can expect the coming releases of Android Studio to leverage this new functionality.

We are particularly proud of Gradle 2.1 containing contributions from 18 people outside of the core Gradle development team, which is a new record for the project.
Thank you to everyone who contributed. Also, thanks to all who raise issue reports for the Gradle 2.0 release allowing us to make Gradle 2.1 even better.

As usual there's also a smattering of other improvements and bug fixes, detailed below.

We hope you enjoy Gradle 2.1.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Easier use of community plugins (i)

The new plugin resolution mechanism, backed by the new [Gradle Plugin Portal](https://plugins.gradle.org), makes it easier to use community Gradle plugins.
Instead of combining a `buildscript` script block and an `apply` statement, both statements can be replaced by a
[plugins script block](dsl/org.gradle.plugin.use.PluginDependenciesSpec.html).

    plugins {
        id 'com.company.myplugin' version '1.3'
    }

Gradle will query the Plugin Portal for the implementation details of the specified plugins.
The Plugin Portal's plugin [browsing interface](https://plugins.gradle.org) provides copy/paste friendly snippets.

All plugin authors are encouraged to submit their plugins for inclusion in the Plugin Portal.
Submission instructions can be found [on the Plugin Portal site](https://plugins.gradle.org/submit).

Many enhancements and features are planned for both the Plugin Portal and plugins in general.
The new `plugins {}` block is not yet a complete replacement for the existing `apply()` method that is used to apply plugins.
Its functionality will be expanded over coming releases.

### Incremental Java compilation (i)

Gradle 2.1 adds, incubating, support for compiling Java code incrementally.

Gradle has long had the ability to perform any build task incrementally by only performing the task if the inputs or outputs of the task change.
When applied to Java compilation, this means that all the source for a given task will be compiled if any source file needs to be recompiled.
The new incremental compilation feature compliments incremental task execution by only recompiling the actual source that needs to be recompiled, instead of all the source.

Incremental compilation has two key benefits:

1. Reduced compilation time during development due to less files being compiled
2. Class files whose content is unchanged by a compile are not updated on the filesystem

The second point above is important for tools such as [JRebel](http://zeroturnaround.com/software/jrebel) 
that watch for changed class files in order to reload the class at runtime.

Incremental compilation can be enabled via the `options` property of the `JavaCompile` task.
The following example illustrates enabling incremental compilation for all `JavaCompile` tasks.

    allprojects {
        tasks.withType(JavaCompile) {
            options.incremental = true
        }
    }
 
Incremental compilation requires extra work and record keeping during compilation to achieve.
This means that a full compile when incremental compile is enabled can be slower than if it was not enabled.
However, this cost is offset for subsequent compile operations as only a subset of the source is compiled.

The current implementation is not able to fully analyze the impact of all changes to the source code in terms of identifying exactly which classes need to be recompiled.
In such situations, all of the source will be recompiled in order to avoid inconsistent compilation results.
Incremental Java compilation will improve over coming Gradle versions to be generally faster, and to invoke a full recompile in response to fewer types of changes.

It is worthwhile to note that Gradle's incremental Java compiler is not based on Apache Ant's incremental compiler (which is also used by Apache Maven).
Incremental compilation with Ant (and Maven) has severe limitations and is notorious for producing inconsistent results.
That is, it frequently produces different set of bytecode to what a full recompile would produce.
This is due to it being based on timestamp comparisons and dependency analysis through bytecode analysis alone.
The Gradle incremental compiler is not based on timestamps and also employs bytecode **and source** analysis in order to more thoroughly analyze the impact of a change.

While we have extensively tested this feature during development, it will significantly benefit from usage “in the field”.
Please try out this exciting new feature and report any problems encountered via the [Gradle Forums](http://forums.gradle.org).

For more information please see the user guide section on “[Incremental Java Compilation](userguide/java_plugin.html#sec:incremental_compile)”.

### Use of HTTPS for mavenCentral() and jcenter() dependency repositories

The commonly used Maven Central and Bintray jCenter repositories are now accessed over the HTTPS protocol.
No change is required to builds to take advantage of this change.

If you are using the `mavenCentral()` or `jcenter()` [repository notations](dsl/org.gradle.api.artifacts.dsl.RepositoryHandler.html) 
your build will now access these repositories via HTTPS.

### Groovy version upgraded to 2.3.6

Gradle 2.1 includes Groovy 2.3.6, where Gradle 2.0 included Groovy 2.3.4.

This is a non breaking change.
All build scripts and plugins that work with Gradle 2.0 will continue to work without change.

### Child process descriptions in process listings

Gradle often launches child processes during a build to perform work.
For example, Gradle executes test in a forked JVM process.
During a large build, particularly when [building in parallel](userguide/multi_project_builds.html#sec:parallel_execution), 
there may be more than one forked Gradle process at any time.
In previous Gradle versions, there was no practical way to determine which process was doing what without looking inside the JVM of each process.
Processes are now started with a description as a command line argument.
Command line arguments are usually displayed by process listing utilities such as 
[`ps`](http://www.linfo.org/ps.html) and [`jps`](http://docs.oracle.com/javase/7/docs/technotes/tools/share/jps.html),
which makes it easy to now identify what kind of Gradle process it is.

The following is an example of output from `'jps -m'` during a Gradle 2.1 build:

<p>
<tt><pre>
28649 GradleWorkerMain 'Gradle Test Executor 17'
28630 GradleWorkerMain 'Gradle Compiler Daemon 1'
</pre></tt>
</p>

This feature was contributed by [Rob Spieldenner](https://github.com/rspieldenner) during the 
[“Contributing To Gradle Workshop”](http://www.gradlesummit.com/conference/santa_clara/2014/06/session?id=31169)
at the [Gradle Summit 2014 Conference](http://www.gradlesummit.com/conference/santa_clara/2014/06/home).

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

<p>
<tt><pre>
&lt;dependency&gt;
    &lt;groupId&gt;my.org&lt;/groupId&gt;
    &lt;artifactId&gt;my-module&lt;/artifactId&gt;
    &lt;version&gt;1.2&lt;/version&gt;
    &lt;scope&gt;runtime&lt;/scope&gt;
    &lt;exclusions&gt;
        &lt;exclusion&gt;
            &lt;groupId&gt;commons-logging&lt;/groupId&gt;
            &lt;artifactId&gt;commons-logging&lt;/artifactId&gt;
        &lt;/exclusion&gt;
        &lt;exclusion&gt;
            &lt;groupId&gt;commons-collections&lt;/groupId&gt;
            &lt;artifactId&gt;*&lt;/artifactId&gt;
        &lt;/exclusion&gt;
    &lt;/exclusions&gt;
&lt;/dependency&gt;
</pre></tt>
</p>

This feature addresses [GRADLE-2945] and was contributed by [Biswa Dahal](https://github.com/ffos).

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

The incubating [ivy-publish](userguide/publishing_ivy.html) plugin now supports publishing extra `'info'` elements to the ivy.xml file generated
via the [IvyModuleDescriptorSpec](javadoc/org/gradle/api/publish/ivy/IvyModuleDescriptorSpec.html) interface.
Configured extra info elements are added as children of the ivy `'info'` element.

    publishing {
        publications {
            ivy(IvyPublication) {
                descriptor.extraInfo 'http://my.namespace', 'myElement', 'Some value'
            }
        }
    }

Note that the [ivy schema](http://ant.apache.org/ivy/schemas/ivy.xsd) demands that any extra info elements be added after any child elements
of `'info'` that are defined in the schema (e.g. `'description'` or `'ivyauthor'`).  
This means that any [withXml()](javadoc/org/gradle/api/publish/ivy/IvyModuleDescriptorSpec.html#withXml%28org.gradle.api.Action%29)
actions must take care to insert any schema-defined 'info' child elements <i>before</i> any extra `'info'` elements that may have been added.

Furthermore, retrieving extra info elements with namespace when resolving Ivy modules is also available now.  
This is exposed via the [IvyExtraInfo](javadoc/org/gradle/api/artifacts/ivy/IvyExtraInfo.html) object in component metadata rules.

    dependencies {
        components {
            eachComponent { ComponentMetadataDetails details, IvyModuleDescriptor ivyModule ->
                if (ivyModule.extraInfo.get('http://my.namespace', 'myElement') == 'changing') {
                    details.changing = true
                }
            }
        }
    }

Note that the `Map<String, String>` representation for extra info elements in [IvyModuleDescriptor](javadoc/org/gradle/api/artifacts/ivy/IvyModuleDescriptor.html)
has been replaced with [IvyExtraInfo](javadoc/org/gradle/api/artifacts/ivy/IvyExtraInfo.html).

### Tooling API improvements

The [tooling API](userguide/embedding.html) is used to embed and programmatically invoke Gradle builds. This release sees some new features added
to the tooling API.

### Cancellation support in Tooling API (i)

The Tooling API now provides a way to cancel [operations](javadoc/org/gradle/tooling/LongRunningOperation.html), such as running a build, using the
[CancellationTokenSource](http://www.gradle.org/docs/nightly/javadoc/org/gradle/tooling/CancellationTokenSource.html) API
to submit cancel requests.  The current implementation attempts to cancel the build first, and then will resort to stopping the daemon.

### Task visibility is exposed in Tooling API (i)

This release sees further improvements to the `BuildInvocations` model added in Gradle 1.12. In particular, tasks and selectors accessible from this model now
expose information about their [visibility](javadoc/org/gradle/tooling/model/Launchable.html) as the `public` property.

This change means that it is now possible to implement the equivalent of `gradle tasks` using the tooling API.

### Command line report to show details of the components produced by the build (i)

Sometimes it can be difficult to figure out exactly how Gradle has been configured and what a given build will produce.
To help address this, Gradle now includes a new command line report that shows you some useful details about the components
that your project produces. To use the report, simply run `gradle components`.

In this release, the report shows details of the native libraries and executables defined by the native language plugins. It also shows
some basic details about the components defined by the Jvm language plugins. Over the next few releases, this report will grow to include more
information about other types of components.

## Fixed issues

## Potential breaking changes

### Upgrade to Groovy 2.3.6

The version of Groovy that Gradle uses to compile and run build scripts has been upgraded from 2.3.4 to 2.3.6. 
This should be a non-breaking change, but is mentioned as it is an update to a library that is used by all Gradle builds.

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

The Gradle team is currently working hard on a new, faster configuration model as well as rework that will enable full dependency management
support for native binaries. As part of this work, many changes have been made to the incubating native language plugins. While some effort has been
made to avoid unnecessary breakages, in many cases such changes have been required.

It is anticipated that these plugins will remain unstable for the next release or two. Considering that fact, it may be prudent to hold off upgrading your native
build until the underlying infrastructure has stabilised. Naturally, we'll be [happy to assist with your migration](http://forums.gradle.org),
whether you choose to stick with the 'bleeding-edge' or prefer to wait.

#### Native language plugins no longer apply the base plugin

The native language plugins now apply the [`LifecycleBasePlugin`](dsl/org.gradle.language.base.plugins.LifecycleBasePlugin) instead of the `BasePlugin`. This means
that the default values defined by the `BasePlugin` are not available.

Of note, the following actions of the `BasePlugin` will be missing:

- The `org.gradle.api.plugins.BasePluginConvention` and it's use to configure the `dists` directory, `libs` directory and `archivesBaseName`
- The `build<Configuration>` task rule
- The `upload<Configuration>` task rule
- Any default `Configuration` instances

#### Domain model reorganisation

Many domain model classes have been renamed for consistency, and to permit better integration with the new `jvm` component model.

In general, model classes that define how a component or binary is built have been renamed with the `Spec` suffix.
(Previously, we inconsistently used the `Project` prefix for this purpose).
For example, `ProjectNativeComponent` is now `NativeComponentSpec` and `CUnitTestSuiteBinary` is now `CUnitTestSuiteBinarySpec`.

In addition to these renames for consistency, the following changes were made:

- Merged `NativeTestSuite` and `ProjectComponentNativeTestSuite`
- `NativeTestSuiteBinary` no longer extends `NativeExecutableBinary`
- Merged `TestSuiteExecutableBinary` into `NativeTestSuiteBinary`

#### Changes to native cross compilation and custom platforms support

To avoid a proliferation of methods on [PlatformConfigurableToolChain](dsl/org.gradle.nativebinaries.toolchain.PlatformConfigurableToolChain.html), we removed:

* `target(Platform, Action)`
* `target(Platform)`
* `target(Iterable<? extends Platform>)`
* `target(List<String>)`
* `target(String... platformNames)`
* `target(Iterable<? extends Platform>, Action<? super TargetedPlatformToolChain>)`

#### Changes to `sources` DSL

When a language plugin is applied, a `LanguageSourceSet` is only added to a `FunctionalSourceSet` when that `FunctionalSourceSet` is associated with a component.
In practise, this means that a build script should configure any language source sets after the components have been defined.

More details are available in the following section: [Changes to the incubating `LanguageBasePlugin`](#changes-to-the-incubating-languagebaseplugin).

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

### Changes to the incubating `LanguageBasePlugin`

The `LanguageBasePlugin` serves as a basis for the new component-based native and java language plugin suites. As part of ongoing work in these domains,
major changes have been made to this base plugin.

#### Domain model reorganisation

- Renamed `ProjectComponent` -> `ComponentSpec`
- Renamed `ProjectComponentContainer` -> `ComponentSpecContainer`
- Renamed `ComponentSpecIdentifier` -> `NamedProjectComponentIdentifier`
- Renamed `ProjectBinary` -> `BinarySpec`

#### Renamed `projectComponents` container to `componentSpecs`

The `projectComponents` container extension has been renamed to `componentSpecs`. This container is now added by the `ComponentModelBasePlugin` and
not by the `LanguageBasePlugin`.

#### Creation of default `LanguageSourceSet` instances

In previous Gradle versions each language plugin applied triggered the automatic creation of a `LanguageSourceSet` for each
FunctionalSourceSet in the project. With Gradle 2.1, this has been changed to that only languages appropriate to the respective component
are added to the `FunctionalSourceSet`.

To facilitate this change, a functional source set is created for each declared component in the build at the point of constructing the component.

Consider the example:

    apply plugin: 'cpp'
    apply plugin: 'java-lang'

    executables {
        main
    }


This example defines a `NativeExecutable` component named 'main' and will also create the `FunctionalSourceSet` 'sources.main'. A `CppSourceSet` 'cpp' will be added
to 'sources.main', but no `JavaSourceSet` will be added because this language is not applicable to a `NativeExecutable`.

Similarly, when a jvm library is defined no `c` or `cpp` source sets will be created, even when the `c` and `cpp` language plugins are applied.

If the source sets of a component require further configuration, it is necessary to place this configuration _after_ the declaration of the component:

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

Alternatively, you can create and configure any `FunctionalSourceSet` and `LanguageSourceSet` instances directly via the `sources` DSL at any time:

    apply plugin:'cpp'

    sources {
        lib {
            // explicitly create a cpp source set of type CppSourceSet
            cpp(CppSourceSet)
        }
    }

### Changes to incubating Java language plugins

To better support the production of multiple binary outputs for a single set of sources, a new set of Java
language plugins was introduced in Gradle 1.x. This development continues in this release, with the removal of the
`jvm-lang` plugin, and the replacement of the `java-lang` plugin with a completely new implementation.

The existing `java` plugin is unchanged: only users who explicitly applied the `jvm-lang` or `java-lang` plugins
will be affected by this change.

#### Plugin reorganisation

The plugin classes `org.gradle.api.plugins.JvmLanguagePlugin` and `org.gradle.api.plugins.JavaLanguagePlugin` were merged into
`org.gradle.api.plugins.LegacyJavaComponentPlugin` to avoid confusions with `org.gradle.language.java.plugins.JavaLanguagePlugin`.

The new plugin class `org.gradle.language.java.plugins.LegacyJavaComponentPlugin` does not register a factory for `JavaSourceSet`
and `ResourceSourceSet` on each `FunctionalSourceSet`.

#### Domain model reorganisation

- Renamed `ProjectClassDirectoryBinary` -> `ClassDirectoryBinarySpec`
- Renamed `ProjectJarBinary` -> `JarBinarySpec`

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
