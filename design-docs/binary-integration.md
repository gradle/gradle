# Binary Integration

This spec describes a solution for a better support of updating to a different version of a binary dependency. There are various use cases where this support is needed.

## Use Cases

### Continuous Binary Integration

There are binary dependencies where you and/or the producer of the dependency want to have continuously, automated, early feedback
whether a newer version of this dependency works with your artefact. This usually reflects a collaboration relationship. For this purpose the artifact should be always and automatically using a newer version of the dependency (if available) when build. This will create a strong and desired integration pressure between those two artefacts. The strategy on how to select a newer dependency needs to be declared, e.g. latest minor, latest patch, latest timestamp or a custome rule.

One typical way in our industry to currently achieve automated binary integration is by using SNAPSHOT dependencies. This approach has a couple of major disadvantages:

- If you publish your artefact, the dependency metadata of your artifact does not capture the dependency version which has been used to successfully build the artefact. This is important information though:
	- If you want to stabilise the behaviour of your artefact, for example after promoting to QA, you have to manually replace SNAPSHOT versions with fixed versions. You have to manually decide which fixed version should be use.
	- If you have a dependency on an artefact A that again has a snapshot dependency on an artefact B you might have a version of B in your graph that has never been tested against A. We think this often creates too much instability.
- The build definition can't tell you which exact dependency version is used for the current or any other build.
- There is no easy way to do a rollback to the previous version of a dependency:
	- A dependency broke your build and you want to get quickly get back into a workable state again. 
	- The build breaks and you can't easily figure out whether the new versions of the dependencies broke your build or whether the reasons is somewhere else.
	- You want to do experiments on the behaviour (e.g. performance) between the previous and the new version of a dependency but it is hard to switch between them.
- The information about which version of a SNAPSHOT dependency has been used is not stored in version control. This affects reproducibility and debugging (e.g. Git bisect).
- SNAPSHOTs only solve the problem to continuously and automatically integrate new versions of binary dependencies. This problem though belongs to the broader domain of supporting updating to new versions of dependencies for other reasons.

The Gradle solution should provide the following:

- The build definition will have exact knowledge of the current versions of an updatable dependency and also its history. The dependency metadata that is published will only contain fixed versions of a dependency. 
- Functionality will be provided as part of the build to automatically update to a newer version of a dependency. You can have for example different build types: A task that builds without updating the dependencies and another task that does.
- It should be easy to roll back to the previously used version of the dependency. 
- It should be easy to share the version information with other developers so that they can build exactly what you build.

The Gradle solution assumes as a default, that within the whole transitive dependency graph no dynamic versions are used to provide the qualities like reproducibility and stability. 

### Easy Manual Integration

There is collaboration relationship (e.g. team in the same organization) reflected in the binary dependency between two artefact. But there are practical or cultural reasons why you might not want to automatically update to a newer version of a dependency, for example the quality of the dependency is not very good (e.g. because there is not test coverage) and therefore a dependency update very often breaks your flow of work. This is not a very good situation to be in but is often a reality. In those cases you might not want to automatically update to newer version of a dependency. Their is a price you pay for that with all the risks, waste and pain late integration brings to the table, but it is nonetheless the best strategy. Additionally though, in such a scenario the integration often happens even late than necessary because it is to cumbersome to figure out whether there is a new version of a new dependency, what are its changes and then to actually do the update. We want to make it very convenient to inform about newer versions and to update to a newer version:

- Show a message if a newer version exist when executing the build.
- Show in the dependency report that a newer version exists.
- Provide a task that displays the commit log of the newer version.
- Provide a task that updates to the newer version of the dependency

Another very similar use case is normal external dependencies. Even if there is no collaboration relationship, e.g. your have a dependency on JUnit, you might appreciate an easy way to figure out whether there are new versions of JUnit available and also to easily update.

## Implementation Plan

### The resolution process

The implementation is also affected by the constraints of the metadata format. In the initial implementation we will focus on making it work with the currently used metadata formats POM and IVY. Future metadata formats might provide a richer way to support binary integration even better.

In general there are multiple factors that influence which dependency version is used:

1. The version provided in the build script
1. Conflict Resolution
1. A repository that is asked for a version number in case dynamic versions are used. 
1. A dependency resolve hook

The actual version that is used is always a result of a resolution process even if you use fixed versions in the build script. 

If you use only fixed versions in the definition the resolved dependency versions are always reproducible as they are the only input to the conflict resolution process. Most dependency resolve hooks will also lead to reproducible versions as they always do the same transformation. Once you use dynamic versions or dependency resolve hooks that have dynamic input (e.g. a global white list) the resolved versions will change over time. 

It should be noted though that theoretically people can change the dependency metadata for a given version of a dependency. If you then wipe your cache the next time the build result will be different even if you are just using fixed versions. Furthermore the actual artefact in the repository can be changed which would not affect the versions but the actual build result. This would violate though the recommended approach to deal with repository metadata within an organisation. 

### Persisting the graph

We want to persist the resolved versions for a particular build. There are a couple of things to consider:

- The whole transitive graph in larger multi-module builds might be a large object to persist.
- Storing the graph in version control would solve many of the objectives (Build history, sharing, ...). But we currently think it is not the right solution. After all the persisted graph is a derived entity from executing the build and might be not in sync with its inputs (e.g. a version definition in the build script).  

t.b.d.

An artefact has direct dependencies (that might have dependencies on other artifacts). For a subset of the direct dependencies it will be possible to define:

- If a newer version for the dependency exist provide the following options:
-- Update the version number to the newer version.
-- Issue a warning
-- Provide a task that when executed, updates the dependency to a newer version.
-- Provide a hook that gets called if a newer version exists, and get the newest version as an argument.
-- Show that fact in the dependency report.
-- Provide a task that shows the change log of the newer version.

- Provide an easy way to switch to a previously used version of a dependency.


## Notes

- Two primitive operations:
    - Check my changed sources against unchanged dependencies.
    - Check my unchanged sources against changed dependencies.
    - Can combine these
- Two patterns:
    - Optimistic: use changed dependencies for each build, updating the source to record which versions were used
    - Pessimistic: use unchanged dependencies for each build, updating the source only when explicitly requested
- When a failure occurs
    - Provide a way to roll back the changes in dependencies and try again
    - Could potentially provide a way to try again with a different set of dependency changes
    - Blacklist the dependencies that were used so that other builds do not experiment with the same versions
- Reporting
    - Report on what changes were used
    - Report on which new versions could be used
- Provide some high level lifecycle and patterns on top of the primitives
    - Allow some mechanism for ad hod experimentation with dependency versions
- Provide some VCS integration to allow a CI build to change the dependencies.
- Storage of the resolved graph
    - In source
    - In some repository indexed by hash of the dependency selectors
- When in update mode, set the cache timeouts to 0.
