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
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->
=======
### Creating instances of JavaPluginConvention

Instances of this class are intended to be created only by the `java-base` plugin and should not be created directly. Creating instances using the constructor of `JavaPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `java-base` plugin.

### Creating instances of ApplicationPluginConvention

Instances of this class are intended to be created only by the `application` plugin and should not be created directly. Creating instances using the constructor of `ApplicationPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `application` plugin.

### Creating instances of WarPluginConvention

Instances of this class are intended to be created only by the `war` plugin and should not be created directly. Creating instances using the constructor of `WarPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `war` plugin.

### Creating instances of EarPluginConvention

Instances of this class are intended to be created only by the `ear` plugin and should not be created directly. Creating instances using the constructor of `EarPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `ear` plugin.

### Creating instances of BasePluginConvention

Instances of this class are intended to be created only by the `base` plugin and should not be created directly. Creating instances using the constructor of `BasePluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `base` plugin.

### Creating instances of ProjectReportsPluginConvention

Instances of this class are intended to be created only by the `project-reports` plugin and should not be created directly. Creating instances using the constructor of `ProjectReportsPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `project-reports` plugin.

### Adding tasks via TaskContainer.add() and TaskContainer.addAll()

These methods have been deprecated and the `create()` or `register()` methods should be used instead.

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
