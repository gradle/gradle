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

import org.gradle.tooling.internal.gradle.DefaultGradleBuild
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.ProjectIdentifier
import spock.lang.Specification

class GradleBuildConverterTest extends Specification {

    DefaultGradleBuild gradleBuild;

    def "converts rootproject"() {
        setup:
        GradleProject project = gradleProject()
        _ * project.children >> ([] as DomainObjectSet)
        when:

        gradleBuild = new GradleBuildConverter().convert(project)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 0
    }

    def "converts child projects"() {
        setup:
        GradleProject rootProject = gradleProject()
        GradleProject sub1 = gradleProject("sub1", ":sub1")
        GradleProject sub2 = gradleProject("sub2", ":sub2")
        _ * sub1.children >> ([] as DomainObjectSet)
        _ * sub2.children >> ([] as DomainObjectSet)
        _ * rootProject.children >> ([sub1, sub2] as DomainObjectSet)
        when:
        gradleBuild = new GradleBuildConverter().convert(rootProject)
        then:
        rootProjectMapped()
        gradleBuild.rootProject.children.size() == 2
        gradleBuild.rootProject.children*.name == ["sub1", "sub2"]
        gradleBuild.rootProject.children*.path == [":sub1", ":sub2"]
    }

    def "converts nested child projects"() {
        setup:
        GradleProject rootProject = gradleProject()
        GradleProject sub1 = gradleProject("sub1", ":sub1")
        GradleProject sub2 = gradleProject("sub2", ":sub1:sub2")
        _ * sub2.children >> ([] as DomainObjectSet)
        _ * sub1.children >> ([sub2] as DomainObjectSet)
        _ * rootProject.children >> ([sub1] as DomainObjectSet)
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
    }

    def rootProjectMapped() {
        assert gradleBuild.rootProject.name == "rootProject"
        assert gradleBuild.rootProject.path == ":"
        gradleBuild
    }

    GradleProject gradleProject(projectName = "rootProject", path = ":") {
        GradleProject gradleProject = Mock(GradleProject)
        _ * gradleProject.projectIdentifier >> Stub(ProjectIdentifier) {
            getProjectPath() >> path
        }
        _ * gradleProject.name >> projectName
        gradleProject
    }
}
