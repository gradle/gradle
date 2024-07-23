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
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Base utilities and services to report and track configuration problems"

val configurationCacheReportPath by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    attributes { attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("configuration-cache-report")) }
}

// You can have a faster feedback loop by running `configuration-cache-report` as an included build
// See https://github.com/gradle/configuration-cache-report#development-with-gradlegradle-and-composite-build
dependencies {
    configurationCacheReportPath(libs.configurationCacheReport)
}

tasks.processResources {
    from(zipTree(configurationCacheReportPath.elements.map { it.first().asFile })) {
        into("org/gradle/internal/configuration/problems")
        exclude("META-INF/**")
    }
}

dependencies {
    api(projects.baseServices)
    api(projects.buildOption)
    api(projects.concurrent)
    api(projects.coreApi)
    api(projects.fileTemp)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.problemsApi)
    api(projects.stdlibJavaExtensions)

    api(libs.kotlinStdlib)

    implementation(libs.groovyJson)
    implementation(libs.guava)

    implementation(projects.core)
    implementation(projects.hashing)
    implementation(projects.stdlibKotlinExtensions)
}
