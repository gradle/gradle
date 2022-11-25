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
    `java-library`
}

version = "1.0.0"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

// tag::multi-configure[]
testing {
    suites {
        withType(JvmTestSuite::class).matching { it.name in listOf("test", "integrationTest") }.configureEach { // <1>
            useJUnitJupiter()
            dependencies {
                implementation("org.mockito:mockito-junit-jupiter:4.6.1")
            }
        }
        val integrationTest by registering(JvmTestSuite::class)
        val functionalTest by registering(JvmTestSuite::class) {
            useJUnit() // <2>
            dependencies { // <3>
                implementation("org.apache.commons:commons-lang3:3.11")
            }
        }
    }
}
// end::multi-configure[]

val checkDependencies by tasks.registering {
    val testRuntimeClasspath: FileCollection = configurations.getByName("testRuntimeClasspath")
    val integrationTestRuntimeClasspath: FileCollection = configurations.getByName("integrationTestRuntimeClasspath")
    val functionalTestRuntimeClasspath: FileCollection = configurations.getByName("functionalTestRuntimeClasspath")

    dependsOn(testRuntimeClasspath, integrationTestRuntimeClasspath, functionalTestRuntimeClasspath)
    doLast {
        assert(testRuntimeClasspath.files.size == 13)
        assert(testRuntimeClasspath.files.any { it.name == "mockito-junit-jupiter-4.6.1.jar" })
        assert(integrationTestRuntimeClasspath.files.size == 13)
        assert(integrationTestRuntimeClasspath.files.any { it.name == "mockito-junit-jupiter-4.6.1.jar" })
        assert(functionalTestRuntimeClasspath.files.size == 3)
        assert(functionalTestRuntimeClasspath.files.any { it.name == "junit-4.13.2.jar" })
        assert(functionalTestRuntimeClasspath.files.any { it.name == "commons-lang3-3.11.jar" })
    }
}
