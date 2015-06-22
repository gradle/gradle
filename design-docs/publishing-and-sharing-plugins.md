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

Another important implication here is that it opens the door to better tooling support.
Knowing which plugins are in use can potentially provide a lot of information relevant to editors in order to provide assistance (e.g. autocomplete).
By plugins being forward declared, we can know what plugins are in use without requiring the script to be well formed.

## Isolation

Plugins also need to be insulated from each other.
Currently plugin implementations are pushed into a common classloader in many circumstances.
This causes class versioning problems, and all of the other problems associated with loading arbitrary code into an unpartitioned class space.
Plugins under the new mechanism will be subject to isolation from each other, and collaboration made more formal.

Plugins forward declare their dependencies on other JVM libraries and separately on other Gradle plugins.
Plugins also declare which classes from their implementation and dependencies are exported (i.e. visible to consumers of the plugin).

The current generation of plugins can be said to export _everything_. They can also be said to depend on the entire “gradle api” (core + plugins).

_The exact mechanism and semantics of this sharing are TBD._

## Plugin dependencies

Plugin dependencies of plugins are now forward declared, which is available as part of the plugin metadata.
When a user declares a dependency on a plugin, Gradle manages transitive resolution of all plugin dependencies and guarantees that all plugin dependencies have been _applied_ before the plugin is applied.

Therefore, ideally, plugins will no longer use project.apply() to apply plugins but will rely on Gradle applying the plugin because the dependency was declared.
Because use of plugins, and the dependencies of those plugins, is forward declared we can understand which plugins are used by a build without executing any “configuration” code.

This means that plugin application is never conditional.
More fine grained mechanisms will be available to plugin implementations for implementing conditional logic (i.e. model configuration rules).

## Plugin ids

Plugin ids will now be namespaced.
This avoids collision by partitioning the namespace.
Importantly, it also allows us (Gradle core team) to provide an “official” implementation of a previously available plugin by creating our own version, or taking ownership of the existing, and releasing under our namespace.

Plugin ids…

1. may contain any alpha numeric ascii character, '.', '_' and -
1. must contain at least one '.' character, that separates the namespace from the name
1. consist of a namespace (everything before the last '.') and a name (everything after the last '.')
1. conventionally use a lowercase reverse domain name convention for the namespace component
1. conventionally use only lowercase characters for the name component
1. 'org.gradle' and 'com.gradleware' namespaces are reserved (users are actively prevented from using these namespaces)
1. Cannot start or end with '.'
1. Cannot contain '..'

Plugin specs can be made of qualified («namespace.name») or unqualified («name») plugin ids.

Qualified: `org.gradle.java`
Unqualified: `java`

Individual plugin resolvers are at liberty to implicitly qualify unqualified ids.
For example, the `plugins.gradle.org` based plugin resolver implicitly qualifies all unqualified ids to the `'org.gradle'` namespace.

### Open Questions

- When there is more than one implicit namespace, collisions can change over time as new plugins get added to the earlier resolved namespaces

## plugins.gradle.org

Gradle will ask questions of this service via a JSON-over-HTTP API that is tailored for Gradle.
Initially the focus of the service will be to power plugin _use_; over time it will expand to include browsing/searching and publishing.
The `plugins.gradle.org` service will use a [Gradle plugin specific bintray repository](https://bintray.com/gradle/gradle-plugins) (implicitly) as the source of data.
Bintray provides hosting, browsing, searching, publishing etc.

# Terminology

In an attempt to avoid confusion the following terms are defined:

- “plugin dependency DSL” - refers to the `plugins {}` construct that can be used to declare dependencies on Gradle plugins (new)
- “buildscript dependency DSL” - refers to the `buildscript {}` construct that is currently used to declare JAR dependencies
- “isolated plugin” - refers to a plugin loaded through the “plugin dependency dsl” (i.e. one of the features of this mechanism is supporting different kinds of isolation)
- “declarative plugin” - refers to a new style plugin that declares its plugin dependencies and its public API (new)
- “non-declarative plugin” - refers to plugins as we know them (they do not formally declare plugin dependencies or any public API)

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

The order of plugin declarations is insignificant.
The natural ordering of plugin application is alphabetical based on plugin _name_ (not id), respecting plugin dependencies (i.e. depended on plugins are guaranteed to be applied before application).

**Note:** `allprojects {}` and friends are not compatible with this new DSL.
Targets cannot impose plugins on other targets.
A separate mechanism will be available to perform this kind of generalised configuration in a more managed way (discussed later).

**Note:** Eventually, the `plugin {}` mechanism as described will also support script plugins.
Script plugins will be mapped to ids/versions elsewhere in the build, allowing them to be consumed via the same mechanism (discussed later).

**Note:** Plugins applied through the new mechanism _cannot_ apply plugins using `project.apply()`.
However, for backwards compatibility, they can apply plugins that have already been applied for backwards compatibility reasons (discussed later).

### Open issues

- How practical is it to lock down the `plugins {}` DSL so tightly 

## Plugin spec to implementation mappings

Plugin clients declare plugin dependencies in terms of specification.
The settings.gradle file, and init scripts provide the mappings from plugin specs to implementations.

    pluginMappings {
        repositories {
            // DSL for declaring external sources of information about plugins
        }
    }

The default list of repositories will be:

    repositories {
        defaultScriptDir() // script plugins in `$rootDir/gradle/plugins`
        gradlePlugins() // plugins.gradle.org
    }

The 'core plugin repository' is always implicitly the first repository and cannot be disabled.
If any plugin repositories are declared, the `defaultScriptDir()` and `gradlePlugins()` defaults are removed.
Some mechanism (TBD) will be available to configure the defaults (i.e. repositories used when none specified) in some way.

### Potential repository types

- Directory containing script plugins (other than the default convention)
- HTTP “directory” containing script plugins
- Remote directory available over SFTP
- Other instance of plugin portal

### Open issues

- What does the repositories DSL look like?
- Does the `pluginMappings` block apply to the settings/init plugins? If not, how does one specify the mappings there?
- Does the `pluginMappings` block get extracted and executed in isolation like the `plugins` block? With similar strictness?
- Can plugins contribute to the `pluginMappings` block in some way?
- How do buildSrc plugins work with mapping?
- How do `pluginMappings` blocks in multiple init scripts and then the settings script compose?
- Should an individual build script have its own mapping overrides?
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
1. Dependencies on JVM libraries - specified by the plugin script's `buildscript {}` block
1. Entry point/implementation - script body
1. Exported classes - the public classed declared in the script

As new capabilities are added to plugins (particularly WRT new configuration model), consideration should be given to how script plugins express the same thing.

### Open questions

- How are unqualified ids of plugin dependencies to be interpreted? (i.e. script plugins can be used across builds, with potentially different qualifying rules)
- Do these 'new' script plugins need to declare that they are compatible with the new mechanism? Are there any differences WRT their implementation?

## Plugin resolution and application

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
Each resolver must respect `--offline`, in that if it needs to reach out over the network to perform the resolution and `--offline` has been specified then the resolution will fail.
This doesn't apply to loading implementations (e.g. local scripts) from disk.

#### Core plugin resolver

The core plugin resolver is responsible for resolving plugins that are considered to be core to Gradle and are versioned with Gradle.

The list (and metadata) of core plugins is hardcoded within a Gradle release.
Core plugins are NOT necessarily distributed with the release.
The implementation components may be obtainable from jcenter, allowing the distribution to be thinned with components obtained on demand.

Core plugins are always in the `org.gradle` namespace.

*Note:* If a Gradle release includes a new plugin in the core namespace, this needs to be advertised.
Technically, it's a breaking change.
If the to-be-upgraded build was using an unqualified id to depend on a plugin where there is now a core plugin with the same unqualified id, the build will fail because core plugin dependencies cannot contain version numbers.
The resolution is to fully qualify the plugin id.

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

Resolvers are responsible for providing the potential versions.
Selecting the actual version to use based on the version constraint is performed by Gradle.

Dynamic versions are specified using the same syntax that is currently used…

    id("foo").version("0.5.+")
    
### Resolution process, class loading and scoping

The current plugin mechanism has the following characteristics:

- Each build script inherits the class loader scope of its parent project
- Each build script's `buildscript {}` declares java modules that should be added to the class loader scope for that project (and the child projects)
- The root project inherits a class loader scope containing the gradle core API and core plugins, and anything in buildSrc
- When plugins are applied, the class loader scope is searched for the implementation
- Script plugins inherit the same class loader scope that the root project build script inherits
- The depended on java modules (and their dependencies) are loaded “on top of” the inherited class loader scope (with a standard parent first class loader strategy) 

The new mechanism:

- Each unit of logic (i.e. plugin, build script, script plugin) declares its java module dependencies and gradle plugin dependencies (which does not mean they inherit the java module dependencies of their plugin dependencies)
- Each unit of logic (incl. build scripts) only has visibility to the public classes of its dependencies + the gradle core (no plugins) API
- The collective public API is managed from “compatibility” (e.g. version conflict resolution)
- (implied) the class loader scope of a script has no relationship to anything that it does not declare

Moreover, there are now two types of plugins; non-declarative (what we have had to date) and declarative (new style, with improved metadata).

Non-declarative plugins:

- Implicitly make their entire implementation (incl. full transitive dependencies) public API (i.e. visible to the user of the plugin)
- Implicitly declare on the Gradle Core API, core plugins, and buildSrc in the context of where they are being used
- _Declare_ no dependencies on Gradle plugins (though, they may depend on the implementation java module of gradle plugins and opaquely apply the plugin in their implementation)

Declarative plugins:

- Have no implicit public API, though they can declare classes of their implementation to be public
- Forward declare their gradle plugin dependencies (and expect Gradle to have applied them before they themselves are applied)
- Only implicitly depend on the Gradle Core API (no core plugins)

To support a gradual migration from the old to the new, the resolution process must support:

1. Using non-declarative plugins via the `buildscript {}` mechanism
2. Using non-declarative plugins via the `plugins {}` mechanism
3. Using declarative plugins via the `buildscript {}` mechanism
4. Using declarative plugins via the `plugins {}` mechanism

How a plugin is loaded/used is a function of _the mechanism by which it is used_, as opposed to whether it is a newer declarative plugin or not.

#### Changes to project.apply(«plugin»)

In order to support users using declarative plugins via the old `buildscript {}` and `apply(«plugin»)` mechanism, a change needs to be made to the `apply()` method.

Declarative plugins declare their plugin dependencies at runtime, via a discoverable mechanism. That is, given a plugin class, it is possible to query the runtime for the _ids_ of the plugins that this plugin depends on. The implementation of `project.apply()` will be changed to query this information, and implicitly apply the plugin dependencies. The exact mechanism for advertising dependencies is yet to be determined. For this concern, it only needs to provide a list of plugin ids (versions are not respected).

When applying a plugin via `apply()` (assuming the standard resolution process, and that the plugin implementation is valid):

1. The IDs of all plugins that the given plugin depends on is obtained
1. Each depended on plugin is applied via `apply(«id»)` (transitively)

Therefore, it is assumed the implementation of the plugin dependencies are visible to the target scope. 

Declarative plugins:

1. Make their plugin dependencies discoverable at runtime via the agreed upon mechanism
1. Depend on the java libraries that implement each plugin dependency in their published metadata (e.g. POM)

Therefore, declarative plugins can be loaded via the `buildscript {}` dependency mechanism, albeit with access to different classes than if they would have been loaded through the `plugins {}` mechanism.

#### Plugin and build logic resolution process

This section outlines the process for resolving/applying build logic dependencies, encompassing `buildscript {}` and `plugins {}`.

> The process below does not consider “declarative” plugins exposing API, or depending on non core plugins. That fun is being deferred.

1. Execute all `buildscript {}` blocks (they are syntactically not allowed to appear after `plugins {}`), but do not resolve
1. Execute the `plugins {}` block, collecting each plugin spec, but do not resolve
1. Identify all “non-declarative” plugins specified by `plugins {}`, add their implementation java library (and dependencies) to `buildscript.configurations.classpath`
1. Resolve `buildscript.configurations.classpath`, loading the result into a classloader (known as “local buildscript classloader”)
1. Resolve the implementation of each “declarative” plugin specified by `plugins {}`, but don't apply (verify that everything is resolvable)
1. Apply each of the “non-declarative” plugins specified in `plugins {}`, from the “local buildscript classloader”
1. Make the “local buildscript classloader” available to the target script, but not to children
1. Create a filtered class loader from the “local buildscript classloader” that only exposes classes from JARs that were _only_ declared via `buildscript.dependencies {}` (i.e. not JARs that are present due to “non-declarative” plugins in `plugins {}`), known as the “exported buildscript classloader”, to make available for child build scripts to inherit
1. Load each declarative plugin into a separate classloader, based on the Gradle Core API + access to the core plugins it depends on, load plugin class
    1. If the “local buildscript classloader” contains the plugin implementation class, fail (assuming this plugin will be applied via `project.apply()`) - (cannot apply two instances of same plugin _class_)
1. Apply each “declarative” plugin in dependency order
1. Execute the build script

##### Open Issues

- What kind of compatibility checking do we do between the “local buildscript classloader” and the public API of “declarative” plugins?

## Plugin implementation and backwards/forwards compatibility

There are two parties involved in plugins; the author and the user. 
Moreover, the user of a plugin may be the author of another plugin. 
That is, plugins use other plugins.

There are three roles in plugin usage:

1. Build author consuming plugins
2. Plugin author producing a plugin
3. Plugin author consuming plugins as dependencies of their plugin

With the current mechanism, all three roles consume plugins in functionally the same manner. 
That is, declaring a dependency on the plugin implementation JAR and manually applying the depended upon plugin at runtime.
Currently all plugins are non-isolated and depended upon via normal dependency management.
The introduction of plugin isolation, declarative plugins, and a new mechanism for depending on plugins, introduces a new kind of plugin production and consumption.
Plugin users and authors will not be atomically migrating to the new mechanism as a whole, therefore a degree of compatibility between the two mechanism is needed.

We must support the use cases listed in this section, that deal with this transition and compatibility between the current mechanism and plugins and the new.

### Use cases

#### User uses new plugin dependency DSL to use non-declarative plugin

Dedicated story for this in milestone 1, and general approach outlined above in “plugin resolution and application”.
No further questions/considerations.

#### User uses buildscript dependency DSL (and apply()) to use declarative plugin

Dedicated story for this in milestone 2, and general approach outlined above in “plugin resolution and application”.
No further questions/considerations.

#### Non-declarative plugin author depends on declarative plugin

The plugin author will depend on the plugin implementation jar and apply with `project.apply()` as per normal in their build file.
No changes needed (besides already planned changes to make `project.apply()` implicitly apply depended upon plugins)

#### Declarative plugin author depends on non-declarative plugin

The build/development time support for declarative plugins is unplanned at this time.

However, it is assumed that:

1. Declarative plugin authors declare dependencies on plugins instead of the implementation java library of a plugin (as they do now)
2. The development tooling supports loading up the plugin in similar “class visibility managed” environment to what it would be subject to in real use

The mechanism by which authors declare plugin dependencies doesn't make a distinction between declarative/non-declarative (this is determined by the resolver for the plugin).
The development/testing tooling can support loading non-declarative plugins in the same manner that Gradle can at runtime.

# Milestone 1 - non-declarative plugins via `plugins {}`

This milestone enables plugin authors to start making their plugins (relatively as is) usable via `plugins {}`, and allows users to start using `plugins {}` (with some limitations)

## ~~Story: Plugin resolution is cached between builds~~

i.e. responses from plugins.gradle.org are cached to disk (`--offline` support)

* Caching is eternal, for “success” responses (i.e. plugin version is known). 
* --refresh-dependencies invalidates cache
* --offline should not error if response is cached

### Test Coverage

- ~~Subsequent build after build resolving plugin does not query plugin portal~~
- ~~--refresh-dependencies causes request to plugin portal even if resolution is already cached~~
- ~~--refresh-dependencies does not cause error when plugin is not cached~~
- ~~Not found response is not cached (plugin can be “found” in a subsequent build)~~
- ~~Error response is not cached~~
- ~~Unexpected response is not cached~~
- ~~`--offline` can be used if response is cached~~
- ~~`--offline` fails build if plugin is not cached~~
- ~~cached resolution by previous version is used~~

## Story: User sees visual indication of plugin resolution

This story covers improving the feedback when Gradle is dealing with buildscript {} and plugins {} blocks.

- Indication of entering “pre configure phase” (where we execute buildscript and plugins)
- Indication of progress for resolution of individual plugins to implementations (i.e. when communicating with resolution service)
- Indication of progress when resolving/obtaining plugin implementations
  - For declarative plugins, we can include the plugin details in the context (as each of these plugins has an individual resolve)

# Milestone 2 - more accurate classloading

## Story: Gradle core and API classloaders only load core plugins

See https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/groovy/org/gradle/api/internal/plugins/CorePluginRegistry.java#L39-39

## Story: Non-declarative plugins are isolated, and share everything to the local scope only

This story restricts the visibility of classes from non-declarative plugins in that they are not exported from their usage scope.
Moreover, such classes should not have visibility of other classes other than classes defined by the plugin's implementation.

### Test Coverage

- Plugin implementation classes are not visible to script plugins applied to target script
- Plugin implementation classes are not visible to build scripts of child projects
- Classes defined by parent scope of target are not visible to plugin

## Story: Avoid redundant resolution / downloading of plugin dependencies that are provided by the Gradle API

e.g. There's no point in downloading a Groovy implementation for a plugin as the Gradle API is going to impose one on the plugin.

This should work for:

- Classpath declared in a `buildscript` block.
- Implementation classpath for a declarative plugin.
- Implementation classpath for a non-declarative plugin.

## Story: Build user is warned when a plugin they are using has dependencies that conflict with Gradle API

If a plugin declares that it uses Groovy 10 but it's not going to get that, the user should be warned about this.

### Open Issues

- Should we just fail here? (we don't know that the conflict is fatal, things might still work)

## Story: Build user is warned when a plugin they are using has dependencies that conflict with other classes visible to the plugin

If a plugin declares that it uses libX@1.0 but it is forced to use libX@2.0, the user should be warned about this.

### Open Issues

- Should we just fail here? (we don't know that the conflict is fatal, things might still work)

# Milestone 3 - more flexible usage

## Story: Script plugins are able to use `plugins {}`

## Story: All plugins are resolved before any plugin is applied to a target

Before actually applying plugins (potentially expensive), all required plugins should be resolved in the spirit of fail fast.

## Story: User is notified of use of 'deprecated' plugin

## Story: User is informed of reason for requirement of “buildscript” dependency due to non declarative plugin

Given:

    plugins {
      id "foo.bar" version "1.0"
    }
    
Where the `foo.bar` plugin implementation module depends on 'some-library', and some-library is not available in jCenter and is not available in any of the `buildscript.repositories`,
The user is going to get a dependency resolution error claiming that 'some-library' could not be resolved.
The user has no way of knowing that 'some-library' is being resolved due to this plugin.
To diagnose this they would have to have knowledge of each plugin's dependencies.

### Test Coverage 

- Failed resolution of module implementation of non declarative plugin fails with error message indicating why resolve was happening

# Milestone 4 - declarative plugins

## Story: Full plugin resolution context is reused for duration of build

This story involves improving the plugin resolution process to cache/reuse more for the duration of the build:

1. Reuse plugin portal responses
1. Reuse dependency resolution details
1. Reuse implementation classpath
1. Reuse effective classloader network

The implementation may not require reuse at each of the above levels, as reuse at outer levels may effectively provide reuse at lower levels.
In large builds it is common for plugins to be used in many projects.
The goal is to only do the work to prepare a plugin for use (e.g. resolve its implementation) once.

## Story: Script plugins are able to use `plugins {}`

## Story: Plugin author uses plugin development plugin to build a plugin

This story adds a plugin development plugin to help plugin authors build a plugin. Later stories will add the ability to test and publish the plugin.

## Story: User uses declarative plugin via plugin dependencies DSL that depends on core Gradle plugin

## Story: User uses declarative plugin via buildscript dependencies DSL that depends on core Gradle plugin

## Story: Plugin author declares dependency on core Gradle plugin

This story adds a general mechanism for plugin authors to declare dependencies on other plugins.
This story only covers supporting core plugins, but consideration should be given to ensuring the mechanism is evolvable to declaring dependencies on non core plugins.

Required characteristics:

1. List of plugins depended on must be obtainable at runtime, given a plugin implementation _class_ (to support updating `project.apply()` to auto apply dependencies, for loading declarative plugins through `buildscript {}`)
1. Plugin resolution service must be able to obtain list of plugins depended on
1. Build author should not have to specify this information in more than one place

This likely requires build time functionality introduced by the plugin development plugin from the previous story.

## Story: Author of non-declarative plugin builds plugin that depends on declarative plugin

## Story: Plugin author uses plugin development plugin to publish a plugin

This story extends the plugin development plugin to generate the meta-data and publish a plugin.

## Story: Plugins are able to declare exported classes

This is the first story where we require changes to how plugins are published and/or implemented (i.e. exported class information is needed). 

The plugin development plugin should provide some mechanism to declare the exported classes of a plugin (possibly a DSL, possibly annotations in code, or something else).
This should end up in the generated meta-data.

Plugin authors should be able to write their plugin in such a way that it works with the new mechanism and the old project.apply() mechanism (as long as it has no dependency on any other, even core, plugin).

## Story: Declarative plugins are able to depend on other non core plugins

# Story: Author of declarative plugin builds plugin that depends on non-declarative plugin

# Story: Author of declarative plugin builds plugin that depends on non-core declarative plugin

# Milestone 5 - “parkable”

## Story: Gradle is routinely tested against real plugins.gradle.org codebase

This story covers setting up continuous testing of Gradle against the real plugin portal code, but not the real instance.

This provides some verification that the test double that is used in the Gradle build to simulate the plugin portal is representative, and that the inverse (i.e. plugin portal's assumptions about Gradle behaviour) holds.

This does not replace the double based tests in the Gradle codebase.

## Story: Plugin author reasonably tests realistic use of plugin with dependencies

Plugin authors need to be able to verify that their plugin works with the classloader structure it would have in a real build

## Story: Build author searches for plugins using Gradle command-line

Introduce a plugin and implicit task that allows a build author to search for plugins from the central plugin repository, using the Gradle command-line.

## Story: User specifies centrally that a plugin should be applied to multiple projects

- Inject from settings or init script
- Applied in the order defining scripts executed (ie init scripts in order, then settings)
- API of injected plugins are visible to build scripts?
- Classes of injected plugins are shared across projects

### Test cases

- Can inject same plugin multiple times
- Error reporting for unknown injected plugin

## Story: New plugin mechanism can be used to apply plugins from `buildSrc`

## Story: New plugin mechanism can be used to apply `Gradle` plugin

## Story: New plugin mechanism can be used to apply `Settings` plugin

## Story: Plugin declares minimum Gradle version requirement

## Story: User specifies non static plugin version constraint (i.e. dynamic plugin dependencies)

## Story: Local script is used to provide implementation of plugin

### Open questions

- Is it worth considering a testing mechanism for script plugins at this point?

# Future work

## Story: Pathological comms errors while resolving plugins produce reasonable error messages

1. Non responsive server (accepts request but never responds)
1. Server responds extremely slowly (data is transferred frequently enough to avoid idle/response timeout, but is really too slow to let continue)
1. Server responds with inaccurate content length (lots of HTTP clients get badly confused by this)
1. Server responds with extremely large document (protect against blowing out memory trying to read the response)

--- 

Stories below are still to be realigned after recent direction changes. There is some duplication with what is above, that needs to be folded in. In progress.

## Story: Plugins included in Gradle public repository are smoke tested

For plugins to be listed in the public repository, there must be some external (i.e. not performed by plugin author) verification that the plugin is not completely broken.
That is, the plugin should be:

1. Able to be applied via the new plugin mechanism
2. Not produce errors after simply applying

This will (at least) need to be able to be performed _before_ the plugin is included in the public repository. 

### Open issues

1. Are existing plugins periodically tested? Or only upon submission (for each new version)?
1. What action is taken if a plugin used to work but no longer does?

## Story: Resolve plugins relative to Gradle distribution

Plugin resolution uses Gradle runtime's URL (i.e as used by the wrapper) to locate a repository to search for plugins
and implementations.

TBD - introduce some plugins mapping artifact, or perhaps use an init script to bootstrap the mappings.

Plugin mappings are cached per repository.

Deprecate the 'custom distribution' feature some time after this.

## Story: Resolve plugins from enterprise repository

Allow the plugin repositories to use to be declared.

Multiple plugin and module repositories can be declared.

## Story: Resolve core plugins from public repository

- Publish core plugins to a public repository (possibly bintray)
- Produce a minimal Gradle distribution that does not include any plugins
- Change default wrapper configuration to download to this distribution
- Resolve a class import at script compilation time to a core plugin implementation on demand
- Introduce plugin resolution for old DSL.

Deprecate the bin Gradle distribution some time after this.

# More stories

These are yet to be mixed into the above plan:

- Credentials and caching for script plugin repositories.
- Build-init plugin custom build types by resolving build type name to plugin implementation using plugin repository
- Resolve tooling model to provider plugin implementation using plugin repository
- Plugin (script) declares public API
- Resolve script plugins from plugin repository
- Improve error reporting when `plugins { }` block is located inside a statement. Currently reports 'missing method', should give hint that plugins { } block can only be
  used in certain constrained ways.

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
