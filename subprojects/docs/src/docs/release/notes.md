## New and noteworthy

Here are the new features introduced in this Gradle release.

### Version Selection Rules (i)
Fine tuning the dependency resolution process is even more powerful now with the use of version selection rules.  These allow custom rules to be applied whenever
multiple versions of a module are being evaluated.  Using such rules, one can explicitly accept or reject a version or allow the default selection
rules to be applied.  This allows Gradle to customize version selection without knowing what versions might be available at build time.

    configurations {
        conf {
            resolutionStrategy {
                versionSelection {
                    all { VersionSelection selection ->
                        // Accept the newest version that matches the dynamic selector
                        // but does not end with "-experimental".
                        if (selection.requested.group == 'org.sample'
                                && selection.requested.name == 'api'
                                && selection.candidate.version.endsWith('-experimental')) {
                            selection.reject()
                        }
                    }
                }
            }
        }
        dependencies {
            conf "org.sample:api:1.+"
        }
    }

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

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Jake Wharton](https://github.com/JakeWharton) - clarification of hashing used for Gradle Wrapper downloads

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
