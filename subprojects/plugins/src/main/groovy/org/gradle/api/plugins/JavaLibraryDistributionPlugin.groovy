/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.distribution.Distribution


/**
 * A {@link Plugin} which package a project as a distribution including
 * JAR,API documentation and source JAR for the project.
 * @author scogneau
 *
 */
class JavaLibraryDistributionPlugin implements Plugin<Project> {
    static final String JAVA_LIBRARY_PLUGIN_NAME = "java-library-distribution"
    static final String JAVA_LIBRARY_GROUP = JAVA_LIBRARY_PLUGIN_NAME
    static final String TASK_DIST_ZIP_NAME = "distZip"

    private Project project
    Distribution extension

    public void apply(Project project) {
        this.project = project
        project.plugins.apply(JavaPlugin)
		project.plugins.apply(DistributionPlugin)
        addPluginExtension()
        configureDistZipTask()
    }

    private void addPluginExtension() {
        extension = project.distributions[Distribution.MAIN_DISTRIBUTION_NAME]
        extension.name = project.name
        project.extensions.add("distribution", extension)
    }


    private void configureDistZipTask() {
        def distZipTask = project.tasks.getByName(TASK_DIST_ZIP_NAME)
        def jar = project.tasks[JavaPlugin.JAR_TASK_NAME]
        distZipTask.with {
            from(jar)
            from(project.file("src/dist"))
            into("lib") {
                from(project.configurations.runtime)
            }
        }
    }
}
