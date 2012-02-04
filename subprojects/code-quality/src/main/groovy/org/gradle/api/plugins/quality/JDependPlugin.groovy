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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.Instantiator
import org.gradle.api.reporting.ReportingExtension

/**
 * <p>
 * A {@link Plugin} that generates design quality metrics by
 * scanning your source packages.  This is done using the JDepend
 * tool.
 * </p>
 * <p>
 * This plugin will automatically generate a task for each Java source set.
 * </p>
 * See <a href="http://www.clarkware.com/software/JDepend.html">JDepend</a> for more information.
 *
 * @see JDependExtension
 * @see JDepend
 */
class JDependPlugin implements Plugin<ProjectInternal> {
    private ProjectInternal project
    private Instantiator instantiator
    private JDependExtension extension

    void apply(ProjectInternal project) {
        this.project = project
        instantiator = project.services.get(Instantiator)

        project.plugins.apply(JavaBasePlugin)

        configureJDependConfiguration()
        configureJDependExtension()
        configureJDependTasks()
        configureCheckTask()
    }

    private void configureJDependConfiguration() {
        project.configurations.add('jdepend').with {
            visible = false
            transitive = true
            description = 'The JDepend libraries to be used for this project.'
        }
    }

    private void configureJDependExtension() {
        extension = instantiator.newInstance(JDependExtension)
        project.extensions.jdepend = extension
        extension.with {
            toolVersion = "2.9.1"
            sourceSets = project.sourceSets
        }
        extension.conventionMapping.with {
            reportsDir = { project.extensions.getByType(ReportingExtension).file("jdepend") }
        }
    }

    private void configureJDependTasks() {
        project.sourceSets.all { SourceSet sourceSet ->
            def task = project.tasks.add(sourceSet.getTaskName('jdepend', null), JDepend)
            task.with {
                dependsOn(sourceSet.output)
                description = "Run JDepend analysis for ${sourceSet.name} classes"
            }
            task.conventionMapping.with {
                jdependClasspath = {
                    def config = project.configurations['jdepend']
                    if (config.dependencies.empty) {
                        project.dependencies {
                            jdepend "jdepend:jdepend:$extension.toolVersion"
                            jdepend("org.apache.ant:ant-jdepend:1.8.2") {
                                exclude module: "ant"
                                exclude module: "ant-launcher"
                            }
                        }
                    }
                    config
                }
                classesDir = { sourceSet.output.classesDir }
                reportFile = { new File(extension.reportsDir, "${sourceSet.name}.xml") }
                ignoreFailures = { extension.ignoreFailures }
            }
        }
    }

    private void configureCheckTask() {
        project.tasks['check'].dependsOn { extension.sourceSets.collect { it.getTaskName('jdepend', null) }}
    }
}
