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

import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

class DefaultCompatibilityRuleChainTest extends Specification {
    def ruleChain = new DefaultCompatibilityRuleChain(TestUtil.instantiatorFactory().inject(), SnapshotTestUtil.isolatableFactory())

    static class CompatibilityRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            assert details.consumerValue == "value1"
            assert details.producerValue == "value2"
            details.compatible()
        }
    }

    static class CompatibilityRuleWithParams implements AttributeCompatibilityRule<String> {
        String p1

        @Inject
        CompatibilityRuleWithParams(String p1) {
            this.p1 = p1
        }

        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            assert p1 == "p1"
            assert details.consumerValue == "value1"
            assert details.producerValue == "value2"
            details.compatible()
        }
    }

    def "creates instance of rule implementation and delegates to it"() {
        def details = Mock(CompatibilityCheckResult)

        given:
        ruleChain.add(CompatibilityRule)

        when:
        ruleChain.execute(details)

        then:
        1 * details.consumerValue >> "value1"
        1 * details.producerValue >> "value2"
        1 * details.compatible()
    }

    def "can inject configuration into rule instance"() {
        def details = Mock(CompatibilityCheckResult)

        given:
        ruleChain.add(CompatibilityRuleWithParams) { it.params("p1") }

        when:
        ruleChain.execute(details)

        then:
        1 * details.consumerValue >> "value1"
        1 * details.producerValue >> "value2"
        1 * details.compatible()
    }

    static class BrokenRule implements AttributeCompatibilityRule<String> {
        static failure = new RuntimeException("broken")

        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            throw failure
        }
    }

    def "wraps failure to execute rule"() {
        def details = Mock(CompatibilityCheckResult)

        given:
        details.consumerValue >> "value1"
        details.producerValue >> "value2"
        ruleChain.add(BrokenRule)

        when:
        ruleChain.execute(details)

        then:
        def e = thrown(AttributeMatchException)
        e.message == 'Could not determine whether value value2 is compatible with value value1 using DefaultCompatibilityRuleChainTest.BrokenRule.'
        e.cause == BrokenRule.failure
    }

    static class CannotCreateRule implements AttributeCompatibilityRule<String> {
        static failure = new RuntimeException("broken")

        CannotCreateRule() {
            throw failure
        }

        @Override
        void execute(CompatibilityCheckDetails<String> details) {
        }
    }

    def "wraps failure to create rule"() {
        def details = Mock(CompatibilityCheckResult)

        given:
        details.consumerValue >> "value1"
        details.producerValue >> "value2"
        ruleChain.add(CannotCreateRule)

        when:
        ruleChain.execute(details)

        then:
        def e = thrown(AttributeMatchException)
        e.message == 'Could not determine whether value value2 is compatible with value value1 using DefaultCompatibilityRuleChainTest.CannotCreateRule.'
        e.cause instanceof ObjectInstantiationException
        e.cause.cause == CannotCreateRule.failure
    }
}
