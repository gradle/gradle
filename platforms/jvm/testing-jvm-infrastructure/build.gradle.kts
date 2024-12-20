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
    // API dependencies
    api(projects.stdlibJavaExtensions)
    api(projects.time)
    api(projects.baseServices)
    api(projects.messaging)
    api(projects.testingBaseInfrastructure)
    api(libs.jsr305)
    api(libs.junit)
    api(libs.testng)

    // Handling capability conflicts for "bsh"
    api(libs.bsh) {
        because("""
            Ensures conflict resolution between "org.beanshell:bsh" and 
            "org.beanshell:beanshell". This guarantees version 2.0b6 is selected, 
            avoiding build failures due to version mismatches.
        """.trimIndent())
    }

    // Implementation dependencies
    implementation(projects.concurrent)
    implementation(libs.slf4jApi)

    // Test implementation dependencies
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(libs.assertj) {
        because("Testing assertion errors from AssertJ")
    }
    testImplementation("org.opentest4j:opentest4j") {
        version { require("1.3.0") } // Required for MultipleFailuresError
        because("Testing assertion errors from OpenTest4J")
    }

    // Test runtime dependencies
    testRuntimeOnly(libs.guice) {
        because("Required by TestNG")
    }

    // Test fixtures dependencies
    testFixturesImplementation(projects.testingBase)
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.testng)
    testFixturesImplementation(libs.bsh)
}

dependencyAnalysis {
    issues {
        onAny() {
            // Bsh is not used directly, but is selected as the result of capabilities conflict resolution - the classes ARE required at runtime by TestNG
            exclude(libs.bsh) // Bsh is required at runtime by TestNG, not directly used.
        }
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
