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

package org.gradle.api.publication.maven.internal.modelbuilder

import org.gradle.api.Project
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.publication.maven.internal.model.DefaultMavenArtifact
import org.gradle.api.publication.maven.internal.model.DefaultMavenPublication
import org.gradle.api.tasks.bundling.Jar

/**
 * @author: Szczepan Faber, created at: 5/13/11
 */
class MavenPublicationBuilder {

    MavenPublication build(Project project) {
        DefaultMavenPublication publication = project.services.get(ClassGenerator).newInstance(DefaultMavenPublication)
        publication.mainArtifact = project.services.get(ClassGenerator).newInstance(DefaultMavenArtifact)
        //@Peter, I was prolific with comments because I wasn't sure I'll be able to pair soon. Get rid of comments if you like.

        //basic values can be easily extracted from the project:
        publication.conventionMapping.description = { project.description }
        publication.conventionMapping.groupId = { project.group.toString()? project.group.toString() : 'unspecified.group'}
        publication.conventionMapping.version = { project.version.toString() }
        publication.modelVersion = '4.0.0'

        project.plugins.withType(JavaPlugin) {
            publication.packaging = 'jar'
            //I like the simple way of setting the packaging above. There're other theoretical ways of getting the packaging, but they're ugly:
            //or: we can extract packaging from maven installer but that's difficult as the code is complex
            //or: publication.conventionMapping.groupId = { project.convention.plugins.maven.pom().groupId }
            //or: publication.packaging = project.convention.plugins.maven.pom().packaging

            withTask(project, 'jar', Jar) { Jar jar ->
                //It makes more sense to me to get the artifact info from the 'jar' section of the build because 'jar' task is the way for a user to declaratively configure version/baseName.
                publication.conventionMapping.artifactId = { jar.baseName }
                publication.conventionMapping.version = { jar.version }

                //We can also get this info from the project... se below
                //or: publication.conventionMapping.artifactId = { project.convention.plugins.base.archivesBaseName }
                //or: publication.conventionMapping.version = { project.version.toString() }

                //again it feels natural to get it from the 'jar' section of the build.
                publication.mainArtifact.conventionMapping.classifier = { project.jar.classifier }
                publication.mainArtifact.conventionMapping.extension = { project.jar.extension }
                publication.mainArtifact.conventionMapping.file = { project.jar.archivePath }
            }

            publication.conventionMapping.dependencies = {
                new DependenciesConverter().convert(project)
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
