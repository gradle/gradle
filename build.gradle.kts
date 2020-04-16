/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.gradlebuild.UpdateAgpVersions
import org.gradle.gradlebuild.UpdateBranchStatus
import org.gradle.gradlebuild.buildquality.incubation.IncubatingApiAggregateReportTask
import org.gradle.gradlebuild.buildquality.incubation.IncubatingApiReportTask

plugins {
    base                  // basePluginConvention.distsDirName is used for the location of the distribution, might move this to "subprojects/distributions/build/..."
    gradlebuild.lifecycle // this needs to be applied first
    gradlebuild.`ci-reporting`
    gradlebuild.security
    gradlebuild.cleanup
    gradlebuild.buildscan
    gradlebuild.minify
    gradlebuild.wrapper
    gradlebuild.ide
    gradlebuild.`build-version`
    gradlebuild.`update-versions`             // Release process: Update 'released-versions.json' to latest Release Snapshots, RCs and Releases
    gradlebuild.`dependency-vulnerabilities`
    gradlebuild.`generate-subprojects-info`  // CI: Generate subprojects information for the CI testing pipeline fan out
    gradlebuild.`quick-check`                // Local development: Add `quickCheck` task for a checkstyle etc. only run on all changed files before commit
}

buildscript {
    dependencies {
        constraints {
            classpath("xerces:xercesImpl:2.12.0") {
                // it's unclear why we don't get this version directly from buildSrc constraints
                because("Maven Central and JCenter disagree on version 2.9.1 metadata")
            }
        }
    }
}

allprojects {
    group = "org.gradle"

    repositories {
        maven {
            name = "Gradle libs"
            url = uri("https://repo.gradle.org/gradle/libs")
        }
        maven {
            name = "kotlinx"
            url = uri("https://kotlin.bintray.com/kotlinx/")
        }
        maven {
            name = "kotlin-dev"
            url = uri("https://dl.bintray.com/kotlin/kotlin-dev")
        }
        maven {
            name = "kotlin-eap"
            url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
        }
    }
}

tasks.register("packageBuild") {
    description = "Build production distros and smoke test them"
    group =  "build"
    dependsOn(":distributions:verifyIsProductionBuildEnvironment", ":distributions:buildDists",
        ":distributions:integTest", ":docs:check", ":docs:checkSamples")
}

subprojects {
    plugins.withId("gradlebuild.publish-public-libraries") {
        tasks.register("promotionBuild") {
            description = "Build production distros, smoke test them and publish"
            group = "publishing"
            dependsOn(":distributions:verifyIsProductionBuildEnvironment", ":distributions:buildDists",
                ":distributions:integTest", ":docs:check", "publish")
        }
    }
}

tasks.register<UpdateBranchStatus>("updateBranchStatus")

tasks.register<UpdateAgpVersions>("updateAgpVersions") {
    comment.set(" Generated - Update by running `./gradlew updateAgpVersions`")
    minimumSupportedMinor.set("3.4")
    propertiesFile.set(layout.projectDirectory.file("gradle/dependency-management/agp-versions.properties"))
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

fun Project.collectAllIncubationReports() = subprojects.flatMap { it.tasks.withType(IncubatingApiReportTask::class) }

// Ensure the archives produced are reproducible
allprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        dirMode = Integer.parseInt("0755", 8)
        fileMode = Integer.parseInt("0644", 8)
    }
}
