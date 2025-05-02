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
    id("java-library")
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

// tag::configure-suite-dependencies-project[]
dependencies {
    api("com.google.guava:guava:30.1.1-jre") // <1>
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3") // <2>
}

testing {
    suites {
        val integrationTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project()) // <3>
            }
        }
    }
}
// tag::configure-suite-dependencies-project[]
