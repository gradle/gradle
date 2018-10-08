/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.performance.generator.JavaTestProject
import org.gradle.performance.mutator.ApplyAbiChangeToJavaSourceFileMutator
import org.junit.experimental.categories.Category

@Category(PerformanceExperiment)
class KotlinPerformanceComparison extends AbstractCrossBuildPerformanceTest {

    def "clean assemble kotlin vs. java without annotation processing"() {
        given:
        runner.testGroup = "Kotlin comparisons"
        runner.baseline {
            warmUpCount = 2
            invocationCount = 6
            projectName(testProject.projectName).displayName("java").invocation {
                tasksToRun("clean", "assemble").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildSpec {
            warmUpCount = 2
            invocationCount = 6
            projectName(testProject.projectName).displayName("kotlin").invocation {
                tasksToRun("clean", "assemble").args("-Pkotlin").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }

        when:
        def result = runner.run()

        then:
        println result.buildResult("java").speedStats
        println result.buildResult("kotlin").speedStats

        where:
        testProject = JavaTestProject.LARGE_JAVA_MULTI_PROJECT
    }

    def "clean assemble kotlin vs. java with annotation processing"() {
        given:
        runner.testGroup = "Kotlin comparisons"
        runner.baseline {
            warmUpCount = 2
            invocationCount = 6
            projectName(testProject.projectName).displayName("java").invocation {
                tasksToRun("clean", "assemble").args("-Papt").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildSpec {
            warmUpCount = 2
            invocationCount = 6
            projectName(testProject.projectName).displayName("kotlin").invocation {
                tasksToRun("clean", "assemble").args("-Pkotlin", "-Papt").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }

        when:
        def result = runner.run()

        then:
        println result.buildResult("java").speedStats
        println result.buildResult("kotlin").speedStats

        where:
        testProject = JavaTestProject.LARGE_JAVA_MULTI_PROJECT
    }


    def "abi change kotlin vs. java without annotation processing"() {
        given:
        runner.testGroup = "Kotlin comparisons"
        runner.baseline {
            warmUpCount = 10
            invocationCount = 20
            projectName(testProject.projectName).displayName("java").invocation {
                tasksToRun("assemble").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildSpec {
            warmUpCount = 10
            invocationCount = 20
            projectName(testProject.projectName).displayName("kotlin").invocation {
                tasksToRun("assemble").args("-Pkotlin").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildExperimentListener = new ApplyAbiChangeToJavaSourceFileMutator(testProject.config.fileToChangeByScenario['assemble'])

        when:
        def result = runner.run()

        then:
        println result.buildResult("java").speedStats
        println result.buildResult("kotlin").speedStats

        where:
        testProject = JavaTestProject.LARGE_JAVA_MULTI_PROJECT
    }

    def "abi change kotlin vs. java with annotation processing"() {
        given:
        runner.testGroup = "Kotlin comparisons"
        runner.baseline {
            warmUpCount = 10
            invocationCount = 20
            projectName(testProject.projectName).displayName("java").invocation {
                tasksToRun("assemble").args("-Papt").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildSpec {
            warmUpCount = 10
            invocationCount = 20
            projectName(testProject.projectName).displayName("kotlin").invocation {
                tasksToRun("assemble").args("-Pkotlin", "-Papt").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildExperimentListener = new ApplyAbiChangeToJavaSourceFileMutator(testProject.config.fileToChangeByScenario['assemble'])

        when:
        def result = runner.run()

        then:
        println result.buildResult("java").speedStats
        println result.buildResult("kotlin").speedStats

        where:
        testProject = JavaTestProject.LARGE_JAVA_MULTI_PROJECT
    }

    def "configuration time java vs kotlin without annotation processing"() {
        given:
        runner.testGroup = "Kotlin comparisons"
        runner.baseline {
            warmUpCount = 20
            invocationCount = 40
            projectName(testProject.projectName).displayName("java").invocation {
                tasksToRun("help").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildSpec {
            warmUpCount = 20
            invocationCount = 40
            projectName(testProject.projectName).displayName("kotlin").invocation {
                tasksToRun("help").args("-Pkotlin").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }

        when:
        def result = runner.run()

        then:
        println result.buildResult("java").speedStats
        println result.buildResult("kotlin").speedStats

        where:
        testProject = JavaTestProject.LARGE_JAVA_MULTI_PROJECT
    }

    def "configuration time java vs kotlin with annotation processing"() {
        given:
        runner.testGroup = "Kotlin comparisons"
        runner.baseline {
            warmUpCount = 20
            invocationCount = 40
            projectName(testProject.projectName).displayName("java").invocation {
                tasksToRun("help").args("-Papt").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.buildSpec {
            warmUpCount = 20
            invocationCount = 40
            projectName(testProject.projectName).displayName("kotlin").invocation {
                tasksToRun("help").args("-Pkotlin", "-Papt").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }

        when:
        def result = runner.run()

        then:
        println result.buildResult("java").speedStats
        println result.buildResult("kotlin").speedStats

        where:
        testProject = JavaTestProject.LARGE_JAVA_MULTI_PROJECT
    }
}
