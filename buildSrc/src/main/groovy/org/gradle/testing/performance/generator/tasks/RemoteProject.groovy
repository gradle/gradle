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
package org.gradle.testing.performance.generator.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem

class RemoteProject extends DefaultTask {
    @Input String remoteUri
    @Input String branch
    @OutputDirectory File outputDirectory = project.file("$project.buildDir/$name")

    @TaskAction
    void checkout() {
        outputDirectory.deleteDir()
        project.exec {
            commandLine = ["git", "clone", "--depth", "1", "--branch", branch, remoteUri, outputDirectory.absolutePath]
            if (OperatingSystem.current().windows) {
                commandLine = ["cmd", "/c"] + commandLine
            }
        }
        def perfTesting = project.project(':internalPerformanceTesting')
        copyInitScript(perfTesting)
        applyMeasurementPlugin(perfTesting)
    }

    private File copyInitScript(Project perfTesting) {
        new File(outputDirectory, "init.gradle") << perfTesting.file("src/templates/init.gradle").text
    }

    private void applyMeasurementPlugin(Project perfTesting) {
        def buildFile = new File(outputDirectory, "build.gradle")
        String measurementPluginConfiguration = """buildscript {
    dependencies {
        classpath files("${perfTesting.buildDir}/libs/measurement-plugin.jar")
    }
}

apply plugin: org.gradle.performance.plugin.MeasurementPlugin

"""
        if (buildFile.exists()) {
            buildFile.text = """$measurementPluginConfiguration
$buildFile.text"""
        } else {
            buildFile.text = measurementPluginConfiguration
        }
    }
}
