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
import org.gradle.tooling.internal.gradle.DefaultGradleBuild
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class GradleBuildConverterTest extends Specification {
    @Shared
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    DefaultGradleBuild gradleBuild;

    @Unroll
    def "converts rootproject from model #model"() {
        when:
        gradleBuild = new GradleBuildConverter().convert(rootProject)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 0
        where:
        rootProject << [eclipseProject("rootProject", ":"), eclipseProjectVersion3("rootProject", ":")]
        model << [EclipseProject.class.simpleName, EclipseProjectVersion3.class.simpleName]
    }

    @Unroll
    def "converts child projects with model #model"() {
        when:
        gradleBuild = new GradleBuildConverter().convert(rootProject)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 2
        gradleBuild.rootProject.children*.name == ["sub1", "sub2"]
        gradleBuild.rootProject.children*.path == [":sub1", ":sub2"]
        gradleBuild.rootProject.children*.projectDirectory == [relativeProjectDir("sub1"), relativeProjectDir("sub2")]
        where:
        rootProject << [
                eclipseProject("rootProject", ":", eclipseProject("sub1", ":sub1"), eclipseProject("sub2", ":sub2")),
                eclipseProjectVersion3("rootProject", ":", eclipseProjectVersion3("sub1", ":sub1"), eclipseProjectVersion3("sub2", ":sub2"))]
        model << [EclipseProject.class.simpleName, EclipseProjectVersion3.class.simpleName]

    }

    @Unroll
    def "converts nested child projects with model #model"() {
        when:
        gradleBuild = new GradleBuildConverter().convert(rootProject)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 1
        gradleBuild.rootProject.children*.name == ["sub1"]
        gradleBuild.rootProject.children*.path == [":sub1"]
        gradleBuild.rootProject.children.asList()[0].children.size() == 1
        gradleBuild.rootProject.children.asList()[0].children*.name == ['sub2']
        gradleBuild.rootProject.children.asList()[0].children*.path == [':sub1:sub2']
        gradleBuild.rootProject.children.asList()[0].children*.projectDirectory == [relativeProjectDir('sub1/sub2')]
        where:
        rootProject << [eclipseProject("rootProject", ":", eclipseProject("sub1", ":sub1", eclipseProject("sub2", ":sub1:sub2"))),
                eclipseProjectVersion3("rootProject", ":", eclipseProjectVersion3("sub1", ":sub1", eclipseProjectVersion3("sub2", ":sub1:sub2")))]
        model << [EclipseProject.class.simpleName, EclipseProjectVersion3.class.simpleName]

    }

    def relativeProjectDir(String path) {
        temporaryFolder.testDirectory.file(path)
    }

    def rootProjectMapped() {
        assert gradleBuild.rootProject.name == "rootProject"
        assert gradleBuild.rootProject.path == ":"
        assert gradleBuild.rootProject.projectDirectory == temporaryFolder.testDirectory
        gradleBuild
    }

    EclipseProject eclipseProject(String projectName, String path, EclipseProject... children) {
        GradleProject gradleProject = Mock() {
            getPath() >> path
            getName() >> projectName
        }

        EclipseProject eclipseProject = Mock(EclipseProject)
        _ * eclipseProject.getGradleProject() >> gradleProject
        if (path == ":") {
            _ * eclipseProject.getProjectDirectory() >> temporaryFolder.testDirectory
        } else {
            _ * eclipseProject.getProjectDirectory() >> temporaryFolder.testDirectory.file(path.substring(1).replace(":", "/"))
        }
        eclipseProject.children >> (Arrays.asList(children) as org.gradle.tooling.model.DomainObjectSet)
        eclipseProject
    }

    EclipseProjectVersion3 eclipseProjectVersion3(String projectName, String path, EclipseProjectVersion3... children) {
        EclipseProjectVersion3 eclipseProject = Mock(EclipseProjectVersion3)
        _ * eclipseProject.name >> projectName
        _ * eclipseProject.path >> path
        if (path == ":") {
            _ * eclipseProject.getProjectDirectory() >> temporaryFolder.testDirectory
        } else {
            _ * eclipseProject.getProjectDirectory() >> temporaryFolder.testDirectory.file(path.substring(1).replace(":", "/"))
        }
        eclipseProject.children >> Arrays.asList(children)
        eclipseProject
    }
}
