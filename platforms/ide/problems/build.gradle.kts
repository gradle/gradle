/*
 * Copyright 2021 the original author or authors.
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
    id("gradlebuild.distribution.implementation-kotlin")
}

description = """Problem SPI implementations.
    |
    |This project contains the SPI implementations for the problem reporting infrastructure.
""".trimMargin()


val problemReportReportPath by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    attributes { attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("configuration-cache-report")) }
}

// You can have a faster feedback loop by running `configuration-cache-report` as an included build
// See https://github.com/gradle/configuration-cache-report#development-with-gradlegradle-and-composite-build
dependencies {
    problemReportReportPath(libs.configurationCacheReport)
}

tasks.processResources {
    from(zipTree(problemReportReportPath.elements.map { it.first().asFile })) {
        into("org/gradle/internal/impl/problems")
        exclude("META-INF/**")
        rename { fileName ->
            fileName.replace("configuration-cache-report", "problems-report")
        }
    }
}

dependencies {
    api(projects.buildOperations)
    api(projects.buildOption)
    api(projects.concurrent)
    api(projects.configurationProblemsBase)
    api(projects.core)
    api(projects.fileTemp)
    api(projects.loggingApi)
    api(projects.problemsApi)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.jsr305)
    api(libs.kotlinStdlib)

    implementation(projects.logging)
    implementation(projects.messaging)

    testImplementation(projects.stdlibKotlinExtensions)

    testImplementation(libs.junit)

    integTestImplementation(projects.internalTesting)
    integTestImplementation(testFixtures(projects.logging))
    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
