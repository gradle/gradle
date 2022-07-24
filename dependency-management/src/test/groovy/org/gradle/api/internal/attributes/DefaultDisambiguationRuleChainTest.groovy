/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.attributes

import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

class DefaultDisambiguationRuleChainTest extends Specification {
    def ruleChain = new DefaultDisambiguationRuleChain(TestUtil.instantiatorFactory().inject(), SnapshotTestUtil.isolatableFactory())

    static class SelectionRule implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> details) {
            assert details.candidateValues == ["value1", "value2"] as Set
            details.closestMatch("value1")
        }
    }
    static class SelectionRuleWithParams implements AttributeDisambiguationRule<String> {
        String p1

        @Inject
        SelectionRuleWithParams(String p1) {
            this.p1 = p1
        }

        @Override
        void execute(MultipleCandidatesDetails<String> details) {
            assert p1 == "p1"
            assert details.candidateValues == ["value1", "value2"] as Set
            details.closestMatch("value1")
        }
    }

    def "creates instance of rule implementation and delegates to it"() {
        def details = Mock(MultipleCandidatesResult)

        given:
        ruleChain.add(SelectionRule)

        when:
        ruleChain.execute(details)

        then:
        1 * details.candidateValues >> (["value1", "value2"] as Set)
        1 * details.closestMatch("value1")
    }

    def "can inject parameters into rule instance"() {
        def details = Mock(MultipleCandidatesResult)

        given:
        ruleChain.add(SelectionRuleWithParams) { it.params("p1") }

        when:
        ruleChain.execute(details)

        then:
        1 * details.candidateValues >> (["value1", "value2"] as Set)
        1 * details.closestMatch("value1")
    }

    static class BrokenRule implements AttributeDisambiguationRule<String> {
        static failure = new RuntimeException("broken")

        @Override
        void execute(MultipleCandidatesDetails<String> details) {
            throw failure
        }
    }

    def "wraps failure to execute rule"() {
        def details = Mock(MultipleCandidatesResult)

        given:
        details.candidateValues >> (["value1", "value2"] as Set)
        ruleChain.add(BrokenRule)

        when:
        ruleChain.execute(details)

        then:
        def e = thrown(AttributeMatchException)
        e.message == 'Could not select value from candidates [value1, value2] using DefaultDisambiguationRuleChainTest.BrokenRule.'
        e.cause == BrokenRule.failure
    }

    static class CannotCreateRule implements AttributeDisambiguationRule<String> {
        static failure = new RuntimeException("broken")

        CannotCreateRule() {
            throw failure
        }

        @Override
        void execute(MultipleCandidatesDetails<String> details) {
        }
    }

    def "wraps failure to create rule"() {
        def details = Mock(MultipleCandidatesResult)

        given:
        details.candidateValues >> (["value1", "value2"] as Set)
        ruleChain.add(CannotCreateRule)

        when:
        ruleChain.execute(details)

        then:
        def e = thrown(AttributeMatchException)
        e.message == 'Could not select value from candidates [value1, value2] using DefaultDisambiguationRuleChainTest.CannotCreateRule.'
        e.cause instanceof ObjectInstantiationException
        e.cause.cause == CannotCreateRule.failure
    }
}
