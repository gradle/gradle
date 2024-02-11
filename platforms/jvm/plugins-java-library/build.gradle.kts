/*
 * Copyright 2024 the original author or authors.
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

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains the java-library plugin, and its supporting classes.  This plugin is used to build java libraries."

dependencies {
    api(project(":core-api"))

    api(libs.inject)

    implementation(project(":base-services"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-distribution"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-jvm-test-suite"))

    runtimeOnly(project(":core"))
    runtimeOnly(project(":platform-base"))

    testImplementation(project(":plugins-java-base"))

    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestImplementation(testFixtures(project(":resources-http")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

dependencyAnalysis {
    issues {
        onRuntimeOnly {
            // The plugin will suggest moving this to runtimeOnly, but it is needed during :plugins-java-library:compileJava
            // to avoid "class file for org.gradle.api.tasks.bundling.Jar not found"
            exclude(":language-jvm")
        }
    }
}
