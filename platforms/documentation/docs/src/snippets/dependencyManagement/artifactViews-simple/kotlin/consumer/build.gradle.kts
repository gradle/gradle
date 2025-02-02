plugins {
    id("application")
}

repositories {
    mavenCentral()
}

// Declare the dependency on the producer project
dependencies {
    implementation(project(":producer"))
}

tasks.register("checkResolvedVariant") {
    println("RuntimeClasspath Configuration:")
    val resolvedComponents = configurations.runtimeClasspath.get().incoming.resolutionResult.allComponents
    resolvedComponents.forEach { component ->
        if (component.id.displayName == "project :producer") {
            println("- Component: ${component.id}")
            component.variants.forEach {
                println("    - Variant: ${it}")
                it.attributes.keySet().forEach { key ->
                    println("       - ${key.name} -> ${it.attributes.getAttribute(key)}")
                }
            }
        }
    }
    val resolvedArtifacts = configurations.runtimeClasspath.get().incoming.artifacts.resolvedArtifacts
    resolvedArtifacts.get().forEach { artifact ->
        println("- Artifact: ${artifact.file}")
    }

}

tasks.register("artifactWithAttributeAndView") {
    val configuration = configurations.runtimeClasspath.get()
    println("Attributes used to resolve '${configuration.name}':")
    configuration.attributes.keySet().forEach { attribute ->
        val value = configuration.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = $value")
    }

    println("\nAttributes in ArtifactView for 'LibraryElements = classes:'")
    val artifactView = configuration.incoming.artifactView {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("classes"))
        }
    }

    artifactView.artifacts.artifactFiles.files.forEach {
        println("- Artifact: ${it.name}")
    }

    artifactView.attributes.keySet().forEach { attribute ->
        val value = artifactView.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = $value")
    }
}

tasks.register("artifactWithAttributeAndVariantReselectionView") {
    val configuration = configurations.runtimeClasspath.get()
    println("Attributes used to resolve '${configuration.name}':")
    configuration.attributes.keySet().forEach { attribute ->
        val value = configuration.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = $value")
    }

    println("\nAttributes in ArtifactView for 'Category = production:'")
    val artifactView = configuration.incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("production"))
        }
    }

    artifactView.artifacts.artifactFiles.files.forEach {
        println("- Artifact: ${it.name}")
    }

    artifactView.attributes.keySet().forEach { attribute ->
        val value = artifactView.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = $value")
    }
}
