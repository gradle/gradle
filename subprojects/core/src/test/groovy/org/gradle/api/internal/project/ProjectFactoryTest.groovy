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

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.DependenciesAccessors
import org.gradle.internal.build.BuildState
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.project.ImmutableProjectDescriptor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.DefaultTextFileResourceLoader
import org.gradle.internal.resource.EmptyFileTextResource
import org.gradle.internal.scripts.ProjectScopedScriptResolution
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
    def serviceRegistryFactory = Stub(ServiceRegistryFactory)
    def project = Stub(DefaultProject)
    def owner = Stub(BuildState)
    def projectState = Stub(ProjectState)
    def scriptResolution = Stub(ProjectScopedScriptResolution) {
        resolveScriptsForProject(_, _) >> { project, action -> action.get() }
    }
    def dependencyResolutionManagement = Mock(DependencyResolutionManagementInternal)
    def dependenciesAccessors = Mock(DependenciesAccessors)
    def projectRegistry = Mock(ProjectRegistry)
    def factory = new ProjectFactory(
        instantiator,
        new DefaultTextFileResourceLoader(),
        scriptResolution,
        dependencyResolutionManagement,
        dependenciesAccessors,
        projectRegistry
    )
    def rootProjectScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)

    def setup() {
        owner.identityPath >> buildId

        projectDescriptor.identity >> projectIdentity
        projectDescriptor.projectDir >> projectDir
        projectDescriptor.buildFile >> buildFile

        projectState.identity >> projectIdentity
        projectState.owner >> owner
        projectState.descriptor >> projectDescriptor

        projectDir.createDir()
    }

    def "creates a project with build script"() {
        given:
        buildFile.createFile()

        when:
        def result = factory.createProject(projectState, rootProjectScope, baseScope, serviceRegistryFactory)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, projectState, rootProjectScope, baseScope, { it instanceof TextResourceScriptSource }, serviceRegistryFactory) >> project
        1 * projectRegistry.addProject(project)
        1 * dependencyResolutionManagement.configureProject(project)
    }

    def "creates a project with missing build script"() {
        when:
        def result = factory.createProject(projectState, rootProjectScope, baseScope, serviceRegistryFactory)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, projectState, rootProjectScope, baseScope, { it.resource instanceof EmptyFileTextResource }, serviceRegistryFactory) >> project
        1 * projectRegistry.addProject(project)
        1 * dependencyResolutionManagement.configureProject(project)
    }

    def "creates a child project"() {
        when:
        def result = factory.createProject(projectState, rootProjectScope, baseScope, serviceRegistryFactory)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, projectState, rootProjectScope, baseScope, _, serviceRegistryFactory) >> project
        1 * projectRegistry.addProject(project)
        1 * dependencyResolutionManagement.configureProject(project)
    }
}
