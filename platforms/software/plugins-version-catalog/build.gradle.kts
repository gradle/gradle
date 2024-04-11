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

description = """Provides the version catalog plugin."""

dependencies {
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))

    api(libs.guava)
    api(libs.inject)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":logging-api"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))

    runtimeOnly(libs.groovy)

    integTestImplementation(testFixtures(project(":core")))
    integTestImplementation(testFixtures(project(":jvm-services")))
    integTestImplementation(testFixtures(project(":resources-http")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}
