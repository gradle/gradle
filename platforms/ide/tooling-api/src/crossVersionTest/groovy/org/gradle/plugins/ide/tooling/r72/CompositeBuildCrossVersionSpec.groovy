/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r72

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.gradle.GradleBuild

@TargetGradleVersion('>=7.2')
class CompositeBuildCrossVersionSpec extends ToolingApiSpecification {
    def "includes buildSrc builds in model"() {
        buildsWithBuildSrc()

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        expect:
        model.includedBuilds.size() == 1

        def included = model.includedBuilds[0]
        included.buildIdentifier.rootDir == file("child")
        included.includedBuilds.size() == 1
        included.editableBuilds.empty

        model.editableBuilds.size() == 5

        def buildSrc = model.editableBuilds[0]
        buildSrc.buildIdentifier.rootDir == file("buildSrc")
        buildSrc.includedBuilds.empty
        buildSrc.editableBuilds.empty

        model.editableBuilds[1].is(included)

        def includedBuildSrc = model.editableBuilds[2]
        includedBuildSrc.buildIdentifier.rootDir == file("child/buildSrc")
        includedBuildSrc.includedBuilds.empty
        includedBuildSrc.editableBuilds.empty

        def nested = model.editableBuilds[3]
        nested.buildIdentifier.rootDir == file("child/nested")
        nested.includedBuilds.empty
        nested.editableBuilds.empty
        included.includedBuilds[0].is(nested)

        def nestedBuildSrc = model.editableBuilds[4]
        nestedBuildSrc.buildIdentifier.rootDir == file("child/nested/buildSrc")
        nestedBuildSrc.includedBuilds.empty
        nestedBuildSrc.editableBuilds.empty
    }

    @TargetGradleVersion(">4.9 <7.1")
    // versions 4.9 and older do not like nested included builds or nested buildSrc builds
    def "older versions do not include buildSrc builds in model"() {
        buildsWithBuildSrc()

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        expect:
        model.includedBuilds.size() == 1
        def included = model.includedBuilds[0]
        included.buildIdentifier.rootDir == file("child")
        included.includedBuilds.size() == 1
        included.editableBuilds.empty

        model.editableBuilds.size() == 2

        model.editableBuilds[0].is(included)

        def nested = model.editableBuilds[1]
        nested.buildIdentifier.rootDir == file("child/nested")
        nested.includedBuilds.empty
        nested.editableBuilds.empty
        included.includedBuilds[0].is(nested)
    }

    def "includes buildSrc builds of plugin builds"() {
        settingsFile << """
            pluginManagement {
                includeBuild("child")
            }
        """
        buildSrc(file("child"))

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        expect:
        model.includedBuilds.size() == 1

        def included = model.includedBuilds[0]
        included.buildIdentifier.rootDir == file("child")
        included.includedBuilds.empty
        included.editableBuilds.empty

        model.editableBuilds.size() == 2

        model.editableBuilds[0].is(included)

        def includedBuildSrc = model.editableBuilds[1]
        includedBuildSrc.buildIdentifier.rootDir == file("child/buildSrc")
        includedBuildSrc.includedBuilds.empty
        includedBuildSrc.editableBuilds.empty
    }

    def "can query model for multi-project buildSrc builds"() {
        multiProjectBuildSrc(projectDir)
        settingsFile << """
            includeBuild("child")
        """

        def childBuild = file("child")
        multiProjectBuildSrc(childBuild)

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        expect:
        model.includedBuilds.size() == 1
        model.editableBuilds.size() == 3

        def buildSrc = model.editableBuilds[0]
        buildSrc.buildIdentifier.rootDir == file("buildSrc")
        buildSrc.projects.size() == 3

        def nestedBuildSrc = model.editableBuilds[2]
        nestedBuildSrc.buildIdentifier.rootDir == file("child/buildSrc")
        nestedBuildSrc.projects.size() == 3
    }

    @TargetGradleVersion(">=6.8")
    // versions older than 6.8 do not handle cycles
    def "can query model when there are cycles in the included build graph"() {
        settingsFile << """
            includeBuild("child1")
        """
        file("child1/settings.gradle") << """
            includeBuild("../child2")
        """
        file("child2/settings.gradle") << """
            includeBuild("../child1")
        """

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        expect:
        model.includedBuilds.size() == 1

        def included1 = model.includedBuilds[0]
        included1.buildIdentifier.rootDir == file("child1")
        included1.includedBuilds.size() == 1
        included1.editableBuilds.empty

        model.editableBuilds.size() == 2

        model.editableBuilds[0].is(included1)

        def included2 = model.editableBuilds[1]
        included2.buildIdentifier.rootDir == file("child2")
        included2.includedBuilds.size() == 1
        included2.editableBuilds.empty

        included1.includedBuilds[0].is(included2)
        included2.includedBuilds[0].is(included1)
    }

    @TargetGradleVersion(">=6.8")
    // versions older than 6.8 do not allow root to be included by child
    def "can query model when included build includes root build"() {
        settingsFile << """
            includeBuild("child")
        """
        file("child/settings.gradle") << """
            includeBuild("..")
        """

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        expect:
        model.includedBuilds.size() == 1

        def included = model.includedBuilds[0]
        included.buildIdentifier.rootDir == file("child")
        included.includedBuilds.size() == 1
        included.editableBuilds.empty

        model.editableBuilds.size() == 1

        model.editableBuilds[0].is(included)

        included.includedBuilds[0].is(model)
    }

    def "can query model for nested buildSrc builds"() {
        def buildSrcDir = buildSrc(projectDir)
        def nestedBuildSrcDir = buildSrc(buildSrcDir)

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        expect:
        model.includedBuilds.empty
        model.editableBuilds.size() == 2

        def buildSrc = model.editableBuilds[0]
        buildSrc.buildIdentifier.rootDir == buildSrcDir

        def nestedBuildSrc = model.editableBuilds[1]
        nestedBuildSrc.buildIdentifier.rootDir == nestedBuildSrcDir
    }

    def "build action can fetch model for buildSrc project"() {
        buildsWithBuildSrc()

        given:
        def model = withConnection {
            it.action(new FetchBuildSrcProjectModelAction()).run()
        }

        expect:
        model != null
        model.projectDirectory == file("buildSrc")
    }

    def "build action can fetch model for buildSrc build"() {
        buildsWithBuildSrc()

        given:
        def model = withConnection {
            it.action(new FetchBuildSrcModelAction()).run()
        }

        expect:
        model != null
        model.projectDirectory == file("buildSrc")
    }

    def buildsWithBuildSrc() {
        buildSrc(projectDir)
        def child = "child"
        settingsFile << """
            includeBuild("$child")
        """


        def childBuild = file(child)
        buildSrc(childBuild)
        def nested = "nested"
        childBuild.file("settings.gradle") << """
            includeBuild("$nested")
        """

        def nestedBuild = childBuild.file(nested)
        buildSrc(nestedBuild)
    }

    TestFile buildSrc(TestFile dir) {
        def buildSrc = dir.file("buildSrc")
        buildSrc.file("src/main/java/Thing.java") << "class Thing { }"
        return buildSrc
    }

    void multiProjectBuildSrc(TestFile dir) {
        dir.file("buildSrc/settings.gradle") << """
            include("a")
            include("b")
        """
    }
}
