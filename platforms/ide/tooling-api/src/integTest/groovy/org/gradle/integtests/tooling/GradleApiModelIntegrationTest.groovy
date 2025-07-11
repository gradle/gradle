/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiBackedGradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiSpec
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleApiModel

class GradleApiModelIntegrationTest extends AbstractIntegrationSpec implements ToolingApiSpec {

    def "can query Gradle API even if root settings file is broken"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        TestBuildActionResult result = runBuildAction(new TestBuildAction())

        then:
        !result.gradleApiClasspath.isEmpty()
        result.gradleApiClasspath.find { it.name.contains("gradle-core-api-") && it.name.endsWith(".jar") }
        result.gradleApiClasspath.find { it.name.contains("gradle-core-") && it.name.endsWith(".jar") }
        result.gradleApiClasspath.find { it.name.contains("gradle-configuration-cache-") && it.name.endsWith(".jar") }
    }

    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

    static class TestBuildAction implements BuildAction<TestBuildActionResult>, Serializable {
        @Override
        TestBuildActionResult execute(BuildController controller) {
            def gradleApiModel = controller.getModel(GradleApiModel)
            return new TestBuildActionResult(gradleApiModel.gradleApiClasspath)
        }
    }

    static class TestBuildActionResult implements Serializable {
        List<File> gradleApiClasspath;

        TestBuildActionResult(List<File> gradleApiClasspath) {
            this.gradleApiClasspath = gradleApiClasspath;
        }
    }
}
