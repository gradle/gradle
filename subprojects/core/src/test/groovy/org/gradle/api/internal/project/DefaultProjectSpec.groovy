/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.AntBuilder
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.configuration.internal.ListenerBuildOperationDecorator
import org.gradle.internal.Factory
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.resource.DefaultTextFileResourceLoader
import org.gradle.internal.scripts.ProjectScopedScriptResolution
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultProjectSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "can create file collection configured with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))
        def action = { files -> files.builtBy('something') } as Action<ConfigurableFileCollection>

        when:
        def fileCollection = project.files('path', action)

        then:
        fileCollection.builtBy == ['something'] as Set
    }

    def "can create file tree configured with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))
        def action = { fileTree -> fileTree.builtBy('something') } as Action<ConfigurableFileTree>

        when:
        def fileTree = project.fileTree('path', action)

        then:
        fileTree.builtBy == ['something'] as Set
    }

    def "can configure ant tasks with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))

        when:
        project.ant({ AntBuilder ant -> ant.importBuild('someAntBuild') } as Action<AntBuilder>)

        then:
        1 * project.ant.importBuild('someAntBuild')
    }

    def "can configure artifacts with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))

        when:
        project.artifacts({ artifacts -> artifacts.add('foo', 'bar') } as Action<ArtifactHandler>)

        then:
        1 * project.artifacts.add('foo', 'bar')
    }

    def "has useful toString and displayName and paths"() {
        def rootBuild = Stub(GradleInternal)
        rootBuild.isRootBuild() >> true
        rootBuild.parent >> null
        rootBuild.identityPath >> Path.ROOT

        def nestedBuild = Stub(GradleInternal)
        rootBuild.isRootBuild() >> false
        nestedBuild.parent >> rootBuild
        nestedBuild.identityPath >> Path.path(":nested")

        def rootProject = project("root", null, rootBuild)
        def child1 = project("child1", rootProject, rootBuild)
        def child2 = project("child2", child1, rootBuild)

        def nestedRootProject = project("root", null, nestedBuild)
        def nestedChild1 = project("child1", nestedRootProject, nestedBuild)
        def nestedChild2 = project("child2", nestedChild1, nestedBuild)

        expect:
        rootProject.toString() == "root project 'root'"
        rootProject.displayName == "root project 'root'"
        rootProject.path == ":"
        rootProject.buildTreePath == ':'
        rootProject.identityPath == Path.ROOT

        child1.toString() == "project ':child1'"
        child1.displayName == "project ':child1'"
        child1.path == ":child1"
        child1.buildTreePath == ":child1"
        child1.identityPath == Path.path(":child1")

        child2.toString() == "project ':child1:child2'"
        child2.displayName == "project ':child1:child2'"
        child2.path == ":child1:child2"
        child2.buildTreePath == ":child1:child2"
        child2.identityPath == Path.path(":child1:child2")

        nestedRootProject.toString() == "project ':nested'"
        nestedRootProject.displayName == "project ':nested'"
        nestedRootProject.path == ":"
        nestedRootProject.buildTreePath == ":nested"
        nestedRootProject.identityPath == Path.path(":nested")

        nestedChild1.toString() == "project ':nested:child1'"
        nestedChild1.displayName == "project ':nested:child1'"
        nestedChild1.path == ":child1"
        nestedChild1.buildTreePath == ":nested:child1"
        nestedChild1.identityPath == Path.path(":nested:child1")

        nestedChild2.toString() == "project ':nested:child1:child2'"
        nestedChild2.displayName == "project ':nested:child1:child2'"
        nestedChild2.path == ":child1:child2"
        nestedChild2.buildTreePath == ":nested:child1:child2"
        nestedChild2.identityPath == Path.path(":nested:child1:child2")
    }

    ProjectInternal project(String name, ProjectInternal parent, GradleInternal build) {
        def fileOperations = Stub(FileOperations) {
            fileTree(_) >> TestFiles.fileOperations(tmpDir.testDirectory, new DefaultTemporaryFileProvider(() -> new File(tmpDir.testDirectory, "cache"))).fileTree('tree')
        }
        def propertyFactory = new DefaultPropertyFactory(Stub(PropertyHost))
        def objectFactory = Stub(ObjectFactory) {
            fileCollection() >> TestFiles.fileCollectionFactory().configurableFiles()
            property(Object) >> propertyFactory.property(Object)
        }

        def serviceRegistry = new DefaultServiceRegistry()

        serviceRegistry.add(FileOperations, fileOperations)
        serviceRegistry.add(ObjectFactory, objectFactory)
        serviceRegistry.add(TaskContainerInternal, Stub(TaskContainerInternal))
        serviceRegistry.add(InstantiatorFactory, Stub(InstantiatorFactory))
        serviceRegistry.add(AttributesSchema, Stub(AttributesSchema))
        serviceRegistry.add(ModelRegistry, Stub(ModelRegistry))
        serviceRegistry.add(CrossProjectModelAccess, Stub(CrossProjectModelAccess))
        serviceRegistry.add(DependencyResolutionManagementInternal, Stub(DependencyResolutionManagementInternal))
        serviceRegistry.add(DynamicLookupRoutine, new DefaultDynamicLookupRoutine())
        serviceRegistry.add(SoftwareComponentContainer, Mock(SoftwareComponentContainer))
        serviceRegistry.add(CrossProjectConfigurator, Mock(CrossProjectConfigurator))
        serviceRegistry.add(ListenerBuildOperationDecorator, Mock(ListenerBuildOperationDecorator))
        serviceRegistry.add(ArtifactHandler, Mock(ArtifactHandler))

        def antBuilder = Mock(AntBuilder)
        serviceRegistry.addProvider(new Object() {
            Factory<AntBuilder> createAntBuilder() {
                return () -> antBuilder
            }
        })

        def serviceRegistryFactory = Stub(ServiceRegistryFactory) {
            createFor(_) >> serviceRegistry
        }

        build.services >> serviceRegistry

        def container = Mock(ProjectState)
        _ * container.projectPath >> (parent == null ? Path.ROOT : parent.projectPath.child(name))
        _ * container.identityPath >> (parent == null ? build.identityPath : build.identityPath.append(parent.projectPath).child(name))

        def descriptor = Mock(ProjectDescriptor) {
            getName() >> name
            getProjectDir() >> new File("project")
            getBuildFile() >> new File("build file")
        }

        def scriptResolution = Stub(ProjectScopedScriptResolution) {
            resolveScriptsForProject(_, _) >> { p, a -> a.get() }
        }

        def instantiator = TestUtil.instantiatorFactory().decorateLenient(serviceRegistry)
        def factory = new ProjectFactory(instantiator, new DefaultTextFileResourceLoader(null), scriptResolution)
        return factory.createProject(build, descriptor, container, parent, serviceRegistryFactory, Stub(ClassLoaderScope), Stub(ClassLoaderScope))
    }
}
