/*
 * Copyright 2015 the original author or authors.
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

import spock.lang.Unroll

class NativeParallelPerformanceTest extends AbstractCrossBuildPerformanceTest {
    @Unroll
    def "#size parallel performance test" () {
        when:
        runner.testId = "native parallel build ${size}"
        runner.testGroup = 'parallel builds'
        runner.buildSpec {
            projectName("${size}Native").displayName("parallel").invocation {
                tasksToRun("clean", "assemble")
            }
        }
        runner.baseline {
            projectName("${size}Native").displayName("serial").invocation {
                tasksToRun("clean", "assemble").disableParallelWorkers()
            }
        }

        then:
        runner.run()

        where:
        size << [ "small", "medium", "big", "multi" ]
    }
}
