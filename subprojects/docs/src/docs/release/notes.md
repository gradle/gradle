## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### IDE integration improvements


#### Idea Plugin uses targetCompatibility for each subproject to determine module and project bytecode version

The Gradle 'idea' plugin can generate configuration files allowing a Gradle build to be opened and developed in IntelliJ IDEA.
Previous versions of Gradle did not consider any `targetCompatibility` settings for java projects.
This behavior has been improved, so that the generated IDEA project will have a 'bytecode version' matching the highest targetCompatibility value for all imported subprojects.
For a multi-project Gradle build that contains a mix of targetCompatibility values, the generated IDEA module for a sub-project will include an override for the appropriate 'module bytecode version' where it does not match that of the overall generated IDEA project.


#### Scala support for Intellij IDEA versions 14 and later using 'idea' plugin

Beginning with IntelliJ IDEA version 14 Scala projects are no longer configured using a project facet and instead use a
[Scala SDK](http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/1/) project library. This affects how the IDEA metadata should be
generated. Using the 'idea' plugin in conjunction with Scala projects for newer IDEA version would cause errors due to the Scala facet being unrecognized. The 'idea' plugin
now by default creates a Scala SDK project library as well as adds this library to all Scala modules. More information can be found in the
[user guide](https://docs.gradle.org/current/userguide/scala_plugin.html#sec:intellij_idea_integration).

This feature was contributed by [Nicklas Bondesson](https://github.com/nicklasbondesson).

### Software model improvements

#### Model data report

The model report can be very verbose by default: for each node of the model, it will show you the types of the properties as well as the rules that created or mutated them. However, you might only want to see the data that the model contains, and only the data, for example to validate your build configuration. In that case, you can now do this by calling `gradle model --format=short`. By default, Gradle still outputs the most complete report, which is equivalent to calling `gradle model --format=full`.

#### Fine grained application of rules

TBD - A new kind of rule method is now available, which can be used to apply additional rules to some target.

This kind of method is annotated with `@Rules`. The first parameter defines a `RuleSource` type to apply, and the second parameter defines the target element to apply the rules to.

Two new annotations have been added:

- `@RuleInput` can be attached to a property of a `RuleSource` to indicate that the property defines an input for all rules on the `RuleSource`.
- `@RuleTarget` can be attached to a property of a `RuleSource` to indicate that the property defines the target for the `RuleSource`.

### The "scala-library" build init type uses the Zinc compiler by default

When initializing a build with the "scala-library" build init type, the generated build now uses the [Zinc Scala comiler](https://github.com/typesafehub/zinc) by default.
The Zinc compiler provides the benefit of being faster and more efficient than the Ant Scala compiler.

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


### Change in exclude file pattern matching (May Exclude Fewer!)

Projects which use wildcards in patterns to `exclude` files from certain tasks may notice that fewer files are excluded than in previous versions of Gradle.

For example, if the project had a directory structure like:

    files/ignoreMe.txt
    files/valid/Some.class
    file/alsoValid/Some.Java

Then the following `fileTree` would contain no files with previous versions of Gradle.

    fileTree('files') {
        exclude('*')
    }

This version of Gradle changes the behavior (see: [GRADLE-3182](https://issues.gradle.org/browse/GRADLE-3182)) so that `fileTree` will contain all of the files except for `files/ignoreMe.txt`. This behavior is consistent with how `include` patterns already work.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Nicklas Bondesson](https://github.com/nicklasbondesson) - Support IntelliJ IDEA 14+ when using 'scala' and 'idea' plugins
* [Lewis Cowles](https://github.com/LewisCowles1986) - Installation documentation clarification about GRADLE_HOME

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
