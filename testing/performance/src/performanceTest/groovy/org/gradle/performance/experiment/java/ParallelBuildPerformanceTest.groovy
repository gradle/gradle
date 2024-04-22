/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.experiment.java

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.GradleBuildExperimentSpec

import static org.gradle.performance.annotations.ScenarioType.PER_WEEK
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_WEEK, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"])
)
class ParallelBuildPerformanceTest extends AbstractCrossBuildPerformanceTest {

    def "clean assemble with 4 parallel workers"() {
        given:
        runner.testGroup = "parallel builds"
        runner.buildSpec {
            displayName("parallel")
            invocation {
                args("-Dorg.gradle.parallel=true", "--max-workers=4")
            }
        }
        runner.baseline {
            displayName("serial")
            invocation {
                args("-Dorg.gradle.parallel=false", "--max-workers=4")
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
        builder.warmUpCount = 2
        builder.invocationCount = 3
        builder.invocation {
            tasksToRun("clean", "assemble")
        }
    }
}
