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

package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Consumer

@CleanupTestDirectory
class GradleBuildBuilderTest extends Specification {
    @Shared
    @ClassRule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())
    @Shared
    def project = TestUtil.builder(temporaryFolder).withName("root").build()
    @Shared
    def child1 = ProjectBuilder.builder().withName("child1").withParent(project).build()
    @Shared
    def child2 = ProjectBuilder.builder().withName("child2").withParent(project).build()

    def "builds model"() {
        def builder = new GradleBuildBuilder(project.services.get(BuildStateRegistry))

        expect:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.GradleBuild", startProject)
        model.rootProject.path == ":"
        model.rootProject.name == "root"
        model.rootProject.parent == null
        model.rootProject.projectDirectory == project.projectDir
        model.rootProject.children.size() == 2
        model.rootProject.children.every { it.parent == model.rootProject }
        model.projects*.name == ["root", "child1", "child2"]
        model.projects*.path == [":", ":child1", ":child2"]
        model.includedBuilds.empty
        model.editableBuilds.empty

        where:
        startProject | _
        project      | _
        child2       | _
    }

    def "builds model for included builds"() {
        def buildRegistry = Mock(BuildStateRegistry)

        def rootProject = Mock(ProjectState)
        rootProject.projectPath >> Path.ROOT

        def includedProject1 = Mock(ProjectState)
        includedProject1.projectPath >> Path.ROOT

        def includedProject2 = Mock(ProjectState)
        includedProject2.projectPath >> Path.ROOT

        def rootDir = temporaryFolder.createDir("root")
        def dir1 = temporaryFolder.createDir("dir1")
        def dir2 = temporaryFolder.createDir("dir2")

        def rootBuild = Mock(GradleInternal)
        def build1 = Mock(GradleInternal)
        def build2 = Mock(GradleInternal)

        def rootProjects = Mock(BuildProjectRegistry)
        def projects1 = Mock(BuildProjectRegistry)
        def projects2 = Mock(BuildProjectRegistry)

        def rootBuildState = Mock(BuildState)
        def includedBuildState1 = Mock(IncludedBuildState)
        def includedBuildState2 = Mock(IncludedBuildState)

        def includedBuild1 = Mock(IncludedBuildInternal)
        def includedBuild2 = Mock(IncludedBuildInternal)

        rootProject.childProjects >> [].toSet()
        rootProject.identityPath >> Path.path(":some:identity:path")

        includedProject1.childProjects >> [].toSet()
        includedProject1.identityPath >> Path.path(":some:identity:path")
        includedProject2.childProjects >> [].toSet()
        includedProject2.identityPath >> Path.path(":some:identity:path")

        rootBuild.includedBuilds() >> [includedBuild1]

        rootBuildState.mutableModel >> rootBuild
        rootBuildState.buildRootDir >> rootDir
        rootBuildState.projects >> rootProjects
        rootBuildState.identityPath >> Path.path(":some:identity:path")

        includedBuild1.target >> includedBuildState1
        includedBuild2.target >> includedBuildState2

        includedBuildState1.projects >> projects1
        includedBuildState1.buildRootDir >> dir1
        includedBuildState1.importableBuild >> true

        rootProjects.allProjects >> [rootProject].toSet()
        rootProjects.rootProject >> rootProject

        projects1.allProjects >> [includedProject1].toSet()
        projects1.rootProject >> includedProject1

        projects2.allProjects >> [includedProject1].toSet()
        projects2.rootProject >> includedProject2

        build1.includedBuilds() >> [includedBuild2]
        build1.parent >> rootBuild

        includedBuildState1.mutableModel >> build1

        includedBuildState2.projects >> projects2
        includedBuildState2.buildRootDir >> dir2
        includedBuildState2.importableBuild >> true

        build2.includedBuilds() >> []
        build2.parent >> rootBuild

        includedBuildState2.mutableModel >> build2

        buildRegistry.visitBuilds(_) >> { Consumer consumer ->
            consumer.accept(rootBuildState)
            consumer.accept(includedBuildState1)
            consumer.accept(includedBuildState2)
        }

        def builder = new GradleBuildBuilder(buildRegistry)

        expect:
        def model = builder.create(rootBuildState)
        model.includedBuilds.size() == 1

        def model1 = model.includedBuilds[0]
        model1.rootDir == dir1
        model1.includedBuilds.size() == 1

        def model2 = model1.includedBuilds[0]
        model2.rootDir == dir2
        model2.includedBuilds.empty

        model.editableBuilds.size() == 2
        model.editableBuilds[0] == model1
        model.editableBuilds[1] == model2

        model1.editableBuilds.empty
        model2.editableBuilds.empty
    }
}
