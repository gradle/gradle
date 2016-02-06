/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.composite.internal

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.composite.GradleConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.junit.Rule
import spock.lang.Specification

class DefaultCompositeValidatorTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def validator = new DefaultCompositeValidator()
    GradleConnection connection = Mock()

    def "fails when gradle version is too old and does not have overlapping project directories"() {
        def root = gradleProject("root")
        def other = gradleProject("other")
        def gradleProjects = [root, other] as Set
        def gradleVersions = [gradleVersion("1.0-milestone-2"), gradleVersion("2.5")] as Set
        when:
        fails()
        then:
        1 * connection.getModels(GradleProject) >> gradleProjects
        1 * connection.getModels(BuildEnvironment) >> gradleVersions
        0 * connection._
    }

    def "fails when gradle version is newer than minimum and two projects have overlapping project directories"() {
        def root = gradleProject("root")
        def other = gradleProject("root")
        def gradleProjects = [root, other] as Set
        def gradleVersions = [gradleVersion("2.0"), gradleVersion("2.5")] as Set
        when:
        fails()
        then:
        1 * connection.getModels(GradleProject) >> gradleProjects
        1 * connection.getModels(BuildEnvironment) >> gradleVersions
        0 * connection._
    }

    def "verifies both gradle versions and overlapping project directories"() {
        def root = gradleProject("root")
        def other = gradleProject("other")
        def gradleProjects = [root, other] as Set
        def gradleVersions = [gradleVersion("2.0"), gradleVersion("2.5")] as Set
        when:
        passes()
        then:
        1 * connection.getModels(GradleProject) >> gradleProjects
        1 * connection.getModels(BuildEnvironment) >> gradleVersions
        0 * connection._
    }

    def passes() {
        validator.isSatisfiedBy(connection)
    }

    def fails() {
        !passes()
    }

    GradleProject gradleProject(String projectDir, GradleProject rootProject=null) {
        GradleProject project = Mock()
        project.parent >> rootProject
        project.projectDirectory >> temporaryFolder.createDir(projectDir)
        project.name >> projectDir
        return project
    }

    BuildEnvironment gradleVersion(version) {
        BuildEnvironment buildEnvironment = Mock()
        GradleEnvironment gradleEnvironment = Mock()
        buildEnvironment.gradle >> gradleEnvironment
        gradleEnvironment.gradleVersion >> version
        return buildEnvironment
    }
}
