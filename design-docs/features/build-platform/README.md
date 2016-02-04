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

# Milestone 1

The first milestone introduces the concept of the build platform definition, the way it can be declared by the user and how it is resolved. It also lets the user specify
an init script as part of the build platform meta-data that is applied to the build automatically.

## Story - Gradle resolves and caches a build platform meta-data

Early in the build startup, resolve the build platform definition. This information is provided as a configuration block in `settings.gradle`.
The script block (or data extracted from it) and the build platform definition would be cached in the usual way. This story aim for exposing the DSL for defining the identifier,
version and the location of the build platform definition. Given this information, Gradle will resolve and cache the information. Out of scope for this stories is the evaluation
of the build platform definition.

### API

    package org.gradle.api.buildplatform;

    public interface BuildPlatformAware {
        // configures instance of BuildPlatform as delegate
        void buildSystem(Closure config);
    }

    package org.gradle.api.buildplatform.internal;

    public interface BuildPlatformIdentifier {
        String getId();
        String getVersion();
    }

    public interface BuildPlatform {
        // configures instance of RepositoryHandler as delegate
        void from(Closure config);

        // configures instances of BuildPlatformIdentifier as delegate
        void use(Map<String, String> coordinates);

        BuildPlatformIdentifier getIdentifier();
        List<ArtifactRepository> getRepositories();
    }

    package org.gradle.api.initialization;

    public interface Settings extends BuildPlatformAware {
        ...
    }

### Usage

    buildSystem {
        from {
            maven {
                url 'http://myinternalrepo.com/staging'
            }
        }

        use id: 'com.company.build.internal', version: '1.8'
    }

### Implementation

- Introduce a new DSL to the `Settings` class. The DSL can be used to provide the base information of build platform (`id` and `version`) as well as the target location hosting the
build platform meta-data.
- As target locations Maven and Ivy repositories can be specified. Other target location are out-of-scope for this story.
- During the initialization phase of a Gradle build the DSL evaluated. The underlying data structure is populated.
- Gradle uses the build platform definition to resolve the build platform artifact. The artifact is represented as a JAR file accompanied by the relevant Maven or Ivy meta-data.
- Gradle caches the resolved artifacts in its cache in the same way as any other artifact.
- The version used to resolve the build platform artifacts in the binary repository can be dynamic versioning scheme e.g. 1.+ or a changing version. The cache for build platform
definitions would behave based on the usual TTL definitions.
- The `buildSystem` requires the declaration of the `id` and `version` as well as at least one Ivy or Maven Repository.

### Test cases

- A user can declare a build platform definition in a `settings.gradle` file.
- The build platform definition is evaluated when parsing `settings.gradle`.
- A `settings.gradle` file can only contain _one_ build platform definition.
- A build platform definition needs to specify one mandatory repository.
    - An exception is thrown if no repository is provided.
    - An exception is thrown if more than one repository is declared.
- A build platform definition resolved the build platform artifacts
    - If the definition for the given coordinates cannot be found an exception is thrown.
    - Any communication issues lead to a thrown exception.
    - The build platform definition is downloaded only from the provided repository.
    - The download happens during the initialization phase of a Gradle build if the artifacts don't exist in the cache yet.
    - If a cached version of the build platform definition is found, no download is initiated. The build platform definition from the cache is reused.
    - For dynamic/changing versions TTL is adhered.
    - The resolved build platform artifacts are not further processed.

## Story - Gradle evaluates base information in build platform meta-data

Gradle needs to automatically evaluate and apply the resolved build platform definition. A build platform definition will only support a JSON format for now. The scope of this story
is to parse the JSON file, extract the relevant information and apply the definition to the build. For the scope of this story the information that is extracted is the base information
about the build platform: `id` and `version`.

### Build platform meta-data

    {
        "id": "com.company.build.internal",
        "version": "1.8"
    }

### Implementation

- During the initialization phase parse the build platform meta-data located in the cache.
- Read the build platform meta-data from the cached JAR file.
- Use a Java-based, light-weight JSON parsing library to read the file.
- The JSON file name search for is `build-platform.json`. No other name is allowed. The file is located in the directory `META-INF/gradle` of the JAR.
- Parse the values of attributes `id` and `version` and compare them with the attributes specified in the `Settings` file.

### Test cases

- The build platform meta-data can be read from the JAR file containing it.
    - Throw an exception if the file cannot be found.
    - The build platform meta-data is defined in the JSON format.
    - The file needs to be `build-platform.json`. Any other JSON files contained in the JAR file are ignored.
- Basic information in the JSON file can be parsed.
    - Throw an exception if the `build-platform.json` is not valid JSON.
- The parsed values for for `id` and `version` should match the attributes specified in the Settings file.
    - Throw an exception if they don't match.
    - Throw an exception if any of the attributes are not specified. Indicate the missing attribute.

## Story - Gradle evaluates init script in build platform meta-data

A build platform meta-data can declare an optional init script that should be applied automatically by the build. This story extends the JSON definition by an init script
attribute. Gradle evaluates this flag upon resolution and applies the provided init script.

### Build platform meta-data

    {
        ...,
        "init-script": "enterprise-rules.gradle"
    }

### Implementation

- Extend the JSON parsing code by logic to resolve the init script.
- The init script can have any name with the file extension `.gradle`. The file needs to be located in the root directory of the JAR.
- Only one init script can be defined in the build platform meta-data. Init scripts can optionally apply other script plugins bundled with the JAR.
- The init script cannot point to a HTTP location.
- The resolved init script is executed during the initialization phase of the Gradle build.

### Test cases

- If no init script is defined in the build platform meta-data, then nothing has to be done.
- The JSON parsing logic can read the init script attribute.
    - Throw an exception if the parsed init script value points to a non-existent file.
    - Throw an exception if a protocol e.g. `http://` is detected in the init script attribute value.
- Execute the init script during the initialization phase.
    - Propagate an exception thrown by the init script. The build fails.
    - The build platform init script takes precedence over other init scripts found under Gradle user home et al.
    - Other init scripts are executed as well.
    - The init script logic applies to the current Gradle build.

### Open issues

- Init scripts that are hosted outside of the build platform JAR file.

# Milestone 2

This milestone build on top of the existing build platform infrastructure. The user can declare compatible Gradle and Java runtime versions as part of the build platform meta-data
 that are checked automatically against the Gradle build applying the rules.

## Story -  Gradle evaluates Gradle compatibility in build platform meta-data

The build platform meta-data can specify the Gradle version compatible with any of the builds consuming the build platform definition. This story introduces a compatibility attribute
to the meta-data that verifies the compatibility with Gradle version executing the Gradle build.

### Build platform meta-data

    {
        ...,
        "compatibility": {
            "gradleVersion": "2.+"
        }
    }

### Implementation

- Extend the JSON parsing code by logic to resolve the Gradle version compatibility.
- The value of the compatible Gradle version can be a concrete version number e.g. `2.8` or a dynamic version e.g. `2.+`.
- Determine the version of the Gradle version executing the build.
- Compare the parsed Gradle version value with the Gradle runtime value.
- Compatibility checks need to happen before the executing the provided init script.
- If the Gradle version is not compatible fail the build with an appropriate error message.

### Test cases

- If no compatible Gradle version is defined in the build platform meta-data, then nothing has to be done.
- The JSON parsing logic can read the compatible Gradle version attribute.
    - Throw an exception if the version format is invalid.
- Gradle runtime version and Gradle compatible version can be compared.
    - If versions are compatible continue with the execution of the build.
    - Throw an exception with an appropriate message if versions are incompatible.

### Open issues

- Allowing compatible version ranges e.g. `>=2.5 =<2.8`

## Story -  Gradle evaluates Java compatibility in build platform meta-data

The build platform meta-data can specify the Java version compatible with any of the builds consuming the build platform definition. This story introduces a compatibility attribute
to the meta-data that verifies the compatibility with Java version executing the Gradle build.

### Build platform meta-data

    {
        ...,
        "compatibility": {
            "javaVersion": "1.7"
        }
    }

### Implementation

### Test cases

# Milestone 3

This milestone introduce a convenient way to produce the build platform meta-data. It also enables the end user to package and publish the build platform definition with the help
of a Gradle core plugin.

## Story - Introduce Gradle core plugin for producing and publishing a build platform definition

Before a build platform definition can be consumed by a build it needs to be published to a binary repository. This story introduces a new Gradle core plugin to allow a team to develop,
test and publish a build platform definition.

### Implementation

-

### Test cases

-

# Milestone 4

Further integrations into the Gradle ecosystem.

## Story - Build platform meta-data can be published to the Gradle plugin portal and consumed from there

Some organizations or Open Source project may decide to publish the build platform meta-data to a public binary repository. This story aims for extending the `build-system-dev` plugin
to publish to the Gradle plugin portal. The `buildSystem` definition of a platform will need to allow the consumption from the plugin portal.

### Usage

    buildSystem {
        from gradlePluginPortal()
        use id: 'com.company.build.internal', version: '1.8'
    }

### Implementation

-

### Test cases

-

