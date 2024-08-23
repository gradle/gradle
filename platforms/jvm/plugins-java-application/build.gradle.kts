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
    id("gradlebuild.instrumented-java-project")
}

description = "Contains the Application plugin, and its supporting classes.  This plugin is used for creating runnable Java application projects."

dependencies {
    api(projects.core)
    api(projects.coreApi)
    api(projects.stdlibJavaExtensions)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.pluginsJavaBase)
    implementation(projects.baseServices)
    implementation(projects.languageJava)
    implementation(projects.languageJvm)
    implementation(projects.logging)
    implementation(projects.modelCore)
    implementation(projects.platformJvm)
    implementation(projects.pluginsDistribution)
    implementation(projects.pluginsJava)
    implementation(projects.processServices)
    implementation(projects.toolchainsJvm)
    implementation(projects.toolchainsJvmShared)

    implementation(libs.ant)
    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.guava)

    testImplementation(testFixtures(projects.core))

    integTestImplementation(testFixtures(projects.enterpriseOperations))
    integTestImplementation(testFixtures(projects.languageJava))
    integTestImplementation(testFixtures(projects.modelCore))
    integTestImplementation(testFixtures(projects.pluginsJava))
    integTestImplementation(testFixtures(projects.pluginsJavaBase))
    integTestImplementation(testFixtures(projects.resourcesHttp))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
