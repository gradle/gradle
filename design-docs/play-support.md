This specification outlines the work that is required to use Gradle to build applications that use the [Play framework](http://www.playframework.com).

# Use cases

There are 3 main use cases:

- A developer builds a Play application.
- A developer runs a Play application during development.
- A deployer runs a Play application. That is, a Play application is packaged up as a distribution which can be run in a production environment.

# Implementation plan - Milestone 1

## Out of scope

The following features are currently out of scope for this milestone, but certainly make sense for later work:

- Building a Play application for multiple Scala versions. For now, the build for a given Play application will target a single Scala version.
  It will be possible to declare which version of Scala to build for.
- Using continuous mode with JVMs older than Java 7. For now, this will work only with Java 7 and later will be supported. It will be possible to build and run for Java 6.
- Any specific IDE integration, beyond Gradle's current general purpose IDE integration for Java and Scala.
- Any specific testing support, beyond Gradle's current support for testing Java and Scala projects.
- Any specific support for publishing and resolving Play applications, beyond Gradle's current general purpose capabilities.
- Any specific support for authoring plugins, beyond Gradle's current support.
- Installing the Play tools on the build machine.
- Migrating or importing SBT settings for a Play project.

## Performance

Performance should be comparable to SBT:

- Building and starting an application.
- Reload after a change.
- Executing tests for an application.

## Developer compiles Java and Scala source for Play application

Developer uses the standard build lifecycle, such as `gradle assemble` or `gradle build` to compile the Java and Scala source for a Play application.

- Introduce a 'play' plugin.
- Adapt language plugins conventions to Play conventions
    - need to include unmanaged dependencies from `lib/` as compile dependencies
    - sub-projects have a slightly different layout to root project

## Developer compiles route and template source for Play application

Extend the standard build lifecycle to compile the route and template source files to bytecode.

- Compile routes and templates
- Define source sets for each type of source file
- Compilation should be incremental and remove stale outputs

## Developer compiles assets for Play application

Extend the standard build lifecycle to compile the front end assets to CSS and Javascript.

- Compiled assets
    - Coffeescript -> Javascript
    - LESSCSS -> CSS
    - Javascript > Javascript via Google Closure
    - Javascript minification, requirejs optimization
- Include the compiled assets in the Jar
- Include the `public/` assets in the Jar
- Include Play config files in the Jar (eg `conf/play.plugins`)
- Define source sets for each type of source file
- Compilation should be incremental and remove stale outputs
- Expose some compiler options

TBD - integration with Gradle javascript plugins.

## Developer builds and runs Play application

Introduce some lifecycle tasks to allow the developer to run or start the Play application. For example, the
developer may run `gradle run` to run the application or `gradle start` to start the application.

- Add basic server + service domain model and some lifecycle tasks
- Model Play application as service
- Lifecycle to run in foreground, or start in background, as per `play run` and `play start`

Note that this story does not address reloading the application when source files change. This is addressed by a later story.

## Developer builds Play application distribution

Introduce some lifecycle tasks to allow the developer to package up the Play application. For example, the
developer may run `gradle stage` to stage the local application, or `gradle dist` to create a standalone distribution.

- Build distribution image and zips, as per `play stage` and `play dist`
- Integrate with the distribution plugin.

## Long running compiler daemon

Reuse the compiler daemon across builds to keep the Scala compiler warmed up. This is also useful for the other compilers.

## Keep running Play application up-to-date when source changes

This story adds an equivalent of Play's continuous mode, where Gradle monitors the source files for changes and rebuilds and restarts the application when
some change is detected. Note that 'restart' here means a logical restart.

Add a general-purpose mechanism which is able to keep the output of some tasks up-to-date when source files change. For example,
a developer may run `gradle --watch <tasks>`.

- Gradle runs tasks, then watches files that are inputs to a task but not outputs of some other task. When a file changes, repeat.
- Monitor files that are inputs to the model for changes too.
- When the tasks start a service, stop and restart the service(s) after rebuilding, or reload if supported by the service container.
- The Play application container must support reload. According to the Play docs the plugin can simply recreate the application
  ClassLoader.
- Integrate with the build-announcements plugin, so that desktop notifications can be fired when something fails when rerunning the tasks.

So:

- `gradle --watch run` would build and run the Play application. When a change to the source files are detected, Gradle would rebuild and
  restart the application.
- `gradle --watch test run` would build and run the tests and then the Play application. When a change to the source files is detected,
  Gradle would rerun the tests, rebuild and restart the Play application.
- `gradle --watch test` would build and run the tests. When a source file changes, Gradle would rerun the tests.

Note that for this story, the implementation will assume that any source file affects the output of every task listed on the command-line.
For example, running `gradle --watch test run` would restart the application if a test source file changes.

## Developer triggers rebuild of running Play application

This story adds an equivalent of Play's non-continuous mode, where Gradle restarts the application when triggered by the developer reloading
the application in the browser and some source files have changed. Note that 'restart' here means a logical restart.

- Gradle starts the Play server configured with the appropriate hooks to be informed when a request is in progress.
- On each request, check asynchronously whether the application is up-to-date, if a check or rebuild is not already in progress.
- Checks the same set of files as for the above feature, possibly monitored in the same way.
- When an input file is out of date, rebuild the application.

The implementation for continuous and non-continuous modes are basically the same, the main difference being when a rebuild
is triggered.

## Resources are built on demand when running Play application

When running a Play application, start the application without building any resources. Build these resources only when requested
by the client.

- On each request, check whether the task which produces the requested resource has been executed or not. If not, run the task synchronously
  and block until completed.
- Include the transitive input of these tasks as inputs to the watch mechanism, so that further changes in these source files will
  trigger a restart of the application at the appropriate time.
- Failures need to be forwarded to the application for display.

## Developer views compile and other build failures in Play application

Adapt compiler output to the format expected by Play:

- Model configuration problems
- Java and scala compilation failures
- Asset compilation failures
- Other verification task failures?

## Native integration with Specs 2

Introduce a test integration which allows Specs 2 specifications to be executed directly by Gradle, without requiring the use of the Specs 2
JUnit integration.

- Add a Specs 2 plugin
- Add some Specs 2 options to test tasks
- Detect specs 2 specifications and schedule for execution
- Execute specs 2 specifications using its API and adapt execution events

Note: no changes to the HTML or XML test reports will be made.

## Developer runs Scala interactive console

Allow the Scala interactive console to be launched from the command-line.

- Build the project's main classes and make them visible via the console
- Add support for client-side execution of actions
- Model the Scala console as a client-side action
- Remove console decoration prior to starting the Scala console

## Scala code quality plugins

- Scalastyle
- SCCT

## Javascript plugins

- Compile Dust templates to javascript and include in the web application image

## Bootstrap a new Play project

Extend the build init plugin so that it can bootstrap a new Play project, producing the same output as `play new` except with a Gradle build instead of
an SBT build.

## Documentation

- Migrating an SBT based Play project to Gradle
- Writing Gradle plugins that extend the base Play plugin

# Implementation plan - Milestone 2

## Publish Play application to a binary repository

Allow a Play application distribution to be published to a binary repository.

# Implementation plan - Later milestones

Some candidates for later work:

- Improve the HTML test report to render a tree of test executions, for better reporting of Specs 2 execution (and other test frameworks)
- Support the new Java and Scala language plugins
- Improve watch mode so that only those tasks affected be a given change are executed
