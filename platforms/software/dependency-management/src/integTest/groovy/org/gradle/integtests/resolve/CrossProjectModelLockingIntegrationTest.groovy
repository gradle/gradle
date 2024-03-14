/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.resolve

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class CrossProjectModelLockingIntegrationTest extends AbstractIntegrationSpec {

    @NotYetImplemented
    @Requires(value = IntegTestPreconditions.NotParallelExecutor, reason = "Test enables parallel execution")
    def "can resolve another project when org.gradle.parallel is enabled"() {
        propertiesFile << "org.gradle.parallel=true"

        file("consumer/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation project(":")
            }

            task resolve {
                def files = configurations.runtimeClasspath
                doLast {
                    files.each { println it }
                }
            }
        """
        settingsFile << """
            include 'consumer'
        """

        buildFile << """
            plugins {
                id("java-library")
            }
        """

        expect:
        succeeds(":consumer:resolve")
    }

    @NotYetImplemented
    def "can resolve a project from an included build"() {
        file("producer/build.gradle") << """
            plugins {
                id("java-library")
            }

            group = "org"
            version = "1.0"
        """

        file("producer/settings.gradle") << """
            rootProject.name = 'producer'
        """

        settingsFile << """
            includeBuild("producer")
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation "org:producer:1.0"
            }

            task resolve {
                def files = configurations.runtimeClasspath
                doLast {
                    files.each { println it }
                }
            }
        """

        expect:
        succeeds(":resolve")
    }
}
