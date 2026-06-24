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

import gradlebuild.basics.capitalize
import gradlebuild.basics.repoRoot
import gradlebuild.removal.action.UpstreamCommitValueSource
import gradlebuild.removal.action.nextMajorGradleVersion
import gradlebuild.removal.tasks.NextMajorRemovalAggregateReportTask

plugins {
    id("base")
}

// The report targets the next major version, derived from the current version (e.g. 9.7.0 -> 10).
val nextMajor = providers.fileContents(repoRoot().file("version.txt")).asText.map { nextMajorGradleVersion(it) }

// A commit known to exist on gradle/gradle, so source links don't 404 for local-only branch commits.
val sourceCommit = providers.of(UpstreamCommitValueSource::class) {
    parameters.workingDir = repoRoot()
}

// Distinct config name from the incubation aggregation plugin ("reports"), since both plugins are
// applied to the same project.
val nextMajorRemovalReports = configurations.create("nextMajorRemovalReports") {
    isCanBeResolved = false
    isCanBeConsumed = false
    description = "Dependencies to aggregate next-major removal reports from"
}

val allNextMajorRemovalReports = tasks.register<NextMajorRemovalAggregateReportTask>("allNextMajorRemovalReports") {
    group = "verification"
    reports.from(resolver("txt"))
    htmlReportFile = project.layout.buildDirectory.file("reports/removal/all-next-major-removals.html")
    csvReportFile = project.layout.buildDirectory.file("reports/removal/all-next-major-removals.csv")
    currentCommit = sourceCommit
    targetMajorVersion = nextMajor
}

tasks.register<Zip>("allNextMajorRemovalReportsZip") {
    group = "verification"
    destinationDirectory = layout.buildDirectory.dir("reports/removal")
    archiveBaseName = "next-major-removals"
    from(allNextMajorRemovalReports.get().htmlReportFile)
    from(allNextMajorRemovalReports.get().csvReportFile)
    from(resolver("html"))
}

fun resolver(reportType: String) = configurations.create("nextMajorRemovalReport${reportType.capitalize()}Path") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("next-major-removal-report-$reportType"))
    }
    extendsFrom(nextMajorRemovalReports)
}.incoming.artifactView { lenient(true) }.files
