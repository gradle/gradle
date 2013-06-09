# Gradle 2.0

Gradle 2.0 is the next major Gradle release that offers the opportunity to make breaking changes to the public interface of Gradle. This document captures a laundry list of ideas to consider before shipping Gradle 2.0

## Copy tasks

* Change copy tasks so that they no longer implement `CopySpec`. Instead, they should have a `content` property which is a `CopySpec` that contains the main content.
  Leave behind some methods which operate on the file tree as a whole, eg `eachFile()`, `duplicatesStrategy`, `matching()`.
* Change the copy tasks so that `into` always refers to the root of the destination file tree, and that `destinationDir` (possibly with a rename) is instead used to
  specify the root of the destination file tree, for those tasks that produce a file tree on the file system.
* The `CopySpec.with()` method currently assumes that a root copy spec is supplied with all values specified, and no values are inherted by the attached copy spec.
  Instead, change `CopySpec.with()` so that values are inherited from the copy spec.
* Change the default duplicatesStrategy to `fail` or perhaps `warn`.

## buildNeeded and buildDependents

* Rename buildDependents to buildDownstream
* Rename buildNeeded to buildUpstream
* Add a new task buildStream which is equivalent to buildDownstream buildUpstream

## build.gradle in a multiproject build

* A Gradle best pattern is to name the gradle file to be the same name as the subproject. 
* In Gradle 2.0, let's support this out of the box, possibly as a preference to `build.gradle`, and maybe drop support for build.gradle in subprojects.

## Why remind people about Maven?

Change from:

    repositories {
        mavenCentral()
    }

to:

    repositories {
        central()
    }
