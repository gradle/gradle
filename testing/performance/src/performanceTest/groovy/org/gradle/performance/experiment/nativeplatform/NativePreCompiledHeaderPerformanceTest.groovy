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
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario

import static org.gradle.performance.annotations.ScenarioType.PER_WEEK
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_WEEK, operatingSystems = [LINUX], testProjects = ["smallPCHNative", "mediumPCHNative", "bigPCHNative"])
)
class NativePreCompiledHeaderPerformanceTest extends AbstractCrossBuildPerformanceTest {

    def "clean assemble with precompiled headers" () {
        given:
        runner.testGroup = 'pre-compiled header builds'
        runner.buildSpec {
            displayName("Using PCH")
            invocation {
                args("-PusePCH")
                tasksToRun("clean", "assemble")
            }
        }
        runner.baseline {
            displayName("No PCH")
            invocation {
                tasksToRun("clean", "assemble")
            }
        }

        when:
        def results = runner.run()

        then:
        results
    }
}
