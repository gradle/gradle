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
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.initialization.BuildRequestContext
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.initialization.NestedBuildFactory
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistry
import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultIncludedBuildRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def nestedBuildFactory = Stub(NestedBuildFactory)
    def includedBuildFactory = Stub(IncludedBuildFactory)
    def registry = new DefaultIncludedBuildRegistry(includedBuildFactory, Stub(ProjectStateRegistry), Stub(IncludedBuildDependencySubstitutionsBuilder), Stub(GradleLauncherFactory), Stub(ListenerManager), Stub(ServiceRegistry))

    def "is empty by default"() {
        expect:
        !registry.hasIncludedBuilds()
        registry.includedBuilds.empty
    }

    def "can add a root build"() {
        expect:
        def rootBuild = registry.addRootBuild(Stub(BuildDefinition), Stub(BuildRequestContext))
        !rootBuild.implicitBuild
        rootBuild.buildIdentifier == DefaultBuildIdentifier.ROOT
    }

    def "can add an explicit included build"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition = build(dir)
        def includedBuild = Stub(IncludedBuildState)

        given:
        includedBuildFactory.createBuild(buildDefinition, false, _) >> includedBuild

        expect:
        def result = registry.addExplicitBuild(buildDefinition, nestedBuildFactory)
        result == includedBuild

        registry.hasIncludedBuilds()
        registry.includedBuilds as List == [includedBuild]
    }

    def "can add multiple explicit included build"() {
        def dir1 = tmpDir.createDir("b1")
        def dir2 = tmpDir.createDir("b2")
        def buildDefinition1 = build(dir1)
        def buildDefinition2 = build(dir2)
        def includedBuild1 = Stub(IncludedBuildState)
        def includedBuild2 = Stub(IncludedBuildState)

        given:
        includedBuildFactory.createBuild(buildDefinition1, false, _) >> includedBuild1
        includedBuildFactory.createBuild(buildDefinition2, false, _) >> includedBuild2

        expect:
        registry.addExplicitBuild(buildDefinition1, nestedBuildFactory)
        registry.addExplicitBuild(buildDefinition2, nestedBuildFactory)

        registry.hasIncludedBuilds()
        registry.includedBuilds as List == [includedBuild1, includedBuild2]
    }

    def "can add the same explicit included build multiple times"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition1 = build(dir)
        def buildDefinition2 = build(dir)

        given:
        def includedBuild = registry.addExplicitBuild(buildDefinition1, nestedBuildFactory)

        expect:
        registry.addExplicitBuild(buildDefinition2, nestedBuildFactory) == includedBuild
    }

    def "can add an implicit included build"() {
        def dir = tmpDir.createDir("b1")
        def buildDefinition = build(dir)
        def includedBuild = Stub(IncludedBuildState)

        given:
        includedBuildFactory.createBuild(buildDefinition, true, _) >> includedBuild

        expect:
        def result = registry.addImplicitBuild(buildDefinition, nestedBuildFactory)
        result == includedBuild

        registry.hasIncludedBuilds()
        registry.includedBuilds as List == [includedBuild]
    }

    def "can add a nested build"() {
        expect:
        def nestedBuild = registry.addNestedBuild(Stub(SettingsInternal))
        nestedBuild.implicitBuild
    }

    def build(File rootDir) {
        return BuildDefinition.fromStartParameterForBuild(StartParameter.newInstance(), rootDir, DefaultPluginRequests.EMPTY)
    }
}
