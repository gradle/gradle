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
import org.gradle.testing.jacoco.tasks.rules.JacocoRuleScope
import org.gradle.testing.jacoco.tasks.rules.JacocoValidationRule
import spock.lang.Specification

class JacocoValidationRulesContainerImplTest extends Specification {

    JacocoValidationRulesContainerImpl validationRulesContainer = new JacocoValidationRulesContainerImpl()

    def "provides expected default field values"() {
        expect:
        !validationRulesContainer.failOnViolation
        validationRulesContainer.rules.empty
    }

    def "can add rules"() {
        when:
        def rule = validationRulesContainer.rule {
            enabled = false
            scope = JacocoRuleScope.CLASS
            includes = ['**/*.class']
            excludes = ['*Special*.class']
        }

        then:
        validationRulesContainer.rules.size() == 1
        validationRulesContainer.rules[0] == rule

        when:
        rule = validationRulesContainer.rule(new Action<JacocoValidationRule>() {
            @Override
            void execute(JacocoValidationRule jacocoValidationRule) {
                jacocoValidationRule.with {
                    enabled = true
                    scope = JacocoRuleScope.PACKAGE
                    includes = ['**/*']
                    excludes = ['**/special/*']
                }
            }
        })

        then:
        validationRulesContainer.rules.size() == 2
        validationRulesContainer.rules[1] == rule
    }

    def "returned rules are unmodifiable"() {
        when:
        validationRulesContainer.rules << new JacocoValidationRuleImpl()

        then:
        thrown(UnsupportedOperationException)
    }
}
