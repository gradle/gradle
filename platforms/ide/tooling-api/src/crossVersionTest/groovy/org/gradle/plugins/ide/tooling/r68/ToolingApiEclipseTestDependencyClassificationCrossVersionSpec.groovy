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

package org.gradle.plugins.ide.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry
import org.gradle.tooling.model.eclipse.EclipseProject

// Test-dependency classification branches that only produce the "test" classpath attribute from Gradle 6.8 onwards.
@TargetGradleVersion(">=6.8")
class ToolingApiEclipseTestDependencyClassificationCrossVersionSpec extends ToolingApiSpecification {

    private static boolean hasTestAttribute(EclipseClasspathEntry entry) {
        entry.classpathAttributes.find { it.name == 'test' && it.value == 'true' }
    }

    def "external dependency in a custom source set has the test attribute only when the source set name contains 'test'"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }

            ${mavenCentralRepository()}

            sourceSets {
                functionalTest
                integration
            }

            dependencies {
                functionalTestImplementation 'com.google.guava:guava:21.0'
                integrationImplementation 'commons-io:commons-io:1.4'
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def guava = project.classpath.find { it.file.name.contains 'guava' }
        def commonsIo = project.classpath.find { it.file.name.contains 'commons-io' }

        then:
        hasTestAttribute(guava)
        !hasTestAttribute(commonsIo)
    }
}
