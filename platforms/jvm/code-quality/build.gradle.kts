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

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":native"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":plugins"))
    implementation(project(":workers"))
    implementation(project(":reporting"))
    implementation(project(":platform-jvm"))
    implementation(project(":file-collections"))
    compileOnly(project(":internal-instrumentation-api"))

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.ant)

    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":base-services"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))

    integTestImplementation(testFixtures(project(":language-groovy")))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/quality/internal/*")
}
