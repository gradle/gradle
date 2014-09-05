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

import org.gradle.api.RuleAction
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.specs.Spec
import spock.lang.Specification

class DelegatingTargetedRuleActionTest extends Specification {
    def "calls delegate spec" () {
        def delegate = Mock(Spec)
        def action = new DelegatingTargetedRuleAction(delegate, Stub(RuleAction))

        when:
        action.isSatisfiedBy(Stub(ComponentSelection))

        then:
        1 * delegate.isSatisfiedBy(_ as ComponentSelection)
    }

    def "calls delegate action" () {
        def delegate = Mock(RuleAction)
        def action = new DelegatingTargetedRuleAction(Stub(Spec), delegate)

        when:
        action.execute(Stub(ComponentSelection), [])

        then:
        1 * delegate.execute(_ as ComponentSelection, _)

        when:
        action.getInputTypes()

        then:
        1 * delegate.getInputTypes()
    }
}
