package org.gradle.build

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
/*
 * Copyright 2016 the original author or authors.
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

@CacheableTask
class ClasspathManifest extends DefaultTask {

    @Classpath
    FileCollection input = project.configurations.runtimeClasspath

    @Input
    List<String> optionalProjects = []

    @Internal
    List<Project> additionalProjects = []

    @OutputFile
    File getManifestFile() {
        return new File(project.generatedResourcesDir, "${project.archivesBaseName}-classpath.properties")
    }

    @Input
    String getRuntime() {
        return input.fileCollection {
            (it instanceof ExternalDependency) || (it instanceof FileCollectionDependency)
        }.collect { it.name }.join(',')
    }

    @Input
    String getProjects() {
        return (input.allDependencies.withType(ProjectDependency).collect {
            it.dependencyProject.archivesBaseName
        } + additionalProjects*.archivesBaseName).join(',')
    }

    Properties createProperties() {
        def properties = new Properties()
        properties.runtime = getRuntime()
        properties.projects = getProjects()
        if (!getOptionalProjects().empty) {
            properties.optional = getOptionalProjects().join(',')
        }
        return properties
    }

    @TaskAction
    def generate() {
        ReproduciblePropertiesWriter.store(createProperties(), manifestFile)
    }
}
