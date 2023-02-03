/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.rules

import spock.lang.Specification

class DefaultRuleActionValidatorTest extends Specification {

    def "rejects invalid type when type configured" () {
        when:
        def ruleValidator = new DefaultRuleActionValidator(Integer)
        ruleValidator.validate(Stub(RuleAction) {
            getInputTypes() >> { [ Long ] }
        })

        then:
        def failure = thrown(RuleActionValidationException)
        failure.message == "Rule may not have an input parameter of type: java.lang.Long. Second parameter must be of type: java.lang.Integer."
    }

    def "rejects invalid type when no type configured" () {
        when:
        def ruleValidator = new DefaultRuleActionValidator()
        ruleValidator.validate(Stub(RuleAction) {
            getInputTypes() >> { [Long] }
        })

        then:
        def failure = thrown(RuleActionValidationException)
        failure.message == "Rule may not have an input parameter of type: java.lang.Long."
    }

    def "rejects invalid type when multiple types are configured"() {
        when:
        def ruleValidator = new DefaultRuleActionValidator(Integer, Short)
        ruleValidator.validate(Stub(RuleAction) {
            getInputTypes() >> { [Long] }
        })

        then:
        def failure = thrown(RuleActionValidationException)
        failure.message == "Rule may not have an input parameter of type: java.lang.Long. Second parameter must be of type: java.lang.Integer or java.lang.Short."
    }

    def "accepts valid type"() {
        def ruleValidator = new DefaultRuleActionValidator(String)
        def ruleAction = Stub(RuleAction) {
            getInputTypes() >> { [String] }
        }

        expect:
        ruleValidator.validate(ruleAction) == ruleAction
    }

    def "accepts valid type with multiple valid types"() {
        def ruleValidator = new DefaultRuleActionValidator(String, Long)
        def ruleAction = Stub(RuleAction) {
            getInputTypes() >> { [parameterType] }
        }

        expect:
        ruleValidator.validate(ruleAction) == ruleAction

        where:
        parameterType << [String, Long]
    }
}
