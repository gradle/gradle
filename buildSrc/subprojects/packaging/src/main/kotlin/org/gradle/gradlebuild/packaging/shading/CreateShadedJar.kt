package org.gradle.gradlebuild.packaging.shading

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.system.measureTimeMillis

open class CreateShadedJar : DefaultTask() {
    @InputFiles
    lateinit var relocatedClassesConfiguration: FileCollection
    @InputFiles
    lateinit var classTreesConfiguration: FileCollection
    @InputFiles
    lateinit var entryPointsConfiguration: FileCollection

    val jarFile = newOutputFile()

    @TaskAction
    fun shade() {
        val tmpDir = temporaryDir
            val entryPoints = entryPointsConfiguration.files.flatMap { readJson<List<String>>(it) }
            val classTrees = classTreesConfiguration.files.flatMap { readJson<Map<String, List<String>>>(it).entries }
                .groupingBy { it.key }
                .aggregate<Map.Entry<String, List<String>>, String, Set<String>> { _, accumulator: Set<String>?, element: Map.Entry<String, List<String>>, first ->
                    if (first) {
                        element.value.toSet()
                    } else {
                        accumulator!!.union(element.value)
                    }
                }

        val classesToInclude = mutableSetOf<String>()

        val queue: Queue<String> = ArrayDeque<String>()
        queue.addAll(entryPoints)
        while (!queue.isEmpty()) {
            val className = queue.remove()
            if (!classesToInclude.add(className)) {
                queue.addAll(classTrees.getOrDefault(className, emptySet()))
            }
        }
//        JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile.get().asFile))).use { jarOutputStream ->
//            if (classes.manifest != null) {
//                addJarEntry(classes.manifest!!.resourceName, classes.manifest!!.sourceFile, jarOutputStream)
//            }
            val visited = linkedSetOf<ClassDetails>()
//            relocatedClassesConfiguration.files.forEach { classesDir ->
//                classesDir.con
//                classesDir.copyRecursively(allClassesDir, overwrite = true)
//            }
//            for (classDetails in classes.entryPoints) {
//                visitTree(classDetails, classesDir, jarOutputStream, writer, "- ", visited)
//            }
//            for (resource in classes.resources) {
//                addJarEntry(resource.resourceName, resource.sourceFile, jarOutputStream)
//            }
//        }

//        println(classTrees)
//        println(entryPoints)
        val allClassesDir = tmpDir.resolve("classes")
        val copying = measureTimeMillis {
            relocatedClassesConfiguration.files.forEach { classesDir ->
                classesDir.walk().filter { it.isFile }.forEach { println(it) }
//                classesDir.copyRecursively(allClassesDir, overwrite = true)
            }
        }
        println("Copying took $copying ms")
    }

    private
    fun addJarEntry(entryName: String, sourceFile: File, jarOutputStream: JarOutputStream) {
        jarOutputStream.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(sourceFile)).use { inputStream -> inputStream.copyTo(jarOutputStream) }
        jarOutputStream.closeEntry()
    }

    private
    inline fun <reified T> readJson(file: File) =
        file.bufferedReader().use { reader ->
            Gson().fromJson<T>(reader, object : TypeToken<T>() {}.type)
        }
}
