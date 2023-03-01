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

import gradlebuild.basics.accessors.groovy
import gradlebuild.basics.accessors.kotlin
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.basics.repoRoot
import gradlebuild.capitalize
import gradlebuild.incubation.tasks.IncubatingApiReportTask

plugins {
    java
}

val reportTask = tasks.register<IncubatingApiReportTask>("incubationReport") {
    group = "verification"
    description = "Generates a report of incubating APIS"
    title = project.name
    versionFile = repoRoot().file("version.txt")
    releasedVersionsFile = releasedVersionsFile()
    sources.from(sourceSets.main.get().java.sourceDirectories)
    sources.from(sourceSets.main.get().groovy.sourceDirectories)
    htmlReportFile = file(layout.buildDirectory.file("reports/incubation/${project.name}.html"))
    textReportFile = file(layout.buildDirectory.file("reports/incubation/${project.name}.txt"))
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    reportTask {
        sources.from(sourceSets.main.get().kotlin.sourceDirectories)
    }
}

tasks.named("check") { dependsOn(reportTask) }

consumableVariant("txt", reportTask.flatMap { it.textReportFile })
consumableVariant("html", reportTask.flatMap { it.htmlReportFile })

fun consumableVariant(reportType: String, artifact: Provider<RegularFile>) = configurations.create("incubatingReport${reportType.capitalize()}") {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("incubation-report-$reportType"))
    }
    extendsFrom(configurations.implementation.get())
    outgoing.artifact(artifact)
}
