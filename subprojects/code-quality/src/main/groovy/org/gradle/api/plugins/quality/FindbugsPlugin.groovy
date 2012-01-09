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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.Compile

/**
 * <p>
 * A {@link Plugin} which uses static analysis to look for bugs in Java code.  
 * This is done using the Findbugs tool.
 * </p>
 * <p>
 * This plugin will automatically generate a task for each Java source set.
 * </p>
 * See {@link http://findbugs.sourceforge.net/} for more information.
 * @see Findbugs
 * @see FindbugsExtension
 */
class FindbugsPlugin implements Plugin<Project> {
    private static final String FINDBUGS_TASK_NAME = 'findbugs'
    private static final String FINDBUGS_CONFIGURATION_NAME = 'findbugs'
    
    /**
     * Applies the plugin to the specified project.
     * @param project the project to apply this plugin to
     */
    void apply(Project project) {
        project.plugins.apply(ReportingBasePlugin)

        project.configurations.add(FINDBUGS_CONFIGURATION_NAME)
            .setVisible(false)
            .setTransitive(true)
            .setDescription('The findbugs libraries to be used for this project.')

        def extension = new FindbugsExtension(project)
        project.extensions.findbugs = extension

        project.plugins.withType(JavaBasePlugin) {
            configureForJavaPlugin(project, extension)
        }
    }

    /**
     * Adds a dependency for the check task on all
     * Findbugs tasks.
     * @param project the project to configure the check task for
     */
    private void configureCheckTask(Project project) {
        def task = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
        task.dependsOn project.tasks.withType(Findbugs)
    }

    /**
     * Configures Findbugs tasks for Java source sets.
     * @param project the project to configure findbugs for
     * @param convention the findbugs convention to use
     */
    private void configureForJavaPlugin(final Project project, final FindbugsExtension extension) {
        configureCheckTask(project)

        project.convention.getPlugin(JavaPluginConvention).sourceSets.all { SourceSet set ->
            def findbugs = project.tasks.add(set.getTaskName(FINDBUGS_TASK_NAME, null), Findbugs)
            findbugs.description = "Run findbugs analysis for ${set.name} classes"
            findbugs.dependsOn project.tasks.withType(Compile)
            findbugs.conventionMapping.defaultSource = { set.allJava }
            findbugs.conventionMapping.classpath = { set.compileClasspath }
            findbugs.conventionMapping.classes = { set.output }
            findbugs.conventionMapping.resultsFile = { new File(extension.resultsDir, "${set.name}.xml") }
        }
    }
}
