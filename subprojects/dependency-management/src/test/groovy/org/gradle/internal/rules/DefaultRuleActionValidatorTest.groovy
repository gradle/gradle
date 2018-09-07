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

    def "rejects invalid types" () {
        when:
        def ruleValidator = new DefaultRuleActionValidator([String, Integer])
        ruleValidator.validate(Stub(RuleAction) {
            getInputTypes() >> { [ String, Long ] }
        })

        then:
        def failure = thrown(RuleActionValidationException)
        failure.message == "Rule may not have an input parameter of type: java.lang.Long. Valid types (for the second and subsequent parameters) are: [java.lang.String, java.lang.Integer]."
    }

    def "rejects invalid type" () {
        when:
        def ruleValidator = new DefaultRuleActionValidator([Integer])
        ruleValidator.validate(Stub(RuleAction) {
            getInputTypes() >> { [ Long ] }
        })

        then:
        def failure = thrown(RuleActionValidationException)
        failure.message == "Rule may not have an input parameter of type: java.lang.Long. Second parameter must be of type: java.lang.Integer."
    }

    def "accepts valid types" () {
        def ruleValidator = new DefaultRuleActionValidator([String, Integer])
        def ruleAction = Stub(RuleAction) {
            getInputTypes() >> { [ String, Integer ] }
        }

        expect:
        ruleValidator.validate(ruleAction) == ruleAction
    }
}
