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

import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("java-library")
    id("org.gradle.kotlin.kotlin-dsl") // this is 'kotlin-dsl' without version
    id("gradlebuild.code-quality")
    id("org.gradle.kotlin-dsl.ktlint-convention")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(platform(project(":build-platform")))
    implementation("gradlebuild:code-quality")

    testImplementation("org.junit.vintage:junit-vintage-engine")
}

ktlint {
    filter {
        exclude("gradle/kotlin/dsl/accessors/_*/**")
    }
}

tasks.ktlintKotlinScriptCheck {
    // Only check the build files, not all *.kts files in the project
    setSource(files("build.gradle.kts", "settings.gradle.kts"))
}

tasks.validatePlugins {
    failOnWarning.set(true)
    enableStricterValidation.set(true)
}


val isCiServer = "CI" in System.getenv()

if (isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
    gradle.buildFinished {
        failedTasks().forEach { prepareReportForCIPublishing(it.reports["html"].destination) }
    }
}

fun failedTasks() = gradle.taskGraph.allTasks.filter {
    it.project == project && it is Reporting<*> && it.state.failure != null
}.map { it as Reporting<*> }

fun zip(destZip: File, srcDir: File) {
    destZip.parentFile.mkdirs()
    ZipOutputStream(FileOutputStream(destZip), StandardCharsets.UTF_8).use { zipOutput ->
        val srcPath = srcDir.toPath()
        Files.walk(srcPath).use { paths ->
            paths
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .forEach { path ->
                    val zipEntry = ZipEntry(srcPath.relativize(path).toString())
                    zipOutput.putNextEntry(zipEntry)
                    Files.copy(path, zipOutput)
                    zipOutput.closeEntry()
                }
        }
    }
}

fun prepareReportForCIPublishing(report: File) {
    if (report.isDirectory) {
        val destFile = layout.buildDirectory.file("report-${project.name}-${report.name}.zip").get().asFile
        zip(destFile, report)
    } else {
        copy {
            from(report)
            into(layout.buildDirectory)
            rename { "report-${project.name}-${report.parentFile.name}-${report.name}" }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
