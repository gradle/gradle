# Gradle 2.0

Gradle 2.0 is the next major Gradle release that offers the opportunity to make breaking changes to the public interface of Gradle.
This document captures a laundry list of ideas to consider before shipping Gradle 2.0

## buildNeeded and buildDependents
Rename buildDependents to buildDownstream
Rename buildNeeded to buildUpstream
Add a new task buildStream which is equivalent to buildDownstream buildUpstream

## build.gradle in a multiproject build
A Gradle best pattern is to name the gradle file to be the same name as the subproject.
In Gradle 2.0, lets support this out of the box, possibly as a preference to build.gradle, and maybe drop support for build.gradle in subprojects.

