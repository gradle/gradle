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

import gradlebuild.basics.buildCommitId
import gradlebuild.basics.capitalize
import gradlebuild.removal.tasks.Gradle10RemovalAggregateReportTask

plugins {
    id("base")
}

// Distinct config name from the incubation aggregation plugin ("reports"), since both plugins are
// applied to the same project.
val gradle10RemovalReports = configurations.create("gradle10RemovalReports") {
    isCanBeResolved = false
    isCanBeConsumed = false
    description = "Dependencies to aggregate Gradle 10 removal reports from"
}

val allGradle10RemovalReports = tasks.register<Gradle10RemovalAggregateReportTask>("allGradle10RemovalReports") {
    group = "verification"
    reports.from(resolver("txt"))
    htmlReportFile = project.layout.buildDirectory.file("reports/removal/all-gradle10-removals.html")
    csvReportFile = project.layout.buildDirectory.file("reports/removal/all-gradle10-removals.csv")
    currentCommit = project.buildCommitId
}

tasks.register<Zip>("allGradle10RemovalReportsZip") {
    group = "verification"
    destinationDirectory = layout.buildDirectory.dir("reports/removal")
    archiveBaseName = "gradle10-removals"
    from(allGradle10RemovalReports.get().htmlReportFile)
    from(allGradle10RemovalReports.get().csvReportFile)
    from(resolver("html"))
}

fun resolver(reportType: String) = configurations.create("gradle10RemovalReport${reportType.capitalize()}Path") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle10-removal-report-$reportType"))
    }
    extendsFrom(gradle10RemovalReports)
}.incoming.artifactView { lenient(true) }.files
