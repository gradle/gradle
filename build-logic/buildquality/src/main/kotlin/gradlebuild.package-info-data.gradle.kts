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
import gradlebuild.packageinfo.support.packageInfoDataVariant
import gradlebuild.packageinfo.tasks.GeneratePackageInfoDataTask

plugins {
    java
    groovy
}

val packageInfoData = tasks.register<GeneratePackageInfoDataTask>("packageInfoData") {
    description = "Collects the packages this project owns and the package-info.java files that apply to them"
    projectPath = project.path
    projectBaseDir = layout.projectDirectory.asFile.relativeTo(layout.settingsDirectory.asFile).invariantSeparatorsPath
    sourceRoots.from(sourceSets.main.get().java.sourceDirectories)
    sourceRoots.from(sourceSets.main.get().groovy.sourceDirectories)
    outputFile = layout.buildDirectory.file("architecture/package-info.json")
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    packageInfoData {
        // Kotlin projects usually keep their package-info.java under src/main/java, but the Kotlin root is where
        // most of their packages are declared, and a few package-info.java files live there too.
        sourceRoots.from(kotlinMainSourceSet.sourceDirectories)
    }
}

// Intentionally NOT wired into `check`: this data carries no assertions of its own. The rules that consume it live
// in :architecture-test, which aggregates the per-project data across the distribution.

configurations.consumable("packageInfoData") {
    attributes { packageInfoDataVariant(objects) }
    // Deliberately no extendsFrom: this variant only carries an artifact, it is not a graph-traversal vehicle.
    // Consumers reach every project by reselecting this variant over an already-resolved runtime graph, so the
    // variant does not have to republish runtime dependencies.
    outgoing.artifact(packageInfoData.flatMap { it.outputFile })
}
