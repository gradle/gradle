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

package org.gradle.api.internal.project

import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.GradleInternal
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.groovy.scripts.UriScriptSource
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ProjectFactoryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def instantiator = Mock(Instantiator)
    def projectDescriptor = Stub(ProjectDescriptor)
    def gradle = Stub(GradleInternal)
    def serviceRegistryFactory = Stub(ServiceRegistryFactory)
    def project = Stub(DefaultProject)
    def factory = new ProjectFactory(instantiator)

    def setup() {
        gradle.serviceRegistryFactory >> serviceRegistryFactory
    }

    def "creates a project with build script"() {
        def buildFile = tmpDir.createFile("build.gradle")
        def projectDir = tmpDir.createFile("project")

        given:
        projectDescriptor.name >> "name"
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile

        when:
        def result = factory.createProject(projectDescriptor, null, gradle)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", null, projectDir, {it instanceof UriScriptSource}, gradle, serviceRegistryFactory) >> project
    }

    def "creates a project with missing build script"() {
        def buildFile = tmpDir.file("build.gradle")
        def projectDir = tmpDir.createFile("project")

        given:
        projectDescriptor.name >> "name"
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile

        when:
        def result = factory.createProject(projectDescriptor, null, gradle)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", null, projectDir, {it instanceof StringScriptSource}, gradle, serviceRegistryFactory) >> project
    }

    def "creates a child project"() {
        def parent = Mock(ProjectInternal)
        def buildFile = tmpDir.file("build.gradle")
        def projectDir = tmpDir.createFile("project")

        given:
        projectDescriptor.name >> "name"
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile

        when:
        def result = factory.createProject(projectDescriptor, parent, gradle)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", parent, projectDir, _, gradle, serviceRegistryFactory) >> project
        1 * parent.addChildProject(project)
    }
}
