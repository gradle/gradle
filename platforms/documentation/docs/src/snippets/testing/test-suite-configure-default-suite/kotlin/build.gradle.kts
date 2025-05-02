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

// tag::configure-default-suite[]
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useTestNG() // <1>

            targets {
                all {
                    testTask.configure { // <2>
                        // set a system property for the test JVM(s)
                        systemProperty("some.prop", "value")
                        options { // <3>
                            val options = this as TestNGOptions
                            options.preserveOrder = true
                        }
                    }
                }
            }
        }
    }
}
// tag::configure-default-suite[]
