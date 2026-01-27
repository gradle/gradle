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

description = """JVM-specific test infrastructure, including support for bootstrapping and configuring test workers
and executing tests.
Few projects should need to depend on this module directly. Most external interactions with this module are through the
various implementations of WorkerTestDefinitionProcessorFactory.
"""

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.time)
    api(projects.baseServices)
    api(projects.messaging)
    api(projects.testingBaseInfrastructure)

    api(libs.jspecify)

    implementation(projects.concurrent)

    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    compileOnly(providedLibs.junit) {
        because("The actual version is provided by the user on the testRuntimeClasspath")
    }
    compileOnly(providedLibs.testng) {
        because("The actual version is provided by the user on the testRuntimeClasspath")
    }
    compileOnly(providedLibs.junitPlatform) {
        because("The actual version is provided by the user on the testRuntimeClasspath")
    }
    compileOnly(providedLibs.junitPlatformEngine) {
        because("The actual version is provided by the user on the testRuntimeClasspath")
    }

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.time))

    testImplementation(testLibs.assertj) {
        because("We test assertion errors coming from AssertJ")
    }
    testImplementation("org.opentest4j:opentest4j") {
        version {
            // MultipleFailuresError appears only since 1.3.0-RC2
            require("1.3.0")
        }
        because("We test assertion errors coming from OpenTest4J")
    }
    testImplementation(providedLibs.junit) {
        because("To provide an implementation during testing")
    }
    testImplementation(providedLibs.junitPlatform) {
        because("Platform types are used in tests")
    }
    testImplementation(providedLibs.testng) {
        because("To provide an implementation during testing")
    }
    testRuntimeOnly(testLibs.guice) {
        because("Used by TestNG")
    }

    testFixturesImplementation(projects.testingBase)
    testFixturesImplementation(testLibs.junit)
    testFixturesImplementation(providedLibs.testng)

}
