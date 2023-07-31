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
    id("gradlebuild.instrumented-project")
}

description = "Contains the Application plugin, and its supporting classes.  This plugin is used for creating runnable Java application projects."

dependencies {
    api(project(":core"))
    api(project(":core-api"))

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":base-services"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":logging"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-distribution"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":process-services"))
    implementation(project(":toolchains-jvm"))
    implementation(project(":toolchains-jvm-shared"))

    implementation(libs.ant)
    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.guava)

    testImplementation(testFixtures(project(":core")))

    integTestImplementation(testFixtures(project(":enterprise-operations")))
    integTestImplementation(testFixtures(project(":language-java")))
    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":plugins-java")))
    integTestImplementation(testFixtures(project(":plugins-java-base")))
    integTestImplementation(testFixtures(project(":resources-http")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
