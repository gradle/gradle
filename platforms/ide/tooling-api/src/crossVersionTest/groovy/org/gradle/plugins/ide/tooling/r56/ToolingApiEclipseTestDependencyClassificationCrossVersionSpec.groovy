/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r56

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseProjectDependency

/**
 * Covers the "present in both test and non-test configurations" precedence rule of the
 * "is this dependency a test dependency?" classification, which
 * {@code ToolingApiEclipseModelTestSourcesCrossVersionSpec} does not exercise. Verified through
 * {@code EclipseClasspathEntry.getClasspathAttributes()} for both external and project dependencies.
 */
@TargetGradleVersion(">=5.6")
class ToolingApiEclipseTestDependencyClassificationCrossVersionSpec extends ToolingApiSpecification {

    private static boolean hasTestAttribute(EclipseClasspathEntry entry) {
        entry.classpathAttributes.find { it.name == 'test' && it.value == 'true' }
    }

    def "external dependency present in both test and non-test configurations does not have the test attribute"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'com.google.guava:guava:21.0'
                testImplementation 'com.google.guava:guava:21.0'
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def guava = project.classpath.find { it.file.name.contains 'guava' }

        then:
        !hasTestAttribute(guava)
    }

    def "project dependency present in both test and non-test configurations does not have the test attribute"() {
        setup:
        includeProjects("a", "b")
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }

            dependencies {
                implementation project(':b')
                testImplementation project(':b')
            }
        """
        file('b/build.gradle') << "plugins { id 'java-library' }"

        when:
        EclipseProject root = loadToolingModel(EclipseProject)
        EclipseProject projectA = root.children.find { it.gradleProject.path == ':a' }
        EclipseProjectDependency depB = projectA.projectDependencies.find { it.path == 'b' }

        then:
        !hasTestAttribute(depB)
    }
}
