package org.gradle.gradlebuild.packaging.shading

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

open class CreateShadedJar : DefaultTask() {
    @InputFiles
    lateinit var relocatedClassesConfiguration: Configuration
    @InputFiles
    lateinit var classTreesConfiguration: Configuration
    @InputFiles
    lateinit var entryPointsConfiguration: Configuration

    @TaskAction
    fun shade() {
        val tmpDir = temporaryDir
        val entryPoints = entryPointsConfiguration.files.flatMap { readJson<List<String>>(it) }
        val classTrees = classTreesConfiguration.files.flatMap { readJson<Map<String, List<String>>>(it).entries }
            .groupingBy { it.key }
            .aggregate { _, accumulator: Set<String>?, element: Map.Entry<String, List<String>>, first ->
                if (first) {
                    element.value.toSet()
                } else {
                    accumulator!!.union(element.value)
                }
            }

//        println(classTrees)
//        println(entryPoints)
        val allClassesDir = tmpDir.resolve("classes")
        relocatedClassesConfiguration.files.forEach { classesDir ->
              classesDir.copyRecursively(allClassesDir, overwrite = true)
          }
    }

    private inline fun <reified T> readJson(file: File) =
        file.bufferedReader().use { reader ->
            Gson().fromJson<T>(reader, object : TypeToken<T>() {}.type)
        }
}
