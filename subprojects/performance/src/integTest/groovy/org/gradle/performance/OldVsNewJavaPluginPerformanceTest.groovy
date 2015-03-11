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

import org.gradle.performance.fixture.BuildExperimentSpec
import spock.lang.Unroll

class OldVsNewJavaPluginPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        builder.invocation.gradleOpts("-Xmx1024m", "-XX:MaxPermSize=256m")
        super.defaultSpec(builder)
    }

    @Unroll
    def "#size project old vs new java plugin #scenario build"() {
        when:
        runner.testGroup = "old vs new java plugin"
        runner.testId = "$size project old vs new java plugin $scenario build"
        runner.buildSpec {
            projectName("${size}NewJava").displayName("new plugin").invocation {
                tasksToRun(*tasks).useDaemon().enableTransformedModelDsl()
            }
        }
        runner.buildSpec {
            projectName("${size}NewJava").displayName("new plugin (reuse)").invocation {
                tasksToRun(*tasks).useDaemon().enableTransformedModelDsl().enableModelReuse()
            }
        }
        runner.buildSpec {
            projectName("${size}NewJava").displayName("new plugin (reuse + tooling api)").invocation {
                tasksToRun(*tasks).useToolingApi().enableTransformedModelDsl().enableModelReuse()
            }
        }
        runner.buildSpec {
            projectName("${size}NewJava").displayName("new plugin (no client logging)").invocation {
                tasksToRun(*tasks).useDaemon().enableTransformedModelDsl().disableDaemonLogging()
            }
        }
        runner.baseline {
            projectName("${size}OldJava").displayName("old plugin").invocation {
                tasksToRun(*tasks).useDaemon()
            }
        }
        runner.baseline {
            projectName("${size}OldJava").displayName("old plugin (tooling api)").invocation {
                tasksToRun(*tasks).useToolingApi()
            }
        }
        runner.baseline {
            projectName("${size}OldJava").displayName("old plugin (no client logging)").invocation {
                tasksToRun(*tasks).useDaemon().disableDaemonLogging()
            }
        }

        then:
        runner.run()

        where:
        scenario  | size     | tasks
        "empty"   | "small"  | ["help"]
        "empty"   | "medium" | ["help"]
        "empty"   | "big"    | ["help"]
        "full"    | "small"  | ["clean", "assemble"]
        "full"    | "medium" | ["clean", "assemble"]
        "full"    | "big"    | ["clean", "assemble"]
        "partial" | "medium" | [":project1:clean", ":project1:assemble"]
        "partial" | "big"    | [":project1:clean", ":project1:assemble"]
    }
}
