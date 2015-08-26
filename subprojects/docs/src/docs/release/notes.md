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

TBD: Currently, managed model works well for defining a tree of objects. This release improves support for a graph of objects, with references between parts of the
model.

- Can use a link property as input for a rule.

### Compiler daemon reuse in continuous builds

Many Gradle compilers are spawned as separate daemons to accommodate special heap size settings, classpath configurations, etc.  These compiler daemons are started on use, and stopped at
the end of the build.  With Gradle 2.8, these compiler daemons are kept running during the lifetime of a continuous build session and only stopped when the continuous build is canceled.
This improves the performance of continuous builds as the cost of re-spawning these compilers is avoided in between builds.

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

<!--
### Example deprecation
-->

## Potential breaking changes

Upgraded to Groovy 2.4.4. This should be transparent to the majority of users, however it can imply some minor breaking changes.
Please refer to the [Groovy language changelogs](http://groovy-lang.org/changelogs.html) for further details.

<!--
### Example breaking change
-->

### Support for PMD versions <5.0

Investigation of our PMD support revealed that newer PMD plugin features do not work with PMD 4.3,
and the PMD check task does not fail when finding violations.
Because of this, we do not recommend the use Gradle with PMD versions earlier than 5.0,
and we have removed any integration test coverage for these versions.

### New PMD violations due to type resolution changes

PMD can perform additional analysis for some rules (see above), therefore new violations may be found in existing projects.  Previously, these rules were unable to detect problems
because classes outside of your project were not available during analysis.

We would recommend that you fix the violations or disable the failing rules.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Vladislav Bauer](https://github.com/vbauer) - StringBuffer cleanup
* [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Allow user to configure auxclasspath for PMD
* [Alpha Hinex](https://github.com/AlphaHinex) - Allow encoding to be specified for Zip task
* [Brian Johnson](https://github.com/john3300) - Fix AIX support for GRADLE-2799
* [Adam Roberts](https://github.com/AdamRoberts) - Specify minimum priority for PMD task

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
