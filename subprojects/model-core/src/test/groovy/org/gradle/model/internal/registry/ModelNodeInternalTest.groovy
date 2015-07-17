/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import spock.lang.Unroll

class ModelNodeInternalTest extends RegistrySpec {
    CreatorRuleBinder creatorRuleBinder = Mock()

    def "should have zero executed rules initially"() {
        expect:
        new TestNode(creatorRuleBinder).getExecutedRules().size() == 0
    }

    @Unroll
    def "should record executed rules when notify fired #fireCount time(s)"() {
        ModelRuleDescriptor descriptor = Mock()
        ModelNodeInternal modelNode = new TestNode(creatorRuleBinder)
        MutatorRuleBinder executionBinder = Mock()
        executionBinder.isBound() >> true
        executionBinder.getInputBindings() >> []
        executionBinder.getDescriptor() >> descriptor

        when:
        fireCount.times {
            modelNode.notifyFired(executionBinder)
        }

        then:
        modelNode.executedRules.size() == fireCount
        modelNode.executedRules[0] == descriptor

        where:
        fireCount << [1, 2]
    }

    def "should not fire for unbound binders"() {
        setup:
        ModelNodeInternal modelNode = new TestNode(creatorRuleBinder)
        MutatorRuleBinder executionBinder = Mock()
        executionBinder.isBound() >> false

        when:
        modelNode.notifyFired(executionBinder)

        then:
        AssertionError e = thrown()
        e.message == 'RuleBinder must be in a bound state'
    }
}
