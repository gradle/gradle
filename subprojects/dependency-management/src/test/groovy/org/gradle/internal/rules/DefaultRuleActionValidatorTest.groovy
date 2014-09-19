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
    def ruleValidator = new DefaultRuleActionValidator<Object>([String, Integer])

    def "rejects invalid types" () {
        when:
        ruleValidator.validate(Stub(RuleAction) {
            getInputTypes() >> { [ String, Long ] }
        })

        then:
        def failure = thrown(RuleActionValidationException)
        failure.message == "Unsupported parameter type: java.lang.Long"
    }

    def "accepts valid types" () {
        def ruleAction = Stub(RuleAction) {
            getInputTypes() >> { [ String, Integer ] }
        }

        expect:
        ruleValidator.validate(ruleAction) == ruleAction
    }
}
