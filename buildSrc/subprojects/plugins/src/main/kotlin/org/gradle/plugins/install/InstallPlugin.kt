/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.install

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File


private
const val installPathProperty = "gradle_installPath"


/**
 * Adds some validation and conventions for `Install` tasks.
 *
 * Each install task installs into `$gradle_installPath`
 */
class InstallPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("installation", InstallationExtension::class.java)
        val installDir = project.layout.projectDirectory.dir(project.provider { project.findProperty(installPathProperty)?.toString() })
        extension.installDirectory.set(installDir)

        val installTasks = project.tasks.withType(Install::class.java)
        installTasks.configureEach {
            into(extension.installDirectory)
        }
        project.gradle.taskGraph.whenReady {
            for (task in installTasks) {
                if (hasTask(task)) {
                    validateInstallDir(task, extension)
                }
            }
        }
    }

    private
    fun validateInstallDir(task: Install, extension: InstallationExtension) {
        val installDir = extension.installDirectory.asFile.orNull
        if (installDir == null) {
            throw RuntimeException("You can't install without setting the $installPathProperty property.")
        }
        if (installDir.isFile) {
            throw RuntimeException("Install directory $installDir does not look like a Gradle installation. Cannot delete it to install.")
        }
        if (installDir.isDirectory) {
            val libDir = File(installDir, "lib")
            if (!libDir.isDirectory || !libDir.list().any { it.matches(Regex("gradle.*\\.jar")) }) {
                throw RuntimeException("Install directory $installDir does not look like a Gradle installation. Cannot delete it to install.")
            }
        }
        task.into(installDir)
    }
}
