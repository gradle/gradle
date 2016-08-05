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

# Story: Build initialisation generates the Gradle wrapper files (DONE)

This story adds support for generating the Gradle wrapper files, and changes the existing plugin so that it is more
general purpose:

* Rename the `maven2Gradle` plugin to `build-init`.
* Rename the `Maven2GradlePlugin` type to `BuildInitPlugin`.
* Move the plugin and task implementation out of the `maven` project to a new `buildInit` project.
* Move the plugin and task implementation to the `org.gradle.buildinit.plugins` package.
* Add the new packages to `default-imports.txt`.
* Move the integration tests to live in the `buildInit` project.
* Change the plugin to add a lifecycle task called `init` that depends on the `maven2Gradle` task.
* Change the plugin to only add the `maven2Gradle` task if the `pom.xml` file exists in the project root directory.
* Change the plugin to always generate a `settings.gradle` file.
* Change the plugin to generate an empty `build.gradle` when no `pom.xml` is present. The empty script should include
  some comments about how to get started - eg perhaps a commented-out template Java project or perhaps a link to the
  'java' tutorial user guide chapter.
* Change the plugin so that it adds a task of type `Wrapper` to generate the wrapper files.
* Update and rename the existing `boostrapPlugin` chapter in the user guide to reflect the changes.

## Test coverage

* Well-behaved plugin int test for the new plugin.
* Change the existing integration tests to run the `init` task.
* Change the existing integration tests to verify that the wrapper files are generated.
* Change the existing integration tests to verify that a `settings.gradle` is generated.
* Verify that when `init` is run in a directory that does not contain a `pom.xml` then an empty `build.gradle` file,
  plus a `settings.gradle` and the wrapper files are generated.

# Story: User initializes a Gradle build without writing a stub build script (DONE)

This story adds support for automatically applying the `build-init` plugin when `gradle init` is run:

1. Add `ProjectConfigureAction` to the plugin project which adds a task rule that applies the `build-init` plugin
   when `init` is requested. It should only apply the plugin to the root project of a build.
2. Change the `build-init` plugin so that it adds only the `init` lifecycle task and no other tasks when
   any of the following is true. In this case, the `init` task should simply log a warning when run.
    - The settings script already exists.
    - The current project's build script already exists.

## Test coverage

* Change the existing integration tests so that they do not create the stub build script that applies the plugin.
* Running `gradle tasks` in a root directory shows the `init` task.
* Running `gradle init` in a multi-project build logs a warning and does not overwrite existing files.
* Running `gradle init` for a project whose `build.gradle` already exists logs a warning and does not overwrite
  existing files.
* Running `gradle init -b foo.gradle` when `foo.gradle` already exists logs a warning and does not generate
  any files.

# Story: User updates Gradle wrapper without defining wrapper task (DONE)

This story adds a `wrapper` plugin and support for automatically applying the `wrapper` plugin when `gradle wrapper` is run:

* Extract a `wrapper` plugin out of the `build-init` plugin.
    * Should live in the `build-init` project.
    * Should add a `wrapper` task.
* Move the `Wrapper` task type to the `build-init` project, and remove the dependency on the `plugins` project.
* The `wrapper` plugin should add a `ProjectConfigureAction` that adds a task rule to apply the `wrapper` plugin
  to the root project when the `wrapper` task is requested.
* Add an internal mechanism on `TaskContainer` that allows a plugin to add a placeholder for a task, which is some
  action that is called when the task is requested by name and no task with that name exists.
* Change the `build-init` and `wrapper` plugins to use this new mechanism.

## Test coverage

* Well-behaved plugin int test for the new plugin.
* Running `gradle wrapper` on a project updates the wrapper JAR and properties file.
* Running `gradle tasks` shows the `wrapper` task for the root project.
* Running `gradle tasks` does not show the `wrapper` task for a non-root project.
* Running `gradle wrapper` on a project that defines a `wrapper` task runs the task defined in the project, not the
  implicit task defined by the `wrapper` plugin.

# Story: Create a Java library project from scratch (DONE)

This story adds the ability to create a Java library project by running `gradle init` in an empty directory:

* Add a `--type` command-line option to `init`.
* When `type` is `java-library` then:
    * Ignore any existing POM.
    * Skip generation if any build or settings files already exist.
    * Generate a `build.gradle` that applies the Java plugin, adds `mavenCentral()` and the dependencies to allow testing with JUnit.
    * Create the appropriate source directories, if they do not exist.
    * Add a sample class and a unit test, if there are no existing source or test files.
* When `type` is not specified then:
    * Convert a POM, if present.
    * Otherwise, generate an empty build, as per previous stories.
* Change the `settings.gradle` template so that the root project name is set to the project directory name.

## User interaction

From the command-line:

1. User downloads and installs a Gradle distribution.
2. User runs `gradle init --type java-library` from an empty directory.
3. User edits generated build scripts and source, as appropriate.

## Test coverage

* Running `gradle init --type java-library` in an empty directory generates the build files. Running `gradle build` for this project assembles a jar
  and runs the sample test.
* The POM is ignored when `gradle init --type java-library` is used.
* Decent error message when an unknown type is given.
* Update existing test coverage to verify that every generated `settings.gradle` sets the root project name.

# Story: Build init tasks can be referenced using camel-case abbreviations (DONE)

* Improve the tasks selection mechanism so that it takes placeholders into account. The implementation must not trigger creation of the tasks,
  unless the task is actually selected.

## Test coverage

* Can run `gradle ini` or `gradle wrap`
* When a build script defines a `wrap` task, then calling `gradle wrap` does not apply the `wrapper` plugin.
* Decent error message when a POM cannot be parsed (this is adding more coverage for a previous story).
* Running `gradle init` in an empty directory generates build files that do not blow up when `gradle help` is run (this is adding more coverage for a previous story).

# Story: Add further project types (Partially done)

Add the following types:

- `groovy-library`
    - Include a spock unit test
- `scala-library`
    - Include a scalatest suite

# Story: User specifies target gradle version when generating the wrapper

This story adds a commandline property `--gradle-version` to the `wrapper` task to specify the desired Gradle version:

* Add `--gradle-version` command-line option to `wrapper`.

## Test coverage

* Running `gradle wrapper --gradle-version 1.6` generates valid `wrapper.properties` with correct URL.

# Story: User specifies target gradle distribution type when generating the wrapper

This story adds a command line option called `--distribution-type` to the `wrapper` task to specify the desired Gradle distribution type:

* Add `--distribution-type` command-line option to `wrapper`.
* Update the documentation accordingly.

Being able to set the distibution type to `all` instead of the default of `bin` is useful if the source code is required for IDEs to offer proper auto-completion for the DSL.

## Test coverage

* Running `gradle wrapper --gradle-version 2.13 --distribution-type all` generates valid `wrapper.properties` with the expected URL.
* Running `gradle wrapper --gradle-version 2.13 --distribution-type bin` generates valid `wrapper.properties` with the expected URL.
* Running `gradle wrapper --gradle-version 2.13` defaults to `bin` as the distribution type.
* Running `gradle wrapper --gradle-version 2.13 --distribution-type invalid_distribution_name` generates an error.
