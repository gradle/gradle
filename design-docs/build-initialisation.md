
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

A new plugin called `build-setup` will be added. Generally, this plugin will be used in a source tree
that may or may not be empty and that contains no Gradle build. The plugin will infer the project
model from the contents of the source tree, as described below, and generate the necessary Gradle build
files and supporting files.

## User interaction

From the command-line:

1. User downloads and installs a Gradle distribution.
2. User runs `gradle setupBuild` from the root directory of the source tree.
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

# Story: Generate Gradle build from Maven POM (DONE)

When the `pom.xml` packaging is `pom`:
* Generate a multi-project build, with a separate Gradle project for each Maven module referenced in the `pom.xml`, and a root project for the parent module.
* Generate a `build.gradle` for each Maven module based on the contents of the corresponding `pom.xml`.

For all other packagings:
* Generate a single-project build.

For all builds:
* Generate a `settings.gradle`

## Sad day cases

* Maven project does not build
* bad `pom.xml`
* missing `pom.xml`

## Integration test coverage

* convert a multi-module Maven project and run Gradle build with generated Gradle scripts
* convert a single-module Maven project and run with Gradle.
* include a sad day case(s)

## Implementation approach

* Add some basic unit and integration test coverage.
* Use the maven libraries to determine the effective pom in process, rather than forking 'mvn'.
* Reuse the import and maven->gradle mapping that the importer uses.
  We cannot have the converter using one mapping and the importer using a different mapping.
  Plus this means the converter can make use of any type of import (see below).

# Story: Build initialisation generates the Gradle wrapper files

This story adds support for generating the Gradle wrapper files, and changes the existing plugin so that it is more
general purpose:

* Rename the `maven2Gradle` plugin to `build-setup`.
* Rename the `Maven2GradlePlugin` type to `BuildSetupPlugin`.
* Move the plugin and task implementation out of the `maven` project to a new `buildSetup` project.
* Move the plugin and task implementation to the `org.gradle.buildsetup.plugins` package.
* Add the new packages to `default-imports.txt`.
* Move the integration tests to live in the `buildSetup` project.
* Change the plugin to add a lifecycle task called `setupBuild` that depends on the `maven2Gradle` task.
* Change the plugin to only add the `maven2Gradle` task if the `pom.xml` file exists in the project root directory.
* Change the plugin to always generate a `settings.gradle` file.
* Change the plugin to generate an empty `build.gradle` when no `pom.xml` is present. The empty script should include
  some comments about how to get started - eg perhaps a commented-out template Java project or perhaps a link to the
  'java' tutorial user guide chapter.
* Change the plugin so that it adds a task of type `Wrapper` to generate the wrapper files.
* Update and rename the existing `boostrapPlugin` chapter in the user guide to reflect the changes.

## Test coverage

* Well-behaved plugin int test for the new plugin.
* Change the existing integration tests to run the `setupBuild` task.
* Change the existing integration tests to verify that the wrapper files are generated.
* Change the existing integration tests to verify that a `settings.gradle` is generated.
* Verify that when `setupBuild` is run in a directory that does not contain a `pom.xml` then an empty `build.gradle` file,
  plus a `settings.gradle` and the wrapper files are generated.

# Story: User initializes a Gradle build without writing a stub build script

This story adds support for automatically applying the `build-setup` plugin when `gradle setupBuild` is run:

1. Add `ProjectConfigureAction` to the plugin project which adds a task rule that applies the `build-setup` plugin
  when `setupBuild` is requested. It should only apply the plugin to the root project a build.
2. Change the `build-setup` plugin so that it adds only the `setupBuild` lifecycle task and no other tasks when
   any of the following is true. In this case, the `setupBuild` task should simply log a warning when run.
    - The settings script already exists.
    - The current project's build script already exists.

## Test coverage

* Change the existing integration tests so that they do not create the stub build script that applies the plugin.
* Running `gradle tasks` in a root directory shows the `setupBuild` task.
* Running `gradle setupBuild` in a multi-project build logs a warning and does not overwrite existing files.
* Running `gradle setupBuild` for a project whose `build.gradle` already exists logs a warning and does not overwrite
  existing files.
* Running `gradle setupBuild -b foo.gradle` when `foo.gradle` already exists logs a warning and does not generate
  any files.

# Story: User updates Gradle wrapper without defining wrapper task

This story adds a `wrapper` plugin and support for automatically applying the `wrapper` plugin when `gradle wrapper` is run:

* Extract a `wrapper` plugin out of the `build-setup` plugin.
    * Should live in the `build-setup` project.
    * Should add a `wrapper` task.
* Move the `Wrapper` task type to the `build-setup` project, and remove the dependency on the `plugins` project.
* The `wrapper` plugin should add a `ProjectConfigureAction` that adds a task rule to apply the `wrapper` plugin
  to the root project when the `wrapper` task is requested.
* Add an internal mechanism on `TaskContainer` that allows a plugin to add a placeholder for a task, which is some
  action that is called when the task is requested (eg by name)
* Change the `BuildSetupPlugin` to use this new mechanism.

## Test coverage

* Well-behaved plugin int test for the new plugin.
* Running `gradle wrapper` on a project updates the wrapper JAR and properties file.
* Running `gradle tasks` shows the `wrapper` task for the root project.
* Running `gradle tasks` does not show the `wrapper` task for a non-root project.

# Story: Gradle help message informs user how to setup a build

This story adds some helpful output when the user attempts to run Gradle in a directory that does not contain a
Gradle build, to let the user know how to create a new build or convert an existing Maven build:

* Introduce a service through which a plugin can contribute help messages.
* Change the `help` task to use this to assemble the help output.
* Introduce a service through which a plugin can contribute error resolutions.
* Change the `ExceptionAnalyser` implementations to use this.
* Change the `build-setup` plugin to add help messages and error resolutions for empty builds and
  builds that contain a `pom.xml`.

## Test coverage

* The output of `gradle` or `gradle help` in an empty directory includes a message informing the user that there is no Gradle
  build defined in the current directory, and that they can run `gradle setupBuild` to create one.
* The output `gradle` or `gradle help` in a directory with no Gradle files and a `pom.xml` includes a message informing the
  user that they can convert their POM by running `gradle setupBuild`.
* The `* Try ...` error message from `gradle someTask` in an empty directory includes a similar message to the help output.

# Story: Create a Java library project from scratch

This story adds the ability to create a Java library project by running `gradle setupBuild` in an empty directory:

* Add a `--type` command-line option to `setupBuild`.
* When `type` is `java-library` then:
    * Ignore any existing POM.
    * Generate a `build.gradle` that applies the Java plugin, adds `mavenCentral()` and the dependencies to allow testing with JUnit.
    * Create the appropriate source directories, if they do not exist.
    * Possibly add a sample class and a unit test, if there are no existing source or test files.
* When `type` is not specified then:
    * Convert a POM, if present.

## User interaction

From the command-line:

1. User downloads and installs a Gradle distribution.
2. User runs `gradle setupBuild --type java-library` from an empty directory.
3. User modifies generated build scripts and source, as appropriate.

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

# Story: Handle existing Gradle build files

Better handle the case where there is already some Gradle build scripts.

* When `setupBuild` is run and any of the files that would be generated already exist, warn the user and do not
  overwrite the file.

# Story: Improve POM conversion

TBD - fix issues with POM conversion to make it more accurate

- Fix indenting of generated `build.gradle`

## Test coverage

* Decent error message for badly formed pom.xml

# Story: User manually completes migration with help from the build comparison plugin

* Preconfigure the build comparison plugin, as appropriate.
* Inform the user about the build comparison plugin.

# Story: Expose build initialisation through the tooling API

* Extend the tooling API to add the concept of actions. Running a build is a kind of action.
* Add the `setup build` action. When invoked it:
    * Determines the most recent Gradle release.
    * Uses it to run the `setup build` action.

# Story: Create a library project from scratch

* The user specifies the type of library project to create
* As for Java library project creation
* Add support for prompting from the command-line and tooling API

## User interaction

1. User downloads and installs a Gradle distribution.
2. User runs `gradle setupBuild` from an empty directory.
3. The user is prompted for the type of project they would like to create. Alternatively,
   the user can specify the project type as a command-line option.
4. User modifies generated build scripts and source, as appropriate.

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

# Story: Add further project types

TBD

# Story: Create a project with custom convention from scratch

TBD

# Story: Create an organisation specific project from scratch

TBD

# Open issues

- Extensibility: need to be able to add more types of projects
