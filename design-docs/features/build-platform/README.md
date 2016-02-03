# Build engineering team shares build logic with development team

## Use case

The build engineering team provides build infrastructure and build logic to development teams, both inside and outside the organisation. The development teams use
this to automate various development jobs.

The build logic includes open source and closed source plugins, and configuration data. This configuration data includes recommended and supported plugin versions,
Gradle API versions, and Java runtime versions. It also includes details of where to find (most of) these things.

## "Build platform" concept

A "build platform" is a definition of the environment in which build logic executes.

A build engineering team would publish versioned build platform definitions to a repository. Development teams would reference a particular build platform definition from their build.
Given this, the Gradle runtime would use this information to locate, provision and validate the appropriate runtime components and plugins.

## Defining and publishing a build platform

A build platform definition is simply another piece of build logic, and will be published the same way as other build logic such as plugins.

Initially, a build platform definition would include:

- Some id and version information
- Supported Gradle API versions.
- Supported Java API versions.
- An optional bootstrap init script to apply to the build. This init script can use the Gradle APIs to inject configuration and build logic.

A build platform definition is treated as a requirement, or dependency, of a build, and as such would be referenced in the same ways as other dependencies, such as Gradle
plugins or Java libraries.

## Stories

### Story - Gradle resolves and caches a build platform meta-data

Early in the build startup, resolve the build platform definition. This information is provided as a configuration block in `settings.gradle`.
The script block (or data extracted from it) and the build platform definition would be cached in the usual way. This story aim for exposing the DSL for defining the identifier,
version and the location of the build platform definition. Given this information, Gradle will resolve and cache the information. Out of scope for this stories is the evaluation
of the build platform definition.

##### API

    package org.gradle.api.buildplatform;

    public interface BuildPlatformAware {
        // configures BuildPlatform as delegate
        void buildSystem(Closure config);
    }

    package org.gradle.api.buildplatform.internal;

    public interface BuildPlatform {
        String getId();
        String getVersion();
        List<ArtifactRepository> getRepositories();
    }

    package org.gradle.api.initialization;

    public interface Settings extends BuildPlatformAware {
        ...
    }

##### Usage

    buildSystem {
        from {
            maven {
                url 's3:some-bucket'
            }
        }

        use id 'com.company.build.internal' version '1.8'
    }

##### Implementation

-

##### Test cases

-

### Story - Gradle evaluates build platform meta-data and checks for Gradle and Java runtime compatibility

Gradle needs to automatically evaluate and apply the resolved build platform definition. A build platform definition will only support a JSON format for now. The scope of this story
is to parse the JSON file, extract the relevant information and apply the definition to the build. For the scope of this story the only rules that can be provided are compatible
 Gradle version and Java version.

##### Example JSON build platform definition

    {
        "id": "com.company.build.internal",
        "version": "1.8",
        "compatibility": {
            "gradleVersion": "2.+",
            "javaVersion": "1.7"
        }
    }

##### Implementation

-

##### Test cases

-

### Story - Gradle evaluates init script in build platform meta-data

A build platform meta-data can declare an optional init script that should be applied automatically by the build. This story extends the JSON definition by an optional init script
attribute. Gradle evaluates this flag upon resolution and applies the provided init script.

##### Example JSON build platform definition

    {
        ...,
        "init-script": "enterprise-rules.gradle"
    }

##### Implementation

-

##### Test cases

-

### Story - Introduce Gradle core plugin for producing and publishing a build platform definition

Before a build platform definition can be consumed by a build it needs to be published to a binary repository. This story introduces a new Gradle core plugin to allow a team to develop,
test and publish a build platform definition.

##### Implementation

-

##### Test cases

-

### Story - Build platform meta-data can be published to the Gradle plugin portal and consumed from there

Some organizations or Open Source project may decide to publish the build platform meta-data to a public binary repository. This story aims for extending the `build-system-dev` plugin
to publish to the Gradle plugin portal. The `buildSystem` definition of a platform will need to allow the consumption from the plugin portal.

##### Usage

    buildSystem {
        from gradlePluginPortal()
        use id 'com.company.build.internal' version '1.8'
    }

##### Implementation

-

##### Test cases

-

