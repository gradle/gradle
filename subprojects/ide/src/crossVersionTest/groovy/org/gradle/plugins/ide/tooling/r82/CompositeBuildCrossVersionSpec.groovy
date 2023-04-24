/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r82

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion(">=8.2")
@TargetGradleVersion('>=8.2')
class CompositeBuildCrossVersionSpec extends ToolingApiSpecification {

    def "buildTreePath is available on the GradleBuild model"() {
        given:
        includedBuild("b1")
        includedBuild("b2")

        when:
        def model = withConnection {
            it.getModel(GradleBuild)
        }

        then:
        def builds = model.includedBuilds
        builds.size() == 2
        builds.first().projects.first().buildTreePath == ":b1"
        model.projects.first().buildTreePath == ":"
    }

    def "buildTreePath is available for tasks"() {
        given:
        includedBuild("b1")
        includedBuild("b2")

        when:
        def model = withConnection {
            it.action(new FetchTasksAction()).run();
        }.collect { it.buildTreePath }

        then:
        model.containsAll([":b1:buildEnvironment", ":b2:buildEnvironment"])
        model.every { !it.startsWith("::") }
    }

    @TargetGradleVersion('>=8.0 <8.2')
    def "unsupported method for older versions"() {
        given:
        includedBuild("b1")
        includedBuild("b2")

        when:
        withConnection {
            it.action(new FetchTasksAction()).run();
        }.collect { it.buildTreePath }

        then:
        thrown(UnsupportedMethodException)
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
