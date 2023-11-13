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
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import java.io.File


@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class SubprojectsInfo : DefaultTask() {

    private
    val rootPath = project.layout.projectDirectory.asFile.toPath()

    private
    val platformsFolder = project.layout.projectDirectory.dir("platforms")

    private
    val subprojectsFolder = project.layout.projectDirectory.dir("subprojects")

    @get:Internal
    protected
    val subprojectsJson = project.layout.projectDirectory.file(".teamcity/subprojects.json")

    protected
    fun generateSubprojectsJson(): String {
        val subprojects = generateSubprojects()
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(subprojects) + '\n'
    }

    private
    fun generateSubprojectsDirectories(): List<File> {
        val subprojectRoots = platformsFolder.asFile.listFiles(File::isDirectory).plus(platformsFolder.asFile).plus(subprojectsFolder.asFile)
        return subprojectRoots.map { it.listFiles(File::isDirectory).asList() }.flatten()
    }

    private
    fun generateSubprojects(): List<GradleSubproject> {
        return generateSubprojectsDirectories()
            .filter {
                File(it, "build.gradle.kts").exists() ||
                    File(it, "build.gradle").exists()
            }
            .sortedBy { it.name }
            .map(this::generateSubproject)
    }


    private
    fun generateSubproject(subprojectDir: File): GradleSubproject {
        return GradleSubproject(
            subprojectDir.name,
            rootPath.relativize(subprojectDir.toPath()).toString(),
            subprojectDir.hasDescendantDir("src/test"),
            subprojectDir.hasDescendantDir("src/integTest"),
            subprojectDir.hasDescendantDir("src/crossVersionTest")
        )
    }

    private
    fun File.hasDescendantDir(descendant: String) = resolve(descendant).isDirectory
}
