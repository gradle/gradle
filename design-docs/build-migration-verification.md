# What is this?

The build migration verification feature provides a way to automatically verify whether a build behaves
the same after modifying it in some way. More precisely, it checks whether the build produces
the same _outputs_ (modulo some allowed variance) as before.

The feature should provide the build
master with information about changes detected in the outputs, and should help him make decisions
on whether to accept or reject a migration. For example, this could be done by classifying detected
changes as expected vs. unexpected, or low risk vs. high risk.

Verification might include running the build before and after migration, or it might just compare
two sets of existing outputs. Apart from verifying a set of changes to a build, the feature might
also support the promotion of those changes. An example would be to set a new Gradle version in
`gradle-wrapper.properties`.

# Use cases

## Upgrading to a new Gradle version

In this case, the migration consists of updating the Gradle version from X to Y. Typically
(but not necessarily), Y will be greater than X. In order to make the migration less risky and more
predictable, the feature should allow comparison of:

* the declared outputs of the old and new builds (for example generated archives and their contents)

* the verifications performed by the old and new builds (for example executed tests and their results)

* the performance of the old and new builds (for example total build time and maximum heap size)

## Making changes to a Gradle build

In this case, the migration consists of making changes to any resource involved in the build process. Two
examples are making changes to a Gradle build script, and moving a source directory to another subproject.
In order to make the migration less risky and more predictable, the feature should support similar
comparisons as when upgrading to a new Gradle version (see above). However, it might be necessary to
have more control over what is compared and how strict the comparisons are, because certain changes
in the outputs might be desired.

## Migrating from Maven to Gradle

In order to make a switch from Maven to Gradle less risky and more predictable, the feature should support
checking whether a build produces the same outputs after the migration. Which migration paths are supported
needs further discussion and won't necessarily influence the verification side of things.

## Migrating from Ant to Gradle

Similar to the previous use case, except that the source system is Ant.

# User visible changes

The feature will be implemented as a plugin and a set of tasks.

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


