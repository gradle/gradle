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

package gradlebuild.configcachereport.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Base64


@CacheableTask
abstract class MergeReportAssets : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val htmlFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val logoFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cssFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jsFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun action() {
        outputFile.get().asFile.writeText(
            htmlFile.get().asFile.readText().also {
                require(it.contains(cssTag))
                require(it.contains(jsTag))
            }.replace(
                cssTag,
                """
                <style type="text/css">
                ${cssFile.get().asFile.readText().also {
                    require(it.contains(logoStyle))
                }.replace(
                    logoStyle,
                    """background-image: url("data:image/png;base64,${logoFile.get().asFile.base64Encode()}");"""
                )}
                </style>
                """.trimIndent()
            ).replace(
                jsTag,
                """
                <script type="text/javascript">
                ${jsFile.get().asFile.readText()}
                </script>
                """.trimIndent()
            )
        )
    }

    private
    val cssTag = """<link rel="stylesheet" href="./configuration-cache-report.css">"""

    private
    val jsTag = """<script type="text/javascript" src="configuration-cache-report.js"></script>"""

    private
    val logoStyle = """background-image: url("configuration-cache-report-logo.png");"""

    private
    fun File.base64Encode() =
        Base64.getEncoder().encodeToString(readBytes())
}
