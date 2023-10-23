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

//gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-annotations"))
    implementation(project(":base-services"))
    implementation(project(":enterprise-operations"))

    implementation(libs.guava)
    implementation(libs.inject)

    integTestImplementation(project(":internal-testing"))
    integTestImplementation(testFixtures(project(":logging")))
    integTestDistributionRuntimeOnly(project(":distributions-core"))

    testFixturesImplementation(project(":enterprise-operations"))
}
