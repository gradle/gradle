# Gradle Module Metadata 1.0 specification

Consumption of Gradle Module Metadata is automatic. However publication needs to be enabled explicitly for any Gradle version prior to Gradle 6.

Publishing Gradle Module Metadata can be enabled in Gradle settings file (`settings.gradle`):

```
enableFeaturePreview("GRADLE_METADATA")
```

## Goal

This document describes version 1.0 of the Gradle module metadata file. A module metadata file describes the contents of a _module_, which is the unit of publication for a particular repository format, such as a module in a Maven repository. This is often called a "package" in many repository formats.

The module metadata file is a JSON file published alongside the existing repository specific metadata files, such as a Maven POM or Ivy descriptor. It adds additional metadata that can be used by Gradle versions and other tooling that understand the format. This allows the rich Gradle model to be mapped to and "tunnelled" through existing repository formats, while continuing to support existing Gradle versions and tooling that does not understand the format. 

The module metadata file is intended to be machine generated rather than written by a human, but is intended to be human readable.

The module metadata file is also intended to fully describe the binaries in the module where it is present so that it can replace the existing metadata files. This would allow a Gradle repository format to be added, for example.

In version 1.0, the module metadata file can describe only those modules that contain a single _component_, which is some piece of software such as a library or application. Support for more sophisticated mappings may be added by later versions.

## Usage in a Maven repository

When present in a Maven module, the file must have extension `module`. For example, in version 1.2 of 'mylib', the file should be called `mylib-1.2.module`.

Gradle ignores the contents of the Maven POM when the module metadata file is present.

## Contents

The file must be encoded using UTF-8.

The file must contain a JSON object with the following values:

- `formatVersion`: must be present and the first value of the JSON object. Its value must be `"1.0"`
- `component`: optional. Describes the identity of the component contained in the module.
- `createdBy`: optional. Describes the producer of this metadata file and the contents of the module.
- `variants`: optional. Describes the variants of the component packaged in the module, if any.

### `component` value

This value must contain an object with the following values:

- `group`: The group of this component. A string
- `module`: The module name of this component. A string
- `version`: The version of this component. A string
- `url`: optional. When present, indicates where the metadata for the component may be found. When missing, indicates that this metadata file defines the metadata for the whole component. 

### `createdBy` value

This value must contain an object with the following values:

- `gradle`: optional. Describes the Gradle instance that produced the contents of the module. 

### `gradle` value

This value, nested in `createdBy`, must contain an object with the following values:

- `version`: The version of Gradle. A string
- `buildId`: The buildId for the Gradle instance. A string

### `variants` value

This value must contain an array with zero or more elements. Each element must be an object with the following values:

- `name`: The name of the variant. A string. The name must be unique across all variants of the component.
- `attributes`: optional. When missing the variant is assumed to have no attributes.
- `available-at`: optional. Information about where the metadata and files of this variant are available.
- `dependencies`: optional. When missing the variant is assumed to have no dependencies. Must not be present when `available-at` is present.
- `files`: optional. When missing the variant is assumed to have no files. Must not be present when `available-at` is present.
- `capabilities`: optional. When missing the variant is assumed to declared no specific capability.

### `attributes` value

This value, nested in `variants` or elements of `dependencies` or `dependencyConstraints` nodes, must contain an object with a value for each attribute.
The attribute value must be a string or boolean.

### `capabilities` value

This value must contain an array of 0 or more capabilities. Each capability is an object consisting of the mandatory following values:

- `group`: The group of the capability. A string.
- `name`: The name of the capability. A string.
- `version`: The name of the capability. A string.

#### Standard attributes

- `org.gradle.usage` indicates the purpose of the variant. See the `org.gradle.api.attributes.Usage` class for more details. Value must be a string.
- `org.gradle.status` indicates the kind of release: one of `release` or `integration`.
- `org.gradle.category` indicates the type of component (library, platform or documentation). This attribute is mostly used to disambiguate Maven POM files derived either as a platform or a library. Value must be a string.
- `org.gradle.libraryelements` indicates the content of a `org.gradle.category=library` variant, like `jar`, `classes` or `headers-cplusplus`. Value must be a string.
- `org.gradle.docstype` indicates the documentation type of a `org.gradle.category=documentation` variant, like `javadoc`, `sources` or `doxygen`. Value must be a string.
- `org.gradle.dependency.bundling` indicates how dependencies of the variant are bundled. Either externally, embedded or shadowed. See the `org.gradle.api.attributes.Bundling` for more details. Value must be a string.

##### Deprecated attributes value

The `org.gradle.usage` attribute has seen an evolution for its values.
The values `java-api-*` and `java-runtime-*` are now deprecated and replaced by a new combination.

The values for the Java ecosystem are now limited to `java-api` / `java-runtime` combined with the relevant value for `org.gradle.libraryelements`.

Values for the native ecosystem remain unaffected.

Existing metadata must remain compatible and thus tools supporting the Gradle Module Metadata format must support both old and new values, while no longer publishing the deprecated ones.

#### Java Ecosystem specific attributes

- `org.gradle.jvm.version` indicated the minimal target JVM version of a library. For example is built for java 8, its minimal target is `8`. If it's a multi-release jar for Java 9, 10 and 11, it's minimal target is `9`. Value must be an integer corresponding to the Java version.

#### Native ecosystem specific attributes

- `org.gradle.native.debuggable` indicates native binaries that are debuggable. Value must be a boolean.

### `available-at` value

This value, nested in `variants`, must contain an object with the following values:

- `url`: The location of the metadata file that describes the variant. A string. In version 1.0, this must be a path relative to the module.
- `group`: The group of the module. A string
- `module`: The name of the module. A string
- `version`: The version of the module. A string

### `dependencies` value

This value, nested in `variants`, must contain an array with zero or more elements. Each element must be an object with the following values:

- `group`: The group of the dependency.
- `module`: The module of the dependency.
- `version`: optional. The version constraint of the dependency.
- `excludes`: optional. Defines the exclusions that apply to this dependency. 
- `reason`: optional. A explanation why the dependency is used. Can typically be used to explain why a specific version is requested.
- `attributes`: optional. If set, attributes will override the consumer attributes during dependency resolution for this specific dependency.
- `requestedCapabilities`: optional. If set, declares the capabilities that the dependency must provide in order to be selected. See `capabilities` above for the format.

#### `version` value

This value, nested in elements of the `dependencies` or `dependencyConstraints` nodes, defines the version constraint of a dependency or dependency constraint. Has the same meaning as `version` in the Gradle DSL. A version constraint consists of:
- `requires`: optional. The required version for this dependency.
- `prefers`: optional. The preferred version for this dependency.
- `strictly`: optional. A strictly enforced version requirement for this dependency.
- `rejects`: optional. An array of rejected versions for this dependency.

#### `excludes` value

This value, nested in elements of the `dependencies` node, must contain an array with zero or more elements. Each element has the same meaning as `exclude` in the Gradle DSL.

Each element must be an object with the of the following values:

- `group`: The group to exclude from transitive dependencies, or wildcard '*' if any group may be excluded.
- `module`: The module to exclude from transitive dependencies, or wildcard '*' if any module may be excluded.

An exclude that has a wildcard value for both `group` and `module` will exclude _all_ transitive dependencies.

### `dependencyConstraints` value

This value, nested in `variants`, must contain an array with zero or more elements. Each element must be an object with the following values:

- `group`: The group of the dependency constraint.
- `module`: The module of the dependency constraint.
- `version`: optional. The version constraint of the dependency constraint.
- `reason`: optional. A explanation why the constraint is used. Can typically be used to explain why a specific version is rejected, or from where a platform comes from.
- `attributes`: optional. If set, attributes will override the consumer attributes during dependency resolution for this specific dependency.

### `files` value

This value, nested in `variants`, must contain an array with zero or more elements. Each element must be an object with the following values:

- `name`: The name of the file. A string. This will be used to calculate the identity of the file in the cache, which means it must be unique across variants for different files.
- `url`: The location of the file. A string. In version 1.0, this must be a path relative to the module.
- `size`: The size of the file in bytes. A number.
- `sha1`: The SHA1 hash of the file content. A hex string.
- `md5`: The MD5 hash of the file content. A hex string.

### Changelog

#### 1.0

- Initial release

## Example

```
{
    "formatVersion": "1.0",
    "component": {
        "group": "my.group",
        "module": "mylib",
        "version": "1.2"
    },
    "createdBy": {
        "gradle": {
            "version": "4.3",
            "buildId": "abc123"
        }
    },
    "variants": [
        {
            "name": "api",
            "attributes": {
                "org.gradle.usage": "java-api",
                "org.gradle.category": "library",
                "org.gradle.libraryelements": "jar"
            },
            "files": [
                { 
                    "name": "mylib-api.jar", 
                    "url": "mylib-api-1.2.jar",
                    "size": "1453",
                    "sha1": "abc12345",
                    "md5": "abc12345"
                }
            ],
            "dependencies": [
                { 
                    "group": "some.group", 
                    "module": "other-lib", 
                    "version": { "requires": "3.4" },
                    "excludes": [
                        { "group": "*", "module": "excluded-lib" }
                    ],
                    "attributes": {
                       "buildType": "debug"
                    }
                }
            ]
        },
        {
            "name": "runtime",
            "attributes": {
                "org.gradle.usage": "java-runtime",
                "org.gradle.category": "library",
                "org.gradle.libraryelements": "jar"
            },
            "files": [
                { 
                    "name": "mylib.jar", 
                    "url": "mylib-1.2.jar",
                    "size": "4561",
                    "sha1": "abc12345",
                    "md5": "abc12345"
                }
            ],
            "dependencies": [
                { 
                    "group": "some.group", 
                    "module": "other-lib", 
                    "version": { "requires": "[3.0, 4.0)", "prefers": "3.4", "rejects": ["3.4.1"] } 
                }
            ],
            "dependencyConstraints": [
                { 
                    "group": "some.group", 
                    "module": "other-lib-2", 
                    "version": { "requires": "1.0" } 
                }
            ]
        }
    ]
}
```
