plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:33.2.1-jre")
}

// tag::process-javadocs-views[]
val customConfiguration by configurations.creating

dependencies {
    customConfiguration("com.google.guava:guava:30.1-jre")
}

tasks.register("processJavadocs") {
    doLast {
        val view = customConfiguration.incoming.artifactView {
            // Filter by attribute
            attributes {
                // This filters the artifacts down to only the Javadoc
                // files associated with the Guava dependency
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.JAVADOC))
            }
        }

        // Get the artifacts
        val artifacts = view.artifacts.artifactFiles

        // Process each artifact
        artifacts.files.forEach { file ->
            println("Processing Javadoc artifact: ${file.name}")
            // Perform custom processing, e.g., copying or transforming
        }
    }
}
// end::process-javadocs-views[]

// tag::resolve-javadocs-views[]
tasks.register("resolveJavadocs") {
    doLast {
        val javadocArtifacts = customConfiguration.incoming.artifactView {
            // Allows Gradle to select artifacts from alternative variants of a component,
            // not just the variant that was initially selected during the dependency graph resolution phase.
            withVariantReselection()
            // Filter by attribute
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME)) // Base usage
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION)) // Selecting documentation category
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType::class.java, DocsType.JAVADOC)) // Selecting Javadoc
            }
        }.artifacts

        if (javadocArtifacts.artifacts.isEmpty()) {
            println("No Javadoc artifacts found.")
        } else {
            javadocArtifacts.forEach { artifact ->
                println("Resolved Javadoc: ${artifact.file.name}")
            }
        }
    }
}
// end::resolve-javadocs-views[]
