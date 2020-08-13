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

package gradlebuild.buildutils.tasks

import com.google.gson.GsonBuilder
import gradlebuild.buildutils.model.GradleSubproject
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File


abstract class GenerateSubprojectsInfo : DefaultTask() {

    private
    val subprojectsFolder = project.layout.projectDirectory.dir("subprojects")

    private
    val subprojectsJson = project.layout.projectDirectory.file(".teamcity/subprojects.json")

    @TaskAction
    fun generateSubprojectsInfo() {
        val subprojects: List<GradleSubproject> = subprojectsFolder.asFile.listFiles(File::isDirectory)!!
            .sorted()
            .map(this::generateSubproject)
        val gson = GsonBuilder().setPrettyPrinting().create()
        subprojectsJson.asFile.writeText(gson.toJson(subprojects))
    }

    private
    fun generateSubproject(subprojectDir: File): GradleSubproject {
        return GradleSubproject(subprojectDir.name,
            subprojectDir.name,
            subprojectDir.hasDescendantDir("src/test"),
            if (subprojectDir.name == "docs") true else subprojectDir.hasDescendantDir("src/integTest"),
            subprojectDir.hasDescendantDir("src/crossVersionTest"))
    }

    private
    fun File.hasDescendantDir(descendant: String) = resolve(descendant).isDirectory
}
