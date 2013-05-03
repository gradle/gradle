An Ivy module has a *status*, declared in the module's descriptor, which indicates the module's maturity. A *status scheme* is a
list of statuses ordered by maturity. The default Ivy status scheme is `integration`, `milestone`, `release`, with
`integration` being the default (and least mature).

Since Ivy 1.4, the status scheme can be customized at will. For example, it could be changed to `bronze`, `silver`,
`gold`, `platinum`. This spec is about adding support for custom status schemes.

# Use cases

Some users have existing Ivy repositories containing modules with custom statuses. To facilitate coexistence and stepwise
(i.e. build-by-build) migration from Ant to Gradle, the latter needs to be able to publish and consume modules with custom statuses.

Publishing modules with custom statuses is already possible, as Gradle allows to fully customize the generated Ivy descriptor.
However, Gradle cannot currently consume modules with custom statuses.

# Implementation plan

## Consume an Ivy module with custom status

Gradle should be able to consume an Ivy module with custom status. Example:

    repositories {
        ivy {
            url ...
        }
    }

    dependencies {
        compile "someGroup:someModule:1.0.0"
    }

The module's status is mentioned in its Ivy descriptor, but not in the build script.

### User visible changes

No API changes are necessary. Ivy modules with custom status should be resolved in the same way as modules with a default status.

### Implementation

TBD.

### Test coverage

* Consume a module with custom Ivy status.
* Consume a module that depends on a module with custom Ivy status.

## Consume a "latest" version of an Ivy module with custom status

A version selector may be of the form `latest.someStatus` (see http://ant.apache.org/ivy/history/latest-milestone/ivyfile/dependency.html).
This will select the highest version with either the given status or a more mature status. For example, given a status scheme
`bronze`, `silver`, `gold`, `latest.silver` will select the highest version whose status is either `silver` or `gold`.

### User visible changes

In Ivy, the status scheme is defined globally in the settings file. We do not want to follow this approach, but rather
model a status scheme as a piece of module metadata. Specifically, it should be possible to define rules that "compute"
the status scheme for a module. This could look as follows:

    moduleMetadata { module ->
        module.statusScheme = ['gold', 'silver', 'bronze']
    }

Different modules may use different status schemes:

    moduleMetadata { module ->
        if (module.group == "olympic") {
            module.statusScheme = ['gold', 'silver', 'bronze']
        } else {
            module.statusScheme = ['release', 'milestone', 'integration']
        }
    }

### Implementation

TBD.

### Test coverage

* Declare no status scheme. Publish module versions with different statuses (from the default scheme), and consume them using different "latest" selectors.

* Declare a single "global" status scheme. Publish module versions with different statuses, and consume them using different "latest" selectors.

* Same as the previous case but with multiple different status schemes.

* Use a "latest" version selector with a status that is not contained in the module's status scheme. A meaningful error should occur.

* If a module version's descriptor does not declare a status, its status defaults to the least mature status for the module's status scheme. (Matches Ivy's behavior.)

* If a module version has no descriptor, its status defaults to the least mature status for the module's status scheme.

## Consume a "latest" version of an Ivy module with custom status that exists in multiple Ivy repositories

If a module exists in multiple Ivy repositories, the "latest" version should be computed across all repositories.

### User visible changes

Resolution selects highest version across all repositories, rather than highest version in first repository that contains the module.

### Implementation

TBD. May turn out that it's better/easier to implement cross-repository "latest" resolution from the start.

### Test coverage

* Declare multiple Ivy repositories. Publish different versions of the same module to different repositories. Make sure that highest version of highest compatible status is selected.

# Open issues

* Should metadata rules live under `configuration.resolutionStrategy`?

* What if the list of repositories contains a Maven repository and `latest.someStatus` is used?

* Figure out what the `integration` flag of an Ivy status means, and if/how we need to support it. See: http://ant.apache.org/ivy/history/2.0.0/settings/statuses.html

* How does "latest" resolution interact with conflict resolution?
