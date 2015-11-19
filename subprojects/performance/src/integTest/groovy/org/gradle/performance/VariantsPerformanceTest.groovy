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

import org.gradle.performance.categories.BasicPerformanceTest
import org.gradle.performance.fixture.BuildExperimentSpec
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(BasicPerformanceTest)
class VariantsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        builder.invocation.gradleOpts("-Xms1g", "-Xmx1g", "-XX:MaxPermSize=256m")
        super.defaultSpec(builder)
    }

    @Unroll
    def "#size project using variants #scenario build"() {
        when:
        runner.testGroup = "project using variants"
        runner.testId = "$size project using variants $scenario build"
        runner.buildSpec {
            projectName("${size}VariantsNewModel").displayName("new model").invocation {
                tasksToRun(task).useDaemon()
            }
        }
        runner.buildSpec {
            projectName("${size}VariantsNewModel").displayName("new model (no client logging)").invocation {
                tasksToRun(task).useDaemon().disableDaemonLogging()
            }
        }
        runner.baseline {
            projectName("${size}VariantsOldModel").displayName("old model").invocation {
                tasksToRun(task).useDaemon()
            }
        }
        runner.baseline {
            projectName("${size}VariantsOldModel").displayName("old model (tooling api)").invocation {
                tasksToRun(task).useToolingApi()
            }
        }
        runner.baseline {
            projectName("${size}VariantsOldModel").displayName("old model (no client logging)").invocation {
                tasksToRun(task).useDaemon().disableDaemonLogging()
            }
        }

        then:
        runner.run()

        where:
        scenario  | size     | task
        "empty"   | "small"  | "help"
        "empty"   | "medium" | "help"
        "empty"   | "big"    | "help"
        "full"    | "small"  | "allVariants"
        "full"    | "medium" | "allVariants"
        "full"    | "big"    | "allVariants"
        "partial" | "medium" | "flavour1type1_t1"
        "partial" | "big"    | "flavour1type1_t1"
    }

    @Unroll
    def "multiproject using variants #scenario build"() {
        when:
        runner.testGroup = "project using variants"
        runner.testId = "multiproject using variants $scenario build"
        runner.buildSpec {
            projectName("variantsNewModelMultiproject").displayName("new model").invocation {
                tasksToRun(*tasks).useDaemon()
            }
        }
        runner.buildSpec {
            projectName("variantsNewModelMultiproject").displayName("new model (no client logging)").invocation {
                tasksToRun(*tasks).useDaemon().disableDaemonLogging()
            }
        }
        runner.baseline {
            projectName("variantsOldModelMultiproject").displayName("old model").invocation {
                tasksToRun(*tasks).useDaemon()
            }
        }
        runner.baseline {
            projectName("variantsOldModelMultiproject").displayName("old model (tooling api)").invocation {
                tasksToRun(*tasks).useToolingApi()
            }
        }
        runner.baseline {
            projectName("variantsOldModelMultiproject").displayName("old model (no client logging)").invocation {
                tasksToRun(*tasks).useDaemon().disableDaemonLogging()
            }
        }

        then:
        runner.run()

        where:
        scenario                      | tasks
        "single variant"              | [":project1:flavour1type1_t1"]
        "all variants single project" | [":project1:allVariants"]
        // This is causing the performance test process to die and the build to hang: disabling for now.
//        "all variants all projects"   | ["allVariants"]
    }
}
