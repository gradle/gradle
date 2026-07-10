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

import org.gradle.model.internal.core.ModelRegistration
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor

class ModelNodeInternalTest extends RegistrySpec {
    def registration = Mock(ModelRegistration)

    def "should have zero executed rules initially"() {
        expect:
        new TestNode(registration).getExecutedRules().size() == 0
    }

    def "should record executed rules when notify fired #fireCount time(s)"() {
        def descriptor = Mock(ModelRuleDescriptor)
        ModelNodeInternal modelNode = new TestNode(registration)
        def executionBinder = Mock(RuleBinder)
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
        ModelNodeInternal modelNode = new TestNode(registration)
        def executionBinder = Mock(RuleBinder)
        executionBinder.isBound() >> false

        when:
        modelNode.notifyFired(executionBinder)

        then:
        AssertionError e = thrown()
        e.message == 'RuleBinder must be in a bound state'
    }
}
