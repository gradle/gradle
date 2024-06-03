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

description = "Public types for unit testing plugins"

dependencies {
    api(libs.jsr305)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":concurrent"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":persistent-cache"))
    api(project(":serialization"))
    api(project(":time"))
    api(project(":build-process-services"))

    implementation(project(":build-state"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":java-language-extensions"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":daemon-protocol"))
    implementation(project(":daemon-services"))
    implementation(project(":native"))

    testImplementation(project(":testing-base"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder loads services from a Gradle distribution.")
    }
    integTestRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder loads services from a Gradle distribution.")
    }
    integTestDistributionRuntimeClasspath(project(":distributions-core"))
}
