/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.buildscan.tasks

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File


@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class AbstractExtractCodeQualityBuildScanData : DefaultTask() {

    @get:Internal
    internal
    abstract val xmlOutputs: ConfigurableFileCollection

    @get:Internal
    internal
    abstract val rootDir: DirectoryProperty

    @get:Internal
    internal
    var buildScanExt: BuildScanExtension? = null

    init {
        doNotTrackState("Adds build scan values")
    }

    @TaskAction
    fun action() {
        if (buildScanExt == null) return
        val basePath = rootDir.get().asFile
        xmlOutputs.files.filter { it.exists() }.forEach { xmlFile ->
            extractIssuesFrom(xmlFile, basePath).forEach { issue ->
                buildScanExt?.value(buildScanValueName, issue)
            }
        }
    }

    @get:Internal
    protected
    abstract val buildScanValueName: String

    protected
    abstract fun extractIssuesFrom(xmlFile: File, basePath: File): List<String>
}


@DisableCachingByDefault(because = "Does not produce cacheable outputs")
abstract class ExtractCheckstyleBuildScanData : AbstractExtractCodeQualityBuildScanData() {

    override val buildScanValueName: String = "Checkstyle Issue"

    override fun extractIssuesFrom(xmlFile: File, basePath: File): List<String> {
        val checkstyle = Jsoup.parse(xmlFile.readText(), "", Parser.xmlParser())
        return checkstyle.getElementsByTag("file").flatMap { file ->
            file.getElementsByTag("error").map { error ->
                val filePath = File(file.attr("name")).relativeTo(basePath).path
                "$filePath:${error.attr("line")}:${error.attr("column")} \u2192 ${error.attr("message")}"
            }
        }
    }
}


@DisableCachingByDefault(because = "Does not produce cacheable outputs")
abstract class ExtractCodeNarcBuildScanData : AbstractExtractCodeQualityBuildScanData() {

    override val buildScanValueName: String = "CodeNarc Issue"

    override fun extractIssuesFrom(xmlFile: File, basePath: File): List<String> {
        val codenarc = Jsoup.parse(xmlFile.readText(), "", Parser.xmlParser())
        return codenarc.getElementsByTag("Package").flatMap { codenarcPackage ->
            val packagePath = codenarcPackage.attr("path")
            codenarcPackage.getElementsByTag("File").flatMap { file ->
                val fileName = file.attr("name")
                file.getElementsByTag("Violation").map { violation ->
                    val filePath = "$packagePath/$fileName"
                    val message = violation.run {
                        getElementsByTag("Message").first()
                            ?: getElementsByTag("SourceLine").first()
                    }
                    "$filePath:${violation.attr("lineNumber")} \u2192 ${message.text()}"
                }
            }
        }
    }
}
