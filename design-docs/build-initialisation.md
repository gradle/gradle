
This feature covers the initial setup of a Gradle build, either from an existing source tree or from scratch. The result of using
this feature is a Gradle build that is usable in some form. In some cases, the build will be fully functional, and in other
cases, the build will require some additional manual work to be completed before it is ready to be used.

When used with an existing build, this feature is intended to integrate closely with the build comparison feature, so that the
initial build is created using the build initialization feature, and then the build comparison feature can be used to drive
manual changes to the Gradle build, and to inform when the Gradle build is ready to replace the existing build.

# Use cases

1. I have a multi-module Maven build that I want to migrate to Gradle.
2. I have an Ant based build that I want to migrate to Gradle.
3. I have an Ant + Ivy based build that I want to migrate to Gradle.
4. I have a Make based build that I want to migrate to Gradle.
5. I have an Eclipse based build that I want to migrate to Gradle.
6. I have an IDEA based build that I want to migrate to Gradle.
7. I want to create a Java library project from scratch.
8. I want to create a {Groovy, Scala, C++, Javascript, Android} library from scratch.
9. I want to create a {Java, native, Android, web} application from scratch.
10. I want to create a project that follows my organisation's conventions from scratch.
11. I want to create an organisation specific project type from scratch.

# Implementation approach

A new plugin called `build-init` will be added. Generally, this plugin will be used in a source tree
that may or may not be empty and that contains no Gradle build. The plugin will infer the project
model from the contents of the source tree, as described below, and generate the necessary Gradle build
files and supporting files.

## User interaction

From the command-line:

1. User downloads and installs a Gradle distribution.
2. User runs `gradle init` from the root directory of the source tree.
3. User runs the appropriate build comparison task from the root directory.
4. User modifies Gradle build, if required, directed by the build comparison report.

From the IDE:

1. User runs `initialize Gradle build` action from UI and selects the source tree to initialize.
2. User runs the appropriate build comparison task from the root directory.
3. User modifies Gradle build, if required, directed by the build comparison report.

## Inferring the project model

This can start off pretty basic: if there is a source file with extension `.java`, then the Java plugin is required, if there is a
source file with extension `.groovy`, then the Groovy plugin is required, and so on.

The inference can evolve over time:
* if the source file path contains an element called `test`, then assume it is part of the test source set.
* parse the source to extract package declarations, and infer the source directory roots from this.
* parse the source import statements and infer the test frameworks involved.
* parse the source import statements and infer the project dependencies.
* infer that the project is a web app from the presence of a `web.xml`.
* And so on.

The result of the inference can potentially be presented to the user to confirm (or they can just edit the generated build file). When nothing
useful can be inferred, the user can select from a list or assemble the model interactively.

# Story: Add further project types

Add the following types:

- `gradle-plugin`
- `web-application`
- `java-application`
- `cpp-library`
- `cpp-application`

## Implementation

- Allow a build template to depend on another template. For example, there is a base template that generates the `settings.gradle`

## Test coverage

- For each build type, generate and execute the build.

# Story: Update the user guide Java tutorial to use the `init` task

# Story: Gradle help message informs user how to init a build

This story adds some helpful output when the user attempts to run Gradle in a directory that does not contain a
Gradle build, to let the user know how to create a new build or convert an existing Maven build:

* Introduce an internal service through which a plugin can contribute help messages.
* Change the `help` task to use this to assemble the help output.
* Introduce an internal service through which a plugin can contribute error resolutions.
* Change the `ExceptionAnalyser` implementations to use this.
* Change the `build-init` plugin to add help messages and error resolutions for empty builds and
  builds that contain a `pom.xml`.

## Test coverage

* The output of `gradle` or `gradle help` in an empty directory includes a message informing the user that there is no Gradle
  build defined in the current directory, and that they can run `gradle init` to create one.
* The output `gradle` or `gradle help` in a directory with no Gradle files and a `pom.xml` includes a message informing the
  user that they can convert their POM by running `gradle init`.
* The `* Try ...` error message from `gradle someTask` in an empty directory includes a similar message to the help output.

# Story: User updates wrapper to use the most recent nightly, release candidate or release

This story adds the ability for the user to easily update the build to use the most recent release of a given type:

* Change the `wrapper` plugin to add `useNighty`, `useReleaseCandidate` and `useRelease` tasks of type `Wrapper`
* Copy the configuration logic from `$rootDir/gradle/wrapper.gradle`
* Change the `wrapper` plugin to add task rules for each of these tasks to the root project.
* Update the Gradle website's `downloads`, `nightly` and `release-candidate` pages to mention you can simply run `gradle use${Target}`.

## Test coverage

* Running `gradle useNightly` uses a nightly build
* Running `gradle useReleaseCandidate` uses the most recent release candidate, if any.
* Running `gradle useRelease` uses the most recent release.

# Story: Users updates wrapper

Make it convenient to update the wrapper implementation (not the Gradle runtime that the wrapper uses). Currently, you need to run the `wrapper` task twice.

* Publish the wrapper jar as part of the release process.
* Change the wrapper task to download and install a wrapper implementation. Should probably default to the wrapper from the target Gradle version or
  possibly the most recent wrapper that is compatible with the target Gradle version.

# Story: Handle existing Gradle build files

Better handle the case where there is already some Gradle build scripts.

* When `init` is run and any of the files that would be generated already exist, warn the user and do not
  overwrite the file.

# Story: Improve POM conversion

TBD - fix issues with POM conversion to make it more accurate

- Fix indenting of generated `build.gradle`

## Test coverage

* Decent error message for badly formed `pom.xml`.

# Story: Build initialisation prompts user for inputs

Some build templates are configurable (eg project names, packages, etc). Add an interactive mechanism for the user to
provide these inputs.

Some candidates for input:

- Which build type to use
- Project name
- Package name
- Version of Java/Groovy/Scala to use
- Which test framework to use

# Story: User adds a project to an existing build

TBD

# Story: Create a project with custom convention from scratch

Add a resolution mechanism which can resolve build type to an implementation plugin, similar to the plugin resolution mechanism.

# Story: Create an organisation specific project from scratch

Allow the resolution mechanism to search an organisation-specific repository.

# Story: User manually completes migration with help from the build comparison plugin

* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

# Story: Expose build initialisation through the tooling API

* Extend the tooling API to add the concept of actions. Running a build is a kind of action.
* Add the `init build` action. When invoked it:
    * Determines the most recent Gradle release.
    * Uses it to run the `init build` action.

# Story: Migrating from Ant to Gradle

* Infer the project model from the contents of the source tree.
* Generate a `build.gradle` that applies the appropriate plugin for the project type. It does _not_ import the `build.xml`.
* Generate a `settings.gradle`.
* Generate the wrapper files.
* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

## User interaction

From the command-line:

1. User downloads and installs a Gradle distribution.
2. User runs `gradle initGradleBuild` from the root directory of the Ant build.
3. User runs the appropriate build comparison task from the root directory.
4. User modifies Gradle build, directed by the build comparison report.

# Story: Migrating from Ant+Ivy to Gradle

* Infer the project model from the contents of the source tree.
* Generate a `build.gradle` that applies the appropriate plugin for the project type.
* Convert the `ivysettings.xml` and `ivy.xml` to build.gradle DSL.
* Generate a `settings.gradle`.
* Generate the wrapper files.
* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

# Story: Migrating from Make to Gradle

As for the Ant to Gradle case.

# Story: Migrating from Eclipse to Gradle

* Infer the project layout, type and dependencies from the Eclipse project files.
* Generate a multi-project build, with a Gradle project per Eclipse project.
* Generate a `settings.gradle`.
* Generate the wrapper files.
* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

# Story: Migrating from IDEA to Gradle

As for the Eclipse to Gradle case.
