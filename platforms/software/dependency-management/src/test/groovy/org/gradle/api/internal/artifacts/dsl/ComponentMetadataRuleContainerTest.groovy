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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.rules.SpecRuleAction
import spock.lang.Specification
import spock.lang.Subject

class ComponentMetadataRuleContainerTest extends Specification {

    @Subject
    def container = new ComponentMetadataRuleContainer()

    def 'is empty by default'() {
        expect:
        container.asImmutable().rules.isEmpty()
    }

    def 'records added rules in order'() {
        given:
        def rule1 = Mock(SpecRuleAction)
        def rule2 = configurableRule()

        when:
        container.addRule(rule1)
        container.addClassRule(rule2)

        then:
        def rules = container.asImmutable().rules
        rules.size() == 2
        rules[0] instanceof ImmutableComponentMetadataRules.ImmutableRule.ActionBased
        rules[0].rule() == rule1
        rules[1] instanceof ImmutableComponentMetadataRules.ImmutableRule.ClassBased
        rules[1].classRules().contains(rule2)
    }

    def 'records subsequent class based rule in a single wrapper'() {
        given:
        def rule1 = configurableRule()
        def rule2 = configurableRule()

        when:
        container.addClassRule(rule1)
        container.addClassRule(rule2)

        then:
        def rules = container.asImmutable().rules
        rules.size() == 1
        rules[0] instanceof ImmutableComponentMetadataRules.ImmutableRule.ClassBased
        rules[0].classRules().containsAll([rule1, rule2])
    }

    private SpecConfigurableRule configurableRule() {
        Mock(SpecConfigurableRule) {
            getConfigurableRule() >> Stub(ConfigurableRule)
        }
    }
}
