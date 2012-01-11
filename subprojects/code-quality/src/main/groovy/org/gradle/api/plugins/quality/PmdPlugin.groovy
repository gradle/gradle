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

import org.gradle.api.internal.Instantiator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.SourceSet

/**
 *  A plugin for the <a href="http://pmd.sourceforge.net/">PMD source code analyzer.
 * <p>
 * Declares a <tt>findbugs</tt> configuration which needs to be configured with the FindBugs library to be used.
 * Additional plugins can be added to the <tt>findbugsPlugins</tt> configuration.
 * <p>
 * For each source set that is to be analyzed, a {@link Pmd} task is created and configured to analyze all Java code.
 * <p
 * All PMD tasks (including user-defined ones) are added to the <tt>check</tt> lifecycle task.
 *
 * @see PmdExtension
 * @see Pmd
 */
class PmdPlugin implements Plugin<ProjectInternal> {
    private ProjectInternal project
    private Instantiator instantiator
    private PmdExtension extension

    void apply(ProjectInternal project) {
        this.project = project
        instantiator = project.services.get(Instantiator)

        project.plugins.apply(JavaBasePlugin)

        configurePmdConfiguration()
        configurePmdExtension()
        configurePmdTasks()
        configureCheckTask()
    }

    private void configurePmdExtension() {
        extension = instantiator.newInstance(PmdExtension, project)
        project.extensions.pmd = extension
        extension.with {
            toolVersion = "4.3"
            ruleSets = ["basic"]
            ruleSetFiles = project.files()
        }
        extension.conventionMapping.with {
            xmlReportsDir = { new File(project.reportsDir, "pmd") }
            htmlReportsDir = { new File(project.reportsDir, "pmd") }
        }
    }

    private configurePmdConfiguration() {
        project.configurations.add('pmd').with {
            visible = false
            transitive = true
            description = 'The PMD libraries to be used for this project.'
        }
    }

    private void configurePmdTasks() {
        project.sourceSets.all { SourceSet sourceSet ->
            def task = project.tasks.add(sourceSet.getTaskName('pmd', null), Pmd)
            task.with {
                description = "Run PMD analysis for ${sourceSet.name} classes"
            }
            task.conventionMapping.with {
                pmdClasspath = {
                    def config = project.configurations['pmd']
                    if (config.dependencies.empty) {
                        project.dependencies {
                            pmd "pmd:pmd:$extension.toolVersion"
                        }
                    }
                    config
                }
                defaultSource = { sourceSet.allJava }
                ruleSets = { extension.ruleSets }
                ruleSetFiles = { extension.ruleSetFiles }
                xmlReportFile = { new File(extension.xmlReportsDir, "${sourceSet.name}.xml") }
                htmlReportFile = { new File(extension.htmlReportsDir, "${sourceSet.name}.html") }
                ignoreFailures = { extension.ignoreFailures }
            }
        }
    }

    private void configureCheckTask() {
        project.tasks['check'].dependsOn { extension.sourceSets.collect { it.getTaskName('pmd', null) }}
    }
}
