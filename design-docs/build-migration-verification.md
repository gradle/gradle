# What is this?

The build migration verification feature aims to compare two builds functionally, with a focus on providing confidence that
they are substitutable.

The two builds in question may potentially be anything. Some examples are:

* A Maven build compared with a Gradle build
* A Gradle build, executed with two different Gradle versions
* A Gradle build, executed before and after a speculative change

The comparison of the function of two different builds can be used for different purposes. Some examples are:

* Verify that Gradle version upgrade does not have a negative effect
* When migrating from another tool to Gradle, verify that are functionally equivalent
* When migrating from another tool to Gradle, track the progress of the migration
* Verify that a change to a Gradle build does not have a negative effect

# Components

The idea can be conceptualised into two separate components; the build _functions_, and the comparison of two sets of functions.

## Build Function

A build function is an intentionally general concept. The most obvious build function will often be the creation of some kind of artifact,
but there are other interesting functions that users will want information about. Some examples are:

* Executing tests (where the same classes tested? where the same tests executed? where the results the same?)
* Analysing code (where the same classes analysed? was the configuration the same? where the results the same?)
* A “build run” - e.g. a clean build that compiles, analyses, tests (does this take an equivalent amount of time?)

## Comparison

Given two sets of functions, both individual functions and the set as an entity will need to be compared. For functions that can be identified
as the same logical function from both sets, individual characteristics will need to be compared (e.g. does the jar have the same contents?).
It will also be important to compare the sets as an entity, answering the question of whether two builds have the same overall function.

For function comparison the goal may not be equivalence, but explainable difference. Some examples of explainable difference are:

* Embedded timestamps in outputs (e.g. Groovy classfiles, test result files)
* Environmental context (e.g. system properties in test result files)
* Acceptable path differences

This may influence the information display to be less focussed on a binary yes/no and more focussed on illuminating what the differences are
and providing some insight on the risk of the differences.

# Modelling Build Functions

The concept of a model of build functions should not be coupled to how that model was “constructed”. A build function model may be
constructed by manual specification, or generated in an intelligent and dynamic way.

For Gradle projects, we can likely generate a complete build function model without user intervention. For other types of build tools we may
do less automatically. That is, the user may need to specify some or all of the build function model.

Even for the scenario where we can completely generate the build function model automatically, we _may_ still require a level of user intervention.
For example, the user may not wish to compare test execution between two models because they are irreconcilably different and this has been accepted.
However, this may be a function of the comparison and not of the input models.

## Modelling the relationship between two models

Having two models of build functions may not be sufficient. There may need to be a model of how the functions from either side relate to each
other.

As with the specification of the build function models themselves, we may be able to generate the relationship model to varing degrees. For
a comparison between two instances of the same logical Gradle project, the relationship can likely be completely generated. For other types of
comparison the relationship between functions on either side may not be so obvious.

# Use cases

## Upgrading to a new Gradle version

In this case, a single Gradle project “executed” by two different Gradle versions is being compared. This is likely to be undertaken by someone wanting to upgrade the Gradle version used by their project and are seeking reassurance
that things will be ok after they upgrade.

The potential upgrade may include changes to the configuration of the build. New Gradle versions introduce deprecations and changes, and as such
users will want to be able to verify that the build functions the same after making any accommodations for changes in the new version.

## Making speculative changes to a Gradle build

In this case, a single Gradle project is being compared before/after some change is made to the build configuration, which does not include a change
in the Gradle version used to build. The is likely to be undertaken by someone wanting to “test” a change to the build.

This is largely the same as the “Upgrading Gradle” case, except there may be intentional differences. That is, there may be expected difference
and the difference is what is being verified. There will also be the case of wanting to make changes that have no functional impact (e.g. removing
redundant configuration).

## Migrating from another tool to Gradle

In this case, a project is looking to move from some tool to Gradle. This feature can be used to illuminate the process and potentially provide a
checkpoint of when it is complete.

Ideally, the comparison phase will be agnostic to the “source” of the build function model. That is, the comparison phase compares like functions
 and functions can be modelled in a general way that is source agnostic. For example, the creation of a JAR archive can be modelled in a way that is
 not unique to the build system that created it. Therefore, working with a different system is largely about constructing a build function model for
 that system/build. It is also less likely that the relationship between the two models can be determined completely automatically.

Another difference may be the lifecycle of the comparison process. For a Gradle-to-Gradle “migration”, the timeframe is likely to be short as
there is not likely to be significant development involved. For a NonGradle-to-Gradle “migration”, there may be significant development involved
(i.e. to develop the Gradle build) which would imply the comparison will be run over time. The user may wish to use this feature to track
the progress of the development of the Gradle build by periodically running the comparison.

### Maven

Given Maven's predictable operation and well defined model, it may be possible to generate a build function model automatically. Given that we
intend to create functionality for interpreting a Maven model (to either port it to Gradle or integrate with it), this seems reasonable.

There may still need to be manual intervention to deal with things like Maven plugins that we don't understand.

### Ant

Given Ant's lack of a model, generating a build function model for an Ant build automatically may not be possible.

### Something else

There should be a way to construct a build function model manually. Therefore, we could potential work with any system.

# User visible changes

It's not clear at this time, how users will interact with this feature.

The final output is likely to be a HTML report identifying the encountered differences, and explaining the input build function
models.

# Integration test coverage

## General

The feature can be tested by verifying migrations whose outcome is known.

## Upgrading to a new Gradle version

In particular, integration tests should cover the following cases:

* New build produces a different set of archives (e.g. different archives names)

* New build produces archive containing different set of files

* New build produces archive with different file content (e.g. class file differs)

* New build runs different set of tests

* New build produces different test results

* New build takes longer (if it's possible to have a stable test for this)

* New build has a higher maximum heap size (if it's possible to have a stable test for this)

* New build has a higher maximum PermGen size (if it's possible to have a stable test for this)

# Implementation approach

## Upgrading to a new Gradle version

The user adds the `build-migration` plugin to the build's root project.

The user issues a command like:

    gradlew migrate-build from=1.0 to=1.1-rc-1

If `from` is unspecified, the current Gradle version is used.

Note: It should not be necessary to apply a plugin to the build script in order for this to work.
`from` and `to` should be two parameters of the `migrate-build` task. In order to fulfill these
requirements, two new Gradle features (implicit application of plugins from the command line
and task specific command line options) will have to be implemented.

The `migrate-build` task

* Executes the build with Gradle version `from` (and reconfigured build directory(s))

* Executes the build with Gradle version `to` (and reconfigured build directory(s))

* Locates the outputs of the two builds (currently these are defined as the contents of their `archives` configurations)

* Compares the Jar, War, Ear, Zip, and Tar archives produced by the two builds (file paths & contents)

* Compares which tests were executed by the two builds, and what their results were

* Compares key performance numbers of the two builds (total build time, maximum heap size,
maximum PermGen size, potentially more information as provided by `--profile`)

* Logs some progress information to the console

* Logs a high-level verification result to the console

* Generates a detailed HTML report that can be viewed after verification has completed

* Aborts or skips part of the verification when the artifacts generated by the two builds
cannot be found or cannot be mapped to each other

'Jar', 'War', 'Ear', 'Zip', 'Tar', 'JUnit XML report', and 'TestNG XML report' are artifact
types known to the migration verification plugin. The plugin might ship with one task type
per artifact type, and might add one task per artifact to be compared.

Test comparison should work both for JUnit and TestNG tests. It could be implemented by
comparing XML reports, or by comparing what's reported by an (injected) Gradle test listener.

Comparing memory consumption might be tricky. For example, a higher maximum heap size might not
necessarily mean that more heap is required, but just that for some reason, garbage collection
kicked in later.

### Backwards and forward compatibility

The `build-migration` plugin must be able to compare older version of Gradle (possibly
older than the version used to execute the plugin) with newer versions (possibly newer than the version
used to execute the plugin). To make this work, the plugin

* executes the `from` and `to` builds via the tooling API
* queries the builds for information (e.g. what the outputs are and where they are located) via the tooling API

The tooling API will provide a special model containing all information necessary for build migration verification.

# Open issues

## General

* Does verification include execution of the "old" and "new" builds, or can it work off preexisting outputs?

* Who determines what the outputs of a build (or the subset of outputs that should be compared) are, and where
they are located?

* Assuming the feature is implemented as a plugin, where does the plugin and its tasks get executed?
In the old build? The new build? An independent "migration" build?

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


