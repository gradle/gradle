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

package org.gradle.performance.experiment.nativeplatform

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceExperiment)
class NativeParallelPerformanceTest extends AbstractCrossBuildPerformanceTest {
    @Unroll
    def "clean assemble on #testProject with parallel workers" () {
        given:
        runner.testGroup = 'parallel builds'
        runner.buildSpec {
            projectName(testProject).displayName("parallel").invocation {
                tasksToRun("clean", "assemble")
            }
        }
        runner.baseline {
            projectName(testProject).displayName("serial").invocation {
                tasksToRun("clean", "assemble").disableParallelWorkers()
            }
        }

        when:
        def results = runner.run()

        then:
        results

        where:
        testProject << [ "smallNative", "mediumNative", "bigNative", "multiNative" ]
    }
}
