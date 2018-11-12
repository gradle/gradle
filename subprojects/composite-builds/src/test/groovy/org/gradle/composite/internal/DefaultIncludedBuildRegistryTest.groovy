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

import org.gradle.StartParameter
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.initialization.GradleLauncher
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.initialization.NestedBuildFactory
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.BuildTreeScopeServices
import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification

class DefaultIncludedBuildRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def includedBuildFactory = Stub(IncludedBuildFactory)
    def registry = new DefaultIncludedBuildRegistry(includedBuildFactory, Stub(ProjectStateRegistry), Stub(IncludedBuildDependencySubstitutionsBuilder), Stub(GradleLauncherFactory), Stub(ListenerManager), Stub(BuildTreeScopeServices))

    def "is empty by default"() {
        expect:
        !registry.hasIncludedBuilds()
        registry.includedBuilds.empty
    }

    def "can add a root build"() {
        expect:
        def rootBuild = registry.createRootBuild(Stub(BuildDefinition))
        !rootBuild.implicitBuild
        rootBuild.buildIdentifier == DefaultBuildIdentifier.ROOT
        rootBuild.identityPath == Path.ROOT

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
        includedBuildFactory.createBuild(buildIdentifier, idPath, buildDefinition, false, _) >> includedBuild

        expect:
        def result = registry.addIncludedBuild(buildDefinition)
        result == includedBuild

        registry.hasIncludedBuilds()
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

        registry.hasIncludedBuilds()
        registry.includedBuilds as List == [includedBuild1, includedBuild2]
    }

    def "can add multiple builds with same dir base name"() {
        def dir1 = tmpDir.createDir("b1")
        def dir2 = tmpDir.createDir("other/b1")
        def dir3 = tmpDir.createDir("other2/b1")
        def buildDefinition1 = build(dir1)
        def buildDefinition2 = build(dir2)
        def buildDefinition3 = build(dir3)
        def includedBuild1 = Stub(IncludedBuildState)
        def includedBuild2 = Stub(IncludedBuildState)
        def includedBuild3 = Stub(IncludedBuildState)

        // This just demonstrates existing behaviour, not necessarily desired behaviour
        def id1 = new DefaultBuildIdentifier("b1")
        def id2 = new DefaultBuildIdentifier("b1:1")
        def id3 = new DefaultBuildIdentifier("b1:2")
        includedBuild1.buildIdentifier >> id1
        includedBuild2.buildIdentifier >> id2
        includedBuild3.buildIdentifier >> id3

        def idPath1 = Path.path(":b1")
        def idPath2 = Path.path(":b1:1")
        def idPath3 = Path.path(":b1:2")

        given:
        registry.attachRootBuild(rootBuild())
        includedBuildFactory.createBuild(id1, idPath1, buildDefinition1, false, _) >> includedBuild1
        includedBuildFactory.createBuild(id2, idPath2, buildDefinition2, false, _) >> includedBuild2
        includedBuildFactory.createBuild(id3, idPath3, buildDefinition3, false, _) >> includedBuild3

        expect:
        registry.addIncludedBuild(buildDefinition1)
        registry.addIncludedBuild(buildDefinition2)
        registry.addIncludedBuild(buildDefinition3)

        registry.hasIncludedBuilds()
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
        includedBuildFactory.createBuild(new DefaultBuildIdentifier("b1"), Path.path(":b1"), buildDefinition, true, _) >> includedBuild

        expect:
        def result = registry.addImplicitIncludedBuild(buildDefinition)
        result == includedBuild

        registry.hasIncludedBuilds()
        registry.includedBuilds as List == [includedBuild]
    }

    def "can add a nested build"() {
        given:
        def buildDefinition = Stub(BuildDefinition)
        buildDefinition.name >> "nested"

        expect:
        def nestedBuild = registry.addNestedBuild(buildDefinition, Stub(BuildState))
        nestedBuild.implicitBuild
        nestedBuild.buildIdentifier == new DefaultBuildIdentifier("nested")
        nestedBuild.identityPath == Path.path(":nested")

        registry.getBuild(nestedBuild.buildIdentifier).is(nestedBuild)
    }

    def "can add multiple nested builds with same name"() {
        given:
        def buildDefinition = Stub(BuildDefinition)
        buildDefinition.name >> "nested"

        expect:
        def nestedBuild1 = registry.addNestedBuild(buildDefinition, Stub(BuildState))
        nestedBuild1.buildIdentifier == new DefaultBuildIdentifier("nested")
        nestedBuild1.identityPath == Path.path(":nested")

        def nestedBuild2 = registry.addNestedBuild(buildDefinition, Stub(BuildState))
        nestedBuild2.buildIdentifier == new DefaultBuildIdentifier("nested:1")
        nestedBuild2.identityPath == Path.path(":nested:1")

        def nestedBuild3 = registry.addNestedBuild(buildDefinition, Stub(BuildState))
        nestedBuild3.buildIdentifier == new DefaultBuildIdentifier("nested:2")
        nestedBuild3.identityPath == Path.path(":nested:2")
    }

    def "can add multiple nested builds with same name and different levels of nesting"() {
        given:
        def rootBuild = rootBuild()
        registry.attachRootBuild(rootBuild)

        def parent1Definition = Stub(BuildDefinition)
        parent1Definition.name >> "parent"
        def parent1 = registry.addNestedBuild(parent1Definition, rootBuild)

        def buildDefinition = Stub(BuildDefinition)
        buildDefinition.name >> "nested"

        expect:
        def nestedBuild1 = registry.addNestedBuild(buildDefinition, rootBuild)
        nestedBuild1.buildIdentifier == new DefaultBuildIdentifier("nested")
        nestedBuild1.identityPath == Path.path(":nested")

        def nestedBuild2 = registry.addNestedBuild(buildDefinition, parent1)
        nestedBuild2.buildIdentifier == new DefaultBuildIdentifier("nested:1")
        // Shows current behaviour, not necessarily desired behaviour
        nestedBuild2.identityPath == Path.path(":parent:nested:1")

        def nestedBuild3 = registry.addNestedBuild(buildDefinition, nestedBuild1)
        nestedBuild3.buildIdentifier == new DefaultBuildIdentifier("nested:2")
        nestedBuild3.identityPath == Path.path(":nested:nested:2")

        def nestedBuild4 = registry.addNestedBuild(buildDefinition, parent1)
        nestedBuild4.buildIdentifier == new DefaultBuildIdentifier("nested:3")
        nestedBuild4.identityPath == Path.path(":parent:nested:3")
    }

    def build(File rootDir) {
        return BuildDefinition.fromStartParameterForBuild(StartParameter.getConstructor().newInstance(), null, rootDir, DefaultPluginRequests.EMPTY, null)
    }

    def rootBuild() {
        def nestedBuildFactory = Stub(NestedBuildFactory)
        def gradleLauncher = Stub(GradleLauncher)
        def gradle = Stub(GradleInternal)
        def services = Stub(ServiceRegistry)

        nestedBuildFactory.nestedInstance(_, _) >> gradleLauncher
        gradleLauncher.gradle >> gradle
        gradle.services >> services
        services.get(NestedBuildFactory) >> nestedBuildFactory

        def build = Stub(RootBuildState)
        build.buildIdentifier >> DefaultBuildIdentifier.ROOT
        build.identityPath >> Path.ROOT
        build.nestedBuildFactory >> nestedBuildFactory
        return build
    }
}
