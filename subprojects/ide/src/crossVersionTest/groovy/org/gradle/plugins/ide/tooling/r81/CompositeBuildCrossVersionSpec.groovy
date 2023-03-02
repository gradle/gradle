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

package org.gradle.plugins.ide.tooling.r81

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion(">=8.1")
@TargetGradleVersion('>=8.1')
class CompositeBuildCrossVersionSpec extends ToolingApiSpecification {
    def "includes buildSrc builds of plugin builds"() {
        includedBuild("b1")
        includedBuild("b2")

        given:
        def model = withConnection {
            it.getModel(GradleBuild)
        }


        expect:
        def builds = model.includedBuilds
        builds.size() == 2
        builds.first().projects.first().buildTreePath == ":b1"
        model.projects.first().buildTreePath == ":"
    }



    void multiProjectBuildSrc(TestFile dir) {
        dir.file("buildSrc/settings.gradle") << """
            include("a")
            include("b")
        """
    }

    TestFile includedBuild(String build) {
        settingsFile << """
            includeBuild("$build")
        """

        def buildDir = file(build)
        def settings = buildDir.file("settings.gradle")
        settings << "rootProject.name = '$build-root-name'"
        buildDir
    }
}
