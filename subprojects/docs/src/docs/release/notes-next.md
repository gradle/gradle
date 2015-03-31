## New and noteworthy

Here are the new features introduced in this Gradle release.

### Google Test support (i)

- TBD

### Dependency substitution accepts projects

You can now replace an external dependency with a project dependency. The `DependencyResolveDetails` object
allows access to the `ComponentSelector` as well:

    resolutionStrategy {
        eachDependency { details ->
            if (details.selector instanceof ModuleComponentSelector && details.selector.group == 'com.example' && details.selector.module == 'my-module') {
                useTarget project(":my-module")
            }
        }
    }
### Dependency substitution rules

In previous Gradle versions you could replace an external dependency with another like this:

    resolutionStrategy {
        eachDependency { details ->
            if (details.requested.group == 'com.example' && details.requested.module == 'my-module') {
                useVersion '1.3'
            }
        }
    }

This behaviour has been enhanced and extended, with the introduction of 'Dependency Substitution Rules'.
These rules allow an external dependency to be replaced with a project dependency, and vice-versa. 

You replace a project dependency with an external dependency like this:

    resolutionStrategy {
        dependencySubstitution {
            withProject(project(":api")) { 
                useTarget group: "org.utils", name: "api", version: "1.3" 
            }
        }
    }

And replace an external dependency with an project dependency like this:


    resolutionStrategy {
        dependencySubstitution {
            withModule("com.example:my-module") {
                useTarget project(":project1")  
            }
        }
    }

There are other options available to match module and project dependencies:

    all { DependencySubstitution<ComponentSelector> details -> /* ... */ }
    eachModule() { ModuleDependencySubstitution details -> /* ... */ }
    withModule("com.example:my-module") { ModuleDependencySubstitution details -> /* ... */ }
    eachProject() { ProjectDependencySubstitution details -> /* ... */ }
    withProject(project(":api)) { ProjectDependencySubstitution details -> /* ... */ }

It is also possible to replace one project dependency with another, or one external dependency with another. (The latter provides the same functionality
as `eachDependency`).
Note that the `ModuleDependencySubstitution` has a convenience `useVersion()` method. For the other substitutions you should use `useTarget()`.

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

### Changing a configuration after it has been resolved

TODO

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Lorant Pinter](https://github.com/lptr), [Daniel Vigovszky](https://github.com/vigoo) and [Mark Vujevits](https://github.com/vujevits) - implement dependency substitution for projects

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
