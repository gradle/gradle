/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publication.maven.internal

import org.gradle.api.Project
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

/**
 * @author: Szczepan Faber, created at: 5/13/11
 */
class MavenModelBuilder {

    MavenPublication build(Project project) {
        DefaultMavenPublication publication = project.services.get(ClassGenerator).newInstance(DefaultMavenPublication)
        publication.mainArtifact = project.services.get(ClassGenerator).newInstance(DefaultMavenArtifact)

        publication.conventionMapping.description = { project.description }
        publication.conventionMapping.groupId = { project.group.toString() }
        publication.modelVersion = '4.0.0'

        project.plugins.withType(JavaPlugin) {
            publication.packaging = 'jar'
            //or: in theory we can extract this info from maven resolver but that's difficult as the code is complex
            //or: publication.conventionMapping.groupId = { project.convention.plugins.maven.pom().groupId }
            //or: publication.packaging = project.convention.plugins.maven.pom().packaging

            withTask(project, 'jar', Jar) { Jar jar ->
                publication.conventionMapping.artifactId = { jar.baseName }
                publication.conventionMapping.version = { jar.version }
                //or: publication.conventionMapping.artifactId = { project.convention.plugins.base.archivesBaseName }
                //or: publication.conventionMapping.version = { project.version.toString() }

                publication.mainArtifact.classifier = project.jar.classifier
                publication.mainArtifact.extension = project.jar.extension
                publication.mainArtifact.file = project.jar.archivePath
            }
        }

        return publication
    }

    void withTask(Project project, String taskName, Class taskType, Closure configureTask) {
        project.tasks.withType(taskType) {
            if (it.name == taskName) {
                configureTask(it)
            }
        }
    }
}
