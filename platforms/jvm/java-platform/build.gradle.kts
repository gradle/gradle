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

description = "Adds support for creating dependency platforms for JVM projects"

errorprone {
    disabledChecks.addAll(
        "InlineFormatString", // 1 occurrences
    )
}

dependencies {
    api(project(":core-api"))

    api(libs.inject)

    implementation(project(":base-services"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":ivy"))
    implementation(projects.javaLanguageExtensions)
    implementation(project(":maven"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":publish"))

    runtimeOnly(libs.groovy)

    integTestImplementation(testFixtures(project(":dependency-management")))
    integTestImplementation(testFixtures(project(":resources-http")))

    testImplementation(project(":language-java")) {
        because("need to access JavaCompile task")
    }

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/java/**")
}
