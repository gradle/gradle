## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Zip file name encoding

Gradle will use the default character encoding for file names when creating Zip archives.  Depending on where the archive will be extracted, this may not be the best possible encoding
to use due to the way various operating systems and archive tools interpret file names in the archive.  Some tools assume the extracting platform character encoding is the same encoding used
to create the archive. A mismatch between encodings will manifest itself as "corrupted" or "mangled" file names.

[Zip](dsl/org.gradle.api.tasks.bundling.Zip.html) tasks can now be configured with an explicit encoding to handle cases where the default character encoding is inappropriate.  This configuration
option only affects the file name and comment fields of the archive (not the _content_ of the files in the archive). The default behavior has not been changed, so no changes
should be necessary for existing builds.

### PMD Improvements (i)

#### PMD 'rulePriority' configuration

By default, the PMD plugin will report all rule violations and fail if any violations are found.  This means the only way to disable low priority violations was to create a custom ruleset.

Gradle now supports configuring a "rule priority" threshold.  The PMD report will contain only violations higher than or equal to the priority configured.

You configure the threshold via the [PmdExtension](dsl/org.gradle.api.plugins.quality.PmdExtension.html).  You can also configure the property on a per-task level through
[Pmd](dsl/org.gradle.api.plugins.quality.Pmd.html).

   pmd {
       rulePriority = 3
   }

#### Better PMD analysis with type resolution

Some PMD rules require access to the dependencies of your project to perform type resolution. If the dependencies are available on PMD's auxclasspath,
[additional problems can be detected](http://pmd.sourceforge.net/pmd-5.3.2/pmd-java/rules/java/android.html).

Gradle now automatically adds the compile dependencies of each analyzed source set to PMD's auxclasspath.  No additional configuration should be necessary to enable this in existing builds.

### Managed model improvements

TBD: Currently, managed model works well for defining a tree of objects. This release improves support for a graph of objects, with references between different model
elements.

- Can use a reference property as input for a rule.

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
[withClasspath(java.util.List)](javadoc/org/gradle/testkit/runner/GradleRunner.html#withClasspath(java.util.List)). This classpath is then available to use to locate plugins in a test build via the
[plugins DSL](userguide/plugins.html#sec:plugins_block). The following code example demonstrates the use of the new TestKit API in a test class based on the test framework Spock:

    class BuildLogicFunctionalTest extends Specification {
        @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
        File buildFile
        List<URI> pluginClasspath

        def setup() {
            buildFile = testProjectDir.newFile('build.gradle')
            pluginClasspath = getClass().classLoader.findResource("plugin-classpath.txt")
              .readLines()
              .collect { new File(it).toURI() }
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
                .withClasspath(pluginClasspath)
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

## Potential breaking changes

Upgraded to Groovy 2.4.4. This should be transparent to the majority of users, however it can imply some minor breaking changes.
Please refer to the [Groovy language changelogs](http://groovy-lang.org/changelogs.html) for further details.

### Support for PMD versions <5.0

Investigation of our PMD support revealed that newer PMD plugin features do not work with PMD 4.3,
and the PMD check task does not fail when finding violations.
Because of this, we do not recommend the use Gradle with PMD versions earlier than 5.0,
and we have removed any integration test coverage for these versions.

### New PMD violations due to type resolution changes

PMD can perform additional analysis for some rules (see above), therefore new violations may be found in existing projects.  Previously, these rules were unable to detect problems
because classes outside of your project were not available during analysis.

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

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Andrew Audibert](https://github.com/aaudiber) - Documentation fix
* [Vladislav Bauer](https://github.com/vbauer) - StringBuffer cleanup
* [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Allow user to configure auxclasspath for PMD
* [Alpha Hinex](https://github.com/AlphaHinex) - Allow encoding to be specified for Zip task
* [Brian Johnson](https://github.com/john3300) - Fix AIX support for GRADLE-2799
* [Alex Muthmann](https://github.com/deveth0) - Documentation fix
* [Adam Roberts](https://github.com/AdamRoberts) - Specify minimum priority for PMD task
* [John Wass](https://github.com/jw3) - Documentation fix

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
