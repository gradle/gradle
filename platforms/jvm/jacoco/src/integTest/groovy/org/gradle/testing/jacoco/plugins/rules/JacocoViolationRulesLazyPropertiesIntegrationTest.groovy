/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JacocoCoverage
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class JacocoViolationRulesLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    private static final String VIOLATION = "Rule violated for class org.gradle.Class1: lines covered ratio is 1.0, but expected maximum is 0.5"

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)

    def setup() {
        JacocoCoverage.assumeDefaultJacocoWorksOnCurrentJdk()
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
        buildFile << """
            import org.gradle.internal.jacoco.rules.JacocoViolationRuleImpl

            abstract class Producer extends DefaultTask {
                @Input
                abstract Property<String> getContent()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = content.get()
                }
            }

            def producer = tasks.register("producer", Producer) {
                content = "0.5"
                outputFile = layout.buildDirectory.file("maximum.txt")
            }

            // A rule value, as a work layer would create it
            def classLinesRule = { BigDecimal maximum ->
                def rule = new JacocoViolationRuleImpl()
                rule.element = 'CLASS'
                rule.limit {
                    it.counter = 'LINE'
                    it.value = 'COVEREDRATIO'
                    it.maximum = maximum
                }
                rule
            }
        """
    }

    def "eager API is unchanged"() {
        buildFile << """
            def container = jacocoTestCoverageVerification.violationRules
            println("failOnViolation: " + container.failOnViolation)
            println("rules: " + container.rules.size())
            def rule = container.rule { element = 'CLASS' }
            println("rules after rule: " + container.rules.size() + " same: " + container.rules[0].is(rule))
            try {
                container.rules.add(rule)
            } catch (UnsupportedOperationException e) {
                println("rules are unmodifiable")
            }
            println("lazy rules: " + jacocoTestCoverageVerification.lazyProperties.violationRules.rules.get().size())
            container.failOnViolation = false
            println("lazy failOnViolation: " + jacocoTestCoverageVerification.lazyProperties.violationRules.failOnViolation.get())
        """

        when:
        succeeds("help")

        then:
        outputContains("failOnViolation: true")
        outputContains("rules: 0")
        outputContains("rules after rule: 1 same: true")
        outputContains("rules are unmodifiable")
        outputContains("lazy rules: 1")
        outputContains("lazy failOnViolation: false")
    }

    def "input names do not change"() {
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules {
                    rule { limit { minimum = 0.1 } }
                }
                doLast {
                    println("inputs: " + inputs.properties.keySet().sort())
                }
            }
        """

        when:
        succeeds("test", "jacocoTestCoverageVerification")

        then:
        outputContains("inputs: [sourceEncoding, violationRules, violationRules.failOnViolation, violationRules.rules]")
    }

    def "a lazily bound rule list reaches the worker"() {
        buildFile << """
            jacocoTestCoverageVerification.lazyProperties.violationRules.rules = provider { [classLinesRule(0.5)] }
            println("eager rules: " + jacocoTestCoverageVerification.violationRules.rules.size())
        """

        when:
        fails("test", "jacocoTestCoverageVerification")

        then:
        outputContains("eager rules: 1")
        failure.assertHasCause(VIOLATION)
    }

    def "an eager rule is added on top of a lazily bound rule list"() {
        buildFile << """
            jacocoTestCoverageVerification {
                lazyProperties.violationRules.rules = provider { [classLinesRule(1.0)] }
                violationRules {
                    rule {
                        element = 'CLASS'
                        limit {
                            counter = 'LINE'
                            value = 'COVEREDRATIO'
                            maximum = 0.5
                        }
                    }
                }
                println("eager rules: " + violationRules.rules.size())
            }
        """

        when:
        fails("test", "jacocoTestCoverageVerification")

        then:
        outputContains("eager rules: 2")
        failure.assertHasCause(VIOLATION)
    }

    def "failOnViolation bound lazily reaches the worker, and a later eager value wins"() {
        buildFile << """
            jacocoTestCoverageVerification {
                violationRules.rule {
                    element = 'CLASS'
                    limit {
                        counter = 'LINE'
                        value = 'COVEREDRATIO'
                        maximum = 0.5
                    }
                }
                lazyProperties.violationRules.failOnViolation = provider { ${lazyValue} }
                ${eagerChange}
            }
        """

        when:
        if (failsBuild) {
            fails("test", "jacocoTestCoverageVerification")
        } else {
            succeeds("test", "jacocoTestCoverageVerification")
        }

        then:
        if (failsBuild) {
            failure.assertHasCause(VIOLATION)
        } else {
            result.assertHasErrorOutput(VIOLATION)
        }

        where:
        lazyValue | eagerChange                                  | failsBuild
        false     | ""                                           | false
        false     | "violationRules.failOnViolation = true"      | true
        true      | "violationRules.failOnViolation = false"     | false
    }

    def "a rule list bound to a task output runs that task first"() {
        buildFile << """
            jacocoTestCoverageVerification.lazyProperties.violationRules.rules.add(
                producer.flatMap { it.outputFile }.map { classLinesRule(new BigDecimal(it.asFile.text.trim())) }
            )
        """

        when:
        fails("test", "jacocoTestCoverageVerification")

        then:
        result.assertTaskScheduled(":producer")
        result.assertTaskOrder(":producer", ":jacocoTestCoverageVerification")
        failure.assertHasCause(VIOLATION)
    }

    def "a rule changed after it was added is used with its last state"() {
        buildFile << """
            def rule = jacocoTestCoverageVerification.violationRules.rule {
                element = 'CLASS'
                limit {
                    counter = 'LINE'
                    value = 'COVEREDRATIO'
                    maximum = 0.5
                }
            }
            rule.enabled = false
        """

        when:
        succeeds("test", "jacocoTestCoverageVerification")

        then:
        executedAndNotSkipped(":jacocoTestCoverageVerification")
        outputDoesNotContain(VIOLATION)
    }
}
