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

package org.gradle.api.internal
import org.gradle.api.Action
import org.gradle.api.RuleAction
import spock.lang.Specification

class NoInputsRuleActionTest extends Specification {

    def "creates rule action based on action"() {
        given:
        def called = false
        String thing = "1"
        def baseAction = { String val ->
            called = true
            assert val.is(thing)
        } as Action<String>

        when:
        ruleAction(baseAction).execute(thing, [])

        then:
        called
    }

    def "rule action has no inputs"() {
        when:
        def action = ruleAction({ String val -> } as Action<String>)

        then:
        action.inputTypes.empty
    }

    RuleAction<String> ruleAction(Action<String> action) {
        new NoInputsRuleAction<String>(action)
    }
}
