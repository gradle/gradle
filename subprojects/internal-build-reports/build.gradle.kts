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

import gradlebuild.incubation.tasks.IncubatingApiAggregateReportTask
import gradlebuild.incubation.tasks.IncubatingApiReportTask

plugins {
    id("gradlebuild.internal.java")
}

val allIncubationReports = tasks.register<IncubatingApiAggregateReportTask>("allIncubationReports") {
    val allReports = collectAllIncubationReports()
    dependsOn(allReports)
    reports = allReports.associateBy({ it.title.get() }) { it.textReportFile.asFile.get() }
}

tasks.register<Zip>("allIncubationReportsZip") {
    destinationDirectory.set(layout.buildDirectory.dir("reports/incubation"))
    archiveBaseName.set("incubating-apis")
    from(allIncubationReports.get().htmlReportFile)
    from(collectAllIncubationReports().map { it.htmlReportFile })
}

fun Project.collectAllIncubationReports() = rootProject.subprojects.flatMap { it.tasks.withType<IncubatingApiReportTask>() }
