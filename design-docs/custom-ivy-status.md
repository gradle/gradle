An Ivy module has a *status*, declared in the module's descriptor, which indicates the module's maturity. The status can be used
in a version selector of the form `latest.someStatus`, which resolves to the highest version with the given or a more mature status.
By default, the list of Ivy statuses is `release`, `milestone`, `integration`, with the latter being the least mature. If a module descriptor
has no status, it defaults to `integration`. It's possible to specify a user-defined list of statuses (and a user-defined default status)
in the Ivy settings file.

To support user-defined module statuses in Gradle, we introduce the concept of *Gradle module metadata*, Gradle's own model
for module metadata. Initially, this type of metadata will be declared in the build script. In the future, it might also come from other places
(repository, custom descriptor, external service, etc.). Initially, the model will contain two properties:

* `status`: The status of a module.
* `statusScheme`: An list of valid statuses for a module, ordered by decreasing maturity.

`statusScheme` defaults to [`release`, `milestone`, `integration`]. The default for `status` depends on which repository hosts the module:

* For an Ivy module, `status` defaults to the module's Ivy status, which in turn defaults to the least mature status in `statusScheme`.
* For a Maven module, `status` defaults to the least mature status in `statusScheme` if the version contains `-SNAPSHOT`, and to the most mature otherwise.
  (This can be improved over time, for example by taking `maven-metadata.xml` into consideration.)
* For a flatDir module, `status` defaults to the least mature status in `statusScheme` if the version contains `-SNAPSHOT`, and to the most mature otherwise.

(Other defaults are conceivable. We could even have different default status schemes per repository type, for example `release` and `snapshot` for Maven repositories.)

# Use cases

Some users have existing Ivy repositories containing modules with custom statuses. To facilitate coexistence and stepwise
(i.e. build-by-build) migration from Ant to Gradle, the latter needs to be able to publish and consume modules with custom statuses.

Publishing modules with custom statuses is already possible, as Gradle allows to fully customize the generated Ivy descriptor.
However, Gradle cannot currently consume modules with custom statuses.

Rich module metadata is an enabler for other interesting features. (What are some good examples?)

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

A version selector of the form `latest.someStatus` will select the highest version with either the given status or a
more mature status. For example, given a status scheme `gold`, `silver`, `bronze`, `latest.silver` will select the
highest version whose status is either `silver` or `gold`.

### User visible changes

A Gradle build script may declare module metadata rules that compute the metadata for a module. This could look as follows:

    moduleMetadata { module, repository ->
        module.statusScheme = ['gold', 'silver', 'bronze']
    }

Different modules may use different status schemes:

    moduleMetadata { module, repository ->
        if (module.group == 'olympic') {
            module.statusScheme = ['gold', 'silver', 'bronze']
        } else {
            module.statusScheme = ['top', 'flop']
        }
    }

Different repositories may use different status schemes:

    moduleMetadata { module, repository ->
        if (repository.name == 'olympic') {
            module.statusScheme = ['gold', 'silver', 'bronze']
        } else {
            module.statusScheme = ['top', 'flop']
        }
    }

It should also be possible to set the module's status, overriding its default status.

### Implementation

TBD.

### Test coverage

* Declare no status scheme. Publish module versions with different statuses (from the default scheme), and consume them using different "latest" selectors.

* Declare a single "global" status scheme. Publish module versions with different statuses, and consume them using different "latest" selectors.

* Same as the previous case but with multiple different status schemes.

* Use a "latest" version selector with a status that is not contained in the module's status scheme. A meaningful error should occur.

* If a module version's descriptor does not declare a status, its status defaults to the least mature status for the module's status scheme. (Matches Ivy's behavior.)

* If a module version has no descriptor, its status defaults to the least mature status for the module's status scheme.

* Override the status of a module in the build script, and verify that it affects "latest" resolution in the expected way.

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

# Open issues

* Figure out what the `integration` flag of an Ivy status means, and if/how we need to support it. See: http://ant.apache.org/ivy/history/2.0.0/settings/statuses.html

* How does "latest" resolution interact with conflict resolution? (If the latest integration version is higher than the latest release version, our conflict resolution might declare it as the winner.)
  (For now we decided not to do anything special about this, but we might have to eventually.)
