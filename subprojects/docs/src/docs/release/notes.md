Gradle 2.8 delivers performance improvements and a collection of general fixes and enhancements.

Large builds with many source files should see major improvements in incremental build speed.
This release also brings faster build script compilation, faster source compilation when using continuous build,
and general performance improvements that apply to all builds.

Building upon the recent releases, this release brings more improvements to the [Gradle TestKit](userguide/test_kit.html).
It is now easier to inject plugins under test into test builds.

Work continues on the new [managed model](userguide/new_model.html).
This release brings richer modelling capabilities along with interoperability improvements when dynamically depending on rule based tasks.

A Gradle release would not be complete without contributions from the wonderful Gradle community.
This release provides support for file name encoding in Zip files, support for more PMD features and other fixes from community pull requests.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster incremental builds

One of Gradle's key features is the ability to perform “incremental builds”.
This allows Gradle to avoid doing redundant work, by detecting that it can safely reuse files created by a previous build.
For example, the “class files” created by the Java compiler can be reused if there were no changes to source code or compiler settings since the previous build.
This is a [generic capability available to all kinds of work performed by Gradle](userguide/more_about_tasks.html#sec:up_to_date_checks).

This feature relies on tracking checksums of files in order to detect changes.
In this Gradle release, improvements have been made to the management of file checksums,
resulting in significantly improved build times when the build is mostly up to date (i.e. many previously created files were able to be reused).

Highly incremental builds of projects with greater than 140,000 files have been measured at 35-50% faster with Gradle 2.8.
Very large projects (greater than 400,000 files) are also significantly faster again, if there is ample memory available to the build process
(see [“The Build Environment”](userguide/build_environment.html) in the Gradle User Guide for how to control memory allocation).
Smaller projects also benefit from these changes.

No build script or configuration changes, beyond upgrading to Gradle 2.8, are required to leverage these performance improvements.

### Faster build script compilation

Build script compilation times have been reduced by up to 30% in this version of Gradle.

This improvement is noticeable when building a project for the first time with a certain version of Gradle, or after making changes to build scripts.
This is due to Gradle caching the compiled form of the build scripts.

The reduction in compilation time per script is dependent on the size and complexity of script.
Additionally, the reduction for the entire build is dependent on the number of build scripts that need to be compiled.

### Faster compilation for continuous builds

In many cases Gradle will spawn a daemon process to host a compiler tool. This is done for performance reasons,
as well as to accommodate special heap size settings, classpath configurations, etc.
A compiler daemon is started on first use, and stopped at the end of the build.

With Gradle 2.8, compiler daemons are kept running throughout the lifetime of a continuous build session and will only be stopped when the continuous build is cancelled.
This improves the performance of continuous builds, since the cost of re-spawning these compilers is avoided for subsequent builds.
This change has no impact on non-continuous builds: in this case each compiler daemon will be stopped at the end of the build.

The following compilers are forked and should demonstrate improved performance with continuous builds:

- Java compiler - when `options.fork = true` (default is `false`)
- Scala compiler - when used with the `scala` plugin and `scalaCompileOptions.useAnt = false` (default is `true`)
- Groovy compiler - when `options.fork = true` (default is `true`)
- Scala compiler - when used with the `play` plugin
- Play Routes compiler, Twirl compiler and Javascript compiler

### General performance improvements

This release also contains various other performance improvements that are generally applicable to most builds.
These improvements are due to the use of more efficient data structures and smarter caching.
Build time reductions vary depending on the size and nature of the build.

### Convenient injection of classes under test via TestKit API

Previous releases of Gradle required the end user to provide classes under test (e.g. plugin and custom task implementations) to the TestKit by assigning them to the buildscript's classpath.

This release makes it more convenient to inject classes under test through the `GradleRunner` API with the method
<a href="javadoc/org/gradle/testkit/runner/GradleRunner.html#withPluginClasspath(java.util.Iterable)">withPluginClasspath(Iterable&lt;File&gt;)</a>.
This classpath is then available to use to locate plugins in a test build via the
[plugins DSL](userguide/plugins.html#sec:plugins_block). The following code example demonstrates the use of the new TestKit API in a test class based on the test framework Spock:

    class BuildLogicFunctionalTest extends Specification {
        @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
        File buildFile
        List<File> pluginClasspath

        def setup() {
            buildFile = testProjectDir.newFile('build.gradle')
            pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
              .readLines()
              .collect { new File(it) }
        }

        def "execute helloWorld task"() {
            given:
            buildFile << """
                plugins {
                    id 'com.company.helloworld'
                }
            """

            when:
            def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('helloWorld')
                .withPluginClasspath(pluginClasspath)
                .build()

            then:
            result.standardOutput.contains('Hello world!')
            result.taskPaths(SUCCESS) == [':helloWorld']
        }
    }

Future versions of Gradle will aim for automatically injecting the classpath without additional configuration from the end user.

### Zip file name encoding

Gradle will use the default character encoding for file names when creating Zip archives.  Depending on where the archive will be extracted, this may not be the best possible encoding
to use due to the way various operating systems and archive tools interpret file names in the archive.  Some tools assume the extracting platform character encoding is the same encoding used
to create the archive. A mismatch between encodings will manifest itself as "corrupted" or "mangled" file names.

[Zip](dsl/org.gradle.api.tasks.bundling.Zip.html) tasks can now be configured with an explicit character encoding to use for file names and comment
fields in the archive. (This option does not affect the _content_ of the files in the archive.)
The default behavior has not been changed, so no changes should be necessary for existing builds.

### Configuration of the rule priority threshold for PMD (i)

By default, the PMD plugin will report all rule violations and fail the build if any violations are found.
This means the only way to ignore low priority violations was to create a custom ruleset.

Gradle now supports configuring a "rule priority" threshold.
The PMD report will contain only violations higher than or equal to the priority configured, and the build will only fail if one of
these "priority" violations is discovered.

You configure the rule priority threshold via the [PmdExtension](dsl/org.gradle.api.plugins.quality.PmdExtension.html).
You can also configure the property on a per-task level through the [Pmd](dsl/org.gradle.api.plugins.quality.Pmd.html) task.

    pmd {
        rulePriority = 3
    }

### Better PMD analysis with type resolution (i)

Some PMD rules require access to the dependencies of your project to perform type resolution. If the dependencies are available on PMD's auxclasspath,
[additional problems can be detected](http://pmd.sourceforge.net/pmd-5.3.2/pmd-java/rules/java/android.html).

Gradle now automatically adds the compile dependencies of each analyzed source set to PMD's auxclasspath.  No additional configuration should be necessary to enable this in existing builds.

### Managed model improvements

A number of improvements have been made to the experimental [managed model](userguide/new_model.html) feature. This feature is currently used by the native and Play plugins and will evolve into a
general purpose feature for describing Gradle builds.

#### Support for `Set` and `List` of scalar types

The managed model now supports collections of scalar types. This means that it is possible to use a `Set` or a `List` with element type:

 - a JDK `Number` type (`Integer`, `Double`, ...)
 - a `Boolean`
 - a `String`
 - a `File`
 - or an enumeration type

A collection of a scalar type can be attached to any `@Managed` type as a property, or used for the elements of a `ModelSet` or `ModelMap`, or as a top level element. For example:

    @Managed
    interface User {
        Set<String> getGroups();
    }

Collections of scalar types are available as read-only properties, in which case they default to an empty collection, or as read-write properties, in which case they default to `null`.
Read-only properties are similar to final values: they can be mutated as long as they are the subject of a rule. Read-write properties can also be mutated, but they are not final: the
collection can be overwritten by a configuration rule.

A read-only (non nullable) property is created by defining only a setter, while a read-write property is created by defining both a setter and a getter:

    @Managed
    interface User {
        Set<String> getGroups();
        void setGroups(Set<String> groups);
    }

#### Support for `FunctionalSourceSet`

This release facilitates adding source sets (`LanguageSourceSet`) to arbitrary locations in the model space through the use of the `language-base` plugin and `FunctionalSourceSet`.
A `FunctionalSourceSet` can be attached to any `@Managed` type as a property, or used for the elements of a `ModelSet` or `ModelMap`, or as a top level element.

Using `FunctionalSourceSet` allows build and plugin authors to strongly model things that use collections of sources as `LanguageSourceSet`s.
This kind of strongly typed modelling also allows build and plugin authors to access `LanguageSourceSet`s in a controlled and consistent way using rules.

Adding a top level `FunctionalSourceSet` is as simple as:


    model {
        sources(FunctionalSourceSet)
    }


or from a `RuleSource`

    class Rules extends RuleSource {
        @Model
        void functionalSources(FunctionalSourceSet sources) {
        }
    }
    apply plugin: Rules



Here's an example of creating a managed type with `FunctionalSourceSet` properties.

    @Managed
    interface BuildType {
        FunctionalSourceSet getSources()
        FunctionalSourceSet getInputs()
        ModelMap<FunctionalSourceSet> getComponentSources()
    }


#### Internal views for components

It is now possible to attach internal information to an unmanaged `ComponentSpec`. This way a plugin can make some data about its components visible to build logic via a public component type,
while hiding the rest of the data behind the internal view type. The default implementation of the component must implement all internal views declared for the component.

    interface SampleLibrarySpec extends ComponentSpec {
        String getPublicData()
        void setPublicData(String publicData)
    }

    interface SampleLibrarySpecInternal extends ComponentSpec {
        String getInternalData()
        void setInternalData(String internalData)
    }

    class DefaultSampleLibrarySpec extends BaseComponentSpec implements SampleLibrarySpec, SampleLibrarySpecInternal {
        String internalData
        String publicData
    }

Components can be targeted by rules via their internal types when those internal types extend `ComponentSpec`:

    class Rules extends RuleSource {
        @Mutate
        void mutateInternal(ModelMap<SampleLibrarySpecInternal> sampleLibs) {
            sampleLibs.each { sampleLib ->
                sampleLib.internalData = "internal"
            }
        }
    }

For internal view types that do not not extend `ComponentSpec`, targeting components can be achieved via `ComponentSpecContainer.withType()`:

    @Defaults
    void finalize(ModelMap<SampleLibrarySpec> sampleLibs) {
        sampleLibs.withType(SomeInternalView).all { sampleLib ->
            // ...
        }
    }


#### Rule based model configuration

Interoperability between legacy configuration space and new rule based model configuration space has been improved. More specifically, the `tasks.withType(..)` construct allows legacy configuration tasks
to depend on tasks created via the new rule based approach. See [this issue](https://issues.gradle.org/browse/GRADLE-3318) for details.

#### References between model elements

Currently, the managed model works well for defining a tree of objects. This release improves support for a graph of objects, with better support for references between
different model elements. In particular, the inputs for a rule can traverse a "reference" property, which is simply a read-write property on a `@Managed` type that refers
to some other model element.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### The `sonar` and `sonar-runner` plugins have been superseded by an external plugin from SonarQube

SonarQube is an open platform to manage code quality, and in previous versions of Gradle the `sonar` and `sonar-runner` plugin has provided integration
with this analysis tool. An improved plugin is now available directly from the developers of SonarQube. This new plugin supersedes the `sonar` and `sonar-runner` plugins
that are part of the Gradle distribution; the `sonar` and `sonar-runner` plugin are now deprecated and will be removed in Gradle 3.0.

See [the official documentation from SonarQube](http://docs.sonarqube.org/display/SONAR/Analyzing+with+Gradle) for more details.

### Deprecated classes and constants

The following classes have been deprecated and will be removed in Gradle 3.0:

- `org.gradle.api.artifacts.ConflictResolution`.
- `org.gradle.api.artifacts.Module`.
- `org.gradle.api.tasks.javadoc.AntGroovydoc`.
- `org.gradle.api.tasks.scala.AntScalaDoc`.
- `org.gradle.BuildExceptionReporter`.
- `org.gradle.BuildLogger`.
- `org.gradle.BuildResultLogger`.
- `org.gradle.TaskExecutionLogger`.
- `org.gradle.plugins.ide.eclipse.model.EclipseDomainModel`.
- `org.gradle.api.logging.Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER`. This constant is intended to be used only by the Gradle internals, and will be removed from the Gradle API.
- `org.gradle.util.AvailablePortFinder`.  Although this class is an internal class and
not a part of the public API, some users may be utilizing it and should plan to implement an alternative.

### Setting Eclipse project name in beforeMerged or whenMerged hook

Setting the Eclipse project name in `eclipse.project.file.beforeMerged` or `eclipse.project.file.whenMerged` hook provided by the
`Eclipse` plugin has been deprecated. Support for this will be removed in Gradle 3.0

### Adding a one-item List to a FileCollection

In previous versions of Gradle, adding a one-item list of `FileCollection` objects to a `FileCollection` would succeed.

For example:

    FileCollection foo = files()
    foo += [files()]

However, this behavior was never explicitly supported and only worked incidentally because Groovy selected the appropriate method on `FileCollection` to add a
single `FileCollection` object.  In Groovy 2.4, this behavior has changed and no longer selects the correct method
(see "Using the + operator with Iterable objects" under [Upgraded to Groovy 2.4.4](#groovyBreakingChanges)).
As this construct may be in use, we have added a workaround to maintain backwards compatibility, but this unsupported functionality is considered deprecated.

Instead, adding the `FileCollection` without a list should be used:

    FileCollection foo = files()
    foo += files()

## Potential breaking changes

<a name="groovyBreakingChanges"></a>
### Upgraded to Groovy 2.4.4

The Gradle API now uses Groovy 2.4.4. Previously, it was using Groovy 2.3.10. This change should be transparent to the majority of users; however, it can cause minor problems with existing build scripts and plugins.

Please refer to the [Groovy language changelogs](http://groovy-lang.org/changelogs.html) for further details.

#### Groovy property assignment with multiple setter methods

In earlier versions of Groovy, an assignment to a property that has multiple setter methods that each take a different parameter type would select the setter method in a JVM-implementation dependent way.
This is documented in [GROOVY-6084](https://issues.apache.org/jira/browse/GROOVY-6084).

For example:

    class Foo {
        void setProperties(Properties p) { }
        void setProperties(File f) { }
    }

    def foo = new Foo()
    foo.properties = [:]

In Groovy 2.3 and earlier, this would sometimes fail depending on which setter method was selected.

In Groovy 2.4, this always fails with an exception:

    No signature of method: Foo.setProperties() is applicable for argument types: (java.util.LinkedHashMap) values: [[:]]
    Possible solutions: setProperties(java.io.File), setProperties(java.util.Properties), getProperties(), getProperty(java.lang.String), setProperty(java.lang.String, java.lang.Object)

To fix this problem, select the correct method by supplying the appropriate type (in this case, Properties).

#### Using the + operator with Iterable objects

In earlier versions of Groovy, when the '+' operator was used with objects that implement `java.lang.Iterable` and there was not a direct match on the signature
of the `plus()` method, the behavior was not defined and could result in success.

For example:

    class Foo implements Iterable<String> {
        public Foo plus(Foo bar) { }
    }

    Foo foo = new Foo()
    foo += [ new Foo() ]

In Groovy 2.3 and earlier, this may work, invoking the `plus(Foo)` method.

In Groovy 2.4, this always fails with an exception:

    Cannot cast object '[Foo@563a3ada]' with class 'java.util.ArrayList' to class 'Foo' due to: groovy.lang.GroovyRuntimeException: Could not find matching constructor for: Foo(Foo)

To fix this problem, add a `plus()` method that accepts an appropriate `java.lang.Iterable` object so that there is a direct match on the method signature.

### New PMD violations due to type resolution changes

PMD can perform additional analysis for some rules (see above), therefore new violations may be found in existing projects.  Previously, these rules were unable to detect problems
because classes outside of your project were not available during analysis.

### Updated to CodeNarc 0.24.1

The default version of CodeNarc has been updated from 0.23 to 0.24.1. Should you want to stay on older version, it is possible to downgrade it using the `codenarc` configuration:

    dependencies {
       codenarc 'org.codenarc:CodeNarc:0.17'
    }

### Updated to bndlib 2.4.0

The built-in [OSGi plugin](userguide/osgi_plugin.html) uses the [bndlib](https://github.com/bndtools/bnd) library internally.
The library has been updated from 2.1.0 to [2.4.0](https://github.com/bndtools/bndtools/wiki/Changes-in-2.4.0).

### Improved IDE project name de-duplication

To ensure unique project names in the IDE, Gradle applies de-duplication logic when generating IDE metadata for Eclipse and Idea projects.
This de-duplication logic has been improved such that all projects with non-unique names are now de-duplicated by adding a prefix based on the
parent project(s). For example:

Given a Gradle multiproject build with the following project structure:

    root
    |-foo
    |  \- app
    |
    \-bar
       \- app

The following IDE project name mapping will result:

    root
    |-foo
    |  \- foo-app
    |
    \-bar
       \- bar-app

Duplicate words in a row within the de-duplication prefix are removed from the generated ide project name.

Given a project with the following structure:

    myapp
    |-myapp-foo
    |  \- app
    |
    \-myapp-bar
       \- app

The following IDE project name mapping will result:

    myapp
    |-myapp-foo
    |  \- myapp-foo-myapp-app
    |
    \-myapp-bar
       \- myapp-bar-myapp-app


### Changes to the incubating integration between the managed model and the Java plugins

The Java plugins make some details about the project source sets visible in the managed model, to allow integration between rules based plugins and
the stable Java plugins. This integration has changed in this Gradle release, to move more of the integration into the managed model:

- The `sources` container is no longer added as a project extension. It is now visible only to rules, as part of the managed model.
- `ClassDirectoryBinarySpec` instances can no longer be added to the `binaries` container. Instances are still added to this container by the Java plugins for each source set,
however, additional instances cannot be added. This capability will be added again in a later release, to allow rules based plugins to define arbitrary class directory binaries.
- The Java plugins do not add instances to the `binaries` container until they are required by rules. Previously, the plugins would add these instances eagerly when the
source set was defined.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Andrew Audibert](https://github.com/aaudiber) - Documentation fix
* [Vladislav Bauer](https://github.com/vbauer) - StringBuffer cleanup
* [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Allow user to configure auxclasspath for PMD
* [Alpha Hinex](https://github.com/AlphaHinex) - Allow encoding to be specified for Zip task
* [Brian Johnson](https://github.com/john3300) - Fix AIX support for GRADLE-2799
* [Alex Muthmann](https://github.com/deveth0) - Documentation fix
* [Adam Roberts](https://github.com/AdamRoberts) - Specify minimum priority for PMD task
* [Sebastian Schuberth](https://github.com/sschuberth) - Documentation improvements
* [John Wass](https://github.com/jw3) - Documentation improvements

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
