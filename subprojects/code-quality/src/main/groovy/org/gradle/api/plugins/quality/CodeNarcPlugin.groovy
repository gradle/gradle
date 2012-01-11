/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.internal.Instantiator
import org.gradle.api.tasks.SourceSet
import org.gradle.api.plugins.GroovyBasePlugin

class CodeNarcPlugin implements Plugin<Project> {
    private Project project
    private Instantiator instantiator
    private CodeNarcExtension extension
    
    void apply(Project project) {
        this.project = project
        instantiator = project.services.get(Instantiator)

        project.plugins.apply(GroovyBasePlugin)

        configureCodeNarcConfiguration()
        configureCodeNarcExtension()
        configureCodeNarcTasks()
        configureCheckTask()
    }
    
    private void configureCodeNarcConfiguration() {
        project.configurations.add('codenarc').with {
            visible = false
            transitive = true
            description = 'The CodeNarc libraries to be used for this project.'
        }
    }

    private void configureCodeNarcExtension() {
        extension = instantiator.newInstance(CodeNarcExtension, project)
        project.extensions.codenarc = extension
        extension.with {
            toolVersion = "0.16.1"
        }
        extension.conventionMapping.with {
            configFile = { project.file("config/codenarc/codenarc.xml") }
            reportFormat = { "html" }
            ignoreFailures = { false }
            reportsDir = { new File(project.reportsDir, "codenarc") }
        }
    }

    private void configureCodeNarcTasks() {
        project.sourceSets.all { SourceSet sourceSet ->
            def task = project.tasks.add(sourceSet.getTaskName('codenarc', null), CodeNarc)
            task.with {
                description = "Run CodeNarc analysis for ${sourceSet.name} classes"
            }
            task.conventionMapping.with {
                codenarcClasspath = {
                    def config = project.configurations['codenarc']
                    if (config.dependencies.empty) {
                        project.dependencies {
                            codenarc "org.codenarc:CodeNarc:$extension.toolVersion"
                        }
                    }
                    config
                }
                defaultSource = { sourceSet.allGroovy }
                configFile = { extension.configFile }
                reportFormat = { extension.reportFormat }
                reportFile = {
                    def fileSuffix = task.reportFormat == 'text' ? 'txt' : task.reportFormat
                    new File(extension.reportsDir, "${sourceSet.name}.$fileSuffix")
                }
                ignoreFailures = { extension.ignoreFailures }
            }
        }
    }

    private void configureCheckTask() {
        project.tasks['check'].dependsOn { extension.sourceSets.collect { it.getTaskName('codenarc', null) }}
    }
}
