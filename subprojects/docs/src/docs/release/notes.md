## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Performance Improvements for large projects

The default cache size for file hashes has been increased from 140000 to 400000 entries. This cache is used in the Gradle Daemon to skip hash calculation for files that have not been modified since the previous hash calculation. The cache size will now scale proportionally to the maximum heap size of the Gradle daemon. Gradle 2.8 Daemon will consume more heap memory because of this change. For a 1GB heap, the increase is less than 40 MB. This change will only speed up sub-sequent incremental builds of large projects with more than 140000 files in total because there will be less cache misses in incremental builds. In our tests we saw incremental build times drop to 35-50% of the original incremental build time with such projects.

Some bottlenecks were identified in the persistent cache writing and reading. The previous versions of Gradle used CRC32 checksums to ensure cache integrity. This was replaced with a byte counter which also ensures integrity. In addition, the persistent cache writing and reading was optimized by removing some unnecessary calls to RandomAccessFile.length(). These improvements speed up persistent cache writing and reading so that they aren't shown as hotspots or bottlenecks in the performance tests we have been profiling.
Since the persistent cache storage format changed, the metadata cache version has been updated. This means that Gradle 2.8 will create a new cache directory called `~/.gradle/caches/modules-2/metadata-2.16`.

### Zip file name encoding

Gradle will use the default character encoding for file names when creating Zip archives.  Depending on where the archive will be extracted, this may not be the best possible encoding
to use due to the way various operating systems and archive tools interpret file names in the archive.  Some tools assume the extracting platform character encoding is the same encoding used
to create the archive. A mismatch between encodings will manifest itself as "corrupted" or "mangled" file names.

[Zip](dsl/org.gradle.api.tasks.bundling.Zip.html) tasks can now be configured with an explicit encoding to handle cases where the default character encoding is inappropriate.  This configuration
option only affects the file name and comment fields of the archive (not the _content_ of the files in the archive). The default behavior has not been changed, so no changes
should be necessary for existing builds.

### PMD 'rulePriority' configuration (i)

By default, the PMD plugin will report all rule violations and fail if any violations are found.  This means the only way to disable low priority violations was to create a custom ruleset.

Gradle now supports configuring a "rule priority" threshold.  The PMD report will contain only violations higher than or equal to the priority configured.

You configure the threshold via the [PmdExtension](dsl/org.gradle.api.plugins.quality.PmdExtension.html).  You can also configure the property on a per-task level through
[Pmd](dsl/org.gradle.api.plugins.quality.Pmd.html).

    pmd {
        rulePriority = 3
    }

### Better PMD analysis with type resolution (i)

Some PMD rules require access to the dependencies of your project to perform type resolution. If the dependencies are available on PMD's auxclasspath,
[additional problems can be detected](http://pmd.sourceforge.net/pmd-5.3.2/pmd-java/rules/java/android.html).

Gradle now automatically adds the compile dependencies of each analyzed source set to PMD's auxclasspath.  No additional configuration should be necessary to enable this in existing builds.

### Managed model improvements

#### Scalar collections

The managed model now supports collections of scalar types as properties. This means that it is possible to use as element type of a `Set` or a `List`:

 - a JDK Number type (`Integer`, `Double`, ...)
 - a `Boolean`
 - a `String`
 - a `File`
 - or an enumeration type


    @Managed
    interface User {
        Set<String> getGroups();
    }

Properties of scalar types are available as read-only properties, in which case they default to an empty collection, or as read-write properties, in which case they default to `null`.
Read-only properties are similar to final values: they can be mutated as long as they are the subject of a rule. Read-write properties can also be mutated, but they are not final: the
collection can be overwritten during a mutation phase.

A read-only (non nullable) property is created by defining only a setter, while a read-write property is created by defining both a setter and a getter:

    @Managed
    interface User {
        Set<String> getGroups();
        void setGroups(Set<String> groups);
    }

#### Support for managed FunctionalSourceSet's
This release facilitates adding source sets (`LanguageSourceSet`) to arbitrary locations in the model space through the use of the `language-base` plugin and `FunctionalSourceSet`'s.
Having direct support for `FunctionalSourceSet`'s as both top level model elements and as properties of managed types allows build and plugin authors to strongly model things that use `LanguageSourceSet`'s.
This kind of strongly typed modelling also allows build and plugin authors to access `LanguageSourceSet`'s in a controlled and consistent way using Rules.

Adding a top level FunctionalSourceSet is a simple as:

```groovy
model {
    sources(FunctionalSourceSet)
}
```

or from a `RuleSource`

```groovy
@Model
void sources(FunctionalSourceSet sources) { .. }
```


Here's an example of creating a managed type with `FunctionalSourceSet` properties.

```groovy
@Managed
interface BuildType {
    FunctionalSourceSet getSources()
    FunctionalSourceSet getInputs()
    ModelMap<FunctionalSourceSet> getComponentSources()
}
```

TBD: Currently, managed model works well for defining a tree of objects. This release improves support for a graph of objects, with references between different model
elements.

- Can use a reference property as input for a rule.

#### Internal views for components

It is now possible to attach internal information to an unmanaged `ComponentSpec`. This way a plugin can make some data about its components exposed to build logic via a public component type,
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
        @Finalize
        void mutateInternal(ModelMap<SampleLibrarySpecInternal> sampleLibs) {
            sampleLibs.each { sampleLib ->
                sampleLib.internalData = "internal"
            }
        }
    }

For internal view types that do not not extend `ComponentSpec`, targeting components can be achieved via `ComponentSpecContainer.withType()`:

    @Finalize
    void finalize(ModelMap<SampleLibrarySpec> sampleLibs) {
        sampleLibs.withType(SomeInternalView).all { sampleLib ->
            // ...
        }
    }


### Rule based model configuration

Interoperability between legacy configuration space and new rule based model configuration has been improved. More specifically, the `tasks.withType(..)` construct allows legacy configuration tasks
to depend on tasks created via the new rule based approach. See [this issue](https://issues.gradle.org/browse/GRADLE-3318) for details.

### Faster compilation for continuous builds

Many Gradle compilers are spawned as separate daemons to accommodate special heap size settings, classpath configurations, etc.  These compiler daemons are started on use, and stopped at
the end of the build.  With Gradle 2.8, these compiler daemons are kept running during the lifetime of a continuous build session and only stopped when the continuous build is canceled.
This improves the performance of continuous builds as the cost of re-spawning these compilers is avoided in between builds.

Note that this improvement reduces the overhead of running forked compilers in continuous mode.  This means that it is not relevant for non-continuous builds or builds where the compiler
is run in-process.  In practical terms, this means this improvement affects the following scenarios:

- Java compiler - when options.fork = true (default is false)
- Scala compiler - when scalaCompileOptions.useAnt = false (default is true)
- Groovy compiler - when options.fork = true (default is true)

The Play Routes compiler, Twirl compiler, Javascript compiler, and Scala compiler always run as forked daemons, so compiler reuse will always
be used for those compilers when in continuous mode.

### TestKit API exposes method for injecting classes under test

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

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### AvailablePortFinder

The class `org.gradle.util.AvailablePortFinder` has been deprecated and will be removed in the next version of Gradle.  Although this class is an internal class and
not a part of the public API, some users may be utilizing it and should plan to implement an alternative.

### Setting Eclipse project name in beforeMerged or whenMerged hook

Setting the Eclipse project name in `eclipse.project.file.beforeMerged` or `eclipse.project.file.whenMerged` hook provided by the
`Eclipse` plugin has been deprecated.

## Potential breaking changes

### Upgraded to Groovy 2.4.4

The Gradle API now uses Groovy 2.4.4. Previously it was using Groovy 2.3.10. This change should be transparent to the majority of users, however it can imply some minor breaking changes.
Please refer to the [Groovy language changelogs](http://groovy-lang.org/changelogs.html) for further details.

### New PMD violations due to type resolution changes

PMD can perform additional analysis for some rules (see above), therefore new violations may be found in existing projects.  Previously, these rules were unable to detect problems
because classes outside of your project were not available during analysis.

### Updated to CodeNarc 0.24.1

The default version of CodeNarc has been updated from 0.23 to 0.24.1. Should you want to stay on older version, it is possible to downgrade it using the `codenarc` configuration:

    dependencies {
       codenarc 'org.codenarc:CodeNarc:0.17'
    }

### Improved IDE project naming deduplication

To ensure unique project names in the IDE, Gradle applies a deduplication logic when generating IDE metadata for Eclipse and Idea projects.
This deduplication logic has been improved. All projects with non unique names are now deduplicated. here's an example for clarification:

Given a Gradle multiproject build with the following project structure

    root
    |-foo
    |  \- app
    |
    \-bar
       \- app

results in the following IDE project name mapping:

    root
    |-foo
    |  \- foo-app
    |
    \-bar
       \- bar-app

### Changes to incubating integration between software model and the Java plugins

TBD

The behaviour of `ClassDirectoryBinarySpec` instances has changed:

- `sources` container is no longer added as a project extension. It is visible only as part of the software model.
- `ClassDirectoryBinarySpec` instances can no longer be created using the `binaries` container. Instances are added to this container by the Java plugins for each source set,
however, additional instances cannot be added. This capability will be added again in a later release.
- Instances are not added to the `binaries` container eagerly by the Java plugins.

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
