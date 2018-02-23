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

import org.gradle.tooling.internal.gradle.ConsumerProvidedTaskSelector
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import spock.lang.Specification

class BuildInvocationsConverterTest extends Specification {
    def "converts empty single project"() {
        setup:
        GradleProject project = gradleProject()
        _ * project.children >> ([] as DomainObjectSet)
        _ * project.tasks >> ([] as DomainObjectSet)
        when:
        def builds = new BuildInvocationsConverter().convert(project)
        then:
        builds.taskSelectors.isEmpty()
    }

    def "converts child projects"() {
        setup:
        GradleProject rootProject = gradleProject()
        GradleProject sub1 = gradleProject("sub1", ":sub1")
        GradleTask sub1T1 = gradleTask('t1', ':sub1:t1')
        _ * sub1.children >> ([] as DomainObjectSet)
        _ * sub1.tasks >> ([sub1T1] as DomainObjectSet)
        _ * rootProject.children >> ([sub1] as DomainObjectSet)
        _ * rootProject.tasks >> ([] as DomainObjectSet)
        when:
        def builds = new BuildInvocationsConverter().convert(rootProject)
        then:
        builds.taskSelectors.size() == 1
        builds.taskSelectors*.name as Set == ['t1'] as Set
    }

    def "builds model with all selectors"() {
        setup:
        def project = gradleProject()
        def child1 = gradleProject("child1", ":child1", project)
        def child1a = gradleProject("child1a", ":child1:child1a", child1)
        def child1b = gradleProject("child1b", ":child1:child1b", child1)
        GradleTask t1a = gradleTask('t1', ':child1:child1a:t1')
        GradleTask t1b = gradleTask('t1', ':child1:child1b:t1')
        GradleTask t2b = gradleTask('t2', ':child1:child1b:t2')
        _ * child1a.tasks >> ([t1a] as DomainObjectSet)
        _ * child1b.tasks >> ([t1b, t2b] as DomainObjectSet)
        _ * child1.tasks >> ([] as DomainObjectSet)
        _ * project.tasks >> ([] as DomainObjectSet)
        _ * child1a.children >> ([] as DomainObjectSet)
        _ * child1b.children >> ([] as DomainObjectSet)
        _ * child1.children >> ([child1a, child1b] as DomainObjectSet)
        _ * project.children >> ([child1] as DomainObjectSet)

        when:
        def builds = new BuildInvocationsConverter().convert(project)

        then:
        builds.taskSelectors.size() == 2
        builds.taskSelectors.find { ConsumerProvidedTaskSelector it ->
            it.name == 't1'
        }?.taskNames == [':child1:child1a:t1', ':child1:child1b:t1'] as Set
        builds.taskSelectors.find { ConsumerProvidedTaskSelector it ->
            it.name == 't2'
        }?.taskNames == [':child1:child1b:t2'] as Set
        builds.taskSelectors*.name.each { it != null }
        builds.taskSelectors*.description.each { it != null }
        builds.taskSelectors*.displayName.each { it != null }
    }

    GradleProject gradleProject(projectName = "rootProject", path = ":", parent = null) {
        GradleProject gradleProject = Mock(GradleProject)
        _ * gradleProject.path >> path
        _ * gradleProject.name >> projectName
        _ * gradleProject.parent >> parent
        gradleProject
    }
    GradleTask gradleTask(name, path) {
        GradleTask task = Mock(GradleTask)
        _ * task.name >> name
        _ * task.path >> path
        task
    }
}
