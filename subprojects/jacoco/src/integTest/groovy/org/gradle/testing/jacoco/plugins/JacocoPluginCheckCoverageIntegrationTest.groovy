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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest
import org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric
import org.gradle.testing.jacoco.tasks.rules.JacocoThresholdValue
import spock.lang.Unroll

import static org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric.CLASS
import static org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric.LINE
import static org.gradle.testing.jacoco.tasks.rules.JacocoThresholdValue.COVEREDRATIO
import static org.gradle.testing.jacoco.tasks.rules.JacocoThresholdValue.MISSEDCOUNT

class JacocoPluginCheckCoverageIntegrationTest extends AbstractIntegrationSpec {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)
    private final static String[] TEST_AND_JACOCO_REPORT_TASK_PATHS = [':test', ':jacocoTestReport'] as String[]
    private final static String[] INTEG_TEST_AND_JACOCO_REPORT_TASK_PATHS = [':integrationTest', ':jacocoIntegrationTestReport'] as String[]

    def setup() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
    }

    def "can define no rules"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {}
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "can define single rule without thresholds"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {}
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "can define includes for single rule"() {
        given:
        String scope = "${org.gradle.testing.jacoco.tasks.rules.JacocoRuleScope.CLASS.getClass().getName()}.${org.gradle.testing.jacoco.tasks.rules.JacocoRuleScope.CLASS.name()}"
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        scope = $scope
                        includes = ['com.company.*', 'org.gradle.*']
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for class org.gradle.Class1: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    def "can define excludes for single rule"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        excludes = ['company', '$testDirectory.name']
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "can check rules even all report formats are disabled"() {
        given:
        buildFile << """
            jacocoTestReport {
                reports {
                    xml.enabled false
                    csv.enabled false
                    html.enabled false
                }
                validationRules {
                    rule {
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    @Unroll
    def "can define rule with sufficient coverage for #description"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        ${thresholds.join('\n')}
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)

        where:
        thresholds                                        | description
        [Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO] | 'line metric with covered ratio'
        [Thresholds.Sufficient.CLASS_METRIC_MISSED_COUNT] | 'class metric with missed count'
        [Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO,
         Thresholds.Sufficient.CLASS_METRIC_MISSED_COUNT] | 'line and class metric'

    }

    @Unroll
    def "can define rule with insufficient coverage for #description"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        ${thresholds.join('\n')}
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: $errorMessage")

        where:
        thresholds                                          | description                                       | errorMessage
        [Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO] | 'line metric with covered ratio'                  | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT] | 'class metric with missed count'                  | 'classes missed count is 0.0, but expected minimum is 0.5'
        [Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO,
         Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT] | 'first of multiple insufficient thresholds fails' | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO,
         Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT,
         Thresholds.Sufficient.CLASS_METRIC_MISSED_COUNT]   | 'first insufficient threshold fails'              | 'classes missed count is 0.0, but expected minimum is 0.5'
    }

    def "can define multiple rules"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        $Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                    rule {
                        $Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: classes missed count is 0.0, but expected minimum is 0.5")
    }

    def "can disable rules"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        $Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                    rule {
                        enabled = false
                        $Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "can ignore failures"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    ignoreFailures = true

                    rule {
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    @Unroll
    def "can define same rules for multiple report tasks #tasksPaths"() {
        given:
        javaProjectUnderTest.writeIntegrationTestSourceFiles()

        buildFile << """
            tasks.withType(JacocoReport) {
                validationRules {
                    rule {
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails tasksPaths

        then:
        executedAndNotSkipped(tasksPaths)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")

        where:
        tasksPaths << [TEST_AND_JACOCO_REPORT_TASK_PATHS, INTEG_TEST_AND_JACOCO_REPORT_TASK_PATHS]
    }

    @Unroll
    def "can define different rules for multiple report tasks #tasksPaths"() {
        given:
        javaProjectUnderTest.writeIntegrationTestSourceFiles()

        buildFile << """
            $reportTaskName {
                validationRules {
                    rule {
                        $threshold
                    }
                }
            }
        """

        when:
        fails tasksPaths

        then:
        executedAndNotSkipped(tasksPaths)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: $errorMessage")

        where:
        tasksPaths                              | reportTaskName                | threshold                                         | errorMessage
        TEST_AND_JACOCO_REPORT_TASK_PATHS       | 'jacocoTestReport'            | Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        INTEG_TEST_AND_JACOCO_REPORT_TASK_PATHS | 'jacocoIntegrationTestReport' | Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT | 'classes missed count is 0.0, but expected minimum is 0.5'
    }

    static class Thresholds {
        static class Sufficient {
            static final String LINE_METRIC_COVERED_RATIO = Thresholds.create(LINE, COVEREDRATIO, '0.0', '1.0')
            static final String CLASS_METRIC_MISSED_COUNT = Thresholds.create(CLASS, MISSEDCOUNT, null, '0')
        }

        static class Insufficient {
            static final String LINE_METRIC_COVERED_RATIO = Thresholds.create(LINE, COVEREDRATIO, '0.0', '0.5')
            static final String CLASS_METRIC_MISSED_COUNT = Thresholds.create(CLASS, MISSEDCOUNT, '0.5', null)
        }

        private static String create(JacocoThresholdMetric metric, JacocoThresholdValue value, String minimum, String maximum) {
            StringBuilder threshold = new StringBuilder()
            threshold <<= 'threshold {\n'

            if (metric) {
                threshold <<= "    metric = ${metric.getClass().getName()}.${metric.name()}\n"
            }
            if (value) {
                threshold <<= "    value = ${value.getClass().getName()}.${value.name()}\n"
            }
            if (minimum) {
                threshold <<= "    minimum = $minimum\n"
            }
            if (maximum) {
                threshold <<= "    maximum = $maximum\n"
            }

            threshold <<= '}'
            threshold.toString()
        }
    }
}
