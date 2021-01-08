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


class CompositeBuildTaskLifecycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "does not fail when mustRunAfter task in included build is not scheduled for execution"() {
        given:
        buildA.buildFile << """
            task a {
                dependsOn gradle.includedBuild('buildB').task(':b')
            }
        """

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                task b {
                    mustRunAfter 'c'
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
    }

    def "mustRunAfter task from included build fails when not explicitly scheduled"() {
        given:
        buildA.buildFile << """
            task a {
                mustRunAfter gradle.includedBuild('buildB').task(':b')
            }
        """

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                task b {
                }
            """
        }
        includedBuilds << buildB

        when:
        executer.expectDocumentedDeprecationWarning("Using mustRunAfter to reference tasks from another build has been deprecated. This will fail with an error in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#referencing_tasks_from_included_builds")
        fails(buildA, 'a')

        then:
        failureDescriptionContains("Included build task ':b' was never scheduled for execution.")
    }

    def "shouldRunAfter task from included build fails when not explicitly scheduled"() {
        given:
        buildA.buildFile << """
            task a {
                shouldRunAfter gradle.includedBuild('buildB').task(':b')
            }
        """

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                task b {
                }
            """
        }
        includedBuilds << buildB

        when:
        executer.expectDocumentedDeprecationWarning("Using shouldRunAfter to reference tasks from another build has been deprecated. This will fail with an error in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#referencing_tasks_from_included_builds")
        fails(buildA, 'a')

        then:
        failureDescriptionContains("Included build task ':b' was never scheduled for execution.")
    }

    def "finalizedBy is respected within included build"() {
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

    def "finalizedBy task from included build schedules finalizer for execution"() {
        given:
        buildA.buildFile << """
            task a {
                finalizedBy gradle.includedBuild('buildB').task(':b')
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
        executer.expectDocumentedDeprecationWarning("Using finalizedBy to reference tasks from another build has been deprecated. This will fail with an error in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#referencing_tasks_from_included_builds")
        includedBuilds << buildB

        when:
        execute(buildA, 'a')

        then:
        assertTaskExecuted(':', ':a')
        assertTaskExecuted(':buildB', ':b')
        assertTaskExecuted(':buildB', ':c')
    }
}
