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

### New PluginAware methods for detecting the presence of plugins

The `PluginAware` interface (implemented by `Project`, `Gradle` and `Settings`) has the following new methods for detecting the presence of plugins, based on ID:

* findPlugin()
* hasPlugin()
* withPlugin()

These methods should be used when reacting to the presence of another plugin or for ad-hoc reporting.

TODO - more detail.

### ANTLR plugin supports ANTLR version 3.X and 4.X

Additionally to the existing 2.X support, the [ANTLR Plugin](userguide/antlrPlugin.html) now supports ANTLR version 3 and 4. 
To use ANTLR version 3 or 4 in a build, an according antlr dependency must be declared explicitly:

    apply plugin: "java"
    apply plugin: "antlr"
    
    repositories() {
        jcenter()
    }
    
    dependencies {
        antlr 'org.antlr:antlr4:4.3'
    }
  
This feature was contributed by [Rob Upcraft](https://github.com/upcrob).

### AntlrTask running in separate process

The [`AntlrTask`](dsl/org.gradle.api.plugins.AntlrTask.html) is now 
executed in a separate process. This allows more fine grained control over memory settings just for the ANTLR process.
See [Antlr Plugin](userguide/antlrPlugin.html) for further details. 

This feature was also contributed by [Rob Upcraft](https://github.com/upcrob).

### Build Comparison plugin now compares nested archives

The [Build Comparison plugin](userguide/comparing_builds.html) has been improved in this release to compare entries of nested archives.
Previously, when comparing an archive all archive entries were treated as binary blobs.
Now, entries of archive entries are inspected recursively where possible.
That is, archive entries that are themselves archives are compared entry by entry.
A common type of nested archive is a WAR file containing JAR files.

This feature was contributed by [Björn Kautler](https://github.com/Vampire).

### Daemon health - TODO

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

### Multiple `PluginContainer` methods are deprecated.

[`PluginContainer.apply(String)`](javadoc/org/gradle/api/plugins/PluginContainer.html#apply\(java.lang.String\)) and
[`PluginContainer.apply(Class)`](javadoc/org/gradle/api/plugins/PluginContainer.html#apply\(java.lang.Class\)) methods are deprecated, 
please use [`PluginAware.apply(Map)`](javadoc/org/gradle/api/plugins/PluginAware.html#apply\(java.util.Map\)) or 
[`PluginAware.apply(Closure)`](javadoc/org/gradle/api/plugins/PluginAware.html#apply\(groovy.lang.Closure\)) instead.

    // Instead of…
    project.plugins.apply("java")
    
    // Please use…
    project.apply(plugin: "java")

All other mutative methods of `PluginContainer` are deprecated without replacements:

* `add(Plugin)`
* `addAll(Collection<? extends Plugin>)`
* `clear()`
* `remove(Object)`
* `removeAll(Collection<?>)`
* `retainAll(Collection<?>)`

These methods have no useful purpose.   

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to incubating component metadata rules

- The `eachComponent` method on the incubating `ComponentMetadataHandler` interface has been replaced with `all`.
- Arguments to metadata rules must now have a typed `ComponentMetadataDetails` argument as the first argument.

### Major to incubating 'native-component' and 'jvm-component' plugins

As we develop a new configuration and component model for Gradle, we are also developing an underlying infrastructure to allow
the easy implementation of plugins supporting new platforms (native/jvm/javascript) and languages (C/C++/Java/Scala/CoffeeScript).

This version of Gradle takes a big step in that direction, by migrating the existing component-based plugins to sit on top of this
new infrastructure. This includes the incubating 'jvm-component' and 'java-lang' plugins, as well as all of the plugins providing
support for building native applications.

Due to this, the DSL for defining native executables and libraries has fundamentally changed. The `executables` and `libraries` containers
have been removed, and components are now added by type to the `components` container owned by the model registry. Another major change is
that source sets for a component are now declared directly within the component definition, instead of being configured on the `sources` block.

Please take a look at the sample applications found in `samples/native-binaries` to get a better idea of how you may migrate your Gradle build
file to the new syntax.

Note that this functionality is a work-in-progress, and in some cases it may be preferable to remain on an earlier version of Gradle until
it has stabilised.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Lari Hotari](https://github.com/lhotari) - improvements to output handling in Tooling API (GRADLE-2687) and coloring for the output
* [Sébastien Cogneau](https://github.com/scogneau) - share distribution plugin logic with application plugin
* [Greg Chrystall](https://github.com/ported) - idea plugin generates wrong sources jar for multi artifacts dependencies (GRADLE-3170)
* [Rob Upcraft](https://github.com/upcrob) - add support for ANTLR v3 and v4 to antlr plugin (GRADLE-902)
* [Andreas Schmid](https://github.com/aaschmid) - changes to Eclipse classpath generating when using WTP (GRADLE-1422) 
* [Björn Kautler](https://github.com/Vampire) - improvements to Build Comparison plugin

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
