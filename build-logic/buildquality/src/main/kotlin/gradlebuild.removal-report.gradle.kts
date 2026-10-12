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
import gradlebuild.basics.repoRoot
import gradlebuild.removal.action.nextMajorGradleVersion
import gradlebuild.removal.tasks.NextMajorRemovalReportTask

plugins {
    java
    groovy
}

// The report targets the next major version, derived from the current version (e.g. 9.7.0 -> 10).
// Once the build moves to 10.x this becomes 11 with no code change.
val nextMajor = providers.fileContents(repoRoot().file("version.txt")).asText.map { nextMajorGradleVersion(it) }

val reportTask = tasks.register<NextMajorRemovalReportTask>("nextMajorRemovalReport") {
    group = "verification"
    description = "Generates a report of deprecations scheduled for removal / to become an error in the next major Gradle version"
    title = project.name
    targetMajorVersion = nextMajor
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

fun consumableVariant(reportType: String, artifact: Provider<RegularFile>) = configurations.create("nextMajorRemovalReport${reportType.capitalize()}") {
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("next-major-removal-report-$reportType"))
    }
    extendsFrom(configurations.implementation.get())
    outgoing.artifact(artifact)
}
