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
import gradlebuild.configcachereport.tasks.MergeReportAssets
import gradlebuild.configcachereport.tasks.VerifyDevWorkflow
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask

plugins {
    kotlin("js")
}

dependencies {
    compileOnly(kotlin("stdlib-js"))
}

val assembleReport by tasks.registering(MergeReportAssets::class) {
    htmlFile.set(webpackFile("configuration-cache-report.html"))
    logoFile.set(webpackFile("configuration-cache-report-logo.png"))
    cssFile.set(webpackFile("configuration-cache-report.css"))
    jsFile.set(webpackFile("configuration-cache-report.js"))
    outputFile.set(layout.buildDirectory.file("$name/configuration-cache-report.html"))
}

tasks.assemble {
    dependsOn(assembleReport)
}

configurations.create("configurationCacheReport") {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("configuration-cache-report"))
    }
    outgoing.artifact(assembleReport)
}

val stageDir = layout.buildDirectory.dir("stageDevReport")

val stageDevReport by tasks.registering(Sync::class) {
    from(assembleReport)
    from("src/test/resources")
    into(stageDir)
}

val verifyDevWorkflow by tasks.registering(VerifyDevWorkflow::class) {
    stageDirectory.set(stageDevReport.map { projectDir(it.destinationDir) })
}

tasks.named("test") {
    dependsOn(verifyDevWorkflow)
}

fun projectFile(file: File) =
    layout.projectDirectory.file(file.absolutePath)

fun projectDir(dir: File) =
    layout.projectDirectory.dir(dir.absolutePath)

fun webpackFile(fileName: String) =
    tasks.named("browserProductionWebpack").map {
        projectFile((it as KotlinWebpack).destinationDirectory.resolve(fileName))
    }

tasks.named<KtLintCheckTask>("runKtlintCheckOverKotlinScripts") {
    // Only check the build files, not all *.kts files in the project
    setIncludes(listOf("*.gradle.kts"))
}
