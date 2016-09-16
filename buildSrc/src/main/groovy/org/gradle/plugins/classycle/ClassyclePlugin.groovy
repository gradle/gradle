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

package org.gradle.plugins.classycle

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet

@CompileStatic
class ClassyclePlugin implements Plugin<Project> {

    public static final String CLASSYCLE_CONFIGURATION_NAME = 'classycle'

    @Override
    void apply(Project project) {
        project.configurations.create(CLASSYCLE_CONFIGURATION_NAME)
        project.dependencies.add(CLASSYCLE_CONFIGURATION_NAME, 'classycle:classycle:1.4@jar')

        def classycleTask = project.tasks.create('classycle')

        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension)

        project.convention.getPlugin(JavaPluginConvention).sourceSets.all { SourceSet sourceSet ->
            def taskName = sourceSet.getTaskName('classycle', null)
            project.tasks.create(taskName, Classycle, { Classycle task ->
                task.reportName = sourceSet.name
                task.classesDir = sourceSet.output.classesDir
                task.reportDir = reporting.file('classycle')
                task.dependsOn(sourceSet.output)
            } as Action<Classycle>)
            classycleTask.dependsOn(taskName)
            project.tasks.getByPath('check').dependsOn(taskName)
            project.tasks.getByPath('codeQuality').dependsOn(taskName)
        }
    }
}
