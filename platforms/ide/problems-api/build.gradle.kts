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
}

description = """A problems description API
    |
    |This project provides base classes to describe problems and their
    |solutions, in a way that enforces the creation of good error messages.
    |
    |It's a stripped down version of the original code available
    |at https://github.com/melix/jdoctor/
""".trimMargin()

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.buildOperations)

    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(projects.serialization)

    testImplementation(projects.logging)
    integTestImplementation(projects.internalTesting)
    integTestImplementation(testFixtures(projects.logging))
    integTestDistributionRuntimeOnly(projects.distributionsCore)

    testFixturesImplementation(projects.enterpriseOperations)
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.internalIntegTesting)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}

packageCycles {
    excludePatterns.add("org/gradle/api/problems/**") // ProblemId.create() and ProblemGroup.create() return internal types
}
