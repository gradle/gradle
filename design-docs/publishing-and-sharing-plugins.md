
This document describes some improvements to Gradle's plugin mechanism to make it easier to publish and share plugins
within the Gradle community and an organisation.

# Use cases

## Publish a plugin to make it available for the community to use

I have an open source plugin implementation, and I want some way to publish this plugin and let people know it exists.
I don't want to manage any infrastructure to do this.

## Discover and use a community plugin in my build

I am a build author. I want to find plugins that are available in the Gradle ecosystem and have some way to easily use
these in my build.

## Discover and use a core plugin in my build

As a build author, I often don't care whether a plugin is a core Gradle plugin or an external plugin.
I want to discover and use core plugins in the same way I discover and use other plugins.

## Make a plugin available to builds in my organisation

I have an enterprise plugin implementation, and I want some way to publish this plugin for builds in my organisation to use.

## Discover when new plugins are available to use

I want to find out when there are new versions of the plugins I use in my build.
I want to find out when there are new plugins available that I could use.

## Understand which plugins my build is using and why

For example, I want to find out which builds in my organisation are using some plugin, for auditing or security or licensing purposes.

## Download less stuff to use a new version of Gradle

The Gradle distribution currently bundles many plugins and their dependencies. Everyone who uses Gradle must download all
this stuff regardless of which (if any) of these plugins they use.

Instead, I want to download just the Gradle runtime and those core plugins I need.

## Install the build runtime for offline use

I have a build which is run on machine which are not connected to any network resources (eg I'm mobile or I'm in a secure environment
or my network connection is extremely slow and/or unreliable).
I want to have a single install image that contains everything that my build needs, including Gradle runtime, core plugins,
community plugins and the dependencies of whatever I need to build.

Similarly, the installation image of Android Studio needs to bundle the Gradle runtime, some core plugins, the Android plugin and some
dependencies.

# Implementation plan

There are several main parts to the solution:

A public repository hosted at [bintray](http://bintray.com) will be created to make plugins available to the community.
The bintray UI provides plugin authors with a simple way publish and share their plugin with the community. It also
allows build authors with a simple way to discover plugins.

The second part of the solution will be to improve the plugin resolution mechanism in Gradle to resolve plugins from
this repository in some convenient way.

Implementation-wise, we plan to change the mechanism for resolving a plugin declaration to a plugin implementation class
to add an extension point. This will allow any particular strategy to be implemented, and strategies to be combined.

Built on top of this extension point will be a resolver implementation that uses the package meta-data from the bintray
repository to resolve a plugin reference to a bintray package, and then to an implementation class.

A "plugin development" plugin will be added to help plugin authors to build, test and publish their plugin to a repository,
with additional integration with bintray to add the appropriate meta-data.

Some additional reporting will also be added to help build authors understand the plugins that are used in their build and
to discover new versions of the plugins that they are using.

## Plugin resolution

The general problem is, given a plugin declaration

    apply plugin: 'some-id'

we need to resolve the plugin name `some-id` to an implementation class.

The current mechanism searches the classpath declared by the script associated with the target domain object for a
plugin with that name. So, in the case of a `Project` object, the classpath declared in the build script is searched.
The setting script classpath is used in the case of a `Settings` object, and the init script classpath in the case of a `Gradle` object.

Plugin declarations will be generalised to become a kind of dependency declaration, so that:

    apply <some-criteria>

means: 'apply a plugin implementation that meets the given criteria'. Initially, the criteria will be limited to plugin name and
version.

As for other kinds of dependency resolution in Gradle, there will be a number of repositories, or locations, where Gradle will
search for plugin implementations. There will be several such repositories baked into Gradle, but it will be possible to add custom
implementations:

1. A Gradle core plugin repository. This will use a hard-coded set of mappings from plugin name to implementation module. It may use the
   implementation module included the distribution, if present, or may resolve the implementation module from the public bintray repository.
   This repository allows the core Gradle plugins to be moved out of the Gradle distribution archives,
   changing Gradle into a logical distribution of a small runtime and a collection of plugins that can be downloaded separately as
   required, similar to, say, a Linux distribution. It further allows some plugins to be moved out of the distribution entirely,
   via deprecation of a particular mapping.
1. The classpath repository. This will use the appropriate search classpath to locate a plugin implementation.
1. The public bintray repository. This will resolve plugin implementations using meta-data and files from the pubic bintray repository.
1. Possibly also a repository that uses mappings rules provided by the build. This would allow, for example, for a build to say things like:
   map plugin name 'x' to this Gradle script, or this implementation module, or this implementation `Class` instance.

Given a plugin declaration `apply plugin: $name`, search for an implementation in the following locations, stopping when a match is found:

1. Search the Gradle runtime's (hard-coded) set of core plugins.
1. Search for a plugin with the given name in the search classpath.
1. If not found, fail. Possibly search bintray for candidate versions to include in the error message.

Given a plugin declaration `apply plugin: $name, version: $version`

If `$version` is a static version selector, then search for a candidate implementation in the following locations, stopping when a match is found.
If `$version` is a dynamic version selector, then search for candidate implementation in all of the following locations, selecting the highest version found:

1. Search for plugin with the given name in the Gradle runtime's mappings. If found, verify that the implementation meets the version criteria.
1. Search for plugin with the given name in the search classpath. If found, verify that the implementation meets the version criteria.
1. Attempt to resolve the plugin name using bintray, as described below.
1. If not found, fail. Possibly search bintray for candidate versions to include in the error message.

### Examples

Apply the core `java` plugin, the implementation is either bundled in the distribution or fetched from a repository:

    apply plugin: `java`

Apply version >= 0.5 and < 0.6 of the `android` plugin fetched from the Gradle plugins bintray repository:

    apply plugin: `android`, version: `0.5.+`

### Resolution of a plugin declaration using a plugin repository

Resolution of a plugin declaration using a plugin repository is made up of two steps: First, the plugin declaration is resolved to an implementation module.
Second, the implementation module and its dependencies are resolved to a classpath and the implementation class is loaded from this classpath.

The repository may be used to perform one or both resolution steps. For example, the Gradle runtime mapping does not use the repository to determine the
implementation module, but may use the repository resolve the module to a classpath.

Step 1: Given a plugin name and version, use repository to resolve to a module:

1. If the given name and version have already been resolved to a package in this build, reuse the mapping.
1. If the given name and version to package mapping is present in the persistent cache and has not expired, reuse the mapping.
1. If running `--offline`, fail.
1. Fetch from the repository the list of packages that have the plugin name associated with the package. Select the highest version that
   meets the version criteria and which is compatible with the current Gradle version. Fail if there are no such packages.
1. Cache the result.

Step 2: Given an implementation module:

1. If the module has been resolved in this build, reuse the mapping.
1. Resolve the module and its dependencies from the respository.
1. Load the implementation classpath into a `ClassLoader` whose parent is the Gradle API `ClassLoader` (see [ClassLoader graph](https://docs.google.com/drawings/d/1-hEaN0HDSbyw_QSuK8rUOqELohbufyl7osAQvCd7COk/edit?usp=sharing)).
1. Load the plugin implementation from this `ClassLoader`.
1. Cache the result.

Note that the classes from the plugin are not made visible to any script. This means that a script will not be able to use any classes provided by the plugin.
Plugin implementation will be made visible to those other plugins whose meta-data includes a dependency declaration on that plugin.

To use the classes from a plugin in a script, the script author will declare that the classes are required by the script:

    buildscript {
        require plugin: 'my-plugin', version: '1.2'
    }

    import my.plugin.SomeClass

    task doStuff(type: SomeClass)

The `require` statement resolves the plugin declaration to an implementation and makes the implementation available to the script compile classpath,
but does not apply the plugin.

Specifically, the `require` statement resolves the plugin declaration to an implementation `ClassLoader`, as for the `apply` statement, and
adds this as a parent of the compile `ClassLoader` for the script. The dependency is not inherited by subprojects, so that each script must declare
its dependencies.

Another example, where a script applies a plugin to several projects:

    buildscript {
        require plugin: 'my-plugin', version: '1.2.+'
    }

    import my.plugin.SomeClass

    subprojects {
        apply plugin: 'my-plugin', version: '1.2.+'
        myPlugin {
            someThing = new SomeClass()
        }
    }

# Stories

TBD

# Open issues

TBD - declaring dependencies of a plugin on other plugins
TBD - configuring which repositories, possibly none, to use to resolve plugin declaration and to use to resolve implementation modules.
TBD - backwards compatibility wrt moving the core plugins. EG all core plugins are currently visible on every script compile classpath.
TBD - declare and expose only the API of the plugin
TBD - require statement adds a mapping rule so that version is not required to apply the plugin later in the same script.
TBD - handle conflicts where different versions of a plugin are requested to be applied to the target object.
TBD - conflict resolution for the script compile classpath when mixing combinations of `require` and `dependencies { classpath ... }` and inherited classpath.
