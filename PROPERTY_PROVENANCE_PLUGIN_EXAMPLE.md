# A multi-actor property provenance example

This example is intended to be small enough to paste into a demo while still showing why
provenance is useful across plugin boundaries. Three actors take part:

```text
defaults plugin -- convention (shadowed) --\
                                           property -- consumer plugin task action -- failure
build author ---- explicit source (selected) /
```

The defaults plugin creates the property and supplies a convention. The consumer plugin
registers the task that eventually reads it. Between those events, the build author binds
a missing explicit Provider. The example requires a Gradle distribution containing the
property-provenance prototype.

`buildSrc/build.gradle.kts`:

```kotlin
plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("propertyDefaults") {
            id = "com.example.property-defaults"
            implementationClass = "com.example.DefaultsPlugin"
        }
        create("propertyConsumer") {
            id = "com.example.property-consumer"
            implementationClass = "com.example.ConsumerPlugin"
        }
    }
}
```

`buildSrc/src/main/java/com/example/PropertyProvenanceExtension.java`:

```java
package com.example;

import org.gradle.api.provider.Property;

public final class PropertyProvenanceExtension {
    private final Property<String> value;

    public PropertyProvenanceExtension(Property<String> value) {
        this.value = value;
    }

    public Property<String> getValue() {
        return value;
    }
}
```

`buildSrc/src/main/java/com/example/DefaultsPlugin.java`:

```java
package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public final class DefaultsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Property<String> value = project.getObjects().property(String.class);
        value.convention(project.getProviders().gradleProperty("missing-default"));
        project.getExtensions().add(
            PropertyProvenanceExtension.class,
            "provenance",
            new PropertyProvenanceExtension(value)
        );
    }
}
```

`buildSrc/src/main/java/com/example/ConsumerPlugin.java`:

```java
package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class ConsumerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.example.property-defaults");
        PropertyProvenanceExtension extension =
            project.getExtensions().getByType(PropertyProvenanceExtension.class);

        project.getTasks().register("shareProvenance", task ->
            task.doLast(ignored -> extension.getValue().get())
        );
    }
}
```

`build.gradle.kts`:

```kotlin
import com.example.PropertyProvenanceExtension

plugins {
    id("com.example.property-consumer")
}

extensions.getByType<PropertyProvenanceExtension>().value.set(
    providers.gradleProperty("missing-explicit")
)
```

Run the failing task with provenance enabled:

```text
./gradlew shareProvenance -Dorg.gradle.internal.property-provenance=true
```

The relevant part of the failure is:

```text
Failure trace to source:
    at task ':shareProvenance' action (ConsumerPlugin.java:<line>) [get()]
    at build file 'build.gradle.kts' (build.gradle.kts:<line>) [explicit source]

Shadowed configuration:
    at plugin 'com.example.property-defaults' (DefaultsPlugin.java:<line>) [convention]
```

This is the longest meaningful effective trace in the current ordinary-property slice: the
failed operation, the selected source, and a separately rendered shadowed convention. An
additional ordinary `set` would replace the selected source and deliberately cut the old
one from the effective trace. Likewise, `map` and `flatMap` describe Provider dependencies,
not additional property contributions.

A longer effective chain requires structural self-updates or the collaborative update
pipeline. Those are intentionally outside this prototype slice; presenting either as if it
already worked would blur the distinction between property provenance and Provider
dependency metadata.
