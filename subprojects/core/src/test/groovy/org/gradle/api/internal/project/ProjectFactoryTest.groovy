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

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.internal.build.BuildState
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.DefaultTextFileResourceLoader
import org.gradle.internal.resource.EmptyFileTextResource
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ProjectFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def instantiator = Mock(Instantiator)
    def projectDescriptor = Stub(DefaultProjectDescriptor)
    def gradle = Stub(GradleInternal)
    def serviceRegistryFactory = Stub(ServiceRegistryFactory)
    def projectRegistry = Mock(ProjectRegistry)
    def project = Stub(DefaultProject)
    def buildId = Stub(BuildIdentifier)
    def owner = Stub(BuildState)
    def projectState = Stub(ProjectState)
    def factory = new ProjectFactory(instantiator, new DefaultTextFileResourceLoader())
    def rootProjectScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)

    def setup() {
        owner.buildIdentifier >> buildId
    }

    def "creates a project with build script"() {
        def buildFile = tmpDir.createFile("build.gradle")
        def projectDir = tmpDir.createFile("project")

        given:
        projectDescriptor.name >> "name"
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile
        gradle.projectRegistry >> projectRegistry

        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, null, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", null, projectDir, buildFile, { it instanceof TextResourceScriptSource }, gradle, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * projectRegistry.addProject(project)
    }

    def "creates a project with missing build script"() {
        def buildFile = tmpDir.file("build.gradle")
        def projectDir = tmpDir.createFile("project")

        given:
        projectDescriptor.name >> "name"
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile
        gradle.projectRegistry >> projectRegistry

        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, null, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", null, projectDir, buildFile, { it.resource instanceof EmptyFileTextResource }, gradle, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * projectRegistry.addProject(project)
    }

    def "creates a child project"() {
        def parent = Mock(ProjectInternal)
        def buildFile = tmpDir.file("build.gradle")
        def projectDir = tmpDir.createFile("project")

        given:
        projectDescriptor.name >> "name"
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile
        gradle.projectRegistry >> projectRegistry

        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, parent, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", parent, projectDir, buildFile, _, gradle, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * projectRegistry.addProject(project)
    }
}
