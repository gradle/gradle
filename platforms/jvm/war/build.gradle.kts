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
    id("gradlebuild.instrumented-java-project")
}

description = "Adds support for assembling JVM web application WAR files"

dependencies {
    api(projects.languageJvm)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)

    api(libs.groovy)
    api(libs.inject)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.dependencyManagement)
    implementation(projects.fileCollections)
    implementation(projects.fileOperations)
    implementation(projects.languageJava)
    implementation(projects.logging)
    implementation(projects.modelCore)
    implementation(projects.platformBase)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJvmTestSuite)

    runtimeOnly(projects.testingBase)


    testImplementation(projects.pluginsJavaBase)
    testImplementation(testFixtures(projects.core))
    // TODO remove this
    testImplementation(projects.pluginsJavaBase)

    testRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/internal/*")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
