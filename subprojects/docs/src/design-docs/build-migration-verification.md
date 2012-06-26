# What is this?

The build migration verification feature provides a way to automatically verify whether a build behaves
the same after modifying it in some way. More precisely, it checks whether the build produces
the same _outputs_ (modulo some allowed variance) as before.

The feature should provide the build
master with information about changes detected in the outputs, and should help him make decisions
on whether to accept or reject a migration. For example, this could be done by classifying detected
changes as expected vs. unexpected, or low risk vs. high risk.

Verification might include running the build before and after migration, or it might just compare
two sets of existing outputs. Apart from verifying a set of changes to a build, the feature might
also support the promotion of those changes. An example would be to set a new Gradle version in
gradle-wrapper.properties.

# Use cases

## Upgrading to a new Gradle version

In order to make Gradle updates less risky and more predictable, the feature should support checking
whether a build produces the same outputs after changing the Gradle version from X to Y. Typically
(but not necessarily), Y will be greater than X.

## Refactoring a Gradle build

In order to make refactoring a Gradle build less risky and more predictable, the feature should support
checking whether a build produces the same outputs after making some configuration changes to it.

## Migrating from Maven to Gradle

In order to make a switch from Maven to Gradle less risky and more predictable, the feature should support
checking whether a build that has been migrated to Gradle behaves the same as the original Maven build.
Which migration methods are supported needs further discussion and won't necessarily influence the
verification side of things.

## Migrating from Ant to Gradle

Similar to the previous use case, except that the source system is Ant.

# User visible changes

The feature will be implemented as a new plugin and a set of new tasks.

# Integration test coverage

The feature can be tested by verifying migrations whose outcome is known.

# Implementation approach

# Open issues

* How much knowledge does verification need about the "old" and "new" builds? Is it good enough to have two
sets of outputs (together with a mapping between them), or is additional information required?

* Does verification always include execution of the "old" and "new" builds, or can it work off preexisting outputs?

* Who decides what the outputs of a build (or the subset of outputs that should be compared) are?

* Assuming the feature is implemented as a plugin, where does the plugin and its task get executed?
In the old build? The new build? An independent "migration" build?


