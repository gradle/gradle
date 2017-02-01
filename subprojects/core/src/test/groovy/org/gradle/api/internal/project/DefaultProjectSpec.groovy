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
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.util.Path
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DefaultProjectSpec extends Specification {

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
        def antBuilder = Mock(AntBuilder)
        project.ant >> antBuilder

        when:
        project.ant({ AntBuilder ant -> ant.importBuild('someAntBuild') } as Action<AntBuilder>)

        then:
        1 * antBuilder.importBuild('someAntBuild')
    }

    def "can configure artifacts with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))
        def artifactHandler = Mock(ArtifactHandler)
        project.artifacts >> artifactHandler

        when:
        project.artifacts({ artifacts -> artifacts.add('foo', 'bar') } as Action<ArtifactHandler>)

        then:
        1 * artifactHandler.add('foo', 'bar')
    }

    def "can configure repositories with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))
        def repositoryHandler = Mock(RepositoryHandler)
        project.repositories >> repositoryHandler

        when:
        project.repositories({ repositories -> repositories.jcenter() } as Action<RepositoryHandler>)

        then:
        1 * repositoryHandler.jcenter()
    }

    def "can configure dependencies with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))
        def dependencyHandler = Mock(DependencyHandler)
        project.dependencies >> dependencyHandler

        when:
        project.dependencies({ dependencies -> dependencies.add('foo', 'bar') } as Action<DependencyHandler>)

        then:
        1 * dependencyHandler.add('foo', 'bar')
    }

    def "can configure buildscript with an Action"() {
        given:
        def project = project('root', null, Stub(GradleInternal))
        def scriptHandler = Mock(ScriptHandler)
        project.buildscript >> scriptHandler

        when:
        project.buildscript({ ScriptHandler buildscript -> buildscript.sourceFile } as Action<ScriptHandler>)

        then:
        1 * scriptHandler.sourceFile
    }

    def "has useful toString and displayName and paths"() {
        def rootBuild = Stub(GradleInternal)
        rootBuild.parent >> null
        rootBuild.identityPath >> Path.ROOT

        def nestedBuild = Stub(GradleInternal)
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
        rootProject.identityPath == Path.ROOT

        child1.toString() == "project ':child1'"
        child1.displayName == "project ':child1'"
        child1.path == ":child1"
        child1.identityPath == Path.path(":child1")

        child2.toString() == "project ':child1:child2'"
        child2.displayName == "project ':child1:child2'"
        child2.path == ":child1:child2"
        child2.identityPath == Path.path(":child1:child2")

        nestedRootProject.toString() == "project ':nested'"
        nestedRootProject.displayName == "project ':nested'"
        nestedRootProject.path == ":"
        nestedRootProject.identityPath == Path.path(":nested")

        nestedChild1.toString() == "project ':nested:child1'"
        nestedChild1.displayName == "project ':nested:child1'"
        nestedChild1.path == ":child1"
        nestedChild1.identityPath == Path.path(":nested:child1")

        nestedChild2.toString() == "project ':nested:child1:child2'"
        nestedChild2.displayName == "project ':nested:child1:child2'"
        nestedChild2.path == ":child1:child2"
        nestedChild2.identityPath == Path.path(":nested:child1:child2")
    }

    def project(String name, ProjectInternal parent, GradleInternal build) {
        def instantiator = DirectInstantiator.INSTANCE
        def serviceRegistryFactory = Stub(ServiceRegistryFactory)
        def serviceRegistry = Stub(ServiceRegistry)

        _ * serviceRegistryFactory.createFor(_) >> serviceRegistry
        _ * serviceRegistry.newInstance(TaskContainerInternal) >> Stub(TaskContainerInternal)
        _ * serviceRegistry.get(Instantiator) >> instantiator
        _ * serviceRegistry.get(AttributesSchema) >> Stub(AttributesSchema)
        _ * serviceRegistry.get(ModelRegistry) >> Stub(ModelRegistry)

        def fileResolver = Mock(FileResolver) { getPatternSetFactory() >> TestFiles.getPatternSetFactory() }
        def taskResolver = Mock(TaskResolver)
        def tempFileProvider = Mock(TemporaryFileProvider)
        def fileLookup = Mock(FileLookup)
        def directoryFileTreeFactory = Mock(DefaultDirectoryFileTreeFactory)
        def fileOperations = instantiator.newInstance(DefaultFileOperations, fileResolver, taskResolver, tempFileProvider, instantiator, fileLookup, directoryFileTreeFactory)

        return Spy(DefaultProject, constructorArgs: [name, parent, new File("project"), Stub(ScriptSource), build, serviceRegistryFactory, Stub(ClassLoaderScope), Stub(ClassLoaderScope)]) {
            getFileOperations() >> fileOperations
        }
    }
}
