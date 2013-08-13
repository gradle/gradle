An Ivy module has a *status*, declared in the module's descriptor, which indicates the module's maturity. The status can be used
in a version selector of the form `latest.someStatus`, which resolves to the highest version with the given or a more mature status.
By default, the list of Ivy statuses is `release`, `milestone`, `integration`, with the latter being the least mature. If a module descriptor
has no status, it defaults to `integration`. It's possible to specify a user-defined list of statuses (and a user-defined default status)
in the Ivy settings file.

To support user-defined module statuses in Gradle, we introduce the concept of *Gradle component metadata*, Gradle's own model
for component metadata. Initially, this type of metadata will be declared in the build script. In the future, it might also come from other places
(repository, custom descriptor, external service, etc.). Initially, the model will contain two properties:

* `status`: The status of a component.
* `statusScheme`: A list of valid statuses for a component, ordered from least to most mature.

`statusScheme` defaults to [`integration`, `milestone`, `release`]. `status` defaults to the status declared in the (Ivy) descriptor, or
`integration` if no status has been declared.

# Use cases

Some users have existing Ivy repositories containing modules with custom statuses. To facilitate coexistence and stepwise
(i.e. build-by-build) migration from Ant to Gradle, the latter needs to be able to publish and consume modules with custom statuses.

Publishing modules with custom statuses is already possible, as Gradle allows to fully customize the generated Ivy descriptor.
However, Gradle cannot currently consume modules with custom statuses.

Rich module metadata is an enabler for other interesting features. (What are some good examples?)

# Implementation plan

## Consume an Ivy module with custom status (DONE)

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

### Test coverage

* Consume a module with custom Ivy status.
* Consume a module that depends on a module with custom Ivy status.

## Consume a "latest" version of an Ivy module with custom status

A version selector of the form `latest.someStatus` will select the highest version with either the given status or a
more mature status. For example, given a status scheme `gold`, `silver`, `bronze`, `latest.silver` will select the
highest version whose status is either `silver` or `gold`.

### User visible changes

A Gradle build script may declare component metadata rules that compute the metadata for a component. This could look as follows:

    componentMetadata {
        eachComponent { details ->
            details.statusScheme = ['bronze', 'silver', 'gold']
        }
    }

Different components may use different status schemes:

    componentMetadata { details ->
        if (details.id.group == 'olympic') {
            details.statusScheme = ['bronze', 'silver', 'gold']
        } else {
            details.statusScheme = ['flop', 'top']
        }
    }

It should also be possible to set the component's status, overriding its default status.

Note: For this story, the metadata rules do not apply to components resolved from an Ivy `DependencyResolver` instance.

### Test coverage

* Declare no status scheme. Publish module versions with different statuses (from the default scheme), and consume them using different "latest" selectors.
* Declare a single "global" status scheme. Publish module versions with different statuses, and consume them using different "latest" selectors.
* Same as the previous case but with multiple different status schemes.
* Use a "latest" version selector with a status that is not contained in the module's status scheme. A meaningful error should occur.
* Publish a module version with a status that is not contained in the module's status scheme. A meaningful error should occur.
* If a module version's descriptor does not declare a status, its status defaults to the least mature status for the module's status scheme. (Matches Ivy's behavior.)
    * Verify that the status exposed in meta-data tracks changes in the status scheme.
* If a module version has no descriptor, its status defaults to the least mature status for the module's status scheme.
    * Verify that the status exposed in meta-data tracks changes in the status scheme.
* Override the status of a module in the build script, and verify that it affects "latest" resolution in the expected way.
* Verify that the original metadata is cached, not the mutated metadata:
    * Publish 2 versions of a module to an HTTP repository
    * Use a meta-data rule to set the statuses of each version so that version 1 is selected.
    * Change the meta-data rule so that version 2 is selected instead.

## Declare a module as "changing"

The `componentMetadata` rule should allow to declare a component as "changing". This effectively makes Ivy changing patterns a first-class Gradle concept. Example:

    componentMetadata { details ->
        if (details.id.version.endsWith("-dev")) {
            details.changing = true
        }
    }

### User visible changes

Add a "changing" property to `ComponentMetadataDetails`.

### Test coverage

* Add a "changing" rule and verify that the module is treated as changing (cf. the existing concept of changing dependency).
* Verify that `details.changing` is initialized correctly for a module that results from resolving a changing dependency.

## Consume a "latest" version of an Ivy module with custom status that exists in multiple Ivy repositories

If a module exists in multiple Ivy repositories, the "latest" version should be computed across all repositories.

### User visible changes

Resolution selects highest version across all repositories, rather than highest version in first repository that contains the module.

### Implementation

TBD. May turn out that it's better/easier to implement cross-repository "latest" resolution from the start.

### Test coverage

* Declare multiple Ivy repositories. Publish different versions of the same module to different repositories. Make sure that highest version of highest compatible status is selected.

## Consume a "latest" version of a Maven module

## Consume a "latest" version of a flatDir module

## Consume a "latest" version of a module that exists in different types of repositories (Ivy, Maven, flatDir)

## The "+" sub version selector considers versions semantically

Currently, this selector does not consider the versions semantically. For example, `1+` matches `1.0` and `11.0` but not `2.0`.

Also improve the version ordering algorithm to handle cases like `1` vs `1.0`.

# Open issues

* How to deal with the situation where a configuration depends on a configuration from another project? Will all metadata come from the
  project whose configuration gets resolved, rather than from the project that declares the dependency? (We already have the same problem
  for repositories.)
* Figure out what the `integration` flag of an Ivy status means, and if/how we need to support it. See: http://ant.apache.org/ivy/history/2.0.0/settings/statuses.html
* How does "latest" resolution interact with conflict resolution? (If the latest integration version is higher than the latest release version, our conflict resolution might declare it as the winner.)
  (For now we decided not to do anything special about this, but we might have to eventually.)
