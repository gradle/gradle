This spec covers the creation and evolution of a Gradle plugin for use by Gradle plugin authors.

# Use cases

As we enrich the plugin mechanism, more development and release time support will be needed to make plugin development effective and enjoyable.
This support will be based on a Gradle core plugin that plugin authors can apply to their plugin projects.

## As a plugin author, I want to make my plugin discoverable via `plugins.gradle.org`

## As a plugin author, I want to develop and run automated tests for my plugin functionality

## As a plugin author, I want to manually test my plugin before releasing

Possibly by testing locally before publishing, or perhaps by staging remotely and then promoting after verification.

## As a plugin author, I want to provide documentation for my plugin in a standard format 

Where the format is “standard” for Gradle plugins (and may be specially understood by the plugin portal).

## As a plugin author, I want to publish my plugin to an internal company repository of plugins

# Implementation plan

## Story: Add a core `java-gradle-plugin` plugin 

This story adds the necessary bits to our build to include a new core plugin. 

1. Add a new `plugin-development` subproject
    1. Add to settings.gradle
    1. Add to `pluginProjects` in root `build.gradle`
    1. Add build script for new project
1. Add a plugin implementation
    1. add incubating `org.gradle.api.plugins.devel.JavaGradlePluginPlugin`
    1. implementation adds `java` plugin and compile dependency on `gradleApi()`.
    1. add META-INF plugin descriptor
    1. `jar` task warns if jar contains no plugin descriptors

### Test Coverage

1. Builds can apply `java-gradle-plugin` plugin (add a subclass of `WellBehavedPluginTest` to verify this).
1. Applying `java-gradle-plugin` causes project to be a `java` project
1. `gradle jar` produces usable plugin jar (assuming `src/main/java` contains valid plugin impl)
1. `gradle jar` issues warning if built jar does not contain any plugin descriptors

### Open questions

- Need to detangle the implementation language from the kind of thing being produced (a plugin). Should use the same scheme as the native and
new jvm plugins to do this.
- Should be able to implement a plugin using Groovy and no Java.

## Story: Plugin author declares plugin implementations in build

Currently plugin authors declare plugin implementations by way of the `META-INF/gradle-plugins/*.properties` files.
This story adds support for declaring the plugins as part of the build model, including the implementing class.

> The plugin declarations will also form the data to be used when publishing the plugin to the central plugin repository

### Test Coverage

1. Plugin IDs are validated (only contain valid chars, are qualified, are unique(?))
1. The plugin can be applied by id when included via a `buildscript { }` block.
1. The plugin can be applied by id when included via a `plugins { }` block (using the appropriate test fixtures, not `plugins.gradle.org`)

### Open Questions

- If we code generate the implementation mapping property files, how will we deal with the standard code generation problems when developing the plugin in the IDE?
    - e.g. the plugin tests may require this file to exist 
- Do we use the component model for this? i.e. model a plugin as a component (which is also a Java library component)
- Plugin should be usable from any build script when plugin implementation is in the `buildSrc` project.

## Story: Plugin author makes plugin available via central plugin service, by publishing plugin to bintray 

This functionality makes it easy for plugin authors to make their plugin available from the central plugin service (i.e. `plugins.gradle.org`).
 
At this time, in order for a plugin to be available via the central service…
 
1. The plugin implementation must be available as a Maven module in Bintray's Jcenter repository
1. A Bintray package must exist for the plugin
1. A Bintray package version must exist for each version of the plugin to be made available
1. Each bintray package version must contain a `gradle-plugin` attribute of the form: "«qualified plugin id»:«implementation module group»:«implementation module name»"
1. The Bintray package must be _linked_ into the canonical Gradle plugin Bintray repo
 
Additionally, the following should also happen:

1. The Bintray package for the plugin has a description, website and licence information
1. Each Bintray package version has a description

The central Gradle plugin repository extracts this data from Bintray, transforming it into its own model (which is similar to Bintray's).
As we are likely to eventually support more publishing destinations than Bintray, our tooling support should use our model and not Bintray's.

### Open Questions

- Should we use the publishing infrastructure for this? e.g. …

    publishing {
        publications {
          gradlePlugin(BintrayHostedGradlePlugin) {
            repo = …
            subject = …
            username = …
            accessKey = …
            // other bintray configuration
          }
        }
    }

- How much metadata should be set via the build?
    - Does it make sense to have the plugin description in the build?
        - If so, what happens on subsequent releases where the description has been updated on the portal?
        - Ignore for non-initial releases?
    - Same question for other metadata such as tags, website etc.
- Should the plugin perform any version number management (i.e. increment on releases, de -SNAPSHOT)?
- Should the ability to publish to maven local for local testing be included?
- What failures do we handle and how? eg failure applying the metadata after uploading the artifacts
- What validation do we apply?

## Story: Plugin development plugin is made public

- List the plugin in the standard plugins chapter in the user guide.
- Include the plugin DSL in the DSL guide.
- Update the 'writing plugins' chapter to include a description and sample of using the dev plugin.
- Include in the release notes.

# Later features

- Build init plugin generates a template Gradle plugin project.
- Plugin author writes and run functional tests for plugin.
- Plugin author generates and uploads documentation for plugin.
