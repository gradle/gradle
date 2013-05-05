# What is this?

The build comparison functionality allow users to compare two builds functionally, identifying differences in the observable outcomes. There are three primary use cases that this functionality will serve as that basis for:

1. Changing the version of Gradle used to build a project (typically a new version, i.e. **upgrading**)
2. **Migrating** a project that is built with another tool (e.g. Ant, Maven or anything else) to be built with Gradle
3. **Testing a change** to a Gradle project's configuration by comparing before and after the change

The above list is in order of importance.

By identifying the differences, or confirming that there are no differences, between two builds the user can upgrade/migrate/change with more confidence and reduced risk.

# Components

The idea can be conceptualised into two separate concerns; identifying and producing build outcomes, and the comparison of the outcomes.

## Build Outcomes

A build outcome is an intentionally general concept. The most obvious build outcome will often be the creation of some kind of artifact (e.g. creation of a zip file),
but there are other interesting outcomes that users may want information about. Some examples are:

* Executing tests (where the same classes tested? where the same tests executed? where the results the same?)
* Analysing code (where the same classes analysed? was the configuration the same? where the results the same?)
* A “build run” - e.g. a clean build that compiles, analyses, tests (does this take an equivalent amount of time?)

These are not necessarily outcomes we will support. They are provided as examples of less obvious outcomes that we may support at some time.

## Comparison

The comparison of two builds is really a comparison of their comparable outcomes. 

The goal may not be absolute equivalence but, explainable difference. Some examples of explainable difference are:

* Embedded timestamps in outputs (e.g. Groovy classfiles, test result files)
* Environmental context (e.g. system properties in test result files)
* Acceptable path differences

The information display will not be focussed on a binary yes/no, but on illuminating what the differences are.

# Modelling Build Outcomes

To support comparing outcomes from two different build systems, a build outcome is agnostic to the build system that produced it. For example, an Ant build and a Gradle build can produce a zip file that is logically equivalent (i.e. is intended to represent the same “project”) and it must be possible to compare these outcomes without regard of the build system used.

## Identifying the outcomes of a build

For Gradle projects, we can identify build outcomes without user intervention by using the Tooling API to inspect the model (specifics on this later in the document). For other types of build tools we may do less automatically. That is, the user may need to specify what the outcomes are.

Even for the scenario where we can identify the outcomes, we _may_ still require a level of user intervention. For example, the user may not wish to compare test execution between two builds because they are irreconcilably different and this has been accepted.

## Idenitifying comparable outcomes

Given two sets of outcomes, which outcomes are meaningfully comparable to each other needs to be identified. 

The general strategy will be to centre the identification of comparable outcomes based on outcome IDs. In most cases, the ID of an outcome will be derived from the path of the task that produced it. IDs will be internal and opaque.

When identifying the outcomes of a Gradle build, the outcome IDs will be deterministic. This means that when comparing two instances of the same build configuration, comparable outcomes are those that have matching outcome IDs. 

When comparing a Gradle build to something else, it will not be possible to use ID equality. Given that users will have to model the outcomes of the other build in the host build (the build that executes the comparison process), part of the modelling will be the outcome's association with an outcome on the Gradle side. This can be done via a Spec instance (more on this later).

# The “result”

It is important to communicate that a comparison failure may not be an actual failure. Builds are surprisingly volatile. The main contributor to volatility is the use of timestamps. There are also other contributors such as system properties and other environmental factors.

There are 2 strategies to dealing with this:

1. Expose enough information about the comparison result for users to make an informed assessment of the difference
2. Allow fine grained control over the comparison process to compensate for volatility

How to “deal” with each strategy is dependent on the nature of the outcome.

# What is to be compared (i.e. outcomes)

The majority of builds produce one or more file artifacts, and this is their primary purpose. The comparison functionality will focus on comparing files. Furthermore, it is generally not practical to “see inside” a system other than Gradle; the only feasible means of communication with an “other” system is via the file system. 

It may be desirable to compare things that are not naturally files. For the case where a system other than Gradle is involved, the strategy will be to have the other system serialise a representation of the outcome of interest to the file system for comparison. For example, the details of test execution could be compared by comparing JUnit XML output or internal compile classpaths can be compared by writing the classpath out to a text file with each entry to one line.

For the case of Gradle to Gradle comparisons it may not be necessary to communicate via the filesystem; we can compare models returned by the tooling API if necessary. However, at least initially, we will only be interested in comparing things that are available via the filesystem.

## Archives

In the initial version of this functionality, only the binary contents of archives will be compared and the individual contents. 

Aspects to be compared are:

* The list of entries (to identify entries that only exist in one of the archives)
* The checksum of matching entries (to indicate that the content is different)
* Size (variant on above, more informative than just saying the content is different)

### Content-wise comparison

Files can contain volatile information, that will always produce a difference when compared with a logically equivalent file. Files may contain things such as:

* Timestamps
* Environmental properties
* Relative paths

Assuming that we want to compensate for this, it will be necessary to employ pluggable comparison strategies for different kinds of files. The strategy to use may be inferred from the file extension, but likely this will be insufficient for the majority of cases. The user must be given control of this, as it will not be possible for Gradle to automatically identify such expected differences automatically in all cases.

There are two possible strategies that can be used to deal with this fact:

1. Parsed comparison
1. Filtered comparison

A “parsed comparison” would involve effectively deserialising the file into an in memory representation that can be compared in a flexible way. A “filtered comparison” would involve transforming the file before comparison to _remove_ aspects that may be acceptably different.

For text files, a filtered comparison would be reasonably easy to facilitate by allowing the user to provide regular expression based search/replace.

There is a third option which is effectively a combination of the two. In this approach, the file is parsed and then a filtered version written back out. This could be used to deal with the fact that the Groovy compiler embeds timestamps into class files. The class file could be read with something like ASM, the timestamp field removed, and then written back to disk for conversion. The key difference is that in the end filesystem objects are compared instead of in memory objects.

# Integration test coverage

## General

The feature can be tested by verifying migrations whose outcome is known. The comparison can be run, and the HTML report inspected to understand the result.

## Upgrading Gradle Versions

It may be difficult to test “failed” comparisons for the “upgrade Gradle” case. However, given that this is just a special configuration where both source and target builds are the same, the “failed” comparison path can be tested by comparing two non equivalent builds.

## Other comparisons

Builds that are known to be equivalent can be compared. Builds that are known to be non equivalent can be compared. In short, the expected result is known and that can be verified as the actual result.

# Implementation

## User Interaction

The functionality is packaged in plugins and tasks, as per normal. The plugins/tasks are part of the _host_ build, which is the build that invokes the comparison. This does not need to be the build being compared, but will often be. The user adds the plugins/tasks to the host build, then invokes a task which invokes the builds to be compared.

There are 5 steps to a comparison:

1. Create and fulfill a set of build outcomes (once for each build in the comparison, i.e. twice)
2. Associate outcomes from either side as being comparable
3. Specify/configure the comparison strategies
4. Execute the comparison, producing a result
5. Communicate the result to the user (i.e. render a report)

Parts 1 and 2 are specific to what is being compared (i.e. there will be Gradle and non Gradle versions). Parts 3 through 5 are mostly agnostic and can be used for all comparisons.

### `CompareBuilds` *task*

The `CompareBuilds` task is the task that users will “invoke” to perform a comparison. It will depend on other tasks.

Configuration:

1. Model representing the fulfilled build outcomes of each build to be compared (buildable model objects, discussed later)
2. A strategy that _associates_ individual build outcome objects identifying that they are comparable (`BuildOutcomeAssociator`)
3. A strategy that can provide comparison strategies for types of associated build outcomes, that produces model objects describing the comparison result (`BuildOutcomeComparatorFactory`)
4. A strategy for rendering the comparison result (`BuildComparisonResultRenderer`)

The fact that the configuration is focussed on providing strategies for the comparison alludes to the comparison being very extensible. By convention, the strategies will be provided and preconfigured. These objects will allow a certain amount of configurability, but should the user need full control they could provide their own strategies.  

Execution:

Given the above strategies, this task does the following…

1. Asks the `BuildOutcomeAssociator` for a model object (`BuildComparisonSpec`) that describes how the builds should be compared (based on the build outcomes)
2. Based on the `BuildComparisonSpec`, uses the `BuildOutcomeComparatorFactory` to produce a model object that describes the results of the comparison (`BuildComparisonResult`)
3. Based on the `BuildComparisonResult`, asks the `BuildComparisonResultRenderer` to render out a report

This task will be a `VerificationTask`. The `BuildComparisonResult` object will have an `isBuildsAreIdentical()` method. If this is false, the task will fail (unless `VerificationTask.ignoreFailures = true`).

### `GenerateGradleBuildOutcomes` *task*

The `GenerateGradleBuildOutcomes` task will be responsible for providing a fulfilled set of build outcomes describing a Gradle build. That is, it executes a Gradle build and describes the outcomes.

Configuration:

1. The filesystem location of the to-be-built Gradle project (defaults to current project)
2. The target Gradle version of the to-be-built Gradle project (default to version in use)
3. The invocation details (i.e. tasks, properties etc.)

The Gradle invocation cannot be strongly modelled with `StartParameter` as this object is coupled to the current version. This task is a general interface to executing with an arbitrary Gradle version, so the invocation will be configured based on the command line interface.

The task will expose a `Buildable` property (something like `Set<BuildOutcome>`) that describes the fulfilled build outcomes.

This task will use the Tooling API to execute the build, and to inspect the build model to discover the outcomes. This implies that the target build is Gradle 1.2 or higher as the Tooling API provider for earlier Gradle versions will not be able to provide the model that will be used to discover the outcomes. Gradle versions older than 1.2 will be handled differently.

### `GenerateInferredGradleBuildOutcomes` *task*

The `GenerateInferredGradleBuildOutcomes` task is the pre Gradle 1.2 version of `GenerateGradleBuildOutcomes`. It exposes the same kind of invocation configuration as `GenerateGradleBuildOutcomes`, with the addition of accepting a `GenerateGradleBuildOutcomes` task to infer from (and depend on).

The task will expose a `Buildable` property (something like `Set<BuildOutcome>`) that describes the fulfilled build outcomes, inferred from the outcomes of the specified `GenerateGradleBuildOutcomes` task.
  
This task will use the Tooling API to execute the build.

### `GenerateBuildOutcomes` *task*

The `GenerateBuildOutcomes` task is used for non Gradle builds in a comparison. It wraps an `ExecSpec` and exposes a builder style DSL for specifying the outcomes. For example…

    task sourceComparableBuild(type: GenerateBuildOutcomes) {
      exec {
        commandLine "mvn", "assemble"
        // configure process to launch
      }
      outcomes {
        zip("target/foo-1.0.jar") {
          // more config
        }
        junitTestXml("target/test-reports")
      }
    }

(the “outcomes DSL” above is a quick sketch, not a well thought out specification).

The task will expose a `Buildable` property (something like `Set<BuildOutcome>`) that describes the fulfilled build outcomes, as specified by the builder DSL.

### `compare-gradle-upgrade` plugin

The `compare-gradle-upgrade` plugin adds the following tasks:

1. `sourceGradleBuild(type: GenerateGradleBuildOutcomes)`
2. `targetGradleBuild(type: GenerateGradleBuildOutcomes)`
3. `compareGradleUpgrade(type: CompareBuilds)`

The (buildable) build outcome sets from #1 and #2 are wired into #3. The strategies for #3 are preconfigured for all the outcome types that the _host_ build version of Gradle is configured for and to compare outcomes with the same ID. 

The #1 and #2 tasks are preconfigured to be the same build as the *host* build (i.e. same Gradle version), and to execute `clean assemble`. The user is expected to configure `targetGradleBuild.gradleVersion` to be the version of Gradle that they wish to upgrade to.

#### Auto detecting latest version

We could hit `http://services.gradle.org/versions/current` and preconfigure the `targetGradleBuild` to be the latest release.

### `compare-gradle-migration` plugin

The `compare-gradle-upgrade` plugin adds the following tasks:

1. `sourceOtherBuild(type: GenerateBuildOutcomes)`
2. `targetGradleBuild(type: GenerateGradleBuildOutcomes)`
3. `compareGradleUpgrade(type: CompareBuilds)`

The (buildable) build outcome sets from #1 and #2 are wired into #3. 

The association strategy (i.e. for identifying comparable outcomes) used by #3 is derived from the configuration of #1. That is, as part of the configuration of #1 that specifies what the outcomes are, they can at that point be associated with their comparable counterpart from the Gradle side.

For example…

    sourceOtherBuild {
      outcomes {
        zip("target/foo-1.0.jar") {
          compareTo { BuildOutcome gradleOutcome ->
            it instanceif ArchiveBuildOutcome && it.relativePath == "build/foo-1.0.jar"
          }
        }
        junitTestXml("target/test-reports") // no counterpart, uncompared
      }
    }

This is mixing concerns to some extent, but it's a convenient place (for the user) for this configuration.

# Making upgrading more convenient

There are two complimentary Gradle features in the pipeline that will make it more convenient to use this feature for Gradle upgrades. If we have the ability to implicitly apply plugins and configure the model based on the invocation then a user will be able to try a Gradle upgrade without having to modify their build script.

Given the following invocation:

    ./gradlew compareGradleUpgrade targetGradleBuild.gradleVersion=1.4

We would apply the `compare-gradle-upgrade` plugin, and have the comparison compare against Gradle 1.4.

Moreover, if it were possible to update the wrapper without modifying the build script then the user could actually perform the upgrade without modification.

    ./gradlew compareGradleUpgrade targetGradleBuild.gradleVersion=1.4

(check comparison report)

    ./gradlew wrapper wrapper.gradleVersion=1.4

# Open issues
