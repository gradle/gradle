package org.gradle.api.publication.maven.internal

import org.gradle.api.publication.maven.MavenPublication
import org.apache.maven.model.Model
import org.apache.maven.model.Dependency
import org.gradle.api.publication.maven.MavenDependency

class MavenPublicationPomGenerator {
    private final MavenPublication publication

    private final Model model = new Model()

    MavenPublicationPomGenerator(MavenPublication publication) {
        this.publication = publication
    }

    Model generatePom() {
        generateMainAttributes()
        generateDependencies()
        model
    }

    private void generateMainAttributes() {
        model.modelVersion = publication.modelVersion
        model.groupId = publication.groupId
        model.artifactId = publication.artifactId
        model.version = publication.version
        model.packaging = publication.packaging
        model.description = publication.description
    }

    private void generateDependencies() {
        model.dependencies = publication.dependencies.collect { MavenDependency mavenDep ->
            def dependency = new Dependency()
            dependency.groupId = mavenDep.groupId
            dependency.artifactId = mavenDep.artifactId
            dependency.version = mavenDep.version
            dependency.classifier = mavenDep.classifier
            dependency.scope = mavenDep.scope.name().toLowerCase()
            dependency.optional = mavenDep.optional
        }
    }
}
