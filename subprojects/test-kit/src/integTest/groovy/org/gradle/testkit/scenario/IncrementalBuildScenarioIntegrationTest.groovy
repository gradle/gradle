/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.scenario

import org.gradle.testkit.scenario.collection.IncrementalBuildScenario


class IncrementalBuildScenarioIntegrationTest extends AbstractGradleScenarioIntegrationTest {

    def "can run incremental build scenario changing workspace file input"() {

        given:
        def scenario = IncrementalBuildScenario.create()
            .withRunnerFactory { runner().withArguments("-Dheader=CONSTANT").forwardOutput() }
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withInputChangeWorkspaceAction { root ->
                new File(root, 'input.txt') << 'CHANGED'
            }

        when:
        def result = scenario.run()

        then:
        result.ofStep(IncrementalBuildScenario.Steps.CLEAN_BUILD)
        result.ofStep(IncrementalBuildScenario.Steps.UP_TO_DATE_BUILD)
        result.ofStep(IncrementalBuildScenario.Steps.INCREMENTAL_BUILD)
    }

    def "can run incremental build scenario changing command line input"() {

        given:
        def scenario = IncrementalBuildScenario.create()
            .withRunnerFactory { runner().withArguments("-Dheader=ORIGINAL").forwardOutput() }
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withInputChangeRunnerAction { runner ->
                runner.withArguments(runner.arguments + "-Dheader=CHANGED")
            }

        when:
        def result = scenario.run()

        then:
        result.ofStep(IncrementalBuildScenario.Steps.CLEAN_BUILD)
        result.ofStep(IncrementalBuildScenario.Steps.UP_TO_DATE_BUILD)
        result.ofStep(IncrementalBuildScenario.Steps.INCREMENTAL_BUILD)
    }
}
