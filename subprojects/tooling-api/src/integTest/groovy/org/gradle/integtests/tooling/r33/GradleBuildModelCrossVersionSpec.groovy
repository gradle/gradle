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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion(">=3.3")
class GradleBuildModelCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=3.3")
    def "Included builds are present in the model"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                rootProject.name = 'root'
                includeBuild 'includedBuild'
            """
        }
        multiProjectBuildInSubFolder("includedBuild", ["a", "b", "c"])

        when:
        GradleBuild model = loadToolingModel(GradleBuild)

        then:
        model.includedBuilds.size() == 1
        model.includedBuilds[0].projects.size() == 4
    }

    @TargetGradleVersion(">=3.1 <3.3")
    def "No included builds for old Gradle versions"() {
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                rootProject.name = 'root'
                includeBuild 'includedBuild'
            """
        }
        multiProjectBuildInSubFolder("includedBuild", ["a", "b", "c"])
        when:
        GradleBuild model = loadToolingModel(GradleBuild)

        then:
        model.includedBuilds.size() == 0
    }

    def "No included builds for single root project"() {
        singleProjectBuildInRootFolder("root")
        when:
        GradleBuild model = loadToolingModel(GradleBuild)

        then:
        model.includedBuilds.size() == 0
    }
}
