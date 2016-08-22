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

package org.gradle.integtests.tooling.r31

import org.gradle.integtests.tooling.fixture.ProjectConnectionToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion('>=2.0')
@TargetGradleVersion(">=3.1")
class ToolingModelBuildDetectionCrossVersionSpec extends ProjectConnectionToolingApiSpecification {

    def setup() {
        settingsFile << 'rootProject.name = "root"'
    }

    def "sets build type attributes for tooling model request"() {
        when:
        buildFile << """
            import org.gradle.initialization.buildtype.BuildTypeAttributes
            def buildType = gradle.services.get(BuildTypeAttributes)

            assert !buildType.compositeBuild
            assert !buildType.nestedBuild
            assert buildType.toolingApiBuild
            assert buildType.toolingModelRequest
"""

        then:
        withConnection { connection -> connection.getModel(GradleBuild) }
    }

    def "sets build type attributes for task execution request"() {
        given:
        buildFile << """
            import org.gradle.initialization.buildtype.BuildTypeAttributes
            def buildType = gradle.services.get(BuildTypeAttributes)

            assert !buildType.compositeBuild
            assert !buildType.nestedBuild
            assert buildType.toolingApiBuild
            assert !buildType.toolingModelRequest

            task dummy {}
"""

        when:
        def output = withBuild({ it.forTasks("dummy")} )

        then:
        output.result.assertTasksExecuted(":dummy")
    }
}
