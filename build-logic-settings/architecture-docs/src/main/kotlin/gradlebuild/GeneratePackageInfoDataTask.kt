/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild

import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GeneratePackageInfoDataTask : DefaultTask() {

    companion object {
        val packageLineRegex = Regex("""package\s*([^;\s]+)\s*;""")

        fun findPackageInfoFiles(objects: ObjectFactory, projectBaseDirs: Provider<List<File>>): FileCollection {
            return objects.fileCollection().from(projectBaseDirs.map {
                it.flatMap { projectDir -> listOf(File(projectDir, "src/main/java"), File(projectDir, "src/main/groovy")) }
            }).asFileTree.matching {
                include("**/package-info.java")
            }.filter {
                it.isFile
            }
        }
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageInfoFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val baseDir = project.layout.settingsDirectory.asFile

    @TaskAction
    fun action() {
        val results = mutableListOf<Pair<String, String>>()

        for (packageInfoFile in packageInfoFiles.files) {
            val packageLine = packageInfoFile.useLines { lines -> lines.first { it.startsWith("package") } }
            val packageName = packageLineRegex.find(packageLine)!!.groupValues[1]
            results.add(packageName to packageInfoFile.relativeTo(baseDir).path)
        }

        val outputData = results.groupBy(keySelector = { it.first }, valueTransform = { it.second })
        outputFile.get().asFile.writeText(Gson().toJson(outputData))
    }

}
