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
import org.gradle.initialization.ProjectDescriptorInternal
import org.gradle.internal.management.DependencyResolutionManagementInternal
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
    def projectDescriptor = Stub(ProjectDescriptorInternal) {
       getName() >> "name"
       getProjectDir() >> tmpDir.file("project")
       getBuildFile() >> tmpDir.file("build.gradle")
    }
    def serviceRegistryFactory = Stub(ServiceRegistryFactory)
    def project = Stub(DefaultProject)
    def buildId = Path.ROOT
    def projectState = Stub(ProjectState) {
        getIdentity() >> { ProjectIdentity.forRootProject(buildId, projectDescriptor.name) }
    }
    def scriptResolution = Stub(ProjectScopedScriptResolution) {
        resolveScriptsForProject(_, _) >> { project, action -> action.get() }
    }
    def factory = new ProjectFactory(instantiator, new DefaultTextFileResourceLoader(), scriptResolution)
    def rootProjectScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)
    def dependencyResolutionManagement = Mock(DependencyResolutionManagementInternal)
    def serviceRegistry = Mock(ServiceRegistry) {
        get(DependencyResolutionManagementInternal) >> dependencyResolutionManagement
    }
    def gradle = Stub(GradleInternal) {
        getServices() >> serviceRegistry
    }

    def "creates a project with build script"() {
        given:
        projectDescriptor.projectDir.mkdirs()
        projectDescriptor.buildFile.createNewFile()

        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, projectDescriptor.buildFile, { it instanceof TextResourceScriptSource }, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * dependencyResolutionManagement.configureProject(project)
    }

    def "creates a project with missing build script"() {
        given:
        projectDescriptor.projectDir.mkdirs()
        assert !projectDescriptor.buildFile.exists()

        when:
        def result = factory.createProject(gradle, projectDescriptor, projectState, serviceRegistryFactory, rootProjectScope, baseScope)

        then:
        result == project
        1 * instantiator.newInstance(DefaultProject, projectDescriptor.buildFile, { it.resource instanceof EmptyFileTextResource }, projectState, serviceRegistryFactory, rootProjectScope, baseScope) >> project
        1 * dependencyResolutionManagement.configureProject(project)
    }
}
