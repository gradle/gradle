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
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
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
open class Classycle @Inject constructor(
    @get:Inject open val antBuilder: IsolatedAntBuilder,

    @get:Classpath val classycleClasspath: FileCollection,

    @get:Input val excludePatterns: Provider<List<String>>,
    @get:Input val reportName: String,

    classesDirs: FileCollection,
    defaultReportResourcesZip: RegularFileProperty,
    private val defaultReportDirectory: Provider<Directory>,
    defaultReportFile: Provider<RegularFile>,
    defaultAnalysisFile: Provider<RegularFile>
) : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val reportResourcesZip: Provider<RegularFile> = newInputFile()

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val existingClassesDir: FileCollection

    @get:OutputFile
    val reportFile: Provider<RegularFile> = newOutputFile()

    @get:OutputFile
    val analysisFile: Provider<RegularFile> = newOutputFile()

    init {
        (reportResourcesZip as RegularFileProperty).set(defaultReportResourcesZip)
        existingClassesDir = classesDirs.filter(File::exists)
        (reportFile as RegularFileProperty).set(defaultReportFile)
        (analysisFile as RegularFileProperty).set(defaultAnalysisFile)
    }

    @TaskAction
    fun generate() = project.run {
        val classesDirs = existingClassesDir
        antBuilder.withClasspath(classycleClasspath).execute(closureOf<AntBuilderDelegate> {
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
                            "reportFile" to reportFile.get().asFile,
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
                            "src" to reportResourcesZip.get().asFile,
                            "dest" to defaultReportDirectory.get().asFile)
                        "classycleReport"(
                            "reportFile" to analysisFile.get().asFile,
                            "reportType" to "xml",
                            "mergeInnerClasses" to true,
                            "title" to "$name $reportName ($path)") {

                            withFilesetOf(classesDirs, excludePatterns.get())
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                    throw RuntimeException("Classycle check failed: $ex.message. " +
                        "See failure report at ${clickableUrl(reportFile.get().asFile)} and analysis report at ${clickableUrl(analysisFile.get().asFile)}", ex)
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
