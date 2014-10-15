## New and noteworthy

Here are the new features introduced in this Gradle release.

### Component metadata rule enhancements

The interface for defining component metadata rules has been enhanced so that it now supports defining rules on a per module basis
as well as for all modules.  Furthermore, rules can now also be specified as `rule source` objects.

    dependencies {
        components {
            // This rule applies to all modules
            all { ComponentMetadataDetails details ->
                if (details.group == "my.org" && details.status == "integration") {
                    details.changing = true
                }
            }

            // This rule applies to only the "my.org:api" module
            withModule("my.org:api") { ComponentMetadetails details ->
                details.statusScheme = [ "testing", "candidate", "release" ]
            }

            // This rule uses a rule source object to define another rule for "my.org:api"
            withModule("my.org:api", new CustomStatusRule()) // See class definition below
        }
    }

    class CustomStatusRule {
        @org.gradle.model.Mutate
        void setComponentStatus(ComponentMetadataDetails details) {
            if (details.status == "integration") {
                details.status = "testing"
            }
        }
    }

Note that a typed `ComponentMetadataDetails` parameter is required for every rule.

See the [userguide section](userguide/dependency_management.html#component_metadata_rules) on component metadata rules for further information.

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

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to incubating component metadata rules

- The `eachComponent` method on the incubating `ComponentMetadataHandler` interface has been replaced with `all`.
- Arguments to metadata rules must now have a typed `ComponentMetadataDetails` argument as the first argument.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Lari Hotari](https://github.com/lhotari) - improvements to output handling in Tooling API (GRADLE-2687) and coloring for the output
* [Sébastien Cogneau](https://github.com/scogneau) - share distribution plugin logic with application plugin
* [Greg Chrystall](https://github.com/ported) - idea plugin generates wrong sources jar for multi artifacts dependencies (GRADLE-3170)

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
