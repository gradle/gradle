## ~~Story: Introduce plugins DSL block~~

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
- ~~Disallowed syntax/constructs cause compile errors, with correct line number of offending statement and suitable explanation of what is allowed (following list is not exhaustive)~~
  - ~~Cannot access `Script` api~~
  - ~~Cannot access script target API (e.g. `Gradle` for init scripts, `Settings` for settings script, `Project` for build)~~
  - ~~Cannot use if statement~~
  - ~~Cannot define local variable~~
  - ~~Cannot use GString values as string arguments to `id()` or `version()`~~
- ~~Plugin ids contain only valid characters~~
- ~~Plugin id cannot begin or end with '.'~~
- ~~Plugin id cannot be empty string~~
- ~~Plugin version cannot be empty string~~

## ~~Story: User uses declarative plugin “from” `plugins.gradle.org` of static version, with no plugin dependencies, with no exported classes~~

> This story doesn't strictly deal with the milestone goal, but is included in this milestone for historical reasons.
> Moreover, it's a simpler story than adding support for non-declarative plugins and adding plugin resolution service support  in one step.

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

- ~~404 responses that indicate that the plugin or plugin version do not exist are not fatal - try next resolver~~
- ~~generic 404 responses are considered fatal~~
- ~~If plugin portal response indicates that the plugin is known, but not by that version (also a 404), failure message to user should include this information (later stories might include information about what versions are known about)~~
- ~~Attempt to use -SNAPSHOT or a dynamic version selector produces helpful 'not supported' error message~~
- ~~Success response document of incompatible schema produces error~~
- ~~Success response document of compatible schema, but with extra data elements, is ok~~
- ~~Failed resolution of module implementation from specified repository fails, with error message indicating why resolve was happening~~
- ~~Successful resolution of module implementation, but no plugin with id found in resultant classpath, yields useful error message~~
- ~~Successful resolution of module implementation, but unexpected error encountered when loading `Plugin` implementation class, yields useful error message~~
- ~~Successful resolution of module implementation, but exception encountered when _applying_ plugin, yields useful error message~~
- ~~Plugin is available in build script via `PluginContainer`~~
    - ~~`withType()`~~
    - ~~`withId()`~~
- ~~Plugin implementation classes are not visible to build script (or to anything else)~~
- ~~Plugin cannot access classes from core Gradle plugins~~
- ~~Plugin can access classes from Gradle API~~
- ~~Plugin cannot access Gradle internal implementation classes~~
- ~~Plugin resolution fails when --offline is specified~~
- ~~Client follows redirect from server~~
- ~~Unicode characters in the response are interpreted correctly and don't cause strange behaviour~~
- ~~Plugin id and version numbers can contain URL meta chars and unicode chars (regardless of valid plugin ids not being allowed to contain non ascii alphanum or -) - request URLs should be well formed~~
- ~~Reasonable error message on network failure talking to plugin portal~~
- ~~Reasonable error message on network failure talking to repository containing plugin implementation~~

## Story: ~~Error message for unknown plugin or plugin version includes link to relevant human search interfaces~~

The “not found” responses from the portal include an arbitrary message.
This should be displayed to the user, as it can provide more information.

e.g. when a plugin is not found, the URL of the search interface can be displayed.
When a particular version is not found, the URL for the plugin can be displayed (which provides the available versions)

### Test Coverage

- ~~When a plugin is not found, the message provided by the resolution service is displayed~~
- ~~When a plugin version is not found, the message provided by the resolution service is displayed~~

## Story: User is notified that Gradle version is deprecated for use with plugin portal

The plugin portal may include a http header that indicates that the client has a “status”, by providing a checksum of this status.
The full status info can be retrieved by GETing the JSON document at /api/gradle/«gradle version» (this response should contain the same checksum header).
This JSON document may include a 'deprecationMessage' item which is a string.

When this endpoint is returning a deprecationMessage for the client, every build should display the deprecation message (only once, like other deprecation messages).
However, every build should not be required to make a HTTP call to the status endpoint.
Whenever a request is made to the service, the status checksum header should be used to determine whether the client's cached status is still current.
This does mean that the server's status may be different than the client's cached copy, if the client has not made a request to the service in some time.
To mitigate this, running with `--refresh-dependencies` will enforce that the status is checked.

The general approach when making a request to the service is:

- Does the response contain the status checksum header?
    - NO - do not print deprecation message
    - YES - Is there a cached status response?
        - NO - Fetch status (cache result)
        - YES - Does cached checksum match checksum from 1?
            - NO - fetch status document (cache result)
            - YES - use cached status

If there is a problem fetching the status (error response, or out of protocol response) the problem should be logged and operations continue as normal (i.e. non working status service does not fail the build).

### Test Coverage

- ~~Plugin query responses do not need to contain header - no message printed~~
- ~~response contains checksum, status service provides message, message printed~~
- ~~response contains checksum, status service provides message - multiple plugin requests in build, one message printed~~
- ~~response contains checksum, status service provides no message - no message printed~~
- ~~response contains checksum, status service returns error - no message printed~~
- ~~response contains checksum, status service returns out of protocol response - no message printed~~
- ~~status cached for checksum across builds~~
- ~~cached status is invalidated when running with --refresh-dependencies~~
- ~~can use --offline when status is cached - message printed~~
- ~~deprecation can be retracted - status is invalidated when previously non null checksum becomes null~~

## Story: Make new plugin resolution mechanism public

Story is predicated on plugins.gradle.org providing a searchable interface for plugins.

- Update http://www.gradle.org/plugins
  - Should have far less content, just a few short words that Gradle has a vibrant plugin mechanism / ecosystem and link to portal and user guide
  - less is more here
- Update the 'plugins' wiki page to direct build authors and plugin authors to `http://plugins.gradle.org` instead.
- Add link to further documentation in relevant error message (at least the compile time validation of plugin {} syntax)
- Include new DSL in DSL reference.
- Include types in the public API.
- Add links to user guide to `org.gradle.plugin.use` types Javadoc
- Add some material to the user guide discussion about using plugins.
- Update website to replace references to the 'plugins' wiki page to instead reference `http://plugins.gradle.org`
- Announce in the release notes.

Note: Plugin authors cannot really contribution to plugins.gradle.org at this point. The content will be “hand curated”.
