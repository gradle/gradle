/*
 * Copyright 2022 the original author or authors.
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

gradlebuildJava.usedInWorkers()

description = """JVM-specific test infrastructure, including support for bootstrapping and configuring test workers
and executing tests.
Few projects should need to depend on this module directly. Most external interactions with this module are through the
various implementations of WorkerTestClassProcessorFactory.
"""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":testing-base"))

    implementation(libs.slf4jApi)
    implementation(libs.commonsLang)
    implementation(libs.junit)
    implementation(libs.testng)
    implementation(libs.bsh) {
        because("Used by TestNG")
    }

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":messaging")))
    testRuntimeOnly(libs.guice) {
        because("Used by TestNG")
    }

    testFixturesImplementation(project(":testing-base"))
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.testng)
    testFixturesImplementation(libs.bsh)
}
