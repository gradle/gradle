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

import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip

/**
 * A {@link Plugin} which package project as a distribution including
 *  JAR, API documentation and source JAR for the project.
 * @author scogneau
 *
 */
@Incubating
class JavaLibraryDistributionPlugin implements Plugin<Project> {
    static final String JAVA_LIBRARY_PLUGIN_NAME = "java-library-distribution"
    static final String JAVA_LIBRARY_GROUP = JAVA_LIBRARY_PLUGIN_NAME
    static final String TASK_DIST_ZIP_NAME = "distZip"

    private DistributionExtension extension
    private Project project

    public void apply(Project project) {
        this.project = project
        project.plugins.apply(JavaPlugin)
        addPluginExtension()
        addDistZipTask()
    }

    private void addPluginExtension() {
        extension = project.extensions.create("distribution", DistributionExtension)
        extension.name = project.name
    }

    private void addDistZipTask() {
        def distZipTask = project.tasks.add(TASK_DIST_ZIP_NAME, Zip)
        distZipTask.description = "Bundles the project as a java library distribution."
        distZipTask.group = JAVA_LIBRARY_GROUP
        distZipTask.conventionMapping.baseName = {
            if (project.distribution.name == null) {
                throw new GradleException("Distribution name must not be null! Check your configuration of the java-library-distribution plugin.")
            }
            extension.name
        }
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
