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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.build.BuildState
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.project.ImmutableProjectDescriptor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.DefaultTextFileResourceLoader
import org.gradle.internal.resource.EmptyFileTextResource
import org.gradle.internal.scripts.ProjectScopedScriptResolution
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification

class ProjectFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def instantiator = Mock(Instantiator)
    def buildId = Path.ROOT
    def buildFile = tmpDir.file("build.gradle")
    def projectDir = tmpDir.file("project")
    def projectIdentity = ProjectIdentity.forRootProject(buildId, "name")
    def projectDescriptor = Stub(ImmutableProjectDescriptor)
    def gradle = Stub(GradleInternal)
    def serviceRegistryFactory = Stub(ServiceRegistryFactory)
    def projectRegistry = Mock(ProjectRegistry)
    def project = Stub(DefaultProject)
    def owner = Stub(BuildState)
    def projectState = Stub(ProjectState)
    def scriptResolution = Stub(ProjectScopedScriptResolution) {
        resolveScriptsForProject(_, _) >> { project, action -> action.get() }
    }
    def factory = new ProjectFactory(instantiator, new DefaultTextFileResourceLoader(), scriptResolution)
    def rootProjectScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)
    def serviceRegistry = Mock(ServiceRegistry)
    def dependencyResolutionManagement = Mock(DependencyResolutionManagementInternal)

    def setup() {
        owner.identityPath >> buildId

        projectDescriptor.identity >> projectIdentity
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile

        projectState.identity >> projectIdentity

        projectDir.createDir()
    }

    def "creates a project with build script"() {
        given:
        buildFile.createFile()
        gradle.projectRegistry >> projectRegistry
        gradle.services >> serviceRegistry
        serviceRegistry.get(DependencyResolutionManagementInternal) >> dependencyResolutionManagement

        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, null, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", null, projectDir, buildFile, { it instanceof TextResourceScriptSource }, gradle, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * projectRegistry.addProject(project)
        1 * dependencyResolutionManagement.configureProject(project)
    }

    def "creates a project with missing build script"() {
        given:
        gradle.projectRegistry >> projectRegistry
        gradle.services >> serviceRegistry
        serviceRegistry.get(DependencyResolutionManagementInternal) >> dependencyResolutionManagement
        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, null, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", null, projectDir, buildFile, { it.resource instanceof EmptyFileTextResource }, gradle, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * projectRegistry.addProject(project)
        1 * dependencyResolutionManagement.configureProject(project)
    }

    def "creates a child project"() {
        def parent = Mock(ProjectInternal)

        given:
        gradle.projectRegistry >> projectRegistry
        gradle.services >> serviceRegistry
        serviceRegistry.get(DependencyResolutionManagementInternal) >> dependencyResolutionManagement
        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, parent, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, "name", parent, projectDir, buildFile, _, gradle, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * projectRegistry.addProject(project)
        1 * dependencyResolutionManagement.configureProject(project)
    }
}
