/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.build

import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File


class GenerateSubprojectsInfoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("generateSubprojectsInfo", GenerateSubprojectsInfoTask::class.java)
    }
}


open class GenerateSubprojectsInfoTask : DefaultTask() {
    @TaskAction
    fun generateSubprojectsInfo() {
        val subprojects: List<GradleSubproject> = project.rootDir.resolve("subprojects").listFiles(File::isDirectory).map(this::generateSubproject)
        val gson = GsonBuilder().setPrettyPrinting().create()
        project.rootDir.resolve(".teamcity/subprojects.json").writeText(gson.toJson(subprojects))
    }

    private
    fun generateSubproject(subprojectDir: File): GradleSubproject {
        return GradleSubproject(subprojectDir.name,
            subprojectDir.name.kebabToCamel(),
            subprojectDir.hasDescendantDir("src/test"),
            subprojectDir.hasDescendantDir("src/integTest"),
            subprojectDir.hasDescendantDir("src/crossVersionTest"))
    }
}


fun File.hasDescendantDir(descendant: String) = resolve(descendant).isDirectory


fun String.kebabToCamel() = split("-").map { it.capitalize() }.joinToString("").decapitalize()


data class GradleSubproject(val dirName: String, val name: String, val unitTests: Boolean, val functionalTests: Boolean, val crossVersionTests: Boolean)
