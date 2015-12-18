This specification outlines the work that is required to use Gradle to build applications that use the [Play framework](http://www.playframework.com).

## Features

- [Build author configures and builds a Play application](build-play-application)
- [Build author specifies Play, Scala and Java platform for Play application](specify-target-platform)
- [Build author runs a Play application in development](run-play-application)
- [Build author runs tests for a Play application](test-play-application)
- [Build author develops Play application in IDE](play-application-in-ide)
- [Administrator deploys a Play application in production](publish-and-deploy)

## Debt

### Documentation and release notes for Play support

Before the support for Play framework is fully usable and can be properly 'released' we need to add documentation and release notes.

This specification outlines the work that is required to use Gradle to build applications that use the [Play framework](http://www.playframework.com).

## Use cases

There are 3 main use cases:

- A developer builds a Play application.
- A developer runs a Play application during development.
- A deployer runs a Play application. That is, a Play application is packaged up as a distribution which can be run in a production environment.

## Performance

Performance should be comparable to SBT:

- Building and starting an application.
- Reload after a change.
- Executing tests for an application.

## Out of scope

The following features are currently out of scope for this spec, but certainly make sense for later work:

- Building a Play application for multiple Scala versions. For now, the build for a given Play application will target a single Scala version.
  It will be possible to declare which version of Scala to build for.
- Using continuous mode with JVMs older than Java 7. For now, this will work only with Java 7 and later will be supported. It will be possible to build and run for Java 6.
- Any specific IDE integration, beyond Gradle's current general purpose IDE integration for Java and Scala.
- Any specific testing support, beyond Gradle's current support for testing Java and Scala projects.
- Any specific support for publishing and resolving Play applications, beyond Gradle's current general purpose capabilities.
- Any specific support for authoring plugins, beyond Gradle's current support.
- Installing the Play tools on the build machine.
- Migrating or importing SBT settings for a Play project.

## Documentation

- Migrating an SBT based Play project to Gradle
- Writing Gradle plugins that extend the base Play plugin

## Further features

- Internal mechanism for plugin to inject renderer(s) into components report.
- Model language transformations, and change Play support to allow a Play application to take any JVM language as input.
- Declare dependencies on other Java/Scala libraries
- Control joint compilation of sources based on source set dependencies.
- Build multiple variants of a Play application.
- Generate an application install, eg with launcher scripts and so on.
