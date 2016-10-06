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

package org.gradle.performance

import org.gradle.performance.categories.BasicPerformanceTest
import org.gradle.performance.categories.Experiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([Experiment, BasicPerformanceTest])
class LocalTaskOutputCachePerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll("Test '#testProject' calling #tasks (daemon) with local cache")
    def "test"() {
        when:
        runner.testId = "local cache $testProject ${tasks.join(' ')} (daemon)"
        runner.testGroup = "task output cache"
        runner.buildSpec {
            projectName(testProject).displayName("cached").invocation {
                tasksToRun(tasks).useDaemon().args("-Dorg.gradle.cache.tasks=true")
            }
        }
        runner.baseline {
            projectName(testProject).displayName("non-cached").invocation {
                tasksToRun(tasks).useDaemon()
            }
        }

        then:
        runner.run()

        where:
        testProject            | tasks
        "multi"                | ["clean", "build"]
        "largeEnterpriseBuild" | ["clean", "assemble"]
    }

}
