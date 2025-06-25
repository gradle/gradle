/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArchivesConfigurationDeprecationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile """
            plugins {
                id("base")
            }
        """
    }

    def "deprecation when adding artifacts to the archives configuration"() {
        buildFile """
            task jar(type: Jar) {}

            configurations {
                archives {
                    outgoing.artifact(tasks.jar)
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(
            "The archives configuration has been deprecated for artifact declaration. " +
                "This will fail with an error in Gradle 10. " +
                "Add artifacts as a direct task dependencies of the 'assemble' task instead of declaring them in the archives configuration. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#sec:archives-configuration"
        )
        succeeds("assemble")
    }

    def "deprecation when consuming artifacts from the archives configuration"() {
        settingsFile """
            rootProject.name = "root"
            include(":sub")
        """
        buildFile """
            task jar(type: Jar) {}

            configurations {
                resolvable("resolvableArchives") {
                    extendsFrom archives
                }
                archives {
                    outgoing.artifact(tasks.jar)
                }
            }
        """
        buildFile("sub/build.gradle", """
            configurations {
                dependencyScope("consumeArchives")
                resolvable("resolvableArchives") {
                    extendsFrom consumeArchives
                }
            }

            dependencies {
                consumeArchives project(path: ":", configuration: "archives")
            }

            task("resolve") {
                def resolvableArchives = configurations.resolvableArchives
                inputs.files resolvableArchives
                doLast {
                    resolvableArchives.files.each { println it }
                }
            }
        """)

        expect:
        executer.expectDocumentedDeprecationWarning(
            "The archives configuration has been deprecated for artifact declaration. " +
                "This will fail with an error in Gradle 10. " +
                "Add artifacts as a direct task dependencies of the 'assemble' task instead of declaring them in the archives configuration. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#sec:archives-configuration"
        )
        executer.expectDocumentedDeprecationWarning("The archives configuration has been deprecated for consumption. " +
            "This will fail with an error in Gradle 10. " +
            "For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation."
        )
        succeeds(":sub:resolve")
    }

    def "error when adding dependencies to the archives configuration"() {
        settingsFile """
            include(":sub")
        """
        buildFile """
            dependencies {
                archives project(":sub")
            }
        """
        file("sub").createDir()

        expect:
        fails("assemble")
        failure.assertHasCause("Dependencies can not be declared against the `archives` configuration.")
    }

    def "error when resolving the archives configuration"() {
        buildFile """
            task("resolve") {
                def archives = configurations.archives
                doLast {
                    archives.files.each { println it }
                }
            }
        """

        expect:
        fails("resolve")
        failure.assertHasCause("Resolving dependency configuration 'archives' is not allowed")
    }
}
