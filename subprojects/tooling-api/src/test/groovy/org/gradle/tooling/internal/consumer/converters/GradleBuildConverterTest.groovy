/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.consumer.converters

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.gradle.DefaultGradleBuild
import org.gradle.tooling.internal.gradle.DefaultGradleProject
import org.gradle.tooling.model.GradleProject
import org.junit.Rule
import spock.lang.Specification

class GradleBuildConverterTest extends Specification {


    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    ConsumerOperationParameters operationParameters = Mock(ConsumerOperationParameters)

    DefaultGradleBuild gradleBuild;

    def "converts rootproject"() {
        setup:
        1 * operationParameters.getProjectDir() >> temporaryFolder.testDirectory
        when:
        gradleBuild = new GradleBuildConverter().convert(gradleProject(), operationParameters)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 0
    }

    def "converts child projects"() {
        setup:
        1 * operationParameters.getProjectDir() >> temporaryFolder.testDirectory
        GradleProject rootProject = gradleProject()
        rootProject.children = [gradleProject("sub1", ":sub1"), gradleProject("sub2", ":sub2")] as List
        when:
        gradleBuild = new GradleBuildConverter().convert(rootProject, operationParameters)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 2
        gradleBuild.rootProject.children*.name == ["sub1", "sub2"]
        gradleBuild.rootProject.children*.path == [":sub1", ":sub2"]
    }

    def "converts nested child projects"() {
        setup:
        1 * operationParameters.getProjectDir() >> temporaryFolder.testDirectory
        GradleProject rootProject = gradleProject()
        GradleProject childLvl1 = gradleProject("sub1", ":sub1")
        childLvl1.children = [gradleProject("sub2", ":sub1:sub2")]
        rootProject.children = [childLvl1]
        when:
        gradleBuild = new GradleBuildConverter().convert(rootProject, operationParameters)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 1
        gradleBuild.rootProject.children*.name == ["sub1"]
        gradleBuild.rootProject.children*.path == [":sub1"]
        gradleBuild.rootProject.children.asList()[0].children.size() == 1
        gradleBuild.rootProject.children.asList()[0].children*.name == ['sub2']
        gradleBuild.rootProject.children.asList()[0].children*.path == [':sub1:sub2']
    }

    def rootProjectMapped() {
        assert gradleBuild.rootProject.name == "rootProject"
        assert gradleBuild.rootProject.path == ":"
        assert gradleBuild.rootProject.projectDirectory == temporaryFolder.testDirectory
        gradleBuild
    }

    GradleProject gradleProject(projectName = "rootProject", path = ":") {
        DefaultGradleProject gradleProject = new DefaultGradleProject()
        gradleProject.path = path
        gradleProject.name = projectName
        gradleProject
    }
}
