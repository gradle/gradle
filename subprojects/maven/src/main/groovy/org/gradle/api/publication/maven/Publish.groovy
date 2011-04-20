package org.gradle.api.publication.maven

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction
import org.sonatype.aether.impl.internal.DefaultServiceLocator
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory

/**
 * Publishes a Maven publication to a Maven repository.
 */
class Publish extends ConventionTask {
    MavenPublication publication
    MavenRepository repository

    @TaskAction
    void execute() {


    }
}

