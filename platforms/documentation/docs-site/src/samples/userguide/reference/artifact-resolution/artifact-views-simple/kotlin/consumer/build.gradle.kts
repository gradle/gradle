// tag::artifact-views-app[]
plugins {
    application
}

repositories {
    mavenCentral()
}

// Declare the dependency on the producer project
dependencies {
    implementation(project(":producer")) // This references another subproject in the same build
}
// end::artifact-views-app[]


tasks.register("checkResolvedVariant") {
    println("RuntimeClasspath Configuration:")
    val resolvedArtifacts = configurations.runtimeClasspath.get().incoming.artifacts.resolvedArtifacts.get()
    resolvedArtifacts.forEach { artifact ->
        println("- Artifact: ${artifact.file}") // Print each resolved artifact file
    }
    val resolvedComponents = configurations.runtimeClasspath.get().incoming.resolutionResult.allComponents
    resolvedComponents.forEach { component ->
        if (component.id.displayName == "project :producer") {
            println("- Component: ${component.id}")
            component.variants.forEach { variant ->
                println("    - Variant: ${variant.displayName}")
                variant.attributes.keySet().forEach { key ->
                    println("       - ${key.name} -> ${variant.attributes.getAttribute(key)}")
                }
            }
        }
    }
}

// tag::artifact-views-sel[]
tasks.register("artifactWithAttributeAndView") {
    val configuration = configurations.runtimeClasspath
    println("ArtifactView with attribute 'libraryelements = classes' for ${configuration.name}:")
    val artifactView = configuration.get().incoming.artifactView {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class, "classes"))
        }
    }
    println("- Attributes:")
    artifactView.attributes.keySet().forEach { attribute ->
        val value = artifactView.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = ${value}")
    }
    artifactView.artifacts.artifactFiles.files.forEach { file ->
        println("- Artifact: ${file.name}")
    }
}
// tag::artifact-views-sel[]

// tag::artifact-views-resel[]
tasks.register("artifactWithAttributeAndVariantReselectionView") {
    val configuration = configurations.runtimeClasspath
    println("ArtifactView with attribute 'category = production' for ${configuration.name}:")
    val artifactView = configuration.get().incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "production"))
        }
    }
    println("- Attributes:")
    artifactView.attributes.keySet().forEach { attribute ->
        val value = artifactView.attributes.getAttribute(attribute)
        println("  - ${attribute.name} = ${value}")
    }
    artifactView.artifacts.artifactFiles.files.forEach { file ->
        println("- Artifact: ${file.name}")
    }
}
// end::artifact-views-resel[]
