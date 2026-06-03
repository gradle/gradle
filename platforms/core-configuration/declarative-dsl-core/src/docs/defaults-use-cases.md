# Use cases for default configuration in Declarative

## Where to provide defaults

### Build-scoped defaults

Defaults scoped to one build, provided within that build. 

> This is the only supported scenario with current DCL 

For example, in `settings.gradle.dcl`:
```kotlin
pluginManagement { /* ... */ }

plugins {
    id("my.ecosystem").version("1.0")
}

defaults {
    myEcosystemProjectType { /* ... */ }
}
```

### Project-subtree-scoped defaults

With more flexibility in project structure declarations,
defaults could be provided within the structural units 
of the project structure and could affect the projects
under that unit, e.g.:

```text
settings.gradle.dcl // defaults for the whole build

client
-- defaults.gradle.dcl // defaults for the client subprojects
-- // some client-side subprojects

server
-- defaults.gradle.dcl // defaults for the server subprojects
-- // some server-side subprojects
```

A natural extension would be to support different ecosystems in the project subtrees, similar to how plugins work now.
This would solve the gradual plugin version migration problem in large builds. 

## Named defaults

This is another way to cover defaults that are only applied within a specific scope.
Instead of a global set of defaults, the user would create named sets of defaults and apply them by name
to the projects (or sets of projects).

```kotlin
defaults {
    namedDefaultsSet("server") {
        javaLibrary { /* ... */ }
        javaApplication { /* ... */ }
    }
    namedDefaultsSet("client") {
        javaApplication { /* ... */ }
        androidApplication { /* ... */ }
    }
}
```

```kotlin
// client/cli/build.gradle.dcl
usingDefaults(client)

javaApplication { // Uses the defaults from the client named defaults set
    /* ... */
}
```

### Distributable defaults

Sets of defaults could be written in one build and then distributed for consumption
in other builds. The declaring build would depend on the schemas of the plugins
that it provides the defaults for.

The declaring builds could be:
* Conventions, providing sets of defaults that are meaningful to reuse in an organization or in a set of scenarios. 
* Plugin builds, providing some meaningful defaults for the plugins they publish.

Potential problems: plugin versioning.

#### Special case: defaults attached to members

The definition owner could declare the following kinds of defaults:
* A default value for a property, at the property declaration; this will be the effective value if not overridden anywhere in the
    user build.
* For a container, a set of default elements in it that will be present even if not specified by the user
  * At the container declaration, it makes sense to provide only the identities for the default elements. If they need
    additional configuration, this is the distributable defaults use case (with defaults provided by the plugin owner).

### Gradle User Home defaults

Defaults could be provided for all builds on a local machine.
This could be: authentication details, machine-specific paths.

Potential problems: plugin versioning; might need more than one set of defaults.

# How to apply defaults

## Defaults for a project type

These are mixed into the configuration whenever the project type is used, unconditionally.

```kotlin
defaults {
    javaLibrary { /* ... */ }
    kotlinApplication { /* ... */ }
}
```

## Defaults for container elements

> Note: this might appear anywhere else in defaults where a container is used

A container with elements created by the user needs the defaults for an element to be applied to that element when it is used
in the project configuration:

```kotlin
defaults {
    javaLibrary {
        testSuites {
            testSuite("integTest") { /* ... */ } // creates the element and configures it with the block
            testSuite("smokeTest") { /* ... */ }
        }
    }   
}
```

## Defaults for container elements with no identity

When a container element has no identity key, all items from the defaults are present in the result but cannot be additionally configured. This is the case with dependencies:

```kotlin
defaults {
    javaLibrary {
        dependencies {
            implementation("org.example:lib:1.0") // this element has no identity key and cannot be addressed in the build file
            implementation("org.example:other-lib:1.0")
        }
    }
}

// build.gradle.dcl
javaLibrary {
    dependencies { // includes everything from the defaults and adds more:
        implementation("org.example:extra-lib:1.0")
    }
}
```

## Defaults for an ad-hoc project type

A special case is defaults for a "lightweight project type" (a new project type produced
from an existing one).

```kotlin
localProjectTypes {
    javaLibrary("serverSideJavaLibrary")
}

defaults {
    serverSideJavaLibrary { /* ... */ }
}
```

## Defaults for a set of project types, based on the definition supertype

Assume there are several project types that share a definition supertype:

```kotlin
interface MyLanguageDefinition : Definition
interface MyLanguageLibraryDefinition : MyLanguageDefinition
interface MyLanguageApplicationDefinition : MyLanguageDefinition 
```

It makes sense to share some defaults for the supertype `MyLanguageDefinition`. However, it is lacking a DSL name.
One option would be to provide a DSL name for it, like:

```kotlin
@DefaultsDslName("myLanguage")
interface MyLanguageDefinition : Definition
```

Then the usage could look like:

```kotlin
defaults {
    myLanguage { /* ... */ } // applied to both project types
    myLibrary { /* ... */ }
    myApplication { /* ... */ }
}
```

## Non-forcing / conditional / optional / nested defaults

In the cases above, the whole content of a project-type defaults block is applied to the project using that project type.

However, it makes sense to declare some of the defaults as "non-forcing", meaning that some parts of the defaults is only applied
to the project when the corresponding features (or nested blocks) appear in the project configuration:

```
defaults {
    kotlinLibrary {
        javaVersion = 17
        detekt { // Forces detekt to be applied to all `kotlinLibrary` projects  
            /* ... */
        }
        defaults { // the contents of this block are non-forcing and are only applied if the blocks appear in the project configuration:
            compose { /* ... */ }
            kotlinxSerialization { /* ... */ }
        }
        testSuites { // this is a container with user-defined elements
            testSuite("integTest") { /* ... */ } // forces the presence of integTest
            defaults {
                testSuite("smokeTest") { /* ... */ } // does not force the presence of smokeTest but applies if it is present 
            }
        }
    }
}
```

> Note: the choice of `defaults` for the nested blocks proved to be consistent with what the top-level `defaults` means; however, a different
> name might be a better option, like `ifPresent/ILeLe`, `optionally`, `optionalDefaults` etc.

> Note: not everything that can appear in a project type's block would work in a nested `defaults` block. For instance, putting a regular property
> inside `defaults { }` makes no sense, but using a feature block like `compose` is valid. There is semantical segregation of what makes sense there.

> Note: some feedback from JetBrains indicates that multi-level nesting of `defaults { }` is at the edge of easily understandable configuration
> structure: simpler cases of it are intuitive, more complex ones are not.

## Defaults for all container elements

A container with user-defined elements might have defaults for all elements

```kotlin
defaults {
    javaLibrary {
        sourceSets {
            sourceSet("integTest") { /* ... */ } // This is still a forcing default: the item is created
            
            defaults { // syntax is provisional and based on non-forcing defaults above:
                sourceSet { // instead of `sourceSet("name")`, we're using just sourceSet
                    
                }
                sourceSet("main") { /* ... */ } // could live side-by-side with non-forcing defaults for specific items
            }
        }
    }
}
```

## Defaults for project features

It makes sense to provide defaults for a project feature, meaning that those defaults are applied wherever the feature is used.

```kotlin
featureDefaults {
    detekt { 
        reports {
            html = true
        }
    }
}
```

> Note: it was agreed upon that using a separate `featureDefaults` block and not putting feature defaults in `defaults` is beneficial for comprehensibility.

Potential problems: identifying a feature; features sharing a name but having different definitions.

## Defaults for a family of project features sharing a definition type

This is similar to defaults for a family of project types with a shared definition supertype, but for features.

# Special cases

## Defaults for collections

A collection-typed property can have a default value with some elements in it.
The build file can then either append to that value, adding new elements, or assign a new value to the property,
overriding the elements.

```kotlin
defaults {
    kotlinLibrary {
        compilerOptions {
            freeCompilerArgs = ["-Xprogressive=true"]
        }
    }
}

// foo/build.gradle.dcl
kotlinLibrary {
    compilerOptions {
        freeCompilerArgs = ["-Werror"] // drops the default
    }
}

// bar/build.gradle.dcl
kotlinLibrary {
    compilerOptions {
        freeCompilerArgs += ["-Werror"] // keeps the default and adds more
    }
}
```

For the map types, overriding a value is done in a key-aware way: matching keys
override the values.

```
defaults {
    myProjectType {
        myMap = [
            "foo" to "bar",
            "bar" to "baz"
        ]
    }
}

// build.gradle.dcl
myProjectType {
    myMap += [
        "bar" to "qux" // replaces `"bar" to "baz"`
    ]
}
```

## Defaults using project-specific services

It sometimes makes sense to provide a set of defaults that means different things in different projects. 
For instance, project-specific paths could be used in defaults but get evaluated to different real paths in each project:

```
defaults {
    androidLibrary {
        proguard {
            proguardFile = layout.projectDir.file("src/proguard-rules.pro") // relative to the concrete project's directory
        }
    }
}
```

# Use cases that have no support in DCL design

## Removing defaults

It is not possible to remove defaults in DCL. If a default is set, it cannot be undone by a build file.
"Undeclaring" something is a counter-intuitive operation that would surprise readers who see a default
and then observe a project that ignores it.

For properties, the default value can be overridden, but other than that, defaults stay:
* An applied feature cannot be "unapplied".
* A container element cannot be "undeclared".

Instead, the DCL design covers such cases by providing a way to scope defaults or to introduce
ad-hoc project types and provide different defaults to them compared to the original project types.

This has another implication: a value type (a type that is assignable to properties) must not be a target
to features (or, a weaker restriction: values of such types used as property values cannot be targets to features).
Otherwise, overriding property values would drop applied features. This justifies having both properties and 
nested models/containers coexisting in definitions.

These restrictions allow the users who have the intuition of imperative build configuration to use it with DCL
that is driven by static data and documents until the very end where it gets projected onto the JVM world. 
Namely, if you think of a feature application as an operation that has side effects on the build model, you would not
expect the defaults-system to undo such operations when it combines defaults with the actual configuration.

# Tooling use cases

What should the DCL tooling be able to do with defaults?

## Getting effective definitions

The tooling is able to compose all defaults that are applicable to the definition into one effective definition.
This is done at the data level without lowering it to JVM or any other non-Declarative representation.

### Showing effective definition

The tooling shows the effective definition in a GUI or by adding more information to the editor (like inlay hints).
For the defaults in the effective definition, there might be markers telling where it came from 
(settings file, project-subtree, plugin-provided defaults, etc.). If there are multiple overrides of a single
value, it should be possible to see all of them (maybe in a hint when you focus on that value).

### Navigation related to defaults

From a definition (either in a project or in some defaults), the tooling navigates:
* to every default definition that precedes the current definition and provides data 
  * or can provide data but doesn't: "Where can I put defaults for this?"
* from defaults, to every usage that provides data: "Where is this overridden?"
  * or can provide data but doesn't: "Where is this default effective?"

### Defaults-aware code completion

In a project build definition, if the defaults make sure that the effective definition already 
has a nested object (an applied feature, or a container element), the code completion should be aware of that
and should provide a hint that it is already present. For container elements, code completion should suggest
the present elements as separate items (like `sourceSet("main") { }` in addition to `sourceSet` with a new name)

## Inspections and refactoring

### Unused defaults

A project type or a feature is not used anywhere in the build, so the defaults are useless and can be removed.

#### Unused optional defaults

With the optional/non-forcing defaults, if they do not become effective anywhere because the corresponding nested definitions
do not appear in project definitions, the tooling should be able to tell so and suggest removing them.

### Useless defaults (project-wide)

If a default is overridden in all effective definitions, the tooling should be able to detect it and suggest removing the default.

### Part of definition can be a default (project-wide)

If a part of the definition appears uniformly in a scope (all projects, or a project-subtree that can provide defaults),
the tooling should be able to detect it and suggest extracting the part into the corresponding defaults.

### Useless data in a definition

If a definition duplicates the defaults, the tooling should be able to detect it and suggest removing the duplicate.

### Extract to defaults

The tooling should be able to extract a part of a definition into the defaults (letting the user choose the scope: defaults
for the project type or feature or named defaults or local project type; global or local to subproject-tree). 

Since that can affect other projects, the tooling 
should show the predicted effect: which projects get new data in their definitions (and maybe the details on the data).

### Push defaults downstream

If the user does not want a default anymore and wants every project (or a narrower scope) to provide a value, they can push the defaults
closer to the effective definitions.

The tooling should be able to move a set of defaults to the project definitions (or to more specific defaults, like in subprojects).
This should be done in a way that effective definitions stay the same. The tooling should let the user preview the effect.
If the effective definition has some of the defaults overridden, the tooling should only put the rest.

