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


class NativePreCompiledHeaderPerformanceTest extends AbstractCrossBuildPerformanceTest {
    @Unroll
    def "#size pch performance test" () {
        when:
        runner.testId = "native pch build ${size}"
        runner.testGroup = 'pre-compiled header builds'
        runner.buildSpec {
            projectName("${size}PCHNative").displayName("Using PCH").invocation {
                args("-PusePCH")
                tasksToRun("clean", "assemble")
            }
        }
        runner.baseline {
            projectName("${size}PCHNative").displayName("No PCH").invocation {
                tasksToRun("clean", "assemble")
            }
        }

        then:
        runner.run()

        where:
        size << [ "small", "medium", "big" ]
    }
}
