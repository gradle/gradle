
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

# Goals / Rationale

## Spec to implementation mapping

Resolving plugins is more complicated than resolving Maven or Ivy dependencies (as currently implemented/practiced).
Conceptually, an extra layer is necessary to resolve a “plugin spec” (i.e. id/version) into a description of how to use it (i.e. the software and metadata needed to 'instantiate' it).
Potentially, then the implementation of the components then need to be resolved.
Decoupling the requirement (i.e. plugin X) from how to provide it (i.e. this jar in that repository) enables many benefits.

## Forward declaration

Additionally, the fact that a build uses a given plugin will be “hoisted” up.
At the moment, we can't reliably identify what plugins are used by a build until we configure the whole build.
This prevents us from using that information in helpful ways.
For example, tooling could determine which plugins are in use without having to run full configuration and could provide better authoring assistance.
It also allows more optimised classloading structures to be formed.

## Isolation

Plugins also need to be insulated from each other.
Currently plugin implementations are pushed into a common classloader in many circumstances.
This causes class versioning problems, and all of the other problems associated with loading arbitrary code into an unpartitioned class space.
Plugins under the new mechanism will be subject to isolation from each other, and collaboration made more formal.

Plugins forward declare their dependencies on other JVM libraries and separately on other Gradle plugins.
Plugins also declare which classes from their implementation and dependencies are exported (i.e. visible to consumers of the plugin).

_The exact mechanism and semantics of this sharing are TBD._

## Plugin ids

Plugin ids will now be namespaced.
This avoids collision by partitioning the namespace.
Importantly, it also allows us (Gradle core team) to provide an “official” implementation of a previously available plugin by creating our own version, or taking ownership of the existing, and releasing under our namespace.

Plugin ids…

1. may contain any alpha numeric ascii character and -
1. must contain at least one '.' character, that separates the namespace from the name
1. consist of a namespace (everything before the last '.') and a name (everything after the last '.')
1. conventionally use a lowercase reverse domain name convention for the namespace component
1. conventionally use only lowercase characters for the name component

Plugin specs can be made of qualified («namespace.name») or unqualified («name») plugin ids.

Qualified: `org.gradle.java`
Unqualified: `java`

Individual plugin resolvers are at liberty to implicitly qualify unqualified ids.
For example, the `plugins.gradle.org` based plugin resolver implicitly qualifies all unqualified ids to the `'org.gradle'` namespace.

### Open Questions

- Should we support all unicode alphanums? (it seems there is less agreement about what this set is)
- When there is more than one implicit namespace, collisions can change over time as new plugins get added to the earlier resolved namespaces

## plugins.gradle.org

Gradle will ask questions of this service via a JSON-over-HTTP API that is tailored for Gradle.
Initially the focus of the service will be to power plugin _use_; over time it will expand to include browsing/searching and publishing.
The `plugins.gradle.org` service will use a [Gradle plugin specific bintray repository](https://bintray.com/gradle/gradle-plugins) (implicitly) as the source of data.
Bintray provides hosting, browsing, searching, publishing etc.

# Implementation plan

## Declaring plugins

A new DSL block will be introduced to apply plugins from a Gradle script. 

    plugins {
        // Declare that 
        id "some-plugin"
        id("some-plugin")
        
        // Apply the given version of the plugin to the target of the script
        id 'some-plugin' version '1.2+'
        id('some-plugin').version('1.2+')
    }

The block will be supported in build, settings and init scripts.

Script execution becomes:

1. Parse the script to extract the `buildscript {}` and `plugins {}` blocks
1. Compile the blocks, using only the Gradle API as compile classpath 
1. Execute the blocks in the order they appear in the script
1. Resolve the script compile classpath according to `buildscript {}`
1. Resolve the plugins according to `plugins {}` (details in subsequent section)
1. Merge the script compile classpath contributions from `plugins {}` with `buildscript {}`
1. Compile the “rest” of the script
1. Apply the plugins defined by `plugins {}` to the target (details in subsequent section)
1. Execute the "rest" of the script as per normal

Note that plugins that are declared in the `plugins {}` block are not visible in any classpath outside the declaring script. This contrasts to the
classpath declared in a build script `buildscript {}` block, which is visible to the build scripts of child projects.

The `plugins {}` block is a _heavily_ restricted DSL. 
The only allowed constructs are:

1. Calls to the `id(String)` method with a `String` literal object, and potentially a call to the `version(String)` with a string literal method of this methods return.

Attempts to do _anything_ else will result in a _compile_ error.
This guarantees that we can execute the `plugins {}` block at any time to understand what plugins are going to be used.

**Note:** `allprojects {}` and friends are not compatible with this new DSL.
Targets cannot impose plugins on other targets.
A separate mechanism will be available to perform this kind of generalised configuration in a more managed way (discussed later).

**Note:** Eventually, the `plugin {}` mechanism as described will also support script plugins.
Script plugins will be mapped to ids/versions elsewhere in the build, allowing them to be consumed via the same mechanism (discussed later).

### Open issues

- How practical is it to lock down the `plugins {}` DSL so tightly 
- What is the significance of plugin ordering?

## Plugin spec to implementation mappings

Plugin clients declare plugin dependencies in terms of specification.
The settings.gradle file, and init scripts provide the mappings from plugin specs to implementations.

    pluginMappings {
        repositories {
            // DSL for declaring external sources of information about plugins
        }
        scripts {
            // DSL for declaring that a local script is the implementation of a particular plugin
        }
    }

If no repositories are declared, this will default to `plugins.gradle.org`. 
Some mechanism (TBD) will be available to configure this default in some way.

### Open issues

- What does the repositories DSL look like?
- What does the scripts DSL look like?
- Does the `pluginMappings` block apply to the settings/init plugins? If not, how does one specify the mappings there?
- Does the `pluginMappings` block get extracted and executed in isolation like the `plugins` block? With similar strictness?
- Can plugins contribute to the `pluginMappings` block in some way?
- How do buildSrc plugins work with mapping?
- How do `pluginMappings` blocks in multiple init scripts and then the settings script compose?
- Should an individual build script have its own mapping overrides?
- Order of precedence between repository and script plugins
- Could an `inline {}` DSL be used here to give the location of arbitrary detached Gradle plugin projects that need to be built? (i.e. buildSrc replacement)

## Script plugins

Script plugins and binary plugins will be unified from a consumption perspective.
A script plugin is simply another way to implement a plugin.

A convention will be established that maps plugin id to script implementation.

`id("foo")` = `$rootDir/gradle/plugins/foo.gradle`
`id("foo.bar")` = `$rootDir/gradle/plugins/foo.bar.gradle`
`id("foo").version("1.0")` = `$rootDir/gradle/plugins/foo_1.0.gradle`
`id("foo.bar").version("1.0")` = `$rootDir/gradle/plugins/foo.bar_1.0.gradle`

Explicit mappings will also be possible via the `pluginMappings {}` DSL (details TBD).

This requires that script plugins can express all things that binary plugins can in terms of usage requirements:

1. Dependencies on other plugins - specified by the plugin script's `plugins {}` block
1. Dependencies on JVM libraries - _TBD (`buildscript.dependencies`?)_
1. Entry point/implementation - script body
1. Exported classes - _TBD_

As new capabilities are added to plugins (particularly WRT new configuration model), consideration should be given to how script plugins express the same thing.

### Open questions

- How are unqualified ids of plugin dependencies to be interpreted? (i.e. script plugins can be used across builds, with potentially different qualifying rules)
- Do these 'new' script plugins need to declare that they are compatible with the new mechanism? Are there any differences WRT their implementation?

## Plugin resolution

Each plugin spec is independently resolvable to an implementation.

### Specs

A spec consists of a:

* plugin id (qualified or unqualified)
* compatibility constraints

Compatibility constraints consist of:

* version constraints (may be empty)
* target Gradle runtime

#### Open questions

- Should the other plugins in play be considered part of the spec? (i.e. find the “best” version that works with everything else that is in play)

### Resolver types

A spec is resolved into an implementation by iterating through the following resolvers, stopping at the first match.

#### Open Questions

- When resolving a plugin for the first time and running with --offline, should the build fail as soon as a non core/script resolver is attempted?

#### Core plugin resolver

The core plugin resolver is responsible for resolving plugins that are considered to be core to Gradle and are versioned with Gradle.

The list (and metadata) of core plugins is hardcoded within a Gradle release.
Core plugins are NOT necessarily distributed with the release.
The implementation components may be obtainable from jcenter, allowing the distribution to be thinned with components obtained on demand.

Core plugins are always in the `org.gradle` namespace.

##### Open Questions

* What precautions need to be taken when adding a new plugin to the core list? as these will take precedence over the previously used implementation of an unqualified and unversioned plugin spec?

#### Script plugin resolver

Resolver for conventional script plugin locations (see above).

#### plugins.gradle.org resolver

This resolver will ask the `plugins.gradle.org` web service to resolve plugin specs into implementation metadata, that Gradle can then use to obtain the implementation.

Plugin specs will be serialized into `plugins.gradle.org` URLs.
Requests to such URLs yield JSON documents that act as the plugin metadata, and provide information on how to obtain the implementation of the plugin.
Or, they may yield JSON documents that indicate the known versions of the requested plugin that meet the specification.

#### User mapping resolver

This resolver uses the explicit rules defined by the build environment (i.e. init scripts, settings scripts) to map the spec to an implementation.

### Dynamic versions

Version constraints may be dynamic.
In this case, each plugin resolver is asked for all of the versions of the plugin that it knows about that are otherwise compatible.
The best version available, considering all resolvers, will be used.
Note that this is different to “normal” Gradle dependency resolution that only considers the first repository that has any version of the thing.

Resolvers are responsible for providing the potential versions.
Selecting the actual version to use based on the version constraint is performed by Gradle.

Dynamic versions are specified using the same syntax that is currently used…

    id("foo").version("0.5.+")


# Stories


## Story: Introduce plugins DSL block

Adds the `plugins {}` DSL to init, settings and build scripts (not arbitrary script plugins at this point). Plugin specs can be specified in the DSL, but they don't do anything yet.

### Implementation

1. Add a `PluginSpecDsl` service to all script service registries
1. Add a compile transform that rewrites `plugins {}` to be `ConfigureUtil.configure(services.get(PluginSpecDsl), {})` or similar - we don't want to add a `plugins {}` method to any API
    - This should probably be added to the existing transform that extracts `buildscript {}`
1. Add an `id(String)` method to `PluginSpecDsl` that returns `PluginSpec`, that has a `version(String)` method that returns `PluginSpecDsl` (self)
1. Update the `plugin {}` transform to disallow everything except calling `id(String)` and optionally `version(String)` on the result
1. Update the transform to error if encountering any statement other than a `buildscript {}` statement before a `plugins {}` statement
1. Update the transform to error if encountering a `plugins {}` top level statement in a script plugin

### Test cases

- `plugins {}` block is available to init, settings and build scripts
- Statement other than `buildscript {}` before `plugins {}` statement causes compile error, with correct line number of offending statement
- `buildscript {}` is allowed before `plugins {}` statement
- multiple `plugins {}` blocks in a single script causes compile error, with correct line number of first offending plugin statement
- `buildscript {}` after `plugins {}` statement causes compile error, with correct line number of offending buildscript statement
- Disallowed syntax/constructs cause compile errors, with correct line number of offending statement and suitable explanation of what is allowed (following list is not exhaustive)
  - Cannot access `Script` api
  - Cannot access script target API (e.g. `Gradle` for init scripts, `Settings` for settings script, `Project` for build)
  - Cannot use if statement
  - Cannot define local variable
  - Cannot use GString values as string arguments to `id()` or `version()`
  
## Story: Can use plugins {} DSL to apply core plugin from buildscript to `Project`



--- 

Stories below are still to be realigned after recent direction changes. In progress.

## Story: Resolve hard-coded set of plugins from public bintray repository

Adds a basic mechanism to load plugins from a repository. Adds a plugin resolver that uses a hard-coded mapping from plugin name + version to implementation component,
then resolves the implementation from the public repository and `jcenter`. At this stage, the repository is used to resolve the plugin implementation, but the
plugin meta-data is not used.

Cache the implementation ClassLoader within a single build invocation, so that if multiple scripts apply the same plugin, then the same implementation Class is used
in each location. The implementation ClassLoader should be wrapped in a filtering ClassLoader so that the plugin id resources `/META-INF/gradle-plugins/**` are not
visible.

Change the construction of the script ClassLoaders so that:

- Each script has a 'parent scope' ClassLoader.
    - For the build script of a non-root project, this is the 'public scope' of the parent project's build script (for backwards compatibility).
    - For all other scripts, this is the root ClassLoader, which exposes the Gradle API and core plugins.
- Each script has a 'public scope' ClassLoader:
    - When the `buildscript { ... }` block does not declare any classpath, this is the same as the 'parent scope' ClassLoader.
    - When the `buildscript { ... }` block declares a classpath, these classes are loaded a ClassLoader whose parent is the 'parent scope' ClassLoader.
      This is 'public scope' ClassLoader for the script.
- The script classes are loaded in a ClassLoader whose parents are the 'public scope' ClassLoader plus and implementation ClassLoaders for any plugins declared
  in the `plugins { ... }` block.
- The 'public scope' of a project's build script is used to find plugins by `Project.apply()`

The Gradleware developers will select a small set of plugins to include in this hard-coded mapping. The mapping should ideally include the Android plugins.

At this stage, dependencies on other plugins are not supported. Dependencies on other components are supported.

### Test cases

- The classes from plugins declared in a script's `plugins { ... }` block are visible:
    - when compiling the script. (✓)
- The classes from plugins declared in a script's `plugins { ... }` block are NOT visible:
    - from classes declared in a script's `buildscript { ... }` block. (✓)
- When a parent project's build script uses a `plugins { ... }` block to apply non-core plugins:
    - The classes from plugins are not visible when compiling a child project's build script. (✓)
    - The plugins are not visible via a child project's `Project.apply()` method. (✓)
- Verify that a plugin applied using `plugins { ... }` block is not visible via the project's `Project.apply()` method. (✓)
- When multiple scripts apply the same plugin to different targets, the plugin implementation is downloaded from remote repository once only and cached. (✓)
- When multiple scripts apply the same plugin to different targets, the plugin classes are the same. (✓)

### Open issues

- Which classes to make visible from a given plugin?
- Should possibly allow `buildscript { }` classes to see `plugins { }` classes, so that a custom plugin can extend a public plugin.

## Story: Resolve plugins from public plugin repository

Extend the above mechanism to use plugin meta-data from the public plugin repository to map a plugin name + version to implementation component.

Uses meta-data manually attached to each package in the repository. Again, the Gradleware developers will select a small set of plugins to include in the repository.

Implementation should use `http://plugins.gradle.org` as the entry point to the public plugin repository.

### Test cases

- When multiple scripts apply the same plugin to different targets, the plugin resolution is done against the remote repository once only and cached.
- Build author receives a nice error message when using the `plugins { ... }` block to:
    - Attempt to apply a plugin from a remote repository without declaring a version selector. (✓)
    - Attempt to apply an unknown plugin.
        - Should list some candidates that are available, including those in the remote repositories.
    - Attempting to apply an unknown version of a plugin.
        - Should list some candidate versions that are available.
    - Plugins with -SNAPSHOT versions are requested (Bintray does not allow snapshot versions)
- Plugins can be resolved with status version numbers (e.g. latest.release)
- Plugins can be resolved with version ranges (e.g. 2.+, ]1.0,2.0])

## Story: External plugins are usable when offline

Cache the plugin mapping. Periodically check for new versions when a dynamic version selector is used. Reuse cached mapping when `--offline`.

## Story: Plugins included in Gradle public repository are smoke tested

For plugins to be listed in the public repository, there must be some external (i.e. not performed by plugin author) verification that the plugin is not completely broken.
That is, the plugin should be:

1. Able to be applied via the new plugin mechanism
2. Not produce errors after simply applying

This will (at least) need to be able to be performed _before_ the plugin is included in the public repository. 

### Open issues

1. Are existing plugins periodically tested? Or only upon submission (for each new version)?
1. What action is taken if a plugin used to work but no longer does?

## Story: Make plugin DSL public

- Include new DSL in DSL reference.
- Include types in the public API.
- Add some material to the user guide discussion about using plugins.
- Update website to replace references to the 'plugins' wiki page to instead reference `http://plugins.gradle.org`
- Update the 'plugins' wiki page to direct build authors and plugin authors to `http://plugins.gradle.org` instead.
- Announce in the release notes.

## Story: Plugin author requests that plugin version be included in the Gradle plugins repository

For now, the set of plugins available via the public plugin repository will be curated by Gradleware, such that some manual action is required to add
a new plugin (version) to the public repository.

TBD - perhaps implement this using the bintray 'contact' UI plus some kind of reference from the Gradle website.

Retire the 'plugins' wiki page some point after this.

## Story: Build author searches for plugins using Gradle command-line

Introduce a plugin and implicit task that allows a build author to search for plugins from the central plugin repository, using the Gradle command-line.

## Story: Plugins declare dependencies on other plugins

Should include dependencies on core plugins.

When two plugins declare a dependency on some other plugin, the same plugin implementation ClassLoader should be used in both cases. Similarly, when
a build script and a plugin declare a dependency on the same plugin, the same implementation ClassLoader should be used in both cases.

## Story: Plugin author publishes plugin to bintray

Add a basic plugin authoring plugin, that adds support for publishing to bintray with the appropriate meta-data.

## Story: Plugin author can test use of plugin

Authors should be able to test that their plugins are compatible with the new mechanism.

- Provide mechanism to simulate plugin application at unit test level (new mechanism has functional differences at application time)
- Provide mechanism to functionally test new plugin metadata (i.e. correctly declared dependencies on other plugins)

(note: overlap with [design-docs/testing-user-build-logic.md](https://github.com/gradle/gradle/blob/master/design-docs/testing-user-build-logic.md))

## Story: Build author searches for plugins using central Web UI

Introduce a Web UI that allows a build author to search for and view basic details about available Gradle plugins. Backed by the meta-data hosted in the
public Bintray repository.

TBD - where hosted, how implemented, tested and deployed

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

## Story: Daemon reuses plugin implementation across builds

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
- configuring which repositories, possibly none, to use to resolve plugin declaration and to use to resolve implementation modules.
- backwards compatibility wrt moving the core plugins. eg all core plugins are currently visible on every script compile classpath.
- declare and expose only the API of the plugin
- handle conflicts where different versions of a plugin are requested to be applied to the target object.
- conflict resolution for the script compile classpath when mixing combinations of `plugins { apply ... } ` and `dependencies { classpath ... }` and inherited classpath.
- deprecate and remove inherited classpath.
- plugins that add new kinds of repository and resolver implementations or that define and configure the repositories to use.
- automate promotion of new plugin versions to the public repository
