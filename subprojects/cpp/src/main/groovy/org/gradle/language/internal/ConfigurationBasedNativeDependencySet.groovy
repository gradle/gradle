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
package org.gradle.language.internal

import org.gradle.nativebinaries.NativeDependencySet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.artifacts.Configuration

class ConfigurationBasedNativeDependencySet implements NativeDependencySet {

    private final String baseName
    final String headersConfigurationName
    final String filesConfigurationName // files is a bad name
    final Project project
    private Task headerExtractionTask

    ConfigurationBasedNativeDependencySet(Project project, String baseName = "main") {
        this.baseName = baseName
        this.headersConfigurationName = baseName + "HeaderDependencies"
        this.filesConfigurationName = baseName + "FileDependencies"
        this.project = project

        createConfigurations()
        initHeaderExtractionTask()
    }

    private createConfigurations() {
        project.configurations.with {
            create(headersConfigurationName)
            create(filesConfigurationName)
        }
    }

    private initHeaderExtractionTask() {
        def headersConfiguration = getHeadersConfiguration()
        def dir = project.file("$project.buildDir/dependency-headers/$baseName")
        headerExtractionTask = project.task(baseName + "ExtractHeaders") {
            inputs.files headersConfiguration
            outputs.files { dir.listFiles() }
            doLast {
                headersConfiguration.each { headerZip ->
                    project.copy {
                        from project.zipTree(headerZip)
                        into "$dir/${headerZip.name - '.zip'}"
                    }
                }
            }
        }
    }

    Configuration getHeadersConfiguration() {
        project.configurations[headersConfigurationName]
    }

    FileCollection getIncludeRoots() {
        headerExtractionTask.outputs.files
    }

    FileCollection getLinkFiles() {
        project.configurations[filesConfigurationName]
    }

    FileCollection getRuntimeFiles() {
        return getLinkFiles()
    }

    void add(Map dep) {
        // hackity hack hack
        project.dependencies {
            def m = { classifier, ext -> [classifier: classifier, ext: ext] }
            delegate."$headersConfigurationName"(dep + m("headers", "zip"))
            delegate."$filesConfigurationName"(dep + m("so", "so"))
        }
    }
}
