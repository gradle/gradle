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

package org.gradle.integtests.composite

import spock.lang.Issue


class CompositeBuildTaskLifecycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    def "does not fail when #method task in included build is not scheduled for execution"() {
        given:
        buildA.buildFile << """
            task a {
                dependsOn gradle.includedBuild('buildB').task(':b')
            }
        """

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                task b {
                    $method 'c'
                }

                task c {
                }
            """
        }
        includedBuilds << buildB

        when:
        execute(buildA, 'a')

        then:
        assertTaskExecuted(':buildB', ':b')
        assertTaskExecuted(':', ':a')
        assertTaskNotExecuted(':buildB', ':c')

        where:
        method << ["shouldRunAfter", "mustRunAfter"]
    }

    def "finalizedBy task within included build schedules finalizer for execution"() {
        given:
        buildA.buildFile << """
            task a {
                dependsOn gradle.includedBuild('buildB').task(':b')
            }
        """

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                task b {
                    finalizedBy 'c'
                }

                task c {
                }
            """
        }
        includedBuilds << buildB

        when:
        execute(buildA, 'a')

        then:
        assertTaskExecuted(':buildB', ':b')
        assertTaskExecuted(':buildB', ':c')
        assertTaskExecuted(':', ':a')
    }

    @Issue("https://github.com/gradle/gradle/issues/15875")
    void '#method can not reference tasks from another build'() {
        given:
        buildA.buildFile << """
            task a {
                $method gradle.includedBuild('buildB').task(':b')
            }
        """

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                task b {
                    $method 'c'
                }

                task c {
                }
            """
        }

        includedBuilds << buildB

        expect:
        fails(buildA, "a")
        result.hasErrorOutput("Cannot use $method to reference tasks from another build")

        where:
        method << ["finalizedBy", "shouldRunAfter", "mustRunAfter"]
    }
}
