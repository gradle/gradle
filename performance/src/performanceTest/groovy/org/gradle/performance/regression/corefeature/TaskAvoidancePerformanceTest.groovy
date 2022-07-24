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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.GradleBuildExperimentSpec

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(@Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects =  ["largeJavaMultiProject", "largeMonolithicJavaProject"]))
class TaskAvoidancePerformanceTest extends AbstractCrossBuildPerformanceTest {

    def "help with lazy and eager tasks"() {
        given:
        runner.testGroup = "configuration avoidance"
        runner.buildSpec {
            displayName("lazy")
            invocation {
                tasksToRun("help")
            }
        }
        runner.baseline {
            displayName("eager")
            invocation {
                tasksToRun("help")
                args("-Dorg.gradle.internal.tasks.eager=true")
            }
        }

        when:
        def results = runner.run()

        then:
        results
    }

    @Override
    protected void defaultSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        super.defaultSpec(builder)
        builder
            .warmUpCount(5)
            .invocationCount(10)
    }
}
