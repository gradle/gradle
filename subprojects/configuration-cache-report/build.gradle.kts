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

        fun projectFile(f: File) =
            layout.projectDirectory.file(f.absolutePath)

        fun webpackFile(fileName: String) =
            browserProductionWebpack.map {
                projectFile(it.destinationDirectory.resolve(fileName))
            }

        htmlFile.set(webpackFile("configuration-cache-report.html"))
        logoFile.set(webpackFile("configuration-cache-report-logo.png"))
        cssFile.set(webpackFile("configuration-cache-report.css"))
        jsFile.set(webpackFile("configuration-cache-report.js"))
        kotlinJs.set(unpackKotlinJsStdlib.map { projectFile(it.destinationDir.resolve("kotlin.js")) })
        outputFile.set(layout.buildDirectory.file("$name/configuration-cache-report.html"))
    }

    assemble {
        dependsOn(assembleReport)
    }

    val stageDir = layout.buildDirectory.dir("stageDevReport")

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
                val expected = setOf(
                    stage.resolve("configuration-cache-report.html"),
                    stage.resolve("configuration-cache-report-data.js")
                )
                require(stagedFiles.toSet() == expected) {
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
                kotlinJsTag,
                """
                <script type="text/javascript">
                ${kotlinJs.get().asFile.readText()}
                </script>
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
    val kotlinJsTag = """<script type="text/javascript" src="kotlin.js"></script>"""

    private
    val jsTag = """<script type="text/javascript" src="configuration-cache-report.js"></script>"""

    private
    val logoStyle = """background-image: url("configuration-cache-report-logo.png");"""

    private
    fun File.base64Encode() =
        Base64.getEncoder().encodeToString(readBytes())
}
