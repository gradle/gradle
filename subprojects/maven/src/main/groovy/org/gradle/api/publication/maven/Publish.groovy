package org.gradle.api.publication.maven

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction

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

