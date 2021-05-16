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

package org.gradle.plugins.ide.tooling.m71

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion(">=4.10")
@TargetGradleVersion('>=7.1')
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

    @TargetGradleVersion(">=3.0 <7.1")
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

    def buildsWithBuildSrc() {
        buildSrc(projectDir)
        settingsFile << """
            includeBuild("child")
        """

        def childBuild = file("child")
        buildSrc(childBuild)
        childBuild.file("settings.gradle") << """
            includeBuild("nested")
        """

        def nestedBuild = childBuild.file("nested")
        buildSrc(nestedBuild)
    }

    void buildSrc(TestFile dir) {
        dir.file("buildSrc/src/main/java/Thing.java") << "class Thing { }"
    }
}
