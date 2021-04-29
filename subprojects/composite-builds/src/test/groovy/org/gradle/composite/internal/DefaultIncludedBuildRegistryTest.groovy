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

import org.gradle.api.GradleException
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.exception.ExceptionAnalyser
import org.gradle.internal.Actions
import org.gradle.internal.build.BuildAddedListener
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.IncludedBuildFactory
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.buildtree.BuildTreeController
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.session.CrossBuildSessionState
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.plugin.management.internal.PluginRequests
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
    def gradleLauncherFactory = Mock(BuildLifecycleControllerFactory)
    def buildTree = Mock(BuildTreeController)
    def factory = new BuildStateFactory(buildTree, gradleLauncherFactory, Stub(BuildModelControllerServices), listenerManager, Stub(GradleUserHomeScopeServiceRegistry), Stub(CrossBuildSessionState), Stub(BuildCancellationToken))
    def registry = new DefaultIncludedBuildRegistry(
        includedBuildFactory,
        Stub(IncludedBuildDependencySubstitutionsBuilder),
        listenerManager,
        factory
    )

    def "is empty by default"() {
        expect:
        registry.includedBuilds.empty
    }

    def "can add a root build"() {
        def notifiedBuild
        def buildDefinition = Stub(BuildDefinition)
        def gradleLauncher = Stub(BuildLifecycleController)
        def gradle = Stub(GradleInternal)
        def services = Stub(ServiceRegistry)

        when:
        def rootBuild = registry.createRootBuild(buildDefinition)

        then:
        _ * buildTree.services >> new DefaultServiceRegistry()
        _ * gradleLauncher.gradle >> gradle
        _ * gradle.services >> services
        _ * services.get(WorkerLeaseService) >> Stub(WorkerLeaseService)
        _ * services.get(IncludedBuildControllers) >> Stub(IncludedBuildControllers)
        _ * services.get(ExceptionAnalyser) >> Stub(ExceptionAnalyser)
        _ * services.get(BuildOperationExecutor) >> Stub(BuildOperationExecutor)
        1 * gradleLauncherFactory.newInstance(buildDefinition, _, null, _) >> gradleLauncher
        1 * buildAddedListener.buildAdded(_) >> { BuildState addedBuild ->
            notifiedBuild = addedBuild
        }
        0 * _

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
        def buildIdentifier = new DefaultBuildIdentifier("b1")
        def idPath = Path.path(":b1")
        includedBuild.buildIdentifier >> buildIdentifier

        given:
        registry.attachRootBuild(rootBuild())
        includedBuildFactory.createBuild(buildIdentifier, idPath, buildDefinition, false, _ as BuildState) >> includedBuild

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
        def dir1 = tmpDir.createDir("b1")
        def dir2 = tmpDir.createDir("b2")
        def buildDefinition1 = build(dir1)
        def buildDefinition2 = build(dir2)
        def includedBuild1 = Stub(IncludedBuildState)
        def includedBuild2 = Stub(IncludedBuildState)

        given:
        registry.attachRootBuild(rootBuild())
        includedBuildFactory.createBuild(new DefaultBuildIdentifier("b1"), Path.path(":b1"), buildDefinition1, false, _) >> includedBuild1
        includedBuildFactory.createBuild(new DefaultBuildIdentifier("b2"), Path.path(":b2"), buildDefinition2, false, _) >> includedBuild2

        expect:
        registry.addIncludedBuild(buildDefinition1)
        registry.addIncludedBuild(buildDefinition2)

        registry.includedBuilds as List == [includedBuild1, includedBuild2]
    }

    def "can add multiple builds with same dir base name"() {
        def dir1 = tmpDir.createDir("b1")
        def dir2 = tmpDir.createDir("other/b1")
        def dir3 = tmpDir.createDir("other2/b1")
        def buildDefinition1 = build(dir1, "b1")
        def buildDefinition2 = build(dir2, "b2")
        def buildDefinition3 = build(dir3, "b3")
        def id1 = new DefaultBuildIdentifier("b1")
        def id2 = new DefaultBuildIdentifier("b2")
        def id3 = new DefaultBuildIdentifier("b3")
        def idPath1 = Path.path(":b1")
        def idPath2 = Path.path(":b2")
        def idPath3 = Path.path(":b3")
        def includedBuild1 = Stub(IncludedBuildState) { getBuildIdentifier() >> id1 }
        def includedBuild2 = Stub(IncludedBuildState) { getBuildIdentifier() >> id2 }
        def includedBuild3 = Stub(IncludedBuildState) { getBuildIdentifier() >> id3 }
        includedBuild1.identityPath >> idPath1
        includedBuild2.identityPath >> idPath2
        includedBuild3.identityPath >> idPath3

        given:
        registry.attachRootBuild(rootBuild())
        includedBuildFactory.createBuild(id1, idPath1, buildDefinition1, false, _) >> includedBuild1
        includedBuildFactory.createBuild(id2, idPath2, buildDefinition2, false, _) >> includedBuild2
        includedBuildFactory.createBuild(id3, idPath3, buildDefinition3, false, _) >> includedBuild3

        expect:
        registry.addIncludedBuild(buildDefinition1)
        registry.addIncludedBuild(buildDefinition2)
        registry.addIncludedBuild(buildDefinition3)

        registry.includedBuilds as List == [includedBuild1, includedBuild2, includedBuild3]

        registry.getBuild(id1).is(includedBuild1)
    }

    def "can add the same included build multiple times"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition1 = build(dir)
        def buildDefinition2 = build(dir)

        given:
        registry.attachRootBuild(rootBuild())
        def includedBuild = registry.addIncludedBuild(buildDefinition1)

        expect:
        registry.addIncludedBuild(buildDefinition2) is includedBuild
    }

    def "can add an implicit included build"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition = build(dir)
        def includedBuild = Stub(IncludedBuildState)

        given:
        registry.attachRootBuild(rootBuild())
        includedBuildFactory.createBuild(new DefaultBuildIdentifier("b1"), Path.path(":b1"), buildDefinition, true, _ as BuildState) >> includedBuild

        when:
        def result = registry.addImplicitIncludedBuild(buildDefinition)
        then:
        1 * buildAddedListener.buildAdded(includedBuild)
        0 * _

        result.is(includedBuild)

        registry.includedBuilds as List == [includedBuild]
    }

    def "can add a buildSrc nested build"() {
        given:
        def buildDefinition = Stub(BuildDefinition)
        buildDefinition.name >> "buildSrc"
        registry.attachRootBuild(rootBuild())
        def owner = Stub(BuildState) { getIdentityPath() >> Path.ROOT }
        def notifiedBuild

        when:
        def nestedBuild = registry.addBuildSrcNestedBuild(buildDefinition, owner)
        then:
        1 * buildAddedListener.buildAdded(_) >> { BuildState addedBuild ->
            notifiedBuild = addedBuild
        }

        nestedBuild.implicitBuild
        nestedBuild.buildIdentifier == new DefaultBuildIdentifier("buildSrc")
        nestedBuild.identityPath == Path.path(":buildSrc")
        notifiedBuild.is(nestedBuild)

        registry.getBuild(nestedBuild.buildIdentifier).is(nestedBuild)
    }

    def "cannot add multiple buildSrc nested builds with same name"() {
        given:
        def buildDefinition = Stub(BuildDefinition)
        buildDefinition.name >> "buildSrc"
        registry.attachRootBuild(rootBuild())
        def owner = Stub(BuildState) { getIdentityPath() >> Path.ROOT }

        when:
        registry.addBuildSrcNestedBuild(buildDefinition, owner)
        registry.addBuildSrcNestedBuild(buildDefinition, owner)

        then:
        thrown GradleException
    }

    def "can add multiple buildSrc nested builds with same name and different levels of nesting"() {
        given:
        def rootBuild = rootBuild()
        registry.attachRootBuild(rootBuild)

        def parent1Definition = Stub(BuildDefinition)
        parent1Definition.name >> "parent"
        def parent1 = registry.addIncludedBuild(parent1Definition)

        def buildDefinition = Stub(BuildDefinition)
        buildDefinition.name >> "buildSrc"
        buildDefinition.buildRootDir >> new File("d")

        expect:
        def nestedBuild1 = registry.addBuildSrcNestedBuild(buildDefinition, rootBuild)
        nestedBuild1.buildIdentifier == new DefaultBuildIdentifier("buildSrc")
        nestedBuild1.identityPath == Path.path(":buildSrc")

        def nestedBuild2 = registry.addBuildSrcNestedBuild(buildDefinition, parent1)
        // Shows current behaviour, not necessarily desired behaviour
        nestedBuild2.buildIdentifier == new DefaultBuildIdentifier("buildSrc:1")
        nestedBuild2.identityPath == Path.path(":parent:buildSrc")

        def nestedBuild3 = registry.addBuildSrcNestedBuild(buildDefinition, nestedBuild1)
        nestedBuild3.buildIdentifier == new DefaultBuildIdentifier("buildSrc:2")
        nestedBuild3.identityPath == Path.path(":buildSrc:buildSrc")
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

    RootBuildState rootBuild(String... projects) {
        def gradleLauncher = Stub(BuildLifecycleController)
        def parentGradle = Stub(GradleInternal)
        def gradle = Stub(GradleInternal)
        def settings = Stub(SettingsInternal)
        def services = Stub(ServiceRegistry)
        def build = Stub(RootBuildState)

        buildTree.services >> new DefaultServiceRegistry()

        gradleLauncherFactory.newInstance(_, _, _, _) >> gradleLauncher
        gradleLauncher.gradle >> gradle
        gradle.services >> services
        gradle.settings >> settings
        settings.rootProject >> Stub(ProjectDescriptor) {
            getName() >> "root"
        }
        settings.findProject(_) >> {
            it[0] in projects ? Stub(ProjectDescriptor) : null
        }

        build.buildIdentifier >> DefaultBuildIdentifier.ROOT
        build.identityPath >> Path.ROOT
        build.loadedSettings >> settings
        build.mutableModel >> parentGradle
        return build
    }
}
