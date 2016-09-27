# Motivation

Eclipse offers a general-purpose API for configuring preferences on a per-project level. 
These preference files are simple property files containing key-value pairs. They are for instance
used to configure JDT's compiler settings like this:

```
org.eclipse.jdt.core.compiler.source=1.6
org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.6
org.eclipse.jdt.core.compiler.compliance=1.6
```

Gradle should support a generic API to define such preferences, so they can be shared as part of the build
or even configured in a plugin. They should then also be made available via the Tooling API. This would greatly
expand the number of tools that Buildship supports out of the box without any tool-specific changes in Buildship.

# Proposed Change

Add a preferences DSL to the `eclipse` plugin:

```
eclipse {
    preferences {
        jdtCore {
            node = 'org.eclipse.jdt.core'
            preference 'org.eclipse.jdt.core.compiler.source', '1.6'
            preference 'org.eclipse.jdt.core.compiler.codegen.targetPlatform', '1.6'
        }
    }
}
```

Create one task for each preference node (e.g. org.eclipse.jdt.core) which merges the Gradle model with
the previous state of the file and writes the new preferences out.
