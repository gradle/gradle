package com.acme

import org.gradle.api.JavaVersion
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.named
import javax.inject.Inject

// tag::inject_software_component_factory[]
class InstrumentedJarsPlugin @Inject constructor(
    private val softwareComponentFactory: SoftwareComponentFactory) : Plugin<Project> {
// end::inject_software_component_factory[]

    override fun apply(project: Project) = project.run {
        val outgoingConfiguration = createOutgoingConfiguration()
        attachArtifact()
        configurePublication(outgoingConfiguration)
        addVariantToExistingComponent(outgoingConfiguration)
    }

    private fun Project.configurePublication(outgoing: Configuration) {
        // tag::create_adhoc_component[]
        // create an adhoc component
        val adhocComponent = softwareComponentFactory.adhoc("myAdhocComponent")
        // add it to the list of components that this project declares
        components.add(adhocComponent)
        // and register a variant for publication
        adhocComponent.addVariantsFromConfiguration(outgoing) {
            mapToMavenScope("runtime")
        }
        // end::create_adhoc_component[]
    }

    private fun Project.attachArtifact() {
        val instrumentedJar = tasks.register<Jar>("instrumentedJar") {
            archiveClassifier.set("instrumented")
        }

        artifacts {
            add("instrumentedJars", instrumentedJar)
        }

    }

    private fun Project.createOutgoingConfiguration(): Configuration {
        val instrumentedJars by configurations.creating {
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, JavaVersion.current().majorVersionNumber)
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("instrumented-jar"))
            }
        }
        return instrumentedJars
    }

    private fun Project.addVariantToExistingComponent(outgoing: Configuration) {
        // tag::add_variant_to_existing_component[]
        val javaComponent = components.findByName("java") as AdhocComponentWithVariants
        javaComponent.addVariantsFromConfiguration(outgoing) {
            // dependencies for this variant are considered runtime dependencies
            mapToMavenScope("runtime")
            // and also optional dependencies, because we don't want them to leak
            mapToOptional()
        }
        // end::add_variant_to_existing_component[]
    }
}
