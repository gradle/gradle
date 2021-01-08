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
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import spock.lang.Specification

class DefaultRuleActionAdapterTest extends Specification {
    def RuleActionValidator noopValidator
    def ruleActionAdapter

    def setup() {
        noopValidator = Stub(RuleActionValidator) {
            validate(_) >> { RuleAction ruleAction -> ruleAction }
        }
    }

    def "can adapt from closure" () {
        ruleActionAdapter = new DefaultRuleActionAdapter(noopValidator, "context")
        def closureCalled = ""

        when:
        def ruleAction = ruleActionAdapter.createFromClosure(String, { String s -> closureCalled = s })
        ruleAction.execute("string", [])

        then:
        ruleAction.inputTypes == []
        closureCalled == "string"

        when:
        ruleAction = ruleActionAdapter.createFromClosure(String, { s -> closureCalled = s })
        ruleAction.execute("object", [])

        then:
        ruleAction.inputTypes == []
        closureCalled == "object"

        when:
        ruleAction = ruleActionAdapter.createFromClosure(String, { -> closureCalled = delegate })
        ruleAction.execute("zero", [])

        then:
        ruleAction.inputTypes == []
        closureCalled == "zero"

        when:
        ruleAction = ruleActionAdapter.createFromClosure(String, { closureCalled = it })
        ruleAction.execute("it", [])

        then:
        ruleAction.inputTypes == []
        closureCalled == "it"

        when:
        ruleAction = ruleActionAdapter.createFromClosure(String, { String s, String input1, Integer input2 -> closureCalled = input1 + input2 })
        ruleAction.execute("", ["foo", 3])

        then:
        ruleAction.inputTypes == [String, Integer]
        closureCalled == "foo3"
    }

    def "can adapt from action" () {
        ruleActionAdapter = new DefaultRuleActionAdapter(noopValidator, "context")
        def actionCalled = false

        when:
        def ruleAction = ruleActionAdapter.createFromAction(Stub(Action) {
            execute(_) >> { actionCalled = true }
        })
        ruleAction.execute("", [])

        then:
        actionCalled
    }

    def "fails to adapt closure with invalid subject" () {
        ruleActionAdapter = new DefaultRuleActionAdapter(noopValidator, "context")

        when:
        ruleActionAdapter.createFromClosure(String, {List subject -> })

        then:
        def failure = thrown(InvalidUserCodeException)
        failure.message == "The closure provided is not valid as a rule for 'context'."
        failure.cause instanceof RuleActionValidationException
        failure.cause.message == "First parameter of rule action closure must be of type 'String'."
    }

    def "fails to adapt closure when validation fails" () {
        def RuleActionValidator ruleActionValidator = Stub(RuleActionValidator) {
            validate(_) >> { RuleAction ruleAction -> throw new RuleActionValidationException("FAILED") }
        }
        ruleActionAdapter = new DefaultRuleActionAdapter(ruleActionValidator, "context")

        when:
        ruleActionAdapter.createFromClosure(String, { String s -> })

        then:
        def failure = thrown(InvalidUserCodeException)
        failure.message == "The closure provided is not valid as a rule for 'context'."
        failure.cause instanceof RuleActionValidationException
        failure.cause.message == "FAILED"
    }

    def "fails to adapt action when validation fails" () {
        def RuleActionValidator ruleActionValidator = Stub(RuleActionValidator) {
            validate(_) >> { RuleAction ruleAction -> throw new RuleActionValidationException("FAILED") }
        }
        ruleActionAdapter = new DefaultRuleActionAdapter(ruleActionValidator, "context")

        when:
        ruleActionAdapter.createFromAction(Stub(Action))

        then:
        def failure = thrown(InvalidUserCodeException)
        failure.message == "The action provided is not valid as a rule for 'context'."
        failure.cause instanceof RuleActionValidationException
        failure.cause.message == "FAILED"
    }
}
