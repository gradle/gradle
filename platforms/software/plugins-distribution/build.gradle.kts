/*
 * Copyright 2023 the original author or authors.
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

description = "Plugin used to package a project as a distribution."

dependencies {
    api(libs.inject)

    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))

    implementation(project(":dependency-management"))
    implementation(project(":file-collections"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))

    implementation(libs.commonsLang)

    runtimeOnly(libs.groovy)

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Uses application plugin.")
    }
}

integTest.usesJavadocCodeSnippets = true
