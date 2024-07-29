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

description = "Plugins and integration with code quality (Checkstyle, PMD, CodeNarc)"

errorprone {
    disabledChecks.addAll(
        "HidingField", // 3 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.modelCore)
    api(projects.platformJvm)
    api(projects.pluginsJavaBase)
    api(projects.reporting)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)
    api(projects.workers)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.logging)
    implementation(projects.native)
    implementation(projects.pluginsGroovy)
    implementation(projects.serviceLookup)
    compileOnly(projects.internalInstrumentationApi)

    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)

    runtimeOnly(projects.languageJvm)

    testImplementation(projects.fileCollections)
    testImplementation(projects.pluginsJava)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.modelCore))

    testFixturesImplementation(projects.core)
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.baseServices)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsFull)

    integTestImplementation(testFixtures(projects.languageGroovy))
    integTestImplementation(libs.jsoup) {
        because("We need to validate generated HTML reports")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/quality/internal/*")
}
