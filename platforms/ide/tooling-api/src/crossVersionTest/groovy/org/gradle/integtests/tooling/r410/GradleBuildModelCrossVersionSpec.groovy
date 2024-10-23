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

package org.gradle.integtests.tooling.r410

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.gradle.GradleBuild

class GradleBuildModelCrossVersionSpec extends ToolingApiSpecification {
    @TargetGradleVersion(">=4.10")
    def "nested included builds are visible only in the model of the containing build"() {
        given:
        def rootDir = singleProjectBuildInRootFolder("root") {
            settingsFile << """
                rootProject.name = 'root'
                includeBuild 'buildB'
            """
        }
        def buildBDir = multiProjectBuildInSubFolder("buildB", ["a", "b", "c"]) {
            settingsFile << """
                includeBuild '../buildC'
            """
        }
        def buildCDir = singleProjectBuildInSubfolder("buildC")

        when:
        GradleBuild rootBuild = loadToolingModel(GradleBuild)

        then:
        rootBuild.buildIdentifier.rootDir == rootDir
        rootBuild.rootProject.name == "root"
        rootBuild.includedBuilds.size() == 1

        def buildB = rootBuild.includedBuilds[0]
        buildB.buildIdentifier.rootDir == buildBDir
        buildB.rootProject.name == "buildB"
        buildB.projects.size() == 4
        buildB.includedBuilds.size() == 1

        def buildC = buildB.includedBuilds[0]
        buildC.buildIdentifier.rootDir == buildCDir
        buildC.rootProject.name == "buildC"
        buildC.projects.size() == 1
        buildC.includedBuilds.empty
    }

    @TargetGradleVersion(">=4.10")
    def "root build model exposes all builds that participate in the composite when nested included builds are present"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                rootProject.name = 'root'
                includeBuild 'buildB'
            """
        }
        def buildBDir = multiProjectBuildInSubFolder("buildB", ["a", "b", "c"]) {
            settingsFile << """
                includeBuild '../buildC'
            """
        }
        def buildCDir = singleProjectBuildInSubfolder("buildC")

        when:
        GradleBuild rootBuild = loadToolingModel(GradleBuild)

        then:
        rootBuild.editableBuilds.size() == 2

        def buildB = rootBuild.editableBuilds[0]
        buildB.buildIdentifier.rootDir == buildBDir
        buildB.rootProject.name == "buildB"
        buildB.editableBuilds.empty

        def buildC = rootBuild.editableBuilds[1]
        buildC.buildIdentifier.rootDir == buildCDir
        buildC.rootProject.name == "buildC"
        buildC.editableBuilds.empty
    }

    @TargetGradleVersion(">=3.3")
    def "root build model exposes all builds that participate in the composite"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                rootProject.name = 'root'
                includeBuild 'buildB'
                includeBuild 'buildC'
            """
        }
        def buildBDir = multiProjectBuildInSubFolder("buildB", ["a", "b", "c"]) {
            settingsFile << """
            """
        }
        def buildCDir = singleProjectBuildInSubfolder("buildC")

        when:
        GradleBuild rootBuild = loadToolingModel(GradleBuild)

        then:
        rootBuild.editableBuilds.size() == 2

        def buildB = rootBuild.editableBuilds[0]
        buildB.buildIdentifier.rootDir == buildBDir
        buildB.rootProject.name == "buildB"
        buildB.editableBuilds.empty

        def buildC = rootBuild.editableBuilds[1]
        buildC.buildIdentifier.rootDir == buildCDir
        buildC.rootProject.name == "buildC"
        buildC.editableBuilds.empty
    }
}
