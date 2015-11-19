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

import org.gradle.performance.categories.NativePerformanceTest
import org.gradle.performance.fixture.BuildExperimentSpec
import org.junit.experimental.categories.Category
import spock.lang.Ignore
import spock.lang.Unroll

@Ignore
@Category(NativePerformanceTest)
class NativeScenarioPerformanceTest extends AbstractCrossBuildPerformanceTest {
    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        builder.invocation.gradleOpts("-Xms1g", "-Xmx1g", "-XX:MaxPermSize=256m")
        super.defaultSpec(builder)
    }

    @Unroll
    def "native #size project comparison for #scenario" () {
        when:
        runner.testGroup = "Native build comparison"
        runner.testId = "$size native comparison $scenario build"
        runner.baseline {
            projectName("${size}ScenarioNative").displayName("baseline").invocation {
                tasksToRun(*tasks)
            }
        }
        runner.buildSpec {
            projectName("${size}ScenarioNative").displayName("with daemon").invocation {
                tasksToRun(*tasks).useDaemon()
            }
        }
        runner.buildSpec {
            projectName("${size}ScenarioNative").displayName("with tooling api").invocation {
                tasksToRun(*tasks).useToolingApi()
            }
        }
        runner.buildSpec {
            projectName("${size}ScenarioNative").displayName("with daemon (no client logging)").invocation {
                tasksToRun(*tasks).useDaemon().disableDaemonLogging()
            }
        }

        then:
        runner.run()

        where:
        scenario | size     | tasks
        "empty"  | "small"  | ["help"]
        "empty"  | "medium" | ["help"]
        "empty"  | "big"    | ["help"]
        "full"   | "small"  | ["clean", "assemble"]
        "full"   | "medium" | ["clean", "assemble"]
        "full"   | "big"    | ["clean", "assemble"]
    }
}
