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

package org.gradle.plugins.ide.tooling.r67

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=6.7')
@TargetGradleVersion(">=6.7")
class ToolingApiEclipseModelUnresolvedDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        def mavenRepo = new MavenFileRepository(file('maven-repo'))
        mavenRepo.module('org.example', 'lib', '1.0').publish()

        settingsFile << 'rootProject.name = "root"'
        buildFile << """
            plugins {
                id 'java-library'
            }

            repositories {
                maven {
                    url '${mavenRepo.uri}'
                }
            }
            dependencies {
                implementation 'org.example:lib:1.0'
                implementation 'org.example:does not exist:1.0'
            }
        """
    }

    @TargetGradleVersion("=6.6")
    def "Older Gradle versions mark all dependencies as resolved"() {
        when:
        def project = loadToolingModel(EclipseProject)
        def allDependencies = project.classpath

        then:
        allDependencies.size() == 2
        allDependencies[0].resolved
        allDependencies[0].attemptedSelector == null
        allDependencies[1].resolved
        allDependencies[1].attemptedSelector == null
    }

    def "Client can distinguish resolved and unresolved dependencies"() {
        when:
        def project = loadToolingModel(EclipseProject)
        def allDependencies = project.classpath
        def resolvedDependencies = project.classpath.findAll { it.resolved }
        def unresolvedDependencies = project.classpath.findAll { !it.resolved }

        then:
        allDependencies.size() == 2

        and:
        resolvedDependencies.size() == 1
        resolvedDependencies[0].resolved
        resolvedDependencies[0].attemptedSelector == null

        and:
        unresolvedDependencies.size() == 1
        !unresolvedDependencies[0].resolved
        unresolvedDependencies[0].attemptedSelector.displayName == 'org.example:does not exist:1.0'
    }
}
