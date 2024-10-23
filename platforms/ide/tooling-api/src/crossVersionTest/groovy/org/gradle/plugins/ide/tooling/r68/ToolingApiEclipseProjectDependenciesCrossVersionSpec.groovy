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
package org.gradle.plugins.ide.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject

@TargetGradleVersion(">=6.8")
class ToolingApiEclipseProjectDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def "project dependency does not leak test sources"() {
        setup:
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject).children.find { it.gradleProject.path == ':b' }

        then:
        project.projectDependencies.size() == 1
        project.projectDependencies[0].classpathAttributes.find { it.name == 'without_test_code' }.value == "true"
    }

    def "project dependency pointing to test fixture project exposes test sources"() {
        setup:
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject).children.find { it.gradleProject.path == ':b' }

        then:
        project.projectDependencies.size() == 1
        project.projectDependencies[0].classpathAttributes.find { it.name == 'without_test_code' }.value == "false"
    }
}
