# Gradle 2.0

Gradle 2.0 is the next major Gradle release that offers the opportunity to make breaking changes to the public interface of Gradle. This document captures a laundry
list of ideas to consider before shipping Gradle 2.0.

Note: for these changes, the old behaviour or feature to be removed should be deprecated in a Gradle 1.x release, probably no later than Gradle 1.8. Similarly
for changes to behaviour.

## Remove Ivy and Maven types from the Gradle API

* Change `ArtifactRepositoryContainer` and `RepositoryHandler` to remove methods that accept an Ivy `DependencyResolver` as parameter.
* Remove `RepositoryHandler.mavenRepo()`.
* Change `ArtifactRepositoryContainer` to change methods that return `DependencyResolver` to return `ArtifactRepository` or remove the method.
* Change `MavenResolver` so that it no longer extends `DependencyResolver`
* Remove `MavenResolver.settings`
* Change `MavenDeployer.repository` and `snapshotRepository` and remove `addProtocolProviderJars()`.
* Change `PublishFilter` so that it accepts a `PublishArtifact` instead of an `Artifact`.

## Copy tasks

* Change copy tasks so that they no longer implement `CopySpec`. Instead, they should have a `content` property which is a `CopySpec` that contains the main content.
  Leave behind some methods which operate on the file tree as a whole, eg `eachFile()`, `duplicatesStrategy`, `matching()`.
* Change the copy tasks so that `into` always refers to the root of the destination file tree, and that `destinationDir` (possibly with a better name) is instead used
  to specify the root of the destination file tree, for those tasks that produce a file tree on the file system.
* Change the `Jar` type so that there is a single `metaInf` copy spec which is a child of the main content, rather than creating a new copy spec each time `metainf`
  is referenced. Do the same for `War.webInf`.
* The `CopySpec.with()` method currently assumes that a root copy spec is supplied with all values specified, and no values are inherted by the attached copy spec.
  Instead, change `CopySpec.with()` so that values are inherited from the copy spec.
* Change the default duplicatesStrategy to `fail` or perhaps `warn`.
* Change the `Ear` type so that the generated descriptor takes precedence over a descriptor in the main content, similar to the manifest for `Jar` and the
  web XML for `War`.

## Archive tasks + base plugin

* Move defaults for output directory to the tasks and remove from base plugin.
* Use `${task.name}.${task.extension}` as the default archive name, so that the default does not conflict with another
  archive task.

## Test output

* Change the default result and report directory for the `Test` type to include the task's name, so that the default
  does not conflict with another `Test` task.

## Gradle GUI and Open-API

* Now that we have reasonable tooling support via IDEs it might be worth scrapping the Gradle GUI and open-api that it uses.

## Remove old dependency result graph

* Remove methods that use `ResolvedDependency` and `UnresolvedDependency`
* Possibly keep methods that use `ResolveArtifact` if no replacement has been added

## Remove API methods that are added by the DSL decoration

* Remove all methods that accept a `Closure` when an `Action` overload is available. Add missing overloads where appropriate.
* Remove all set methods that contain no custom logic.

## Remove references to internal classes from API

## Remove support for convention objects

Extension objects have been available for over 2 years and are now an established pattern.

Support for convention objects cannot be removed until all the core plugins have migrated to using extensions.

## Decorate classes at load time instead of subclassing

Decorating classes at load time is generally a more reliable approach and offers a few new interesting use cases we can support. For example, by decorating classes
at load time we can support expressions such as `new MyDslType()`, rather than requiring that Gradle control the instantiation of decorated objects.

Switching to decoration at load time should generally be transparent to most things, except for clients of `ProjectBuilder` that refer to types
which are not loaded by Gradle, such as the classes under test.

## Restructure plugin package heirarchy

## buildNeeded and buildDependents

* Rename buildDependents to buildDownstream
* Rename buildNeeded to buildUpstream
* Add a new task buildStream which is equivalent to buildDownstream buildUpstream

## build.gradle in a multiproject build

* A Gradle best pattern is to name the gradle file to be the same name as the subproject. 
* In Gradle 2.0, let's support this out of the box, possibly as a preference to `build.gradle`, and maybe drop support for `build.gradle` in subprojects.

## Why remind people about Maven?

Change from:

    repositories {
        mavenCentral()
    }

to:

    repositories {
        central()
    }
