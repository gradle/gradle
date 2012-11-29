/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.forcing

import org.gradle.api.Action
import org.gradle.api.internal.artifacts.configurations.ModuleForcingStrategy
import spock.lang.Specification

import static java.util.Collections.emptySet
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * by Szczepan Faber, created at: 11/29/12
 */
class ForcedModuleRulesFactorySpec extends Specification {

    private factory = new ForcedModuleRulesFactory()
    private forcingStrategy = Mock(ModuleForcingStrategy)

    def "provides empty rules when input is empty"() {
        when:
        forcingStrategy.getRule() >> null
        forcingStrategy.getForcedModules() >> []

        then:
        factory.createRules(forcingStrategy).is(emptySet())
    }

    def "creates rule for given modules"() {
        given:
        forcingStrategy.getRule() >> null
        forcingStrategy.getForcedModules() >> [newSelector("org", "module", "1.0"), newSelector("org", "module2", "2.0")]

        when:
        def rules = factory.createRules(forcingStrategy) as List

        then:
        rules.size() == 1
        rules[0] instanceof ForcedVersionsRule
        rules[0].forcedModules == ["org:module": "1.0", "org:module2": "2.0"]
    }

    def "includes specified rule"() {
        when:
        def rule = {} as Action

        forcingStrategy.getRule() >> rule
        forcingStrategy.getForcedModules() >> []

        then:
        factory.createRules(forcingStrategy) == [rule] as Set
    }

    def "puts created forced versions rule before other rules"() {
        given:
        def rule = {} as Action

        forcingStrategy.getRule() >> rule
        forcingStrategy.getForcedModules() >> [newSelector("org", "module", "1.0"), newSelector("org", "module2", "2.0")]

        when:
        def rules = factory.createRules(forcingStrategy) as List

        then:
        rules.size() == 2
        rules[0] instanceof ForcedVersionsRule
        rules[1] == rule
    }
}
