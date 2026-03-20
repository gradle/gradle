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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests the deprecation of using a Project object directly as a dependency notation.
 */
class ProjectDependencyNotationDeprecationIntegrationTest extends AbstractIntegrationSpec {

    // region Deprecation Expected

    def "emits deprecation when Project object is used directly as dependency notation"() {
        given:
        settingsFile << "include 'sub'"
        file("sub/build.gradle") << """
            plugins { id 'java-library' }
        """
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation project
            }
        """

        expect:
        expectProjectNotationDeprecationWarning()
        succeeds("dependencies")
    }

    def "emits deprecation when subproject Project object is used via findProject"() {
        given:
        settingsFile << "include 'sub'"
        file("sub/build.gradle") << """
            plugins { id 'java-library' }
        """
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation rootProject.findProject(':sub')
            }
        """

        expect:
        expectProjectNotationDeprecationWarning()
        succeeds("dependencies")
    }

    def "emits deprecation when using dependencies.create with a Project object"() {
        given:
        buildFile << """
            plugins { id 'java-library' }
            def dep = dependencies.create(project)
            assert dep != null
        """

        expect:
        expectProjectNotationDeprecationWarning()
        succeeds("help")
    }

    def "emits deprecation when Project is used as dependency constraint notation"() {
        given:
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                constraints {
                    implementation project
                }
            }
        """

        expect:
        expectProjectConstraintNotationDeprecationWarning()
        succeeds("dependencies")
    }

    def "emits deprecation when Project object is passed to platform()"() {
        given:
        settingsFile << "include 'sub'"
        file("sub/build.gradle") << """
            plugins { id 'java-platform' }
        """
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation platform(rootProject.findProject(':sub'))
            }
        """

        expect:
        expectProjectNotationDeprecationWarning()
        succeeds("dependencies")
    }

    def "emits deprecation when Project object is passed to testFixtures()"() {
        given:
        settingsFile << "include 'sub'"
        file("sub/build.gradle") << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
            }
        """
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation testFixtures(rootProject.findProject(':sub'))
            }
        """

        expect:
        expectProjectNotationDeprecationWarning()
        succeeds("dependencies")
    }

    // endregion

    // region No Deprecation Expected

    def "no deprecation when using project(path) on DependencyHandler"() {
        given:
        settingsFile << "include 'sub'"
        file("sub/build.gradle") << """
            plugins { id 'java-library' }
        """
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation project(":sub")
            }
        """

        expect:
        succeeds("dependencies")
    }

    def "no deprecation when using project() no-arg on DependencyHandler"() {
        given:
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation project()
            }
        """

        expect:
        succeeds("dependencies")
    }

    def "no deprecation when using dependencyFactory createProjectDependency no-arg"() {
        given:
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation project.getDependencyFactory().createProjectDependency()
            }
        """

        expect:
        succeeds("dependencies")
    }

    def "no deprecation when using dependencyFactory createProjectDependency with path"() {
        given:
        settingsFile << "include 'sub'"
        file("sub/build.gradle") << """
            plugins { id 'java-library' }
        """
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation project.getDependencyFactory().createProjectDependency(":sub")
            }
        """

        expect:
        succeeds("dependencies")
    }

    // endregion

    // region Functional Verification

    def "dependency resolves correctly despite deprecation warning"() {
        given:
        settingsFile << "include 'sub'"
        file("sub/build.gradle") << """
            plugins { id 'java-library' }
        """
        file("sub/src/main/java/sub/Lib.java") << """
            package sub;
            public class Lib {}
        """
        buildFile << """
            plugins { id 'java-library' }
            dependencies {
                implementation rootProject.findProject(':sub')
            }

            tasks.register("resolve") {
                def files = configurations.runtimeClasspath.files
                doLast {
                    println "resolved: " + files*.name
                }
            }
        """

        expect:
        expectProjectNotationDeprecationWarning()
        succeeds("resolve")
        outputContains("sub.jar")
    }

    // endregion

    private void expectProjectNotationDeprecationWarning() {
        executer.expectDocumentedDeprecationWarning(
            "Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10. " +
            "Please use the project(String) method on DependencyHandler or the createProjectDependency(String) method on DependencyFactory instead. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_project_notation"
        )
    }

    private void expectProjectConstraintNotationDeprecationWarning() {
        executer.expectDocumentedDeprecationWarning(
            "Using a Project object as a dependency constraint notation has been deprecated. This will fail with an error in Gradle 10. " +
            "Please use the project(String) method on DependencyHandler or the createProjectDependency(String) method on DependencyFactory instead. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_project_notation"
        )
    }
}
