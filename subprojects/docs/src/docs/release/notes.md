## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Scala support for Intellij IDEA versions 14 and later using 'idea' plugin

Beginning with IntelliJ IDEA version 14 Scala projects are no longer configured using a project facet and instead use a
[Scala SDK](http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/1/) project library. This affects how the IDEA metadata should be
generated. Using the 'idea' plugin in conjunction with Scala projects for newer IDEA version would cause errors due to the Scala facet being unrecognized. The 'idea' plugin
now by default creates a Scala SDK project library as well as adds this library to all Scala modules. More information can be found in the
[user guide](https://docs.gradle.org/current/userguide/scala_plugin.html#sec:intellij_idea_integration).

This feature was contributed by [Nicklas Bondesson](https://github.com/nicklasbondesson).

### Software model improvements

#### Fine grained application of rules

TBD - A new kind of rule method is now available, which can be used to apply additional rules to some target.

This kind of method is annotated with `@Rules`. The first parameter defines a `RuleSource` type to apply, and the second parameter defines the target element to apply the rules to.

Two new annotations have been added:

- `@RuleInput` can be attached to a property of a `RuleSource` to indicate that the property defines an input for all rules on the `RuleSource`.
- `@RuleTarget` can be attached to a property of a `RuleSource` to indicate that the property defines the target for the `RuleSource`.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to 'idea' plugin Scala projects

Scala projects using the 'idea' plugin now generate IntelliJ IDEA metadata targeting versions 14 and newer. Users of IDEA versions older than 14 will need to update
their build scripts to specify that metadata should be generated for an earlier IDEA version.

    idea {
        targetVersion = '13'
    }


## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Nicklas Bondesson](https://github.com/nicklasbondesson) - Support IntelliJ IDEA 14+ when using 'scala' and 'idea' plugins

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
