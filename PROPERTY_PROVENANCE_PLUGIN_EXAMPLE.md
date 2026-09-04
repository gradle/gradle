# A multi-actor property provenance example

This example is intended to be small enough to paste into a demo while still showing a
genuine multi-stage trace across plugin boundaries:

```text
DefaultsPlugin: source.convention(missing Provider)
    -> NormalizerPlugin: normalized.set(source.map(...))
        -> build.gradle.kts: value.set(normalized.map(...))
            -> ConsumerPlugin task action: value.get() -> failure

ConsumerPlugin: value.convention("consumer fallback") -> shadowed
```

The first two arrows cross ordinary scalar Property bindings; the last reaches the failed
read. The two `map` operations are Provider dependencies, not property contributions, so
the trace walks through them but does not invent frames for them. The example requires a
Gradle distribution containing the property-provenance prototype.

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
        create("propertyNormalizer") {
            id = "com.example.property-normalizer"
            implementationClass = "com.example.NormalizerPlugin"
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
    private final Property<String> source;
    private final Property<String> normalized;
    private final Property<String> value;

    public PropertyProvenanceExtension(
        Property<String> source,
        Property<String> normalized,
        Property<String> value
    ) {
        this.source = source;
        this.normalized = normalized;
        this.value = value;
    }

    public Property<String> getSource() {
        return source;
    }

    public Property<String> getNormalized() {
        return normalized;
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
        Property<String> source = project.getObjects().property(String.class);
        source.convention(project.getProviders().gradleProperty("missing-default"));
        project.getExtensions().add(
            PropertyProvenanceExtension.class,
            "provenance",
            new PropertyProvenanceExtension(
                source,
                project.getObjects().property(String.class),
                project.getObjects().property(String.class)
            )
        );
    }
}
```

`buildSrc/src/main/java/com/example/NormalizerPlugin.java`:

```java
package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class NormalizerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.example.property-defaults");
        PropertyProvenanceExtension extension =
            project.getExtensions().getByType(PropertyProvenanceExtension.class);
        extension.getNormalized().set(extension.getSource().map(String::trim));
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
        project.getPluginManager().apply("com.example.property-normalizer");
        PropertyProvenanceExtension extension =
            project.getExtensions().getByType(PropertyProvenanceExtension.class);

        extension.getValue().convention("consumer fallback");
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

val provenance = extensions.getByType<PropertyProvenanceExtension>()
provenance.value.set(provenance.normalized.map { "normalized=$it" })
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
    at plugin 'com.example.property-normalizer' (NormalizerPlugin.java:<line>) [explicit source]
    at plugin 'com.example.property-defaults' (DefaultsPlugin.java:<line>) [convention]

Shadowed configuration:
    at plugin 'com.example.property-consumer' (ConsumerPlugin.java:<line>) [convention]
```

Traversal happens only while Gradle formats the failure. It uses the already assembled
Provider graph, does not evaluate a Provider or execute either mapping function, and stops
if it encounters a repeated node. An ordinary replacement `set` still cuts the previous
source chain, while conventions shadowed by an explicit binding remain outside the trace.
