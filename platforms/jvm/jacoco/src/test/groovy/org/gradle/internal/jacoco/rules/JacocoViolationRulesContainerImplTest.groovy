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

package org.gradle.internal.jacoco.rules

import org.gradle.api.Action
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule
import org.gradle.util.TestUtil
import spock.lang.Specification

class JacocoViolationRulesContainerImplTest extends Specification {

    JacocoViolationRulesContainerImpl violationRulesContainer = TestUtil.newInstance(JacocoViolationRulesContainerImpl)

    def "provides expected default field values"() {
        expect:
        violationRulesContainer.failOnViolation.get()
        violationRulesContainer.rules.get().empty
    }

    def "can add rules"() {
        when:
        def rule = violationRulesContainer.rule(new Action<JacocoViolationRule>() {
            @Override
            void execute(JacocoViolationRule jacocoValidationRule) {
                jacocoValidationRule.with {
                    enabled = false
                    element = 'CLASS'
                    includes = ['**/*.class']
                    excludes = ['*Special*.class']
                }
            }
        })

        then:
        violationRulesContainer.rules.get().size() == 1
        violationRulesContainer.rules.get()[0] == rule

        when:
        rule = violationRulesContainer.rule(new Action<JacocoViolationRule>() {
            @Override
            void execute(JacocoViolationRule jacocoValidationRule) {
                jacocoValidationRule.with {
                    enabled = true
                    element = 'PACKAGE'
                    includes = ['**/*']
                    excludes = ['**/special/*']
                }
            }
        })

        then:
        violationRulesContainer.rules.get().size() == 2
        violationRulesContainer.rules.get()[1] == rule
    }

    def "returned rules are unmodifiable"() {
        when:
        violationRulesContainer.rules << Mock(JacocoViolationRuleImpl)

        then:
        thrown(MissingMethodException)
    }
}
