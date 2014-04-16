
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

1. may contain any alpha numeric ascii character and -
1. must contain at least one '.' character, that separates the namespace from the name
1. consist of a namespace (everything before the last '.') and a name (everything after the last '.')
1. conventionally use a lowercase reverse domain name convention for the namespace component
1. conventionally use only lowercase characters for the name component
1. 'org.gradle' and 'com.gradleware' namespaces are reserved (users are actively prevented from using these namespaces)

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

## Plugin implementation backwards compatibility

TBD.

# Milestone 1 - “usable”

## Story: Introduce plugins DSL block

Adds the `plugins {}` DSL to build scripts (settings, init or arbitrary script not supported at this point). Plugin specs can be specified in the DSL, but they don't do anything yet.

### Implementation

1. Add a `PluginSpecDsl` service to all script service registries (i.e. “delegate” of `plugins {}`)
1. Add a compile transform that rewrites `plugins {}` to be `ConfigureUtil.configure(services.get(PluginSpecDsl), {})` or similar - we don't want to add a `plugins {}` method to any API
    - This should probably be added to the existing transform that extracts `buildscript {}`
1. Add an `id(String)` method to `PluginSpecDsl` that returns `PluginSpec`, that has a `version(String)` method that returns `PluginSpecDsl` (self)
1. Update the `plugin {}` transform to disallow everything except calling `id(String)` and optionally `version(String)` on the result
1. Update the transform to error if encountering any statement other than a `buildscript {}` statement before a `plugins {}` statement
1. Update the transform to error if encountering a `plugins {}` top level statement in a script plugin
1. `PluginSpecDsl` should validate plugin ids (see format specification above)

### Test cases

- ~~`plugins {}` block is available to build scripts~~
- ~~`plugins {}` block in init, settings and arbitrary scripts yields suitable 'not supported' method~~
- ~~Statement other than `buildscript {}` before `plugins {}` statement causes compile error, with correct line number of offending statement~~
- ~~`buildscript {}` is allowed before `plugins {}` statement~~
- ~~multiple `plugins {}` blocks in a single script causes compile error, with correct line number of first offending plugin statement~~
- ~~`buildscript {}` after `plugins {}` statement causes compile error, with correct line number of offending buildscript statement~~
- Disallowed syntax/constructs cause compile errors, with correct line number of offending statement and suitable explanation of what is allowed (following list is not exhaustive)
  - Cannot access `Script` api
  - Cannot access script target API (e.g. `Gradle` for init scripts, `Settings` for settings script, `Project` for build)
  - Cannot use if statement
  - Cannot define local variable
  - Cannot use GString values as string arguments to `id()` or `version()`
  
## Story: Can use plugins {} in build script to use core plugin

This story makes it possible for the user to use the new application mechanism to apply core plugins.
At this point, there's no real advantage to the user or us in this, other than fleshing out the mechanics

1. Add an internal service that advertises the core plugins of Gradle runtime (at this stage, all plugins shipped with the distribution)
1. Change the implementation/use of `PluginSpecDsl` to make the specified plugins available
1. After the execution of the plugins {} block, but before the “body” of the script, iterate through the specified plugins
1. For each plugin specified, resolve the specification against the plugin resolvers - only the core plugin resolver at this stage
1. If the plugin spec can't be satisfied (i.e. has a version constraint, or is not the _name_ of a core plugin), the build should fail indicating that the plugin spec could not be satisfied by the available resolvers (future stories will address providing more information to users, e.g. a list of available core plugins)

At this stage, applying a core plugin with this mechanism effectively has the same semantics as having `apply plugin: "«name»"` as the first line of the build script.

Note: plugins from buildSrc are not core plugins.

### Test cases

- ~~`plugins { id "java" }` applies the java plugin to the project when used in a _build_ script (equally for any core plugin)~~
- ~~`plugins { id "java" version "«anything»" }` produces error stating that core plugins cannot have version constraints~~
- `plugins { id "java"; id "java" }` produces error stating that the same plugin was specified twice
- `plugins { id "org.gradle.java" }` is equivalent to `plugins { id "java"}`
- `plugins { id "«non core plugin»" }` produces suitable 'not found' type error message
- Using project.apply() to apply a plugin that was already applied using the plugins {} mechanism works (i.e. has no effect)
- Plugins are applied alphabetically based on name

### Open questions 

- Should a qualified plugin id of a namespace other than 'org.gradle', with no version constraint, yield a special error message? i.e. only 'org.gradle' plugins can omit version

## Story: User uses plugin “from” `plugins.gradle.org` of static version, with no plugin dependencies, with no exported classes 

This story covers adding a plugin “resolver” that uses the plugins.gradle.org service to resolve a plugin spec into an implementation.

Dynamic versions are not supported.
Plugins obtained via this method must have no dependencies on any other plugin, including core plugins, and do not make any of their implementation classes available to the client project/scripts (i.e. no classes from the plugin can be used outside the plugin implementation).
No resolution caching is performed; if multiple projects attempt to use the same plugin it will be resolved each time and a separate classloader built from the implementation (address in later stories).

A new plugin resolver will be implemented that queries the plugin portal, talking JSON over HTTP.
See the plugin portal spec for details of the protocol.
This resolver will be appended to the list of resolvers used (i.e. currently only containing the core plugin resolver).

Plugin specs can be translated into metadata documents using urls such as: `plugins.gradle.org/api/gradle/«gradle version»/plugin/use/«plugin id»/«version»`.

There are 4 kinds of responses that need to be considered for this story:

1. 3xx redirect
1. 200 response with expected JSON payload (see plugin portal spec)
1. 404 response with JSON payload indicating no plugin for that id/version found (see plugin portal spec)
1. Anything else

Subsequent stories refine the error handling. This story encompasses the bare minimum.

The “plugin found” JSON response contains two vital datum, among other data.

1. A “«group»:«artifact»:«version»” dependency notation string
1. A URL to an m2 repo that is accessible without authentication

The m2 repository is known to contain the dependency denoted in the dependency notation string.
The runtime usage resolution (i.e. module artifact + dependencies) of the dependency from the given repository is expected to form a classpath that contains a plugin implementation mapped to the qualified id (i.e. a `/META-INF/gradle-plugins/«qualified id».properties` file with `implementation-class` property).

The dependencies of the plugin implementation must also be available from the specified maven repository.
That is, this is the only repository available for the resolve.

The plugin resolver will resolve the maven module as per typical Gradle maven dependency resolution.
No configuration (e.g. username/password, exclude rules) of the resolve is possible.
Anything other than successful resolution of the implementation module is fatal to the plugin resolution.

The successfully resolved module forms an implementation classpath.
A new classloader is created from this classpath, with the gradle api classloader (_not_ the plugin classloader) as its parent.
The `Plugin` implementation mapped to the plugin id from this classpath is applied to the project.
No classes from the plugin implementation classpath are made available to scripts, other plugins etc. 

As much of the HTTP infrastructure used in dependency resolution as possible should be used in communicating with the plugin portal.

### Test Coverage

- 404 responses that indicate that the plugin or plugin version do not exist are not fatal - try next resolver
- generic 404 responses are considered fatal
- If plugin portal response indicates that the plugin is known, but not by that version (also a 404), failure message to user should include this information (later stories might include information about what versions are known about)
- Attempt to use -SNAPSHOT or a dynamic version selector produces helpful 'not supported' error message
    - As there is only the core resolver and the portal resolver at this point, this logic could be hardcoded at the start of the resolver list potentially
- Success response document of incompatible schema produces error
- Success response document of compatible schema, but with extra data elements, is ok
- Failed resolution of module implementation from specified repository fails, with error message indicating why resolve was happening
- Successful resolution of module implementation, but no plugin with id found in resultant classpath, yields useful error message
- Successful resolution of module implementation, but unexpected error encountered when loading `Plugin` implementation class, yields useful error message
- Successful resolution of module implementation, but exception encountered when _applying_ plugin, yields useful error message
- Plugin is available in build script via `PluginContainer` - incl. `withType()` and `withId()` methods (note: plugin class is not visible to build script, but could be obtained reflectively)
- Plugin implementation classes are not visible to build script (or to anything else)
- Plugin cannot access classes from core Gradle plugins
- Plugin can access classes from Gradle API
- Plugin resolution fails when --offline is specified
- Client follows redirect from server
- Unicode characters in the response are interpreted correctly and don't cause strange behaviour
- Plugin id and version numbers can contain URL meta chars and unicode chars (regardless of valid plugin ids not being allowed to contain non ascii alphanum or -) - request URLs should be well formed
- Reasonable error message on network failure talking to plugin portal or repository containing plugin implementation

### Open questions

- Is it worth validating the id/version returned by the service against what we asked for?

## Story: Structured error response from plugin portal (when resolving plugin spec) is “forwarded to user”

The plugin portal has a standardised JSON payload for errors.
This story adds understanding of this to Gradle's interactions with the portal, by way of extracting the error information and presenting it to the user instead of a generic failure message.

Any request to the plugin portal may return a “structured error response”.
In some cases this may be part of the standard protocol for that endpoint.
For example, a request for plugin metadata that targets the plugin metadata endpoint but resolves to a non existent plugin will yield a structured error response.
The detail of the error response differentiates the response from a generic 404.

### Test coverage

- 4xx..500 response that isn't a structured error response (e.g. HTML) is handled
- Response advertised as structured error response is of incompatible schema
- Response advertised as structured error response is malformed JSON
- Response advertised as structured error response is of compatible schema, but has extra unexpected elements

### Open questions

- What to use to unmarshall JSON responses? Jackson? Should the API couple to a marshaller at this level?

## Story: User is notified that Gradle version is no longer supported by plugin portal

## Story: User is notified of use of 'deprecated' plugin 

## Story: Gradle is routinely tested against real plugins.gradle.org codebase

This story covers setting up continuous testing of Gradle against the real plugin portal code, but not the real instance.

This provides some verification that the test double that is used in the Gradle build to simulate the plugin portal is representative, and that the inverse (i.e. plugin portal's assumptions about Gradle behaviour) holds.

This does not replace the double based tests in the Gradle codebase.

## Story: Plugins are able to declare exported classes

This is the first story where we require changes to how plugins are published and/or implemented (i.e. exported class information is needed). 

Plugin authors should be able to write their plugin in such a way that it works with the new mechanism and the old project.apply() mechanism (as long as it has no dependency on any other, even core, plugin).

## Story: Plugins are able to declare dependency on core plugin

Plugin authors should be able to write their plugin in such a way that it works with the new mechanism and the old project.apply() mechanism (as long as it has no dependency a non core plugin).

# Milestone 2 - “announceable”

## Story: Plugins are able to depend on other non core plugins

Plugin dependencies can not be dynamic.
Plugin dependencies can not be cyclic.

## Story: Plugin resolution is cached across the entire build

Don't make the same request to plugins.gradle.org in a single build, reuse implementation classloaders.

## Story: Plugin resolution is cached between builds

i.e. responses from plugins.gradle.org are cached to disk (`--offline` support)

## Story: Build author searches for plugins using central Web UI

## Story: Make new plugin resolution mechanism public

Story is predicated on plugins.gradle.org providing a searchable interface for plugins.

- Include new DSL in DSL reference.
- Include types in the public API.
- Add some material to the user guide discussion about using plugins.
- Update website to replace references to the 'plugins' wiki page to instead reference `http://plugins.gradle.org`
- Update the 'plugins' wiki page to direct build authors and plugin authors to `http://plugins.gradle.org` instead.
- Announce in the release notes.

Note: Plugin authors cannot really contribution to plugins.gradle.org at this point. The content will be “hand curated”.

## Story: Plugin author reasonably tests realistic use of plugin with dependencies

Plugin authors need to be able to verify that their plugin works with the classloader structure it would have in a real build

## Story: Plugin author submits plugin for inclusion in plugins.gradle.org

Includes:

- Includes generating all necessary metadata (e.g. exported classes, plugin dependencies)
- Tooling support for publishing in manner suitable for inclusion in plugins.gradle.org
- Admin processes for including plugin, including acceptance policies etc.
- Prevention of use of 'org.gradle' and 'com.gradleware' namespaces

# Milestone 3 - “parkable”

## Story: Pathological comms errors while resolving plugins produce reasonable error messages

1. Non responsive server (accepts request but never responds)
1. Server responds extremely slowly (data is transferred frequently enough to avoid idle/response timeout, but is really too slow to let continue)
1. Server responds with inaccurate content length (lots of HTTP clients get badly confused by this)
1. Server responds with extremely large document (protect against blowing out memory trying to read the response)

## Story: User specifies centrally that a plugin should be applied to multiple projects

## Story: New plugin mechanism can be used to apply `Gradle` plugin

## Story: New plugin mechanism can be used to apply `Settings` plugin

## Story: Plugin declares minimum Gradle version requirement

## Story: Local script is used to provide implementation of plugin

### Open questions

- Is it worth considering a testing mechanism for script plugins at this point?

# Future work

## Story: Build author searches for plugins using Gradle command-line

Introduce a plugin and implicit task that allows a build author to search for plugins from the central plugin repository, using the Gradle command-line.

## Story: User specifies non static plugin version constraint (i.e. dynamic plugin dependencies)

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

- Credentials and caching for script plugin repositories.
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
