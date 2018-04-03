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
package org.gradle.gradlebuild.buildquality.classycle

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.*

import java.io.File
import java.net.URI
import javax.inject.Inject


@CacheableTask
open class Classycle : DefaultTask() {

    @get:Internal
    lateinit var classesDirs: FileCollection

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val existingClassesDir: FileCollection
        get() = classesDirs.filter(File::exists)

    @get:Input
    val excludePatterns: ListProperty<String> = project.objects.listProperty()

    @get:Input
    lateinit var reportName: String

    @get:Internal
    lateinit var reportDir: File

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val reportResourcesZip: RegularFileProperty = newInputFile()

    @get:OutputFile
    val reportFile
        get() = File(reportDir, "$reportName.txt")

    private
    val analysisFile: File
        get() = File(reportDir, "${reportName}_analysis.xml")


    @get:Inject
    protected
    open val antBuilder: IsolatedAntBuilder
        get() = throw UnsupportedOperationException()


    @TaskAction
    fun generate() = project.run {
        val classesDirs = existingClassesDir
        val classpath = configurations[classycleBaseName].files
        reportFile.parentFile.mkdirs()
        antBuilder.withClasspath(classpath).execute(closureOf<AntBuilderDelegate> {
            ant.withGroovyBuilder {
                "taskdef"(
                    "name" to "classycleDependencyCheck",
                    "classname" to "classycle.ant.DependencyCheckingTask")
                "taskdef"(
                    "name" to "classycleReport",
                    "classname" to "classycle.ant.ReportTask")
                try {
                    "classycleDependencyCheck"(
                        mapOf(
                            "reportFile" to reportFile,
                            "failOnUnwantedDependencies" to true,
                            "mergeInnerClasses" to true),
                        """
                            show allResults
                            check absenceOfPackageCycles > 1 in org.gradle.*
                        """) {

                        withFilesetOf(classesDirs, excludePatterns.get())
                    }
                } catch (ex: Exception) {
                    try {
                        "unzip"(
                            "src" to reportResourcesZip.asFile.get(),
                            "dest" to reportDir)
                        "classycleReport"(
                            "reportFile" to analysisFile,
                            "reportType" to "xml",
                            "mergeInnerClasses" to true,
                            "title" to "$name $reportName ($path)") {

                            withFilesetOf(classesDirs, excludePatterns.get())
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                    throw RuntimeException("Classycle check failed: $ex.message. " +
                        "See failure report at ${clickableUrl(reportFile)} and analysis report at ${clickableUrl(analysisFile)}", ex)
                }
            }
        })
    }
}


private
fun GroovyBuilderScope.withFilesetOf(classesDirs: FileCollection, excludePatterns: List<String>) =
    classesDirs.forEach { classesDir ->
        "fileset"("dir" to classesDir) {
            excludePatterns.forEach { excludePattern ->
                "exclude"("name" to excludePattern)
            }
        }
    }


private
fun clickableUrl(file: File) =
    URI("file", "", file.toURI().path, null, null).toString()
