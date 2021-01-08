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
package gradlebuild.classycle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
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
abstract class Classycle : DefaultTask() {

    @get:Internal
    abstract val classesDirs: ConfigurableFileCollection

    @get:Input
    abstract val excludePatterns: ListProperty<String>

    @get:Input
    abstract val reportName: Property<String>

    @get:Internal
    abstract val reportDir: DirectoryProperty

    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val reportResourcesZip: ConfigurableFileCollection

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val existingClassesDir: FileCollection
        get() = classesDirs.filter(File::exists)

    @get:OutputFile
    val reportFile
        get() = reportName.map { reportDir.file("${it}.txt").get() }

    private
    val analysisFile
        get() = reportName.map { reportDir.file("${it}_analysis.xml").get() }

    @get:Inject
    protected
    abstract val antBuilder: IsolatedAntBuilder

    @TaskAction
    fun generate() = project.run {
        val classesDirs = existingClassesDir
        val classpath = configurations["classycle"].files
        reportFile.get().asFile.parentFile.mkdirs()
        antBuilder.withClasspath(classpath).execute(
            closureOf<AntBuilderDelegate> {
                ant.withGroovyBuilder {
                    "taskdef"(
                        "name" to "classycleDependencyCheck",
                        "classname" to "classycle.ant.DependencyCheckingTask"
                    )
                    "taskdef"(
                        "name" to "classycleReport",
                        "classname" to "classycle.ant.ReportTask"
                    )
                    try {
                        "classycleDependencyCheck"(
                            mapOf(
                                "reportFile" to reportFile.get().asFile,
                                "failOnUnwantedDependencies" to true,
                                "mergeInnerClasses" to true
                            ),
                            """
                            show allResults
                            check absenceOfPackageCycles > 1 in org.gradle.*
                            """
                        ) {

                            withFilesetOf(classesDirs, excludePatterns.get())
                        }
                    } catch (ex: Exception) {
                        try {
                            "unzip"(
                                "src" to reportResourcesZip.asFileTree.filter { it.name == "classycle_report_resources.zip" }.singleFile,
                                "dest" to reportDir.get().asFile,
                            )
                            "classycleReport"(
                                "reportFile" to analysisFile.get().asFile,
                                "reportType" to "xml",
                                "mergeInnerClasses" to true,
                                "title" to "$name $reportName ($path)"
                            ) {

                                withFilesetOf(classesDirs, excludePatterns.get())
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                        throw RuntimeException(
                            "Classycle check failed: $ex.message. " +
                                "See failure report at ${clickableUrl(reportFile)} and analysis report at ${clickableUrl(analysisFile)}",
                            ex
                        )
                    }
                }
            }
        )
    }
}


private
fun GroovyBuilderScope.withFilesetOf(classesDirs: FileCollection, excludePatterns: List<String>) =
    classesDirs.forEach { classesDir ->
        "fileset"("dir" to classesDir) {
            excludePatterns.forEach { excludePattern ->
                "exclude"("name" to excludePattern)
            }
            "exclude"("name" to "META-INF/**")
        }
    }


private
fun clickableUrl(file: Provider<RegularFile>) =
    URI("file", "", file.get().asFile.toURI().path, null, null).toString()
