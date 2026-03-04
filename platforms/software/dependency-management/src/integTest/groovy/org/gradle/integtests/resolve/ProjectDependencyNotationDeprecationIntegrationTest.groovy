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
import spock.lang.Issue

/**
 * Verifies deprecation behavior when a {@link org.gradle.api.Project} object is passed as a dependency notation.
 *
 * @see org.gradle.api.internal.notations.DependencyProjectNotationConverter
 */
@Issue("https://github.com/gradle/gradle/issues/36801")
class ProjectDependencyNotationDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROJECT_AS_NOTATION_DEPRECATION =
        "Passing a Project object as a dependency notation has been deprecated. " +
        "This will fail with an error in Gradle 10. " +
        "Use project(String) on DependencyHandler or DependencyFactory.createProjectDependency(String) instead. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_notation_in_dependency_handler"

    private static final String FACTORY_CREATE_DEPRECATION =
        "The DefaultDependencyFactory.create(Project) method has been deprecated. " +
        "This will fail with an error in Gradle 10. " +
        "Use createProjectDependency(String) instead. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_notation_in_dependency_handler"

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'foo'
        """
        file("foo/build.gradle") << """
            plugins { id('java-library') }
        """
        buildFile << """
            plugins { id('java-library') }
        """
    }

    def "calling implicit project(':foo') does not emit a deprecation warning"() {
        // project(':foo') inside dependencies {} block resolves via DependencyHandler.project(String),
        // not through Project.project(String), so no deprecation is emitted.
        buildFile << """
            dependencies {
                implementation project(':foo')
            }
        """

        expect:
        succeeds("help")
    }

    def "passing a Project object explicitly to add() emits a deprecation warning"() {
        // project.project(':foo') bypasses the DependencyHandler delegate, returning a Project object.
        // Passing that Project object to implementation() routes through DependencyProjectNotationConverter.
        buildFile << """
            dependencies {
                implementation project.project(':foo')
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(PROJECT_AS_NOTATION_DEPRECATION)
        succeeds("help")
    }

    def "calling DependencyFactory.create(Project) emits a deprecation warning"() {
        // getDependencyFactory().create(Project) calls DefaultDependencyFactory.create(Project) which is deprecated.
        buildFile << """
            dependencies {
                implementation project.getDependencyFactory().create(project.project(':foo'))
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(FACTORY_CREATE_DEPRECATION)
        succeeds("help")
    }

    def "calling dependencies.project(':foo') does not emit a deprecation warning"() {
        buildFile << """
            def dep = dependencies.project(':foo')
            dependencies {
                implementation dep
            }
        """

        expect:
        succeeds("help")
    }
}
