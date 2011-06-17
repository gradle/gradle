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
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.publication.maven.MavenScope
import org.gradle.api.publication.maven.internal.model.DefaultMavenArtifact
import org.gradle.api.publication.maven.internal.model.DefaultMavenDependency
import org.gradle.api.publication.maven.internal.model.DefaultMavenPublication
import org.gradle.api.tasks.bundling.Jar

/**
 * @author: Szczepan Faber, created at: 5/13/11
 */
//TODO SF - rename to MavenPublicationBuilder
class MavenModelBuilder {

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

            //TODO SF:
            //should project dependencies be transformed into entries in the pom?
            //how to approach the case when the ExternalDependency has multiple artifcts? Don't know when it happens, though
            //we could check if war plugin was applied and deal with providedCompile and providedRuntime?
            //should we make sure that there are no duplicate entries e.g. the same library in both compile scope and test scope

            publication.conventionMapping.dependencies = {
                //TODO SF: I absolutely hate it but the goal today is not to make the DSL perfect but to have working pre-population of the model
                //1. It suffers from the fundamental convention mapping issue - non mutable collections
                //2. It is hard to reconfigure by the user (Imagine the user typing all this code what I did below if he needs to put a dependency from a different configuration)
                //3. I don't want to pass Configurations to the maven model. We went down that path with ide plugins and it bites us hard. We need the DependencySet!
                def out = new LinkedList()
                project.configurations['compile'].getDependencies(ExternalDependency).each {
                    out << new DefaultMavenDependency(it, MavenScope.COMPILE);
                }

                project.configurations['testCompile'].getDependencies(ExternalDependency).each {
                    out << new DefaultMavenDependency(it, MavenScope.TEST);
                }

                project.configurations['runtime'].getDependencies(ExternalDependency).each {
                    out << new DefaultMavenDependency(it, MavenScope.RUNTIME);
                }

                project.configurations['testRuntime'].getDependencies(ExternalDependency).each {
                    out << new DefaultMavenDependency(it, MavenScope.TEST);
                }
                return out
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
