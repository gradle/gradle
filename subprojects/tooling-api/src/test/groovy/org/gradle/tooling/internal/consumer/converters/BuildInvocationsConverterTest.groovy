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

import org.gradle.tooling.internal.gradle.DefaultBuildInvocations
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.gradle.GradleScript
import spock.lang.Specification

class BuildInvocationsConverterTest extends Specification {

    DefaultBuildInvocations builds;

    def "converts empty single project"() {
        setup:
        GradleProject project = gradleProject()
        _ * project.children >> ([] as DomainObjectSet)
        when:
        builds = new BuildInvocationsConverter().convert(project)
        then:
        builds.selectors.isEmpty()
    }

    def "converts child projects"() {
        setup:
        GradleProject rootProject = gradleProject()
        GradleProject sub1 = gradleProject("sub1", ":sub1")
        GradleTask sub1T1 = Mock(GradleTask)
        GradleScript buildScript = Mock(GradleScript)
        File buildFile = Mock(File)
        _ * sub1.children >> ([] as DomainObjectSet)
        _ * sub1.tasks >> ([sub1T1] as DomainObjectSet)
        _ * rootProject.children >> ([sub1] as DomainObjectSet)
        _ * rootProject.buildScript >> buildScript
        _ * buildScript.sourceFile >> buildFile
        _ * sub1T1.name >> 't1'
        when:
        builds = new BuildInvocationsConverter().convert(rootProject)
        then:
        builds.taskSelectors.size() == 1
        builds.taskSelectors.get(0).name == 't1'
    }

    GradleProject gradleProject(projectName = "rootProject", path = ":") {
        GradleProject gradleProject = Mock(GradleProject)
        _ * gradleProject.path >> path
        _ * gradleProject.name >> projectName
        gradleProject
    }
}
