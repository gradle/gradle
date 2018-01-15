## New and noteworthy

Here are the new features introduced in this Gradle release.

### Advanced POM support (preview)

Gradle now supports [additional features for modules with POM metadata](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/preview-features.adoc). This includes support for _optional dependencies_, _BOM import_ and _compile/runtime scope separation_. 

_Note:_ This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by setting `org.gradle.advancedpomsupport=true` in _gradle.properties_.

### New Gradle `.module` metadata format (preview)

In order to provide rich support for variant-aware dependency management and dependency constraints, Gradle 5.0 will define a [new module metadata format](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/preview-features.adoc), that can be used in conjunction with Ivy descriptor and Maven POM files in existing repositories.

The new metadata format is still under active development, but it can already be used in its current state in Gradle 4.6+ by setting `org.gralde.gradlemetadata=true` in _gradle.properties_.

<!--
### Example new and noteworthy
-->

### Default JaCoCo version upgraded to 0.8.0

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to use [JaCoCo version 0.8.0](http://www.jacoco.org/jacoco/trunk/doc/changes.html) by default.

### Public API for defining command line options for tasks

A custom task implementation exposes properties to make the runtime behavior configurable. Sometimes a user wants to declare the value of an exposed task property on the command line instead of the build script. That's particular helpful if the property value changes more frequently. With this version of Gradle, the task API now supports a mechanism for marking a property to automatically generate a corresponding command line parameter with a specific name at runtime. All you need to do is to annotate a setter method of a property with [Option](dsl/org.gradle.api.tasks.options.Option.html).

The following examples exposes a command line parameter `--url` for the custom task type `UrlVerify`. Let's assume you wanted to pass a URL to a task of this type named `verifyUrl`. The invocation looks as such: `gradle verifyUrl --url=https://gradle.org/`. You can find more information about this feature in the [user guide](userguide/custom_tasks.html#sec:declaring_and_using_command_line_options).

```
import org.gradle.api.tasks.options.Option;

public class UrlVerify extends DefaultTask {
    private String url;

    @Option(option = "url", description = "Configures the URL to be verified.")
    public void setUrl(String url) {
        this.url = url;
    }

    @Input
    public String getUrl() {
        return url;
    }

    @TaskAction
    public void verify() {
        getLogger().quiet("Verifying URL '{}'", url);

        // verify URL by making a HTTP call
    }
}
```

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### TestKit becomes public feature

TestKit was first introduced in Gradle 2.6 to support developers with writing and executing functional tests for plugins. In the course of the Gradle 2.x releases, a lot of new functionality was added. This version of Gradle removes the incubating status and makes TestKit a public feature.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Local build cache directory cleanup is now time-based

Previously, Gradle would clean up the [local build cache directory](userguide/build_cache.html#sec:build_cache_configure_local) only if the size of its contents reached 5 GB, or whatever was configured in `targetSizeInMB`.
From now on Gradle will instead clean up everything older than 7 days, regardless of the size of the cache directory.
As a consequence `targetSizeInMB` is now deprecated, and changing its value has no effect.

The minimum age for entries to be cleaned up can now be configured in `settings.gradle` via the [`removeUnusedEntriesAfterDays`](dsl/org.gradle.caching.local.DirectoryBuildCache.html#org.gradle.caching.local.DirectoryBuildCache:removeUnusedEntriesAfterDays) property:

```
buildCache {
    local {
        removeUnusedEntriesAfterDays = 30
    }
}
```

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

 - [Sergei Dryganets](https://github.com/dryganets) - Improved gpg instructions in signing plugin documentation (gradle/gradle#4023)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
