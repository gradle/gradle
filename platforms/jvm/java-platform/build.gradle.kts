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
    api(projects.coreApi)

    api(libs.inject)

    implementation(projects.baseServices)
    implementation(projects.core)
    implementation(projects.dependencyManagement)
    implementation(projects.ivy)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.maven)
    implementation(projects.platformBase)
    implementation(projects.platformJvm)
    implementation(projects.publish)

    runtimeOnly(libs.groovy)

    integTestImplementation(testFixtures(projects.dependencyManagement))
    integTestImplementation(testFixtures(projects.resourcesHttp))

    testImplementation(projects.languageJava) {
        because("need to access JavaCompile task")
    }

    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/java/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
