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
    java
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

// tag::configure-source-dir[]
testing {
    suites {
        val integrationTest by registering(JvmTestSuite::class) { // <1>
            sources { // <2>
                java { // <3>
                    setSrcDirs(listOf("src/it/java")) // <4>
                }
            }
        }
    }
}
// end::configure-source-dir[]

// tag::configure-test-task[]
testing {
    suites {
        val integrationTest by getting(JvmTestSuite::class) {
            targets {
                all { // <1>
                    testTask.configure {
                        maxHeapSize = "512m" // <2>
                    }
                }
            }
        }
    }
}
// end::configure-test-task[]
