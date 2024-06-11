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

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":model-core"))
    api(project(":platform-jvm"))
    api(project(":plugins-java-base"))
    api(project(":reporting"))
    api(project(":toolchains-jvm"))
    api(project(":toolchains-jvm-shared"))
    api(project(":workers"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":logging"))
    implementation(project(":native"))
    implementation(project(":plugins-groovy"))
    compileOnly(project(":internal-instrumentation-api"))

    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    runtimeOnly(project(":language-jvm"))

    testImplementation(project(":file-collections"))
    testImplementation(project(":plugins-java"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

    testFixturesImplementation(project(":core"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":base-services"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))

    integTestImplementation(testFixtures(project(":language-groovy")))
    integTestImplementation(libs.jsoup) {
        because("We need to validate generated HTML reports")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/quality/internal/*")
}
