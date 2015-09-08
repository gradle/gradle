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
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(ManualPerformanceTest)
class IncrementalTaskInputsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        builder.invocation.gradleOpts("-Xmx4g", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/tmp")
        builder.invocationCount(1).warmUpCount(1)
    }

    @Unroll
    def "compare incremental task inputs to ordinary task inputs #inputCount"() {
        when:
        runner.testGroup = "incremental task inputs"
        runner.testId = "compare incremental task inputs to ordinary task inputs"
        runner.buildSpec {
            projectName("compareTaskInputs").displayName("incremental inputs $inputCount").invocation {
                tasksToRun("buildIncremental").args("-PinputCount=$inputCount").useDaemon()
            }
        }
        runner.baseline {
            projectName("compareTaskInputs").displayName("ordinary inputs $inputCount").invocation {
                tasksToRun("buildOrdinary").args("-PinputCount=$inputCount").useDaemon()
            }
        }

        then:
        runner.run()

        where:
        inputCount << [1, 10, 100, 1000, 10000]
    }

    @Unroll
    def "compare 'no change' to 'one change' with #inputCount inputs"() {
        given:
        def taskCount=100

        when:
        runner.testGroup = "incremental task inputs"
        runner.testId = "compare 'no change' to 'one change'"
        runner.buildSpec {
            invocationCount(5).warmUpCount(2)
            projectName("compareTaskInputs").displayName("no change with $inputCount inputs").invocation {
                tasksToRun("buildIncremental").args("-PinputCount=$inputCount", "-PtaskCount=$taskCount").useDaemon()
            }
        }
        runner.baseline {
            invocationCount(5).warmUpCount(2)
            projectName("compareTaskInputs").displayName("one change with $inputCount inputs").invocation {
                tasksToRun("buildIncremental").args("-PinputCount=$inputCount", "-PtaskCount=$taskCount", "-PchangeOneInput=true").useDaemon()
            }
        }

        then:
        runner.run()

        where:
        inputCount << [1, 10, 100, 1000, 10000]
    }

    @Unroll
    def "find breaking point for input sizes - #inputCount inputs of #inputFileSize"() {
        given:
        def taskCount = 100

        when:
        runner.testGroup = "incremental task inputs"
        runner.testId = "find breaking point for input sizes"
        runner.buildSpec {
            invocationCount(1).warmUpCount(1)
            projectName("compareTaskInputs").displayName("$inputCount inputs of $inputFileSize").invocation {
                tasksToRun("buildIncremental").args("-PinputCount=$inputCount", "-PtaskCount=$taskCount", "-PinputFileSize=$inputFileSize").useDaemon()
            }
        }

        then:
        runner.run()

        where:
        [inputCount, inputFileSize] << [[1, 10, 100, 1000, 10000], [10, 50, 100, 500, 1000].collect { "${it}k".toString() }].combinations()
    }

    @Unroll
    def "find breaking point for input sizes - 100 inputs of #inputFileSize"() {
        given:
        def taskCount = 10
        def inputCount = 100

        when:
        runner.testGroup = "incremental task inputs"
        runner.testId = "find breaking point for input sizes"
        runner.buildSpec {
            invocationCount(1).warmUpCount(1)
            projectName("compareTaskInputs").displayName("$inputCount inputs of $inputFileSize").invocation {
                tasksToRun("buildIncremental").args("-PinputCount=$inputCount", "-PtaskCount=$taskCount", "-PinputFileSize=$inputFileSize").useDaemon()
            }
        }

        then:
        runner.run()

        where:
        inputFileSize << [1, 10, 50, 100].collect { "${it}M".toString() }
    }

}
