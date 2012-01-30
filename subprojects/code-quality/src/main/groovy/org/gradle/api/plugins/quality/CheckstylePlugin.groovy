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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.Instantiator
import org.gradle.api.tasks.SourceSet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.reporting.ReportingExtension

class CheckstylePlugin implements Plugin<Project> {
    private Project project
    private Instantiator instantiator
    private CheckstyleExtension extension
    
    void apply(Project project) {
        this.project = project
        instantiator = project.services.get(Instantiator)

        project.plugins.apply(JavaBasePlugin)

        configureCheckstyleConfiguration()
        configureCheckstyleExtension()
        configureCheckstyleTasks()
        configureCheckTask()
    }
    
    private void configureCheckstyleConfiguration() {
        project.configurations.add('checkstyle').with {
            visible = false
            transitive = true
            description = 'The Checkstyle libraries to be used for this project.'
        }
    }

    private void configureCheckstyleExtension() {
        extension = instantiator.newInstance(CheckstyleExtension, project)
        project.extensions.checkstyle = extension

        extension.with {
            toolVersion = "5.5"
            sourceSets = project.sourceSets
        }

        extension.conventionMapping.with {
            configFile = { project.file("config/checkstyle/checkstyle.xml") }
            configProperties = { [:] }
            reportsDir = { project.extensions.getByType(ReportingExtension).file("checkstyle") }
        }
    }

    private void configureCheckstyleTasks() {
        project.sourceSets.all { SourceSet sourceSet ->
            def task = project.tasks.add(sourceSet.getTaskName('checkstyle', null), Checkstyle)
            task.with {
                description = "Run Checkstyle analysis for ${sourceSet.name} classes"
                classpath = sourceSet.output
            }
            task.conventionMapping.with {
                checkstyleClasspath = {
                    def config = project.configurations['checkstyle']
                    if (config.dependencies.empty) {
                        project.dependencies {
                            checkstyle "com.puppycrawl.tools:checkstyle:$extension.toolVersion"
                        }
                    }
                    config
                }
                defaultSource = { sourceSet.allJava }
                configFile = { extension.configFile }
                configProperties = { extension.configProperties }
                reportFile = { new File(extension.reportsDir, "${sourceSet.name}.xml") }
                ignoreFailures = { extension.ignoreFailures }
            }
        }
    }

    private void configureCheckTask() {
        project.tasks['check'].dependsOn { extension.sourceSets.collect { it.getTaskName('checkstyle', null) }}
    }
}
