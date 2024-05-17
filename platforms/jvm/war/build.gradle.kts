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

description = "Adds support for assembling JVM web application WAR files"

dependencies {
    api(project(":language-jvm"))
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":dependency-management"))
    implementation(project(":file-collections"))
    implementation(project(":language-java"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-jvm-test-suite"))

    runtimeOnly(project(":testing-base"))


    testImplementation(project(":plugins-java-base"))
    testImplementation(testFixtures(project(":core")))
    // TODO remove this
    testImplementation(project(":plugins-java-base"))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/internal/*")
}
