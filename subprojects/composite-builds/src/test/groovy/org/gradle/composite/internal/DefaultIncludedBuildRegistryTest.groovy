/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.initialization.layout.BuildLayout
import org.gradle.internal.Actions
import org.gradle.internal.build.BuildAddedListener
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.IncludedBuildFactory
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.session.CrossBuildSessionState
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification

class DefaultIncludedBuildRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def includedBuildFactory = Stub(IncludedBuildFactory)
    def buildAddedListener = Mock(BuildAddedListener)
    def listenerManager = Stub(ListenerManager) {
        getBroadcaster(BuildAddedListener) >> buildAddedListener
    }
    def services = new DefaultServiceRegistry()
    def modelServices = Mock(BuildModelControllerServices)
    def buildTree = Mock(BuildTreeState)
    def factory = new BuildStateFactory(
        buildTree,
        listenerManager,
        Stub(GradleUserHomeScopeServiceRegistry),
        Stub(CrossBuildSessionState),
        Stub(BuildCancellationToken)
    )
    def registry = new DefaultIncludedBuildRegistry(
        includedBuildFactory,
        listenerManager,
        factory
    )

    def setup() {
        services.add(Stub(WorkerLeaseService))
        services.add(Stub(BuildTreeWorkGraphController))
        services.add(Stub(ExceptionAnalyser))
        services.add(Stub(BuildOperationExecutor))
        services.add(Stub(BuildStateRegistry))
        services.add(Stub(BuildTreeLifecycleControllerFactory))
        services.add(Stub(BuildModelParameters))
        services.add(Stub(GradleInternal))
        services.add(Stub(DocumentationRegistry))
        services.add(modelServices)

        _ * buildTree.services >> services
    }

    def "is empty by default"() {
        expect:
        registry.includedBuilds.empty
    }

    def "can add a root build"() {
        def notifiedBuild
        def buildDefinition = Stub(BuildDefinition)
        def buildController = buildController()
        services.add(buildController)

        when:
        def rootBuild = registry.createRootBuild(buildDefinition)

        then:
        1 * modelServices.servicesForBuild(buildDefinition, _, null) >> Mock(BuildModelControllerServices.Supplier)
        1 * buildAddedListener.buildAdded(_) >> { BuildState addedBuild ->
            notifiedBuild = addedBuild
        }

        !rootBuild.implicitBuild
        rootBuild.buildIdentifier == DefaultBuildIdentifier.ROOT
        rootBuild.identityPath == Path.ROOT
        notifiedBuild.is(rootBuild)

        registry.getBuild(rootBuild.buildIdentifier).is(rootBuild)
    }

    def "can add an included build"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition = build(dir)
        def includedBuild = Stub(IncludedBuildState)
        def buildIdentifier = buildIdentifier(":b1")
        includedBuild.buildIdentifier >> buildIdentifier
        includedBuild.buildRootDir >> dir

        given:
        registry.attachRootBuild(rootBuild())
        includedBuildFactory.createBuild(buildIdentifier, buildDefinition, false, _ as BuildState) >> includedBuild

        when:
        def result = registry.addIncludedBuild(buildDefinition)
        then:
        1 * buildAddedListener.buildAdded(includedBuild)
        0 * _

        result.is includedBuild

        registry.includedBuilds as List == [includedBuild]

        registry.getBuild(buildIdentifier).is(includedBuild)
        registry.getIncludedBuild(buildIdentifier).is(includedBuild)
    }

    def "can add multiple included builds"() {
        def buildDefinition1 = build("b1")
        def buildDefinition2 = build("b2")
        def includedBuild1 = expectIncludedBuildAdded("b1", buildDefinition1)
        def includedBuild2 = expectIncludedBuildAdded("b2", buildDefinition2)

        given:
        registry.attachRootBuild(rootBuild())

        registry.addIncludedBuild(buildDefinition1)
        registry.addIncludedBuild(buildDefinition2)

        expect:
        registry.includedBuilds as List == [includedBuild1, includedBuild2]
    }

    def "can add multiple builds with same dir base name"() {
        def dir1 = tmpDir.createDir("b1")
        def dir2 = tmpDir.createDir("other/b1")
        def dir3 = tmpDir.createDir("other2/b1")
        def buildDefinition1 = build(dir1, "b1")
        def buildDefinition2 = build(dir2, "b2")
        def buildDefinition3 = build(dir3, "b3")
        def includedBuild1 = expectIncludedBuildAdded("b1", buildDefinition1)
        def includedBuild2 = expectIncludedBuildAdded("b2", buildDefinition2)
        def includedBuild3 = expectIncludedBuildAdded("b3", buildDefinition3)

        given:
        registry.attachRootBuild(rootBuild())

        expect:
        registry.addIncludedBuild(buildDefinition1)
        registry.addIncludedBuild(buildDefinition2)
        registry.addIncludedBuild(buildDefinition3)

        registry.includedBuilds as List == [includedBuild1, includedBuild2, includedBuild3]

        registry.getBuild(buildIdentifier(":b1")).is(includedBuild1)
    }

    def "can add the same included build multiple times"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition1 = build(dir)
        def buildDefinition2 = build(dir)
        def includedBuild = expectIncludedBuildAdded("b1", buildDefinition1)

        given:
        registry.attachRootBuild(rootBuild())
        registry.addIncludedBuild(buildDefinition1)

        expect:
        registry.addIncludedBuild(buildDefinition2) is includedBuild
    }

    def "can add an implicit included build"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition = build(dir)
        def includedBuild = Stub(IncludedBuildState)
        includedBuild.buildRootDir >> dir

        given:
        registry.attachRootBuild(rootBuild())
        includedBuildFactory.createBuild(buildIdentifier(":b1"), buildDefinition, true, _ as BuildState) >> includedBuild

        when:
        def result = registry.addImplicitIncludedBuild(buildDefinition)
        then:
        1 * buildAddedListener.buildAdded(includedBuild)
        0 * _

        result.is(includedBuild)

        registry.includedBuilds as List == [includedBuild]
    }

    def "add buildSrc nested build when owner is registered"() {
        given:
        def rootDir = tmpDir.createDir("root")
        rootDir.file("buildSrc/build.gradle").createFile()

        def rootBuild = rootBuild(rootDir)
        def notifiedBuilds = []

        when:
        registry.attachRootBuild(rootBuild)

        then:
        2 * buildAddedListener.buildAdded(_) >> { BuildState addedBuild ->
            notifiedBuilds << addedBuild
        }

        and:
        def nestedBuild = registry.getBuildSrcNestedBuild(rootBuild)
        nestedBuild != null
        nestedBuild.implicitBuild
        nestedBuild.buildIdentifier == buildIdentifier(":buildSrc")
        nestedBuild.identityPath == Path.path(":buildSrc")

        and:
        notifiedBuilds == [rootBuild, nestedBuild]

        and:
        registry.getBuild(nestedBuild.buildIdentifier).is(nestedBuild)
    }

    def "can add multiple buildSrc builds with different levels of nesting"() {
        given:
        def rootDir = tmpDir.createDir("root")
        rootDir.file("buildSrc/build.gradle").createFile()

        def rootBuild = rootBuild(rootDir)
        registry.attachRootBuild(rootBuild)

        def parentDir = rootDir.file("parent").createDir()
        parentDir.file("buildSrc/build.gradle").createFile()

        def parentDefinition = build(parentDir, "parent")
        def parent = expectIncludedBuildAdded("parent", parentDefinition)

        registry.addIncludedBuild(parentDefinition)

        expect:
        def nestedBuild1 = registry.getBuildSrcNestedBuild(rootBuild)
        nestedBuild1.buildIdentifier == buildIdentifier(":buildSrc")
        nestedBuild1.identityPath == Path.path(":buildSrc")

        def nestedBuild2 = registry.getBuildSrcNestedBuild(parent)
        // Shows current behaviour, not necessarily desired behaviour
        nestedBuild2.buildIdentifier == buildIdentifier(":parent:buildSrc")
        nestedBuild2.identityPath == Path.path(":parent:buildSrc")
    }

    def build(String name) {
        return build(tmpDir.createDir(name), name)
    }

    def build(File rootDir, String name = rootDir.name) {
        return BuildDefinition.fromStartParameterForBuild(
            StartParameterInternal.getConstructor().newInstance(),
            name,
            rootDir,
            PluginRequests.EMPTY,
            Actions.doNothing(),
            null,
            false
        )
    }

    IncludedBuildState expectIncludedBuildAdded(String name, BuildDefinition buildDefinition) {
        def idPath = Path.path(":$name")
        def buildIdentifier = new DefaultBuildIdentifier(idPath)

        def gradle = Stub(GradleInternal)
        def services = Stub(ServiceRegistry)

        def includedBuild = Stub(IncludedBuildState)
        includedBuild.buildRootDir >> buildDefinition.buildRootDir
        includedBuild.identityPath >> idPath
        includedBuild.buildIdentifier >> buildIdentifier
        includedBuild.mutableModel >> gradle

        gradle.services >> services

        services.get(PublicBuildPath) >> Stub(PublicBuildPath)

        includedBuildFactory.createBuild(buildIdentifier, buildDefinition, false, _) >> includedBuild

        return includedBuild
    }

    RootBuildState rootBuild(TestFile rootDir = tmpDir.createDir("root-dir")) {
        def settings = Stub(SettingsInternal)
        def gradle = Stub(GradleInternal)
        def buildController = buildController(settings, gradle)
        def build = Stub(RootBuildState)

        services.add(buildController)
        modelServices.servicesForBuild(_, _, _) >> Mock(BuildModelControllerServices.Supplier)
        settings.rootProject >> Stub(ProjectDescriptor) {
            getName() >> "root"
        }
        settings.findProject(_) >> {
            it[0] in projects ? Stub(ProjectDescriptor) : null
        }

        build.buildIdentifier >> DefaultBuildIdentifier.ROOT
        build.identityPath >> Path.ROOT
        build.loadedSettings >> settings
        build.mutableModel >> gradle
        build.buildRootDir >> rootDir
        return build
    }

    private BuildLifecycleController buildController() {
        def settings = Stub(SettingsInternal)
        def gradle = Stub(GradleInternal)
        return buildController(settings, gradle)
    }

    private BuildLifecycleController buildController(SettingsInternal settings, GradleInternal gradle) {
        def buildController = Stub(BuildLifecycleController)
        def services = Stub(ServiceRegistry)
        def buildLayout = Stub(BuildLayout)

        _ * buildController.gradle >> gradle
        if (settings != null) {
            _ * gradle.settings >> settings
        }
        _ * gradle.services >> services
        _ * services.get(BuildLayout) >> buildLayout
        _ * buildLayout.rootDirectory >> tmpDir.file("root-dir")
        _ * services.get(PublicBuildPath) >> Stub(PublicBuildPath)

        return buildController
    }

    private BuildIdentifier buildIdentifier(String path) {
        new DefaultBuildIdentifier(Path.path(path))
    }
}
