/*
 * Copyright 2011 the original author or authors.
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

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":file-collections"))
    implementation(project(":execution"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":plugins"))
    implementation(project(":platform-jvm"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":native"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(libs.ant)
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

classycle {
    excludePatterns.set(listOf("org/gradle/plugins/ear/internal/*"))
}
