
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

A [public repository](https://bintray.com/gradle/gradle-plugins) hosted at [bintray](http://bintray.com) will be created to make plugins
available to the community. The bintray UI provides plugin authors with a simple way publish and share their plugin with the community.
It also allows build authors with a simple way to discover plugins.

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

## Declaring and applying plugins

A new DSL block will be introduced to apply plugins from a Gradle script. The plugins declared in this block will be made
available to the script's compile classpath:

    plugins {
        // Apply the given plugin to the target of the script
        apply plugin: 'some-plugin'
        // Apply the given version of the plugin to the target of the script
        apply plugin: 'some-plugin', version: '1.2+'
        // Apply the given script to the target of this script
        apply from: 'some-script.gradle'
    }

The block will be supported in build, settings and init scripts.

Using a separate DSL block means that:

- The block can be executed before the rest of the script is compiled. This way, the public API of each plugin can be made available to the script.
- The block can be executed early in the build, to allow configuration-time dependencies on other projects to be declared.
- The plugin DSL can be evolved without affecting the current DSL.

The `plugins {} ` block is executed after the `buildscript {}` blocks, and must appear after it in the script.
Script execution becomes:

1. Parse the script to extract the `buildscript {}` and `plugins {}` blocks.
2. Compile the blocks, using the Gradle API as compile classpath. This means that the classpath declared in the `buildscript {}` block
   will not be available to the `plugins {}` block.
2. Execute the blocks in the order they appear in the script.
3. Resolve the script compile classpath. This means resolving the `classpath` declared in the `buildscript {}` block, plus the public API
   declared for each plugin, and detecting and resolving conflicts.
4. Compile the script.
5. Execute the script.

The `plugins {}` block does not delegate to the script's target object. Instead, each script has its own plugin handler. This handler represents
a context for resolving plugin declarations. The plugin handler is responsible for taking a plugin declaration, resolving it, and then
applying the resulting plugin implementation to the script's target object.

Note that plugins that are declared in the `plugins {}` block are not visible in any classpath outside the declaring script. This contrasts to the
classpath declared in a build script `buildscript {}` block, which is visible to the build scripts of child projects.

### Open issues

- Select the target to apply the plugin to, eg `allprojects { ... }`.
- Allow scripts to be applied from this block, and make them visible to the script classpath.
- What happens when the API is used to apply a plugin?
- A hierarchy of plugin containers:
    - Build-level plugin resolver (mutable)
        - One for each init script
        - One for each settings script
        - One for each build script
- How to make a plugin implementation visible to all scripts?
- How to declare plugin repositories?
- How to make classes visible without applying the plugin?
- How apply a plugin that is built in the same project?

## Declaring plugin repositories

The `plugins {}` block will allow plugin repositories to be declared.

    plugins {
        repositories {
            // uses the public bintray repo for plugin meta-data and modules, and jcenter for additional modules
            gradlePlugins()
            // uses the given bintray repo for plugin meta-data and modules
            bintray {
                url '...'
            }
            // uses the given repo for modules
            maven {
                url '...'
            }
        }
    }

If no repositories are declared, this will default to `gradlePlugins()`. Some mechanism (TBD) will be available to configure this default in some way.

### Open issues

- This means that a given plugin implementation can end up with different implementation classpaths in different scripts. Allow this? Fail?
- Separate plugin meta-data and module repository declarations?
- Give a name to the protocol used to resolve from bintray and use this name for bintray and artifactory repositories.
- An init script should be able to define how to resolve plugins for all settings and build scripts and for API usage. Possibly for buildSrc as well.
- An settings script should be able to define how to resolve plugins for all build scripts, and for API usage.
- A root build script should be able to define how to resolve plugins for all build scripts (including self) and for API usage.

## Examples

## Plugin resolution

The general problem is, given a plugin declaration

    apply plugin: 'some-plugin'

we need to resolve the plugin name `some-plugin` to an implementation class.

The current mechanism searches the classpath declared by the script associated with the target object for a
plugin with that name. So, in the case of a `Project` object, the classpath declared in the build script is searched.
The setting script classpath is used in the case of a `Settings` object, and the init script classpath in the case of a `Gradle` object.

Plugin declarations will be generalised to become a kind of dependency declaration, so that:

    apply <some-criteria>

means: 'apply a plugin implementation that meets the given criteria'. Initially, the criteria will be limited to plugin name and
version.

As for other kinds of dependency resolution in Gradle, there will be a number of resolvers that Gradle will use to
search for plugin implementations. A resolver may search some repository or other location for a plugin. There will be several such resolvers baked
into Gradle, but it will be possible to add custom implementations:

1. A Gradle core plugin resolver. This will use a hard-coded set of mappings from plugin name to implementation module. It may use the
   implementation module included the distribution, if present, or may resolve the implementation module from the public bintray repository.
   This resolver allows the core Gradle plugins to be moved out of the Gradle distribution archives,
   changing Gradle into a logical distribution of a small runtime and a collection of plugins that can be downloaded separately as
   required, similar to, say, a Linux distribution. It further allows some plugins to be moved out of the distribution entirely,
   via deprecation of a particular mapping.
1. The classpath resolver. This will use the appropriate search classpath to locate a plugin implementation.
1. The public bintray repository. This will resolve plugin implementations using meta-data and files from the pubic bintray repository.
1. Possibly also a resolver that uses mappings rules provided by the build. This would allow, for example, for a build to say things like:
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

Resolution of a plugin declaration using a plugin repository is made up of two steps: First, the plugin declaration is resolved to a plugin implementation component.
Second, the plugin component and its dependencies are resolved to a classpath and the plugin implementation class is loaded from this classpath.

The provided repository may be used to perform one or both resolution steps. For example, the Gradle core plugin resolver does not use the repository to determine the
implementation component, but may use the repository resolve the component to a classpath.

Step 1: Given a plugin name and version, use repository to resolve to a plugin component:

1. If the given name and version have already been resolved to a plugin component in this build, reuse the mapping.
1. If the given name and version to component mapping is present in the persistent cache and has not expired, reuse the mapping.
1. If running `--offline`, fail.
1. Fetch from the repository the list of packages that have the plugin name associated with the package. Select the highest version that
   meets the version criteria and which is compatible with the current Gradle version. Fail if there are no such packages.
1. Cache the result.

Step 2: Given a plugin name and plugin component:

1. If the component has been resolved in this build, reuse the mapping.
1. Resolve the component and its runtime dependencies from the repository to produce a runtime classpath
1. Load the runtime classpath into a `ClassLoader` whose parent is the Gradle API `ClassLoader` (see [ClassLoader graph](https://docs.google.com/drawings/d/1-hEaN0HDSbyw_QSuK8rUOqELohbufyl7osAQvCd7COk/edit?usp=sharing)).
1. Load the plugin implementation class from this `ClassLoader`.
1. Cache the result.

Note that no core plugins will be visible to the plugin implementation by default. These will be declared as explicit dependencies of the plugin (TBD).

# Stories

## Story: Spike plugin resolution from bintray

Add some basic DSL and resolver infrastructure to demonstrate plugin resolution from the public plugin repository.

## Story: Introduce plugins DSL block

Adds the initial DSL support and APIs. At this stage, can only be used to apply core plugins to the script's target object. Later stories make this more useful.

### Test cases

- Script can use a `plugins { ... }` block to apply a core plugin.
- Can use both `buildscript { ... }` and `plugins { ... }` blocks in a script to apply plugins.
- Build author receives a nice error message when:
    - A statement other than `buildscript { ... }` precedes the `plugins { ... }` statement.
    - Attempting to apply an unknown plugin in a `plugins { ... }` block.
        - Should give a set of candidate plugin ids that are available.
    - Attempting to apply a core plugin with a version selector in a `plugins { ... }` block.
    - Attempting to apply a plugin declared in the script's `buildscript { ... }` from the `plugins { ... }` block.
    - Attempting to apply a plugin declared a parent project's build script `buildscript { ... }` from the `plugins { ... }` block.
- The script's delegate object is not visible to the `plugins { ... }` block.

## Story: Resolve hard-coded set of plugins from public bintray repository

Adds a basic mechanism to load plugins from a repository. Adds a plugin resolver that uses a hard-coded mapping from plugin name + version to implementation component,
then resolves the implementation from the public repository and `jcenter`. At this stage, the repository is used to resolve the plugin implementation, but the
plugin meta-data is not used.

Cache implementation ClassLoader with a given build invocation, so that if multiple scripts apply the same plugin, then the same implementation Class is used.

The Gradleware developers will select a small set of plugins to include in this hard-coded mapping. The mapping should ideally include the Android plugins.

At this stage, dependencies on other plugins are not supported. Dependencies on other components are supported.

### Test cases

- The classes from plugins declared in a script's `plugins { ... }` block are visible when compiling the script.
- When a parent project's build script uses a `plugins { ... }` block to apply non-core plugins:
    - The classes from plugins are not visible when compiling a child project's build script.
    - The plugins are not visible via a child project's `Project.apply()` method.
- Verify that a plugin applied using `plugins { ... }` block is not visible via the project's `Project.apply()` method.
- When multiple scripts apply the same plugin to different targets, the plugin implementation is downloaded from remote repository once only and cached.
- When multiple scripts apply the same plugin to different targets, the plugin classes are the same.

### Open issues

- Which classes to make visible for a plugin?

## Story: Resolve plugins from public plugin repository

Extend the above mechanism to use plugin meta-data from the public plugin repository to map a plugin name + version to implementation component.

Uses meta-data manually attached to each package in the repository. Again, the Gradleware developers will select a small set of plugins to include in the repository.

Implementation should use `http://plugins.gradle.org` as the entry point to the public plugin repository.

### Test cases

- When multiple scripts apply the same plugin to different targets, the plugin resolution is done against the remote repository once only and cached.
- Build author receives a nice error message when using the `plugins { ... }` block to:
    - Attempt to apply a plugin from a remote repository without declaring a version selector.
    - Attempt to apply an unknown plugin.
        - Should list some candidates that are available, including those in the remote repositories.
    - Attempting to apply an unknown version of a plugin.
        - Should list some candidate versions that are available.

## Story: External plugins are usable when offline

Cache the plugin mapping. Periodically check for new versions when a dynamic version selector is used. Reuse cached mapping when `--offline`.

## Story: Make plugin DSL public

- Include new DSL in DSL reference.
- Include types in the public API.
- Add some material to the user guide discussion about using plugins.
- Announce in the release notes.

## Story: Plugin author requests that plugin version be included in the Gradle plugins repository

For now, the set of plugins available via the public plugin repository will be curated by Gradleware, such that some manual action is required to add
a new plugin (version) to the public repository.

TBD - perhaps implement this using the bintray 'contact' UI plus some kind of reference from the Gradle website.

Retire the 'plugins' wiki page some point after this.

## Story: Plugins declare dependencies on other plugins

Should include dependencies on core plugins.

When two plugins declare a dependency on some other plugin, the same plugin implementation ClassLoader should be used in both cases. Similarly, when
a build script and a plugin declare a dependency on the same plugin, the same implementation ClassLoader should be used in both cases.

## Story: Plugin author publishes plugin to bintray

Add a basic plugin authoring plugin, that adds support for publishing to bintray with the appropriate meta-data.

## Story: Resolve plugins relative to Gradle distribution

Plugin resolution uses Gradle runtime's URL (i.e as used by the wrapper) to locate a repository to search for plugins
and implementations.

TBD - introduce some plugins mapping artifact, or perhaps use an init script to bootstrap the mappings.

Plugin mappings are cached per repository.

Deprecate the 'custom distribution' feature some time after this.

## Story: Resolve plugins from enterprise repository

Allow the plugin repositories to use to be declared.

Multiple plugin and module repositories can be declared.

## Story: Resolve local script plugins

Add a resolver that uses some convention to map plugin id to a build script.

    plugins {
        // Looks for a script in $rootDir/gradle/plugins/groovy-project.gradle
        apply plugin: 'groovy-project'
    }

## Story: Daemon reuses plugin implementation

Cache the implementation ClassLoader across builds. More details in the [performance spec](performance.md).

## Story: Resolve core plugins from public repository

- Publish core plugins to a public repository (possibly bintray)
- Produce a minimal Gradle distribution that does not include any plugins
- Change default wrapper configuration to download to this distribution
- Resolve a class import at script compilation time to a core plugin implementation on demand
- Introduce plugin resolution for old DSL.

Deprecate the bin Gradle distribution some time after this.

# More stories

These are yet to be mixed into the above plan:

- Build-init plugin custom build types by resolving build type name to plugin implementation using plugin repository
- Resolve tooling model to provider plugin implementation using plugin repository
- Plugin (script) declares public API
- Resolve script plugins from plugin repository

# Open issues

- conditional plugin application
- need some way to tweak the resolve strategy for plugin component resolution.
- declaring dependencies of a plugin on other plugins
- configuring which repositories, possibly none, to use to resolve plugin declaration and to use to resolve implementation modules.
- backwards compatibility wrt moving the core plugins. eg all core plugins are currently visible on every script compile classpath.
- declare and expose only the API of the plugin
- handle conflicts where different versions of a plugin are requested to be applied to the target object.
- conflict resolution for the script compile classpath when mixing combinations of `plugins { apply ... } ` and `dependencies { classpath ... }` and inherited classpath.
- deprecate and remove inherited classpath.
- plugins that add new kinds of repository and resolver implementations or that define and configure the repositories to use.
- automate promotion of new plugin versions to the public repository
