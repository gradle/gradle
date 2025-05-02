package com.acme

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.component.AdhocComponentWithVariants

import javax.inject.Inject

// https://issues.apache.org/jira/browse/GROOVY-10055
//@CompileStatic
class InstrumentedJarsPlugin implements Plugin<Project> {
    // tag::inject_software_component_factory[]
    private final SoftwareComponentFactory softwareComponentFactory

    @Inject
    InstrumentedJarsPlugin(SoftwareComponentFactory softwareComponentFactory) {
        this.softwareComponentFactory = softwareComponentFactory
    }
    // end::inject_software_component_factory[]

    @Override
    void apply(Project project) {
        Configuration outgoingConfiguration = createOutgoingConfiguration(project)
        attachArtifact(project)
        configurePublication(project, outgoingConfiguration)
        addVariantToExistingComponent(project, outgoingConfiguration)
    }

    private void attachArtifact(Project project) {
        def instrumentedJar = project.tasks.register("instrumentedJar", Jar) { Jar jar ->
            jar.archiveClassifier.set("instrumented")
        }
        project.artifacts.add("instrumentedJars", instrumentedJar)
    }

    private Configuration createOutgoingConfiguration(Project project) {
        project.configurations.create("instrumentedJars") { Configuration cnf ->
            cnf.canBeConsumed = true
            cnf.canBeResolved = false
            cnf.attributes {
                it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                it.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, JavaVersion.current().majorVersion.toInteger())
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, 'instrumented-jar'))
            }
        }
    }

    private void configurePublication(Project project, Configuration outgoing) {
        // tag::create_adhoc_component[]
        // create an adhoc component
        def adhocComponent = softwareComponentFactory.adhoc("myAdhocComponent")
        // add it to the list of components that this project declares
        project.components.add(adhocComponent)
        // and register a variant for publication
        adhocComponent.addVariantsFromConfiguration(outgoing) {
            it.mapToMavenScope("runtime")
        }
        // end::create_adhoc_component[]
    }

    private void addVariantToExistingComponent(Project project, Configuration outgoing) {
        // tag::add_variant_to_existing_component[]
        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.components.findByName("java")
        javaComponent.addVariantsFromConfiguration(outgoing) {
            // dependencies for this variant are considered runtime dependencies
            it.mapToMavenScope("runtime")
            // and also optional dependencies, because we don't want them to leak
            it.mapToOptional()
        }
        // end::add_variant_to_existing_component[]
    }

}
