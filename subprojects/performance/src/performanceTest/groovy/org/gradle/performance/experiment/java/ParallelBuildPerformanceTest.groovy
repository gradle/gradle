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
import org.gradle.performance.categories.PerformanceExperiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceExperiment)
class ParallelBuildPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "clean assemble on #testProject with 2 parallel workers"() {
        when:
        runner.testGroup = "parallel builds"
        runner.buildSpec {
            projectName(testProject).displayName("parallel").invocation {
                tasksToRun("clean", "assemble").gradleOpts("-Xms${memory}", "-Xmx${memory}").args("--parallel", "--max-workers=2")
            }
        }
        runner.baseline {
            projectName(testProject).displayName("serial").invocation {
                tasksToRun("clean", "assemble").gradleOpts("-Xms${memory}", "-Xmx${memory}")
            }
        }

        then:
        runner.run()

        where:
        testProject                  | memory | warmUpRuns | runs
        "largeMonolithicJavaProject" | '4g'   | 2          | 6
        "largeJavaMultiProject"      | '4g'   | 2          | 6
    }

}
