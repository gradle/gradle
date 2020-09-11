/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.constraints

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Issue

class DependencyConstraintsBugsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    // Ideally this should be a reproducer using generated dependencies but I wasn't able
    // to figure out a reproducer
    @Issue("https://github.com/gradle/gradle/issues/13960")
    def "should resolve dependency which version is provided by an upgraded transitive platform"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${jcenterRepository()}

            dependencies {
                implementation(platform("io.micronaut:micronaut-bom:2.0.1"))
                implementation("io.micronaut.kafka:micronaut-kafka")
                testImplementation("io.micronaut.test:micronaut-test-spock")
            }

            task resolve {
                inputs.files(configurations.testRuntimeClasspath)
                doLast {
                    configurations.testRuntimeClasspath.files.name.each {
                        println(it)
                    }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        noExceptionThrown()
        outputContains "micronaut-messaging-2.0.1.jar"
    }
}
