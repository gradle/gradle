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

import org.apache.commons.lang.RandomStringUtils
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

    @Unroll("contentEquals() returns #expected for #nodeA <> #nodeB")
    def "contentEquals() returns expected result"() {
        expect:
        nodeA.contentEquals(nodeB) == expected

        where:
        nodeA                                     | nodeB                                     | expected
        node(null)                                | node(null)                                | true
        node("a")                                 | node("a")                                 | true
        node("a")                                 | node("b")                                 | false
        node(null)                                | node("a")                                 | false
        node("a")                                 | node(null)                                | false
        node("a", node("child"))                  | node("a")                                 | false
        node("a")                                 | node("a", node("child"))                  | false
        node("a", node("child"))                  | node("a", node("child"))                  | true
        node("a", node("child1"), node("child2")) | node("a", node("child1"), node("child2")) | true
        node("a", node("child2"), node("child1")) | node("a", node("child1"), node("child2")) | false
        node("a", node("child1"))                 | node("a", node("child1"), node("child2")) | false
        node("a", node("child1"), node("child2")) | node("a", node("child1"))                 | false
    }

    @Unroll("contentHashCode() returns #expected for #nodeToTest")
    def "contentHashCode() returns expected result"() {
        expect:
        nodeToTest.contentHashCode() == expected

        where:
        nodeToTest               | expected
        node(null)               | 0
        node("a")                | "a".hashCode()
        node("a", node("child")) | "a".hashCode() * 31 + "child".hashCode()
    }

    ModelNodeInternal node(def privateData, ModelNodeInternal... links) {
        return Spy(ModelNodeInternal) {
            getPath() >> RandomStringUtils.randomAlphabetic(12)
            getPrivateData() >> privateData
            getLinks() >> (links as List)
            toString() >> "$privateData${links.length == 0 ? "" : links as List}"
        }
    }
}
