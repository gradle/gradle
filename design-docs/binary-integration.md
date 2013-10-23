# Binary Integration

This spec describes a solution for a better support of updating to a different version of a binary dependency. Their are various use cases where this support is needed.

## Use Cases

### Continuous Binary Integration

Their are binary dependencies where you and/or the producer of the dependency want to have continuously, automated, early feedback 
whether the newest version of this dependency works with your artefact. This usually reflects a strong collaboration relationship. For this purpose the
artifact should be always and automatically using the newest version of the dependency when build. This will create a strong and desired integration
pressure between those two artefacts.

Currently the typical way to achieve this is by using SNAPSHOT dependencies. They have a couple of major disadvantages:

- If you publish your artefact, the dependency metadata of your artifact is not capturing the dependency version which has been used to successfully build
  the artefact. This is important information though:
	- If you want to stabilise the behaviour of your artefact, for example after promoting to QA, you have to manually replace SNAPSHOT versions with fixed
	  versions. You have to manually decide which fixed version should be use.
	- If your have a dependency on an artefact A that again has a snapshot dependency on an artefact B you might have a version of B in your graph that has
	  never been tested against A. We think this often creates too much instability.
- The build script is not explicit of which dependency version is being used.
- If the build breaks it might be hard to figure out whether the new versions of the dependencies broke your build or whether the reasons is somewhere else.
  There is no way to do a rollback to the latest version that worked.
- If you want to do experiments on the behaviour (e.g. performance) between the previous and the new version of a dependency it is hard to switch between them.
- The explicit version that is used is not stored in version control. This affects reproducibility and debugging (e.g. Git bisect).
- SNAPSHOT's only solve the problem to continuously and automatically integrate new versions of binary dependencies. This problem belongs to the broader domain
  of supporting updating to new versions of dependencies for various reasons.

The Gradle solution should provide the following:

- The build script will only contain fixed version of a dependency. The dependency metadata that is published will only contain fixed versions of a dependency. 
- Functionality will be provided as part of the build to automatically update to a newer version of a dependency. You can have for example different build types.
  A task that builds without updating the dependencies and another task that does.
- It should be easy to roll back to the previously used version of the dependency. 

The Gradle solution assumes that within the whole transitive dependency graph no dynamic versions are used to provide the qualities like reproducibility and stability. 

### Easy Manual Integration

There is collaboration relationship (e.g. team in the same organization) reflected in the binary dependency between two artefact. But their are practical or
cultural reasons why you might not want to automatically update to a newer version of a dependency, for example the quality of the dependency is not very good
(e.g. because there is not test coverage) and therefore a dependency update very often breaks your flow of work. This is not a very good situation to be in but
is often a reality. In those cases you might not want to automatically update to newer version of a dependency. The big trade off in this situation is all
the risks, waste and pain late integration brings to the table. In such a scenario the integration often not just happens late because people don't want to
explicitly integrate at a certain point. It does not happen because it is to cumbersome to figure out whether there is a new version of a new dependency, what
are its changes and then actually do the update. We want to make it very convenient to inform about newer versions and to update to a newer version:

- Show a message if a newer version exist when executing the build.
- Show in the dependency report that a newer version exists.
- Provide a task that displays the commit log of the newer version.
- Provide a task that updates to the newer version of the dependency

Another very similar use case are normal external dependencies. Even if there is no collaboration relationship, e.g. your have a dependency on JUnit, you
might appreciate an easy way to figure out whether their are new versions of JUnit available and also to easily update.

## Implementation Plan

An artefact is having direct dependencies (that might have dependencies on other artifacts). For a subset of the direct it will be possible to define:

- If a newer version for the dependency exist provide the following options:
-- Update the version number to the newer version.
-- Issue a warning
-- Provide a task that when executed, updates the dependency to a newer version.
-- Provide a hook that gets called if a newer version exists, and get the newest version as an argument.
-- Show that fact in the dependency report.
-- Provide a task that shows the change log of the newer version.

- Provide an easy way to switch to a previously used version of a dependency.

It needs to be figured out where we get previous versions from a dependency from. There are a couple of sources:
- Version Control
- Binary Repository Manager
- The internal Gradle Cache can save dependency versions that have been updated.




