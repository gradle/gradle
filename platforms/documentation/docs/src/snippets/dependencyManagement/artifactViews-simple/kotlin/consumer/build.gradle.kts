// tag::artifact-views-app[]
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
// end::artifact-views-app[]

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

// tag::artifact-views-sel[]
tasks.register("artifactWithAttributeAndView") {
    val configuration = configurations.runtimeClasspath.get()
    println("\nArtifactView with attribute 'libraryelements = classes' for ${configuration.name}:")
    val artifactView = configuration.incoming.artifactView {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("classes"))
        }
    }
    println("- Attributes:")
    artifactView.attributes.keySet().forEach { attribute ->
        val value = artifactView.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = $value")
    }
    artifactView.artifacts.artifactFiles.files.forEach {
        println("- Artifact: ${it.name}")
    }
}
// end::artifact-views-sel[]

// tag::artifact-views-resel[]
tasks.register("artifactWithAttributeAndVariantReselectionView") {
    val configuration = configurations.runtimeClasspath.get()
    println("\nArtifactView with attribute 'category = production' for ${configuration.name}:")
    val artifactView = configuration.incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("production"))
        }
    }
    println("- Attributes:")
    artifactView.attributes.keySet().forEach { attribute ->
        val value = artifactView.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = $value")
    }
    artifactView.artifacts.artifactFiles.files.forEach {
        println("- Artifact: ${it.name}")
    }
}
// end::artifact-views-resel[]
