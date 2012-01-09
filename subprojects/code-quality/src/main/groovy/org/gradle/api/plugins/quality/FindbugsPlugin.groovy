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
import org.gradle.api.internal.Instantiator
import org.gradle.api.internal.project.ProjectInternal

/**
 * A plugin for the <a href="http://findbugs.sourceforge.net">FindBugs</a> byte code analyzer.
 *
 * <p>
 * Declares a <tt>findbugs</tt> configuration which needs to be configured with the FindBugs library to be used.
 * Additional plugins can be added to the <tt>findbugsPlugins</tt> configuration.
 *
 * <p>
 * For projects that have the Java (base) plugin applied, a {@link FindBugs} task is
 * created for each source set.
 *
 * @see FindBugs
 * @see FindBugsExtension
 */
class FindBugsPlugin implements Plugin<ProjectInternal> {
    private ProjectInternal project
    private Instantiator instantiator
    private FindBugsExtension extension

    void apply(ProjectInternal project) {
        this.project = project
        instantiator = project.services.get(Instantiator)
    
        project.plugins.apply(JavaBasePlugin)

        configureFindBugsConfigurations()
        configureFindBugsExtension()
        configureFindBugsTasks()
        configureCheckTask()
    }

    private configureFindBugsConfigurations() {
        project.configurations.add('findbugs').with {
            visible = false
            transitive = true
            description = 'The FindBugs libraries to be used for this project.'
        }

        project.configurations.add('findbugsPlugins').with {
            visible = false
            transitive = true
            description = 'The FindBugs plugins to be used for this project.'
        }
    }

    private configureFindBugsExtension() {
        extension = instantiator.newInstance(FindBugsExtension, project)
        project.extensions.findbugs = extension
        extension.with {
            toolVersion = "1.3.9" // 2.0.0 isn't yet available from Maven Central
        }
        extension.conventionMapping.with {
            reportsDir = { new File(project.reportsDir, "findbugs") }
        }
    }

    private void configureFindBugsTasks() {
        project.sourceSets.all { SourceSet sourceSet ->
            def task = project.tasks.add(sourceSet.getTaskName('findbugs', null), FindBugs)
            task.with {
                description = "Run FindBugs analysis for ${sourceSet.name} classes"
                pluginClasspath = project.configurations['findbugsPlugins']
                classes = sourceSet.output
            }
            task.conventionMapping.with {
                findbugsClasspath = {
                    def config = project.configurations['findbugs']
                    if (config.dependencies.empty) {
                        project.dependencies {
                            findbugs "com.google.code.findbugs:findbugs:$extension.toolVersion"
                            findbugs "com.google.code.findbugs:findbugs-ant:$extension.toolVersion"
                        }
                    }
                    config
                }
                defaultSource = { sourceSet.allJava }
                classpath = { sourceSet.compileClasspath }
                reportFile = { new File(extension.reportsDir, "${sourceSet.name}.xml") }
                ignoreFailures = { extension.ignoreFailures }
            }
        }
    }

    private void configureCheckTask() {
        project.tasks['check'].dependsOn { extension.sourceSets.collect { it.getTaskName('findbugs', null) }}
    }
}
