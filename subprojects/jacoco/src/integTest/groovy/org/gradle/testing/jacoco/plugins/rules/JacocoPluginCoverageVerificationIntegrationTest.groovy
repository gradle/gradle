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

package org.gradle.testing.jacoco.plugins.rules

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.jacoco.plugins.JacocoMultiVersionIntegrationTest
import org.gradle.testing.jacoco.plugins.fixtures.JacocoCoverage
import spock.lang.Unroll

import static org.gradle.testing.jacoco.plugins.rules.JacocoViolationRulesLimit.Insufficient
import static org.gradle.testing.jacoco.plugins.rules.JacocoViolationRulesLimit.Sufficient

@TargetCoverage({ JacocoCoverage.DEFAULT_COVERAGE })
class JacocoPluginCoverageVerificationIntegrationTest extends JacocoMultiVersionIntegrationTest {

    private final static String[] TEST_TASK_PATH = [':test'] as String[]
    private final static String[] JACOCO_COVERAGE_VERIFICATION_TASK_PATH = [':jacocoTestCoverageVerification'] as String[]
    private final static String[] TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS = TEST_TASK_PATH + JACOCO_COVERAGE_VERIFICATION_TASK_PATH
    private final static String[] INTEG_TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS = [':integrationTest', ':jacocoIntegrationTestCoverageVerification'] as String[]

    def setup() {
        javaProjectUnderTest.writeSourceFiles()
    }

    def "can define no rules"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {}
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
    }

    def "can define single rule without limits"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {}
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
    }

    def "Ant task reports error for unknown field value"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        element = 'UNKNOWN'
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
        failure.assertHasCause("'UNKNOWN' is not a permitted value for org.jacoco.core.analysis.ICoverageNode\$ElementType")
    }

    def "can define includes for single rule"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        element = 'CLASS'
                        includes = ['com.company.*', 'org.gradle.*']
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
        failure.assertHasCause("Rule violated for class org.gradle.Class1: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    def "can define excludes for single rule"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        excludes = ['company', '$testDirectory.name']
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
    }

    @Unroll
    def "can define rule with sufficient coverage for #description"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        ${limits.join('\n')}
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)

        where:
        limits                                               | description
        [Sufficient.LINE_METRIC_COVERED_RATIO]               | 'line metric with covered ratio'
        [Sufficient.CLASS_METRIC_MISSED_COUNT]               | 'class metric with missed count'
        [Sufficient.LINE_METRIC_COVERED_RATIO,
         Sufficient.CLASS_METRIC_MISSED_COUNT]               | 'line and class metric'
        [Sufficient.LINE_METRIC_COVERED_RATIO_OUT_OF_BOUNDS] | 'line metric with covered ratio with values out of bounds'
    }

    @Unroll
    def "can define rule with insufficient coverage for #description"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        ${limits.join('\n')}
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
        failure.assertHasCause("Rule violated for bundle $testDirectory.name: $errorMessage")

        where:
        limits                                                      | description                                                              | errorMessage
        [Insufficient.LINE_METRIC_COVERED_RATIO]                    | 'line metric with covered ratio'                                         | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Insufficient.CLASS_METRIC_MISSED_COUNT]                    | 'class metric with missed count'                                         | 'classes missed count is 0.0, but expected minimum is 0.5'
        [Insufficient.LINE_METRIC_COVERED_RATIO,
         Insufficient.CLASS_METRIC_MISSED_COUNT]                    | 'first of multiple insufficient limits fails'                            | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Sufficient.LINE_METRIC_COVERED_RATIO,
         Insufficient.CLASS_METRIC_MISSED_COUNT,
         Sufficient.CLASS_METRIC_MISSED_COUNT]                      | 'first insufficient limits fails'                                        | 'classes missed count is 0.0, but expected minimum is 0.5'
        [Insufficient.CLASS_METRIC_MISSED_COUNT_MINIMUM_GT_MAXIMUM] | 'class metric with missed count with minimum greater than maximum value' | 'classes missed count is 0.0, but expected minimum is 0.5'
    }

    def "can define same rule multiple times"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        ${Sufficient.LINE_METRIC_COVERED_RATIO}
                    }
                    rule {
                        ${Sufficient.LINE_METRIC_COVERED_RATIO}
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
    }

    def "can define multiple, different rules"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        $Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                    rule {
                        $Insufficient.CLASS_METRIC_MISSED_COUNT
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
        failure.assertHasCause("Rule violated for bundle $testDirectory.name: classes missed count is 0.0, but expected minimum is 0.5")
    }

    def "can disable rules"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        $Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                    rule {
                        enabled = false
                        $Insufficient.CLASS_METRIC_MISSED_COUNT
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
    }

    def "can ignore failures"() {
        given:
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    failOnViolation = false

                    rule {
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)
        result.assertHasErrorOutput("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    @Unroll
    def "can define same rules for multiple report tasks #tasksPaths"() {
        given:
        javaProjectUnderTest.writeIntegrationTestSourceFiles()

        buildFile << """
            tasks.withType(JacocoCoverageVerification) {
                violationRules {
                    rule {
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails tasksPaths

        then:
        executedAndNotSkipped(tasksPaths)
        failure.assertHasCause("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")

        where:
        tasksPaths << [TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS, INTEG_TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS]
    }

    @Unroll
    def "can define different rules for multiple report tasks #tasksPaths"() {
        given:
        javaProjectUnderTest.writeIntegrationTestSourceFiles()

        buildFile << """
            $reportTaskName {
                violationRules {
                    rule {
                        $limit
                    }
                }
            }
        """

        when:
        fails tasksPaths

        then:
        executedAndNotSkipped(tasksPaths)
        failure.assertHasCause("Rule violated for bundle $testDirectory.name: $errorMessage")

        where:
        tasksPaths                                             | reportTaskName                              | limit                                  | errorMessage
        TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS       | 'jacocoTestCoverageVerification'            | Insufficient.LINE_METRIC_COVERED_RATIO | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        INTEG_TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS | 'jacocoIntegrationTestCoverageVerification' | Insufficient.CLASS_METRIC_MISSED_COUNT | 'classes missed count is 0.0, but expected minimum is 0.5'
    }

    def "task is never UP-TO-DATE as it does not define any outputs"() {
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule {
                        $Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS)

        when:
        succeeds TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executed(JACOCO_COVERAGE_VERIFICATION_TASK_PATH)
        skipped(TEST_TASK_PATH)

        when:
        buildFile << """
            jacocoTestCoverageVerification.violationRules.rules[0].limits[0].maximum = 0.5
        """

        fails TEST_AND_JACOCO_COVERAGE_VERIFICATION_TASK_PATHS

        then:
        executed(JACOCO_COVERAGE_VERIFICATION_TASK_PATH)
        skipped(TEST_TASK_PATH)
        failure.assertHasCause("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }
}
