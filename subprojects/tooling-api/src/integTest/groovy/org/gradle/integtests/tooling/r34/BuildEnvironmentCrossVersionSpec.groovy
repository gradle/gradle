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

package org.gradle.integtests.tooling.r34

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.build.BuildEnvironment

class BuildEnvironmentCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=3.4")
    @TargetGradleVersion(">=3.4")
    def "provide getEnvironmentVariables on BuildEnvironment"() {
        when:
        def buildEnvironment = withConnection { ProjectConnection connection ->
            connection.getModel(BuildEnvironment.class)
        }

        then:
        buildEnvironment?.environmentVariables == System.getenv()
    }

    @ToolingApiVersion(">=3.4")
    @TargetGradleVersion(">=3.4")
    def "provide setEnvironmentVariables on LongRunningOperation"() {
        when:
        BuildEnvironment buildEnvironment = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setEnvironmentVariables(["var": "val"])
                .get()
        }

        then:
        buildEnvironment?.environmentVariables == ["var": "val"]
    }

}
