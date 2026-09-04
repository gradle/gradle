# Property provenance from a plugin

This example uses a `buildSrc` plugin to configure a property directly. It then reads the
missing explicit Provider from a task action, producing a trace back to the plugin. The
example requires a Gradle distribution containing the property-provenance prototype.

`buildSrc/build.gradle.kts`:

```kotlin
plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("propertyProvenance") {
            id = "com.example.property-provenance"
            implementationClass = "com.example.PropertyProvenancePlugin"
        }
    }
}
```

`buildSrc/src/main/java/com/example/PropertyProvenancePlugin.java`:

```java
package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public final class PropertyProvenancePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Property<String> value = project.getObjects().property(String.class);
        value.convention("default");
        value.set(project.getProviders().gradleProperty("missing-value"));

        project.getTasks().register("showProvenance", task ->
            task.doLast(ignored -> value.get())
        );
    }
}
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.example.property-provenance")
}
```

Run the failing task with provenance enabled:

```text
./gradlew showProvenance -Dorg.gradle.internal.property-provenance=true
```

The relevant part of the failure is:

```text
Failure trace to source:
    at task ':showProvenance' action (PropertyProvenancePlugin.java:<line>) [get()]
    at plugin 'com.example.property-provenance' (PropertyProvenancePlugin.java:<line>) [explicit source]

Shadowed configuration:
    at plugin 'com.example.property-provenance' (PropertyProvenancePlugin.java:<line>) [convention]
```

The same plugin attribution is retained when the `set` or `convention` call runs later in a
callback registered by the plugin, such as a `tasks.register { ... }` configuration action.
