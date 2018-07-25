/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.action

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

class InstantiatingActionTest extends Specification {

    final shouldNotFail = new InstantiatingAction.ExceptionHandler() {
        @Override
        void handleException(Object target, Throwable throwable) {
            throw new AssertionError("Expected test to pass, but failed with an exception", throwable)
        }
    }

    def "can instantiate rule with parameters"() {
        def details = Mock(Details)

        when:
        def action = new InstantiatingAction<Details>(
            DefaultConfigurableRules.of(
                DefaultConfigurableRule.of(RuleWithParams, new Action<ActionConfiguration>() {
                    @Override
                    void execute(ActionConfiguration actionConfiguration) {
                        actionConfiguration.params(123, "test string")
                    }
                }, TestUtil.valueSnapshotter())),
            TestUtil.instantiatorFactory().decorate(),
            shouldNotFail
        )

        then:
        action.rules.configurableRules[0].ruleParams.isolate() == [123, "test string"] as Object[]
        action.rules.configurableRules[0].ruleClass == RuleWithParams

        when:
        action.execute(details)

        then:
        1 * details.see(123, "test string")

    }

    def "can instantiate rule with injected parameters"() {
        def details = Mock(Details)
        def service = Mock(SomeService)
        def registry = new DefaultServiceRegistry()
        registry.add(SomeService, service)

        when:
        def action = new InstantiatingAction<Details>(
            DefaultConfigurableRules.of(DefaultConfigurableRule.of(RuleWithInjectedParams)),
            TestUtil.instantiatorFactory().inject(registry),
            shouldNotFail
        )

        then:
        action.rules.configurableRules[0].ruleParams.isolate() == [] as Object[]
        action.rules.configurableRules[0].ruleClass == RuleWithInjectedParams

        when:
        action.execute(details)

        then:
        1 * service.doSomething() >> "hello from service"
        1 * details.see("hello from service")

    }

    def "can instantiate rule with injected and user-provided parameters"() {
        def details = Mock(Details)
        def service = Mock(SomeService)
        def registry = new DefaultServiceRegistry()
        registry.add(SomeService, service)

        when:
        def action = new InstantiatingAction<Details>(
            DefaultConfigurableRules.of(
                DefaultConfigurableRule.of(RuleWithInjectedAndRegularParams, new Action<ActionConfiguration>() {
                    @Override
                    void execute(ActionConfiguration actionConfiguration) {
                        actionConfiguration.params(456)
                    }
                }, TestUtil.valueSnapshotter())),
            TestUtil.instantiatorFactory().inject(registry),
            shouldNotFail
        )

        then:
        action.rules.configurableRules[0].ruleParams.isolate() == [456] as Object[]
        action.rules.configurableRules[0].ruleClass == RuleWithInjectedAndRegularParams

        when:
        action.execute(details)

        then:
        1 * service.doSomething() >> "hello from service"
        1 * details.see("hello from service", 456)

    }

    static interface Details {
        void see(Object... args)
    }

    static class RuleWithoutParams implements Action<Details> {
        @Override
        void execute(Details details) {
            details.see()
        }
    }

    static class RuleWithParams implements Action<Details> {
        private final int x
        private final String str

        RuleWithParams(int x, String str) {
            this.x = x
            this.str = str
        }

        @Override
        void execute(Details details) {
            details.see(x, str)
        }
    }

    static class RuleWithInjectedParams implements Action<Details> {

        private final SomeService service

        @Inject
        RuleWithInjectedParams(SomeService service) {
            this.service = service
        }

        @Override
        void execute(Details details) {
            details.see(service.doSomething())
        }
    }

    static class RuleWithInjectedAndRegularParams implements Action<Details> {

        private final SomeService service
        private final int x

        @Inject
        RuleWithInjectedAndRegularParams(SomeService service, int x) {
            this.service = service
            this.x = x
        }

        @Override
        void execute(Details details) {
            details.see(service.doSomething(), x)
        }
    }

    static interface SomeService<T> {
        T doSomething()
    }
}
