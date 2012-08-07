# What is this?

The build migration verification feature aims to compare two builds functionally, with a focus on providing confidence that
they are substitutable. That is, this feature can be used to provide information on whether a migration from one build to another introduces unwanted change.

The two builds in question may potentially be anything. Some examples are:

* A Gradle build, executed with two different Gradle versions
* A Maven build compared with a Gradle build
* An Ant build compared with a Gradle build

# Components

The idea can be conceptualised into two separate components; the build outcomes, and the comparison of two sets of outcomes.

## Build Outcomes

A build outcome is an intentionally general concept. The most obvious build outcome will often be the creation of some kind of artifact,
but there are other interesting outcomes that users may want information about. Some examples are:

* Executing tests (where the same classes tested? where the same tests executed? where the results the same?)
* Analysing code (where the same classes analysed? was the configuration the same? where the results the same?)
* A “build run” - e.g. a clean build that compiles, analyses, tests (does this take an equivalent amount of time?)

---

**Questions:**

* Do we want to include things like compile classpaths for compilation and runtime classpaths for test execution?

Changes in these things may not necessarily produce different final outcomes. For example, the compile may succeed and the tests may pass but if there is
an unexpected and unintentional change in a classpath that is something that you are likely to want to know about.

## Comparison

Given two sets of outcomes, both individual outcomes and the set as an entity will need to be compared. 

Outcomes that are logically equivalent shall be compared to each other. For example, both builds produce the same logical artifact. These outcomes can be 
compared to check they are equivalent. However, the goal may not be absolute equivalence but explainable difference. Some examples of explainable difference are:

* Embedded timestamps in outputs (e.g. Groovy classfiles, test result files)
* Environmental context (e.g. system properties in test result files)
* Acceptable path differences

This may influence the information display to be less focussed on a binary yes/no and more focussed on illuminating what the differences are
and providing some insight on the risk of the differences.

The sets of outcomes will need to be compared. If one build has an outcome that the other does not, that will need to be indicated to the user.

# Modelling Build Outcomes

The concept of a model of build outcomes should not be coupled to how that model was “constructed”. A build outcome model may be
constructed by manual specification, or generated in an intelligent and dynamic way.

For Gradle projects, we can likely generate a complete build outcome model without user intervention. For other types of build tools we may
do less automatically. That is, the user may need to specify some or all of the build function model.

Even for the scenario where we can completely generate the build outcomes model automatically, we _may_ still require a level of user intervention.
For example, the user may not wish to compare test execution between two models because they are irreconcilably different and this has been accepted.
However, this may be a function of the comparison and not of the input models.

## Modelling the relationship between two models

Having two models of build outcomes may not be sufficient. It is also necessary to link the outcomes from both builds. That is, we need to know
which functions are logically equivalent so they can be compared.

As with the specification of the build function models themselves, we may be able to determine the associations automatically. For
a comparison between two instances of the same logical Gradle project, the association can be inferred. For other types of
comparison, the association may not be inferrable or may only be weakly inferrable (i.e. low chance of being correct).

# Use cases

## Upgrading to a new Gradle version

In this case, a single Gradle project “executed” by two different Gradle versions is being compared. This is likely to be undertaken by someone wanting to upgrade the Gradle version used by their project and are seeking reassurance
that things will be ok after they upgrade.

### Upgrades that require changes

The potential upgrade may include changes to the configuration of the build. New Gradle versions introduce deprecations and changes, and as such
users will want to be able to verify that the build functions the same after making any accommodations for changes in the new version.

## Making speculative changes to a Gradle build

In this case, a single Gradle project is being compared before/after some change is made to the build configuration, which does not include a change
in the Gradle version used to build. This is likely to be undertaken by someone wanting to “test” a change to the build.

This is largely the same as the “Upgrading Gradle” case, except there may be intentional differences. That is, there may be expected difference
and the difference is what is being verified. There will also be the case of wanting to make changes that have no functional impact (e.g. removing
redundant configuration).

## Migrating from another tool to Gradle

In this case, a project is looking to move from some tool to Gradle. This feature can be used to illuminate the process and potentially provide a
checkpoint of when it is complete.

Ideally, the comparison phase will be agnostic to the “source” of the build outcome model. That is, the comparison phase compares like outcomes
 and functions can be modelled in a general way that is source agnostic. For example, the creation of a JAR archive can be modelled in a way that is
 not unique to the build system that created it. Therefore, working with a different system is largely about constructing a build function model for
 that system/build.

Another difference may be the lifecycle of the comparison process. For a Gradle-to-Gradle “migration”, the timeframe is likely to be short as
there is not likely to be significant development involved. For a NonGradle-to-Gradle “migration”, there may be significant development involved
(i.e. to develop the Gradle build) which would imply the comparison will be run over time. The user may wish to use this feature to track
the progress of the development of the Gradle build by periodically running the comparison.

### Maven

Given Maven's predictable operation and well defined model, it may be possible to generate a build function model automatically. Given that we
intend to create functionality for interpreting a Maven model (to either port it to Gradle or integrate with it), this seems reasonable.

There may still need to be manual intervention to deal with things like Maven plugins that we don't understand.

### Ant

Given Ant's lack of a model, generating a build outcome model for an Ant build automatically may not be possible. What we will do is guess that the Ant build has the 
same outcomes as the build it is being compared to. 

### Something else

There should be a way to construct a build outcome model manually. Therefore, we could potential work with any system.

This is not a high priority.

# The “result”

It may not be desirable to “fail the comparison” if there is any difference; there may be some expected or accepted differences. However, it would be desirable to give the user a yes/no answer. This would mean that:

1. The goal *has* to be complete parity
2. The expected differences are specifiable
3. We don't aim for a yes/no answer

The third option seems the most preferable.

Detailed information can be presented as an HTML report.

# What is to be compared

Initially, two types of outcomes will be compared:

## Archives

The binary contents of archives will be compared and the individual contents. 

It may be necessary to understand certain types of contents. For example, it may be necessary to compare .class files in a certain way to accommodate for volatile data (e.g. timestamps).

## Test Execution

Only the output of the execution will be considered. This includes:

* Which tests were executed
* The results of each test

# Integration test coverage

## General

The feature can be tested by verifying migrations whose outcome is known.

## Upgrading Gradle Versions

It may be difficult to test “failed” comparisons for the “upgrade Gradle” case. However, given that this is just a special configuration where both from/to builds are the same, the “failed” comparison path can be tested by comparing two non equivalent builds. Another alternative would be to somehow inject a difference between the two.

## Other comparisons

Builds that are known to be equivalent can be compared. Builds that are known to be non equivalent can be compared. In short, the expected result is known and that can be verified as the actual result.

# Implementation approach

## User Interface

The functionality is packaged as one or more plugins. The plugin, amongst other things, adds a task (e.g. `compareBuilds`) that invokes the comparison process.

There will need to be other tasks and model elements that feed into this comparison task. At the least, there will need to be two other tasks that actually invoke the builds to be compared. There may be separate tasks that generate the model of what is to be compared without actually building creating what is to be compared.

### Modification free comparison

It is desirable to support a mode of operation where the user does not need to modify their build to use this functionality. This could be achieved if Gradle can 
infer that a plugin should be applied to fullfil what it was asked to do and if the Gradle project model is suitably externally configurable.

For example, the user could test a Gradle upgrade by executing:

    ./gradlew compareBuild toVersion=1.3

This would implicitly apply the plugin that provides the comparison functionality and compare with Gradle 1.3.

## Sources Inference

A previously stated goal of this functionality is to require little user configuration. As such, the application of the plugin triggers inference of what the comparison should be of. For example, if a `build.xml` is found at the project root then it can be inferred that we are comparing an ant build to the Gradle build. If no trace of another build system can be found (that we understand), we can infer that we are comparing across Gradle versions.

The user should be able to opt-out of the inference (manual configuration) or modify the result.

## Gradle Model Inference

The outcomes for a Gradle build can be inferred by inspecting the build model. This can be done in a cross version compatibility way by using the Tooling API.

## Maven Model Inference

The model for a maven build can likely be inferred for a reasonably high percentage of real world maven builds by understanding the Maven model.

## Ant Model Inference

It will not be possible to infer what the Ant build will be doing. The strategy will be to assume that it does the same as the Gradle build that it is being compared to.

If this is insufficent the user will have to use the provided DSL/API to build the model.

# Open issues

## General

* Does verification always include execution of the "old" and "new" builds, or can it work off preexisting outputs?

* How much knowledge does verification need about the "old" and "new" builds? Is it good enough to have two
sets of outputs (together with a mapping between them), or is additional information required?

* How much information does the feature need about how and by whom an artifact that is to be compared was produced?
Does it need to be able to compare different representations of the same information, like a Gradle JUnit report and
an Ant JUnit report (assuming the report formats are different), or a Gradle JUnit report produced by Gradle version X
and one produced by Gradle version Y?

## Upgrading to new Gradle version

* How are the builds executed? Using the tooling API? Using the Gradle wrapper?

* Are the two builds executed in separate processes, or do they (potentially) share a daemon?

* Does the plugin have to make sure that the two builds use separate Gradle metadata (.gradle directory)?
