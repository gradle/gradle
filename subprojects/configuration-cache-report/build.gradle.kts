/*
 * Copyright 2019 the original author or authors.
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
import java.util.Base64

plugins {
    id("gradlebuild.internal.kotlin-js")
}

dependencies {
    compileOnly(kotlin("stdlib-js"))
}

tasks {

    compileKotlinJs {
        kotlinOptions {
            metaInfo = false
            moduleKind = "plain"
        }
    }

    val unpackKotlinJsStdlib by registering(Copy::class) {
        group = "build"
        description = "Unpacks the Kotlin JavaScript standard library"

        val kotlinStdLibJsJar = configurations.named("compileClasspath").map { compileClasspath ->
            val kotlinStdlibJsJarRegex = Regex("kotlin-stdlib-js-.+\\.jar")
            compileClasspath.single { file -> file.name.matches(kotlinStdlibJsJarRegex) }
        }

        from(kotlinStdLibJsJar.map(::zipTree)) {
            include("**/*.js")
            exclude("META-INF/**")
        }

        into("$buildDir/$name")

        includeEmptyDirs = false
    }

    val assembleReport by registering(MergeReportAssets::class) {
        htmlFile.set(browserProductionWebpack.map { layout.projectDirectory.file(it.destinationDirectory.resolve("configuration-cache-report.html").absolutePath) })
        logoFile.set(browserProductionWebpack.map { layout.projectDirectory.file(it.destinationDirectory.resolve("configuration-cache-report-logo.png").absolutePath) })
        cssFile.set(browserProductionWebpack.map { layout.projectDirectory.file(it.destinationDirectory.resolve("configuration-cache-report.css").absolutePath) })
        jsFile.set(browserProductionWebpack.map { layout.projectDirectory.file(it.destinationDirectory.resolve("configuration-cache-report.js").absolutePath) })
        kotlinJs.set(unpackKotlinJsStdlib.map { layout.projectDirectory.file(it.destinationDir.resolve("kotlin.js").absolutePath) })
        outputFile.set(layout.buildDirectory.file("$name/configuration-cache-report.html"))
    }

    assemble {
        dependsOn(assembleReport)
    }

    val stageDir = layout.buildDirectory.dir(name)

    val stageDevReport by registering(Sync::class) {
        from(assembleReport)
        from("src/test/resources")
        into(stageDir)
    }

    val verifyDevWorkflow by registering {
        dependsOn(stageDevReport)
        doLast {
            stageDir.get().asFile.let { stage ->
                val stagedFiles = stage.listFiles()
                val expected = listOf(
                    stage.resolve("configuration-cache-report.html"),
                    stage.resolve("configuration-cache-report-data.js")
                )
                require(stagedFiles == expected) {
                    "Unexpected staged files, found ${stagedFiles.map { it.relativeTo(stage).path }}"
                }
            }
        }
    }

    test {
        dependsOn(verifyDevWorkflow)
    }
}

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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val kotlinJs: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun action() {
        outputFile.get().asFile.writeText(
            htmlFile.get().asFile.readText().also {
                require(it.contains(cssTag))
                require(it.contains(kotlinJsTag))
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
                kotlinJsTag, """
                <script type="text/javascript">
                ${kotlinJs.get().asFile.readText()}
                </script>
                """.trimIndent()
            ).replace(
                jsTag, """
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
    val kotlinJsTag = """<script type="text/javascript" src="kotlin.js"></script>"""

    private
    val jsTag = """<script type="text/javascript" src="configuration-cache-report.js"></script>"""

    private
    val logoStyle = """background-image: url("configuration-cache-report-logo.png");"""

    private
    fun File.base64Encode() =
        Base64.getEncoder().encodeToString(readBytes())
}
