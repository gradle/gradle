/*
 * Copyright 2026 the original author or authors.
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

import gradlebuild.basics.accessors.kotlinMainSourceSet
import gradlebuild.basics.capitalize
import gradlebuild.removal.tasks.Gradle10RemovalReportTask

plugins {
    java
    groovy
}

val reportTask = tasks.register<Gradle10RemovalReportTask>("gradle10RemovalReport") {
    group = "verification"
    description = "Generates a report of deprecations scheduled for removal / to become an error in Gradle 10"
    title = project.name
    sources.from(sourceSets.main.get().java.sourceDirectories)
    htmlReportFile = file(layout.buildDirectory.file("reports/removal/${project.name}.html"))
    textReportFile = file(layout.buildDirectory.file("reports/removal/${project.name}.txt"))
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    reportTask {
        sources.from(kotlinMainSourceSet.sourceDirectories)
    }
}

// Intentionally NOT wired into `check`: this report is informational, not a quality gate (see plan, decision #2).

consumableVariant("txt", reportTask.flatMap { it.textReportFile })
consumableVariant("html", reportTask.flatMap { it.htmlReportFile })

fun consumableVariant(reportType: String, artifact: Provider<RegularFile>) = configurations.create("gradle10RemovalReport${reportType.capitalize()}") {
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle10-removal-report-$reportType"))
    }
    extendsFrom(configurations.implementation.get())
    outgoing.artifact(artifact)
}
