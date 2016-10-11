## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

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
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### BuildInvocations model is always returned for the connected project

In previous Gradle versions, when connected to a sub-project and asking for the `BuildInvocations` model using a `ProjectConnection`,
the `BuildInvocations` model for the root project was returned instead. Gradle will now
return the `BuildInvocations` model of the project that the `ProjectConnection` is connected to.


### Java `Test` task doesn't track working directory as input

Previously changing the working directory for a `Test` task made the task out-of-date. Changes to the contents had no such effect: Gradle was only tracking the path of the working directory. Tracking the contents would have been problematic, too, since the default working directory is the project directory. All-in-all tracking the working directory path wasn't adding much functionality as most tests don't rely on the working directory at all, and those that do depend on its contents as well.

From Gradle 3.3 the working directory is not tracked at all. Due to this changing the path of the working directory between builds won't make the task out-of-date.

If it's needed, the working directory can be added as an explicit input to the task, with contents tracking:

```groovy
test {
    workingDir "$buildDir/test-work"
    inputs.dir workingDir
}
```

To restore the previous behavior of tracking only the path of the working directory:

```groovy
test {
    inputs.property "workingDir", workingDir
}
```

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
